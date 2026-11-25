package com.ailauncher.presentation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.ailauncher.data.packages.AppIconData
import com.ailauncher.domain.ai.ToolNames
import com.ailauncher.domain.model.AppCategory
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.AppTheme
import com.ailauncher.domain.model.CategorizationProgress
import com.ailauncher.domain.model.CategorySource
import com.ailauncher.domain.model.ChatMessage
import com.ailauncher.domain.model.ChatRole
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.domain.model.DockEntry
import com.ailauncher.domain.model.DrawerLayout
import com.ailauncher.domain.model.DrawerTab
import com.ailauncher.domain.model.EngineState
import com.ailauncher.domain.model.FolderAccent
import com.ailauncher.domain.model.FolderRule
import com.ailauncher.domain.model.LauncherWidgetConfig
import com.ailauncher.domain.model.LauncherWidgetKind
import com.ailauncher.domain.model.TodoItem
import com.ailauncher.domain.model.TodoPriority
import com.ailauncher.presentation.folders.FolderEditorContent
import com.ailauncher.presentation.system.SystemInfoContent
import com.ailauncher.presentation.system.SystemInfoUiState
import com.ailauncher.domain.system.SystemInfoSections
import com.ailauncher.presentation.home.LauncherWidgetData
import com.ailauncher.domain.model.GridPosition
import com.ailauncher.domain.model.HomeItem
import com.ailauncher.domain.model.HomeItemContent
import com.ailauncher.domain.model.IconStyle
import com.ailauncher.domain.model.InferenceMode
import com.ailauncher.domain.model.LauncherSettings
import com.ailauncher.domain.model.MessageStatus
import com.ailauncher.domain.model.ModelRuntimeInfo
import com.ailauncher.domain.model.PlannerBlock
import com.ailauncher.domain.model.SmartExtraction
import com.ailauncher.domain.model.SmartPill
import com.ailauncher.domain.model.ThemeMode
import com.ailauncher.domain.model.ToolInvocation
import com.ailauncher.domain.model.ToolStatus
import com.ailauncher.domain.model.UpcomingEvent
import com.ailauncher.presentation.chat.ChatInput
import com.ailauncher.presentation.chat.ChatMessageList
import com.ailauncher.presentation.chat.ChatMessageUi
import com.ailauncher.presentation.chat.ChatUiState
import com.ailauncher.presentation.chat.ToolCardCallbacks
import com.ailauncher.presentation.components.EngineStatusChip
import com.ailauncher.presentation.drawer.AlphaSection
import com.ailauncher.presentation.drawer.CategorySection
import com.ailauncher.presentation.drawer.DrawerCallbacks
import com.ailauncher.presentation.drawer.DrawerContent
import com.ailauncher.presentation.drawer.DrawerUiState
import com.ailauncher.presentation.home.Glance
import com.ailauncher.presentation.home.HomeCallbacks
import com.ailauncher.presentation.home.HomeContent
import com.ailauncher.presentation.home.HomeUiState
import com.ailauncher.presentation.settings.ThemePicker
import com.ailauncher.presentation.theme.AiLauncherTheme
import com.ailauncher.presentation.theme.LocalLauncherStyle
import com.ailauncher.presentation.theme.WallpaperTint
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Renders the home screen, drawer and theme picker in every theme (Robolectric native graphics),
 * failing on any crash or blank frame, and writes the PNGs to build/outputs/screenshots for review.
 */
@OptIn(ExperimentalCoilApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w393dp-h851dp-xhdpi")
class ThemeScreenshotTest {

    @get:Rule val compose = createComposeRule()

    private class Shot(val name: String, val content: @Composable () -> Unit)

    @Test
    fun rendersEveryThemeAndLayout() {
        val shots = buildList {
            AppTheme.entries.forEach { theme ->
                add(Shot("home-${theme.slug}-dark-wallpaper") { Home(settings(theme), DarkWallpaper, WallpaperTint.Light) })
            }
            add(Shot("home-material-you-light-wallpaper") { Home(settings(AppTheme.MATERIAL_YOU), LightWallpaper, WallpaperTint.Dark) })
            add(Shot("home-colourful-light-wallpaper") { Home(settings(AppTheme.COLORFUL), LightWallpaper, WallpaperTint.Dark) })
            add(Shot("home-neon-light-wallpaper") { Home(settings(AppTheme.NEON), LightWallpaper, WallpaperTint.Dark) })
            // The reported bug: a mostly light wallpaper (so the system asks for dark text) with a
            // dark band behind the clock. "before" reproduces the old rendering: no halo, no shade.
            add(Shot("bug-before-dark-text-on-dark-band") {
                Home(
                    settings(AppTheme.CLASSIC).copy(homeTextShade = false),
                    BandedWallpaper,
                    WallpaperTint.Dark.copy(shadow = Shadow.None, chip = Color(0x22000000)),
                )
            })
            add(Shot("bug-after-dark-text-on-dark-band") { Home(settings(AppTheme.CLASSIC), BandedWallpaper, WallpaperTint.Dark) })
            add(Shot("home-minimal-mono-dark-wallpaper") { Home(settings(AppTheme.MONOCHROME).copy(minimalMode = true), DarkWallpaper, WallpaperTint.Light) })
            add(Shot("home-minimal-amoled-light-wallpaper") { Home(settings(AppTheme.AMOLED).copy(minimalMode = true), LightWallpaper, WallpaperTint.Dark) })
            add(Shot("home-neon-themed-icons") { Home(settings(AppTheme.NEON).copy(iconStyle = IconStyle.THEMED), DarkWallpaper, WallpaperTint.Light) })

            add(Shot("home-material-you-page-2") { Home(settings(AppTheme.MATERIAL_YOU), DarkWallpaper, WallpaperTint.Light, page = 1) })
            add(Shot("home-classic-5-columns") { Home(settings(AppTheme.CLASSIC).copy(homeGridColumns = 5), LightWallpaper, WallpaperTint.Dark) })

            add(Shot("drawer-neon-categories") { Drawer(settings(AppTheme.NEON), DrawerTab.CATEGORIES, DrawerLayout.GRID) })
            add(Shot("drawer-material-you-folders") { Drawer(settings(AppTheme.MATERIAL_YOU, ThemeMode.DARK), DrawerTab.FOLDERS, DrawerLayout.GRID) })
            add(Shot("drawer-colourful-light-folders") { Drawer(settings(AppTheme.COLORFUL, ThemeMode.LIGHT), DrawerTab.FOLDERS, DrawerLayout.GRID) })
            add(Shot("drawer-colourful-light-categories") { Drawer(settings(AppTheme.COLORFUL, ThemeMode.LIGHT), DrawerTab.CATEGORIES, DrawerLayout.LIST) })
            add(Shot("drawer-colourful-dark-grid") { Drawer(settings(AppTheme.COLORFUL, ThemeMode.DARK), DrawerTab.ALPHABETICAL, DrawerLayout.GRID) })
            add(Shot("drawer-amoled-list") { Drawer(settings(AppTheme.AMOLED), DrawerTab.ALPHABETICAL, DrawerLayout.LIST) })
            add(Shot("drawer-classic-dark-list") { Drawer(settings(AppTheme.CLASSIC, ThemeMode.DARK), DrawerTab.ALPHABETICAL, DrawerLayout.LIST) })
            add(Shot("drawer-minimal-mono-light") { Drawer(settings(AppTheme.MONOCHROME, ThemeMode.LIGHT).copy(minimalMode = true), DrawerTab.CATEGORIES, DrawerLayout.GRID) })
            add(Shot("drawer-monochrome-greyscale-icons") { Drawer(settings(AppTheme.MONOCHROME, ThemeMode.DARK).copy(iconStyle = IconStyle.GRAYSCALE), DrawerTab.ALPHABETICAL, DrawerLayout.GRID) })

            // Store listing renders: a full first page of apps instead of the test's widget placeholders.
            add(Shot("store-home-material-you") { Home(settings(AppTheme.MATERIAL_YOU), DarkWallpaper, WallpaperTint.Light, items = StoreHomeItems) })
            add(Shot("store-home-neon") { Home(settings(AppTheme.NEON), DarkWallpaper, WallpaperTint.Light, items = StoreHomeItems) })
            add(Shot("chat-material-you-unused-apps") { Chat(settings(AppTheme.MATERIAL_YOU, ThemeMode.DARK), UnusedAppsConversation) })
            add(Shot("chat-classic-light-day-plan") { Chat(settings(AppTheme.CLASSIC, ThemeMode.LIGHT), DayPlanConversation) })

            // 1.4: the launcher's to-do widgets and smart folders.
            add(Shot("home-widgets-material-you") { Home(settings(AppTheme.MATERIAL_YOU), DarkWallpaper, WallpaperTint.Light, items = WidgetHomeItems, widgets = true) })
            add(Shot("home-widgets-amoled") { Home(settings(AppTheme.AMOLED), DarkWallpaper, WallpaperTint.Light, items = WidgetHomeItems, widgets = true) })
            add(Shot("home-widgets-colourful-light-page-2") {
                Home(settings(AppTheme.COLORFUL, ThemeMode.LIGHT), LightWallpaper, WallpaperTint.Dark, page = 1, items = WidgetHomeItems, widgets = true)
            })
            add(Shot("home-widgets-neon-page-2") { Home(settings(AppTheme.NEON), DarkWallpaper, WallpaperTint.Light, page = 1, items = WidgetHomeItems, widgets = true) })
            add(Shot("drawer-smart-folders") {
                Drawer(settings(AppTheme.MATERIAL_YOU, ThemeMode.DARK), DrawerTab.FOLDERS, DrawerLayout.GRID, folders = AllFolders)
            })
            add(Shot("folder-editor-smart-dark") { FolderEditor(settings(AppTheme.MATERIAL_YOU, ThemeMode.DARK), NewAppsFolder) })
            add(Shot("folder-editor-category-light") {
                FolderEditor(settings(AppTheme.CLASSIC, ThemeMode.LIGHT), NewAppsFolder.copy(name = "Games", rule = FolderRule.Category(AppCategory.GAMES)))
            })
            add(Shot("folder-editor-new-light") { FolderEditor(settings(AppTheme.COLORFUL, ThemeMode.LIGHT), null) })

            // 1.5: System info, the page after the assistant.
            add(Shot("system-info-material-you-dark") { SystemInfo(settings(AppTheme.MATERIAL_YOU, ThemeMode.DARK), firstItem = 0) })
            add(Shot("system-info-material-you-dark-cpu") { SystemInfo(settings(AppTheme.MATERIAL_YOU, ThemeMode.DARK), firstItem = 2) })
            add(Shot("system-info-colourful-light-memory") { SystemInfo(settings(AppTheme.COLORFUL, ThemeMode.LIGHT), firstItem = 4) })
            add(Shot("system-info-amoled-device") { SystemInfo(settings(AppTheme.AMOLED), firstItem = 7) })

            add(Shot("settings-theme-picker-light") { Picker(settings(AppTheme.COLORFUL, ThemeMode.LIGHT)) })
            add(Shot("settings-theme-picker-dark") { Picker(settings(AppTheme.NEON, ThemeMode.DARK)) })
        }

        val outDir = File(System.getProperty("screenshotDir") ?: "build/outputs/screenshots").apply { mkdirs() }
        var current by mutableStateOf(shots.first())
        compose.setContent {
            CompositionLocalProvider(LocalInspectionMode provides true, LocalAsyncImagePreviewHandler provides FakeIcons) {
                current.content()
            }
        }
        shots.forEach { shot ->
            current = shot
            compose.waitForIdle()
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            assertWithMessage("${shot.name} drew real content").that(distinctColours(bitmap)).isAtLeast(64)
            File(outDir, "${shot.name}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    // ------------------------------------------------------------------------------------ scenes

    @Composable
    private fun Home(
        settings: LauncherSettings,
        wallpaper: Brush,
        tint: WallpaperTint,
        page: Int = 0,
        items: List<HomeItem> = HomeItems,
        widgets: Boolean = false,
    ) {
        AiLauncherTheme(settings) {
            Box(Modifier.fillMaxSize().background(wallpaper)) {
                HomeContent(
                    state = HomeUiState(
                        now = Now,
                        use24Hour = false,
                        upcoming = UpcomingEvent(UpcomingEvent.Kind.ALARM, "Gym", Now.plusHours(21).toInstant().toEpochMilli()),
                        dock = Apps.filter { it.isInDock }.sortedBy { it.dockPosition }.take(4).map { DockEntry.App(it) } +
                            DockEntry.Folder(WorkFolder.copy(dockPosition = 4)),
                        apps = Apps.associateBy { it.componentKey },
                        homeItems = items,
                        pageCount = 2,
                        folders = AllFolders.associateBy { it.id },
                        pills = listOf(SmartPill("otp", "Bank", SmartExtraction.Otp("482913"), Now.toInstant().toEpochMilli())),
                        badges = mapOf(Apps[1].packageName to 3),
                        glance = Glance(
                            openTodos = 3,
                            urgentTodos = 1,
                            nextBlock = PlannerBlock(1, Now.toLocalDate(), 10 * 60, 90, "Deep work", null, false),
                        ),
                        settings = settings,
                        widgetData = if (widgets) WidgetData else LauncherWidgetData(),
                    ),
                    tint = tint,
                    style = LocalLauncherStyle.current,
                    pagerState = rememberPagerState(initialPage = page) { 2 },
                    queryState = rememberTextFieldState(),
                    clip = null,
                    widgetHost = null,
                    editingWidgetId = null,
                    callbacks = HomeCallbacks(dragEnabled = true),
                )
            }
        }
    }

    /** The folder editor sheet's content, as it opens (smart mode for a smart folder). */
    @Composable
    private fun FolderEditor(settings: LauncherSettings, folder: CustomFolder?) {
        AiLauncherTheme(settings) {
            Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.statusBarsPadding().padding(top = 16.dp)) {
                    FolderEditorContent(
                        existing = folder,
                        allApps = Apps.sortedBy { it.label },
                        initialSelection = folder?.packageNames ?: listOf(Apps[5].packageName, Apps[6].packageName),
                        onSave = { _, _, _, _, _ -> },
                        onDismiss = {},
                        smartFolders = true,
                        previewCount = { rule -> if (rule == FolderRule.LauncherTools) 8 else 3 },
                    )
                }
            }
        }
    }

    @Composable
    private fun Drawer(settings: LauncherSettings, tab: DrawerTab, layout: DrawerLayout, folders: List<CustomFolder> = listOf(WorkFolder, TravelFolder)) {
        AiLauncherTheme(settings) {
            Box(Modifier.fillMaxSize().background(DarkWallpaper)) {
                DrawerContent(
                    state = DrawerUiState(
                        loading = false,
                        tab = tab,
                        layout = if (settings.minimalMode) DrawerLayout.LIST else layout,
                        columns = 4,
                        alphabetical = Apps.sortedBy { it.label }.groupBy { it.label.first().uppercase() }
                            .map { (letter, apps) -> AlphaSection(letter, apps) },
                        categories = Apps.groupBy { it.category }.toSortedMap(compareBy<AppCategory?> { it?.ordinal ?: Int.MAX_VALUE })
                            .map { (category, apps) -> CategorySection(category, apps.sortedBy { it.label }) },
                        totalApps = Apps.size,
                        notSortedByAi = 0,
                        progress = CategorizationProgress(running = false, categorized = 0, total = 0),
                        modelReady = true,
                        folders = folders,
                        appsByPackage = Apps.groupBy { it.packageName },
                        suggestions = Apps.take(5),
                        badges = mapOf(Apps[1].packageName to 2),
                    ),
                    queryState = rememberTextFieldState(),
                    minimal = settings.minimalMode,
                    callbacks = DrawerCallbacks(),
                )
            }
        }
    }

    /** The assistant screen as the app draws it (top bar, conversation, input with quick actions). */
    @Composable
    private fun Chat(settings: LauncherSettings, conversation: List<ChatMessageUi>) {
        AiLauncherTheme(settings) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Aura")
                                Spacer(Modifier.width(12.dp))
                                EngineStatusChip(EngineState.Ready(RuntimeInfo, null), null, onClick = {})
                            }
                        },
                    )
                },
                bottomBar = {
                    ChatInput(
                        state = rememberTextFieldState(),
                        busy = false,
                        reasoning = false,
                        onToggleReasoning = {},
                        onSend = {},
                        onStop = {},
                        onQuickAction = {},
                    )
                },
            ) { padding ->
                ChatMessageList(
                    state = ChatUiState(messages = conversation, engine = EngineState.Ready(RuntimeInfo, null)),
                    shared = null,
                    listState = rememberLazyListState(),
                    padding = padding,
                    cardCallbacks = ToolCardCallbacks(),
                    onConfirm = { _, _ -> },
                    onCancel = { _, _ -> },
                    onReport = {},
                    onSharedAction = {},
                    onSharedDismiss = {},
                    onOpenModelSetup = {},
                )
            }
        }
    }

    @Composable
    private fun SystemInfo(settings: LauncherSettings, firstItem: Int) {
        AiLauncherTheme(settings) {
            SystemInfoContent(
                state = SystemInfoUiState(
                    details = SystemFixture.details,
                    live = SystemFixture.live,
                    cpuHistory = SystemFixture.history,
                    ramHistory = SystemFixture.history.map { 0.55f + it * 0.15f },
                    sections = SystemInfoSections.build(SystemFixture.details, SystemFixture.live),
                ),
                onBack = {},
                onCopy = {},
                listState = rememberLazyListState(initialFirstVisibleItemIndex = firstItem),
            )
        }
    }

    @Composable
    private fun Picker(settings: LauncherSettings) {
        AiLauncherTheme(settings) {
            Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.statusBarsPadding()) {
                    ThemePicker(selected = settings.appTheme, mode = settings.themeMode, onSelect = {})
                }
            }
        }
    }

    // -------------------------------------------------------------------------------- fixtures

    private val AppTheme.slug get() = name.lowercase().replace('_', '-')

    private fun settings(theme: AppTheme, mode: ThemeMode = ThemeMode.SYSTEM) =
        LauncherSettings(onboardingCompleted = true, appTheme = theme, themeMode = mode)

    private fun distinctColours(bitmap: Bitmap): Int {
        val colours = HashSet<Int>()
        for (y in 0 until bitmap.height step 4) for (x in 0 until bitmap.width step 4) colours += bitmap.getPixel(x, y)
        return colours.size
    }

    private companion object {
        val Now: ZonedDateTime = ZonedDateTime.of(2026, 11, 24, 9, 41, 0, 0, ZoneId.of("UTC"))

        val DarkWallpaper = Brush.linearGradient(
            listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF3A2C5A)),
            start = Offset.Zero,
            end = Offset(800f, 1700f),
        )
        val LightWallpaper = Brush.linearGradient(
            listOf(Color(0xFFFDFBF7), Color(0xFFF1E4D8), Color(0xFFDCE6F2)),
            start = Offset.Zero,
            end = Offset(800f, 1700f),
        )
        /** Bright overall (the system hint says "dark text"), but dark where the clock sits. */
        val BandedWallpaper = Brush.verticalGradient(
            0f to Color(0xFF1C1F26),
            0.30f to Color(0xFF2B303A),
            0.40f to Color(0xFFE9E6E1),
            1f to Color(0xFFF7F5F1),
        )

        fun app(label: String, category: AppCategory?, dock: Int? = null) = AppInfo(
            componentKey = "com.example.${label.lowercase()}/.Main",
            packageName = "com.example.${label.lowercase().filter { it.isLetter() }}",
            activityName = ".Main",
            systemLabel = label,
            customLabel = null,
            category = category,
            categorySource = CategorySource.AI,
            isHidden = false,
            dockPosition = dock,
            launchCount = 0,
            lastLaunchedAt = 0,
            isSystemApp = false,
            installedAt = 0,
            updatedAt = 0,
        )

        val WorkFolder = CustomFolder(
            id = 1,
            name = "Work",
            description = "Apps for my job",
            accent = FolderAccent.TEAL,
            sortOrder = 0,
            packageNames = listOf("com.example.calendar", "com.example.gmail", "com.example.keepnotes", "com.example.notion"),
            dockPosition = null,
        )
        val TravelFolder = CustomFolder(
            id = 2,
            name = "Travel",
            description = null,
            accent = FolderAccent.AMBER,
            sortOrder = 1,
            packageNames = listOf("com.example.maps", "com.example.chrome"),
            dockPosition = null,
        )

        val NewAppsFolder = CustomFolder(
            id = 3,
            name = "New apps",
            description = null,
            accent = FolderAccent.SKY,
            sortOrder = 2,
            packageNames = listOf("com.example.duolingo", "com.example.notion", "com.example.chess"),
            dockPosition = null,
            rule = FolderRule.RecentlyInstalled(14),
        )
        val ToolsFolder = CustomFolder(
            id = 4,
            name = "AI Launcher",
            description = null,
            accent = FolderAccent.VIOLET,
            sortOrder = 3,
            packageNames = emptyList(),
            dockPosition = null,
            rule = FolderRule.LauncherTools,
        )
        val UnusedFolder = CustomFolder(
            id = 5,
            name = "Not used lately",
            description = "Clean these up",
            accent = FolderAccent.ROSE,
            sortOrder = 4,
            packageNames = listOf("com.example.reddit", "com.example.amazon", "com.example.news", "com.example.fit"),
            dockPosition = null,
            rule = FolderRule.NotUsedFor(30),
        )
        val AllFolders = listOf(WorkFolder, TravelFolder, NewAppsFolder, ToolsFolder, UnusedFolder)

        private fun todo(id: Long, title: String, list: String = TodoItem.DEFAULT_LIST, priority: TodoPriority = TodoPriority.MEDIUM, dueHour: Int? = null, dueDays: Long = 0, done: Boolean = false) =
            TodoItem(
                id = id,
                title = title,
                notes = null,
                listName = list,
                priority = priority,
                dueAtMillis = dueHour?.let { Now.toLocalDate().plusDays(dueDays).atTime(it, 0).atZone(Now.zone).toInstant().toEpochMilli() },
                completed = done,
                createdAt = id,
                completedAt = if (done) Now.minusMinutes(2).toInstant().toEpochMilli() else null,
            )

        val WidgetData = LauncherWidgetData(
            todos = listOf(
                todo(1, "Send the Q4 report", list = "Work", priority = TodoPriority.HIGH, dueHour = 8),
                todo(2, "Call the electrician", dueHour = 18),
                todo(3, "Book flight to Pune", priority = TodoPriority.HIGH),
                todo(4, "Renew car insurance", dueHour = 10, dueDays = 1),
                todo(5, "Milk and eggs", list = "Shopping"),
                todo(6, "Rice, 5 kg", list = "Shopping"),
                todo(7, "Water the plants", done = true),
                todo(8, "Plan the weekend", priority = TodoPriority.LOW, dueHour = 12, dueDays = 4),
            ),
            todayBlocks = listOf(
                PlannerBlock(1, Now.toLocalDate(), 9 * 60, 90, "Deep work", null, false),
                PlannerBlock(2, Now.toLocalDate(), 13 * 60, 60, "Gym", null, false),
            ),
        )

        val Apps = listOf(
            app("Phone", AppCategory.COMMUNICATION, dock = 0),
            app("Messages", AppCategory.COMMUNICATION, dock = 1),
            app("Chrome", AppCategory.UTILITIES, dock = 2),
            app("Camera", AppCategory.PHOTOGRAPHY, dock = 3),
            app("Spotify", AppCategory.MEDIA, dock = 4),
            app("Calendar", AppCategory.PRODUCTIVITY),
            app("Gmail", AppCategory.PRODUCTIVITY),
            app("Keep Notes", AppCategory.PRODUCTIVITY),
            app("WhatsApp", AppCategory.COMMUNICATION),
            app("Instagram", AppCategory.SOCIAL),
            app("Reddit", AppCategory.SOCIAL),
            app("GPay", AppCategory.FINANCE),
            app("Amazon", AppCategory.SHOPPING),
            app("YouTube", AppCategory.MEDIA),
            app("Photos", AppCategory.PHOTOGRAPHY),
            app("Chess", AppCategory.GAMES),
            app("Maps", AppCategory.TRAVEL),
            app("Fit", AppCategory.HEALTH),
            app("Duolingo", AppCategory.EDUCATION),
            app("News", AppCategory.NEWS),
            app("Clock", AppCategory.UTILITIES),
            app("Files", AppCategory.UTILITIES),
            app("Settings", AppCategory.UTILITIES),
            app("Notion", null),
        )

        /** Page 1: smart folders, a to-do list and the quick-add bar; page 2: Today and a Shopping list. */
        val WidgetHomeItems = listOf(
            HomeItem(1, GridPosition(page = 0, x = 0, y = 2), HomeItemContent.Folder(NewAppsFolder.id)),
            HomeItem(2, GridPosition(page = 0, x = 1, y = 2), HomeItemContent.Folder(ToolsFolder.id)),
            HomeItem(3, GridPosition(page = 0, x = 2, y = 2), HomeItemContent.Folder(WorkFolder.id)),
            HomeItem(4, GridPosition(page = 0, x = 3, y = 2), HomeItemContent.App(Apps[8].componentKey)),
            HomeItem(5, GridPosition(page = 0, x = 0, y = 3, spanX = 4, spanY = 2), HomeItemContent.LauncherWidget(LauncherWidgetConfig(LauncherWidgetKind.TODO_LIST))),
            HomeItem(6, GridPosition(page = 1, x = 0, y = 0, spanX = 4, spanY = 2), HomeItemContent.LauncherWidget(LauncherWidgetConfig(LauncherWidgetKind.TODAY))),
            HomeItem(7, GridPosition(page = 1, x = 0, y = 2, spanX = 2, spanY = 2), HomeItemContent.LauncherWidget(LauncherWidgetConfig(LauncherWidgetKind.TODO_LIST, "Shopping"))),
            HomeItem(8, GridPosition(page = 1, x = 2, y = 2), HomeItemContent.Folder(UnusedFolder.id)),
            HomeItem(9, GridPosition(page = 1, x = 3, y = 2), HomeItemContent.App(Apps[16].componentKey)),
            HomeItem(10, GridPosition(page = 1, x = 0, y = 4, spanX = 4, spanY = 1), HomeItemContent.LauncherWidget(LauncherWidgetConfig(LauncherWidgetKind.QUICK_ADD))),
        )

        val StoreHomeItems = buildList {
            add(HomeItem(1, GridPosition(page = 0, x = 0, y = 2), HomeItemContent.Folder(WorkFolder.id)))
            add(HomeItem(2, GridPosition(page = 0, x = 1, y = 2), HomeItemContent.Folder(TravelFolder.id)))
            // Calendar, WhatsApp, Instagram, YouTube, Photos, Maps, Fit, Duolingo, News, Files.
            listOf(5, 8, 9, 13, 14, 16, 17, 18, 19, 21).forEachIndexed { index, app ->
                val cell = index + 2
                add(HomeItem(10L + index, GridPosition(page = 0, x = cell % 4, y = 2 + cell / 4), HomeItemContent.App(Apps[app].componentKey)))
            }
        }

        /** Page 1: a folder, apps and a 4×2 widget under the clock; page 2: a folder, an app and a widget. */
        val HomeItems = listOf(
            HomeItem(1, GridPosition(page = 0, x = 0, y = 2), HomeItemContent.Folder(WorkFolder.id)),
            HomeItem(2, GridPosition(page = 0, x = 1, y = 2), HomeItemContent.App(Apps[5].componentKey)),
            HomeItem(3, GridPosition(page = 0, x = 2, y = 2), HomeItemContent.App(Apps[8].componentKey)),
            HomeItem(4, GridPosition(page = 0, x = 3, y = 2), HomeItemContent.App(Apps[13].componentKey)),
            HomeItem(5, GridPosition(page = 0, x = 0, y = 3, spanX = 4, spanY = 2), HomeItemContent.Widget(7, "com.example.clock/.Widget")),
            HomeItem(6, GridPosition(page = 1, x = 0, y = 0), HomeItemContent.Folder(TravelFolder.id)),
            HomeItem(7, GridPosition(page = 1, x = 1, y = 0), HomeItemContent.App(Apps[16].componentKey)),
            HomeItem(8, GridPosition(page = 1, x = 0, y = 1, spanX = 2, spanY = 2), HomeItemContent.Widget(8, "com.example.notes/.Widget")),
        )

        val RuntimeInfo = ModelRuntimeInfo(
            name = "Qwen3-1.7B",
            description = "qwen3 1.7B Q4_K - Medium",
            contextSize = 4096,
            threads = 4,
            kvCacheType = "q8_0",
            sizeBytes = 1_101_457_408,
            parameterCount = 1_720_574_976,
        )

        private fun message(id: Long, role: ChatRole, content: String, tools: List<ToolInvocation> = emptyList(), tps: Float? = null) =
            ChatMessageUi(
                ChatMessage(
                    id = id,
                    conversationId = "default",
                    role = role,
                    content = content,
                    thinking = null,
                    toolCalls = tools,
                    status = MessageStatus.COMPLETE,
                    mode = if (role == ChatRole.ASSISTANT) InferenceMode.FAST_ACTION else null,
                    tokensPerSecond = tps,
                    createdAt = 0,
                ),
            )

        private fun unusedApp(label: String, daysAgo: Long, mb: Long) = buildJsonObject {
            val pkg = "com.example.${label.lowercase().filter { it.isLetter() }}"
            put("componentKey", "$pkg/.Main")
            put("packageName", pkg)
            put("label", label)
            put("lastUsedAt", Now.minusDays(daysAgo).toInstant().toEpochMilli())
            put("sizeBytes", mb * 1_000_000)
        }

        val UnusedAppsConversation = listOf(
            message(1, ChatRole.USER, "Which apps haven't I used in a month?"),
            message(
                2,
                ChatRole.ASSISTANT,
                "You haven't opened these 4 apps in over 30 days. Together they take about 1.2 GB.",
                listOf(
                    ToolInvocation(
                        id = "t1",
                        name = ToolNames.LIST_UNUSED_APPS,
                        arguments = buildJsonObject { put("inactiveDays", 30) },
                        status = ToolStatus.SUCCEEDED,
                        summary = "Not opened for 30+ days",
                        resultMessage = "4 apps unused for 30+ days",
                        displayData = buildJsonObject {
                            put("kind", "unused_apps")
                            put("inactive_days", 30)
                            putJsonArray("apps") {
                                add(unusedApp("Duolingo", 64, 312))
                                add(unusedApp("Reddit", 47, 401))
                                add(unusedApp("Chess", 39, 96))
                                add(unusedApp("Amazon", 33, 388))
                            }
                        },
                    ),
                ),
                tps = 11.4f,
            ),
        )

        private fun block(id: String, title: String, time: String, message: String) = ToolInvocation(
            id = id,
            name = ToolNames.MANAGE_PLANNER,
            arguments = buildJsonObject { put("action", "schedule_block"); put("title", title) },
            status = ToolStatus.SUCCEEDED,
            summary = "$title · $time",
            resultMessage = message,
            displayData = buildJsonObject { put("kind", "planner"); put("date", "2026-11-24") },
        )

        val DayPlanConversation = listOf(
            message(1, ChatRole.USER, "Summarize my day"),
            message(
                2,
                ChatRole.ASSISTANT,
                "You have a team stand-up at 9:30, a dentist reminder at 4:00 PM and 2 urgent to-dos: **send the invoice** and **book train tickets**. You're free 11:00 to 1:00.",
                tps = 10.8f,
            ),
            message(3, ChatRole.USER, "Block 2 to 4 for the slides and the gym at 6"),
            message(
                4,
                ChatRole.ASSISTANT,
                "Done. Slides from 2:00 to 4:00 PM and the gym from 6:00 to 7:00 PM.",
                listOf(
                    block("p1", "Slides", "2:00 PM – 4:00 PM", "Planned “Slides” today 14:00-16:00"),
                    block("p2", "Gym", "6:00 PM – 7:00 PM", "Planned “Gym” today 18:00-19:00"),
                ),
                tps = 10.2f,
            ),
        )

        /** Stand-in launcher icons (no PackageManager in tests), honouring the icon style. */
        val FakeIcons = AsyncImagePreviewHandler { request ->
            val data = request.data as? AppIconData ?: return@AsyncImagePreviewHandler createBitmap(1, 1).asImage()
            val size = 144
            val bitmap = createBitmap(size, size)
            val canvas = Canvas(bitmap)
            val hue = ((data.packageName.hashCode() and 0x7fffffff) % 360).toFloat()
            val (background, foreground) = when (data.style) {
                IconStyle.ORIGINAL -> android.graphics.Color.HSVToColor(floatArrayOf(hue, 0.6f, 0.85f)) to android.graphics.Color.WHITE
                IconStyle.GRAYSCALE -> android.graphics.Color.HSVToColor(floatArrayOf(0f, 0f, 0.55f + (hue % 30) / 100f)) to android.graphics.Color.WHITE
                IconStyle.THEMED -> data.themedBackground to data.themedForeground
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background }
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            paint.color = foreground
            paint.textSize = 66f
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.DEFAULT_BOLD
            val letter = data.packageName.substringAfterLast('.').first().uppercase()
            canvas.drawText(letter, size / 2f, size / 2f + 24f, paint)
            bitmap.asImage()
        }
    }
}
