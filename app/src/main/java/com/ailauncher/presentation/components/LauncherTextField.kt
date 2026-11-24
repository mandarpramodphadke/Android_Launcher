package com.ailauncher.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The launcher's single text input, used by every screen and dialog.
 *
 * - **No cursor lag.** It edits a [TextFieldState] synchronously. The old fields echoed every
 *   keystroke through a ViewModel flow, and the late echo put the cursor *before* the letter
 *   just typed. ViewModels now read the state (`snapshotFlow { state.text }`) instead of feeding it.
 * - **Placeholder only when empty and unfocused**, always one line (no wrapping, ellipsized).
 * - **Legible on any wallpaper:** a 96%-opaque surfaceContainerHighest fill, a 1 dp border
 *   (outlineVariant, primary when focused), onSurface text and a primary cursor.
 * - A one-tap clear button whenever there is text.
 */
@Composable
fun LauncherTextField(
    state: TextFieldState,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (RowScope.() -> Unit)? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    inputTransformation: InputTransformation? = null,
    enabled: Boolean = true,
    showClear: Boolean = true,
    isError: Boolean = false,
    shape: Shape = RoundedCornerShape(28.dp),
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    minHeight: Dp = 52.dp,
    unfocusedBorder: Brush? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors = MaterialTheme.colorScheme
    BasicTextField(
        state = state,
        modifier = modifier,
        enabled = enabled,
        inputTransformation = inputTransformation,
        textStyle = textStyle.merge(TextStyle(color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f))),
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        lineLimits = lineLimits,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(colors.primary),
        decorator = launcherFieldDecorator(
            isEmpty = state.text.isEmpty(),
            placeholder = placeholder,
            interactionSource = interactionSource,
            leadingContent = leadingContent,
            trailingContent = trailingContent,
            onClear = if (showClear && enabled) ({ state.clearText() }) else null,
            isError = isError,
            shape = shape,
            textStyle = textStyle,
            minHeight = minHeight,
            unfocusedBorder = unfocusedBorder,
        ),
    )
}

/** [LauncherTextField] for passwords and passphrases, with a show/hide toggle. */
@Composable
fun LauncherSecureTextField(
    state: TextFieldState,
    placeholder: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    onKeyboardAction: KeyboardActionHandler? = null,
    imeAction: ImeAction = ImeAction.Done,
) {
    var visible by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.bodyLarge
    BasicSecureTextField(
        state = state,
        modifier = modifier,
        textStyle = textStyle.merge(TextStyle(color = colors.onSurface)),
        textObfuscationMode = if (visible) TextObfuscationMode.Visible else TextObfuscationMode.RevealLastTyped,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        onKeyboardAction = onKeyboardAction,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(colors.primary),
        decorator = launcherFieldDecorator(
            isEmpty = state.text.isEmpty(),
            placeholder = placeholder,
            interactionSource = interactionSource,
            leadingContent = null,
            trailingContent = {
                IconButton(onClick = { visible = !visible }) {
                    Icon(
                        if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                        contentDescription = if (visible) "Hide passphrase" else "Show passphrase",
                        tint = colors.onSurfaceVariant,
                    )
                }
            },
            onClear = null,
            isError = isError,
            shape = RoundedCornerShape(16.dp),
            textStyle = textStyle,
            minHeight = 52.dp,
            unfocusedBorder = null,
        ),
    )
}

@Composable
private fun launcherFieldDecorator(
    isEmpty: Boolean,
    placeholder: String,
    interactionSource: MutableInteractionSource,
    leadingContent: @Composable (() -> Unit)?,
    trailingContent: @Composable (RowScope.() -> Unit)?,
    onClear: (() -> Unit)?,
    isError: Boolean,
    shape: Shape,
    textStyle: TextStyle,
    minHeight: Dp,
    unfocusedBorder: Brush?,
): TextFieldDecorator {
    val focused by interactionSource.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val border = when {
        isError -> BorderStroke(1.dp, colors.error)
        focused -> BorderStroke(1.dp, colors.primary)
        unfocusedBorder != null -> BorderStroke(1.dp, unfocusedBorder)
        else -> BorderStroke(1.dp, colors.outlineVariant)
    }
    return TextFieldDecorator { innerTextField ->
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .background(colors.surfaceContainerHighest.copy(alpha = 0.96f), shape)
                .border(border, shape)
                .padding(start = if (leadingContent != null) 4.dp else 18.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingContent?.invoke()
            Box(Modifier.weight(1f).padding(vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                // Shown only while empty AND unfocused: it vanishes the moment the field is focused.
                if (isEmpty && !focused) {
                    Text(
                        placeholder,
                        style = textStyle,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                innerTextField()
            }
            if (onClear != null && !isEmpty) {
                IconButton(onClick = onClear, modifier = Modifier.semantics { contentDescription = "Clear text" }) {
                    Icon(Icons.Rounded.Close, contentDescription = null, tint = colors.onSurfaceVariant)
                }
            }
            if (trailingContent != null) trailingContent() else Spacer(Modifier.width(12.dp))
        }
    }
}

/**
 * The home screen's AI command bar: type to search apps, send to ask the assistant, sparkle to
 * open the chat, microphone for offline voice input.
 */
@Composable
fun LauncherCommandBar(
    state: TextFieldState,
    onSubmit: (String) -> Unit,
    onOpenChat: () -> Unit,
    onVoice: (() -> Unit)?,
    modifier: Modifier = Modifier,
    placeholder: String = "Ask Aura or search apps",
    sparkleModifier: Modifier = Modifier,
    unfocusedBorder: Brush? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors = MaterialTheme.colorScheme
    val submit = {
        val text = state.text.toString().trim()
        if (text.isNotEmpty()) onSubmit(text)
    }
    LauncherTextField(
        state = state,
        placeholder = placeholder,
        modifier = modifier,
        interactionSource = interactionSource,
        unfocusedBorder = unfocusedBorder,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        onKeyboardAction = { submit() },
        leadingContent = {
            IconButton(onClick = onOpenChat) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = "Open AI assistant", tint = colors.primary, modifier = sparkleModifier)
            }
        },
        trailingContent = {
            when {
                state.text.isNotBlank() -> IconButton(onClick = submit) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Ask the assistant", tint = colors.primary)
                }
                onVoice != null -> IconButton(onClick = onVoice) {
                    Icon(Icons.Rounded.Mic, contentDescription = "Voice input", tint = colors.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
                else -> Spacer(Modifier.width(12.dp))
            }
        },
    )
}
