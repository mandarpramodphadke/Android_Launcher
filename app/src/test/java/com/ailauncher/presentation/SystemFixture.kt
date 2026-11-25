package com.ailauncher.presentation

import com.ailauncher.domain.model.BatteryStatus
import com.ailauncher.domain.model.CameraDetails
import com.ailauncher.domain.model.CpuCluster
import com.ailauncher.domain.model.CpuCoreStatus
import com.ailauncher.domain.model.CpuDetails
import com.ailauncher.domain.model.DeviceDetails
import com.ailauncher.domain.model.DisplayDetails
import com.ailauncher.domain.model.GpuDetails
import com.ailauncher.domain.model.LiveSystemStatus
import com.ailauncher.domain.model.MemoryStatus
import com.ailauncher.domain.model.NetworkStatus
import com.ailauncher.domain.model.SensorDetails
import com.ailauncher.domain.model.StorageStatus
import com.ailauncher.domain.model.SystemDetails
import com.ailauncher.domain.model.ThermalStatus
import kotlin.math.sin

/** A made-up 8-core phone for the System info screenshots. */
object SystemFixture {
    val details = SystemDetails(
        device = DeviceDetails(
            brand = "Example", manufacturer = "Example", model = "Phone 8 Pro", device = "falcon", board = "kalama", hardware = "qcom",
            socManufacturer = "Qualcomm", socModel = "SM8550", androidVersion = "16", sdkInt = 36, securityPatch = "2026-11-05",
            buildNumber = "BP2A.250605.031", kernel = "6.1.99-android14-11", bootloader = "falcon-1.2-1234", abis = listOf("arm64-v8a", "armeabi-v7a"),
            javaVm = "ART 2.1.0", mediaPerformanceClass = 34,
        ),
        cpu = CpuDetails(
            name = "Qualcomm SM8550",
            cores = 8,
            clusters = listOf(CpuCluster("Cortex-X3", 1, 3_187_200), CpuCluster("Cortex-A715", 3, 2_803_200), CpuCluster("Cortex-A510", 4, 2_016_000)),
            architecture = "aarch64",
            features = "fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp cpuid asimdrdm lrcpc dcpop asimddp".split(' '),
            governor = "walt",
        ),
        gpu = GpuDetails("Adreno (TM) 740", "Qualcomm", "OpenGL ES 3.2 V@0746.0", "1.3.128", 128),
        display = DisplayDetails(1440, 3120, 510, 6.8f, 120f, 120f, hdr = true, wideColorGamut = true),
        cameras = listOf(
            CameraDetails("Rear", 50.3f, listOf(6.3f), true),
            CameraDetails("Rear", 12.2f, listOf(2.2f), true),
            CameraDetails("Rear", 10.0f, listOf(19.0f), true),
            CameraDetails("Front", 12.0f, listOf(2.7f), false),
        ),
        sensors = listOf("Accelerometer", "Gyroscope", "Magnetic field", "Light", "Proximity", "Pressure", "Step counter", "Rotation vector", "Gravity", "Hinge angle")
            .map { SensorDetails("$it sensor", "Example", it) },
        features = listOf("Mobile network", "eSIM", "NFC", "Bluetooth LE", "Ultra-wideband", "GPS", "Fingerprint", "Face unlock", "USB OTG"),
    )

    val live = LiveSystemStatus(
        battery = BatteryStatus(76, "Charging", "USB", "Good", 32.5f, 4180, "Li-poly", 1450, 3_800, 214, 2_700_000),
        memory = MemoryStatus(11_800_000_000, 4_600_000_000, 270_000_000, false, 6_400_000_000, 4_100_000_000, 536_870_912),
        storage = listOf(StorageStatus("Internal storage", 238_000_000_000, 141_000_000_000, removable = false, capacityBytes = 256_000_000_000)),
        cpuCores = listOf(
            CpuCoreStatus(0, true, 1_017_600, 307_200, 2_016_000), CpuCoreStatus(1, true, 307_200, 307_200, 2_016_000),
            CpuCoreStatus(2, true, 1_555_200, 307_200, 2_016_000), CpuCoreStatus(3, true, 307_200, 307_200, 2_016_000),
            CpuCoreStatus(4, true, 2_265_600, 499_200, 2_803_200), CpuCoreStatus(5, true, 1_190_400, 499_200, 2_803_200),
            CpuCoreStatus(6, false, null, 499_200, 2_803_200), CpuCoreStatus(7, true, 2_976_000, 595_200, 3_187_200),
        ),
        gpuBusyPercent = 23,
        gpuClockMhz = 342,
        thermal = ThermalStatus("Normal", 0.31f, powerSaver = false),
        network = NetworkStatus("Wi-Fi", metered = false, validated = true, downstreamKbps = 240_000, upstreamKbps = 48_000),
        uptimeMillis = (2 * 24 + 7) * 3_600_000L,
    )

    val history: List<Float> = (0 until 60).map { 0.35f + 0.25f * sin(it / 6f) + if (it % 13 == 0) 0.2f else 0f }
}
