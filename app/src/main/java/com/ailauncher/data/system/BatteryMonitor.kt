package com.ailauncher.data.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.ailauncher.domain.model.BatteryGlance
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/** Battery level and charging state from the system's battery broadcast (no permission needed). */
@Singleton
class BatteryMonitor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val battery: Flow<BatteryGlance?> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(read(intent))
            }
        }
        // The broadcast is sticky: registering returns the current state straight away.
        val sticky = ContextCompat.registerReceiver(context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
        trySend(sticky?.let(::read))
        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }.distinctUntilChanged()

    private fun read(intent: Intent): BatteryGlance? {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return BatteryGlance((level * 100 / scale).coerceIn(0, 100), charging)
    }
}
