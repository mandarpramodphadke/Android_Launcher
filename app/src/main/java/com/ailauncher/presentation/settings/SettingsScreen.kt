package com.ailauncher.presentation.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ailauncher.BuildConfig
import com.ailauncher.data.system.LockMethod
import com.ailauncher.domain.model.DrawerTab
import com.ailauncher.domain.model.EngineState
import com.ailauncher.domain.model.ModelConfig
import com.ailauncher.domain.model.ModelFileState
import com.ailauncher.domain.model.ThemeMode
import com.ailauncher.presentation.components.AppIcon
import com.ailauncher.presentation.components.AppListItem
import com.ailauncher.presentation.components.OnResume
import com.ailauncher.presentation.components.PassphraseDialog
import com.ailauncher.presentation.components.ThemedSystemBarIcons
import com.ailauncher.presentation.components.findActivity
import com.ailauncher.presentation.components.formatBytes
import java.time.LocalDate

private enum class BackupFlow { EXPORT, IMPORT, CLOUD_UP, CLOUD_DOWN }

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenModelSetup: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showHidden by remember { mutableStateOf(false) }
    var passphraseFor by remember { mutableStateOf<BackupFlow?>(null) }
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }

    ThemedSystemBarIcons()
    OnResume { viewModel.refreshSystemStatus() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshSystemStatus() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            pendingUri = uri
            passphraseFor = BackupFlow.EXPORT
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pendingUri = uri
            passphraseFor = BackupFlow.IMPORT
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }

            // ---------------------------------------------------------------- Account
            item {
                ProfileCard(
                    state = state,
                    onSignIn = { context.findActivity()?.let(viewModel::signIn) },
                    onSignOut = viewModel::signOut,
                )
            }

            // ---------------------------------------------------------------- Default launcher
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (state.system.isDefaultLauncher) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primaryContainer,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Home, null)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (state.system.isDefaultLauncher) "AI Launcher is your home app" else "Make AI Launcher your home app", style = MaterialTheme.typography.titleSmall)
                            Text("Used when you press the Home button.", style = MaterialTheme.typography.bodySmall)
                        }
                        if (!state.system.isDefaultLauncher) {
                            Button(onClick = { runCatching { roleLauncher.launch(viewModel.defaultLauncherIntent()) } }) { Text("Set default") }
                        } else {
                            TextButton(onClick = { runCatching { roleLauncher.launch(viewModel.homeSettingsIntent()) } }) { Text("Change") }
                        }
                    }
                }
            }

            // ---------------------------------------------------------------- Appearance
            item { SectionTitle("Appearance") }
            item {
                SegmentedSetting(
                    title = "Theme",
                    options = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"),
                    selected = state.settings.themeMode,
                    onSelect = { mode -> viewModel.updateSettings { it.copy(themeMode = mode) } },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    SwitchSetting("Wallpaper colours", "Material You colours from your wallpaper", state.settings.dynamicColor) { on ->
                        viewModel.updateSettings { it.copy(dynamicColor = on) }
                    }
                }
            }
            item {
                SegmentedSetting(
                    title = "Clock",
                    options = listOf<Pair<Boolean?, String>>(null to "System", false to "12-hour", true to "24-hour"),
                    selected = state.settings.use24HourClock,
                    onSelect = { value -> viewModel.updateSettings { it.copy(use24HourClock = value) } },
                )
            }
            item {
                SegmentedSetting(
                    title = "App grid columns",
                    options = (3..6).map { it to it.toString() },
                    selected = state.settings.gridColumns,
                    onSelect = { columns -> viewModel.updateSettings { it.copy(gridColumns = columns) } },
                )
            }

            // ---------------------------------------------------------------- Gestures
            item { SectionTitle("Home screen & gestures") }
            item {
                val lockStatus = when (state.system.lockMethod) {
                    LockMethod.ACCESSIBILITY -> "Using the accessibility service (keeps fingerprint unlock)"
                    LockMethod.DEVICE_ADMIN -> "Using device admin"
                    LockMethod.NONE -> "Tap to set up: needs the accessibility service or device admin"
                }
                SwitchSetting("Double-tap to lock", lockStatus, state.settings.doubleTapToLock) { on ->
                    viewModel.updateSettings { it.copy(doubleTapToLock = on) }
                }
            }
            if (state.system.lockMethod == LockMethod.NONE && state.settings.doubleTapToLock) {
                item {
                    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { runCatching { context.startActivity(viewModel.accessibilitySettingsIntent()) } }) { Text("Accessibility") }
                        OutlinedButton(onClick = { runCatching { roleLauncher.launch(viewModel.deviceAdminIntent()) } }) { Text("Device admin") }
                    }
                }
            }
            item {
                SwitchSetting("Swipe down for notifications", null, state.settings.swipeDownForNotifications) { on ->
                    viewModel.updateSettings { it.copy(swipeDownForNotifications = on) }
                }
            }
            item {
                SwitchSetting("AI command bar", "Ask the assistant or search apps from the home screen", state.settings.showAiCommandBar) { on ->
                    viewModel.updateSettings { it.copy(showAiCommandBar = on) }
                }
            }

            // ---------------------------------------------------------------- Drawer
            item { SectionTitle("App drawer") }
            item {
                SegmentedSetting(
                    title = "Open on",
                    options = listOf(DrawerTab.ALPHABETICAL to "A–Z", DrawerTab.CATEGORIES to "Smart categories"),
                    selected = state.settings.drawerTab,
                    onSelect = { tab -> viewModel.updateSettings { it.copy(drawerTab = tab) } },
                )
            }
            item { ActionSetting("Hidden apps", "${state.hiddenApps.size} hidden") { showHidden = true } }
            item { ActionSetting("Re-sort all apps with AI", "Keeps the categories you chose yourself", onClick = viewModel::resortAllApps) }
            item { ActionSetting("Reset automatic categories", null, onClick = viewModel::resetCategories) }

            // ---------------------------------------------------------------- AI model
            item { SectionTitle("On-device AI") }
            item {
                val status = when (val file = state.modelFile) {
                    is ModelFileState.Ready -> "Downloaded · ${context.formatBytes(file.sizeBytes)} · " + when (state.engine) {
                        is EngineState.Ready, is EngineState.Generating -> "loaded in memory"
                        else -> "loads on demand"
                    }
                    is ModelFileState.Downloading -> "Downloading ${(file.fraction * 100).toInt()}%"
                    is ModelFileState.Paused -> "Download paused"
                    else -> "Not downloaded"
                }
                ActionSetting("Qwen3 1.7B model", status, onClick = onOpenModelSetup)
            }
            item {
                ChoiceSetting(
                    title = "Context window",
                    options = ModelConfig.CONTEXT_OPTIONS.map { it to "$it tokens" + if (it == 4096) " (more RAM)" else "" },
                    selected = state.modelConfig.contextSize,
                    onSelect = { size -> viewModel.updateModelConfig { it.copy(contextSize = size) } },
                )
            }
            item {
                ChoiceSetting(
                    title = "CPU threads",
                    options = listOf(0 to "Auto (${state.performanceCores.coerceIn(2, 4)} performance cores)") + listOf(2, 4, 6, 8).map { it to "$it threads" },
                    selected = state.modelConfig.threads,
                    onSelect = { threads -> viewModel.updateModelConfig { it.copy(threads = threads) } },
                )
            }
            item {
                ChoiceSetting(
                    title = "Free RAM when idle",
                    options = ModelConfig.AUTO_UNLOAD_OPTIONS.map { it to if (it == 0) "Never" else "After $it min" },
                    selected = state.modelConfig.autoUnloadMinutes,
                    onSelect = { minutes -> viewModel.updateModelConfig { it.copy(autoUnloadMinutes = minutes) } },
                )
            }
            if (state.engine is EngineState.Ready) {
                item { ActionSetting("Free model memory now", null, onClick = viewModel::unloadModel) }
            }
            item {
                SwitchSetting("Reasoning mode by default", "Qwen3 thinks step by step before answering (slower)", state.modelConfig.reasoningByDefault) { on ->
                    viewModel.updateModelConfig { it.copy(reasoningByDefault = on) }
                }
            }
            item {
                SwitchSetting("Open apps without confirming", "The assistant launches apps immediately", state.settings.autoConfirmAppLaunch) { on ->
                    viewModel.updateSettings { it.copy(autoConfirmAppLaunch = on) }
                }
            }
            item {
                SwitchSetting("Save reminders to calendar", "Otherwise reminders are local notifications", state.settings.saveRemindersToCalendar) { on ->
                    viewModel.updateSettings { it.copy(saveRemindersToCalendar = on) }
                }
            }
            item { ActionSetting("Clear chat history", null, onClick = viewModel::clearChatHistory) }

            // ---------------------------------------------------------------- Backup
            item { SectionTitle("Backup & restore") }
            item {
                ActionSetting("Export encrypted backup", "Settings, dock, hidden apps and AI categories") {
                    exportLauncher.launch("ai-launcher-backup-${LocalDate.now()}.json")
                }
            }
            item { ActionSetting("Import backup", null) { importLauncher.launch(arrayOf("application/json", "*/*")) } }
            if (state.cloudBackupAvailable) {
                item { ActionSetting("Back up to your Google account", "End-to-end encrypted in Firebase") { passphraseFor = BackupFlow.CLOUD_UP } }
                item { ActionSetting("Restore from your Google account", null) { passphraseFor = BackupFlow.CLOUD_DOWN } }
            }

            // ---------------------------------------------------------------- About
            item { SectionTitle("About") }
            item {
                AppListItem(
                    headlineContent = { Text("AI Launcher ${BuildConfig.VERSION_NAME}") },
                    supportingContent = { Text("On-device inference by llama.cpp · Qwen3 (Apache-2.0). Inference never uses the network.") },
                )
            }
        }
    }

    if (showHidden) {
        AlertDialog(
            onDismissRequest = { showHidden = false },
            title = { Text("Hidden apps") },
            text = {
                if (state.hiddenApps.isEmpty()) {
                    Text("No hidden apps. Long-press an app and choose Hide app.")
                } else {
                    LazyColumn {
                        items(state.hiddenApps, key = { it.componentKey }) { app ->
                            AppListItem(
                                headlineContent = { Text(app.label) },
                                leadingContent = { AppIcon(app, 36.dp) },
                                trailingContent = { TextButton(onClick = { viewModel.unhide(app) }) { Text("Unhide") } },
                            )
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showHidden = false }) { Text("Done") } },
        )
    }

    passphraseFor?.let { flow ->
        PassphraseDialog(
            title = when (flow) {
                BackupFlow.EXPORT -> "Encrypt backup"
                BackupFlow.IMPORT -> "Decrypt backup"
                BackupFlow.CLOUD_UP -> "Encrypt cloud backup"
                BackupFlow.CLOUD_DOWN -> "Decrypt cloud backup"
            },
            confirm = flow == BackupFlow.EXPORT || flow == BackupFlow.CLOUD_UP,
            onDismiss = {
                passphraseFor = null
                pendingUri = null
            },
            onSubmit = { passphrase ->
                val uri = pendingUri
                when (flow) {
                    BackupFlow.EXPORT -> uri?.let { viewModel.exportBackup(it, passphrase) }
                    BackupFlow.IMPORT -> uri?.let { viewModel.importBackup(it, passphrase) }
                    BackupFlow.CLOUD_UP -> viewModel.uploadToCloud(passphrase)
                    BackupFlow.CLOUD_DOWN -> viewModel.restoreFromCloud(passphrase)
                }
                passphraseFor = null
                pendingUri = null
            },
        )
    }
}

@Composable
private fun ProfileCard(state: SettingsUiState, onSignIn: () -> Unit, onSignOut: () -> Unit) {
    val profile = state.profile
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).clip(CircleShape), contentAlignment = Alignment.Center) {
                if (profile?.photoUrl != null) {
                    AsyncImage(model = profile.photoUrl, contentDescription = "Profile photo", modifier = Modifier.size(52.dp))
                } else {
                    Icon(Icons.Rounded.AccountCircle, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (profile == null || profile.isGuest) "Guest" else profile.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    when {
                        profile == null || profile.isGuest -> "Offline mode · everything stays on this phone"
                        profile.firebaseLinked -> (profile.email ?: "") + " · cloud backup on"
                        else -> profile.email ?: "Signed in"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (profile == null || profile.isGuest) {
                if (state.googleSignInConfigured) Button(onClick = onSignIn) { Text("Sign in") }
            } else {
                TextButton(onClick = onSignOut) { Text("Sign out") }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 12.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

@Composable
private fun SwitchSetting(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    AppListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        onClick = { onChange(!checked) },
    )
}

@Composable
private fun ActionSetting(title: String, subtitle: String?, onClick: () -> Unit) {
    AppListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        onClick = onClick,
    )
}

@Composable
private fun <T> SegmentedSetting(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(label, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun <T> ChoiceSetting(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        AppListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(options.firstOrNull { it.first == selected }?.second ?: "") },
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    expanded = false
                    onSelect(value)
                })
            }
        }
    }
}
