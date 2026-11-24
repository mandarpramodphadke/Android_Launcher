package com.ailauncher.presentation

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.ailauncher.data.packages.AppIconData
import com.ailauncher.domain.model.AppCategory
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.AppTheme
import com.ailauncher.domain.model.CategorizationProgress
import com.ailauncher.domain.model.CategorySource
import com.ailauncher.domain.model.DrawerLayout
import com.ailauncher.domain.model.DrawerTab
import com.ailauncher.domain.model.IconStyle
import com.ailauncher.domain.model.LauncherSettings
import com.ailauncher.domain.model.ThemeMode
import com.ailauncher.domain.model.UpcomingEvent
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.input.rememberTextFieldState
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.domain.model.DockEntry
import com.ailauncher.domain.model.FolderAccent
import com.ailauncher.domain.model.GridPosition
import com.ailauncher.domain.model.HomeItem
import com.ailauncher.domain.model.HomeItemContent
import com.ailauncher.domain.model.PlannerBlock
import com.ailauncher.domain.model.SmartExtraction
import com.ailauncher.domain.model.SmartPill
import com.ailauncher.presentation.drawer.AlphaSection
import com.ailauncher.presentation.drawer.DrawerCallbacks
import com.ailauncher.presentation.home.Glance
import com.ailauncher.presentation.home.HomeCallbacks
import com.ailauncher.presentation.drawer.CategorySection
import com.ailauncher.presentation.drawer.DrawerContent
import com.ailauncher.presentation.drawer.DrawerUiState
import com.ailauncher.presentation.home.HomeContent
import com.ailauncher.presentation.home.HomeUiState
import com.ailauncher.presentation.settings.ThemePicker
import com.ailauncher.presentation.theme.AiLauncherTheme
import com.ailauncher.presentation.theme.LocalLauncherStyle
import com.ailauncher.presentation.theme.WallpaperTint
import com.google.common.truth.Truth.assertWithMessage
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
    private fun Home(settings: LauncherSettings, wallpaper: Brush, tint: WallpaperTint, page: Int = 0) {
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
                        homeItems = HomeItems,
                        pageCount = 2,
                        folders = mapOf(WorkFolder.id to WorkFolder, TravelFolder.id to TravelFolder),
                        pills = listOf(SmartPill("otp", "Bank", SmartExtraction.Otp("482913"), Now.toInstant().toEpochMilli())),
                        badges = mapOf(Apps[1].packageName to 3),
                        glance = Glance(
                            openTodos = 3,
                            urgentTodos = 1,
                            nextBlock = PlannerBlock(1, Now.toLocalDate(), 10 * 60, 90, "Deep work", null, false),
                        ),
                        settings = settings,
                    ),
                    tint = tint,
                    style = LocalLauncherStyle.current,
                    pagerState = rememberPagerState(initialPage = page) { 2 },
                    queryState = rememberTextFieldState(),
                    clip = null,
                    widgetHost = null,
                    editingWidgetId = null,
                    callbacks = HomeCallbacks(),
                )
            }
        }
    }

    @Composable
    private fun Drawer(settings: LauncherSettings, tab: DrawerTab, layout: DrawerLayout) {
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
                        folders = listOf(WorkFolder, TravelFolder),
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
