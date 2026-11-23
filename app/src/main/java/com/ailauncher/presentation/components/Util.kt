package com.ailauncher.presentation.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun Context.formatBytes(bytes: Long): String = Formatter.formatShortFileSize(this, bytes.coerceAtLeast(0))

fun formatDuration(seconds: Long): String = when {
    seconds < 60 -> "${seconds}s"
    seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
    else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
}

/** Runs [onResume] every time the hosting lifecycle resumes (e.g. returning from Settings). */
@Composable
fun OnResume(onResume: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val callback = rememberUpdatedState(onResume)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) callback.value()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}
