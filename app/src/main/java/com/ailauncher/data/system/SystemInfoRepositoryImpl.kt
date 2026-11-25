package com.ailauncher.data.system

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.FeatureInfo
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import android.os.storage.StorageManager
import android.view.Display
import androidx.core.content.ContextCompat
import com.ailauncher.domain.model.BatteryStatus
import com.ailauncher.domain.model.CameraDetails
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
import com.ailauncher.domain.repository.SystemInfoRepository
import com.ailauncher.domain.system.CpuClusters
import com.ailauncher.domain.system.CpuInfoParser
import com.ailauncher.domain.system.SystemFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Reads the System info page's values with public APIs and world-readable /proc and /sys files.
 * Nothing needs a permission, and nothing that identifies the phone (serial number, IMEI, Android
 * ID, MAC address) is read. Values a phone doesn't expose come back as null.
 */
@Singleton
class SystemInfoRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : SystemInfoRepository {

    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val batteryManager = context.getSystemService(BatteryManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private val storageManager = context.getSystemService(StorageManager::class.java)

    private val detailsLock = Mutex()
    private var cachedDetails: SystemDetails? = null

    /** The advertised internal storage size; read once (a binder call). */
    private val internalCapacity: Long? by lazy {
        runCatching { context.getSystemService(StorageStatsManager::class.java)?.getTotalBytes(StorageManager.UUID_DEFAULT) }.getOrNull()
    }

    override suspend fun details(): SystemDetails = detailsLock.withLock {
        cachedDetails ?: withContext(Dispatchers.IO) {
            // Each part on its own: a maker's broken API costs one value, never the page.
            SystemDetails(
                device = device(),
                cpu = runCatching { cpu() }.getOrElse { CpuDetails(null, Runtime.getRuntime().availableProcessors(), emptyList(), null, emptyList(), null) },
                gpu = runCatching { gpu() }.getOrElse { GpuDetails(null, null, null, null, null) },
                display = runCatching { display() }.getOrNull(),
                cameras = runCatching { cameras() }.getOrDefault(emptyList()),
                sensors = runCatching { sensors() }.getOrDefault(emptyList()),
                features = runCatching { features() }.getOrDefault(emptyList()),
            )
        }.also { cachedDetails = it }
    }

    override fun live(intervalMillis: Long): Flow<LiveSystemStatus> = flow {
        while (true) {
            emit(readLive())
            delay(intervalMillis)
        }
    }.flowOn(Dispatchers.IO)

    private fun readLive(): LiveSystemStatus {
        val (gpuBusy, gpuClock) = gpuLoad()
        return LiveSystemStatus(
            battery = runCatching { battery() }.getOrNull(),
            memory = runCatching { memory() }.getOrElse { MemoryStatus(0, 0, 0, false, null, null, Runtime.getRuntime().maxMemory()) },
            storage = runCatching { storage() }.getOrDefault(emptyList()),
            cpuCores = runCatching { cpuCores() }.getOrDefault(emptyList()),
            gpuBusyPercent = gpuBusy,
            gpuClockMhz = gpuClock,
            thermal = runCatching { thermal() }.getOrNull(),
            network = runCatching { network() }.getOrNull(),
            uptimeMillis = SystemClock.elapsedRealtime(),
        )
    }

    // ------------------------------------------------------------------------------ device

    private fun device(): DeviceDetails = DeviceDetails(
        brand = Build.BRAND.orEmpty().replaceFirstChar { it.titlecase(Locale.getDefault()) },
        manufacturer = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.titlecase(Locale.getDefault()) },
        model = Build.MODEL.orEmpty(),
        device = Build.DEVICE.orEmpty(),
        board = Build.BOARD.orEmpty(),
        hardware = Build.HARDWARE.orEmpty(),
        socManufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MANUFACTURER.known() else null,
        socModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL.known() else null,
        androidVersion = Build.VERSION.RELEASE.orEmpty(),
        sdkInt = Build.VERSION.SDK_INT,
        securityPatch = Build.VERSION.SECURITY_PATCH.known(),
        buildNumber = Build.DISPLAY.orEmpty(),
        kernel = System.getProperty("os.version").known(),
        bootloader = Build.BOOTLOADER.known(),
        abis = Build.SUPPORTED_ABIS.toList(),
        javaVm = System.getProperty("java.vm.version")?.let { "ART $it" },
        mediaPerformanceClass = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.VERSION.MEDIA_PERFORMANCE_CLASS.takeIf { it > 0 } else null,
    )

    private fun String?.known(): String? = this?.trim()?.takeIf { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }

    // --------------------------------------------------------------------------------- CPU

    private fun cpu(): CpuDetails {
        val parsed = CpuInfoParser.parse(readText("/proc/cpuinfo").orEmpty())
        val indices = coreIndices()
        val maxKhz = indices.associateWith { readLong("$CPU_DIR/cpu$it/cpufreq/cpuinfo_max_freq") }
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOfNotNull(Build.SOC_MANUFACTURER.known(), Build.SOC_MODEL.known()).joinToString(" ").takeIf { it.isNotEmpty() }
        } else {
            null
        }
        return CpuDetails(
            name = soc ?: parsed.hardware ?: parsed.modelName ?: Build.HARDWARE.known(),
            cores = indices.size,
            clusters = CpuClusters.group(parsed.cores, maxKhz, indices.size),
            architecture = System.getProperty("os.arch").known(),
            features = parsed.features,
            governor = readText("$CPU_DIR/cpu0/cpufreq/scaling_governor")?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    private fun coreIndices(): List<Int> =
        CpuInfoParser.parseRange(readText("$CPU_DIR/possible")).ifEmpty { (0 until Runtime.getRuntime().availableProcessors()).toList() }

    private fun cpuCores(): List<CpuCoreStatus> {
        val online = CpuInfoParser.parseRange(readText("$CPU_DIR/online")).toSet()
        return coreIndices().map { index ->
            val dir = "$CPU_DIR/cpu$index/cpufreq"
            CpuCoreStatus(
                index = index,
                online = online.isEmpty() || index in online,
                currentKhz = readLong("$dir/scaling_cur_freq"),
                minKhz = readLong("$dir/cpuinfo_min_freq"),
                maxKhz = readLong("$dir/cpuinfo_max_freq"),
            )
        }
    }

    // --------------------------------------------------------------------------------- GPU

    private fun gpu(): GpuDetails {
        val gl = runCatching { readGl() }.getOrNull()
        // The platform may return null here (seen under test), so it is read as nullable.
        val available: Array<FeatureInfo>? = context.packageManager.systemAvailableFeatures
        val vulkan = available.orEmpty()
            .firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }
            ?.version?.takeIf { it > 0 }?.let(SystemFormat::vulkanVersion)
        val glEs = activityManager?.deviceConfigurationInfo?.glEsVersion
        return GpuDetails(
            renderer = gl?.renderer,
            vendor = gl?.vendor,
            openGlVersion = gl?.version ?: glEs?.let { "OpenGL ES $it" },
            vulkanVersion = vulkan,
            extensions = gl?.extensions,
        )
    }

    private class GlStrings(val renderer: String?, val vendor: String?, val version: String?, val extensions: Int?)

    /**
     * Asks OpenGL ES for the GPU's name with a 1×1 offscreen context on this thread, then releases
     * it. The display is left initialised: the app's own rendering shares it.
     */
    private fun readGl(): GlStrings? {
        val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) return null
        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null
        val configs = arrayOfNulls<EGLConfig>(1)
        val count = IntArray(1)
        val attributes = intArrayOf(
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) || count[0] == 0) return null
        val config = configs[0] ?: return null
        val eglContext = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0)
        if (eglContext == EGL14.EGL_NO_CONTEXT) return null
        val surface = EGL14.eglCreatePbufferSurface(display, config, intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0)
        try {
            if (surface == EGL14.EGL_NO_SURFACE || !EGL14.eglMakeCurrent(display, surface, surface, eglContext)) return null
            return GlStrings(
                renderer = GLES20.glGetString(GLES20.GL_RENDERER).known(),
                vendor = GLES20.glGetString(GLES20.GL_VENDOR).known(),
                version = GLES20.glGetString(GLES20.GL_VERSION).known(),
                extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS)?.split(' ')?.count { it.isNotBlank() },
            )
        } finally {
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            if (surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, eglContext)
        }
    }

    /** Adreno phones expose GPU load and clock; most others keep them private. */
    private fun gpuLoad(): Pair<Int?, Int?> {
        val busy = readText("$KGSL/gpu_busy_percentage")?.trim()?.removeSuffix("%")?.trim()?.toIntOrNull()?.takeIf { it in 0..100 }
        val clockHz = readLong("$KGSL/gpuclk") ?: readLong("$KGSL/devfreq/cur_freq")
        return busy to clockHz?.let { (it / 1_000_000).toInt() }?.takeIf { it > 0 }
    }

    // ----------------------------------------------------------------------------- battery

    private fun battery(): BatteryStatus? {
        val intent = ContextCompat.registerReceiver(context, null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
            ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val state = when (intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
            else -> "Unknown"
        }
        val plugged = when (intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC charger"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            PLUGGED_DOCK -> "Dock"
            else -> null
        }
        val health = when (intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheating"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            BatteryManager.BATTERY_HEALTH_COLD -> "Cold"
            else -> null
        }
        val cycles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            intent.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1).takeIf { it >= 0 }
        } else {
            null
        }
        // Units vary by maker: most report µA / µAh as documented, some report mA / mAh.
        val current = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
            ?.takeIf { it != Int.MIN_VALUE && it != 0 }
            ?.let { if (abs(it) > 20_000) it / 1000 else it }
        val counter = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
            ?.takeIf { it != Int.MIN_VALUE && it > 0 }
            ?.let { if (it > 100_000) it / 1000 else it }
        return BatteryStatus(
            levelPercent = if (level >= 0 && scale > 0) level * 100 / scale else null,
            state = state,
            plugged = plugged,
            health = health,
            temperatureC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }?.let { it / 10f },
            voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1).takeIf { it > 0 },
            technology = intent.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY).known(),
            currentMilliAmps = current,
            chargeCounterMah = counter,
            cycleCount = cycles,
            chargeTimeRemainingMillis = batteryManager?.computeChargeTimeRemaining()?.takeIf { it > 0 },
        )
    }

    // ----------------------------------------------------------------------- memory, storage

    private fun memory(): MemoryStatus {
        val info = ActivityManager.MemoryInfo().also { activityManager?.getMemoryInfo(it) }
        val meminfo = CpuInfoParser.parseMemInfo(readText("/proc/meminfo").orEmpty())
        return MemoryStatus(
            totalBytes = info.totalMem,
            availableBytes = info.availMem,
            lowMemoryThresholdBytes = info.threshold,
            lowMemory = info.lowMemory,
            swapTotalBytes = meminfo["SwapTotal"]?.takeIf { it > 0 },
            swapFreeBytes = meminfo["SwapFree"],
            appHeapLimitBytes = Runtime.getRuntime().maxMemory(),
        )
    }

    private fun storage(): List<StorageStatus> {
        val internal = runCatching {
            val stat = StatFs(Environment.getDataDirectory().path)
            StorageStatus("Internal storage", stat.totalBytes, stat.availableBytes, removable = false, capacityBytes = internalCapacity)
        }.getOrNull()
        val removable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            storageManager?.storageVolumes.orEmpty()
                .filter { it.isRemovable && it.state == Environment.MEDIA_MOUNTED }
                .mapNotNull { volume ->
                    runCatching {
                        val dir = volume.directory ?: return@runCatching null
                        val stat = StatFs(dir.path)
                        StorageStatus(volume.getDescription(context), stat.totalBytes, stat.availableBytes, removable = true)
                    }.getOrNull()
                }
        } else {
            emptyList()
        }
        return listOfNotNull(internal) + removable
    }

    // ------------------------------------------------------------------- thermal, network

    private fun thermal(): ThermalStatus? {
        val manager = powerManager ?: return null
        val status = when (manager.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "Normal"
            PowerManager.THERMAL_STATUS_LIGHT -> "Warm"
            PowerManager.THERMAL_STATUS_MODERATE -> "Hot (moderate)"
            PowerManager.THERMAL_STATUS_SEVERE -> "Hot (severe)"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
            PowerManager.THERMAL_STATUS_EMERGENCY -> "Emergency"
            PowerManager.THERMAL_STATUS_SHUTDOWN -> "Shutting down"
            else -> "Unknown"
        }
        val headroom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.getThermalHeadroom(10).takeIf { !it.isNaN() && it >= 0f }
        } else {
            null
        }
        return ThermalStatus(status, headroom, manager.isPowerSaveMode)
    }

    private fun network(): NetworkStatus {
        val manager = connectivity ?: return NetworkStatus("Unknown", metered = false, validated = false, downstreamKbps = null, upstreamKbps = null)
        val caps = manager.activeNetwork?.let { manager.getNetworkCapabilities(it) }
            ?: return NetworkStatus("Offline", metered = false, validated = false, downstreamKbps = null, upstreamKbps = null)
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile data"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "Bluetooth tethering"
            else -> "Other"
        }
        return NetworkStatus(
            type = type,
            metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            downstreamKbps = caps.linkDownstreamBandwidthKbps.takeIf { it > 0 },
            upstreamKbps = caps.linkUpstreamBandwidthKbps.takeIf { it > 0 },
        )
    }

    // -------------------------------------------------------- display, cameras, sensors, features

    private fun display(): DisplayDetails? {
        val display = context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY) ?: return null
        val mode = display.mode
        val width = mode.physicalWidth
        val height = mode.physicalHeight
        val metrics = context.resources.displayMetrics
        val diagonal = if (metrics.xdpi > 0 && metrics.ydpi > 0) {
            val w = width / metrics.xdpi
            val h = height / metrics.ydpi
            sqrt(w * w + h * h).takeIf { it in 2f..20f }
        } else {
            null
        }
        return DisplayDetails(
            widthPx = minOf(width, height),
            heightPx = maxOf(width, height),
            densityDpi = metrics.densityDpi,
            diagonalInches = diagonal,
            refreshRateHz = display.refreshRate,
            maxRefreshRateHz = display.supportedModes.maxOfOrNull { it.refreshRate } ?: display.refreshRate,
            hdr = display.isHdr,
            wideColorGamut = display.isWideColorGamut,
        )
    }

    private fun cameras(): List<CameraDetails> {
        val manager = context.getSystemService(CameraManager::class.java) ?: return emptyList()
        return manager.cameraIdList.mapNotNull { id ->
            runCatching {
                val c = manager.getCameraCharacteristics(id)
                val facing = when (c.get(CameraCharacteristics.LENS_FACING)) {
                    CameraCharacteristics.LENS_FACING_FRONT -> "Front"
                    CameraCharacteristics.LENS_FACING_BACK -> "Rear"
                    else -> "External"
                }
                val pixels = c.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
                CameraDetails(
                    facing = facing,
                    megapixels = pixels?.let { it.width.toLong() * it.height / 1_000_000f },
                    focalLengthsMm = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.toList().orEmpty(),
                    hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
                )
            }.getOrNull()
        }
    }

    private fun sensors(): List<SensorDetails> {
        val manager = context.getSystemService(SensorManager::class.java) ?: return emptyList()
        return manager.getSensorList(Sensor.TYPE_ALL).map { sensor ->
            val type = sensor.stringType.orEmpty().substringAfterLast('.').replace('_', ' ')
                .replaceFirstChar { it.titlecase(Locale.getDefault()) }
            SensorDetails(sensor.name.orEmpty(), sensor.vendor.orEmpty(), type)
        }.distinctBy { it.name to it.type }
    }

    private fun features(): List<String> {
        val pm = context.packageManager
        val named = buildList {
            add(PackageManager.FEATURE_TELEPHONY to "Mobile network")
            add(PackageManager.FEATURE_TELEPHONY_EUICC to "eSIM")
            add(PackageManager.FEATURE_NFC to "NFC")
            add(PackageManager.FEATURE_BLUETOOTH_LE to "Bluetooth LE")
            add(PackageManager.FEATURE_WIFI_DIRECT to "Wi-Fi Direct")
            add(PackageManager.FEATURE_WIFI_AWARE to "Wi-Fi Aware")
            // PackageManager.FEATURE_UWB; phones with UWB declare it from Android 12 on.
            add(FEATURE_UWB to "Ultra-wideband")
            add(PackageManager.FEATURE_LOCATION_GPS to "GPS")
            add(PackageManager.FEATURE_FINGERPRINT to "Fingerprint")
            add(PackageManager.FEATURE_FACE to "Face unlock")
            add(PackageManager.FEATURE_STRONGBOX_KEYSTORE to "StrongBox security chip")
            add(PackageManager.FEATURE_USB_HOST to "USB OTG")
            add(PackageManager.FEATURE_CONSUMER_IR to "IR blaster")
            add(PackageManager.FEATURE_SENSOR_BAROMETER to "Barometer")
            add(PackageManager.FEATURE_SENSOR_STEP_COUNTER to "Step counter")
            add(PackageManager.FEATURE_CAMERA_FLASH to "Camera flash")
            add(PackageManager.FEATURE_AUDIO_PRO to "Pro audio")
        }
        return named.filter { (feature, _) -> pm.hasSystemFeature(feature) }.map { it.second }
    }

    // ------------------------------------------------------------------------------- files

    private fun readText(path: String): String? = runCatching { File(path).takeIf { it.canRead() }?.readText() }.getOrNull()

    private fun readLong(path: String): Long? = readText(path)?.trim()?.toLongOrNull()?.takeIf { it > 0 }

    private companion object {
        const val CPU_DIR = "/sys/devices/system/cpu"
        const val KGSL = "/sys/class/kgsl/kgsl-3d0"

        /** BatteryManager.BATTERY_PLUGGED_DOCK (API 33). */
        const val PLUGGED_DOCK = 8

        const val FEATURE_UWB = "android.hardware.uwb"
    }
}
