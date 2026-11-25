package com.ailauncher.presentation.planner

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.OutlinedFlag
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ailauncher.domain.model.PlannerBlock
import com.ailauncher.domain.model.TodoItem
import com.ailauncher.domain.model.TodoPriority
import com.ailauncher.presentation.components.LauncherTextField
import com.ailauncher.presentation.components.OnResume
import com.ailauncher.presentation.components.ThemedSystemBarIcons
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** To-dos & Daily Planner: the same lists and time blocks the assistant's manage_todo / manage_planner tools edit. */
@Composable
fun PlannerScreen(
    initialTab: PlannerTab,
    onBack: () -> Unit,
    onAskAssistant: (String) -> Unit,
    viewModel: PlannerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var editingTodo by remember { mutableStateOf<TodoEditTarget?>(null) }
    var editingBlock by remember { mutableStateOf<BlockEditTarget?>(null) }
    val snackbar = remember { SnackbarHostState() }
    ThemedSystemBarIcons()
    OnResume { viewModel.refresh() }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(initialTab) { tab = initialTab }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("To-dos & planner") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = {
            if (tab == PlannerTab.PLAN) {
                ExtendedFloatingActionButton(
                    onClick = { editingBlock = BlockEditTarget(null, defaultStart(state)) },
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("Add block") },
                    modifier = Modifier.navigationBarsPadding(),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                PlannerTab.entries.forEach { entry ->
                    Tab(selected = tab == entry, onClick = { tab = entry }, text = { Text(entry.label) })
                }
            }
            when (tab) {
                PlannerTab.PLAN -> DayPlan(
                    state = state,
                    viewModel = viewModel,
                    onAddAt = { start -> editingBlock = BlockEditTarget(null, start) },
                    onEditBlock = { block -> editingBlock = BlockEditTarget(block, block.startMinutes) },
                    onAskAssistant = onAskAssistant,
                )
                PlannerTab.TODOS -> TodoList(
                    state = state,
                    viewModel = viewModel,
                    onEdit = { item -> editingTodo = TodoEditTarget(item) },
                    onAskAssistant = onAskAssistant,
                )
            }
        }
    }

    editingTodo?.let { target ->
        TodoEditorSheet(
            existing = target.item,
            lists = state.lists,
            onSave = { title, notes, list, priority, due ->
                viewModel.saveTodo(target.item, title, notes, list, priority, due)
                editingTodo = null
            },
            onDelete = target.item?.let { item -> { viewModel.deleteTodo(item); editingTodo = null } },
            onDismiss = { editingTodo = null },
        )
    }
    editingBlock?.let { target ->
        BlockEditorSheet(
            existing = target.block,
            initialStart = target.startMinutes,
            use24Hour = state.use24Hour,
            todos = state.todos.filter { !it.completed },
            onSave = { title, start, duration, todoId ->
                viewModel.saveBlock(target.block, title, start, duration, todoId)
                editingBlock = null
            },
            onDelete = target.block?.let { block -> { viewModel.removeBlock(block); editingBlock = null } },
            onDismiss = { editingBlock = null },
        )
    }
}

private data class TodoEditTarget(val item: TodoItem?)

private data class BlockEditTarget(val block: PlannerBlock?, val startMinutes: Int)

/** Next half hour today (within the day), or 09:00 on other days. */
private fun defaultStart(state: PlannerUiState): Int {
    if (state.date != state.today) return 9 * 60
    val now = LocalTime.now()
    val next = ((now.hour * 60 + now.minute) / 30 + 1) * 30
    return next.coerceAtMost(23 * 60)
}

// ----------------------------------------------------------------------------- day plan

@Composable
private fun DayPlan(
    state: PlannerUiState,
    viewModel: PlannerViewModel,
    onAddAt: (Int) -> Unit,
    onEditBlock: (PlannerBlock) -> Unit,
    onAskAssistant: (String) -> Unit,
) {
    val calendarPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.refresh() }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "day") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::previousDay) { Icon(Icons.Rounded.ChevronLeft, "Previous day") }
                Column(
                    Modifier.weight(1f).clickable(onClickLabel = "Go to today") { viewModel.goToToday() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(dayWord(state.date, state.today), style = MaterialTheme.typography.titleMedium)
                    Text(
                        state.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = viewModel::nextDay) { Icon(Icons.Rounded.ChevronRight, "Next day") }
            }
        }
        item(key = "ask") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val day = dayWord(state.date, state.today).lowercase()
                AssistChip(
                    onClick = { onAskAssistant("Plan my day $day: fit my urgent to-dos into my free time.") },
                    label = { Text("Plan my day with AI") },
                    leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp)) },
                )
                AssistChip(
                    onClick = { onAskAssistant("When am I free $day?") },
                    label = { Text("When am I free?") },
                    leadingIcon = { Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp)) },
                )
            }
        }
        if (!state.calendarAccess) {
            item(key = "calendar") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Event, null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Show calendar events here so planning skips your meetings (read on-device only).",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) }) { Text("Allow") }
                    }
                }
            }
        }
        state.allDayEvents.forEach { event ->
            item(key = "allday-${event.title}-${event.startMillis}") {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Event, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text("All day · ${event.title}", color = MaterialTheme.colorScheme.onTertiaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (state.timeline.none { it !is TimelineEntry.Free }) {
            item(key = "empty") {
                Text(
                    "Nothing planned. Add a time block, or ask the assistant: “block 2 to 4pm tomorrow for deep work”.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        items(state.timeline, key = { entry ->
            when (entry) {
                is TimelineEntry.Block -> "block-${entry.block.id}"
                is TimelineEntry.Event -> "event-${entry.event.title}-${entry.event.startMillis}"
                is TimelineEntry.Free -> "free-${entry.startMinutes}"
            }
        }) { entry ->
            TimelineRow(entry, state.use24Hour, onToggle = viewModel::toggleBlock, onEdit = onEditBlock, onAddAt = onAddAt)
        }
    }
}

@Composable
private fun TimelineRow(
    entry: TimelineEntry,
    use24Hour: Boolean,
    onToggle: (PlannerBlock) -> Unit,
    onEdit: (PlannerBlock) -> Unit,
    onAddAt: (Int) -> Unit,
) {
    val time = "${clock(entry.startMinutes, use24Hour)} – ${clock(entry.endMinutes, use24Hour)}"
    when (entry) {
        is TimelineEntry.Block -> Card(
            onClick = { onEdit(entry.block) },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = entry.block.completed, onCheckedChange = { onToggle(entry.block) })
                Column(Modifier.weight(1f)) {
                    Text(
                        entry.block.title,
                        style = MaterialTheme.typography.titleSmall,
                        textDecoration = if (entry.block.completed) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(time, style = MaterialTheme.typography.bodySmall)
                    entry.linkedTodo?.let { Text("To-do: ${it.title}", style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        is TimelineEntry.Event -> Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Event, null, tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(entry.event.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(time, entry.event.location?.takeIf { it.isNotBlank() }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        is TimelineEntry.Free -> Surface(
            onClick = { onAddAt(entry.startMinutes) },
            shape = RoundedCornerShape(12.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Free · $time", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Icon(Icons.Rounded.Add, "Plan this time", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// -------------------------------------------------------------------------------- to-dos

@Composable
private fun TodoList(
    state: PlannerUiState,
    viewModel: PlannerViewModel,
    onEdit: (TodoItem?) -> Unit,
    onAskAssistant: (String) -> Unit,
) {
    var highPriority by rememberSaveable { mutableStateOf(false) }
    var showDone by rememberSaveable { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val today = state.today
    val open = state.openTodos
    val overdue = open.filter { it.dueAtMillis != null && it.dueAtMillis < today.atStartOfDay(zone).toInstant().toEpochMilli() }
    val dueToday = open.filter { it.dueAtMillis != null && it !in overdue && localDate(it.dueAtMillis, zone) == today }
    val upcoming = open.filter { it.dueAtMillis != null && it !in overdue && it !in dueToday }.sortedBy { it.dueAtMillis }
    val someday = open.filter { it.dueAtMillis == null }.sortedWith(compareByDescending<TodoItem> { it.priority }.thenBy { it.createdAt })
    val submit = { viewModel.addTodoFromField(if (highPriority) TodoPriority.HIGH else TodoPriority.MEDIUM) }

    Column(Modifier.fillMaxSize()) {
        LauncherTextField(
            state = viewModel.newTodo,
            placeholder = if (state.listFilter != null) "Add to ${state.listFilter}" else "Add a to-do",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            onKeyboardAction = { submit() },
            trailingContent = {
                IconButton(onClick = { highPriority = !highPriority }) {
                    Icon(
                        if (highPriority) Icons.Rounded.Flag else Icons.Rounded.OutlinedFlag,
                        if (highPriority) "High priority (on)" else "High priority (off)",
                        tint = if (highPriority) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = submit) { Icon(Icons.AutoMirrored.Rounded.Send, "Add to-do") }
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = state.listFilter == null, onClick = { viewModel.setListFilter(null) }, label = { Text("All") })
            state.lists.forEach { list ->
                FilterChip(selected = state.listFilter == list, onClick = { viewModel.setListFilter(list) }, label = { Text(list) })
            }
            AssistChip(
                onClick = { onAskAssistant("What should I focus on today from my to-dos?") },
                label = { Text("Prioritize with AI") },
                leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp)) },
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 96.dp),
            modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        ) {
            fun section(key: String, title: String, list: List<TodoItem>, accent: Color? = null) {
                if (list.isEmpty()) return
                item(key = "h-$key") { TodoHeader(title, list.size, accent) }
                items(list, key = { "t-${it.id}" }) { item ->
                    TodoRow(item, state.use24Hour, zone, today, onToggle = viewModel::toggleTodo, onEdit = onEdit, onDelete = viewModel::deleteTodo)
                }
            }
            section("overdue", "Overdue", overdue, accent = null)
            section("today", "Today", dueToday)
            section("upcoming", "Upcoming", upcoming)
            section("someday", "No date", someday)
            if (open.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "All clear. Add a to-do above, or tell the assistant “remind me to buy milk on my errands list”.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            val done = state.doneTodos
            if (done.isNotEmpty()) {
                item(key = "done-toggle") {
                    TextButton(onClick = { showDone = !showDone }, modifier = Modifier.padding(start = 4.dp)) {
                        Text(if (showDone) "Hide completed (${done.size})" else "Show completed (${done.size})")
                    }
                }
                if (showDone) {
                    items(done.take(50), key = { "d-${it.id}" }) { item ->
                        TodoRow(item, state.use24Hour, zone, today, onToggle = viewModel::toggleTodo, onEdit = onEdit, onDelete = viewModel::deleteTodo)
                    }
                }
            }
        }
    }
}

@Composable
private fun TodoHeader(title: String, count: Int, accent: Color?) {
    Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent ?: if (title == "Overdue") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text("$count", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TodoRow(
    item: TodoItem,
    use24Hour: Boolean,
    zone: ZoneId,
    today: LocalDate,
    onToggle: (TodoItem) -> Unit,
    onEdit: (TodoItem?) -> Unit,
    onDelete: (TodoItem) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable { onEdit(item) }.padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = item.completed, onCheckedChange = { onToggle(item) })
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.priority == TodoPriority.HIGH && !item.completed) {
                    Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.error, CircleShape))
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (item.completed) TextDecoration.LineThrough else null,
                    color = if (item.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val details = listOfNotNull(
                item.dueAtMillis?.let { dueLabel(it, zone, today, use24Hour) },
                item.listName.takeUnless { it.equals(TodoItem.DEFAULT_LIST, ignoreCase = true) },
                item.notes?.lineSequence()?.firstOrNull()?.take(60),
            )
            if (details.isNotEmpty()) {
                Text(
                    details.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = { onDelete(item) }) { Icon(Icons.Rounded.Delete, "Delete ${item.title}", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

// ------------------------------------------------------------------------------- editors

/** Create or edit a to-do. Also opened from the home screen's to-do widgets. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TodoEditorSheet(
    existing: TodoItem?,
    lists: List<String>,
    onSave: (title: String, notes: String?, list: String, priority: TodoPriority, dueAtMillis: Long?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
    /** List for a new to-do (e.g. the list a widget shows). */
    initialList: String? = null,
) {
    val zone = ZoneId.systemDefault()
    val title = rememberTextFieldState(existing?.title.orEmpty())
    val notes = rememberTextFieldState(existing?.notes.orEmpty())
    val list = rememberTextFieldState(existing?.listName ?: initialList ?: TodoItem.DEFAULT_LIST)
    var priority by remember { mutableStateOf(existing?.priority ?: TodoPriority.MEDIUM) }
    var due by remember { mutableStateOf(existing?.dueAtMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime() }) }
    var pickDate by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(
            Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (existing == null) "New to-do" else "Edit to-do", style = MaterialTheme.typography.titleLarge)
            LauncherTextField(
                state = title,
                placeholder = "What needs doing?",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            LauncherTextField(
                state = notes,
                placeholder = "Notes (optional)",
                lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 2, maxHeightInLines = 6),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            LauncherTextField(
                state = list,
                placeholder = "List (e.g. Errands)",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            if (lists.size > 1) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    lists.forEach { name ->
                        FilterChip(
                            selected = list.text.toString().equals(name, ignoreCase = true),
                            onClick = { list.edit { replace(0, length, name) } },
                            label = { Text(name) },
                        )
                    }
                }
            }
            Text("Priority", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TodoPriority.entries.forEachIndexed { index, p ->
                    SegmentedButton(
                        selected = priority == p,
                        onClick = { priority = p },
                        shape = SegmentedButtonDefaults.itemShape(index, TodoPriority.entries.size),
                    ) { Text(p.wireName.replaceFirstChar { it.uppercase() }) }
                }
            }
            Text("Due", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val today = LocalDate.now(zone)
                FilterChip(selected = due == null, onClick = { due = null }, label = { Text("No date") })
                FilterChip(
                    selected = due?.toLocalDate() == today,
                    onClick = { due = today.atTime(due?.toLocalTime() ?: LocalTime.of(18, 0)) },
                    label = { Text("Today") },
                )
                FilterChip(
                    selected = due?.toLocalDate() == today.plusDays(1),
                    onClick = { due = today.plusDays(1).atTime(due?.toLocalTime() ?: LocalTime.of(9, 0)) },
                    label = { Text("Tomorrow") },
                )
                FilterChip(selected = false, onClick = { pickDate = true }, label = { Text("Pick date…") })
            }
            due?.let { value ->
                OutlinedButton(onClick = { pickTime = true }) {
                    Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(value.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)))
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(
                    enabled = title.text.isNotBlank(),
                    onClick = {
                        onSave(title.text.toString(), notes.text.toString(), list.text.toString(), priority, due?.atZone(zone)?.toInstant()?.toEpochMilli())
                    },
                ) { Text("Save") }
            }
        }
    }
    if (pickDate) {
        val initial = (due?.toLocalDate() ?: LocalDate.now(zone)).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val dateState = rememberDatePickerState(initialSelectedDateMillis = initial)
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { millis ->
                        val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        due = day.atTime(due?.toLocalTime() ?: LocalTime.of(9, 0))
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = dateState) }
    }
    if (pickTime) {
        val current = due ?: LocalDateTime.now(zone)
        TimePickerDialog(
            initialMinutes = current.hour * 60 + current.minute,
            onDismiss = { pickTime = false },
            onPick = { minutes ->
                due = (due?.toLocalDate() ?: LocalDate.now(zone)).atTime(minutes / 60, minutes % 60)
                pickTime = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BlockEditorSheet(
    existing: PlannerBlock?,
    initialStart: Int,
    use24Hour: Boolean,
    todos: List<TodoItem>,
    onSave: (title: String, startMinutes: Int, durationMinutes: Int, linkedTodoId: Long?) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val title = rememberTextFieldState(existing?.title.orEmpty())
    var start by remember { mutableIntStateOf(existing?.startMinutes ?: initialStart) }
    var duration by remember { mutableIntStateOf(existing?.durationMinutes ?: 60) }
    var linked by remember { mutableStateOf(existing?.linkedTodoId) }
    var pickTime by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))) {
        Column(
            Modifier.navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (existing == null) "New time block" else "Edit time block", style = MaterialTheme.typography.titleLarge)
            LauncherTextField(
                state = title,
                placeholder = "What's this time for?",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { pickTime = true }) {
                Icon(Icons.Rounded.Schedule, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("${clock(start, use24Hour)} – ${clock((start + duration).coerceAtMost(24 * 60), use24Hour)}")
            }
            Text("Duration", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15, 30, 45, 60, 90, 120, 180).forEach { minutes ->
                    FilterChip(
                        selected = duration == minutes,
                        onClick = { duration = minutes },
                        enabled = start + minutes <= 24 * 60,
                        label = { Text(if (minutes < 60) "$minutes min" else if (minutes % 60 == 0) "${minutes / 60} h" else "${minutes / 60}.5 h") },
                    )
                }
            }
            if (todos.isNotEmpty()) {
                Text("For a to-do (optional)", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = linked == null, onClick = { linked = null }, label = { Text("None") })
                    todos.take(12).forEach { todo ->
                        FilterChip(
                            selected = linked == todo.id,
                            onClick = {
                                linked = todo.id
                                if (title.text.isBlank()) title.edit { replace(0, length, todo.title) }
                            },
                            label = { Text(todo.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(enabled = title.text.isNotBlank(), onClick = { onSave(title.text.toString(), start, duration, linked) }) { Text("Save") }
            }
            Spacer(Modifier.height(4.dp))
        }
    }
    if (pickTime) {
        TimePickerDialog(
            initialMinutes = start,
            onDismiss = { pickTime = false },
            onPick = { minutes ->
                start = minutes
                if (start + duration > 24 * 60) duration = 24 * 60 - start
                pickTime = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initialMinutes: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val timeState = rememberTimePickerState(
        initialHour = (initialMinutes / 60).coerceIn(0, 23),
        initialMinute = initialMinutes % 60,
        is24Hour = android.text.format.DateFormat.is24HourFormat(context),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(timeState.hour * 60 + timeState.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        text = { TimePicker(state = timeState) },
    )
}

// ------------------------------------------------------------------------------- format

private fun clock(minutes: Int, use24Hour: Boolean): String {
    val m = minutes.coerceIn(0, 24 * 60)
    val time = if (m == 24 * 60) LocalTime.MIDNIGHT else LocalTime.of(m / 60, m % 60)
    return time.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a"))
}

private fun dayWord(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern("EEEE"))
}

private fun localDate(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

internal fun dueLabel(millis: Long, zone: ZoneId, today: LocalDate, use24Hour: Boolean): String {
    val at = Instant.ofEpochMilli(millis).atZone(zone)
    val time = at.toLocalTime().format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a"))
    return when (val day = at.toLocalDate()) {
        today -> "Today $time"
        today.plusDays(1) -> "Tomorrow $time"
        else -> if (day.isBefore(today)) "Overdue · ${day.format(DateTimeFormatter.ofPattern("d MMM"))}" else "${day.format(DateTimeFormatter.ofPattern("EEE d MMM"))} $time"
    }
}
