package com.ailauncher.data.actions

import com.ailauncher.domain.usecase.FreeSlots
import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.ailauncher.domain.action.ActionResult
import com.ailauncher.domain.ai.PlannerAction
import com.ailauncher.domain.ai.TodoAction
import com.ailauncher.domain.ai.ToolCommand
import com.ailauncher.domain.model.ContactActionType
import com.ailauncher.domain.model.FileAccessLevel
import com.ailauncher.domain.model.FileActivityMode
import com.ailauncher.domain.model.FileEntry
import com.ailauncher.domain.model.FileTypeFilter
import com.ailauncher.domain.model.PlannerBlock
import com.ailauncher.domain.model.TodoItem
import com.ailauncher.domain.model.TodoPriority
import com.ailauncher.domain.model.UnusedApp
import com.ailauncher.domain.repository.AppRepository
import com.ailauncher.domain.repository.AppUsageRepository
import com.ailauncher.domain.repository.ContactRepository
import com.ailauncher.domain.repository.FileRepository
import com.ailauncher.domain.repository.NotificationDigestRepository
import com.ailauncher.domain.repository.PlannerRepository
import com.ailauncher.domain.repository.SettingsRepository
import com.ailauncher.domain.repository.TodoRepository
import com.ailauncher.domain.search.FuzzyMatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.ln
import kotlin.math.pow

/**
 * Executes the launcher-context tools: storage insights, to-dos, the planner, the notification
 * digest and contact actions. Every one of them reads or writes on-device data only.
 *
 * Two payloads come back: `data` (compact, fed to the model) and `display` (complete, for the
 * chat card's buttons). A missing permission is reported as `display.needs`, so the card can offer
 * a "Grant" button instead of the model guessing what went wrong.
 */
@Singleton
class LauncherToolExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    private val usage: AppUsageRepository,
    private val files: FileRepository,
    private val todos: TodoRepository,
    private val planner: PlannerRepository,
    private val notifications: NotificationDigestRepository,
    private val contacts: ContactRepository,
    private val settings: SettingsRepository,
    private val agenda: AgendaProvider,
) {
    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val isoDate = DateTimeFormatter.ISO_LOCAL_DATE
    private val hhmm = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    // ===================================================================================== apps

    suspend fun listUnusedApps(c: ToolCommand.ListUnusedApps): ActionResult {
        val days = c.inactiveDays ?: settings.launcherSettings.first().unusedAppThresholdDays
        val now = System.currentTimeMillis()
        val cutoff = now - days * DAY_MILLIS
        val lastUsed = usage.lastUsedTimes(now - maxOf(days * 2L, LOOKBACK_DAYS) * DAY_MILLIS)
        val hasUsageAccess = usage.hasUsageAccess()
        val candidates = appRepository.apps.first()
            .filter { !it.isHidden && !it.isSystemApp }
            .distinctBy { it.packageName }
            .mapNotNull { app ->
                val last = lastUsed[app.packageName]
                // Never-used apps count from their install date.
                val reference = last ?: app.installedAt
                if (reference > 0 && reference < cutoff) {
                    UnusedApp(app.componentKey, app.packageName, app.label, last, null, app.installedAt)
                } else {
                    null
                }
            }
            .sortedBy { it.lastUsedAtMillis ?: 0L }
            .take(MAX_DISPLAY)
            .map { it.copy(sizeBytes = usage.appSizeBytes(it.packageName)) }

        val message = when {
            candidates.isEmpty() -> "No apps unused for $days days"
            else -> "${candidates.size} app${if (candidates.size == 1) "" else "s"} unused for $days+ days"
        } + if (!hasUsageAccess) " (from launches in this launcher; allow Usage access to include every app)" else ""
        val data = buildJsonObject {
            put("inactive_days", days)
            put("usage_access", hasUsageAccess)
            put("count", candidates.size)
            putJsonArray("apps") {
                candidates.take(MAX_FOR_MODEL).forEach { app ->
                    add(
                        buildJsonObject {
                            put("name", app.label)
                            put("last_used", app.lastUsedAtMillis?.let { date(it) } ?: "never")
                            app.sizeBytes?.let { put("size", formatBytes(it)) }
                        },
                    )
                }
            }
        }
        val display = buildJsonObject {
            put("kind", "unused_apps")
            put("inactive_days", days)
            if (!hasUsageAccess) put("needs", NEEDS_USAGE_ACCESS)
            putJsonArray("apps") {
                candidates.forEach { app ->
                    add(
                        buildJsonObject {
                            put("componentKey", app.componentKey)
                            put("packageName", app.packageName)
                            put("label", app.label)
                            app.lastUsedAtMillis?.let { put("lastUsedAt", it) }
                            app.sizeBytes?.let { put("sizeBytes", it) }
                            put("installedAt", app.installedAtMillis)
                        },
                    )
                }
            }
        }
        return ActionResult.ok(message, data, display)
    }

    // ==================================================================================== files

    suspend fun findLargestFiles(c: ToolCommand.FindLargestFiles): ActionResult {
        val result = files.largest(c.limit, c.fileType)
        if (result.accessLevel == FileAccessLevel.NONE) return needsFileAccess()
        val total = result.files.sumOf { it.sizeBytes }
        val message = if (result.files.isEmpty()) {
            "No ${c.fileType.displayName} found"
        } else {
            "${result.files.size} largest ${c.fileType.displayName} · ${formatBytes(total)} in total"
        }
        return ActionResult.ok(message, filesData(result.files, result.accessLevel, c.fileType), filesDisplay("largest_files", result.files, result.accessLevel))
    }

    suspend fun queryFiles(c: ToolCommand.QueryFilesByActivity): ActionResult {
        val now = System.currentTimeMillis()
        val (found, level) = when (c.mode) {
            FileActivityMode.UNUSED_SINCE -> {
                val days = c.unusedDays ?: settings.launcherSettings.first().staleFileThresholdDays
                val result = files.notModifiedSince(now - days * DAY_MILLIS, c.fileType, MAX_DISPLAY)
                result.files to result.accessLevel
            }
            FileActivityMode.DOWNLOADED -> files.downloadedBetween(startOf(c.startDate), endOf(c.endDate), c.fileType, MAX_DISPLAY)
                .let { it.files to it.accessLevel }
            FileActivityMode.MODIFIED_RANGE -> files.modifiedBetween(startOf(c.startDate), endOf(c.endDate), c.fileType, MAX_DISPLAY)
                .let { it.files to it.accessLevel }
            // The launcher's own log needs no storage permission.
            FileActivityMode.OPENED_OR_CLICKED ->
                files.openedBetween(startOf(c.startDate), endOf(c.endDate), c.fileType, MAX_DISPLAY) to files.accessLevel()
        }
        if (c.mode != FileActivityMode.OPENED_OR_CLICKED && level == FileAccessLevel.NONE) return needsFileAccess()
        val what = c.fileType.displayName
        val message = when (c.mode) {
            FileActivityMode.UNUSED_SINCE -> "${found.size} $what untouched for ${c.unusedDays ?: settings.launcherSettings.first().staleFileThresholdDays}+ days"
            FileActivityMode.DOWNLOADED -> "${found.size} $what downloaded ${rangeText(c)}"
            FileActivityMode.OPENED_OR_CLICKED -> "${found.size} $what opened ${rangeText(c)}"
            FileActivityMode.MODIFIED_RANGE -> "${found.size} $what changed ${rangeText(c)}"
        }
        return ActionResult.ok(message, filesData(found, level, c.fileType), filesDisplay("file_query", found, level))
    }

    private fun startOf(date: LocalDate?): Long = (date ?: LocalDate.now(zone).minusDays(6)).atStartOfDay(zone).toInstant().toEpochMilli()
    private fun endOf(date: LocalDate?): Long = (date ?: LocalDate.now(zone)).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun rangeText(c: ToolCommand.QueryFilesByActivity): String {
        val start = c.startDate ?: return ""
        val end = c.endDate ?: start
        return if (start == end) "on $start" else "from $start to $end"
    }

    private fun filesData(found: List<FileEntry>, level: FileAccessLevel, type: FileTypeFilter): JsonObject = buildJsonObject {
        put("count", found.size)
        put("total_size", formatBytes(found.sumOf { it.sizeBytes }))
        if (level == FileAccessLevel.MEDIA_ONLY && type in setOf(FileTypeFilter.ALL, FileTypeFilter.DOCUMENT, FileTypeFilter.ARCHIVE)) {
            put("note", "Only photos, videos and audio are visible; documents and archives need All files access.")
        }
        putJsonArray("files") {
            found.take(MAX_FOR_MODEL).forEach { f ->
                add(
                    buildJsonObject {
                        put("name", f.name.take(60))
                        put("size", formatBytes(f.sizeBytes))
                        (f.lastOpenedAtMillis ?: f.modifiedAtMillis)?.let { put("date", date(it)) }
                        f.relativePath?.let { put("folder", it.take(40)) }
                    },
                )
            }
        }
    }

    private fun filesDisplay(kind: String, found: List<FileEntry>, level: FileAccessLevel): JsonObject = buildJsonObject {
        put("kind", kind)
        put("access", level.name)
        if (level == FileAccessLevel.MEDIA_ONLY) put("needs", NEEDS_ALL_FILES)
        putJsonArray("files") {
            found.forEach { f ->
                add(
                    buildJsonObject {
                        put("uri", f.uri)
                        put("name", f.name)
                        f.mimeType?.let { put("mime", it) }
                        put("sizeBytes", f.sizeBytes)
                        f.modifiedAtMillis?.let { put("modifiedAt", it) }
                        f.addedAtMillis?.let { put("addedAt", it) }
                        f.lastOpenedAtMillis?.let { put("openedAt", it) }
                        f.relativePath?.let { put("path", it) }
                    },
                )
            }
        }
    }

    private fun needsFileAccess() = ActionResult(
        success = false,
        message = "Allow access to your photos, videos and audio to search files.",
        display = buildJsonObject { put("needs", NEEDS_MEDIA) },
    )

    // ==================================================================================== to-dos

    suspend fun manageTodo(c: ToolCommand.ManageTodo): ActionResult = when (c.action) {
        TodoAction.ADD -> {
            val item = todos.add(c.title.orEmpty(), null, c.listName ?: TodoItem.DEFAULT_LIST, c.priority ?: TodoPriority.MEDIUM, c.dueAtMillis)
            ActionResult.ok(
                "Added “${item.title}” to ${item.listName}",
                buildJsonObject {
                    put("status", "added")
                    put("id", item.id)
                    put("title", item.title)
                    put("list", item.listName)
                },
                buildJsonObject { put("kind", "todo"); put("todoId", item.id) },
            )
        }
        TodoAction.COMPLETE, TodoAction.DELETE -> {
            val target = findTodo(c.todoId, c.title, includeCompleted = c.action == TodoAction.DELETE)
            when {
                target == null -> ActionResult.fail("I couldn't find a task called “${c.title ?: c.todoId}”.")
                c.action == TodoAction.COMPLETE -> {
                    todos.setCompleted(target.id, true)
                    ActionResult.ok("Marked “${target.title}” as done", buildJsonObject { put("status", "completed"); put("title", target.title) })
                }
                else -> {
                    todos.delete(target.id)
                    ActionResult.ok("Deleted “${target.title}”", buildJsonObject { put("status", "deleted"); put("title", target.title) })
                }
            }
        }
        TodoAction.LIST -> {
            val pending = todos.all().filter { !it.completed }
                .filter { c.listName == null || it.listName.equals(c.listName, ignoreCase = true) }
            val message = when {
                pending.isEmpty() -> c.listName?.let { "Nothing on your $it list" } ?: "No open to-dos"
                else -> "${pending.size} open to-do${if (pending.size == 1) "" else "s"}" + (c.listName?.let { " on $it" } ?: "")
            }
            val data = buildJsonObject {
                put("count", pending.size)
                putJsonArray("todos") {
                    pending.take(MAX_TODOS_FOR_MODEL).forEach { t ->
                        add(
                            buildJsonObject {
                                put("id", t.id)
                                put("title", t.title)
                                put("list", t.listName)
                                if (t.priority == TodoPriority.HIGH) put("priority", "high")
                                t.dueAtMillis?.let { put("due", dateTime(it)) }
                            },
                        )
                    }
                }
            }
            ActionResult.ok(message, data, buildJsonObject { put("kind", "todo_list"); c.listName?.let { put("list", it) } })
        }
    }

    private suspend fun findTodo(id: Long?, title: String?, includeCompleted: Boolean): TodoItem? {
        id?.let { todos.get(it) }?.let { return it }
        val query = title?.takeIf { it.isNotBlank() } ?: return null
        val pool = todos.all().filter { includeCompleted || !it.completed }
        pool.firstOrNull { it.title.equals(query, ignoreCase = true) }?.let { return it }
        val best = FuzzyMatcher.rank(query, pool) { listOf(it.title) }.firstOrNull() ?: return null
        return best.first.takeIf { best.second >= FuzzyMatcher.CONFIDENT_MATCH * 0.8 }
    }

    // ================================================================================== planner

    suspend fun managePlanner(c: ToolCommand.ManagePlanner): ActionResult {
        val today = LocalDate.now(zone)
        return when (c.action) {
            PlannerAction.SCHEDULE_BLOCK -> {
                val date = c.date ?: today
                val block = planner.schedule(date, c.startMinutes ?: 9 * 60, c.durationMinutes ?: 60, c.title.orEmpty(), null)
                val clash = planner.blocksOn(date).firstOrNull { it.id != block.id && it.overlaps(block) }
                ActionResult.ok(
                    "Planned “${block.title}” ${label(block)}" + (clash?.let { " (overlaps “${it.title}”)" } ?: ""),
                    buildJsonObject {
                        put("status", "scheduled")
                        put("id", block.id)
                        put("date", date.format(isoDate))
                        put("time", timeRange(block))
                        put("title", block.title)
                        clash?.let { put("overlaps", it.title) }
                    },
                    buildJsonObject { put("kind", "planner"); put("date", date.format(isoDate)) },
                )
            }
            PlannerAction.RESCHEDULE -> {
                val block = findBlock(c.blockId, c.title, c.date ?: today)
                    ?: return ActionResult.fail("I couldn't find a block called “${c.title ?: c.blockId}”.")
                val moved = block.copy(
                    date = c.date ?: block.date,
                    startMinutes = c.startMinutes ?: block.startMinutes,
                    durationMinutes = c.durationMinutes ?: block.durationMinutes,
                )
                if (moved.endMinutes > 24 * 60) return ActionResult.fail("That would run past midnight.")
                planner.update(moved)
                ActionResult.ok(
                    "Moved “${moved.title}” to ${label(moved)}",
                    buildJsonObject { put("status", "rescheduled"); put("date", moved.date.format(isoDate)); put("time", timeRange(moved)); put("title", moved.title) },
                    buildJsonObject { put("kind", "planner"); put("date", moved.date.format(isoDate)) },
                )
            }
            PlannerAction.REMOVE_BLOCK -> {
                val block = findBlock(c.blockId, c.title, c.date ?: today)
                    ?: return ActionResult.fail("I couldn't find a block called “${c.title ?: c.blockId}”.")
                planner.remove(block.id)
                ActionResult.ok("Removed “${block.title}” from ${block.date}", buildJsonObject { put("status", "removed"); put("title", block.title) })
            }
            PlannerAction.GET_DAY_PLAN -> {
                val date = c.date ?: today
                val blocks = planner.blocksOn(date)
                val events = agenda.eventsOn(date)
                val busy = blocks.map { it.startMinutes to it.endMinutes } + events.filterNot { it.allDay }.map { e ->
                    minutesOf(e.startMillis) to minutesOf(e.endMillis).let { if (it <= minutesOf(e.startMillis)) 24 * 60 else it }
                }
                val free = FreeSlots.between(busy)
                val data = buildJsonObject {
                    put("date", date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)))
                    putJsonArray("blocks") {
                        blocks.forEach { b ->
                            add(buildJsonObject { put("id", b.id); put("time", timeRange(b)); put("title", b.title); if (b.completed) put("done", true) })
                        }
                    }
                    putJsonArray("events") {
                        events.take(10).forEach { e ->
                            add(buildJsonObject { put("title", e.title); if (e.allDay) put("all_day", true) else put("time", "${hm(e.startMillis)}-${hm(e.endMillis)}") })
                        }
                    }
                    putJsonArray("free") { free.forEach { (s, e) -> add(kotlinx.serialization.json.JsonPrimitive("${clock(s)}-${clock(e)}")) } }
                }
                val message = if (blocks.isEmpty()) "Nothing planned for ${dayWord(date)}" else "${blocks.size} block${if (blocks.size == 1) "" else "s"} planned for ${dayWord(date)}"
                ActionResult.ok(message, data, buildJsonObject { put("kind", "planner"); put("date", date.format(isoDate)) })
            }
        }
    }

    private suspend fun findBlock(id: Long?, title: String?, near: LocalDate): PlannerBlock? {
        id?.let { planner.get(it) }?.let { return it }
        val query = title?.takeIf { it.isNotBlank() } ?: return null
        val pool = planner.blocksBetween(near.minusDays(1), near.plusDays(7))
        val ranked = FuzzyMatcher.rank(query, pool) { listOf(it.title) }
        // Prefer the match on the requested day, then the soonest one.
        return ranked.filter { it.second >= FuzzyMatcher.CONFIDENT_MATCH * 0.8 }
            .sortedWith(compareBy({ it.first.date != near }, { it.first.date }, { -it.second }))
            .firstOrNull()?.first
    }

    private fun minutesOf(millis: Long): Int = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime().let { it.hour * 60 + it.minute }
    private fun clock(minutes: Int) = LocalTime.of(minutes / 60 % 24, minutes % 60).format(hhmm)
    private fun hm(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).format(hhmm)
    private fun timeRange(b: PlannerBlock) = "${clock(b.startMinutes)}-${clock(b.endMinutes)}"
    private fun label(b: PlannerBlock) = "${dayWord(b.date)} ${timeRange(b)}"

    private fun dayWord(date: LocalDate): String {
        val today = LocalDate.now(zone)
        return when (date) {
            today -> "today"
            today.plusDays(1) -> "tomorrow"
            else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
        }
    }

    // ============================================================================ notifications

    suspend fun summarizeNotifications(c: ToolCommand.SummarizeNotifications): ActionResult {
        val enabled = settings.launcherSettings.first().notificationDigestEnabled
        if (!enabled || !notifications.hasListenerAccess()) {
            return ActionResult(
                success = false,
                message = "Turn on the notification digest in Settings ▸ Notifications first. It stays on this phone.",
                display = buildJsonObject { put("needs", NEEDS_NOTIFICATION_ACCESS) },
            )
        }
        var items = notifications.recent(System.currentTimeMillis() - DAY_MILLIS)
        c.appName?.let { app ->
            val wanted = FuzzyMatcher.normalize(app)
            items = items.filter { FuzzyMatcher.normalize(it.appLabel).contains(wanted) || FuzzyMatcher.normalize(it.packageName).contains(wanted) }
        }
        val apps = items.map { it.appLabel }.distinct()
        val message = if (items.isEmpty()) "No notifications in the last 24 hours" + (c.appName?.let { " from $it" } ?: "")
        else "${items.size} notification${if (items.size == 1) "" else "s"} from ${apps.size} app${if (apps.size == 1) "" else "s"}"
        val data = buildJsonObject {
            put("count", items.size)
            putJsonArray("notifications") {
                items.take(MAX_NOTIFICATIONS_FOR_MODEL).forEach { n ->
                    add(
                        buildJsonObject {
                            put("app", n.appLabel)
                            n.title?.let { put("title", it.take(60)) }
                            n.text?.let { put("text", it.replace('\n', ' ').take(140)) }
                            put("time", hm(n.postedAtMillis))
                        },
                    )
                }
            }
        }
        return ActionResult.ok(message, data, buildJsonObject { put("kind", "notifications") })
    }

    // ================================================================================= contacts

    suspend fun contactAction(c: ToolCommand.ContactAction): ActionResult {
        if (!contacts.hasPermission()) {
            return ActionResult(
                success = false,
                message = "Allow contacts access so I can find ${c.contactName}.",
                display = buildJsonObject { put("needs", NEEDS_CONTACTS) },
            )
        }
        val matches = contacts.search(c.contactName, limit = 3)
        val match = matches.firstOrNull() ?: return ActionResult.fail("I couldn't find ${c.contactName} in your contacts.")
        val ambiguous = matches.size > 1 && matches[1].displayName.equals(c.contactName, ignoreCase = true) &&
            matches[0].displayName.equals(c.contactName, ignoreCase = true)
        if (ambiguous) return ActionResult.fail("Which ${c.contactName}: ${matches.joinToString(" or ") { it.displayName + " (" + it.phoneNumber + ")" }}?")
        val number = match.phoneNumber
        val digits = number.filter { it.isDigit() || it == '+' }
        val started = withContext(Dispatchers.Main) {
            when (c.action) {
                ContactActionType.CALL -> {
                    val canCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
                    start(Intent(if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.fromParts("tel", digits, null)))
                }
                ContactActionType.SMS -> start(
                    Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", digits, null)).apply { c.message?.let { putExtra("sms_body", it) } },
                )
                ContactActionType.WHATSAPP -> {
                    val url = "https://api.whatsapp.com/send?phone=${digits.removePrefix("+")}" +
                        (c.message?.let { "&text=" + Uri.encode(it) } ?: "")
                    WHATSAPP_PACKAGES.any { pkg -> start(Intent(Intent.ACTION_VIEW, url.toUri()).setPackage(pkg)) }
                }
            }
        }
        if (!started) {
            return ActionResult.fail(
                when (c.action) {
                    ContactActionType.WHATSAPP -> "WhatsApp isn't installed."
                    ContactActionType.SMS -> "No messaging app can send texts."
                    ContactActionType.CALL -> "This phone can't place calls."
                },
            )
        }
        val verb = when (c.action) {
            ContactActionType.CALL -> "Calling"
            ContactActionType.SMS -> "Texting"
            ContactActionType.WHATSAPP -> "Opening WhatsApp with"
        }
        return ActionResult.ok(
            "$verb ${match.displayName} (${match.phoneLabel ?: "phone"})",
            buildJsonObject { put("status", "started"); put("contact", match.displayName) },
        )
    }

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    // =================================================================================== format

    private fun date(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().format(isoDate)
    private fun dateTime(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).let { "${it.toLocalDate().format(isoDate)} ${it.format(hhmm)}" }

    companion object {
        const val NEEDS_USAGE_ACCESS = "usage_access"
        const val NEEDS_MEDIA = "media"
        const val NEEDS_ALL_FILES = "all_files"
        const val NEEDS_NOTIFICATION_ACCESS = "notification_access"
        const val NEEDS_CONTACTS = "contacts"

        private const val DAY_MILLIS = 86_400_000L
        private const val LOOKBACK_DAYS = 400L
        private const val MAX_DISPLAY = 30
        private const val MAX_FOR_MODEL = 10
        private const val MAX_TODOS_FOR_MODEL = 20
        private const val MAX_NOTIFICATIONS_FOR_MODEL = 20
        private val WHATSAPP_PACKAGES = listOf("com.whatsapp", "com.whatsapp.w4b")

        /** 1 decimal, binary units, locale-independent (the model reads it). */
        fun formatBytes(bytes: Long): String {
            if (bytes < 1024) return "$bytes B"
            val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceAtMost(4)
            val value = bytes / 1024.0.pow(exp)
            return String.format(Locale.ROOT, "%.1f %s", value, "KMGT"[exp - 1] + "B")
        }
    }
}
