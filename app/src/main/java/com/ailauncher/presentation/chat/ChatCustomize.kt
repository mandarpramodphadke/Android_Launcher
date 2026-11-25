package com.ailauncher.presentation.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.ailauncher.domain.model.AssistantProfile
import com.ailauncher.domain.model.AssistantTone
import com.ailauncher.domain.model.ChatBackground
import com.ailauncher.domain.model.ResponseLength
import com.ailauncher.presentation.components.LauncherTextField

/** Chat background and the assistant's behaviour, from the chat's top bar. */
@Composable
fun ChatCustomizeSheet(
    state: ChatUiState,
    onBackground: (ChatBackground) -> Unit,
    onPickPhoto: () -> Unit,
    onDim: (Float) -> Unit,
    onSaveAssistant: (AssistantProfile, thinkByDefault: Boolean) -> Unit,
    promptFor: (AssistantProfile) -> String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ChatCustomizeContent(state, onBackground, onPickPhoto, onDim, onSaveAssistant = { profile, think ->
            onSaveAssistant(profile, think)
            onDismiss()
        }, promptFor = promptFor)
    }
}

/** The sheet's content (the screenshot test renders it directly). */
@Composable
internal fun ChatCustomizeContent(
    state: ChatUiState,
    onBackground: (ChatBackground) -> Unit,
    onPickPhoto: () -> Unit,
    onDim: (Float) -> Unit,
    onSaveAssistant: (AssistantProfile, thinkByDefault: Boolean) -> Unit,
    promptFor: (AssistantProfile) -> String,
) {
    val name = rememberTextFieldState(state.assistant.name)
    val instructions = rememberTextFieldState(state.assistant.instructions)
    var length by remember { mutableStateOf(state.assistant.length) }
    var tone by remember { mutableStateOf(state.assistant.tone) }
    var think by remember { mutableStateOf(state.thinkByDefault) }
    var showPrompt by remember { mutableStateOf(false) }
    var dim by remember(state.backgroundDim) { mutableFloatStateOf(state.backgroundDim) }

    fun draft() = AssistantProfile(name.text.toString(), length, tone, instructions.text.toString())

    Column(
        Modifier
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Customize chat", style = MaterialTheme.typography.titleLarge)

        SectionTitle("Background")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = listOf(ChatBackground.DEFAULT to "Plain", ChatBackground.WALLPAPER to "Wallpaper", ChatBackground.PHOTO to "Photo")
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = state.background == value,
                    onClick = { if (value == ChatBackground.PHOTO && state.backgroundFile == null) onPickPhoto() else onBackground(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(label) }
            }
        }
        if (state.background == ChatBackground.PHOTO || state.backgroundFile != null) {
            OutlinedButton(onClick = onPickPhoto) {
                Icon(Icons.Rounded.Image, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.backgroundFile == null) "Choose a photo" else "Change photo")
            }
        }
        if (state.background != ChatBackground.DEFAULT) {
            Text("Veil, so messages stay readable", style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                VEILS.forEachIndexed { index, (value, label) ->
                    SegmentedButton(
                        selected = kotlin.math.abs(dim - value) < 0.08f,
                        onClick = {
                            dim = value
                            onDim(value)
                        },
                        shape = SegmentedButtonDefaults.itemShape(index, VEILS.size),
                    ) { Text(label) }
                }
            }
        }

        HorizontalDivider()
        SectionTitle("Assistant")
        LauncherTextField(
            state = name,
            placeholder = "Name (${AssistantProfile.DEFAULT_NAME})",
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        Text("Answers", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = listOf(ResponseLength.SHORT to "Short", ResponseLength.BALANCED to "Balanced", ResponseLength.DETAILED to "Detailed")
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(selected = length == value, onClick = { length = value }, shape = SegmentedButtonDefaults.itemShape(index, options.size)) { Text(label) }
            }
        }
        Text("Tone", style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = listOf(AssistantTone.FRIENDLY to "Friendly", AssistantTone.PROFESSIONAL to "Professional", AssistantTone.PLAYFUL to "Playful")
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(selected = tone == value, onClick = { tone = value }, shape = SegmentedButtonDefaults.itemShape(index, options.size)) { Text(label) }
            }
        }
        Text("Your instructions", style = MaterialTheme.typography.labelLarge)
        LauncherTextField(
            state = instructions,
            placeholder = "e.g. Reply in Hindi when I write in Hindi. Use metric units. I'm a student, keep it simple.",
            lineLimits = TextFieldLineLimits.MultiLine(minHeightInLines = 3, maxHeightInLines = 8),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${instructions.text.length} / ${AssistantProfile.MAX_INSTRUCTIONS}. Added to the assistant's system prompt; its safety rules always apply.",
            style = MaterialTheme.typography.bodySmall,
            color = if (instructions.text.length > AssistantProfile.MAX_INSTRUCTIONS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Think before answering", style = MaterialTheme.typography.bodyLarge)
                Text("Start each chat in thinking mode: slower, better for planning and maths", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = think, onCheckedChange = { think = it })
        }
        TextButton(onClick = { showPrompt = !showPrompt }) { Text(if (showPrompt) "Hide the full system prompt" else "Show the full system prompt") }
        AnimatedVisibility(showPrompt) {
            Text(
                promptFor(draft()),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp))
                    .padding(12.dp),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = {
                name.setTextAndPlaceCursorAtEnd(AssistantProfile.DEFAULT_NAME)
                instructions.setTextAndPlaceCursorAtEnd("")
                length = ResponseLength.BALANCED
                tone = AssistantTone.FRIENDLY
                think = false
            }) { Text("Reset") }
            Spacer(Modifier.weight(1f))
            Button(onClick = { onSaveAssistant(draft(), think) }) { Text("Save", fontWeight = FontWeight.SemiBold) }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

/** Veil strengths offered for a chat background (surface colour over the picture). */
private val VEILS = listOf(0.3f to "Light", 0.55f to "Medium", 0.75f to "Strong")
