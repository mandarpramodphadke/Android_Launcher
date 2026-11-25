package com.ailauncher.data.calendar

import android.content.Context
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ailauncher.data.local.dao.CalendarFeedDao
import com.ailauncher.data.local.entity.CalendarFeedEntity
import com.ailauncher.data.local.entity.FeedEventEntity
import com.ailauncher.data.reminders.CalendarDataSource
import com.ailauncher.domain.calendar.IcsParser
import com.ailauncher.domain.model.AgendaEvent
import com.ailauncher.domain.model.CalendarFeed
import com.ailauncher.domain.model.DeviceCalendar
import com.ailauncher.domain.repository.CalendarSourcesRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device calendars come from Android's calendar storage. Subscribed calendars are downloaded from
 * the link the user entered (nothing is sent but the request for that address); imported files
 * are copied into the app. Their events are expanded and stored in Room, and re-read every six
 * hours while a subscription exists.
 */
@Singleton
class CalendarSourcesRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: CalendarFeedDao,
    private val deviceCalendars: CalendarDataSource,
    private val http: OkHttpClient,
) : CalendarSourcesRepository {

    private val mutex = Mutex()
    private val fileDir get() = File(context.filesDir, "calendars").apply { mkdirs() }

    override fun canReadDeviceCalendars(): Boolean = deviceCalendars.canRead()

    override suspend fun deviceCalendars(): List<DeviceCalendar> = withContext(Dispatchers.IO) { deviceCalendars.calendars() }

    override val feeds: Flow<List<CalendarFeed>> = dao.observe().map { rows -> rows.map { it.toDomain() } }

    override suspend fun subscribe(name: String, url: String): Result<CalendarFeed> = withContext(Dispatchers.IO) {
        val link = normalizeLink(url) ?: return@withContext Result.failure(IllegalArgumentException("Enter a calendar link that starts with https:// or webcal://"))
        runCatching {
            val text = download(link)
            val feedName = name.trim().ifEmpty { calendarName(text) ?: link.toUri().host ?: "Calendar" }
            mutex.withLock {
                val id = dao.insert(CalendarFeedEntity(name = feedName.take(60), url = link, colorArgb = nextColor(), lastSyncedAt = null, lastError = null, eventCount = 0, createdAt = now()))
                store(dao.get(id)!!, text).toDomain()
            }.also { schedulePeriodicRefresh() }
        }
    }

    override suspend fun importFile(name: String?, uri: String): Result<CalendarFeed> = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri.toUri())?.use { readLimited(it.readAtMost(MAX_BYTES)) }
                ?: throw IOException("That file couldn't be opened")
            if (!text.contains("BEGIN:VCALENDAR", ignoreCase = true)) throw IOException("That file isn't a calendar (.ics)")
            mutex.withLock {
                val feedName = name?.trim()?.takeIf { it.isNotEmpty() } ?: calendarName(text) ?: "Imported calendar"
                val id = dao.insert(CalendarFeedEntity(name = feedName.take(60), url = null, colorArgb = nextColor(), lastSyncedAt = null, lastError = null, eventCount = 0, createdAt = now()))
                File(fileDir, "$id.ics").writeText(text)
                store(dao.get(id)!!, text).toDomain()
            }
        }
    }

    override suspend fun refresh(feedId: Long): Result<CalendarFeed> = withContext(Dispatchers.IO) {
        val feed = dao.get(feedId) ?: return@withContext Result.failure(IllegalStateException("That calendar was removed"))
        runCatching {
            val text = feed.url?.let { download(it) } ?: File(fileDir, "${feed.id}.ics").readText()
            mutex.withLock { store(feed, text).toDomain() }
        }.onFailure { e ->
            dao.update(feed.copy(lastError = e.message ?: "Couldn't update"))
        }
    }

    override suspend fun refreshAll() {
        dao.all().forEach { refresh(it.id) }
    }

    override suspend fun remove(feedId: Long) = withContext(Dispatchers.IO) {
        mutex.withLock {
            dao.delete(feedId)
            File(fileDir, "$feedId.ics").delete()
        }
        if (dao.all().none { it.url != null }) runCatching { WorkManager.getInstance(context).cancelUniqueWork(CalendarFeedSyncWorker.NAME) }
    }

    override suspend fun feedEventsBetween(startMillis: Long, endMillis: Long): List<AgendaEvent> = withContext(Dispatchers.IO) {
        dao.eventsBetween(startMillis, endMillis).map { AgendaEvent(it.title, it.startMillis, it.endMillis, it.allDay, it.location) }
    }

    /** Parses [text] and replaces the feed's stored events (from 60 days ago to about a year ahead). */
    private suspend fun store(feed: CalendarFeedEntity, text: String): CalendarFeedEntity {
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val occurrences = IcsParser.occurrences(IcsParser.parse(text), now.minusSeconds(60L * DAY_SECONDS), now.plusSeconds(400L * DAY_SECONDS), zone, MAX_EVENTS)
        val updated = feed.copy(lastSyncedAt = now(), lastError = null, eventCount = occurrences.size)
        dao.replaceEvents(
            updated,
            occurrences.map { FeedEventEntity(feedId = feed.id, title = it.title.take(200), location = it.location?.take(200), startMillis = it.startMillis, endMillis = it.endMillis, allDay = it.allDay) },
        )
        return updated
    }

    private fun download(link: String): String {
        val request = Request.Builder().url(link).header("Accept", "text/calendar, */*;q=0.5").build()
        val client = http.newBuilder().callTimeout(30, TimeUnit.SECONDS).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("The calendar server answered ${response.code}")
            val text = readLimited(response.body.byteStream().use { it.readAtMost(MAX_BYTES) })
            if (!text.contains("BEGIN:VCALENDAR", ignoreCase = true)) throw IOException("That link isn't a calendar (.ics)")
            return text
        }
    }

    private fun readLimited(bytes: ByteArray): String = bytes.toString(Charsets.UTF_8)

    /** Reads at most [limit] bytes; a bigger calendar is refused rather than filling memory. */
    private fun InputStream.readAtMost(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            if (out.size() + n > limit) throw IOException("That calendar is too big (over 8 MB)")
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private suspend fun nextColor(): Int = COLORS[dao.all().size % COLORS.size]

    private fun schedulePeriodicRefresh() {
        val request = PeriodicWorkRequestBuilder<CalendarFeedSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        runCatching { WorkManager.getInstance(context).enqueueUniquePeriodicWork(CalendarFeedSyncWorker.NAME, ExistingPeriodicWorkPolicy.KEEP, request) }
    }

    private fun now() = System.currentTimeMillis()

    private fun CalendarFeedEntity.toDomain() = CalendarFeed(id, name, url, colorArgb, lastSyncedAt, lastError, eventCount)

    companion object {
        private const val MAX_BYTES = 8 * 1024 * 1024
        private const val MAX_EVENTS = 5_000
        private const val DAY_SECONDS = 86_400L
        private val COLORS = intArrayOf(0xFF3F51B5.toInt(), 0xFF009688.toInt(), 0xFFE91E63.toInt(), 0xFFFF9800.toInt(), 0xFF8BC34A.toInt(), 0xFF9C27B0.toInt())

        /** https and webcal links only ("webcal://" is the same address over https). */
        fun normalizeLink(url: String): String? {
            val trimmed = url.trim()
            val https = when {
                trimmed.startsWith("webcals://", ignoreCase = true) -> "https://" + trimmed.substring(10)
                trimmed.startsWith("webcal://", ignoreCase = true) -> "https://" + trimmed.substring(9)
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
                else -> return null
            }
            return https.takeIf { it.length > 8 && !it.contains(' ') }
        }

        /** The calendar's own name (X-WR-CALNAME), if it has one. */
        fun calendarName(text: String): String? = text.lineSequence()
            .firstOrNull { it.startsWith("X-WR-CALNAME", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.takeIf { it.isNotEmpty() }
    }
}
