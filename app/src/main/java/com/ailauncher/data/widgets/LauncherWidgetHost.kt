package com.ailauncher.data.widgets

import android.annotation.SuppressLint
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.SizeF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ViewConfiguration
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/** A widget an app offers, for the picker sheet. */
data class WidgetProviderEntry(
    val info: AppWidgetProviderInfo,
    val label: String,
    val appLabel: String,
    val packageName: String,
    /** Minimum size in dp (resize limits may allow smaller on Android 12+). */
    val minWidthDp: Float,
    val minHeightDp: Float,
    /** Android 12+ suggested default size in cells, or 0. */
    val targetCellWidth: Int,
    val targetCellHeight: Int,
) {
    val provider: ComponentName get() = info.provider
}

/** Result of the system "Configure widget" activity, routed from MainActivity.onActivityResult. */
data class WidgetConfigureResult(val requestCode: Int, val appWidgetId: Int, val success: Boolean)

/**
 * The launcher's [AppWidgetHost]: id allocation, binding (silently when allowed, otherwise through
 * the system permission dialog), optional configuration, view creation and clean-up. The host
 * listens for widget updates only while the home screen is started.
 */
@Singleton
class LauncherWidgetHost @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)

    private val host = object : AppWidgetHost(context, HOST_ID) {
        override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo?): AppWidgetHostView =
            LauncherAppWidgetHostView(context)
    }

    private val _configureResults = MutableSharedFlow<WidgetConfigureResult>(extraBufferCapacity = 4)
    val configureResults: SharedFlow<WidgetConfigureResult> = _configureResults.asSharedFlow()

    fun startListening() = runCatching { host.startListening() }.onFailure { Log.w(TAG, "startListening failed", it) }

    fun stopListening() = runCatching { host.stopListening() }.onFailure { Log.w(TAG, "stopListening failed", it) }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(appWidgetId: Int) {
        runCatching { host.deleteAppWidgetId(appWidgetId) }
    }

    fun info(appWidgetId: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(appWidgetId) }.getOrNull()

    /** Binds without UI when the user has already allowed this launcher to add widgets. */
    fun bindIfAllowed(appWidgetId: Int, entry: WidgetProviderEntry): Boolean =
        runCatching { manager.bindAppWidgetIdIfAllowed(appWidgetId, entry.info.profile, entry.provider, null) }.getOrDefault(false)

    /** The system "Allow AI Launcher to create widgets?" dialog. */
    fun bindIntent(appWidgetId: Int, entry: WidgetProviderEntry): Intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, entry.provider)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, entry.info.profile)

    fun needsConfiguration(appWidgetId: Int): Boolean {
        val info = info(appWidgetId) ?: return false
        if (info.configure == null) return false
        // "Configuration optional" widgets (Android 12+) can be placed with their defaults.
        val optional = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) != 0
        return !optional
    }

    /** Configuration activities are often not exported, so the host must launch them. */
    fun startConfigure(activity: Activity, appWidgetId: Int, requestCode: Int): Boolean = runCatching {
        host.startAppWidgetConfigureActivityForResult(activity, appWidgetId, 0, requestCode, null)
        true
    }.onFailure { Log.w(TAG, "Configure failed for $appWidgetId", it) }.getOrDefault(false)

    fun onConfigureResult(requestCode: Int, appWidgetId: Int, success: Boolean) {
        _configureResults.tryEmit(WidgetConfigureResult(requestCode, appWidgetId, success))
    }

    fun createView(viewContext: Context, appWidgetId: Int): LauncherAppWidgetHostView? {
        val info = info(appWidgetId) ?: return null
        return runCatching { host.createView(viewContext, appWidgetId, info) as? LauncherAppWidgetHostView }.getOrNull()
    }

    /** Tells the widget its size (in dp) so it can pick a layout. */
    fun updateSize(view: AppWidgetHostView, widthDp: Float, heightDp: Float) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                view.updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp, heightDp)))
            } else {
                @Suppress("DEPRECATION")
                view.updateAppWidgetSize(null, widthDp.toInt(), heightDp.toInt(), widthDp.toInt(), heightDp.toInt())
            }
        }
    }

    fun providers(): List<WidgetProviderEntry> {
        val pm = context.packageManager
        val density = context.resources.displayMetrics.density
        return runCatching { manager.getInstalledProvidersForProfile(null) }.getOrDefault(emptyList())
            .mapNotNull { info ->
                val packageName = info.provider?.packageName ?: return@mapNotNull null
                val appLabel = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString() }.getOrDefault(packageName)
                WidgetProviderEntry(
                    info = info,
                    label = runCatching { info.loadLabel(pm) }.getOrNull() ?: appLabel,
                    appLabel = appLabel,
                    packageName = packageName,
                    minWidthDp = info.minWidth / density,
                    minHeightDp = info.minHeight / density,
                    targetCellWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) info.targetCellWidth else 0,
                    targetCellHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) info.targetCellHeight else 0,
                )
            }
            .sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
    }

    fun previewImage(entry: WidgetProviderEntry): Drawable? = runCatching {
        entry.info.loadPreviewImage(context, context.resources.displayMetrics.densityDpi)
            ?: entry.info.loadIcon(context, context.resources.displayMetrics.densityDpi)
    }.getOrNull()

    companion object {
        private const val TAG = "WidgetHost"
        const val HOST_ID = 0x41494C // "AIL"
    }
}

/**
 * Widget view that reports a long press (to enter resize / remove mode) without breaking taps and
 * scrolling inside the widget: the press is detected in onInterceptTouchEvent, and only once it
 * fires does the host take over the gesture (the approach Launcher3 uses).
 */
class LauncherAppWidgetHostView(context: Context) : AppWidgetHostView(context) {

    var onLongPress: (() -> Unit)? = null

    private var longPressed = false
    private var downX = 0f
    private var downY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPressRunnable = Runnable {
        if (parent != null && isPressedLongEnough) {
            longPressed = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onLongPress?.invoke()
        }
    }
    private var isPressedLongEnough = false

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                longPressed = false
                isPressedLongEnough = true
                downX = ev.x
                downY = ev.y
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
            }
            MotionEvent.ACTION_MOVE -> if (abs(ev.x - downX) > touchSlop || abs(ev.y - downY) > touchSlop) cancelLongPress()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelLongPress()
        }
        return longPressed
    }

    // Clicks are left to super.onTouchEvent (which calls performClick); the only event consumed
    // here is the end of a long press, which is not a click.
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            cancelLongPress()
            if (longPressed) {
                longPressed = false
                return true
            }
        }
        return longPressed || super.onTouchEvent(event)
    }

    override fun cancelLongPress() {
        super.cancelLongPress()
        isPressedLongEnough = false
        removeCallbacks(longPressRunnable)
    }
}
