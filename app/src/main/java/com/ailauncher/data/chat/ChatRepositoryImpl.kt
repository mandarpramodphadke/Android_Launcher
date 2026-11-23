package com.ailauncher.data.chat

import com.ailauncher.data.local.dao.ChatDao
import com.ailauncher.data.local.entity.ChatMessageEntity
import com.ailauncher.domain.model.ChatMessage
import com.ailauncher.domain.model.ChatRole
import com.ailauncher.domain.model.InferenceMode
import com.ailauncher.domain.model.MessageStatus
import com.ailauncher.domain.model.ToolInvocation
import com.ailauncher.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Room-backed, persistent chat history. Tool invocations are stored as JSON on their message. */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    private val dao: ChatDao,
) : ChatRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val toolListSerializer = ListSerializer(ToolInvocation.serializer())
    private val toolUpdateMutex = Mutex()

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> =
        dao.observe(conversationId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun messages(conversationId: String, limit: Int): List<ChatMessage> =
        dao.recent(conversationId, limit).map { it.toDomain() }

    override suspend fun insert(message: ChatMessage): Long = dao.insert(message.toEntity().copy(id = 0))

    override suspend fun update(message: ChatMessage) = dao.update(message.toEntity())

    override suspend fun message(id: Long): ChatMessage? = dao.get(id)?.toDomain()

    override suspend fun updateToolInvocation(messageId: Long, invocation: ToolInvocation) {
        toolUpdateMutex.withLock {
            val message = dao.get(messageId)?.toDomain() ?: return
            val calls = message.toolCalls.map { if (it.id == invocation.id) invocation else it }
            dao.update(message.copy(toolCalls = calls).toEntity())
        }
    }

    override suspend fun clear(conversationId: String) = dao.clear(conversationId)

    override suspend fun recoverInterruptedMessages() = dao.markStreamingAsCancelled()

    private fun ChatMessageEntity.toDomain() = ChatMessage(
        id = id,
        conversationId = conversationId,
        role = ChatRole.entries.firstOrNull { it.name == role } ?: ChatRole.ASSISTANT,
        content = content,
        thinking = thinking,
        toolCalls = toolCallsJson?.let { raw ->
            runCatching { json.decodeFromString(toolListSerializer, raw) }.getOrDefault(emptyList())
        } ?: emptyList(),
        status = MessageStatus.entries.firstOrNull { it.name == status } ?: MessageStatus.COMPLETE,
        mode = mode?.let { m -> InferenceMode.entries.firstOrNull { it.name == m } },
        tokensPerSecond = tokensPerSecond,
        createdAt = createdAt,
    )

    private fun ChatMessage.toEntity() = ChatMessageEntity(
        id = id,
        conversationId = conversationId,
        role = role.name,
        content = content,
        thinking = thinking,
        toolCallsJson = if (toolCalls.isEmpty()) null else json.encodeToString(toolListSerializer, toolCalls),
        status = status.name,
        mode = mode?.name,
        tokensPerSecond = tokensPerSecond,
        createdAt = createdAt,
    )
}
