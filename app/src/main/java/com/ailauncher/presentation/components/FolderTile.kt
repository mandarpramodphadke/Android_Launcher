package com.ailauncher.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.CustomFolder
import com.ailauncher.domain.model.FolderAccent
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

/** A custom folder as an icon: its accent tile with up to four app icons. */
@Composable
fun FolderIcon(folder: CustomFolder, apps: List<AppInfo>, size: Dp, modifier: Modifier = Modifier) {
    val (container, _) = folder.accent.colors()
    val mini = (size - 14.dp) / 2
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.3f))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            apps.take(4).chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    row.forEach { app -> AppIcon(app, mini) }
                    if (row.size == 1) Spacer(Modifier.size(mini))
                }
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
) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "Open folder ${folder.name}",
                onLongClickLabel = "Folder options",
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
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
