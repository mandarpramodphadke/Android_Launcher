package com.ailauncher.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddTask
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ailauncher.domain.model.LauncherWidgetConfig
import com.ailauncher.domain.model.LauncherWidgetKind
import com.ailauncher.domain.model.PlannerBlock
import com.ailauncher.domain.model.TodoItem
import com.ailauncher.domain.model.TodoPriority
import com.ailauncher.domain.usecase.DueState
import com.ailauncher.domain.usecase.TodoWidgets
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** What the launcher's own widgets draw: the to-dos and today's planner blocks. */
@Immutable
data class LauncherWidgetData(
    val todos: List<TodoItem> = emptyList(),
    val todayBlocks: List<PlannerBlock> = emptyList(),
)

/** What the launcher's widgets can do (wired by [LauncherHomeScreen]). */
@Stable
class LauncherWidgetCallbacks(
    val onToggleTodo: (TodoItem) -> Unit = {},
    /** Opens the to-do editor for a new to-do in [listName] (null: the default list). */
    val onAddTodo: (listName: String?) -> Unit = {},
    val onEditTodo: (TodoItem) -> Unit = {},
    val onOpenTodos: () -> Unit = {},
    val onOpenPlanner: () -> Unit = {},
)

val LauncherWidgetKind.icon: ImageVector
    get() = when (this) {
        LauncherWidgetKind.TODO_LIST -> Icons.Rounded.Checklist
        LauncherWidgetKind.TODAY -> Icons.Rounded.Today
        LauncherWidgetKind.QUICK_ADD -> Icons.Rounded.AddTask
    }

private val RowHeight = 36.dp
private val HeaderHeight = 36.dp
private val QuickAddHeight = 56.dp

/**
 * A widget the launcher draws itself, in the theme's colours. A long press anywhere on it (also on
 * a row or a button) starts editing: resize, move, list, remove.
 */
@Composable
fun LauncherWidgetView(
    config: LauncherWidgetConfig,
    data: LauncherWidgetData,
    now: ZonedDateTime,
    use24Hour: Boolean,
    callbacks: LauncherWidgetCallbacks,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pill = config.kind == LauncherWidgetKind.QUICK_ADD
    val shape = RoundedCornerShape(if (pill) 50 else 24)
    val primary: () -> Unit = when (config.kind) {
        LauncherWidgetKind.TODO_LIST -> callbacks.onOpenTodos
        LauncherWidgetKind.TODAY -> callbacks.onOpenPlanner
        LauncherWidgetKind.QUICK_ADD -> ({ callbacks.onAddTodo(config.listName) })
    }
    Box(modifier.fillMaxSize().padding(4.dp), contentAlignment = Alignment.Center) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .then(if (pill) Modifier.height(QuickAddHeight) else Modifier.fillMaxHeight())
                .clip(shape)
                .combinedClickable(
                    onClickLabel = when (config.kind) {
                        LauncherWidgetKind.TODO_LIST -> "Open to-dos"
                        LauncherWidgetKind.TODAY -> "Open planner"
                        LauncherWidgetKind.QUICK_ADD -> "Add a to-do"
                    },
                    onLongClickLabel = "Edit widget",
                    onClick = primary,
                    onLongClick = onLongPress,
                ),
        ) {
            when (config.kind) {
                LauncherWidgetKind.TODO_LIST -> TodoListWidget(config.listName, data, now, use24Hour, callbacks, onLongPress)
                LauncherWidgetKind.TODAY -> TodayWidget(data, now, use24Hour, callbacks, onLongPress)
                LauncherWidgetKind.QUICK_ADD -> QuickAddWidget(config.listName, data)
            }
        }
    }
}

@Composable
private fun TodoListWidget(
    listName: String?,
    data: LauncherWidgetData,
    now: ZonedDateTime,
    use24Hour: Boolean,
    callbacks: LauncherWidgetCallbacks,
    onLongPress: () -> Unit,
) {
    val nowMillis = now.toInstant().toEpochMilli()
    val rows = TodoWidgets.rows(data.todos, listName, nowMillis, now.zone)
    val open = TodoWidgets.openCount(data.todos, listName)
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
        val capacity = rowsThatFit(maxHeight - HeaderHeight)
        val narrow = maxWidth < 200.dp
        val showCount = maxWidth >= 150.dp
        Column(Modifier.fillMaxSize()) {
            WidgetHeader(
                icon = Icons.Rounded.Checklist.takeUnless { narrow },
                title = listName ?: "To-dos",
                trailing = if (open > 0 && showCount) "$open" else null,
                onAdd = { callbacks.onAddTodo(listName) },
                onLongPress = onLongPress,
            )
            if (rows.isEmpty()) {
                EmptyLine("All done. Tap + to add a to-do.")
            } else {
                val overflow = rows.size > capacity
                val shown = if (overflow) rows.take((capacity - 1).coerceAtLeast(1)) else rows
                shown.forEach { item -> TodoWidgetRow(item, now, use24Hour, callbacks, onLongPress) }
                if (overflow && capacity > 1) {
                    MoreRow(rows.size - shown.size, onClick = callbacks.onOpenTodos, onLongPress = onLongPress)
                }
            }
        }
    }
}

@Composable
private fun TodayWidget(
    data: LauncherWidgetData,
    now: ZonedDateTime,
    use24Hour: Boolean,
    callbacks: LauncherWidgetCallbacks,
    onLongPress: () -> Unit,
) {
    val overview = TodoWidgets.today(data.todos, data.todayBlocks, now.toInstant().toEpochMilli(), now.zone)
    val time = DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a")
    BoxWithConstraints(Modifier.fillMaxSize().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)) {
        val compact = maxHeight < HeaderHeight + RowHeight * 2
        val capacity = rowsThatFit(maxHeight - HeaderHeight - RowHeight)
        Column(Modifier.fillMaxSize(), verticalArrangement = if (compact) Arrangement.Center else Arrangement.Top) {
            if (!compact) {
                Row(Modifier.fillMaxWidth().height(HeaderHeight).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Today, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Today", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        now.format(DateTimeFormatter.ofPattern("EEE d MMM")),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val blockText = when {
                overview.current != null -> "Now · ${overview.current.title} · until ${overview.current.endTime.format(time)}"
                overview.next != null -> "Next · ${overview.next.startTime.format(time)} ${overview.next.title}"
                else -> "Nothing planned. Plan your day"
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(RowHeight)
                    .clip(RoundedCornerShape(12.dp))
                    .combinedClickable(onClick = callbacks.onOpenPlanner, onLongClick = onLongPress)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Schedule, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    blockText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (overview.current != null) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (compact) {
                if (overview.due.isNotEmpty()) {
                    Text(
                        "${overview.due.size} due today",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 26.dp),
                    )
                }
            } else {
                when {
                    overview.due.isEmpty() -> EmptyLine("Nothing due today")
                    capacity > 0 -> {
                        val overflow = overview.due.size > capacity
                        val shown = if (overflow) overview.due.take((capacity - 1).coerceAtLeast(1)) else overview.due
                        shown.forEach { TodoWidgetRow(it, now, use24Hour, callbacks, onLongPress) }
                        if (overflow && capacity > 1) MoreRow(overview.due.size - shown.size, callbacks.onOpenTodos, onLongPress)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickAddWidget(listName: String?, data: LauncherWidgetData) {
    val open = TodoWidgets.openCount(data.todos, listName)
    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.AddTask, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(
            if (listName == null) "Add a to-do" else "Add to $listName",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (open > 0) {
            Text("$open open", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun WidgetHeader(icon: ImageVector?, title: String, trailing: String?, onAdd: () -> Unit, onLongPress: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(HeaderHeight), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        // Title and count share the space left of the + button; the title gives way first.
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .combinedClickable(role = Role.Button, onClickLabel = "Add a to-do", onClick = onAdd, onLongClick = onLongPress),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, "Add a to-do", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun TodoWidgetRow(item: TodoItem, now: ZonedDateTime, use24Hour: Boolean, callbacks: LauncherWidgetCallbacks, onLongPress: () -> Unit) {
    val nowMillis = now.toInstant().toEpochMilli()
    val state = TodoWidgets.dueState(item, nowMillis, now.zone)
    Row(
        Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClickLabel = "Edit ${item.title}", onClick = { callbacks.onEditTodo(item) }, onLongClick = onLongPress),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(RowHeight)
                .clip(CircleShape)
                .combinedClickable(
                    role = Role.Checkbox,
                    onClickLabel = if (item.completed) "Mark ${item.title} as not done" else "Mark ${item.title} as done",
                    onClick = { callbacks.onToggleTodo(item) },
                    onLongClick = onLongPress,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (item.completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                null,
                tint = if (item.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
        }
        if (item.priority == TodoPriority.HIGH && !item.completed) {
            Box(Modifier.size(7.dp).background(MaterialTheme.colorScheme.error, CircleShape))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            item.title,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (item.completed) TextDecoration.LineThrough else null,
            color = if (item.completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        val due = item.dueAtMillis?.takeIf { !item.completed }?.let { dueText(it, state, now, use24Hour) }
        if (due != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                due,
                style = MaterialTheme.typography.labelSmall,
                color = if (state == DueState.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(end = 8.dp),
            )
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun MoreRow(count: Int, onClick: () -> Unit, onLongPress: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(RowHeight)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(start = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text("+$count more", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 4.dp, end = 8.dp, top = 6.dp),
    )
}

private fun rowsThatFit(height: Dp): Int = (height / RowHeight).toInt().coerceAtLeast(0)

private fun dueText(millis: Long, state: DueState?, now: ZonedDateTime, use24Hour: Boolean): String {
    val at = Instant.ofEpochMilli(millis).atZone(now.zone)
    return when (state) {
        DueState.OVERDUE -> "Overdue"
        DueState.TODAY -> at.format(DateTimeFormatter.ofPattern(if (use24Hour) "HH:mm" else "h:mm a"))
        DueState.TOMORROW -> "Tomorrow"
        DueState.LATER, null -> at.format(DateTimeFormatter.ofPattern("EEE d"))
    }
}

/** Small picture of a launcher widget for the widget picker. */
@Composable
fun LauncherWidgetPreview(kind: LauncherWidgetKind, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.size(72.dp),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(kind.icon, null, modifier = Modifier.size(20.dp))
            val lines = when (kind) {
                LauncherWidgetKind.TODO_LIST -> 3
                LauncherWidgetKind.TODAY -> 2
                LauncherWidgetKind.QUICK_ADD -> 1
            }
            repeat(lines) { index ->
                Box(
                    Modifier
                        .fillMaxWidth(if (index == 0) 0.9f else 0.65f)
                        .height(6.dp)
                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.35f), CircleShape),
                )
            }
        }
    }
}
