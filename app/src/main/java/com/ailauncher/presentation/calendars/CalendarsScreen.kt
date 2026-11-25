package com.ailauncher.presentation.calendars

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ailauncher.domain.model.CalendarFeed
import com.ailauncher.domain.model.DeviceCalendar
import com.ailauncher.presentation.components.LauncherTextField
import com.ailauncher.presentation.components.ThemedSystemBarIcons

/**
 * Which calendars the assistant, planner and Today widget read: every calendar on the phone
 * (Google, Outlook, Exchange, Samsung, …), calendars subscribed to by link, and imported files.
 */
@Composable
fun CalendarsScreen(onBack: () -> Unit, viewModel: CalendarsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    ThemedSystemBarIcons()
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    // Calendars added in Outlook's settings show up when coming back here.
    LifecycleResumeEffect(viewModel) {
        viewModel.reload()
        onPauseOrDispose { }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { viewModel.reload() }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.importFile(it.toString()) } }
    var addingLink by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<CalendarFeed?>(null) }
    var pickingReminderCalendar by remember { mutableStateOf(false) }

    CalendarsContent(
        state = state,
        onBack = onBack,
        onGrant = { permission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) },
        onShown = viewModel::setShown,
        onPickReminderCalendar = { pickingReminderCalendar = true },
        onAddLink = { addingLink = true },
        onImport = { importFile.launch(arrayOf("text/calendar", "text/x-vcalendar", "application/octet-stream", "*/*")) },
        onRefresh = viewModel::refresh,
        onRemove = { removing = it },
        snackbar = snackbar,
    )

    if (addingLink) {
        AddCalendarLinkDialog(
            onAdd = { name, url ->
                addingLink = false
                viewModel.subscribe(name, url)
            },
            onDismiss = { addingLink = false },
        )
    }
    removing?.let { feed ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove ${feed.name}?") },
            text = { Text("Its events disappear from AI Launcher. The calendar itself isn't changed.") },
            confirmButton = {
                TextButton(onClick = {
                    removing = null
                    viewModel.remove(feed)
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
    if (pickingReminderCalendar) {
        ReminderCalendarDialog(
            calendars = state.deviceCalendars.filter { it.writable },
            selected = state.reminderCalendarId,
            onPick = { id ->
                pickingReminderCalendar = false
                viewModel.setReminderCalendar(id)
            },
            onDismiss = { pickingReminderCalendar = false },
        )
    }
}

/** The screen's content, stateless (the screenshot test renders it). */
@Composable
internal fun CalendarsContent(
    state: CalendarsUiState,
    onBack: () -> Unit,
    onGrant: () -> Unit,
    onShown: (DeviceCalendar, Boolean) -> Unit,
    onPickReminderCalendar: () -> Unit,
    onAddLink: () -> Unit,
    onImport: () -> Unit,
    onRefresh: (CalendarFeed) -> Unit,
    onRemove: (CalendarFeed) -> Unit,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                title = { Text("Calendars") },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text(
                    "The assistant, the planner and the Today widget read these calendars. Everything stays on this phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            item { Header("On this phone") }
            if (!state.canRead) {
                item {
                    InfoCard(
                        "AI Launcher needs calendar access to read your calendars. Google, Outlook, Samsung and Exchange calendars all appear here once it's allowed.",
                        action = "Allow access",
                        onAction = onGrant,
                    )
                }
            } else {
                if (state.deviceCalendars.isEmpty()) {
                    item { Text("No calendars on this phone yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp)) }
                }
                state.deviceCalendars.groupBy { it.account to it.accountType }.forEach { (account, calendars) ->
                    item(key = "account-${account.first}-${account.second}") {
                        Text(
                            "${account.first.ifEmpty { "Account" }} · ${accountTypeName(account.second)}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                        )
                    }
                    items(calendars, key = { "cal-${it.id}" }) { calendar ->
                        val shown = calendar.id !in state.hiddenIds
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(role = Role.Checkbox) { onShown(calendar, !shown) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = shown, onCheckedChange = null, modifier = Modifier.padding(horizontal = 8.dp))
                            ColorDot(calendar.colorArgb)
                            Spacer(Modifier.width(10.dp))
                            Text(calendar.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        }
                    }
                }
                item {
                    val target = state.deviceCalendars.firstOrNull { it.id == state.reminderCalendarId }
                    Surface(
                        onClick = onPickReminderCalendar,
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("New reminders go to", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                target?.let { "${it.name} (${it.account})" } ?: "Automatic: your main synced calendar",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item {
                InfoCard(
                    "Using Outlook? Open the Outlook app ▸ Settings ▸ your account ▸ turn on \"Sync calendars\". " +
                        "Its calendars then appear above. Samsung, Exchange and other calendar apps that sync to your phone work the same way.",
                )
            }

            item { Header("Subscribed and imported") }
            if (state.feeds.isEmpty()) {
                item {
                    Text(
                        "Add any calendar that has a link (.ics or webcal): Outlook.com, iCloud, Proton, Fastmail, school timetables, sports fixtures, public holidays…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            items(state.feeds, key = { "feed-${it.id}" }) { feed -> FeedRow(feed, working = feed.id in state.working, onRefresh = { onRefresh(feed) }, onRemove = { onRemove(feed) }) }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = onAddLink, enabled = CalendarsViewModel.NEW !in state.working) {
                        Icon(Icons.Rounded.Link, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Add link")
                    }
                    OutlinedButton(onClick = onImport, enabled = CalendarsViewModel.NEW !in state.working) {
                        Icon(Icons.Rounded.FileOpen, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Import .ics")
                    }
                    if (CalendarsViewModel.NEW in state.working) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, start = 4.dp))
}

@Composable
private fun ColorDot(argb: Int) {
    Box(Modifier.size(14.dp).background(Color(argb), CircleShape))
}

@Composable
private fun InfoCard(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(Icons.Rounded.Info, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(text, style = MaterialTheme.typography.bodyMedium)
                if (action != null) {
                    Button(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) { Text(action) }
                }
            }
        }
    }
}

@Composable
private fun FeedRow(feed: CalendarFeed, working: Boolean, onRefresh: () -> Unit, onRemove: () -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorDot(feed.colorArgb)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(feed.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val status = buildString {
                    append(if (feed.isFile) "Imported file" else "Link")
                    append(" · ").append(if (feed.eventCount == 1) "1 event" else "${feed.eventCount} events")
                    feed.lastSyncedAt?.takeIf { !feed.isFile }?.let { append(" · updated ").append(ago(it)) }
                }
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                feed.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
            if (working) {
                CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
            } else if (!feed.isFile) {
                IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, "Update ${feed.name} now") }
            }
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Delete, "Remove ${feed.name}") }
        }
    }
}

@Composable
private fun AddCalendarLinkDialog(onAdd: (name: String, url: String) -> Unit, onDismiss: () -> Unit) {
    val name = rememberTextFieldState()
    val url = rememberTextFieldState()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Add, null) },
        title = { Text("Add a calendar link") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LauncherTextField(
                    state = url,
                    placeholder = "https://… or webcal://…",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                LauncherTextField(
                    state = name,
                    placeholder = "Name (optional)",
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Where to find the link:\n" +
                        "• Outlook.com: Settings ▸ Calendar ▸ Shared calendars ▸ Publish a calendar ▸ ICS link\n" +
                        "• Google: calendar settings ▸ Secret address in iCal format\n" +
                        "• iCloud: share the calendar ▸ Public calendar\n\n" +
                        "AI Launcher downloads the calendar from that address and refreshes it every few hours. Nothing else is sent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name.text.toString(), url.text.toString()) }, enabled = url.text.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ReminderCalendarDialog(calendars: List<DeviceCalendar>, selected: Long?, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New reminders go to") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ChoiceRow("Automatic", "Your main synced calendar", selected == null) { onPick(null) }
                calendars.forEach { calendar ->
                    ChoiceRow(calendar.name, calendar.account, selected == calendar.id) { onPick(calendar.id) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ChoiceRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A friendly name for a calendar account type. */
internal fun accountTypeName(type: String): String = when {
    type == "LOCAL" -> "Phone"
    type == "com.google" -> "Google"
    type.contains("outlook", ignoreCase = true) || type.contains("microsoft", ignoreCase = true) -> "Outlook"
    type.contains("exchange", ignoreCase = true) || type.contains("eas", ignoreCase = true) -> "Exchange"
    type.contains("samsung", ignoreCase = true) || type == "com.osp.app.signin" -> "Samsung"
    type.contains("davdroid", ignoreCase = true) || type.contains("davx5", ignoreCase = true) || type.contains("caldav", ignoreCase = true) -> "CalDAV"
    type.contains("icloud", ignoreCase = true) -> "iCloud"
    type.isBlank() -> "Calendar"
    else -> type.substringAfterLast('.').replaceFirstChar { it.uppercase() }
}

private fun ago(millis: Long): String {
    val minutes = ((System.currentTimeMillis() - millis) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 48 * 60 -> "${minutes / 60} h ago"
        else -> "${minutes / (24 * 60)} days ago"
    }
}
