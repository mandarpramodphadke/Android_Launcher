package com.ailauncher.domain.system

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
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SystemInfoTest {

    /** Trimmed /proc/cpuinfo of an 8-core phone: 4 × A510, 3 × A715, 1 × X3. */
    private val cpuinfo = buildString {
        (0..7).forEach { i ->
            val part = when (i) {
                in 0..3 -> "0xd46"
                in 4..6 -> "0xd4d"
                else -> "0xd4e"
            }
            append("processor\t: $i\nBogoMIPS\t: 38.40\n")
            append("Features\t: fp asimd evtstrm aes pmull sha1 sha2 crc32 atomics fphp asimdhp cpuid asimdrdm lrcpc dcpop asimddp\n")
            append("CPU implementer\t: 0x41\nCPU architecture: 8\nCPU variant\t: 0x1\nCPU part\t: $part\nCPU revision\t: 0\n\n")
        }
        append("Hardware\t: Qualcomm Technologies, Inc KALAMA\n")
    }

    @Test
    fun parsesCpuinfoCoresFeaturesAndHardware() {
        val parsed = CpuInfoParser.parse(cpuinfo)
        assertThat(parsed.hardware).isEqualTo("Qualcomm Technologies, Inc KALAMA")
        assertThat(parsed.features).contains("asimddp")
        assertThat(parsed.cores).hasSize(8)
        assertThat(ArmCores.name(parsed.cores[0])).isEqualTo("Cortex-A510")
        assertThat(ArmCores.name(parsed.cores[7])).isEqualTo("Cortex-X3")
        assertThat(ArmCores.name(CoreId(0x99, 0x1))).isNull()

        val maxKhz = (0..7).associateWith { i -> if (i < 4) 2_016_000L else if (i < 7) 2_803_200L else 3_187_200L }
        val clusters = CpuClusters.group(parsed.cores, maxKhz, 8)
        assertThat(clusters).containsExactly(
            CpuCluster("Cortex-X3", 1, 3_187_200),
            CpuCluster("Cortex-A715", 3, 2_803_200),
            CpuCluster("Cortex-A510", 4, 2_016_000),
        ).inOrder()
    }

    @Test
    fun parsesRangesAndMeminfo() {
        assertThat(CpuInfoParser.parseRange("0-7")).isEqualTo((0..7).toList())
        assertThat(CpuInfoParser.parseRange("0-3,6\n")).containsExactly(0, 1, 2, 3, 6).inOrder()
        assertThat(CpuInfoParser.parseRange(null)).isEmpty()
        val mem = CpuInfoParser.parseMemInfo("MemTotal:        7841232 kB\nSwapTotal:       4194300 kB\nSwapFree:        1024 kB\nbad line\n")
        assertThat(mem["MemTotal"]).isEqualTo(7_841_232L * 1024)
        assertThat(mem["SwapFree"]).isEqualTo(1024L * 1024)
    }

    @Test
    fun clockLoadAveragesOnlineCoresWithData() {
        val cores = listOf(
            CpuCoreStatus(0, true, 500_000, 300_000, 2_000_000),
            CpuCoreStatus(1, true, 2_000_000, 300_000, 2_000_000),
            CpuCoreStatus(2, false, null, null, 3_000_000),
            CpuCoreStatus(3, true, null, null, null),
        )
        assertThat(CpuClusters.clockLoad(cores)).isWithin(1e-4f).of(0.625f)
        assertThat(CpuClusters.clockLoad(listOf(CpuCoreStatus(0, true, null, null, null)))).isNull()
    }

    @Test
    fun formatsSizesSpeedsAndVersions() {
        assertThat(SystemFormat.bytes(128_000_000_000)).isEqualTo("128 GB")
        assertThat(SystemFormat.bytes(52_400_000_000)).isEqualTo("52.4 GB")
        assertThat(SystemFormat.memory(8L * 1024 * 1024 * 1024)).isEqualTo("8 GB")
        assertThat(SystemFormat.memory(7_841_232L * 1024)).isEqualTo("7.5 GB")
        assertThat(SystemFormat.bytes(512)).isEqualTo("512 B")
        assertThat(SystemFormat.frequency(3_187_200)).isEqualTo("3.19 GHz")
        assertThat(SystemFormat.frequency(576_000)).isEqualTo("576 MHz")
        assertThat(SystemFormat.duration(((2 * 24 + 5) * 60 + 7) * 60_000L)).isEqualTo("2 d 5 h")
        assertThat(SystemFormat.duration(65 * 60_000L)).isEqualTo("1 h 5 min")
        assertThat(SystemFormat.vulkanVersion((1 shl 22) or (3 shl 12) or 239)).isEqualTo("1.3.239")
        // Android reports less RAM than is fitted; the page names the size the phone was sold with.
        assertThat(SystemFormat.installedRam(11_800_000_000)).isEqualTo("12 GB")
        assertThat(SystemFormat.installedRam(7_700_000_000)).isEqualTo("8 GB")
        assertThat(SystemFormat.installedRam(5_600_000_000)).isEqualTo("6 GB")
        assertThat(SystemFormat.installedRam(2_900_000_000)).isEqualTo("3 GB")
    }

    @Test
    fun buildsThePagesSections() {
        val details = SystemDetails(
            device = DeviceDetails(
                brand = "Google", manufacturer = "Google", model = "Pixel 8", device = "shiba", board = "shiba", hardware = "shiba",
                socManufacturer = "Google", socModel = "Tensor G3", androidVersion = "16", sdkInt = 36, securityPatch = "2026-11-05",
                buildNumber = "BP2A.250605", kernel = "6.1.99", bootloader = null, abis = listOf("arm64-v8a"), javaVm = "ART 2.1.0",
                mediaPerformanceClass = 34,
            ),
            cpu = CpuDetails("Google Tensor G3", 9, listOf(CpuCluster("Cortex-X3", 1, 2_910_000), CpuCluster(null, 8, null)), "aarch64", listOf("fp", "asimd"), "sched_pixel"),
            gpu = GpuDetails("Mali-G715", "ARM", "OpenGL ES 3.2 v1.r44p1", "1.3.0", 120),
            display = DisplayDetails(1080, 2400, 420, 6.2f, 60f, 120f, hdr = true, wideColorGamut = true),
            cameras = listOf(CameraDetails("Rear", 50.3f, listOf(6.9f), true), CameraDetails("Rear", 12.0f, listOf(2.2f), true), CameraDetails("Front", 10.5f, emptyList(), false)),
            sensors = (1..9).map { SensorDetails("Sensor $it", "Vendor", "Accelerometer") },
            features = listOf("NFC", "eSIM"),
        )
        val live = LiveSystemStatus(
            battery = BatteryStatus(80, "Charging", "USB", "Good", 31.4f, 4210, "Li-ion", 1200, 3_600, 210, 3_900_000),
            memory = MemoryStatus(8L shl 30, 3L shl 30, 256L shl 20, false, 4L shl 30, 3L shl 30, 512L shl 20),
            storage = listOf(StorageStatus("Internal storage", 110_000_000_000, 40_000_000_000, removable = false, capacityBytes = 128_000_000_000)),
            cpuCores = listOf(CpuCoreStatus(0, true, 1_000_000, 300_000, 2_000_000)),
            gpuBusyPercent = null,
            gpuClockMhz = null,
            thermal = ThermalStatus("Normal", 0.2f, powerSaver = false),
            network = NetworkStatus("Wi-Fi", metered = false, validated = true, downstreamKbps = 120_000, upstreamKbps = 900),
            uptimeMillis = 3 * 3_600_000L,
        )
        val sections = SystemInfoSections.build(details, live).associateBy { it.key }
        fun value(key: SectionKey, label: String) = sections.getValue(key).rows.first { it.first == label }.second

        assertThat(value(SectionKey.BATTERY, "Status")).isEqualTo("Charging · USB")
        assertThat(value(SectionKey.BATTERY, "Capacity (estimated)")).isEqualTo("≈4,500 mAh")
        assertThat(value(SectionKey.BATTERY, "Current")).isEqualTo("+1200 mA")
        assertThat(value(SectionKey.CPU, "Cores")).isEqualTo("9 (1 + 8)")
        assertThat(value(SectionKey.CPU, "1 × Cortex-X3")).isEqualTo("up to 2.91 GHz")
        assertThat(value(SectionKey.GPU, "Vulkan")).isEqualTo("1.3.0")
        assertThat(value(SectionKey.MEMORY, "Used")).isEqualTo("5 GB (63%)")
        assertThat(value(SectionKey.MEMORY, "Swap (zRAM)")).isEqualTo("1 GB of 4 GB")
        assertThat(value(SectionKey.MEMORY, "Installed")).isEqualTo("8 GB")
        assertThat(value(SectionKey.STORAGE, "Capacity")).isEqualTo("128 GB")
        assertThat(value(SectionKey.STORAGE, "Used")).isEqualTo("70 GB (64%)")
        assertThat(value(SectionKey.STORAGE, "Free")).isEqualTo("40 GB")
        assertThat(value(SectionKey.DISPLAY, "Refresh rate")).isEqualTo("60 Hz (up to 120 Hz)")
        assertThat(value(SectionKey.SOFTWARE, "Android")).isEqualTo("16 (API 36)")
        assertThat(value(SectionKey.NETWORK, "Speed estimate")).isEqualTo("↓ 120 Mbps  ↑ 900 kbps")
        assertThat(value(SectionKey.THERMAL, "Before throttling")).isEqualTo("80% headroom")
        assertThat(sections.getValue(SectionKey.CAMERAS).rows.map { it.first }).containsExactly("Rear camera 1", "Rear camera 2", "Front camera").inOrder()
        assertThat(value(SectionKey.CAMERAS, "Rear camera 1")).isEqualTo("50.3 MP · 6.9 mm · flash")
        assertThat(sections.getValue(SectionKey.SENSORS).title).isEqualTo("Sensors (9)")
        assertThat(sections.getValue(SectionKey.DEVICE).rows.map { it.first }).doesNotContain("Manufacturer")

        val text = SystemInfoSections.asText(SystemInfoSections.build(details, live))
        assertThat(text).startsWith("Battery\nLevel: 80%\n")
        assertThat(text).contains("\n\nProcessor\nChip: Google Tensor G3\n")
    }

    @Test
    fun missingValuesLeaveSectionsOut() {
        assertThat(SystemInfoSections.build(null, null)).isEmpty()
        val battery = BatteryStatus(null, "Unknown", null, null, null, null, null, null, null, null, null)
        assertThat(battery.estimatedCapacityMah).isNull()
        assertThat(battery.copy(levelPercent = 50, chargeCounterMah = 10).estimatedCapacityMah).isNull() // nonsense value
    }
}
