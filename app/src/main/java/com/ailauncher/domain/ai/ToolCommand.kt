package com.ailauncher.domain.ai

import com.ailauncher.domain.model.ContactActionType
import com.ailauncher.domain.model.FileActivityMode
import com.ailauncher.domain.model.FileTypeFilter
import com.ailauncher.domain.model.TodoPriority
import java.time.LocalDate

/**
 * A validated, strongly typed action produced by [ToolRouter] from the model's raw JSON tool call.
 * Executed by [com.ailauncher.domain.action.ActionExecutor].
 */
sealed interface ToolCommand {
    /** Tool name as declared to the model. */
    val toolName: String

    /** Side-effect free tools run automatically and feed their result back to the model. */
    val isReadOnly: Boolean get() = false

    data class SetAlarm(
        val hour: Int,
        val minute: Int,
        val label: String?,
        /** java.util.Calendar day constants (SUNDAY = 1 … SATURDAY = 7); empty = one-shot alarm. */
        val days: List<Int>,
    ) : ToolCommand {
        override val toolName get() = ToolNames.SET_ALARM
    }

    data class SetTimer(val seconds: Int, val label: String?) : ToolCommand {
        override val toolName get() = ToolNames.SET_TIMER
    }

    data class CreateReminder(val title: String, val timestampMillis: Long) : ToolCommand {
        override val toolName get() = ToolNames.CREATE_REMINDER
    }

    data class LaunchApp(val appName: String) : ToolCommand {
        override val toolName get() = ToolNames.LAUNCH_APP
    }

    data class ToggleSetting(val setting: DeviceSetting, val state: ToggleState) : ToolCommand {
        override val toolName get() = ToolNames.TOGGLE_SETTING
    }

    data object SortApps : ToolCommand {
        override val toolName get() = ToolNames.SORT_APPS
    }

    data object GetTodayAgenda : ToolCommand {
        override val toolName get() = ToolNames.GET_TODAY_AGENDA
        override val isReadOnly get() = true
    }

    /** This phone's specs and status, and which on-device AI models suit it. */
    data object GetDeviceInfo : ToolCommand {
        override val toolName get() = ToolNames.GET_DEVICE_INFO
        override val isReadOnly get() = true
    }

    /** [inactiveDays] null = the user's default threshold from Settings. */
    data class ListUnusedApps(val inactiveDays: Int?) : ToolCommand {
        override val toolName get() = ToolNames.LIST_UNUSED_APPS
        override val isReadOnly get() = true
    }

    data class FindLargestFiles(val limit: Int, val fileType: FileTypeFilter) : ToolCommand {
        override val toolName get() = ToolNames.FIND_LARGEST_FILES
        override val isReadOnly get() = true
    }

    /**
     * Date filters are resolved to concrete days by the router. [unusedDays] null with
     * [FileActivityMode.UNUSED_SINCE] = the user's default stale-file threshold.
     */
    data class QueryFilesByActivity(
        val mode: FileActivityMode,
        val unusedDays: Int?,
        val startDate: LocalDate?,
        val endDate: LocalDate?,
        val fileType: FileTypeFilter,
    ) : ToolCommand {
        override val toolName get() = ToolNames.QUERY_FILES_BY_ACTIVITY
        override val isReadOnly get() = true
    }

    data class ManageTodo(
        val action: TodoAction,
        val title: String? = null,
        val listName: String? = null,
        val priority: TodoPriority? = null,
        val dueAtMillis: Long? = null,
        val todoId: Long? = null,
    ) : ToolCommand {
        override val toolName get() = ToolNames.MANAGE_TODO
        override val isReadOnly get() = action == TodoAction.LIST
    }

    data class ManagePlanner(
        val action: PlannerAction,
        val date: LocalDate? = null,
        /** Minutes after midnight. */
        val startMinutes: Int? = null,
        val durationMinutes: Int? = null,
        val title: String? = null,
        val blockId: Long? = null,
    ) : ToolCommand {
        override val toolName get() = ToolNames.MANAGE_PLANNER
        override val isReadOnly get() = action == PlannerAction.GET_DAY_PLAN
    }

    /** Reads the opt-in notification digest (last 24 h); [appName] narrows it to one app. */
    data class SummarizeNotifications(val appName: String? = null) : ToolCommand {
        override val toolName get() = ToolNames.SUMMARIZE_NOTIFICATIONS
        override val isReadOnly get() = true
    }

    data class ContactAction(val action: ContactActionType, val contactName: String, val message: String?) : ToolCommand {
        override val toolName get() = ToolNames.CONTACT_ACTION
    }
}

enum class TodoAction(val wireName: String) {
    ADD("add"), COMPLETE("complete"), DELETE("delete"), LIST("list");

    companion object {
        fun fromWire(value: String?): TodoAction? = when (value?.trim()?.lowercase()) {
            "add", "create", "new", "insert", "add_todo", "add_task" -> ADD
            "complete", "done", "finish", "check", "mark_done", "completed", "tick" -> COMPLETE
            "delete", "remove", "drop", "clear" -> DELETE
            "list", "show", "get", "read", "view", "all" -> LIST
            else -> null
        }
    }
}

enum class PlannerAction(val wireName: String) {
    SCHEDULE_BLOCK("schedule_block"), RESCHEDULE("reschedule"), REMOVE_BLOCK("remove_block"), GET_DAY_PLAN("get_day_plan");

    companion object {
        fun fromWire(value: String?): PlannerAction? = when (value?.trim()?.lowercase()?.replace(' ', '_')) {
            "schedule_block", "schedule", "add", "add_block", "plan", "create", "block" -> SCHEDULE_BLOCK
            "reschedule", "move", "update", "change", "move_block", "reschedule_block" -> RESCHEDULE
            "remove_block", "remove", "delete", "cancel", "delete_block" -> REMOVE_BLOCK
            "get_day_plan", "get", "show", "list", "view", "day_plan", "get_plan", "plan_for_day" -> GET_DAY_PLAN
            else -> null
        }
    }
}

enum class DeviceSetting(val wireName: String, val displayName: String) {
    WIFI("wifi", "Wi-Fi"),
    BLUETOOTH("bluetooth", "Bluetooth"),
    FLASHLIGHT("flashlight", "Flashlight"),
    DND("dnd", "Do Not Disturb"),
    ;

    companion object {
        fun fromWire(value: String?): DeviceSetting? {
            val v = value?.trim()?.lowercase() ?: return null
            return when (v) {
                "wifi", "wi-fi", "wlan", "internet" -> WIFI
                "bluetooth", "bt" -> BLUETOOTH
                "flashlight", "torch", "flash", "light" -> FLASHLIGHT
                "dnd", "do_not_disturb", "do not disturb", "silent", "focus" -> DND
                else -> null
            }
        }
    }
}

enum class ToggleState {
    ON, OFF, TOGGLE;

    companion object {
        fun fromWire(value: String?): ToggleState = when (value?.trim()?.lowercase()) {
            "on", "true", "enable", "enabled", "1" -> ON
            "off", "false", "disable", "disabled", "0" -> OFF
            else -> TOGGLE
        }
    }
}

object ToolNames {
    const val SET_ALARM = "set_alarm"
    const val SET_TIMER = "set_timer"
    const val CREATE_REMINDER = "create_reminder"
    const val LAUNCH_APP = "launch_app"
    const val TOGGLE_SETTING = "toggle_setting"
    const val SORT_APPS = "sort_apps"
    const val GET_TODAY_AGENDA = "get_today_agenda"
    const val LIST_UNUSED_APPS = "list_unused_apps"
    const val FIND_LARGEST_FILES = "find_largest_files"
    const val QUERY_FILES_BY_ACTIVITY = "query_files_by_activity"
    const val MANAGE_TODO = "manage_todo"
    const val MANAGE_PLANNER = "manage_planner"
    const val SUMMARIZE_NOTIFICATIONS = "summarize_notifications"
    const val CONTACT_ACTION = "contact_action"
    const val GET_DEVICE_INFO = "get_device_info"
}
