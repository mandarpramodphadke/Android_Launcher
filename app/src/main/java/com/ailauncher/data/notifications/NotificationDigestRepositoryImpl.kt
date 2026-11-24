package com.ailauncher.data.notifications

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Parcelable
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.ailauncher.data.local.dao.NotificationDao
import com.ailauncher.data.local.entity.NotificationDigestEntity
import com.ailauncher.domain.model.NotificationDigestItem
import com.ailauncher.domain.model.SmartExtraction
import com.ailauncher.domain.model.SmartPill
import com.ailauncher.domain.notifications.NotificationSmartExtractor
import com.ailauncher.domain.repository.NotificationDigestRepository
import com.ailauncher.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The opt-in notification digest. Notifications are only recorded while the user has turned the
 * digest on in Settings (on top of granting notification access), are stored in the app's private
 * database, are purged after 24 hours, and are never sent anywhere.
 */
@Singleton
class NotificationDigestRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: NotificationDao,
    private val settings: SettingsRepository,
) : NotificationDigestRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val extractionList = ListSerializer(SmartExtraction.serializer())

    /** Re-evaluates the 24 h window every few minutes so old rows fall off without new posts. */
    private val clock: Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(CLOCK_TICK_MILLIS)
        }
    }

    override val digest: Flow<List<NotificationDigestItem>> = combine(dao.observeAll(), clock) { rows, now ->
        rows.filter { it.postedAt >= now - RETENTION_MILLIS }.map { it.toDomain() }
    }.distinctUntilChanged()

    override val badgeCounts: Flow<Map<String, Int>> = dao.observeAll()
        .map { rows -> rows.filter { it.isActive }.groupingBy { it.packageName }.eachCount() }
        .distinctUntilChanged()

    override val smartPills: Flow<List<SmartPill>> = combine(dao.observeAll(), clock) { rows, now ->
        rows.asSequence()
            .filter { !it.pillDismissed }
            .flatMap { row ->
                decode(row.extractionsJson).asSequence()
                    .filter { extraction -> now - row.postedAt <= maxAge(extraction) }
                    .map { SmartPill(row.notificationKey, row.appLabel, it, row.postedAt) }
            }
            .sortedByDescending { it.postedAtMillis }
            .distinctBy { it.extraction.value }
            .take(MAX_PILLS)
            .toList()
    }.distinctUntilChanged()

    override fun hasListenerAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    override suspend fun recent(sinceMillis: Long): List<NotificationDigestItem> =
        dao.since(maxOf(sinceMillis, System.currentTimeMillis() - RETENTION_MILLIS)).map { it.toDomain() }

    override suspend fun dismissPill(notificationKey: String) = dao.dismissPill(notificationKey)

    override suspend fun clear() = dao.clear()

    // ------------------------------------------------------------------ called by the listener

    private suspend fun enabled() = settings.launcherSettings.first().notificationDigestEnabled

    suspend fun onPosted(sbn: StatusBarNotification) {
        if (!enabled()) return
        val entity = toEntity(sbn, active = true) ?: return
        dao.upsertByKey(entity)
        dao.purgeOlderThan(System.currentTimeMillis() - RETENTION_MILLIS)
    }

    suspend fun onRemoved(key: String) {
        if (!enabled()) return
        dao.markInactive(key)
    }

    /** On (re)connect: everything still in the shade is active, everything else is not. */
    suspend fun resync(active: List<StatusBarNotification>) {
        if (!enabled()) return
        dao.markAllInactive()
        active.forEach { sbn -> toEntity(sbn, active = true)?.let { dao.upsertByKey(it) } }
        dao.purgeOlderThan(System.currentTimeMillis() - RETENTION_MILLIS)
    }

    private fun toEntity(sbn: StatusBarNotification, active: Boolean): NotificationDigestEntity? {
        if (sbn.packageName == context.packageName) return null
        val notification = sbn.notification ?: return null
        if (notification.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE or Notification.FLAG_GROUP_SUMMARY) != 0) return null
        if (notification.category in SKIPPED_CATEGORIES) return null
        val extras = notification.extras ?: return null
        val title = (extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE) ?: extras.getCharSequence(Notification.EXTRA_TITLE))
            ?.toString()?.trim()?.take(MAX_TITLE)
        val text = (lastMessage(extras) ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString()?.trim()?.take(MAX_TEXT)
        if (title.isNullOrBlank() && text.isNullOrBlank()) return null
        val extractions = NotificationSmartExtractor.extract(title, text)
        return NotificationDigestEntity(
            notificationKey = sbn.key,
            packageName = sbn.packageName,
            appLabel = appLabel(sbn.packageName),
            title = title,
            text = text,
            category = notification.category,
            postedAt = sbn.postTime,
            isActive = active,
            extractionsJson = if (extractions.isEmpty()) null else json.encodeToString(extractionList, extractions),
        )
    }

    /** MessagingStyle: "Alex: running late" for the newest message. */
    private fun lastMessage(extras: android.os.Bundle): CharSequence? {
        val messages: Array<Parcelable>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getParcelableArray(Notification.EXTRA_MESSAGES, Parcelable::class.java)
        } else {
            @Suppress("DEPRECATION")
            extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        }
        val last = messages?.lastOrNull() as? android.os.Bundle ?: return null
        val text = last.getCharSequence("text") ?: return null
        val sender = last.getCharSequence("sender")
        return if (sender.isNullOrBlank()) text else "$sender: $text"
    }

    private fun appLabel(packageName: String): String = runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getApplicationInfo(packageName, 0)
        }
        pm.getApplicationLabel(info).toString()
    }.getOrDefault(packageName)

    private fun decode(raw: String?): List<SmartExtraction> =
        raw?.let { runCatching { json.decodeFromString(extractionList, it) }.getOrNull() }.orEmpty()

    private fun NotificationDigestEntity.toDomain() = NotificationDigestItem(
        key = notificationKey,
        packageName = packageName,
        appLabel = appLabel,
        title = title,
        text = text,
        postedAtMillis = postedAt,
        isActive = isActive,
        extractions = decode(extractionsJson),
    )

    private fun maxAge(extraction: SmartExtraction): Long = when (extraction) {
        is SmartExtraction.Otp -> OTP_PILL_MILLIS
        is SmartExtraction.MeetingLink -> MEETING_PILL_MILLIS
        is SmartExtraction.TrackingNumber -> RETENTION_MILLIS
    }

    companion object {
        const val RETENTION_MILLIS = 24L * 3_600_000
        private const val OTP_PILL_MILLIS = 15L * 60_000
        private const val MEETING_PILL_MILLIS = 12L * 3_600_000
        private const val CLOCK_TICK_MILLIS = 60_000L
        private const val MAX_PILLS = 3
        private const val MAX_TITLE = 200
        private const val MAX_TEXT = 600
        private val SKIPPED_CATEGORIES = setOf(
            Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_SERVICE, Notification.CATEGORY_PROGRESS,
            Notification.CATEGORY_CALL, Notification.CATEGORY_SYSTEM, Notification.CATEGORY_NAVIGATION,
            Notification.CATEGORY_STATUS,
        )
    }
}
