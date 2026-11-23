package com.ailauncher.presentation.home

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.UpcomingEvent
import com.ailauncher.presentation.components.AppActionsSheet
import com.ailauncher.presentation.components.AppListItem
import com.ailauncher.presentation.components.AppGridTile
import com.ailauncher.presentation.components.SystemBarIcons
import com.ailauncher.presentation.theme.WallpaperTint
import com.ailauncher.presentation.theme.rememberWallpaperTint
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@Composable
fun LauncherHomeScreen(
    onOpenDrawer: () -> Unit,
    onOpenChat: (prompt: String?) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tint = rememberWallpaperTint()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }
    var sheetApp by remember { mutableStateOf<AppInfo?>(null) }
    var showLockSetup by remember { mutableStateOf(false) }
    var showHomeMenu by remember { mutableStateOf(false) }

    SystemBarIcons(darkIcons = tint.shadow == null)

    // The launcher never "goes back" from home; Back only clears the command bar.
    BackHandler {
        focusManager.clearFocus()
        viewModel.onQueryChange("")
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                HomeEvent.LockSetupNeeded -> showLockSetup = true
                is HomeEvent.Message -> snackbar.showSnackbar(event.text)
            }
        }
    }

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { spoken ->
            if (spoken.isNotBlank()) onOpenChat(spoken)
        }
    }
    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Ask your assistant")
    }
    val voiceAvailable = remember { voiceIntent.resolveActivity(context.packageManager) != null }
    val currentOpenDrawer by rememberUpdatedState(onOpenDrawer)
    val currentOpenChat by rememberUpdatedState(onOpenChat)

    Box(
        Modifier
            .fillMaxSize()
            .homeGestures(
                onSwipeUp = { currentOpenDrawer() },
                onSwipeDown = viewModel::onSwipeDown,
                onSwipeLeft = { currentOpenChat(null) },
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { focusManager.clearFocus() },
                    onDoubleTap = { viewModel.onDoubleTap() },
                    onLongPress = { showHomeMenu = true },
                )
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp),
        ) {
            Spacer(Modifier.height(56.dp))
            ClockWidget(state.now, state.use24Hour, tint, onClick = viewModel::openClockApp)
            state.upcoming?.let { event ->
                Spacer(Modifier.height(12.dp))
                UpcomingPill(event, state.now, state.use24Hour, tint, onClick = viewModel::openClockApp)
            }
            Spacer(Modifier.weight(1f))

            AnimatedVisibility(visible = state.searchResults.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                SearchResultsRow(state.searchResults, onLaunch = viewModel::launch, onLongPress = { sheetApp = it })
            }
            Spacer(Modifier.height(12.dp))
            DockBar(state.dock, onLaunch = viewModel::launch, onLongPress = { sheetApp = it })
            Spacer(Modifier.height(12.dp))
            if (state.settings.showAiCommandBar) {
                AiCommandBar(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    onSubmit = { text ->
                        focusManager.clearFocus()
                        viewModel.onQueryChange("")
                        onOpenChat(text)
                    },
                    onOpenChat = { onOpenChat(null) },
                    onVoice = if (voiceAvailable) ({ voiceLauncher.launch(voiceIntent) }) else null,
                )
            }
            SwipeUpHint(tint, onClick = onOpenDrawer)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding())
    }

    sheetApp?.let { app ->
        AppActionsSheet(app = app, handler = viewModel, onDismiss = { sheetApp = null }, showDockReorder = true)
    }

    if (showHomeMenu) {
        ModalBottomSheet(onDismissRequest = { showHomeMenu = false }) {
            AppListItem(
                headlineContent = { Text("Wallpaper") },
                leadingContent = { Icon(Icons.Rounded.Wallpaper, null) },
                onClick = {
                    showHomeMenu = false
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "Choose wallpaper")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                },
            )
            AppListItem(
                headlineContent = { Text("Launcher settings") },
                leadingContent = { Icon(Icons.Rounded.Settings, null) },
                onClick = {
                    showHomeMenu = false
                    onOpenSettings()
                },
            )
            AppListItem(
                headlineContent = { Text("AI assistant") },
                leadingContent = { Icon(Icons.Rounded.AutoAwesome, null) },
                modifier = Modifier.navigationBarsPadding(),
                onClick = {
                    showHomeMenu = false
                    onOpenChat(null)
                },
            )
        }
    }

    if (showLockSetup) {
        AlertDialog(
            onDismissRequest = { showLockSetup = false },
            title = { Text("Double-tap to lock") },
            text = {
                Text(
                    "To turn the screen off with a double tap, enable \"AI Launcher gestures\" in Accessibility settings. " +
                        "It is used only to lock the screen and open notifications - it never reads what is on your screen. " +
                        "Alternatively, allow screen-lock as a device admin (this may require your PIN instead of fingerprint on next unlock).",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showLockSetup = false
                    runCatching { context.startActivity(viewModel.accessibilitySettingsIntent()) }
                }) { Text("Accessibility settings") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showLockSetup = false
                        runCatching { context.startActivity(viewModel.deviceAdminIntent().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }) { Text("Device admin") }
                    TextButton(onClick = {
                        showLockSetup = false
                        viewModel.disableDoubleTapLock()
                    }) { Text("Turn off") }
                }
            },
        )
    }
}

/** Swipe up = app drawer, swipe down = notifications, swipe left = AI chat. */
private fun Modifier.homeGestures(
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onSwipeLeft: () -> Unit,
): Modifier = this.then(
    Modifier.pointerInput(Unit) {
        val threshold = 64.dp.toPx()
        var total = Offset.Zero
        detectDragGestures(
            onDragStart = { total = Offset.Zero },
            onDragEnd = {
                val (dx, dy) = total
                when {
                    abs(dy) >= abs(dx) && dy < -threshold -> onSwipeUp()
                    abs(dy) >= abs(dx) && dy > threshold -> onSwipeDown()
                    abs(dx) > abs(dy) && dx < -threshold -> onSwipeLeft()
                }
            },
            onDrag = { change, amount ->
                change.consume()
                total += amount
            },
        )
    },
)

@Composable
private fun ClockWidget(now: ZonedDateTime, use24Hour: Boolean, tint: WallpaperTint, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val time = now.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm", locale))
    val meridiem = if (use24Hour) null else now.format(DateTimeFormatter.ofPattern("a", locale))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
    Column(Modifier.clickable(onClick = onClick, onClickLabel = "Open clock")) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(time, style = tint.clockStyle)
            meridiem?.let {
                Spacer(Modifier.width(6.dp))
                Text(it, style = TextStyle(fontSize = 20.sp, color = tint.secondary, shadow = tint.shadow), modifier = Modifier.padding(bottom = 14.dp))
            }
        }
        Text(date, style = MaterialTheme.typography.titleMedium.copy(shadow = tint.shadow, fontWeight = FontWeight.Medium), color = tint.secondary)
    }
}

@Composable
private fun UpcomingPill(event: UpcomingEvent, now: ZonedDateTime, use24Hour: Boolean, tint: WallpaperTint, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val at = Instant.ofEpochMilli(event.triggerAtMillis).atZone(now.zone)
    val time = at.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", locale))
    val day = when (at.toLocalDate()) {
        now.toLocalDate() -> ""
        now.toLocalDate().plusDays(1) -> "Tomorrow "
        else -> at.format(DateTimeFormatter.ofPattern("EEE ", locale))
    }
    val darkText = tint.shadow == null
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (darkText) Color(0x22000000) else Color(0x33000000),
        contentColor = tint.content,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (event.kind == UpcomingEvent.Kind.ALARM) Icons.Rounded.Alarm else Icons.Rounded.NotificationsActive,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "$day$time · ${event.title}",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DockBar(apps: List<AppInfo>, onLaunch: (AppInfo) -> Unit, onLongPress: (AppInfo) -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (apps.isEmpty()) {
            Text(
                "Long-press any app in the drawer to pin it here",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(20.dp),
            )
        } else {
            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                apps.forEach { app ->
                    AppGridTile(
                        app = app,
                        onClick = { onLaunch(app) },
                        onLongClick = { onLongPress(app) },
                        showLabel = false,
                        iconSize = 54.dp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultsRow(apps: List<AppInfo>, onLaunch: (AppInfo) -> Unit, onLongPress: (AppInfo) -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            apps.forEach { app ->
                AppGridTile(
                    app = app,
                    onClick = { onLaunch(app) },
                    onLongClick = { onLongPress(app) },
                    iconSize = 44.dp,
                    modifier = Modifier.weight(1f),
                )
            }
            repeat(5 - apps.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** The always-visible AI bar: type to search apps, press send to ask the assistant. */
@Composable
private fun AiCommandBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onOpenChat: () -> Unit,
    onVoice: (() -> Unit)?,
) {
    val submit = rememberUpdatedState(onSubmit)
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, end = 4.dp)) {
            IconButton(onClick = onOpenChat) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = "Open AI assistant", tint = MaterialTheme.colorScheme.primary)
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Ask Aura or search apps…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (query.isNotBlank()) submit.value(query.trim()) }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f),
            )
            when {
                query.isNotBlank() -> IconButton(onClick = { submit.value(query.trim()) }) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Ask the assistant", tint = MaterialTheme.colorScheme.primary)
                }
                onVoice != null -> IconButton(onClick = onVoice) {
                    Icon(Icons.Rounded.Mic, contentDescription = "Voice input")
                }
            }
        }
    }
}

@Composable
private fun SwipeUpHint(tint: WallpaperTint, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(top = 4.dp), contentAlignment = Alignment.Center) {
        IconButton(onClick = onClick) {
            Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "All apps", tint = tint.secondary)
        }
    }
}
