package com.ailauncher.domain.system

import com.ailauncher.domain.model.LiveSystemStatus
import com.ailauncher.domain.model.SystemDetails
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SectionKey { BATTERY, CPU, GPU, MEMORY, STORAGE, DISPLAY, DEVICE, SOFTWARE, NETWORK, THERMAL, CAMERAS, SENSORS, FEATURES }

/** One card of the System info page: a title and label/value rows. */
data class InfoSection(val key: SectionKey, val title: String, val rows: List<Pair<String, String>>)

/**
 * Turns what the phone reports into the System info page's sections. Pure, so the page and the
 * "Copy all" text always say the same thing. Missing values are left out.
 */
object SystemInfoSections {

    fun build(details: SystemDetails?, live: LiveSystemStatus?): List<InfoSection> = buildList {
        live?.battery?.let { b ->
            add(
                InfoSection(
                    SectionKey.BATTERY,
                    "Battery",
                    rows {
                        b.levelPercent?.let { row("Level", "$it%") }
                        row("Status", listOfNotNull(b.state, b.plugged).joinToString(" · "))
                        b.chargeTimeRemainingMillis?.let { row("Full in", SystemFormat.duration(it)) }
                        b.health?.let { row("Health", it) }
                        b.temperatureC?.let { row("Temperature", String.format(Locale.US, "%.1f °C", it)) }
                        b.voltageMv?.let { row("Voltage", String.format(Locale.US, "%.2f V", it / 1000f)) }
                        b.currentMilliAmps?.let { row("Current", "${if (it > 0) "+" else if (it < 0) "−" else ""}${abs(it)} mA") }
                        b.estimatedCapacityMah?.let { row("Capacity (estimated)", "≈${group(it)} mAh") }
                        b.cycleCount?.let { row("Charge cycles", it.toString()) }
                        b.technology?.let { row("Technology", it) }
                    },
                ),
            )
        }

        if (details != null) {
            val cpu = details.cpu
            add(
                InfoSection(
                    SectionKey.CPU,
                    "Processor",
                    rows {
                        cpu.name?.let { row("Chip", it) }
                        row("Cores", if (cpu.clusters.size > 1) "${cpu.cores} (${cpu.clusters.joinToString(" + ") { it.cores.toString() }})" else cpu.cores.toString())
                        cpu.clusters.forEach { cluster ->
                            val label = "${cluster.cores} × ${cluster.coreName ?: "core"}"
                            row(label, cluster.maxKhz?.let { "up to ${SystemFormat.frequency(it)}" } ?: "—")
                        }
                        val abi = details.device.abis.firstOrNull()
                        (cpu.architecture ?: abi)?.let { row("Architecture", listOfNotNull(cpu.architecture, abi?.takeIf { a -> a != cpu.architecture }).joinToString(" · ")) }
                        cpu.governor?.let { row("Governor", it) }
                        if (cpu.features.isNotEmpty()) row("Instruction sets", cpu.features.joinToString(" "))
                    },
                ),
            )
            val gpu = details.gpu
            add(
                InfoSection(
                    SectionKey.GPU,
                    "Graphics",
                    rows {
                        gpu.renderer?.let { row("GPU", it) }
                        gpu.vendor?.let { row("Vendor", it) }
                        gpu.openGlVersion?.let { row("OpenGL ES", it.removePrefix("OpenGL ES ")) }
                        row("Vulkan", gpu.vulkanVersion ?: "Not supported")
                        live?.gpuBusyPercent?.let { row("Load", "$it%") }
                        live?.gpuClockMhz?.let { row("Clock", "$it MHz") }
                        gpu.extensions?.let { row("OpenGL extensions", it.toString()) }
                    },
                ),
            )
        }

        live?.memory?.let { m ->
            add(
                InfoSection(
                    SectionKey.MEMORY,
                    "Memory (RAM)",
                    rows {
                        row("Installed", SystemFormat.installedRam(m.totalBytes))
                        row("Usable", SystemFormat.memory(m.totalBytes))
                        row("Used", "${SystemFormat.memory(m.usedBytes)} (${SystemFormat.percent(m.usedFraction)})")
                        row("Available", SystemFormat.memory(m.availableBytes))
                        val swapTotal = m.swapTotalBytes
                        if (swapTotal != null) {
                            val used = (swapTotal - (m.swapFreeBytes ?: swapTotal)).coerceAtLeast(0)
                            row("Swap (zRAM)", "${SystemFormat.memory(used)} of ${SystemFormat.memory(swapTotal)}")
                        }
                        row("Low-memory threshold", SystemFormat.memory(m.lowMemoryThresholdBytes))
                        if (m.lowMemory) row("State", "Low on memory")
                        row("Per-app limit", SystemFormat.memory(m.appHeapLimitBytes))
                    },
                ),
            )
        }

        live?.storage?.takeIf { it.isNotEmpty() }?.let { volumes ->
            add(
                InfoSection(
                    SectionKey.STORAGE,
                    "Storage",
                    rows {
                        volumes.forEachIndexed { index, v ->
                            // The first volume is the phone's own storage; others (SD cards) are named.
                            val prefix = if (index == 0) "" else "${v.name}: "
                            row("${prefix}Capacity", SystemFormat.bytes(v.capacityBytes ?: v.totalBytes))
                            row("${prefix}Used", "${SystemFormat.bytes(v.usedBytes)} (${SystemFormat.percent(v.usedFraction)})")
                            row("${prefix}Free", SystemFormat.bytes(v.freeBytes))
                        }
                    },
                ),
            )
        }

        if (details != null) {
            details.display?.let { d ->
                add(
                    InfoSection(
                        SectionKey.DISPLAY,
                        "Display",
                        rows {
                            row("Resolution", "${d.widthPx} × ${d.heightPx}")
                            row("Density", "${d.densityDpi} dpi")
                            d.diagonalInches?.let { row("Size", String.format(Locale.US, "≈%.1f in", it)) }
                            val current = d.refreshRateHz.roundToInt()
                            val max = d.maxRefreshRateHz.roundToInt()
                            row("Refresh rate", if (max > current) "$current Hz (up to $max Hz)" else "$current Hz")
                            row("HDR", if (d.hdr) "Yes" else "No")
                            row("Wide colour", if (d.wideColorGamut) "Yes" else "No")
                        },
                    ),
                )
            }
            val dev = details.device
            add(
                InfoSection(
                    SectionKey.DEVICE,
                    "Device",
                    rows {
                        row("Brand", dev.brand)
                        if (!dev.manufacturer.equals(dev.brand, ignoreCase = true)) row("Manufacturer", dev.manufacturer)
                        row("Model", dev.model)
                        row("Code name", dev.device)
                        if (dev.socModel != null) row("SoC", listOfNotNull(dev.socManufacturer, dev.socModel).joinToString(" "))
                        row("Board", dev.board)
                        row("Hardware", dev.hardware)
                    },
                ),
            )
            add(
                InfoSection(
                    SectionKey.SOFTWARE,
                    "Software",
                    rows {
                        row("Android", "${dev.androidVersion} (API ${dev.sdkInt})")
                        dev.securityPatch?.let { row("Security patch", it) }
                        row("Build number", dev.buildNumber)
                        dev.kernel?.let { row("Kernel", it) }
                        dev.bootloader?.let { row("Bootloader", it) }
                        dev.javaVm?.let { row("Runtime", it) }
                        if (dev.abis.isNotEmpty()) row("Supported ABIs", dev.abis.joinToString(", "))
                        dev.mediaPerformanceClass?.let { row("Media performance class", it.toString()) }
                        live?.let { row("Up time", SystemFormat.duration(it.uptimeMillis)) }
                    },
                ),
            )
        }

        live?.network?.let { n ->
            add(
                InfoSection(
                    SectionKey.NETWORK,
                    "Network",
                    rows {
                        row("Connection", n.type)
                        if (n.type != "Offline") {
                            row("Internet", if (n.validated) "Working" else "No internet access")
                            row("Metered", if (n.metered) "Yes" else "No")
                            val down = n.downstreamKbps
                            val up = n.upstreamKbps
                            if (down != null || up != null) {
                                row("Speed estimate", listOfNotNull(down?.let { "↓ ${speed(it)}" }, up?.let { "↑ ${speed(it)}" }).joinToString("  "))
                            }
                        }
                    },
                ),
            )
        }

        live?.thermal?.let { t ->
            add(
                InfoSection(
                    SectionKey.THERMAL,
                    "Temperature & power",
                    rows {
                        row("Thermal state", t.status)
                        t.headroom?.let { row("Before throttling", "${SystemFormat.percent(1f - it.coerceIn(0f, 1f))} headroom") }
                        row("Battery saver", if (t.powerSaver) "On" else "Off")
                    },
                ),
            )
        }

        if (details != null) {
            if (details.cameras.isNotEmpty()) {
                val counters = HashMap<String, Int>()
                add(
                    InfoSection(
                        SectionKey.CAMERAS,
                        "Cameras",
                        rows {
                            details.cameras.forEach { c ->
                                val n = counters.merge(c.facing, 1, Int::plus) ?: 1
                                val sameFacing = details.cameras.count { it.facing == c.facing }
                                val label = if (sameFacing > 1) "${c.facing} camera $n" else "${c.facing} camera"
                                val parts = listOfNotNull(
                                    c.megapixels?.let { mp -> String.format(Locale.US, "%.1f MP", mp).replace(".0 MP", " MP") },
                                    c.focalLengthsMm.firstOrNull()?.let { mm -> String.format(Locale.US, "%.1f mm", mm) },
                                    "flash".takeIf { c.hasFlash },
                                )
                                row(label, parts.joinToString(" · ").ifEmpty { "—" })
                            }
                        },
                    ),
                )
            }
            if (details.features.isNotEmpty()) {
                add(InfoSection(SectionKey.FEATURES, "Features", listOf("Supported" to details.features.joinToString(", "))))
            }
            if (details.sensors.isNotEmpty()) {
                add(
                    InfoSection(
                        SectionKey.SENSORS,
                        "Sensors (${details.sensors.size})",
                        details.sensors.map { it.name to listOf(it.type, it.vendor).filter(String::isNotBlank).joinToString(" · ") },
                    ),
                )
            }
        }
    }

    /** Plain text of every section, for "Copy all". */
    fun asText(sections: List<InfoSection>): String = sections.joinToString("\n\n") { section ->
        section.title + "\n" + section.rows.joinToString("\n") { (label, value) -> "$label: $value" }
    }

    private class RowBuilder {
        val rows = ArrayList<Pair<String, String>>()
        fun row(label: String, value: String) {
            if (value.isNotBlank()) rows += label to value
        }
    }

    private inline fun rows(block: RowBuilder.() -> Unit): List<Pair<String, String>> = RowBuilder().apply(block).rows

    private fun group(value: Int): String = String.format(Locale.US, "%,d", value)

    private fun speed(kbps: Int): String = if (kbps >= 1000) "${(kbps / 1000f).roundToInt()} Mbps" else "$kbps kbps"
}
