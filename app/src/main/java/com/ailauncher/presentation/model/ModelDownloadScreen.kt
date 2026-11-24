package com.ailauncher.presentation.model

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ailauncher.domain.model.ModelFileState
import com.ailauncher.presentation.components.ThemedSystemBarIcons
import com.ailauncher.presentation.components.formatBytes
import com.ailauncher.presentation.components.formatDuration

@Composable
fun ModelDownloadScreen(
    firstRun: Boolean,
    onDone: () -> Unit,
    viewModel: ModelSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    ThemedSystemBarIcons()
    LaunchedEffect(Unit) { viewModel.markSeen() }

    // Android 13+: without this the foreground-download notification is hidden (the download still runs).
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { viewModel.download() }
    val startDownload = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.download()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::import)
    }
    val pickFile = { importLauncher.launch(arrayOf("application/octet-stream", "*/*")) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (firstRun) "" else "AI model") },
                navigationIcon = {
                    if (!firstRun) IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Rounded.Memory, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Text("On-device AI model", style = MaterialTheme.typography.headlineSmall)
            Text(
                "${state.spec.displayName} · ${context.formatBytes(state.spec.sizeBytes)}\n" +
                    "Powers the assistant and smart app sorting. Runs 100% offline - your conversations never leave this phone.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.options.size > 1) {
                ModelChooser(state, onSelect = viewModel::select, onDelete = viewModel::deleteOption)
            }

            DeviceCheck(
                totalRam = context.formatBytes(state.totalRamBytes),
                freeStorage = context.formatBytes(state.freeStorageBytes),
                lowRam = state.lowRam,
                lowStorage = state.lowStorage,
            )

            when (val file = state.file) {
                ModelFileState.Missing -> {
                    Button(onClick = startDownload, modifier = Modifier.fillMaxWidth(), enabled = !state.lowStorage) {
                        Icon(Icons.Rounded.CloudDownload, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Download (${context.formatBytes(state.spec.sizeBytes)})")
                    }
                    OutlinedButton(onClick = pickFile, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.FileOpen, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import a .gguf file")
                    }
                    Text(
                        "Downloads from Hugging Face over any connection. Use Wi-Fi to avoid mobile data charges.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                is ModelFileState.Downloading -> {
                    LinearProgressIndicator(progress = { file.fraction }, modifier = Modifier.fillMaxWidth())
                    val detail = buildString {
                        append("${context.formatBytes(file.downloadedBytes)} of ${context.formatBytes(file.totalBytes)}")
                        if (file.bytesPerSecond > 0) {
                            append(" · ${context.formatBytes(file.bytesPerSecond)}/s")
                            file.etaSeconds?.let { append(" · ${formatDuration(it)} left") }
                        } else {
                            append(" · waiting for network…")
                        }
                    }
                    Text(detail, style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = viewModel::pause) { Text("Pause") }
                        TextButton(onClick = viewModel::cancel) { Text("Cancel") }
                    }
                }
                is ModelFileState.Paused -> {
                    val fraction = if (file.totalBytes > 0) file.downloadedBytes.toFloat() / file.totalBytes else 0f
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    Text("Paused at ${(fraction * 100).toInt()}% · ${context.formatBytes(file.downloadedBytes)}", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = startDownload) { Text("Resume") }
                        TextButton(onClick = viewModel::cancel) { Text("Discard") }
                    }
                }
                is ModelFileState.Verifying -> {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Verifying download (SHA-256)…", style = MaterialTheme.typography.bodyMedium)
                }
                is ModelFileState.Importing -> {
                    if (file.totalBytes > 0) {
                        LinearProgressIndicator(progress = { file.copiedBytes.toFloat() / file.totalBytes }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    Text("Importing ${context.formatBytes(file.copiedBytes)}…", style = MaterialTheme.typography.bodyMedium)
                }
                is ModelFileState.Failed -> {
                    StatusCard(Icons.Rounded.Warning, file.message, error = true)
                    Button(onClick = startDownload, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
                    OutlinedButton(onClick = pickFile, modifier = Modifier.fillMaxWidth()) { Text("Import a .gguf file") }
                }
                is ModelFileState.Ready -> {
                    StatusCard(Icons.Rounded.CheckCircle, "Model ready · ${context.formatBytes(file.sizeBytes)}", error = false)
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text(if (firstRun) "Start using AI Launcher" else "Done") }
                    if (!firstRun) {
                        TextButton(onClick = { confirmDelete = true }) { Text("Delete model to free storage") }
                    }
                }
            }

            state.error?.let { StatusCard(Icons.Rounded.Warning, it, error = true) }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(
                    "Inference never uses the network.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (firstRun && state.file !is ModelFileState.Ready) {
                TextButton(onClick = onDone) { Text("Skip for now - use the launcher without AI") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete the AI model?") },
            text = { Text("The assistant and AI sorting stop working until you download it again. Your app categories are kept.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** The model switcher: Qwen3 0.6B / 1.7B / 4B and any imported GGUF files. */
@Composable
private fun ModelChooser(state: ModelSetupUiState, onSelect: (String) -> Unit, onDelete: (String) -> Unit) {
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Choose a model", style = MaterialTheme.typography.titleMedium)
        state.options.forEach { option ->
            val spec = option.spec
            Card(
                onClick = { onSelect(spec.id) },
                colors = CardDefaults.cardColors(
                    containerColor = if (option.active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = option.active, onClick = { onSelect(spec.id) })
                    Column(Modifier.weight(1f)) {
                        Text(spec.displayName, style = MaterialTheme.typography.titleSmall)
                        if (spec.summary.isNotBlank()) {
                            Text(spec.summary, style = MaterialTheme.typography.bodySmall)
                        }
                        val status = when (val file = option.state) {
                            is ModelFileState.Ready -> "Downloaded · ${context.formatBytes(file.sizeBytes)}"
                            is ModelFileState.Downloading -> "Downloading ${(file.fraction * 100).toInt()}%"
                            is ModelFileState.Paused -> "Paused · ${context.formatBytes(file.downloadedBytes)} of ${context.formatBytes(file.totalBytes)}"
                            is ModelFileState.Verifying -> "Verifying…"
                            is ModelFileState.Importing -> "Importing…"
                            is ModelFileState.Failed -> "Download failed"
                            ModelFileState.Missing -> "Not downloaded · ${context.formatBytes(spec.sizeBytes)}"
                        }
                        Text(status, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        if (state.tooBigFor(spec)) {
                            Text(
                                "Needs about ${context.formatBytes(spec.recommendedRamBytes)} of RAM; this phone has ${context.formatBytes(state.totalRamBytes)}.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    if (spec.imported || (option.state is ModelFileState.Ready && !option.active)) {
                        IconButton(onClick = { onDelete(spec.id) }) { Icon(Icons.Rounded.Delete, "Delete ${spec.displayName}") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCheck(totalRam: String, freeStorage: String, lowRam: Boolean, lowStorage: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("This phone", style = MaterialTheme.typography.titleSmall)
            Text("RAM: $totalRam" + if (lowRam) "  ·  below the recommended 4 GB, replies may be slow" else "", style = MaterialTheme.typography.bodyMedium)
            Text("Free storage: $freeStorage" + if (lowStorage) "  ·  not enough for the model" else "", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StatusCard(icon: ImageVector, text: String, error: Boolean) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
