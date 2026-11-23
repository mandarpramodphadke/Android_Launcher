package com.ailauncher.domain.ai

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
}
