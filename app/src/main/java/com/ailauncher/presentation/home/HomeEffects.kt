package com.ailauncher.presentation.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.ailauncher.presentation.components.OnResume
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Lock / unlock choreography. When the screen turns off the home elements are parked out of view;
 * on ACTION_USER_PRESENT (the user unlocked) or when the home screen resumes they spring back in
 * one after another (clock → pages → dock → command bar).
 */
@Composable
fun rememberHomeEntrance(onScreenOff: () -> Unit): HomeEntrance {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val parts = remember { List(4) { Animatable(1f) } }
    val screenOff by rememberUpdatedState(onScreenOff)

    fun play() {
        if (parts.all { it.value >= 1f && !it.isRunning }) return
        parts.forEachIndexed { index, part ->
            scope.launch {
                delay(index * STAGGER_MILLIS)
                part.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
            }
        }
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        screenOff()
                        scope.launch { parts.forEach { it.snapTo(0f) } }
                    }
                    Intent.ACTION_USER_PRESENT -> play()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // System broadcasts still reach a not-exported receiver.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    // Unlocked straight into another app, then came home: still play the entrance.
    OnResume { play() }

    return HomeEntrance(parts[0].value, parts[1].value, parts[2].value, parts[3].value)
}

private const val STAGGER_MILLIS = 55L

/** Duration of the pre-lock dim / scale-out before the screen is turned off. */
const val PRE_LOCK_MILLIS = 160
