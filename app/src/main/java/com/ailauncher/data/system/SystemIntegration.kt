package com.ailauncher.data.system

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.ailauncher.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Optional accessibility service used only for global actions: lock the screen (double-tap on the
 * home screen), open the notification shade (swipe down) and, in the full version, open Quick
 * Settings (a gesture's choice). It does not read window content.
 * Locking via accessibility keeps fingerprint/face unlock available, unlike DevicePolicyManager.
 */
class LauncherAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: LauncherAccessibilityService? = null
            private set
    }
}

/** Device-admin fallback for double-tap-to-lock (uses only the force-lock policy). */
class LockScreenAdminReceiver : DeviceAdminReceiver()

enum class LockMethod { ACCESSIBILITY, DEVICE_ADMIN, NONE }

/** Home-screen gestures that need system cooperation. */
@Singleton
class SystemGestureController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val devicePolicyManager = context.getSystemService(DevicePolicyManager::class.java)
    private val adminComponent = ComponentName(context, LockScreenAdminReceiver::class.java)

    val lockMethod: LockMethod
        get() = when {
            LauncherAccessibilityService.instance != null -> LockMethod.ACCESSIBILITY
            devicePolicyManager?.isAdminActive(adminComponent) == true -> LockMethod.DEVICE_ADMIN
            else -> LockMethod.NONE
        }

    val isAccessibilityServiceEnabled: Boolean get() = LauncherAccessibilityService.instance != null

    /** @return false if no lock method has been enabled by the user yet. */
    fun lockScreen(): Boolean = when (lockMethod) {
        LockMethod.ACCESSIBILITY ->
            LauncherAccessibilityService.instance?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) == true
        LockMethod.DEVICE_ADMIN -> runCatching { devicePolicyManager?.lockNow() }.isSuccess
        LockMethod.NONE -> false
    }

    /** Expands the notification shade. */
    @SuppressLint("WrongConstant")
    fun expandNotifications(): Boolean {
        LauncherAccessibilityService.instance?.let {
            return it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
        }
        // Fallback used by most launchers; requires EXPAND_STATUS_BAR (normal permission).
        return try {
            val service = context.getSystemService("statusbar") ?: return false
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
            true
        } catch (e: ReflectiveOperationException) {
            Log.w(TAG, "expandNotificationsPanel unavailable", e)
            false
        }
    }

    /** Expands Quick Settings fully (CUSTOM_GESTURES); same route as [expandNotifications]. */
    @SuppressLint("WrongConstant")
    fun expandQuickSettings(): Boolean {
        LauncherAccessibilityService.instance?.let {
            return it.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
        }
        return try {
            val service = context.getSystemService("statusbar") ?: return false
            Class.forName("android.app.StatusBarManager").getMethod("expandSettingsPanel").invoke(service)
            true
        } catch (e: ReflectiveOperationException) {
            Log.w(TAG, "expandSettingsPanel unavailable", e)
            false
        }
    }

    fun accessibilitySettingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun deviceAdminIntent(): Intent =
        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.device_admin_description))

    private companion object {
        const val TAG = "SystemGestures"
    }
}

/** RoleManager (ROLE_HOME) helpers for "Set as default launcher". */
@Singleton
class DefaultLauncherHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val roleManager = context.getSystemService(RoleManager::class.java)

    val isDefaultLauncher: Boolean
        get() = roleManager?.let { it.isRoleAvailable(RoleManager.ROLE_HOME) && it.isRoleHeld(RoleManager.ROLE_HOME) } == true

    /** Intent for the system "default home app" dialog, or the Home settings page as a fallback. */
    fun requestIntent(): Intent {
        val manager = roleManager
        return if (manager != null && manager.isRoleAvailable(RoleManager.ROLE_HOME) && !manager.isRoleHeld(RoleManager.ROLE_HOME)) {
            manager.createRequestRoleIntent(RoleManager.ROLE_HOME)
        } else {
            homeSettingsIntent()
        }
    }

    fun homeSettingsIntent(): Intent = Intent(Settings.ACTION_HOME_SETTINGS)
}
