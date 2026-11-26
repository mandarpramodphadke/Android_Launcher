package com.ailauncher.presentation.settings

import android.content.Intent
import androidx.core.net.toUri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
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
import com.ailauncher.domain.model.FileAccessLevel
import com.ailauncher.domain.model.SearchFolder
import com.ailauncher.presentation.components.NotificationDigestDisclosure
import com.ailauncher.presentation.components.SpecialAccess
import com.ailauncher.presentation.drawer.PrivateSpaceAuth
import com.ailauncher.presentation.drawer.authenticatePrivateSpace
import com.ailauncher.domain.model.EngineState
import com.ailauncher.domain.model.HomeTextColor
import com.ailauncher.domain.model.IconStyle
import com.ailauncher.domain.model.ModelConfig
import com.ailauncher.domain.model.ModelFileState
import com.ailauncher.domain.model.ThemeMode
import com.ailauncher.presentation.components.AccessibilityDisclosure
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
    onOpenPrivacyPolicy: () -> Unit = {},
    onOpenLicenses: () -> Unit = {},
    /** Calendars the assistant reads (full version until it reaches Google Play). */
    onOpenCalendars: (() -> Unit)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showHidden by remember { mutableStateOf(false) }
    var hiddenUnprotected by remember { mutableStateOf(false) }
    var showDigestDisclosure by remember { mutableStateOf(false) }
    var showSearchFolders by remember { mutableStateOf(false) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }
    var passphraseFor by remember { mutableStateOf<BackupFlow?>(null) }
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }

    ThemedSystemBarIcons()
    val iconPacks by viewModel.iconPacks.collectAsStateWithLifecycle()
    val memories by viewModel.memories.collectAsStateWithLifecycle()
    var showMemories by remember { mutableStateOf(false) }
    OnResume {
        viewModel.refreshSystemStatus()
        viewModel.refreshIconPacks()
    }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { viewModel.refreshSystemStatus() }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.refreshSystemStatus() }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.addSearchFolder(it.toString()) }
    }
    val openPrivateSpace = {
        authenticatePrivateSpace(context.findActivity() ?: context) { result ->
            when (result) {
                PrivateSpaceAuth.Granted -> { hiddenUnprotected = false; showHidden = true }
                PrivateSpaceAuth.NoScreenLock -> { hiddenUnprotected = true; showHidden = true }
                is PrivateSpaceAuth.Failed -> viewModel.showMessage(result.message)
            }
        }
    }
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
                ThemePicker(selected = state.settings.appTheme, mode = state.settings.themeMode) { theme ->
                    viewModel.updateSettings { it.copy(appTheme = theme) }
                }
            }
            item {
                val darkOnly = !state.settings.appTheme.supportsLight
                SegmentedSetting(
                    title = "Light or dark",
                    subtitle = if (darkOnly) "${themeLabel(state.settings.appTheme)} is always dark" else null,
                    options = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"),
                    selected = state.settings.themeMode,
                    enabled = !darkOnly,
                    onSelect = { mode -> viewModel.updateSettings { it.copy(themeMode = mode) } },
                )
            }
            item {
                SwitchSetting("Minimal mode", "Text-only home screen and app drawer, no icons", state.settings.minimalMode) { on ->
                    viewModel.updateSettings { it.copy(minimalMode = on) }
                }
            }
            if (!state.settings.minimalMode) {
                item {
                    SegmentedSetting(
                        title = "App icons",
                        subtitle = if (state.settings.iconStyle == IconStyle.THEMED && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            "Themed icons need Android 13, so icons are shown in greyscale"
                        } else {
                            "Themed icons take your theme's colours (apps without one are shown in greyscale)"
                        },
                        options = listOf(IconStyle.ORIGINAL to "Original", IconStyle.THEMED to "Themed", IconStyle.GRAYSCALE to "Greyscale"),
                        selected = state.settings.iconStyle,
                        onSelect = { style -> viewModel.updateSettings { it.copy(iconStyle = style) } },
                    )
                }
            }
            item {
                SegmentedSetting(
                    title = "Home screen text",
                    subtitle = "Auto picks light or dark text to suit your wallpaper",
                    options = listOf(HomeTextColor.AUTO to "Auto", HomeTextColor.LIGHT to "Light", HomeTextColor.DARK to "Dark"),
                    selected = state.settings.homeTextColor,
                    onSelect = { color -> viewModel.updateSettings { it.copy(homeTextColor = color) } },
                )
            }
            item {
                SwitchSetting("Text shade", "Soft gradient behind home screen text, for busy wallpapers", state.settings.homeTextShade) { on ->
                    viewModel.updateSettings { it.copy(homeTextShade = on) }
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
            if (!state.settings.minimalMode) {
                item {
                    SegmentedSetting(
                        title = "Home screen columns",
                        options = (4..5).map { it to it.toString() },
                        selected = state.settings.homeGridColumns,
                        onSelect = { columns -> viewModel.updateSettings { it.copy(homeGridColumns = columns) } },
                    )
                }
                item {
                    SegmentedSetting(
                        title = "App drawer columns",
                        options = (4..5).map { it to it.toString() },
                        selected = state.settings.gridColumns,
                        onSelect = { columns -> viewModel.updateSettings { it.copy(gridColumns = columns) } },
                    )
                }
            }
            looksSettings(
                settings = state.settings,
                iconPacks = iconPacks,
                onUpdate = viewModel::updateSettings,
                onFindIconPacks = {
                    val search = Intent(Intent.ACTION_VIEW, "market://search?q=icon%20pack&c=apps".toUri())
                    if (runCatching { context.startActivity(search) }.isFailure) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/search?q=icon%20pack&c=apps".toUri())) }
                    }
                },
            )

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
                        OutlinedButton(onClick = { showAccessibilityDisclosure = true }) { Text("Accessibility") }
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
            homeExtrasSettings(
                settings = state.settings,
                notificationAccess = state.system.notificationListener,
                contactsAccess = state.system.contactsAccess,
                onUpdate = viewModel::updateSettings,
                onAllowNotificationAccess = {
                    SpecialAccess.open(context, SpecialAccess.notificationListenerIntent(context), Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                onAllowContacts = { permissionLauncher.launch(arrayOf(android.Manifest.permission.READ_CONTACTS)) },
            )

            // ---------------------------------------------------------------- Drawer
            item { SectionTitle("App drawer") }
            item {
                SegmentedSetting(
                    title = "Open on",
                    options = listOf(DrawerTab.ALPHABETICAL to "A–Z", DrawerTab.CATEGORIES to "Smart", DrawerTab.FOLDERS to "Folders"),
                    selected = state.settings.drawerTab,
                    onSelect = { tab -> viewModel.updateSettings { it.copy(drawerTab = tab) } },
                )
            }
            item {
                SwitchSetting("Suggested apps", "Five predictions from your habits, the time of day and connected audio", state.settings.showSuggestedApps) { on ->
                    viewModel.updateSettings { it.copy(showSuggestedApps = on) }
                }
            }
            item { ActionSetting("Private Space", "${state.hiddenApps.size} hidden app${if (state.hiddenApps.size == 1) "" else "s"} · unlock with fingerprint, face or PIN", onClick = openPrivateSpace) }
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
                ActionSetting(state.activeModel?.displayName ?: "AI model", "$status · tap to switch models", onClick = onOpenModelSetup)
            }
            item {
                val gpu = state.system.gpuBackendAvailable
                SegmentedSetting(
                    title = "Processor",
                    subtitle = when {
                        !gpu -> "GPU offload isn't available in this build (arm64 phones only)"
                        state.system.gpuNotice != null -> state.system.gpuNotice
                        state.modelConfig.gpuOffload -> (state.engine as? EngineState.Ready)?.info?.accelerator?.let { "Now running on: $it" }
                            ?: "Uses the phone's GPU through Vulkan; falls back to the CPU automatically"
                        else -> "The CPU works on every phone. GPU offload can be faster on recent Adreno / Mali / Xclipse GPUs"
                    },
                    options = listOf(false to "CPU Only (Safe Default)", true to "GPU Offload (Vulkan)"),
                    selected = state.modelConfig.gpuOffload && gpu,
                    enabled = gpu,
                    onSelect = viewModel::setGpuOffload,
                )
            }
            item {
                ChoiceSetting(
                    title = "Context window",
                    options = ModelConfig.CONTEXT_OPTIONS.map {
                        it to when (it) {
                            ModelConfig.AUTO_CONTEXT -> "Auto (4096 on phones with 6 GB+ RAM)"
                            4096 -> "4096 tokens (all AI tools, more RAM)"
                            else -> "$it tokens (core AI tools)"
                        }
                    },
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
            assistantExtrasSettings(state.settings, memories, onUpdate = viewModel::updateSettings, onShowMemories = { showMemories = true })
            item {
                SwitchSetting("Save reminders to calendar", "Otherwise reminders are local notifications", state.settings.saveRemindersToCalendar) { on ->
                    viewModel.updateSettings { it.copy(saveRemindersToCalendar = on) }
                }
            }
            if (onOpenCalendars != null) {
                item {
                    ActionSetting(
                        "Calendars",
                        "Outlook, Samsung and other calendars on your phone, calendar links and .ics files",
                        onClick = onOpenCalendars,
                    )
                }
            }
            item { ActionSetting("Clear chat history", null, onClick = viewModel::clearChatHistory) }

            // ---------------------------------------------------------------- Notifications
            item { SectionTitle("Notifications") }
            item {
                SwitchSetting(
                    "Notification digest",
                    when {
                        !state.settings.notificationDigestEnabled -> "Off. Badges, smart pills and \"what did I miss?\" need it"
                        !state.system.notificationListener -> "Waiting for notification access · tap to allow"
                        else -> "On-device only · deleted after 24 hours"
                    },
                    state.settings.notificationDigestEnabled,
                ) { on -> if (on) showDigestDisclosure = true else viewModel.setNotificationDigest(false) }
            }
            if (state.settings.notificationDigestEnabled) {
                if (!state.system.notificationListener) {
                    item {
                        ActionSetting("Allow notification access", "Required by Android for the digest") {
                            SpecialAccess.open(context, SpecialAccess.notificationListenerIntent(context), Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }
                    }
                }
                item {
                    SwitchSetting("Unread badges", "Counts on app icons", state.settings.notificationBadges) { on ->
                        viewModel.updateSettings { it.copy(notificationBadges = on) }
                    }
                }
                item { ActionSetting("Clear digest now", null, onClick = viewModel::clearNotificationDigest) }
            }

            // ---------------------------------------------------------------- AI tools
            item { SectionTitle("AI tools & permissions") }
            item {
                ChoiceSetting(
                    title = "\"Unused apps\" means not opened for",
                    options = listOf(14, 30, 60, 90, 180).map { it to "$it days" },
                    selected = state.settings.unusedAppThresholdDays,
                    onSelect = { days -> viewModel.updateSettings { it.copy(unusedAppThresholdDays = days) } },
                )
            }
            item {
                ChoiceSetting(
                    title = "\"Old files\" means untouched for",
                    options = listOf(30, 90, 180, 365).map { it to if (it == 365) "1 year" else "$it days" },
                    selected = state.settings.staleFileThresholdDays,
                    onSelect = { days -> viewModel.updateSettings { it.copy(staleFileThresholdDays = days) } },
                )
            }
            item {
                PermissionSetting("Usage access", "Last-used times for unused apps and suggestions", state.system.usageAccess) {
                    SpecialAccess.open(context, SpecialAccess.usageAccessIntent(context), Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
            if (!BuildConfig.BROAD_FILE_ACCESS) {
                // Google Play build: no storage permission; only folders the user picks are searched.
                item {
                    ActionSetting(
                        "Folders to search",
                        state.searchFolders.joinToString { it.name }.ifBlank { "None yet. Choose folders such as DCIM, Movies or Documents" },
                    ) { showSearchFolders = true }
                }
            } else {
                item {
                    PermissionSetting("Photos, videos & audio", "Find your largest and oldest media files", state.system.fileAccess != FileAccessLevel.NONE) {
                        permissionLauncher.launch(SpecialAccess.mediaPermissions())
                    }
                }
            }
            if (BuildConfig.BROAD_FILE_ACCESS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                item {
                    PermissionSetting("All files access (optional)", "Include documents and archives (PDF, ZIP) in file searches", state.system.fileAccess == FileAccessLevel.ALL_FILES) {
                        SpecialAccess.open(context, SpecialAccess.allFilesAccessIntent(context))
                    }
                }
            }
            item {
                PermissionSetting("Contacts", "Call or message people by name (\"text Sam I'm late\")", state.system.contactsAccess) {
                    permissionLauncher.launch(arrayOf(android.Manifest.permission.READ_CONTACTS))
                }
            }

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
            item { ActionSetting("Privacy policy", "What stays on your phone and what doesn't", onClick = onOpenPrivacyPolicy) }
            item { ActionSetting("Open-source licences", null, onClick = onOpenLicenses) }
        }
    }

    if (showAccessibilityDisclosure) {
        AccessibilityDisclosure(
            onAgree = {
                showAccessibilityDisclosure = false
                runCatching { context.startActivity(viewModel.accessibilitySettingsIntent()) }
            },
            onDismiss = { showAccessibilityDisclosure = false },
        )
    }

    if (showMemories) {
        MemoriesSheet(memories, onDelete = viewModel::deleteMemory, onClear = viewModel::clearMemories, onDismiss = { showMemories = false })
    }
    if (showSearchFolders) {
        SearchFoldersDialog(
            folders = state.searchFolders,
            onAdd = { runCatching { folderPicker.launch(null) }.onFailure { viewModel.showMessage("No folder picker is available") } },
            onRemove = viewModel::removeSearchFolder,
            onDismiss = { showSearchFolders = false },
        )
    }

    if (showDigestDisclosure) {
        NotificationDigestDisclosure(
            onAccept = {
                showDigestDisclosure = false
                viewModel.setNotificationDigest(true)
                if (!state.system.notificationListener) {
                    SpecialAccess.open(context, SpecialAccess.notificationListenerIntent(context), Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            },
            onDismiss = { showDigestDisclosure = false },
        )
    }

    if (showHidden) {
        AlertDialog(
            onDismissRequest = { showHidden = false },
            title = { Text("Private Space") },
            text = {
                if (state.hiddenApps.isEmpty()) {
                    Text("No hidden apps. Long-press an app and choose Move to Private Space." + if (hiddenUnprotected) "\n\nSet a screen lock to protect Private Space." else "")
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
internal fun SectionTitle(text: String) {
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
internal fun SwitchSetting(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    AppListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        onClick = { onChange(!checked) },
    )
}

@Composable
private fun PermissionSetting(title: String, subtitle: String, granted: Boolean, onRequest: () -> Unit) {
    AppListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            if (granted) {
                Icon(Icons.Rounded.CheckCircle, "Allowed", tint = MaterialTheme.colorScheme.primary)
            } else {
                TextButton(onClick = onRequest) { Text("Allow") }
            }
        },
        onClick = if (granted) null else onRequest,
    )
}

@Composable
internal fun ActionSetting(title: String, subtitle: String?, onClick: () -> Unit) {
    AppListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        onClick = onClick,
    )
}

@Composable
internal fun <T> SegmentedSetting(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f),
        )
        if (subtitle != null) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    enabled = enabled,
                ) { Text(label, maxLines = 1) }
            }
        }
    }
}

@Composable
internal fun <T> ChoiceSetting(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
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

/** Google Play build: the folders the AI file tools may search (system folder picker grants). */
@Composable
private fun SearchFoldersDialog(
    folders: List<SearchFolder>,
    onAdd: () -> Unit,
    onRemove: (SearchFolder) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Folders to search") },
        text = {
            Column {
                Text(
                    "The assistant's file tools (largest files, old files, recent changes) only look inside these folders, " +
                        "on this phone. Android doesn't let apps choose the Download folder.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (folders.isEmpty()) {
                    Text("No folders yet.", style = MaterialTheme.typography.bodyMedium)
                }
                folders.forEach { folder ->
                    AppListItem(
                        headlineContent = { Text(folder.name, maxLines = 1) },
                        trailingContent = {
                            IconButton(onClick = { onRemove(folder) }) { Icon(Icons.Rounded.Close, "Stop searching ${folder.name}") }
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onAdd) { Text("Add folder") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
