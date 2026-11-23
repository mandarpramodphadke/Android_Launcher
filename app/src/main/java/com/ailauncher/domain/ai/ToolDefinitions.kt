package com.ailauncher.domain.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * OpenAI-style function schemas injected into Qwen3's `<tools>` block. Descriptions are written
 * for a 1.7B model: short, concrete, and explicit about units and formats (e.g. it is never asked
 * to compute epoch milliseconds itself).
 */
object ToolDefinitions {

    val all: List<JsonObject> by lazy {
        listOf(setAlarm, setTimer, createReminder, launchApp, toggleSetting, sortApps, getTodayAgenda)
    }

    private val setAlarm = function(
        name = ToolNames.SET_ALARM,
        description = "Set an alarm in the phone's clock app, e.g. a wake-up alarm.",
        required = listOf("hour", "minute"),
    ) {
        putJsonObject("hour") { put("type", "integer"); put("description", "Hour in 24-hour format, 0-23.") }
        putJsonObject("minute") { put("type", "integer"); put("description", "Minute, 0-59.") }
        putJsonObject("label") { put("type", "string"); put("description", "Optional short label, e.g. \"Gym\".") }
        putJsonObject("days") {
            put("type", "array")
            putJsonObject("items") { put("type", "integer") }
            put(
                "description",
                "Only for repeating alarms: weekdays to repeat on (1=Sunday, 2=Monday, ... 7=Saturday). " +
                    "Omit for a one-time alarm such as \"tomorrow\".",
            )
        }
    }

    private val setTimer = function(
        name = ToolNames.SET_TIMER,
        description = "Start a countdown timer.",
        required = listOf("seconds"),
    ) {
        putJsonObject("seconds") {
            put("type", "integer")
            put("description", "Total duration in seconds, e.g. 300 for 5 minutes.")
        }
        putJsonObject("label") { put("type", "string"); put("description", "Optional short label.") }
    }

    private val createReminder = function(
        name = ToolNames.CREATE_REMINDER,
        description = "Remind the user about something at a specific date and time with a notification.",
        required = listOf("title"),
    ) {
        putJsonObject("title") { put("type", "string"); put("description", "What to remind the user about.") }
        putJsonObject("datetime") {
            put("type", "string")
            put(
                "description",
                "Local date and time in ISO format YYYY-MM-DDTHH:MM using 24-hour time " +
                    "(7 am = 07:00, 7 pm = 19:00, noon = 12:00). Resolve words like \"tomorrow\" " +
                    "or \"tonight\" from the local time shown with the user's message.",
            )
        }
        putJsonObject("in_minutes") {
            put("type", "integer")
            put("description", "Use instead of datetime for relative requests, e.g. 30 for \"in half an hour\".")
        }
    }

    private val launchApp = function(
        name = ToolNames.LAUNCH_APP,
        description = "Open an app installed on the phone.",
        required = listOf("appName"),
    ) {
        putJsonObject("appName") {
            put("type", "string")
            put("description", "The app's name as the user said it, e.g. \"Spotify\".")
        }
    }

    private val toggleSetting = function(
        name = ToolNames.TOGGLE_SETTING,
        description = "Turn a phone setting on or off: Wi-Fi, Bluetooth, the flashlight or Do Not Disturb.",
        required = listOf("setting"),
    ) {
        putJsonObject("setting") {
            put("type", "string")
            put("enum", buildJsonArray { DeviceSetting.entries.forEach { add(kotlinx.serialization.json.JsonPrimitive(it.wireName)) } })
        }
        putJsonObject("state") {
            put("type", "string")
            putJsonArray("enum") {
                add(kotlinx.serialization.json.JsonPrimitive("on"))
                add(kotlinx.serialization.json.JsonPrimitive("off"))
                add(kotlinx.serialization.json.JsonPrimitive("toggle"))
            }
        }
    }

    private val sortApps = function(
        name = ToolNames.SORT_APPS,
        description = "Organize the user's installed apps into smart categories in the app drawer.",
        required = emptyList(),
    ) {}

    private val getTodayAgenda = function(
        name = ToolNames.GET_TODAY_AGENDA,
        description = "Get today's calendar events, reminders and next alarm. Use when the user asks about their day or schedule.",
        required = emptyList(),
    ) {}

    private fun function(
        name: String,
        description: String,
        required: List<String>,
        properties: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): JsonObject = buildJsonObject {
        put("type", "function")
        putJsonObject("function") {
            put("name", name)
            put("description", description)
            putJsonObject("parameters") {
                put("type", "object")
                putJsonObject("properties", properties)
                putJsonArray("required") { required.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
            }
        }
    }
}
