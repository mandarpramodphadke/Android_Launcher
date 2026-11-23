package com.ailauncher.presentation.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SortByAlpha
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.DrawerLayout
import com.ailauncher.domain.model.DrawerTab
import com.ailauncher.presentation.components.AppActionsSheet
import com.ailauncher.presentation.components.AppGridTile
import com.ailauncher.presentation.components.AppListRow
import com.ailauncher.presentation.components.ThemedSystemBarIcons
import kotlinx.coroutines.launch

@Composable
fun AppDrawerScreen(
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: AppDrawerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var sheetApp by remember { mutableStateOf<AppInfo?>(null) }
    val closeDrawer by rememberUpdatedState(onClose)

    ThemedSystemBarIcons()
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    // Pull down past the top of the list to close the drawer.
    val density = LocalDensity.current
    val closeConnection = remember {
        object : NestedScrollConnection {
            var pulled = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    pulled += available.y
                    if (pulled > with(density) { 120.dp.toPx() }) {
                        pulled = 0f
                        closeDrawer()
                    }
                } else if (consumed.y < 0) {
                    pulled = 0f
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y > 2500f) closeDrawer()
                pulled = 0f
                return Velocity.Zero
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f), modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            DrawerTopBar(
                query = state.query,
                onQueryChange = viewModel::onQueryChange,
                layout = state.layout,
                onToggleLayout = viewModel::toggleLayout,
                onOpenSettings = onOpenSettings,
            )
            if (state.query.isBlank()) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    SegmentedButton(
                        selected = state.tab == DrawerTab.ALPHABETICAL,
                        onClick = { viewModel.setTab(DrawerTab.ALPHABETICAL) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        icon = { Icon(Icons.Rounded.SortByAlpha, null, Modifier.size(18.dp)) },
                    ) { Text("A–Z") }
                    SegmentedButton(
                        selected = state.tab == DrawerTab.CATEGORIES,
                        onClick = { viewModel.setTab(DrawerTab.CATEGORIES) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        icon = { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp)) },
                    ) { Text("Smart categories") }
                }
            }
            Box(Modifier.weight(1f).nestedScroll(closeConnection)) {
                when {
                    state.loading -> Unit
                    state.query.isNotBlank() -> SearchResults(state, viewModel::launch) { sheetApp = it }
                    state.tab == DrawerTab.ALPHABETICAL -> AlphabeticalContent(state, viewModel::launch) { sheetApp = it }
                    else -> CategoryContent(
                        state = state,
                        onLaunch = viewModel::launch,
                        onLongPress = { sheetApp = it },
                        onOrganize = { viewModel.organizeWithAi(false) },
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.fillMaxSize().navigationBarsPadding(), snackbar = { data ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { androidx.compose.material3.Snackbar(data) }
        })
    }

    sheetApp?.let { app -> AppActionsSheet(app = app, handler = viewModel, onDismiss = { sheetApp = null }) }
}

@Composable
private fun DrawerTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    layout: DrawerLayout,
    onToggleLayout: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search apps") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Rounded.Close, "Clear") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onToggleLayout) {
            Icon(
                if (layout == DrawerLayout.GRID) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                contentDescription = if (layout == DrawerLayout.GRID) "Show as list" else "Show as grid",
            )
        }
        IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Launcher settings") }
    }
}

@Composable
private fun SearchResults(state: DrawerUiState, onLaunch: (AppInfo) -> Unit, onLongPress: (AppInfo) -> Unit) {
    if (state.searchResults.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopCenter) {
            Text("No apps match \"${state.query}\"", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(state.columns),
        contentPadding = PaddingValues(12.dp),
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
    ) {
        items(state.searchResults, key = { it.componentKey }) { app ->
            AppGridTile(app = app, onClick = { onLaunch(app) }, onLongClick = { onLongPress(app) })
        }
    }
}

@Composable
private fun AlphabeticalContent(state: DrawerUiState, onLaunch: (AppInfo) -> Unit, onLongPress: (AppInfo) -> Unit) {
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // Index of each section header in the lazy list (headers occupy one item each).
    val headerIndex = remember(state.alphabetical) {
        var index = 0
        state.alphabetical.associate { section ->
            val at = index
            index += 1 + section.apps.size
            section.key to at
        }
    }
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            if (state.layout == DrawerLayout.GRID) {
                AlphabeticalGrid(state, gridState, onLaunch, onLongPress)
            } else {
                AlphabeticalList(state, listState, onLaunch, onLongPress)
            }
        }
        FastScroller(
            letters = state.alphabetical.map { it.key },
            onLetter = { letter ->
                val index = headerIndex[letter] ?: return@FastScroller
                scope.launch {
                    if (state.layout == DrawerLayout.GRID) gridState.scrollToItem(index) else listState.scrollToItem(index)
                }
            },
            modifier = Modifier.fillMaxHeight().navigationBarsPadding(),
        )
    }
}

@Composable
private fun AlphabeticalGrid(
    state: DrawerUiState,
    gridState: LazyGridState,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(state.columns),
        contentPadding = PaddingValues(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 96.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        state.alphabetical.forEach { section ->
            item(key = "header-${section.key}", span = { GridItemSpan(maxLineSpan) }) { SectionHeader(section.key) }
            items(section.apps, key = { it.componentKey }) { app ->
                AppGridTile(app = app, onClick = { onLaunch(app) }, onLongClick = { onLongPress(app) })
            }
        }
    }
}

@Composable
private fun AlphabeticalList(
    state: DrawerUiState,
    listState: LazyListState,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
) {
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 96.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        state.alphabetical.forEach { section ->
            item(key = "header-${section.key}") { SectionHeader(section.key) }
            items(section.apps, key = { it.componentKey }) { app ->
                AppListRow(app = app, onClick = { onLaunch(app) }, onLongClick = { onLongPress(app) }, supportingText = app.category?.displayName)
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp),
    )
}

/** Draggable A–Z rail; jumps the list to the touched letter and shows a preview bubble. */
@Composable
private fun FastScroller(letters: List<String>, onLetter: (String) -> Unit, modifier: Modifier = Modifier) {
    if (letters.size < 2) return
    val haptics = LocalHapticFeedback.current
    var height by remember { mutableIntStateOf(1) }
    var active by remember { mutableStateOf<String?>(null) }
    val currentOnLetter by rememberUpdatedState(onLetter)

    fun select(y: Float) {
        val index = ((y / height.coerceAtLeast(1)) * letters.size).toInt().coerceIn(0, letters.lastIndex)
        val letter = letters[index]
        if (letter != active) {
            active = letter
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            currentOnLetter(letter)
        }
    }

    Box(modifier) {
        Column(
            Modifier
                .fillMaxHeight()
                .width(28.dp)
                .padding(vertical = 8.dp)
                .onSizeChanged { height = it.height }
                .pointerInput(letters) {
                    detectVerticalDragGestures(
                        onDragStart = { select(it.y) },
                        onDragEnd = { active = null },
                        onDragCancel = { active = null },
                    ) { change, _ ->
                        change.consume()
                        select(change.position.y)
                    }
                }
                .pointerInput(letters) {
                    detectTapGestures(onPress = {
                        select(it.y)
                        tryAwaitRelease()
                        active = null
                    })
                },
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            letters.forEach { letter ->
                Text(
                    letter,
                    fontSize = 11.sp,
                    fontWeight = if (letter == active) FontWeight.Bold else FontWeight.Medium,
                    color = if (letter == active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AnimatedVisibility(visible = active != null, modifier = Modifier.align(Alignment.Center)) {
            Box(
                Modifier
                    .padding(end = 48.dp)
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(active.orEmpty(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun CategoryContent(
    state: DrawerUiState,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
    onOrganize: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "banner") { CategorizationBanner(state, onOrganize) }
        state.categories.forEach { section ->
            val title = section.category?.displayName ?: "Not sorted yet"
            item(key = "cat-$title") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("${section.apps.size}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (state.layout == DrawerLayout.LIST) {
                            section.apps.forEach { app ->
                                AppListRow(app = app, onClick = { onLaunch(app) }, onLongClick = { onLongPress(app) })
                            }
                        } else {
                            section.apps.chunked(state.columns).forEach { row ->
                                Row(Modifier.padding(horizontal = 4.dp)) {
                                    row.forEach { app ->
                                        AppGridTile(
                                            app = app,
                                            onClick = { onLaunch(app) },
                                            onLongClick = { onLongPress(app) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    repeat(state.columns - row.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorizationBanner(state: DrawerUiState, onOrganize: () -> Unit) {
    val progress = state.progress
    when {
        progress.running -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (progress.total > 0) "AI is organizing your apps… ${progress.categorized}/${progress.total}"
                        else "AI is organizing your apps…",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.size(8.dp))
                if (progress.total > 0) {
                    LinearProgressIndicator(
                        progress = { progress.categorized.toFloat() / progress.total },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }
        }
        state.notSortedByAi > 0 -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "${state.notSortedByAi} apps use quick-guess categories",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        if (state.modelReady) "Let the on-device AI sort them - nothing leaves your phone."
                        else "Download the on-device model for smarter sorting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                if (state.modelReady) {
                    Spacer(Modifier.width(12.dp))
                    Button(onClick = onOrganize) { Text("Organize") }
                }
            }
        }
        else -> Unit
    }
}
