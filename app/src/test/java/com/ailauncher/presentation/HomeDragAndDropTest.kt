package com.ailauncher.presentation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.core.graphics.createBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.ailauncher.domain.home.DropAction
import com.ailauncher.domain.model.AppCategory
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.CategorySource
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.domain.model.FolderAccent
import com.ailauncher.domain.model.FolderRule
import com.ailauncher.domain.model.GridPosition
import com.ailauncher.domain.model.HomeItem
import com.ailauncher.domain.model.HomeItemContent
import com.ailauncher.domain.model.LauncherSettings
import com.ailauncher.domain.model.LauncherWidgetConfig
import com.ailauncher.domain.model.LauncherWidgetKind
import com.ailauncher.domain.model.TodoItem
import com.ailauncher.domain.model.TodoPriority
import com.ailauncher.presentation.home.LauncherWidgetCallbacks
import com.ailauncher.presentation.home.LauncherWidgetData
import com.ailauncher.presentation.home.DropTarget
import com.ailauncher.presentation.home.HomeCallbacks
import com.ailauncher.presentation.home.HomeContent
import com.ailauncher.presentation.home.HomeUiState
import com.ailauncher.presentation.theme.AiLauncherTheme
import com.ailauncher.presentation.theme.LocalLauncherStyle
import com.ailauncher.presentation.theme.WallpaperTint
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Drag and drop on the home screen, driven by real touch input: long-press then drag a tile onto
 * another app, a folder or an empty cell; release without moving for the menu; tap to open. Also
 * the to-do widget's buttons.
 */
@OptIn(ExperimentalCoilApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class, qualifiers = "w393dp-h851dp-xhdpi")
class HomeDragAndDropTest {

    @get:Rule val compose = createComposeRule()

    private fun app(label: String) = AppInfo(
        componentKey = "com.example.${label.lowercase()}/.Main",
        packageName = "com.example.${label.lowercase()}",
        activityName = ".Main",
        systemLabel = label,
        customLabel = null,
        category = AppCategory.SOCIAL,
        categorySource = CategorySource.AI,
        isHidden = false,
        dockPosition = null,
        launchCount = 0,
        lastLaunchedAt = 0,
        isSystemApp = false,
        installedAt = 0,
        updatedAt = 0,
    )

    private val chat = app("Chat")
    private val maps = app("Maps")
    private val notes = app("Notes")
    private val work = CustomFolder(1, "Work", null, FolderAccent.TEAL, 0, listOf("com.example.mail"), null)
    private val fresh = CustomFolder(2, "Fresh", null, FolderAccent.SKY, 1, emptyList(), null, rule = FolderRule.RecentlyInstalled())

    private val chatItem = HomeItem(1, GridPosition(0, 0, 2), HomeItemContent.App(chat.componentKey))
    private val mapsItem = HomeItem(2, GridPosition(0, 1, 2), HomeItemContent.App(maps.componentKey))
    private val workItem = HomeItem(3, GridPosition(0, 2, 2), HomeItemContent.Folder(work.id))
    private val freshItem = HomeItem(4, GridPosition(0, 3, 2), HomeItemContent.Folder(fresh.id))
    private val notesItem = HomeItem(5, GridPosition(0, 0, 3), HomeItemContent.App(notes.componentKey))

    private val drops = mutableListOf<Pair<HomeItem, DropTarget?>>()
    private val menus = mutableListOf<AppInfo>()
    private val launches = mutableListOf<AppInfo>()
    private val toggled = mutableListOf<TodoItem>()
    private val added = mutableListOf<String?>()
    private val edited = mutableListOf<TodoItem>()
    private val widgetEdits = mutableListOf<HomeItem>()

    private val milk = TodoItem(1, "Milk", null, "Shopping", TodoPriority.MEDIUM, null, false, 1, null)
    private val rent = TodoItem(2, "Pay rent", null, TodoItem.DEFAULT_LIST, TodoPriority.HIGH, null, false, 2, null)
    private val shoppingWidget = HomeItem(6, GridPosition(0, 0, 4, 4, 2), HomeItemContent.LauncherWidget(LauncherWidgetConfig(LauncherWidgetKind.TODO_LIST, "Shopping")))

    private fun show(items: List<HomeItem> = listOf(chatItem, mapsItem, workItem, freshItem, notesItem)) {
        val state = HomeUiState(
            now = ZonedDateTime.of(2026, 11, 25, 9, 0, 0, 0, ZoneId.of("UTC")),
            apps = listOf(chat, maps, notes).associateBy { it.componentKey },
            homeItems = items,
            pageCount = 1,
            folders = mapOf(work.id to work, fresh.id to fresh),
            settings = LauncherSettings(onboardingCompleted = true),
            widgetData = LauncherWidgetData(todos = listOf(milk, rent)),
        )
        compose.setContent {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAsyncImagePreviewHandler provides AsyncImagePreviewHandler { createBitmap(8, 8).asImage() },
            ) {
                AiLauncherTheme(state.settings) {
                    Box(Modifier.fillMaxSize()) {
                        HomeContent(
                            state = state,
                            tint = WallpaperTint.Light,
                            style = LocalLauncherStyle.current,
                            pagerState = rememberPagerState { 1 },
                            queryState = rememberTextFieldState(),
                            clip = null,
                            widgetHost = null,
                            editingWidgetId = null,
                            callbacks = HomeCallbacks(
                                onLaunch = { app, _ -> launches += app },
                                onAppLongPress = { app, _ -> menus += app },
                                dragEnabled = true,
                                onDropItem = { item, target -> drops += item to target },
                                onWidgetLongPress = { widgetEdits += it },
                                widgets = LauncherWidgetCallbacks(
                                    onToggleTodo = { toggled += it },
                                    onAddTodo = { added += it },
                                    onEditTodo = { edited += it },
                                ),
                            ),
                        )
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    private fun centerOf(label: String): Offset = compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot.center

    /** Long-presses [from], drags it in small steps onto [to] (or by [by]) and lets go. */
    private fun dragFrom(from: String, to: String? = null, by: Offset? = null) {
        val delta = by ?: (centerOf(to!!) - centerOf(from))
        compose.onNodeWithText(from).performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            repeat(10) { moveBy(delta / 10f) }
        }
        compose.waitForIdle()
        compose.onNodeWithText(from).performTouchInput { up() }
        compose.waitForIdle()
    }

    @Test
    fun appDroppedOnAnAppMakesAFolder() {
        show()
        dragFrom("Chat", to = "Maps")
        assertThat(drops).hasSize(1)
        val (item, target) = drops.single()
        assertThat(item).isEqualTo(chatItem)
        assertThat((target as DropTarget.Cell).action).isEqualTo(DropAction.CreateFolder(mapsItem))
        assertThat(menus).isEmpty()
    }

    @Test
    fun appDroppedOnFoldersJoinsOrIsTurnedAway() {
        show()
        dragFrom("Notes", to = "Work")
        assertThat((drops.last().second as DropTarget.Cell).action).isEqualTo(DropAction.AddToFolder(workItem, work.id))
        dragFrom("Notes", to = "Fresh")
        assertThat((drops.last().second as DropTarget.Cell).action).isEqualTo(DropAction.SmartFolder(fresh.id))
    }

    @Test
    fun appDroppedOnAnEmptyCellMoves() {
        show()
        // Two cells to the right of Notes (row 4, column 3) is empty.
        val cellWidth = centerOf("Maps").x - centerOf("Chat").x
        dragFrom("Notes", by = Offset(cellWidth * 2, 0f))
        val target = drops.single().second as DropTarget.Cell
        assertThat(target.action).isEqualTo(DropAction.Move(GridPosition(0, 2, 3)))
    }

    @Test
    fun longPressWithoutMovingOpensTheMenuAndTapOpensTheApp() {
        show()
        compose.onNodeWithText("Maps").performTouchInput {
            down(center)
            advanceEventTime(viewConfiguration.longPressTimeoutMillis + 100)
            up()
        }
        compose.waitForIdle()
        assertThat(menus).containsExactly(maps)
        assertThat(drops).isEmpty()

        compose.onNodeWithText("Chat").performTouchInput {
            down(center)
            advanceEventTime(50)
            up()
        }
        compose.waitForIdle()
        assertThat(launches).containsExactly(chat)
    }

    @Test
    fun todoWidgetTicksOffEditsAndAddsInItsList() {
        show(listOf(chatItem, shoppingWidget))
        // Only the widget's list is shown.
        assertThat(compose.onAllNodesWithText("Pay rent").fetchSemanticsNodes()).isEmpty()
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))[0].performClick()
        assertThat(toggled).containsExactly(milk)
        compose.onNodeWithText("Milk").performClick()
        assertThat(edited).containsExactly(milk)
        compose.onNodeWithContentDescription("Add a to-do").performClick()
        assertThat(added).containsExactly("Shopping")
        compose.onNodeWithText("Milk").performTouchInput { longClick() }
        assertThat(widgetEdits).containsExactly(shoppingWidget)
    }
}
