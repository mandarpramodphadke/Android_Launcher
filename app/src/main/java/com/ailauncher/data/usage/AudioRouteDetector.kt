package com.ailauncher.data.usage

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.ailauncher.domain.model.AudioRoute
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** What audio output is connected right now (no permission needed), for app suggestions. */
@Singleton
class AudioRouteDetector @Inject constructor(@ApplicationContext private val context: Context) {

    fun current(): AudioRoute {
        val carMode = context.getSystemService(UiModeManager::class.java)?.currentModeType == Configuration.UI_MODE_TYPE_CAR
        if (carMode) return AudioRoute.CAR
        val devices = runCatching {
            context.getSystemService(AudioManager::class.java)?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        }.getOrNull().orEmpty()
        val types = devices.map { it.type }.toSet()
        return when {
            types.any { it in BLUETOOTH } -> AudioRoute.BLUETOOTH_AUDIO
            types.any { it in WIRED } -> AudioRoute.HEADPHONES
            else -> AudioRoute.SPEAKER
        }
    }

    private companion object {
        val BLUETOOTH: Set<Int> = buildSet {
            add(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP)
            add(AudioDeviceInfo.TYPE_HEARING_AID)
            // Bluetooth LE Audio devices exist from Android 12.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(AudioDeviceInfo.TYPE_BLE_HEADSET)
                add(AudioDeviceInfo.TYPE_BLE_SPEAKER)
            }
        }
        val WIRED = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET,
        )
    }
}
