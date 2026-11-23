package com.ailauncher.data.llm

import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import com.ailauncher.domain.model.DeviceHealth
import com.ailauncher.domain.model.ThermalLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** RAM, thermal and CPU-topology probes used to protect the launcher process. */
@Singleton
class DeviceResources @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val powerManager = context.getSystemService(PowerManager::class.java)

    fun memoryInfo(): ActivityManager.MemoryInfo =
        ActivityManager.MemoryInfo().also { activityManager?.getMemoryInfo(it) }

    /** Resident set size of this process (includes mmap-ed model pages that are in RAM). */
    fun processRssBytes(): Long = runCatching {
        File("/proc/self/status").useLines { lines ->
            lines.firstOrNull { it.startsWith("VmRSS:") }
                ?.filter(Char::isDigit)?.toLongOrNull()?.times(1024)
        }
    }.getOrNull() ?: 0L

    fun thermalLevel(): ThermalLevel = when (powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE) {
        PowerManager.THERMAL_STATUS_NONE -> ThermalLevel.NONE
        PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.LIGHT
        PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
        PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
        else -> ThermalLevel.CRITICAL
    }

    fun snapshot(): DeviceHealth {
        val mem = memoryInfo()
        return DeviceHealth(
            processRssBytes = processRssBytes(),
            availableRamBytes = mem.availMem,
            totalRamBytes = mem.totalMem,
            lowMemory = mem.lowMemory,
            thermalStatus = thermalLevel(),
        )
    }

    /**
     * Number of "performance" cores: cores whose max frequency is within 75% of the fastest core.
     * On big.LITTLE SoCs this excludes the efficiency cluster, which only slows llama.cpp down.
     */
    val performanceCoreCount: Int by lazy {
        val cores = Runtime.getRuntime().availableProcessors()
        val frequencies = (0 until cores).mapNotNull { cpu ->
            runCatching {
                File("/sys/devices/system/cpu/cpu$cpu/cpufreq/cpuinfo_max_freq").readText().trim().toLong()
            }.getOrNull()
        }
        if (frequencies.isEmpty()) {
            (cores / 2).coerceIn(1, 4)
        } else {
            val max = frequencies.max()
            frequencies.count { it >= max * 0.75 }.coerceAtLeast(1)
        }
    }

    /**
     * Threads for llama.cpp: the configured value or up to 4 performance cores, throttled when
     * the device is warm or in battery saver.
     */
    fun recommendedThreads(configured: Int): Int {
        val base = if (configured > 0) configured else performanceCoreCount.coerceIn(2, DEFAULT_MAX_THREADS)
        val thermal = when (thermalLevel()) {
            ThermalLevel.NONE, ThermalLevel.LIGHT -> base
            ThermalLevel.MODERATE -> minOf(base, 2)
            ThermalLevel.SEVERE, ThermalLevel.CRITICAL -> 1
        }
        return if (powerManager?.isPowerSaveMode == true) minOf(thermal, 2) else thermal
    }

    private companion object {
        const val DEFAULT_MAX_THREADS = 4
    }
}
