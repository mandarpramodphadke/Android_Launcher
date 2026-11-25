package com.ailauncher.domain.model

/**
 * What the System info page shows. Everything is read on the phone through public Android APIs
 * that need no permission; nothing is stored or sent anywhere. Values a phone doesn't expose are
 * null and simply not shown.
 */
data class SystemDetails(
    val device: DeviceDetails,
    val cpu: CpuDetails,
    val gpu: GpuDetails,
    val display: DisplayDetails?,
    val cameras: List<CameraDetails>,
    val sensors: List<SensorDetails>,
    /** Notable hardware features ("NFC", "Fingerprint", …). */
    val features: List<String>,
)

data class DeviceDetails(
    val brand: String,
    val manufacturer: String,
    val model: String,
    val device: String,
    val board: String,
    val hardware: String,
    val socManufacturer: String?,
    val socModel: String?,
    val androidVersion: String,
    val sdkInt: Int,
    val securityPatch: String?,
    val buildNumber: String,
    val kernel: String?,
    val bootloader: String?,
    val abis: List<String>,
    val javaVm: String?,
    val mediaPerformanceClass: Int?,
)

/** One group of identical cores, e.g. 4 × Cortex-A510 up to 2.0 GHz. */
data class CpuCluster(val coreName: String?, val cores: Int, val maxKhz: Long?)

data class CpuDetails(
    /** SoC or processor name ("Tensor G3", "Qualcomm Technologies, Inc SM8550"), if known. */
    val name: String?,
    val cores: Int,
    val clusters: List<CpuCluster>,
    val architecture: String?,
    val features: List<String>,
    val governor: String?,
)

data class GpuDetails(
    val renderer: String?,
    val vendor: String?,
    val openGlVersion: String?,
    val vulkanVersion: String?,
    val extensions: Int?,
)

data class DisplayDetails(
    val widthPx: Int,
    val heightPx: Int,
    val densityDpi: Int,
    val diagonalInches: Float?,
    val refreshRateHz: Float,
    val maxRefreshRateHz: Float,
    val hdr: Boolean,
    val wideColorGamut: Boolean,
)

data class CameraDetails(val facing: String, val megapixels: Float?, val focalLengthsMm: List<Float>, val hasFlash: Boolean)

data class SensorDetails(val name: String, val vendor: String, val type: String)

/** Values that change while the page is open, read about once a second. */
data class LiveSystemStatus(
    val battery: BatteryStatus?,
    val memory: MemoryStatus,
    val storage: List<StorageStatus>,
    val cpuCores: List<CpuCoreStatus>,
    val gpuBusyPercent: Int?,
    val gpuClockMhz: Int?,
    val thermal: ThermalStatus?,
    val network: NetworkStatus?,
    val uptimeMillis: Long,
)

data class BatteryStatus(
    val levelPercent: Int?,
    /** "Charging", "Discharging", "Full", "Not charging". */
    val state: String,
    /** "USB", "AC charger", "Wireless", "Dock", or null when unplugged. */
    val plugged: String?,
    val health: String?,
    val temperatureC: Float?,
    val voltageMv: Int?,
    val technology: String?,
    /** Positive while charging on most phones; the sign convention varies by maker. */
    val currentMilliAmps: Int?,
    val chargeCounterMah: Int?,
    val cycleCount: Int?,
    val chargeTimeRemainingMillis: Long?,
) {
    /** Full capacity estimated from the charge counter and the level (mAh). */
    val estimatedCapacityMah: Int?
        get() {
            val counter = chargeCounterMah ?: return null
            val level = levelPercent?.takeIf { it in 5..100 } ?: return null
            return (counter * 100L / level).toInt().takeIf { it in 500..30_000 }
        }
}

data class MemoryStatus(
    val totalBytes: Long,
    val availableBytes: Long,
    val lowMemoryThresholdBytes: Long,
    val lowMemory: Boolean,
    val swapTotalBytes: Long?,
    val swapFreeBytes: Long?,
    val appHeapLimitBytes: Long,
) {
    val usedBytes: Long get() = (totalBytes - availableBytes).coerceAtLeast(0)
    val usedFraction: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
}

data class StorageStatus(
    val name: String,
    val totalBytes: Long,
    val freeBytes: Long,
    val removable: Boolean,
    /** The advertised size (e.g. 128 GB) when Android reports it. */
    val capacityBytes: Long? = null,
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0)
    val usedFraction: Float get() = if (totalBytes > 0) usedBytes.toFloat() / totalBytes else 0f
}

data class CpuCoreStatus(val index: Int, val online: Boolean, val currentKhz: Long?, val minKhz: Long?, val maxKhz: Long?)

data class ThermalStatus(
    /** "None", "Light", "Moderate", "Severe", "Critical", "Emergency", "Shutdown". */
    val status: String,
    /** 0 = cool, 1 = about to throttle (Android 11+). */
    val headroom: Float?,
    val powerSaver: Boolean,
)

data class NetworkStatus(
    /** "Wi-Fi", "Mobile data", "Ethernet", "VPN", … or "Offline". */
    val type: String,
    val metered: Boolean,
    val validated: Boolean,
    val downstreamKbps: Int?,
    val upstreamKbps: Int?,
)
