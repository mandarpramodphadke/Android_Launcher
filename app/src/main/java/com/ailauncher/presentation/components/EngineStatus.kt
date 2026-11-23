package com.ailauncher.presentation.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ailauncher.domain.model.DeviceHealth
import com.ailauncher.domain.model.EngineState
import com.ailauncher.domain.model.ModelRuntimeInfo
import com.ailauncher.domain.model.ThermalLevel
import kotlinx.coroutines.delay

/** Seconds until the idle watchdog frees the model, ticking once per second. */
@Composable
private fun rememberCountdown(targetMillis: Long?): Long? {
    val remaining by produceState<Long?>(initialValue = null, targetMillis) {
        if (targetMillis == null) {
            value = null
            return@produceState
        }
        while (true) {
            value = ((targetMillis - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            delay(1000)
        }
    }
    return remaining
}

private fun mmss(seconds: Long) = "%d:%02d".format(seconds / 60, seconds % 60)

/** Live model status: state, RAM use and the auto-unload countdown. */
@Composable
fun EngineStatusChip(state: EngineState, health: DeviceHealth?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val countdown = rememberCountdown((state as? EngineState.Ready)?.idleUnloadAtMillis)
    val ram = health?.processRssBytes?.takeIf { it > 0 }?.let { context.formatBytes(it) }
    val label = when (state) {
        EngineState.NoModel -> "Model not downloaded"
        EngineState.Unloaded -> "Sleeping" + (ram?.let { " · $it" } ?: "")
        is EngineState.Loading -> "Loading ${(state.progress * 100).toInt()}%"
        is EngineState.Ready -> buildString {
            append("Ready")
            ram?.let { append(" · ").append(it) }
            countdown?.let { append(" · ").append(mmss(it)) }
        }
        is EngineState.Generating -> "Thinking" + (ram?.let { " · $it" } ?: "")
        is EngineState.Error -> "Model error"
        is EngineState.Unsupported -> "AI unsupported"
    }
    AssistChip(
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        leadingIcon = {
            when (state) {
                EngineState.NoModel -> Icon(Icons.Rounded.CloudDownload, null, Modifier.size(AssistChipDefaults.IconSize))
                EngineState.Unloaded -> Icon(Icons.Rounded.Bedtime, null, Modifier.size(AssistChipDefaults.IconSize))
                is EngineState.Error, is EngineState.Unsupported ->
                    Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(AssistChipDefaults.IconSize))
                else -> StatusDot(active = state is EngineState.Generating || state is EngineState.Loading)
            }
        },
        modifier = modifier,
    )
}

@Composable
private fun StatusDot(active: Boolean) {
    val alpha = if (active) {
        val transition = rememberInfiniteTransition(label = "dot")
        transition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
            label = "alpha",
        ).value
    } else {
        1f
    }
    Box(
        Modifier.size(10.dp).alpha(alpha).background(MaterialTheme.colorScheme.primary, CircleShape),
    )
}

/** Details sheet behind the status chip: RAM, model, context and manual load/unload. */
@Composable
fun EngineDetailsSheet(
    state: EngineState,
    health: DeviceHealth?,
    lastTokensPerSecond: Float?,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onManageModel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val info: ModelRuntimeInfo? = when (state) {
        is EngineState.Ready -> state.info
        is EngineState.Generating -> state.info
        else -> null
    }
    val countdown = rememberCountdown((state as? EngineState.Ready)?.idleUnloadAtMillis)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Memory, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("On-device model", style = MaterialTheme.typography.titleLarge)
            }
            if (state is EngineState.Loading) {
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            }
            health?.let { h ->
                DetailRow("Launcher memory", context.formatBytes(h.processRssBytes))
                DetailRow("Free RAM", "${context.formatBytes(h.availableRamBytes)} of ${context.formatBytes(h.totalRamBytes)}")
                if (h.thermalStatus >= ThermalLevel.MODERATE) {
                    DetailRow("Thermal", h.thermalStatus.name.lowercase().replaceFirstChar { it.uppercase() } + " - running slower")
                }
            }
            HorizontalDivider()
            DetailRow(
                "Status",
                when (state) {
                    EngineState.NoModel -> "Not downloaded"
                    EngineState.Unloaded -> "Not in memory (loads on demand)"
                    is EngineState.Loading -> "Loading…"
                    is EngineState.Ready -> "Loaded" + (countdown?.let { " · frees RAM in ${mmss(it)}" } ?: "")
                    is EngineState.Generating -> "Generating"
                    is EngineState.Error -> state.message
                    is EngineState.Unsupported -> state.reason
                },
            )
            info?.let {
                DetailRow("Model", it.name)
                if (it.parameterCount > 0) DetailRow("Parameters", "%.2f B".format(it.parameterCount / 1e9))
                DetailRow("Weights", context.formatBytes(it.sizeBytes) + " (memory-mapped)")
                DetailRow("Context window", "${it.contextSize} tokens")
                DetailRow("KV cache", it.kvCacheType)
                DetailRow("CPU threads", it.threads.toString())
            }
            lastTokensPerSecond?.let { DetailRow("Last reply speed", "%.1f tokens/s".format(it)) }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when (state) {
                    EngineState.NoModel, is EngineState.Unsupported ->
                        Button(onClick = onManageModel, modifier = Modifier.weight(1f)) { Text("Set up model") }
                    is EngineState.Ready, is EngineState.Generating -> {
                        OutlinedButton(onClick = onManageModel, modifier = Modifier.weight(1f)) { Text("Manage") }
                        Button(onClick = onUnload, modifier = Modifier.weight(1f)) { Text("Free memory now") }
                    }
                    else -> {
                        OutlinedButton(onClick = onManageModel, modifier = Modifier.weight(1f)) { Text("Manage") }
                        Button(onClick = onLoad, enabled = state !is EngineState.Loading, modifier = Modifier.weight(1f)) { Text("Load now") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1.4f))
    }
}
