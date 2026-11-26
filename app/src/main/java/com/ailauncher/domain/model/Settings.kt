package com.ailauncher.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class DrawerLayout { GRID, LIST }

@Serializable
enum class DrawerTab { ALPHABETICAL, CATEGORIES, FOLDERS }

/** Colour themes. [supportsLight] is false for themes that are always dark. */
@Serializable
enum class AppTheme(val supportsLight: Boolean) {
    /** Colours extracted from the wallpaper (Android 12+), Classic on older versions. */
    MATERIAL_YOU(true),
    CLASSIC(true),
    /** Pure-black surfaces: maximum contrast and battery savings on OLED screens. */
    AMOLED(false),
    /** Vibrant multi-hue palette with per-category colours and a gradient clock. */
    COLORFUL(true),
    /** Deep-night surfaces with glowing cyan / magenta / lime accents. */
    NEON(false),
    /** Black, white and greys only. Pairs well with minimal mode. */
    MONOCHROME(true),
}

/** Colour of text drawn directly on the wallpaper (home screen clock, favourites). */
@Serializable
enum class HomeTextColor { AUTO, LIGHT, DARK }

@Serializable
enum class IconStyle {
    ORIGINAL,
    /** Android 13-style monochrome icons tinted with the theme; desaturated when an app has none. */
    THEMED,
    GRAYSCALE,
}

/** Shape of adaptive app icons (full version: ICON_CUSTOMIZATION). [SYSTEM] keeps the phone's own mask. */
@Serializable
enum class IconShape { SYSTEM, CIRCLE, SQUIRCLE, ROUNDED_SQUARE, TEARDROP }

/** Face of the home screen clock (full version: CLOCK_STYLES). */
@Serializable
enum class ClockFace {
    /** Large time with the date below (the original clock). */
    CLASSIC,
    /** Hours above minutes in big digits. */
    STACKED,
    /** A dial with hands, the date beside it. */
    ANALOG,
    /** The time in words: "Twenty past four". */
    WORDS,
    /** One thin line: time and date. */
    SLIM,
}

/** Typeface of the home screen clock. */
@Serializable
enum class ClockFont { DEFAULT, CONDENSED, SERIF, MONO, BOLD }

/**
 * Assistant features that depend on the build (full first, Google Play later). Provided from
 * BuildConfig by the app module, so the domain layer stays free of Android.
 */
data class AssistantFeatures(
    /** The general-knowledge system prompt and the get_device_info tool. */
    val betterAnswers: Boolean = false,
    /** Memories the user asks for: the manage_memory tool, quick commands and the prompt section. */
    val memory: Boolean = false,
    /** Replies read aloud with the phone's text-to-speech. */
    val spokenReplies: Boolean = false,
)

/** When the assistant reads its replies aloud (SPOKEN_REPLIES). */
enum class SpokenReplies { OFF, AFTER_VOICE, ALWAYS }

/** What is drawn behind the assistant's messages. */
@Serializable
enum class ChatBackground { DEFAULT, WALLPAPER, PHOTO }

@Serializable
enum class ResponseLength { SHORT, BALANCED, DETAILED }

@Serializable
enum class AssistantTone { FRIENDLY, PROFESSIONAL, PLAYFUL }

/**
 * How the assistant behaves: its name, how long its answers are, its tone and the user's own
 * instructions. The default profile produces exactly the built-in system prompt.
 */
@Serializable
data class AssistantProfile(
    val name: String = DEFAULT_NAME,
    val length: ResponseLength = ResponseLength.BALANCED,
    val tone: AssistantTone = AssistantTone.FRIENDLY,
    /** The user's own instructions, added to the end of the system prompt. */
    val instructions: String = "",
) {
    val displayName: String get() = name.trim().take(MAX_NAME).ifEmpty { DEFAULT_NAME }
    val isDefault: Boolean get() = displayName == DEFAULT_NAME && length == ResponseLength.BALANCED && tone == AssistantTone.FRIENDLY && instructions.isBlank()

    companion object {
        const val DEFAULT_NAME = "Aura"
        const val MAX_NAME = 24
        const val MAX_INSTRUCTIONS = 600
    }
}

/** Launcher-wide user preferences (persisted in DataStore, included in backups). */
@Serializable
data class LauncherSettings(
    val onboardingCompleted: Boolean = false,
    val modelSetupSeen: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val appTheme: AppTheme = AppTheme.MATERIAL_YOU,
    /** Text-only, distraction-free home screen and app drawer. */
    val minimalMode: Boolean = false,
    val homeTextColor: HomeTextColor = HomeTextColor.AUTO,
    /** Soft gradient behind home text so it stays readable on busy wallpapers. */
    val homeTextShade: Boolean = true,
    val iconStyle: IconStyle = IconStyle.ORIGINAL,
    val drawerLayout: DrawerLayout = DrawerLayout.GRID,
    val drawerTab: DrawerTab = DrawerTab.ALPHABETICAL,
    /** App drawer grid columns (4 or 5). */
    val gridColumns: Int = 4,
    /** Home screen page grid columns (4 or 5). */
    val homeGridColumns: Int = 4,
    /** Default for "apps I haven't used": the list_unused_apps tool and storage insights. */
    val unusedAppThresholdDays: Int = 30,
    /** Default for "old files": query_files_by_activity(unused_since). */
    val staleFileThresholdDays: Int = 90,
    /** Opt-in: keep notifications (on this phone only, 24 h) for the digest, badges and smart pills. */
    val notificationDigestEnabled: Boolean = false,
    val notificationBadges: Boolean = true,
    /** Predicted apps strip at the top of the drawer. */
    val showSuggestedApps: Boolean = true,
    val use24HourClock: Boolean? = null,
    val doubleTapToLock: Boolean = true,
    val swipeDownForNotifications: Boolean = true,
    val showAiCommandBar: Boolean = true,
    val autoConfirmAppLaunch: Boolean = false,
    val saveRemindersToCalendar: Boolean = true,
    val hapticFeedback: Boolean = true,
    val chatBackground: ChatBackground = ChatBackground.DEFAULT,
    /** How strongly the chat background is veiled (0..1) so messages stay readable. */
    val chatBackgroundDim: Float = DEFAULT_CHAT_DIM,
    val assistant: AssistantProfile = AssistantProfile(),
    /** Device calendars (CalendarContract ids) the assistant and planner leave out. */
    val hiddenCalendarIds: Set<Long> = emptySet(),
    /** Calendar new reminders go to; null picks the primary synced calendar. */
    val reminderCalendarId: Long? = null,
    /** Package of the icon pack in use; null uses the apps' own icons. */
    val iconPackPackage: String? = null,
    val iconShape: IconShape = IconShape.SYSTEM,
    /** App icon size in percent of the default (80..120). */
    val iconSizePercent: Int = 100,
    /** App and folder names under icons on the home pages. */
    val showHomeLabels: Boolean = true,
    val clockFace: ClockFace = ClockFace.CLASSIC,
    val clockFont: ClockFont = ClockFont.DEFAULT,
    /** Battery level next to the date under the clock. */
    val clockShowsBattery: Boolean = true,
    /** Blur the home screen behind the app drawer, folders and menus (Android 12+). */
    val blurEffects: Boolean = true,
    /** Move the wallpaper a little as you swipe between home pages. */
    val wallpaperParallax: Boolean = true,
    /** What's playing, with play / pause and skip, on the home screen (needs notification access). */
    val mediaControls: Boolean = true,
    /** Contacts, settings, to-dos, files, sums and unit conversions as you type in the command bar. */
    val instantSearch: Boolean = true,
    /** The assistant saves what the user asks it to remember, and uses it in its answers. */
    val assistantMemory: Boolean = true,
    /** Read replies aloud: never, after the user spoke, or always. */
    val spokenReplies: SpokenReplies = SpokenReplies.AFTER_VOICE,
    /** CUSTOM_GESTURES: what swipe down does; null follows [swipeDownForNotifications] (see actionFor). */
    val swipeDownAction: GestureAction? = null,
    /** CUSTOM_GESTURES: what double-tap does; null follows [doubleTapToLock]. */
    val doubleTapAction: GestureAction? = null,
    val twoFingerSwipeAction: GestureAction = GestureAction.QUICK_SETTINGS,
    /** The apps (component keys) the gestures open when set to [GestureAction.OPEN_APP]. */
    val swipeDownApp: String? = null,
    val doubleTapApp: String? = null,
    val twoFingerApp: String? = null,
) {
    companion object {
        const val DEFAULT_CHAT_DIM = 0.55f
        const val MIN_ICON_SIZE = 80
        const val MAX_ICON_SIZE = 120
    }
}

/** On-device model runtime configuration (persisted in DataStore, included in backups). */
@Serializable
data class ModelConfig(
    /**
     * llama.cpp n_ctx, or [AUTO_CONTEXT]: 4096 on phones with ~6 GB of RAM or more (the full tool
     * set needs it), 2048 below that. 2048 keeps the KV cache ~120 MB (q8_0); 4096 doubles it.
     */
    val contextSize: Int = DEFAULT_CONTEXT,
    /** 0 = auto-detect the performance cores (usually 4). */
    val threads: Int = 0,
    /** Free the model from RAM after this many idle minutes. 0 = keep loaded. */
    val autoUnloadMinutes: Int = 3,
    val useMmap: Boolean = true,
    val quantizeKvCache: Boolean = true,
    /** Start chats in Reasoning (thinking) mode instead of Fast Action mode. */
    val reasoningByDefault: Boolean = false,
    /** Catalog id (e.g. "qwen3-1.7b-q4_k_m") or "imported:<file name>" of the model to run. */
    val activeModelId: String = DEFAULT_MODEL_ID,
    /**
     * Offload layers to the GPU through Vulkan. Off by default (CPU only is the safe choice):
     * mobile GPU drivers vary a lot, so the engine falls back to the CPU if Vulkan fails.
     */
    val gpuOffload: Boolean = false,
) {
    /** The n_ctx to load with on a phone that has [totalRamBytes] of RAM. */
    fun resolvedContextSize(totalRamBytes: Long): Int = when {
        contextSize != AUTO_CONTEXT -> contextSize
        totalRamBytes >= LARGE_CONTEXT_MIN_RAM_BYTES -> 4096
        else -> 2048
    }

    companion object {
        const val DEFAULT_MODEL_ID = "qwen3-1.7b-q4_k_m"
        const val AUTO_CONTEXT = 0
        const val DEFAULT_CONTEXT = AUTO_CONTEXT
        val CONTEXT_OPTIONS = listOf(AUTO_CONTEXT, 2048, 4096)

        /** RAM from which Auto picks the 4096-token context ("6 GB" phones report a little less). */
        const val LARGE_CONTEXT_MIN_RAM_BYTES = 5_500_000_000L
        val AUTO_UNLOAD_OPTIONS = listOf(1, 3, 5, 10, 0)
    }
}
