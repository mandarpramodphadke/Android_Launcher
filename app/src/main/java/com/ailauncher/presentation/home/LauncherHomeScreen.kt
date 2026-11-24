package com.ailauncher.presentation.home

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.AppTheme
import com.ailauncher.domain.model.UpcomingEvent
import com.ailauncher.presentation.components.AppActionsSheet
import com.ailauncher.presentation.components.AppGridTile
import com.ailauncher.presentation.components.AppListItem
import com.ailauncher.presentation.components.SystemBarIcons
import com.ailauncher.presentation.components.brushTint
import com.ailauncher.presentation.theme.LauncherColorSchemes
import com.ailauncher.presentation.theme.LauncherStyle
import com.ailauncher.presentation.theme.LocalLauncherStyle
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
    val style = LocalLauncherStyle.current
    val tint = rememberWallpaperTint(style.homeTextColor)
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }
    var sheetApp by remember { mutableStateOf<AppInfo?>(null) }
    var showLockSetup by remember { mutableStateOf(false) }
    var showHomeMenu by remember { mutableStateOf(false) }

    SystemBarIcons(darkIcons = tint.darkText)

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
    val submitQuery: (String) -> Unit = { text ->
        focusManager.clearFocus()
        viewModel.onQueryChange("")
        onOpenChat(text)
    }
    val voice: (() -> Unit)? = if (voiceAvailable) ({ voiceLauncher.launch(voiceIntent) }) else null

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
        HomeContent(
            state = state,
            tint = tint,
            style = style,
            onOpenClock = viewModel::openClockApp,
            onLaunch = viewModel::launch,
            onAppLongPress = { sheetApp = it },
            onQueryChange = viewModel::onQueryChange,
            onSubmitQuery = submitQuery,
            onOpenChat = { onOpenChat(null) },
            onVoice = voice,
            onOpenDrawer = onOpenDrawer,
        )
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

/**
 * The home screen's visible content, drawn over the wallpaper. Stateless (gestures, sheets and
 * dialogs live in [LauncherHomeScreen]), so it can be rendered in screenshot tests.
 */
@Composable
internal fun HomeContent(
    state: HomeUiState,
    tint: WallpaperTint,
    style: LauncherStyle,
    onOpenClock: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onAppLongPress: (AppInfo) -> Unit,
    onQueryChange: (String) -> Unit,
    onSubmitQuery: (String) -> Unit,
    onOpenChat: () -> Unit,
    onVoice: (() -> Unit)?,
    onOpenDrawer: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        if (style.homeTextShade) ReadabilityScrims(tint, style.minimal)
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = if (style.minimal) 28.dp else 20.dp),
        ) {
            Spacer(Modifier.height(if (style.minimal) 72.dp else 56.dp))
            ClockWidget(state.now, state.use24Hour, tint, style, onClick = onOpenClock)
            state.upcoming?.let { event ->
                Spacer(Modifier.height(12.dp))
                UpcomingPill(event, state.now, state.use24Hour, tint, plain = style.minimal, onClick = onOpenClock)
            }
            Spacer(Modifier.weight(1f))

            if (style.minimal) {
                // Distraction-free: favourites as words; typing filters them into search results.
                MinimalAppList(
                    apps = if (state.query.isBlank()) state.dock else state.searchResults,
                    searching = state.query.isNotBlank(),
                    tint = tint,
                    onLaunch = onLaunch,
                    onLongPress = onAppLongPress,
                )
                Spacer(Modifier.height(20.dp))
                if (state.settings.showAiCommandBar) {
                    MinimalCommandBar(
                        query = state.query,
                        onQueryChange = onQueryChange,
                        onSubmit = onSubmitQuery,
                        onOpenChat = onOpenChat,
                        onVoice = onVoice,
                        tint = tint,
                    )
                }
                Spacer(Modifier.height(16.dp))
            } else {
                AnimatedVisibility(visible = state.searchResults.isNotEmpty(), enter = fadeIn(), exit = fadeOut()) {
                    SearchResultsRow(state.searchResults, onLaunch = onLaunch, onLongPress = onAppLongPress)
                }
                Spacer(Modifier.height(12.dp))
                DockBar(state.dock, onLaunch = onLaunch, onLongPress = onAppLongPress)
                Spacer(Modifier.height(12.dp))
                if (state.settings.showAiCommandBar) {
                    AiCommandBar(
                        query = state.query,
                        onQueryChange = onQueryChange,
                        onSubmit = onSubmitQuery,
                        onOpenChat = onOpenChat,
                        onVoice = onVoice,
                    )
                }
                SwipeUpHint(tint, onClick = onOpenDrawer)
            }
        }
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

/**
 * Gradients at the top and bottom of the home screen in the text's opposite polarity (dark behind
 * light text, light behind dark text), so the clock and labels stay readable when the part of the
 * wallpaper behind them differs from the image as a whole.
 */
@Composable
private fun ReadabilityScrims(tint: WallpaperTint, minimal: Boolean) {
    // Same polarity as the text's backing, so on a typical wallpaper (which already suits the
    // text) the shade is barely visible; it only does real work over a mismatched region.
    val edge = tint.scrim.copy(alpha = 0.50f)
    val mid = tint.scrim.copy(alpha = 0.24f)
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(0.38f)
                .background(Brush.verticalGradient(0f to edge, 0.55f to mid, 1f to Color.Transparent)),
        )
        Spacer(Modifier.weight(if (minimal) 0.10f else 0.27f))
        Box(
            Modifier
                .fillMaxWidth()
                .weight(if (minimal) 0.52f else 0.35f)
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.45f to mid, 1f to edge)),
        )
    }
}

@Composable
private fun ClockWidget(now: ZonedDateTime, use24Hour: Boolean, tint: WallpaperTint, style: LauncherStyle, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val time = now.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm", locale))
    val meridiem = if (use24Hour) null else now.format(DateTimeFormatter.ofPattern("a", locale))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", locale))
    Column(Modifier.clickable(onClick = onClick, onClickLabel = "Open clock")) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(time, style = clockTextStyle(tint, style))
            meridiem?.let {
                Spacer(Modifier.width(6.dp))
                Text(
                    it,
                    style = TextStyle(fontSize = 20.sp, color = tint.secondary, shadow = tint.shadow),
                    modifier = Modifier.padding(bottom = if (style.minimal) 11.dp else 14.dp),
                )
            }
        }
        Text(date, style = MaterialTheme.typography.titleMedium.copy(shadow = tint.shadow, fontWeight = FontWeight.Medium), color = tint.secondary)
    }
}

private val NeonCyan = Color(0xFF00E5FF)

/** The big clock, styled per theme: gradient (Colorful), glow (Neon), hairline (Monochrome, minimal). */
private fun clockTextStyle(tint: WallpaperTint, style: LauncherStyle): TextStyle {
    val base = TextStyle(
        fontSize = if (style.minimal) 60.sp else 76.sp,
        fontWeight = if (style.minimal || style.theme == AppTheme.MONOCHROME) FontWeight.Thin else FontWeight.Light,
        letterSpacing = (-2).sp,
        color = tint.content,
        shadow = tint.shadow,
    )
    return when (style.theme) {
        AppTheme.COLORFUL -> {
            // Accents of the variant that suits the wallpaper: deep tones on light, pastels on dark.
            val accents = LauncherColorSchemes.colorful(dark = !tint.darkText)
            base.copy(brush = Brush.linearGradient(listOf(accents.primary, accents.secondary, accents.tertiary)))
        }
        AppTheme.NEON -> if (tint.darkText) {
            base.copy(shadow = Shadow(NeonCyan, blurRadius = 22f))
        } else {
            base.copy(color = Color(0xFFE6FDFF), shadow = Shadow(NeonCyan, blurRadius = 26f))
        }
        else -> base
    }
}

@Composable
private fun UpcomingPill(
    event: UpcomingEvent,
    now: ZonedDateTime,
    use24Hour: Boolean,
    tint: WallpaperTint,
    plain: Boolean,
    onClick: () -> Unit,
) {
    val locale = LocalConfiguration.current.locales[0]
    val at = Instant.ofEpochMilli(event.triggerAtMillis).atZone(now.zone)
    val time = at.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a", locale))
    val day = when (at.toLocalDate()) {
        now.toLocalDate() -> ""
        now.toLocalDate().plusDays(1) -> "Tomorrow "
        else -> at.format(DateTimeFormatter.ofPattern("EEE ", locale))
    }
    val label: @Composable () -> Unit = {
        Row(
            Modifier.padding(horizontal = if (plain) 0.dp else 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (event.kind == UpcomingEvent.Kind.ALARM) Icons.Rounded.Alarm else Icons.Rounded.NotificationsActive,
                contentDescription = null,
                tint = if (plain) tint.secondary else tint.content,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "$day$time · ${event.title}",
                style = MaterialTheme.typography.labelLarge.copy(shadow = if (plain) tint.shadow else null),
                color = if (plain) tint.secondary else tint.content,
                maxLines = 1,
            )
        }
    }
    if (plain) {
        Box(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick)) { label() }
    } else {
        // The chip's backing has the opposite polarity of its text, so it reads on any wallpaper.
        Surface(onClick = onClick, shape = RoundedCornerShape(50), color = tint.chip, contentColor = tint.content) { label() }
    }
}

/** Glowing outline that the Neon theme gives its floating surfaces. */
@Composable
private fun themedOutline(): BorderStroke? {
    if (LocalLauncherStyle.current.theme != AppTheme.NEON) return null
    val scheme = MaterialTheme.colorScheme
    return BorderStroke(1.dp, Brush.horizontalGradient(listOf(scheme.primary.copy(alpha = 0.85f), scheme.secondary.copy(alpha = 0.85f))))
}

/** Multi-hue brush for accent icons in the Colorful and Neon themes. */
@Composable
private fun accentBrush(): Brush? {
    val theme = LocalLauncherStyle.current.theme
    if (theme != AppTheme.COLORFUL && theme != AppTheme.NEON) return null
    val scheme = MaterialTheme.colorScheme
    return Brush.linearGradient(listOf(scheme.primary, scheme.secondary, scheme.tertiary))
}

@Composable
private fun DockBar(apps: List<AppInfo>, onLaunch: (AppInfo) -> Unit, onLongPress: (AppInfo) -> Unit) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.74f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = themedOutline(),
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
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = themedOutline(),
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
    val sparkle = accentBrush()
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 3.dp,
        border = themedOutline(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp, end = 4.dp)) {
            IconButton(onClick = onOpenChat) {
                Icon(
                    Icons.Rounded.AutoAwesome,
                    contentDescription = "Open AI assistant",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = if (sparkle != null) Modifier.brushTint(sparkle) else Modifier,
                )
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
                    Icon(Icons.Rounded.Mic, contentDescription = "Voice input", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Minimal mode's favourites (or live search results): large words straight on the wallpaper. */
@Composable
private fun MinimalAppList(
    apps: List<AppInfo>,
    searching: Boolean,
    tint: WallpaperTint,
    onLaunch: (AppInfo) -> Unit,
    onLongPress: (AppInfo) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val labelStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Light, shadow = tint.shadow)
    Column(Modifier.fillMaxWidth()) {
        if (apps.isEmpty()) {
            Text(
                if (searching) "No matching apps · press enter to ask the assistant"
                else "Long-press an app in the drawer and add it to the dock to list it here",
                style = MaterialTheme.typography.bodyMedium.copy(shadow = tint.shadow),
                color = tint.secondary,
            )
        }
        apps.forEach { app ->
            Text(
                app.label,
                style = labelStyle,
                color = tint.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .combinedClickable(
                        role = Role.Button,
                        onClickLabel = "Open ${app.label}",
                        onLongClickLabel = "App options",
                        onClick = { onLaunch(app) },
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPress(app)
                        },
                    )
                    .padding(vertical = 8.dp),
            )
        }
    }
}

/** Minimal mode's command bar: an underlined field in the wallpaper's text colour. */
@Composable
private fun MinimalCommandBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onOpenChat: () -> Unit,
    onVoice: (() -> Unit)?,
    tint: WallpaperTint,
) {
    val submit = rememberUpdatedState(onSubmit)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onOpenChat) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = "Open AI assistant", tint = tint.content)
        }
        TextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search or ask…", style = LocalTextStyle.current.copy(shadow = tint.shadow)) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(shadow = tint.shadow),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (query.isNotBlank()) submit.value(query.trim()) }),
            colors = TextFieldDefaults.colors(
                focusedTextColor = tint.content,
                unfocusedTextColor = tint.content,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                cursorColor = tint.content,
                focusedIndicatorColor = tint.content,
                unfocusedIndicatorColor = tint.secondary.copy(alpha = 0.7f),
                focusedPlaceholderColor = tint.secondary,
                unfocusedPlaceholderColor = tint.secondary,
            ),
            modifier = Modifier.weight(1f),
        )
        when {
            query.isNotBlank() -> IconButton(onClick = { submit.value(query.trim()) }) {
                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Ask the assistant", tint = tint.content)
            }
            onVoice != null -> IconButton(onClick = onVoice) {
                Icon(Icons.Rounded.Mic, contentDescription = "Voice input", tint = tint.content)
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
