package com.ailauncher.data.actions

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.ailauncher.domain.ai.ToggleState
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device toggles for the `toggle_setting` tool. Since Android 10, apps can no longer flip Wi-Fi or
 * Bluetooth directly, so those open the system Settings Panel / page; the flashlight and Do Not
 * Disturb (once policy access is granted) are switched directly.
 */
@Singleton
class DeviceSettingsController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val cameraManager = context.getSystemService(CameraManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    @Volatile private var torchOn = false
    private val torchCameraId: String? by lazy {
        val manager = cameraManager ?: return@lazy null
        runCatching {
            manager.cameraIdList.firstOrNull { id ->
                val characteristics = manager.getCameraCharacteristics(id)
                characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    characteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }.getOrNull()
    }

    init {
        runCatching {
            cameraManager?.registerTorchCallback(
                object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        if (cameraId == torchCameraId) torchOn = enabled
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        }
    }

    /** @return the new torch state, or null if the device has no flash. */
    fun setTorch(state: ToggleState): Boolean? {
        val id = torchCameraId ?: return null
        val target = when (state) {
            ToggleState.ON -> true
            ToggleState.OFF -> false
            ToggleState.TOGGLE -> !torchOn
        }
        cameraManager?.setTorchMode(id, target) ?: return null
        torchOn = target
        return target
    }

    fun openInternetPanel() = start(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))

    fun openBluetoothSettings() = start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))

    val hasDndAccess: Boolean get() = notificationManager?.isNotificationPolicyAccessGranted == true

    /** @return the new DND state, or null if policy access must be granted first. */
    fun setDoNotDisturb(state: ToggleState): Boolean? {
        val manager = notificationManager ?: return null
        if (!manager.isNotificationPolicyAccessGranted) return null
        val active = manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL
        val target = when (state) {
            ToggleState.ON -> true
            ToggleState.OFF -> false
            ToggleState.TOGGLE -> !active
        }
        manager.setInterruptionFilter(
            if (target) NotificationManager.INTERRUPTION_FILTER_PRIORITY else NotificationManager.INTERRUPTION_FILTER_ALL,
        )
        return target
    }

    fun openDndAccessSettings() = start(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))

    private fun start(intent: Intent): Boolean = runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    }.getOrDefault(false)
}
