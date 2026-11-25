package com.ailauncher.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.domain.model.FolderAccent
import com.ailauncher.domain.model.LauncherTool
import com.ailauncher.presentation.theme.LocalLauncherStyle

/** Container / content colours for a folder accent, legible in light and dark themes. */
@Composable
fun FolderAccent.colors(): Pair<Color, Color> {
    val dark = LocalLauncherStyle.current.dark
    val (light, deep) = when (this) {
        FolderAccent.INDIGO -> Color(0xFFDDE1FF) to Color(0xFF2B3592)
        FolderAccent.TEAL -> Color(0xFFC8F2EA) to Color(0xFF00504A)
        FolderAccent.AMBER -> Color(0xFFFFE0B2) to Color(0xFF6B3E00)
        FolderAccent.ROSE -> Color(0xFFFFD8E4) to Color(0xFF7A1E44)
        FolderAccent.GREEN -> Color(0xFFD6F0C8) to Color(0xFF1F5112)
        FolderAccent.SKY -> Color(0xFFCDE5FF) to Color(0xFF004A77)
        FolderAccent.VIOLET -> Color(0xFFEBDDFF) to Color(0xFF4C2A8C)
        FolderAccent.SLATE -> Color(0xFFE0E3E8) to Color(0xFF3A4250)
    }
    return if (dark) deep to light else light to deep
}

/** Icon of a launcher feature in the "AI Launcher" tools folder. */
val LauncherTool.icon: ImageVector
    get() = when (this) {
        LauncherTool.ASSISTANT -> Icons.Rounded.AutoAwesome
        LauncherTool.MY_DAY -> Icons.Rounded.Today
        LauncherTool.PLANNER -> Icons.AutoMirrored.Rounded.EventNote
        LauncherTool.TODOS -> Icons.Rounded.Checklist
        LauncherTool.CATCH_UP -> Icons.Rounded.NotificationsActive
        LauncherTool.PRIVATE_SPACE -> Icons.Rounded.Lock
        LauncherTool.WIDGETS -> Icons.Rounded.Widgets
        LauncherTool.SETTINGS -> Icons.Rounded.Settings
    }

/** A launcher feature drawn like an app icon: the theme's primary colours and a symbol. */
@Composable
fun LauncherToolIcon(tool: LauncherTool, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(tool.icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * A custom folder as an icon: its accent tile with up to four app icons (or launcher tools). A
 * smart folder, which fills itself, carries a small sparkle badge.
 */
@Composable
fun FolderIcon(folder: CustomFolder, apps: List<AppInfo>, size: Dp, modifier: Modifier = Modifier) {
    val (container, content) = folder.accent.colors()
    val mini = (size - 14.dp) / 2
    Box(modifier.size(size)) {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.3f))
                .background(container),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                val tools = folder.tools
                if (tools.isNotEmpty()) {
                    tools.take(4).chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { row.forEach { LauncherToolIcon(it, mini) } }
                    }
                } else {
                    apps.take(4).chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            row.forEach { app -> AppIcon(app, mini) }
                            if (row.size == 1) Spacer(Modifier.size(mini))
                        }
                    }
                }
            }
        }
        if (folder.isSmart) {
            val badge = (size * 0.34f).coerceAtLeast(14.dp)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = badge * 0.25f, y = badge * 0.25f)
                    .size(badge)
                    .background(content, CircleShape)
                    .border(1.5.dp, container, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.AutoAwesome, "Fills itself", tint = container, modifier = Modifier.size(badge * 0.62f))
            }
        }
    }
}

@Composable
fun FolderTile(
    folder: CustomFolder,
    apps: List<AppInfo>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 52.dp,
    showLabel: Boolean = true,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    labelShadow: Shadow? = null,
    /** Set on home pages: long-press and drag moves the folder. */
    drag: TileDragCallbacks? = null,
) {
    val haptics = LocalHapticFeedback.current
    var held by remember { mutableStateOf(false) }
    val lift by animateFloatAsState(if (held) 1.08f else 1f, label = "folder-lift")
    val gestures = if (drag != null) {
        val state = rememberTileGestureState(onTap = onClick, onLongPress = onLongClick, drag = drag, onHeld = { held = it })
        Modifier.tileGestures(state, tapLabel = "Open folder ${folder.name}", longPressLabel = "Folder options")
    } else {
        Modifier.combinedClickable(
            role = Role.Button,
            onClickLabel = "Open folder ${folder.name}",
            onLongClickLabel = "Folder options",
            onClick = onClick,
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            },
        )
    }
    Column(
        modifier
            .graphicsLayer {
                scaleX = lift
                scaleY = lift
            }
            .clip(RoundedCornerShape(16.dp))
            .then(gestures)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FolderIcon(folder, apps, iconSize)
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            TileLabel(folder.name, labelColor, labelShadow)
        }
    }
}
