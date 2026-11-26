package com.ailauncher.domain.ai

import com.ailauncher.domain.model.ContactActionType
import com.ailauncher.domain.model.FileActivityMode
import com.ailauncher.domain.model.FileTypeFilter
import com.ailauncher.domain.model.TodoPriority
import kotlinx.serialization.json.JsonObject
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

    /** Below this n_ctx the compact tool set is used, so the prompt still leaves room for a conversation. */
    const val FULL_TOOLSET_MIN_CONTEXT = 3072

    /** Every tool (needs a 4096-token context: the block costs ~1.5k tokens). */
    val all: List<JsonObject> by lazy {
        listOf(
            setAlarm, setTimer, createReminder, launchApp, toggleSetting, sortApps, getTodayAgenda,
            manageTodo, managePlanner, listUnusedApps, findLargestFiles, queryFilesByActivity,
            summarizeNotifications, contactAction,
        )
    }

    /**
     * The original assistant tools plus to-dos, for 2048-token contexts (low-RAM phones). The other
     * launcher tools stay reachable there through the deterministic quick commands.
     */
    val compact: List<JsonObject> by lazy {
        listOf(setAlarm, setTimer, createReminder, launchApp, toggleSetting, sortApps, getTodayAgenda, manageTodo)
    }

    /**
     * The tool set for a context window. Fixed per n_ctx (and build), so the KV-cached prefix stays
     * valid. [deviceInfo] adds get_device_info (the build's BETTER_ANSWERS switch).
     */
    fun forContext(contextSize: Int, deviceInfo: Boolean = false, memory: Boolean = false): List<JsonObject> {
        val full = contextSize >= FULL_TOOLSET_MIN_CONTEXT
        var tools = if (full) all else compact
        if (deviceInfo) {
            // Right after get_today_agenda, the other "tell me about" tool.
            val at = tools.indexOf(getTodayAgenda) + 1
            tools = tools.take(at) + getDeviceInfo + tools.drop(at)
        }
        // [memory] (ASSISTANT_MEMORY) adds manage_memory to the full set; at 2048 tokens the quick
        // commands ("remember …", "forget …") still work.
        if (memory && full) tools = tools + manageMemory
        return tools
    }

    private val setAlarm = function(
        name = ToolNames.SET_ALARM,
        description = "Set an alarm in the clock app.",
        required = listOf("hour", "minute"),
    ) {
        property("hour", "integer", "0-23, 24-hour time.")
        property("minute", "integer")
        property("label", "string")
        putJsonObject("days") {
            put("type", "array")
            putJsonObject("items") { put("type", "integer") }
            put("description", "Repeat weekdays, 1=Sunday..7=Saturday. Omit for a one-time alarm (\"tomorrow\").")
        }
    }

    private val setTimer = function(
        name = ToolNames.SET_TIMER,
        description = "Start a countdown timer.",
        required = listOf("seconds"),
    ) {
        property("seconds", "integer", "Total seconds, e.g. 300 for 5 minutes.")
        property("label", "string")
    }

    private val createReminder = function(
        name = ToolNames.CREATE_REMINDER,
        description = "Remind the user at a date and time with a notification.",
        required = listOf("title"),
    ) {
        property("title", "string")
        property(
            "datetime",
            "string",
            "Local YYYY-MM-DDTHH:MM in 24-hour time (7 pm = 19:00). Resolve \"tomorrow\" from the local time in the user's message.",
        )
        property("in_minutes", "integer", "Instead of datetime for relative times, e.g. 30.")
    }

    private val launchApp = function(
        name = ToolNames.LAUNCH_APP,
        description = "Open an installed app or a custom folder (e.g. \"Work folder\").",
        required = listOf("appName"),
    ) {
        property("appName", "string", "Name as the user said it.")
    }

    private val toggleSetting = function(
        name = ToolNames.TOGGLE_SETTING,
        description = "Turn Wi-Fi, Bluetooth, the flashlight or Do Not Disturb on or off.",
        required = listOf("setting"),
    ) {
        enumProperty("setting", DeviceSetting.entries.map { it.wireName })
        enumProperty("state", listOf("on", "off", "toggle"))
    }

    private val sortApps = function(
        name = ToolNames.SORT_APPS,
        description = "Organize the installed apps into smart categories.",
        required = emptyList(),
    ) {}

    private val getTodayAgenda = function(
        name = ToolNames.GET_TODAY_AGENDA,
        description = "Get today's events, reminders, alarm, planned blocks and urgent to-dos. Use for questions about the day.",
        required = emptyList(),
    ) {}

    private val manageMemory = function(
        name = ToolNames.MANAGE_MEMORY,
        description = "Save, list or forget facts about the user, only when they ask you to remember or forget something.",
        required = listOf("action"),
    ) {
        enumProperty("action", MemoryAction.entries.map { it.wireName })
        property("text", "string", "The fact in the user's words, or what to forget.")
    }

    private val getDeviceInfo = function(
        name = ToolNames.GET_DEVICE_INFO,
        description = "Get this phone's specs and status: model, Android version, processor, GPU, RAM, storage, battery level, and which on-device AI (LLM) models it can run well.",
        required = emptyList(),
    ) {}

    private val manageTodo = function(
        name = ToolNames.MANAGE_TODO,
        description = "Add, complete, delete or list to-dos.",
        required = listOf("action"),
    ) {
        enumProperty("action", TodoAction.entries.map { it.wireName })
        property("title", "string")
        property("listName", "string", "e.g. Shopping. Default Inbox.")
        enumProperty("priority", TodoPriority.entries.map { it.wireName })
        property("dueIso", "string", "YYYY-MM-DDTHH:MM")
        property("todoId", "integer")
    }

    private val managePlanner = function(
        name = ToolNames.MANAGE_PLANNER,
        description = "Plan the day in time blocks.",
        required = listOf("action"),
    ) {
        enumProperty("action", PlannerAction.entries.map { it.wireName })
        property("dateIso", "string", "YYYY-MM-DD, default today.")
        property("startTime", "string", "HH:MM, 24-hour (2 pm = 14:00).")
        property("durationMinutes", "integer", "Default 60.")
        property("title", "string")
        property("blockId", "integer")
    }

    private val listUnusedApps = function(
        name = ToolNames.LIST_UNUSED_APPS,
        description = "List apps not opened for a while.",
        required = emptyList(),
    ) {
        property("inactiveDays", "integer", "Omit for the user's default.")
    }

    private val findLargestFiles = function(
        name = ToolNames.FIND_LARGEST_FILES,
        description = "Find the biggest files on the phone.",
        required = emptyList(),
    ) {
        property("limit", "integer", "Default 15.")
        enumProperty("fileType", FileTypeFilter.entries.map { it.wireName })
    }

    private val queryFilesByActivity = function(
        name = ToolNames.QUERY_FILES_BY_ACTIVITY,
        description = "Find files untouched for unusedDays, or downloaded, opened or modified between two dates.",
        required = listOf("filterMode"),
    ) {
        enumProperty("filterMode", FileActivityMode.entries.map { it.wireName })
        property("unusedDays", "integer")
        property("startDateIso", "string", "YYYY-MM-DD")
        property("endDateIso", "string", "YYYY-MM-DD")
        enumProperty("fileType", FileTypeFilter.entries.map { it.wireName })
    }

    private val summarizeNotifications = function(
        name = ToolNames.SUMMARIZE_NOTIFICATIONS,
        description = "Read the last 24 hours of notifications to summarize them.",
        required = emptyList(),
    ) {
        property("appName", "string", "Only this app.")
    }

    private val contactAction = function(
        name = ToolNames.CONTACT_ACTION,
        description = "Call, text (SMS) or WhatsApp a contact.",
        required = listOf("action", "contactName"),
    ) {
        enumProperty("action", ContactActionType.entries.map { it.wireName })
        property("contactName", "string")
        property("message", "string")
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.property(name: String, type: String, description: String? = null) {
        putJsonObject(name) {
            put("type", type)
            description?.let { put("description", it) }
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.enumProperty(name: String, values: List<String>) {
        putJsonObject(name) {
            put("type", "string")
            putJsonArray("enum") { values.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
        }
    }

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
                if (required.isNotEmpty()) putJsonArray("required") { required.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } }
            }
        }
    }
}
