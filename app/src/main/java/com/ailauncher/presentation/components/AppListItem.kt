package com.ailauncher.presentation.components

import androidx.compose.material3.ListItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Thin wrapper over the Material 3 (1.5+) `ListItem` whose headline is the trailing `content`
 * slot; passing [onClick] uses the built-in clickable variant (ripple, semantics, focus).
 */
@Composable
fun AppListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    if (onClick != null) {
        ListItem(
            onClick = onClick,
            modifier = modifier,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            supportingContent = supportingContent,
            content = headlineContent,
        )
    } else {
        ListItem(
            modifier = modifier,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            supportingContent = supportingContent,
            content = headlineContent,
        )
    }
}
