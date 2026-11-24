package com.ailauncher.presentation.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ailauncher.domain.model.AppTheme
import com.ailauncher.domain.model.ThemeMode
import com.ailauncher.presentation.theme.LauncherColorSchemes
import com.ailauncher.presentation.theme.resolveDarkTheme

/** Row of miniature launcher previews, each drawn in its theme's real colours. */
@Composable
internal fun ThemePicker(selected: AppTheme, mode: ThemeMode, onSelect: (AppTheme) -> Unit) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    Column(Modifier.padding(vertical = 8.dp)) {
        Text("Theme", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
        // Open scrolled so the current theme is in view (the row is wider than a phone screen).
        val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selected.ordinal - 1).coerceAtLeast(0))
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.selectableGroup(),
        ) {
            items(AppTheme.entries, key = { it.name }) { theme ->
                val dark = resolveDarkTheme(theme, mode, systemDark)
                val dynamic = if (LauncherColorSchemes.usesWallpaperColors(theme) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                } else {
                    null
                }
                ThemePreview(theme, LauncherColorSchemes.forTheme(theme, dark, dynamic), theme == selected) { onSelect(theme) }
            }
        }
        Text(
            themeDescription(selected),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun ThemePreview(theme: AppTheme, scheme: ColorScheme, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(88.dp)
            .clip(shape)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
    ) {
        Box(
            Modifier
                .size(width = 88.dp, height = 120.dp)
                .clip(shape)
                .background(scheme.background)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = shape,
                ),
        ) {
            Column(Modifier.fillMaxSize().padding(10.dp)) {
                // A tiny home screen: clock, date, accent dots and the command bar.
                Box(Modifier.width(44.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(clockBrush(theme, scheme)))
                Spacer(Modifier.height(5.dp))
                Box(Modifier.width(30.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(scheme.onSurfaceVariant))
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(scheme.primary, scheme.secondary, scheme.tertiary).forEach { color ->
                        Box(Modifier.size(14.dp).background(color, CircleShape))
                    }
                }
                Spacer(Modifier.height(8.dp))
                val bar = RoundedCornerShape(8.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(bar)
                        .background(scheme.surfaceContainerHigh)
                        .then(
                            if (theme == AppTheme.NEON) {
                                Modifier.border(1.dp, Brush.horizontalGradient(listOf(scheme.primary, scheme.secondary)), bar)
                            } else {
                                Modifier
                            },
                        ),
                )
            }
            if (selected) {
                Icon(
                    Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            themeLabel(theme),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

private fun clockBrush(theme: AppTheme, scheme: ColorScheme): Brush = when (theme) {
    AppTheme.COLORFUL -> Brush.horizontalGradient(listOf(scheme.primary, scheme.secondary, scheme.tertiary))
    AppTheme.NEON -> Brush.horizontalGradient(listOf(scheme.primary, scheme.secondary))
    else -> SolidColor(scheme.onSurface)
}

internal fun themeLabel(theme: AppTheme): String = when (theme) {
    AppTheme.MATERIAL_YOU -> "Material You"
    AppTheme.CLASSIC -> "Classic"
    AppTheme.AMOLED -> "AMOLED"
    AppTheme.COLORFUL -> "Colourful"
    AppTheme.NEON -> "Neon"
    AppTheme.MONOCHROME -> "Mono"
}

private fun themeDescription(theme: AppTheme): String = when (theme) {
    AppTheme.MATERIAL_YOU ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "Colours taken from your wallpaper."
        else "Wallpaper colours need Android 12, so this phone shows the Classic colours."
    AppTheme.CLASSIC -> "Indigo and violet accents."
    AppTheme.AMOLED -> "True black, which saves battery on OLED screens. Always dark."
    AppTheme.COLORFUL -> "Vibrant accents, a gradient clock and colour-coded app categories."
    AppTheme.NEON -> "Glowing cyan, magenta and lime on deep night. Always dark."
    AppTheme.MONOCHROME -> "Black, white and greys only. Pairs well with minimal mode."
}
