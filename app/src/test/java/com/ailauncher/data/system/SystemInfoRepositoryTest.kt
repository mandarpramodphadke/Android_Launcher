package com.ailauncher.data.system

import android.app.Application
import android.content.Intent
import android.os.BatteryManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ailauncher.domain.system.SystemInfoSections
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Runs the real reader against Robolectric's system services: every call must succeed or fall
 * back to "unknown" (null) instead of crashing the page.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class SystemInfoRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun readsDetailsAndLiveValuesWithoutCrashing() = runTest {
        // A sticky battery broadcast, as the system keeps one (deprecated for apps, fine in a test).
        @Suppress("DEPRECATION")
        context.sendStickyBroadcast(
            Intent(Intent.ACTION_BATTERY_CHANGED)
                .putExtra(BatteryManager.EXTRA_LEVEL, 42)
                .putExtra(BatteryManager.EXTRA_SCALE, 100)
                .putExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_DISCHARGING)
                .putExtra(BatteryManager.EXTRA_TEMPERATURE, 301),
        )
        val repository = SystemInfoRepositoryImpl(context)
        val details = repository.details()
        assertThat(details.device.sdkInt).isEqualTo(36)
        assertThat(details.cpu.cores).isAtLeast(1)
        assertThat(repository.details()).isSameInstanceAs(details) // read once

        val live = repository.live(intervalMillis = 1).take(2).toList()
        assertThat(live).hasSize(2)
        val first = live.first()
        assertThat(first.battery?.levelPercent).isEqualTo(42)
        assertThat(first.battery?.temperatureC).isWithin(0.01f).of(30.1f)
        assertThat(first.memory.totalBytes).isAtLeast(0L)
        assertThat(first.storage.first().name).isEqualTo("Internal storage")

        val sections = SystemInfoSections.build(details, repository.live(1).first())
        assertThat(sections.map { it.title }).containsAtLeast("Battery", "Processor", "Graphics", "Memory (RAM)", "Device", "Software")
    }
}
