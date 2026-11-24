package com.ailauncher.data.actions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import com.ailauncher.domain.action.ActionExecutor
import com.ailauncher.domain.action.ActionResult
import com.ailauncher.domain.action.LauncherDestination
import com.ailauncher.domain.action.LauncherNavigator
import com.ailauncher.domain.ai.DeviceSetting
import com.ailauncher.domain.ai.ToolCommand
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.CategorySource
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.domain.repository.FolderRepository
import com.ailauncher.domain.repository.AppRepository
import com.ailauncher.domain.repository.CategorizationScheduler
import com.ailauncher.domain.repository.ReminderRepository
import com.ailauncher.domain.repository.ReminderScheduleResult
import com.ailauncher.domain.search.FuzzyMatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

/** Maps validated [ToolCommand]s onto Android system APIs. */
@Singleton
class AndroidActionExecutor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appRepository: AppRepository,
    private val reminderRepository: ReminderRepository,
    private val categorizationScheduler: CategorizationScheduler,
    private val deviceSettings: DeviceSettingsController,
    private val agendaProvider: AgendaProvider,
    private val launcherTools: LauncherToolExecutor,
    private val folderRepository: FolderRepository,
    private val navigator: LauncherNavigator,
) : ActionExecutor {

    override suspend fun execute(command: ToolCommand): ActionResult = when (command) {
        is ToolCommand.SetAlarm -> setAlarm(command)
        is ToolCommand.SetTimer -> setTimer(command)
        is ToolCommand.CreateReminder -> createReminder(command)
        is ToolCommand.LaunchApp -> launchApp(command)
        is ToolCommand.ToggleSetting -> toggle(command)
        ToolCommand.SortApps -> sortApps()
        ToolCommand.GetTodayAgenda -> agenda()
        is ToolCommand.ListUnusedApps -> launcherTools.listUnusedApps(command)
        is ToolCommand.FindLargestFiles -> launcherTools.findLargestFiles(command)
        is ToolCommand.QueryFilesByActivity -> launcherTools.queryFiles(command)
        is ToolCommand.ManageTodo -> launcherTools.manageTodo(command)
        is ToolCommand.ManagePlanner -> launcherTools.managePlanner(command)
        is ToolCommand.SummarizeNotifications -> launcherTools.summarizeNotifications(command)
        is ToolCommand.ContactAction -> launcherTools.contactAction(command)
    }

    private suspend fun setAlarm(c: ToolCommand.SetAlarm): ActionResult {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, c.hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, c.minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        c.label?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        val days = c.days.filter { it in Calendar.SUNDAY..Calendar.SATURDAY }
        if (days.isNotEmpty()) intent.putIntegerArrayListExtra(AlarmClock.EXTRA_DAYS, ArrayList(days))
        val time = java.time.LocalTime.of(c.hour, c.minute).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        return if (start(intent)) ActionResult.ok("Alarm set for $time") else ActionResult.fail("No clock app on this phone can set alarms.")
    }

    private suspend fun setTimer(c: ToolCommand.SetTimer): ActionResult {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, c.seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        c.label?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
        val minutes = c.seconds / 60
        val seconds = c.seconds % 60
        val label = when {
            minutes == 0 -> "$seconds s"
            seconds == 0 -> "$minutes min"
            else -> "$minutes min $seconds s"
        }
        return if (start(intent)) ActionResult.ok("Timer started for $label") else ActionResult.fail("No clock app on this phone can start timers.")
    }

    private suspend fun createReminder(c: ToolCommand.CreateReminder): ActionResult {
        val at = Instant.ofEpochMilli(c.timestampMillis).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
        return when (val result = reminderRepository.schedule(c.title, c.timestampMillis)) {
            is ReminderScheduleResult.InCalendar -> ActionResult.ok("Added to your calendar for $at")
            is ReminderScheduleResult.Local -> ActionResult.ok(
                if (result.exact) "I'll remind you at $at" else "I'll remind you around $at (allow \"Alarms & reminders\" for exact timing)",
            )
        }
    }

    private suspend fun launchApp(c: ToolCommand.LaunchApp): ActionResult {
        // "open my Work folder" names a custom folder; a plain name prefers an app, then a folder.
        val folder = folderRepository.findByName(c.appName)
        if (folder != null && c.appName.contains("folder", ignoreCase = true)) return openFolder(folder)
        val apps = appRepository.apps.first().filter { !it.isHidden }
        val ranked = FuzzyMatcher.rank(c.appName, apps) { app -> listOf(app.label, app.systemLabel) + app.aliases }
        val best = ranked.firstOrNull()
        if (best == null || best.second < FuzzyMatcher.CONFIDENT_MATCH) {
            if (folder != null) return openFolder(folder)
            // The launcher's own To-do & Daily Planner screen.
            if (PLANNER_NAME.containsMatchIn(c.appName)) {
                val todos = TODO_NAME.containsMatchIn(c.appName)
                navigator.navigate(if (todos) LauncherDestination.Todos else LauncherDestination.Planner)
                return ActionResult.ok(if (todos) "Opened your to-dos" else "Opened your planner")
            }
            val suggestions = ranked.take(3).joinToString { it.first.label }
            return ActionResult.fail(
                if (suggestions.isEmpty()) "I couldn't find an app called \"${c.appName}\"."
                else "I couldn't find \"${c.appName}\". Did you mean $suggestions?",
            )
        }
        val app: AppInfo = best.first
        // Per spec, prefer the package's launch intent, then the exact launcher activity.
        val launched = withContext(Dispatchers.Main) {
            context.packageManager.getLaunchIntentForPackage(app.packageName)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                ?.let { runCatching { context.startActivity(it) }.isSuccess } == true
        } || appRepository.launch(app)
        return if (launched) ActionResult.ok("Opening ${app.label}") else ActionResult.fail("${app.label} could not be opened.")
    }

    private fun openFolder(folder: CustomFolder): ActionResult {
        navigator.navigate(LauncherDestination.Folder(folder.id))
        return ActionResult.ok("Opened your ${folder.name} folder")
    }

    private fun toggle(c: ToolCommand.ToggleSetting): ActionResult = when (c.setting) {
        DeviceSetting.FLASHLIGHT -> when (val on = deviceSettings.setTorch(c.state)) {
            null -> ActionResult.fail("This phone has no flashlight.")
            else -> ActionResult.ok(if (on) "Flashlight on" else "Flashlight off")
        }
        DeviceSetting.WIFI ->
            if (deviceSettings.openInternetPanel()) ActionResult.ok("Opened the Wi-Fi panel — Android only lets you switch Wi-Fi yourself.")
            else ActionResult.fail("Couldn't open Wi-Fi settings.")
        DeviceSetting.BLUETOOTH ->
            if (deviceSettings.openBluetoothSettings()) ActionResult.ok("Opened Bluetooth settings.")
            else ActionResult.fail("Couldn't open Bluetooth settings.")
        DeviceSetting.DND -> when (val on = deviceSettings.setDoNotDisturb(c.state)) {
            null -> {
                deviceSettings.openDndAccessSettings()
                ActionResult.fail("Allow AI Launcher to control Do Not Disturb, then try again.")
            }
            else -> ActionResult.ok(if (on) "Do Not Disturb is on" else "Do Not Disturb is off")
        }
    }

    private suspend fun sortApps(): ActionResult {
        val apps = appRepository.apps.first().distinctBy { it.packageName }
        val pending = apps.count { it.categorySource == null || it.categorySource == CategorySource.HEURISTIC }
        if (pending == 0) {
            return ActionResult.ok("Your ${apps.size} apps are already organized. Long-press an app to change its category.")
        }
        categorizationScheduler.schedule(force = false)
        return ActionResult.ok("Organizing $pending apps in the background — check the Smart Categories tab.")
    }

    private suspend fun agenda(): ActionResult {
        val agenda = agendaProvider.today()
        return ActionResult.ok(agenda.summary, agenda.data)
    }

    private suspend fun start(intent: Intent): Boolean = withContext(Dispatchers.Main) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (intent.resolveActivity(context.packageManager) == null) return@withContext false
        try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private companion object {
        val PLANNER_NAME = Regex("""\b(planner|day plan|daily plan|schedule|agenda|to-?dos?|to-do list|task list|tasks)\b""", RegexOption.IGNORE_CASE)
        val TODO_NAME = Regex("""\b(to-?dos?|to-do list|task list|tasks)\b""", RegexOption.IGNORE_CASE)
    }
}
