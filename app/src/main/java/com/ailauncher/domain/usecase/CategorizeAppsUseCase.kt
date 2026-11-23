package com.ailauncher.domain.usecase

import com.ailauncher.domain.ai.LlmEngine
import com.ailauncher.domain.ai.PromptMessage
import com.ailauncher.domain.ai.Qwen3PromptBuilder
import com.ailauncher.domain.ai.SystemPrompts
import com.ailauncher.domain.model.AppCategory
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.CategorySource
import com.ailauncher.domain.model.GenerationEvent
import com.ailauncher.domain.model.GenerationParams
import com.ailauncher.domain.model.LlmPriority
import com.ailauncher.domain.repository.AppRepository
import com.ailauncher.domain.repository.ModelRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject

/**
 * The `sort_apps` pipeline. Categories are persisted per package, so each app is sorted once per
 * install and the drawer loads instantly afterwards.
 *
 *  1. `android:appCategory` declared by the app (exact, free)           -> SYSTEM
 *  2. Package/keyword heuristics for an instant first pass              -> HEURISTIC
 *  3. Qwen3 in Fast Action Mode, in batches, with a GBNF grammar that
 *     only admits `{"<packageName>": "<Category>", ...}`               -> AI
 *
 * User overrides ([CategorySource.USER]) are never touched.
 */
class CategorizeAppsUseCase @Inject constructor(
    private val appRepository: AppRepository,
    private val modelRepository: ModelRepository,
    private val llm: LlmEngine,
) {
    data class Progress(val done: Int, val total: Int)

    /** @return the number of apps that received a category. */
    suspend operator fun invoke(force: Boolean, onProgress: suspend (Progress) -> Unit = {}): Int {
        val candidates = appRepository.apps.first()
            .distinctBy { it.packageName }
            .filter { needsCategorization(it, force) }
        if (candidates.isEmpty()) return 0
        val total = candidates.size
        var done = 0

        val system = mutableMapOf<String, AppCategory>()
        val remaining = mutableListOf<AppInfo>()
        for (app in candidates) {
            val declared = appRepository.systemCategoryOf(app.packageName)
            if (declared != null) system[app.packageName] = declared else remaining += app
        }
        if (system.isNotEmpty()) appRepository.setCategories(system, CategorySource.SYSTEM)
        done += system.size

        val heuristic = remaining
            .filter { it.category == null }
            .mapNotNull { app -> HeuristicCategorizer.categorize(app.packageName, app.label)?.let { app.packageName to it } }
            .toMap()
        if (heuristic.isNotEmpty()) appRepository.setCategories(heuristic, CategorySource.HEURISTIC)

        if (modelRepository.readyModelFile() == null || remaining.isEmpty()) {
            onProgress(Progress(total, total))
            return system.size + heuristic.size
        }

        onProgress(Progress(done, total))
        var assigned = system.size
        for (batch in remaining.chunked(BATCH_SIZE)) {
            val result = categorizeBatch(batch)
            if (result.isNotEmpty()) appRepository.setCategories(result, CategorySource.AI)
            assigned += result.size
            done += batch.size
            onProgress(Progress(done, total))
        }
        return assigned
    }

    private fun needsCategorization(app: AppInfo, force: Boolean): Boolean = when (app.categorySource) {
        null, CategorySource.HEURISTIC -> true
        CategorySource.AI -> force
        CategorySource.SYSTEM, CategorySource.USER -> false
    }

    private suspend fun categorizeBatch(batch: List<AppInfo>): Map<String, AppCategory> {
        val packages = batch.map { it.packageName }
        val prompt = Qwen3PromptBuilder.build(
            messages = listOf(
                PromptMessage(PromptMessage.Role.SYSTEM, SystemPrompts.categorizer),
                PromptMessage(
                    PromptMessage.Role.USER,
                    SystemPrompts.categorizerRequest(batch.map { it.packageName to it.label }) +
                        " " + Qwen3PromptBuilder.NO_THINK,
                ),
            ),
            enableThinking = false,
        )
        val params = GenerationParams.fastAction(
            maxTokens = batch.size * TOKENS_PER_APP + 16,
            grammar = SystemPrompts.categorizerGrammar(packages),
        )
        val output = StringBuilder()
        llm.generate(prompt, params, LlmPriority.BACKGROUND).collect { event ->
            if (event is GenerationEvent.Token) output.append(event.text)
        }
        return parse(output.toString(), packages.toSet())
    }

    companion object {
        const val BATCH_SIZE = 16
        private const val TOKENS_PER_APP = 24
        private val json = Json { isLenient = true }

        /** Parses the model's JSON map, keeping only known package names and categories. */
        fun parse(output: String, allowedPackages: Set<String>): Map<String, AppCategory> {
            val start = output.indexOf('{')
            val end = output.lastIndexOf('}')
            if (start < 0 || end <= start) return emptyMap()
            val obj = runCatching { json.parseToJsonElement(output.substring(start, end + 1)).jsonObject }
                .getOrNull() ?: return emptyMap()
            return buildMap {
                for ((pkg, value) in obj) {
                    if (pkg !in allowedPackages) continue
                    AppCategory.fromName((value as? JsonPrimitive)?.contentOrNull)?.let { put(pkg, it) }
                }
            }
        }
    }
}
