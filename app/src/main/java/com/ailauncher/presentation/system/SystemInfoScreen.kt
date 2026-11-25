package com.ailauncher.presentation.system

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeveloperBoard
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ailauncher.domain.model.CpuCoreStatus
import com.ailauncher.domain.system.CpuClusters
import com.ailauncher.domain.system.InfoSection
import com.ailauncher.domain.system.SectionKey
import com.ailauncher.domain.system.SystemFormat
import com.ailauncher.presentation.components.SwipeDirection
import com.ailauncher.presentation.components.ThemedSystemBarIcons
import com.ailauncher.presentation.components.horizontalSwipe
import com.ailauncher.presentation.components.rememberSwipeAction
import kotlinx.coroutines.launch

/**
 * The page after the AI chat: battery, memory, storage, processor, graphics, display, device and
 * software details, live while it is open. Swipe right (or Back) to return to the chat.
 */
@Composable
fun SystemInfoScreen(onBack: () -> Unit, viewModel: SystemInfoViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    ThemedSystemBarIcons()
    val swipeBack = rememberSwipeAction(onBack)
    SystemInfoContent(
        state = state,
        onBack = onBack,
        onCopy = {
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("System info", viewModel.reportText()))
            scope.launch { snackbar.showSnackbar("System info copied") }
        },
        snackbar = snackbar,
        // Swipe right (the way you came) goes back to the assistant.
        modifier = Modifier.horizontalSwipe(SwipeDirection.RIGHT, swipeBack),
    )
}

/** The page itself, stateless (the screenshot test renders it with fixed values). */
@Composable
internal fun SystemInfoContent(
    state: SystemInfoUiState,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to the assistant") } },
                title = { Text("System info") },
                actions = {
                    IconButton(onClick = onCopy, enabled = state.sections.isNotEmpty()) { Icon(Icons.Rounded.ContentCopy, "Copy all") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Reading your phone…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 4.dp, bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "overview") { Overview(state) }
            items(state.sections, key = { it.key.name }) { section -> SectionCard(section, state) }
            item(key = "footer") {
                Text(
                    "Read on this phone only. Nothing is saved or sent anywhere.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }
        }
    }
}

/** Device name, Android version and chip, with battery, RAM and storage rings. */
@Composable
private fun Overview(state: SystemInfoUiState) {
    val device = state.details?.device
    val live = state.live
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Smartphone, null, Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        device?.let { listOf(it.brand, it.model).distinct().joinToString(" ") } ?: "This phone",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = listOfNotNull(
                        device?.let { "Android ${it.androidVersion}" },
                        state.details?.cpu?.name,
                    ).joinToString(" · ")
                    if (subtitle.isNotEmpty()) Text(subtitle, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val battery = live?.battery
                Ring(
                    fraction = battery?.levelPercent?.div(100f),
                    value = battery?.levelPercent?.let { "$it%" } ?: "—",
                    label = when {
                        battery == null -> "Battery"
                        battery.state == "Charging" -> "Charging"
                        else -> "Battery"
                    },
                )
                val memory = live?.memory
                Ring(
                    fraction = memory?.usedFraction,
                    value = memory?.let { SystemFormat.percent(it.usedFraction) } ?: "—",
                    label = memory?.let { "RAM · ${SystemFormat.installedRam(it.totalBytes)}" } ?: "RAM",
                )
                val storage = live?.storage?.firstOrNull()
                Ring(
                    fraction = storage?.usedFraction,
                    value = storage?.let { SystemFormat.percent(it.usedFraction) } ?: "—",
                    label = storage?.let { "Storage · ${SystemFormat.bytes(it.capacityBytes ?: it.totalBytes)}" } ?: "Storage",
                )
            }
        }
    }
}

@Composable
private fun Ring(fraction: Float?, value: String, label: String) {
    val track = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.18f)
    val progress = MaterialTheme.colorScheme.primary
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics { contentDescription = "$label $value" }) {
        Box(Modifier.size(84.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round)
                val inset = stroke.width / 2
                val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke.width, size.height - stroke.width)
                drawArc(track, startAngle = 135f, sweepAngle = 270f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = stroke)
                if (fraction != null) {
                    drawArc(progress, startAngle = 135f, sweepAngle = 270f * fraction.coerceIn(0f, 1f), useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = stroke)
                }
            }
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private val SectionKey.icon: ImageVector
    get() = when (this) {
        SectionKey.BATTERY -> Icons.Rounded.BatteryChargingFull
        SectionKey.CPU -> Icons.Rounded.Memory
        SectionKey.GPU -> Icons.Rounded.ViewInAr
        SectionKey.MEMORY -> Icons.Rounded.DeveloperBoard
        SectionKey.STORAGE -> Icons.Rounded.SdStorage
        SectionKey.DISPLAY -> Icons.Rounded.Tv
        SectionKey.DEVICE -> Icons.Rounded.PhoneAndroid
        SectionKey.SOFTWARE -> Icons.Rounded.Storage
        SectionKey.NETWORK -> Icons.Rounded.NetworkCheck
        SectionKey.THERMAL -> Icons.Rounded.Thermostat
        SectionKey.CAMERAS -> Icons.Rounded.CameraAlt
        SectionKey.SENSORS -> Icons.Rounded.Sensors
        SectionKey.FEATURES -> Icons.Rounded.Widgets
    }

@Composable
private fun SectionCard(section: InfoSection, state: SystemInfoUiState) {
    var expanded by rememberSaveable(section.key) { mutableStateOf(false) }
    val collapsible = section.key == SectionKey.SENSORS && section.rows.size > COLLAPSED_ROWS
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.animateContentSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(section.key.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            when (section.key) {
                SectionKey.CPU -> state.live?.cpuCores?.takeIf { cores -> cores.any { it.currentKhz != null } }?.let { cores ->
                    CpuCores(cores, state.cpuHistory)
                    Spacer(Modifier.height(10.dp))
                }
                SectionKey.MEMORY -> state.live?.memory?.let {
                    UsageBar(it.usedFraction, "${SystemFormat.memory(it.usedBytes)} of ${SystemFormat.memory(it.totalBytes)} in use")
                    Sparkline(state.ramHistory, Modifier.fillMaxWidth().height(36.dp).padding(top = 6.dp))
                    Spacer(Modifier.height(10.dp))
                }
                SectionKey.STORAGE -> state.live?.storage?.forEach {
                    UsageBar(it.usedFraction, "${it.name}: ${SystemFormat.bytes(it.usedBytes)} used, ${SystemFormat.bytes(it.freeBytes)} free")
                    Spacer(Modifier.height(10.dp))
                }
                SectionKey.BATTERY -> state.live?.battery?.levelPercent?.let {
                    UsageBar(it / 100f, null)
                    Spacer(Modifier.height(10.dp))
                }
                else -> Unit
            }
            val rows = if (collapsible && !expanded) section.rows.take(COLLAPSED_ROWS) else section.rows
            rows.forEach { (label, value) -> InfoRow(label, value) }
            if (collapsible) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Show fewer" else "Show all ${section.rows.size}")
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f).padding(end = 12.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, modifier = Modifier.weight(0.58f))
    }
}

@Composable
private fun UsageBar(fraction: Float, caption: String?) {
    Column {
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** Each core's current speed against its maximum, and the last minute of clock load. */
@Composable
private fun CpuCores(cores: List<CpuCoreStatus>, history: List<Float>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CpuClusters.clockLoad(cores)?.let { load ->
            Text("Clock load ${SystemFormat.percent(load)} · last minute", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Sparkline(history, Modifier.fillMaxWidth().height(40.dp))
        cores.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { core -> CoreBar(core, Modifier.weight(1f)) }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CoreBar(core: CpuCoreStatus, modifier: Modifier = Modifier) {
    val current = core.currentKhz
    val max = core.maxKhz
    Column(modifier) {
        Row {
            Text("CPU ${core.index}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(
                when {
                    !core.online -> "Off"
                    current != null -> SystemFormat.frequency(current)
                    else -> "—"
                },
                style = MaterialTheme.typography.labelMedium,
            )
        }
        LinearProgressIndicator(
            progress = { if (core.online && current != null && max != null && max > 0) (current.toFloat() / max).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp),
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
    }
}

/** A small line chart of 0..1 values (the newest on the right). */
@Composable
private fun Sparkline(values: List<Float>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val step = size.width / (SystemInfoUiState.HISTORY - 1)
        val start = size.width - step * (values.size - 1)
        val points = values.mapIndexed { i, v -> Offset(start + i * step, size.height * (1f - v.coerceIn(0f, 1f))) }
        val stroke = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val area = Path().apply {
            addPath(stroke)
            lineTo(points.last().x, size.height)
            lineTo(points.first().x, size.height)
            close()
        }
        drawPath(area, fill)
        drawPath(stroke, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

private const val COLLAPSED_ROWS = 6
