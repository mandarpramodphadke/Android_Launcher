package com.ailauncher.domain.system

import com.ailauncher.domain.model.CpuCluster
import com.ailauncher.domain.model.CpuCoreStatus
import java.util.Locale
import kotlin.math.roundToInt

/** A core's ARM identity from /proc/cpuinfo ("CPU implementer" and "CPU part"). */
data class CoreId(val implementer: Int, val part: Int)

/** What /proc/cpuinfo says: the SoC line ("Hardware"), the CPU features and each core's identity. */
data class ParsedCpuInfo(
    val hardware: String?,
    val modelName: String?,
    val features: List<String>,
    val cores: Map<Int, CoreId>,
)

/** Pure parsing of /proc and /sys text for the System info page. */
object CpuInfoParser {

    fun parse(text: String): ParsedCpuInfo {
        var hardware: String? = null
        var modelName: String? = null
        var features: List<String> = emptyList()
        val cores = LinkedHashMap<Int, CoreId>()
        var processor: Int? = null
        var implementer: Int? = null
        text.lineSequence().forEach { raw ->
            val key = raw.substringBefore(':').trim().lowercase(Locale.ROOT)
            val value = raw.substringAfter(':', "").trim()
            when (key) {
                "processor" -> {
                    processor = value.toIntOrNull()
                    implementer = null
                }
                "hardware" -> hardware = value.takeIf { it.isNotEmpty() }
                "model name" -> if (modelName == null) modelName = value.takeIf { it.isNotEmpty() }
                "features", "flags" -> if (features.isEmpty()) features = value.split(' ').filter { it.isNotBlank() }
                "cpu implementer" -> implementer = parseHex(value)
                "cpu part" -> {
                    val core = processor
                    val impl = implementer
                    val part = parseHex(value)
                    if (core != null && impl != null && part != null) cores[core] = CoreId(impl, part)
                }
            }
        }
        return ParsedCpuInfo(hardware, modelName, features, cores)
    }

    /** "0-7" or "0-3,6" → the core indices. */
    fun parseRange(text: String?): List<Int> = text.orEmpty().trim().split(',').flatMap { part ->
        val bounds = part.trim().split('-').mapNotNull { it.trim().toIntOrNull() }
        when (bounds.size) {
            1 -> listOf(bounds[0])
            2 -> if (bounds[1] >= bounds[0]) (bounds[0]..bounds[1]).toList() else emptyList()
            else -> emptyList()
        }
    }

    /** /proc/meminfo → value in bytes per key ("MemTotal", "SwapFree", …). */
    fun parseMemInfo(text: String): Map<String, Long> = text.lineSequence().mapNotNull { line ->
        val key = line.substringBefore(':').trim()
        val kb = line.substringAfter(':', "").trim().substringBefore(' ').toLongOrNull()
        if (key.isEmpty() || kb == null) null else key to kb * 1024
    }.toMap()

    private fun parseHex(value: String): Int? = value.removePrefix("0x").removePrefix("0X").toIntOrNull(16)
}

/** Names of common phone CPU cores by implementer and part number. */
object ArmCores {
    private val names = mapOf(
        // Arm
        0x41 to mapOf(
            0xd03 to "Cortex-A53", 0xd04 to "Cortex-A35", 0xd05 to "Cortex-A55", 0xd07 to "Cortex-A57",
            0xd08 to "Cortex-A72", 0xd09 to "Cortex-A73", 0xd0a to "Cortex-A75", 0xd0b to "Cortex-A76",
            0xd0d to "Cortex-A77", 0xd41 to "Cortex-A78", 0xd44 to "Cortex-X1", 0xd46 to "Cortex-A510",
            0xd47 to "Cortex-A710", 0xd48 to "Cortex-X2", 0xd4d to "Cortex-A715", 0xd4e to "Cortex-X3",
            0xd80 to "Cortex-A520", 0xd81 to "Cortex-A720", 0xd82 to "Cortex-X4", 0xd85 to "Cortex-X925",
            0xd87 to "Cortex-A725",
        ),
        // Qualcomm
        0x51 to mapOf(
            0x800 to "Kryo Gold", 0x801 to "Kryo Silver", 0x802 to "Kryo Gold", 0x803 to "Kryo Silver",
            0x804 to "Kryo Gold", 0x805 to "Kryo Silver", 0x001 to "Oryon",
        ),
        // Samsung
        0x53 to mapOf(0x001 to "Exynos M1", 0x002 to "Exynos M3", 0x003 to "Exynos M4", 0x004 to "Exynos M5"),
    )

    fun name(id: CoreId?): String? = id?.let { names[it.implementer]?.get(it.part) }
}

object CpuClusters {
    /**
     * Groups cores that share a core type and top speed, fastest first: "2 × Cortex-X3 up to
     * 3.2 GHz". Cores without data form one group.
     */
    fun group(ids: Map<Int, CoreId>, maxKhz: Map<Int, Long?>, coreCount: Int): List<CpuCluster> {
        if (coreCount <= 0) return emptyList()
        return (0 until coreCount)
            .groupBy { ArmCores.name(ids[it]) to maxKhz[it] }
            .map { (key, cores) -> CpuCluster(key.first, cores.size, key.second) }
            .sortedWith(compareByDescending<CpuCluster> { it.maxKhz ?: 0L }.thenByDescending { it.cores })
    }

    /** How hard the CPU is clocked: the average of current / maximum speed over online cores. */
    fun clockLoad(cores: List<CpuCoreStatus>): Float? {
        val loads = cores.filter { it.online }.mapNotNull { core ->
            val current = core.currentKhz ?: return@mapNotNull null
            val max = core.maxKhz?.takeIf { it > 0 } ?: return@mapNotNull null
            (current.toFloat() / max).coerceIn(0f, 1f)
        }
        return if (loads.isEmpty()) null else loads.average().toFloat()
    }
}

/** Human-readable values for the System info page. */
object SystemFormat {

    /** Bytes with 1000-based units, as Android's storage settings show them ("118 GB"). */
    fun bytes(value: Long): String = scaled(value, 1000.0)

    /** Bytes with 1024-based units, the way RAM is usually counted ("7.6 GB"). */
    fun memory(value: Long): String = scaled(value, 1024.0)

    private fun scaled(value: Long, step: Double): String {
        if (value < step) return "$value B"
        val units = listOf("KB", "MB", "GB", "TB")
        var size = value.toDouble()
        var unit = -1
        while (size >= step && unit < units.lastIndex) {
            size /= step
            unit++
        }
        val text = if (size >= 100 || unit == 0) size.roundToInt().toString() else String.format(Locale.US, "%.1f", size).removeSuffix(".0")
        return "$text ${units[unit]}"
    }

    fun frequency(khz: Long): String = when {
        khz >= 1_000_000 -> String.format(Locale.US, "%.2f GHz", khz / 1_000_000.0)
        else -> "${(khz / 1000.0).roundToInt()} MHz"
    }

    fun duration(millis: Long): String {
        val minutes = (millis / 60_000).coerceAtLeast(0)
        val days = minutes / (24 * 60)
        val hours = (minutes / 60) % 24
        val mins = minutes % 60
        return when {
            days > 0 -> "$days d $hours h"
            hours > 0 -> "$hours h $mins min"
            else -> "$mins min"
        }
    }

    /** Android's FEATURE_VULKAN_HARDWARE_VERSION value ("1.3.0"). */
    fun vulkanVersion(packed: Int): String = "${packed ushr 22}.${(packed shr 12) and 0x3ff}.${packed and 0xfff}"

    fun percent(fraction: Float): String = "${(fraction * 100).roundToInt()}%"

    /**
     * The RAM size a phone is sold with ("12 GB"). Android reports a little less than what is
     * fitted (the kernel and firmware keep some), so this rounds up to the next common size.
     */
    fun installedRam(totalBytes: Long): String {
        val gib = totalBytes / (1024.0 * 1024 * 1024)
        val size = RAM_SIZES.firstOrNull { it >= gib - 0.05 } ?: kotlin.math.ceil(gib)
        return (if (size % 1.0 == 0.0) size.toInt().toString() else size.toString()) + " GB"
    }

    private val RAM_SIZES = listOf(1.0, 1.5, 2.0, 3.0, 4.0, 6.0, 8.0, 10.0, 12.0, 16.0, 18.0, 20.0, 24.0, 32.0)
}
