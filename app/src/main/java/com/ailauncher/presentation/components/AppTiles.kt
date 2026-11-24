package com.ailauncher.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.ailauncher.data.packages.AppIconData
import com.ailauncher.domain.model.AppInfo
import com.ailauncher.domain.model.IconStyle
import com.ailauncher.presentation.theme.LocalLauncherStyle

/** Where an icon is on screen, captured without recomposition (read on tap for the launch animation). */
class IconBounds {
    var rect: Rect = Rect.Zero
}

fun Modifier.trackBounds(bounds: IconBounds): Modifier = onGloballyPositioned { bounds.rect = it.boundsInRoot() }

/**
 * Launcher icon loaded asynchronously through Coil's [com.ailauncher.data.packages.AppIconFetcher],
 * in the icon style chosen in settings (themed icons take the theme's container colours), with an
 * optional unread badge from the notification digest.
 */
@Composable
fun AppIcon(app: AppInfo, size: Dp, modifier: Modifier = Modifier, badgeCount: Int = 0) {
    val style = LocalLauncherStyle.current.iconStyle
    val background = if (style == IconStyle.THEMED) MaterialTheme.colorScheme.primaryContainer.toArgb() else 0
    val foreground = if (style == IconStyle.THEMED) MaterialTheme.colorScheme.onPrimaryContainer.toArgb() else 0
    val model = remember(app.packageName, app.activityName, app.updatedAt, style, background, foreground) {
        AppIconData(app.packageName, app.activityName, app.updatedAt, style, background, foreground)
    }
    Box(modifier.size(size)) {
        AsyncImage(model = model, contentDescription = null, modifier = Modifier.size(size))
        if (badgeCount > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-2).dp)
                    .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (badgeCount > 99) "99+" else badgeCount.toString(),
                    color = MaterialTheme.colorScheme.onError,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Icon + label cell for the home pages, the dock, the drawer grid and search results. Labels are
 * one 12 sp line, ellipsized, so long names never wrap into the next row.
 *
 * @param onClick receives the icon's bounds (root coordinates) for the clip-reveal launch.
 */
@Composable
fun AppGridTile(
    app: AppInfo,
    onClick: (Rect) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 52.dp,
    showLabel: Boolean = true,
    labelColor: Color = MaterialTheme.colorScheme.onSurface,
    labelShadow: Shadow? = null,
    badgeCount: Int = 0,
) {
    val haptics = LocalHapticFeedback.current
    val bounds = remember { IconBounds() }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "Open ${app.label}",
                onLongClickLabel = "App options",
                onClick = { onClick(bounds.rect) },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(app, iconSize, Modifier.trackBounds(bounds), badgeCount = badgeCount)
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            TileLabel(app.label, labelColor, labelShadow)
        }
    }
}

@Composable
fun TileLabel(text: String, color: Color, shadow: Shadow?) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, lineHeight = 16.sp, shadow = shadow),
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
    )
}

/** Full-width row for the list layout of the drawer; text-only (no icon) in minimal mode. */
@Composable
fun AppListRow(
    app: AppInfo,
    onClick: (Rect) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    showIcon: Boolean = true,
    badgeCount: Int = 0,
) {
    val haptics = LocalHapticFeedback.current
    val bounds = remember { IconBounds() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .trackBounds(bounds)
            .combinedClickable(
                onClick = { onClick(bounds.rect) },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .padding(horizontal = if (showIcon) 12.dp else 16.dp, vertical = if (showIcon) 8.dp else 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        if (showIcon) {
            AppIcon(app, 40.dp, badgeCount = badgeCount)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                app.label,
                style = if (showIcon) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (supportingText != null) {
                Text(
                    supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}
