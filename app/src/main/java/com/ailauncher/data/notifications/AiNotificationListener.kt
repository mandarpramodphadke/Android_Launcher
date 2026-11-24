package com.ailauncher.data.notifications

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.ailauncher.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives notifications only after the user grants "Notification access" AND turns on the digest
 * in Settings (checked for every notification). Content is handed to
 * [NotificationDigestRepositoryImpl], which keeps it on the phone for at most 24 hours.
 */
@AndroidEntryPoint
class AiNotificationListener : NotificationListenerService() {

    @Inject lateinit var repository: NotificationDigestRepositoryImpl

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onListenerConnected() {
        val active = runCatching { activeNotifications?.toList().orEmpty() }.getOrDefault(emptyList())
        launch { repository.resync(active) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        launch { repository.onPosted(sbn) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        launch { repository.onRemoved(sbn.key) }
    }

    private fun launch(block: suspend () -> Unit) {
        scope.launch { runCatching { block() }.onFailure { Log.w(TAG, "Digest update failed", it) } }
    }

    private companion object {
        const val TAG = "AiNotificationListener"
    }
}
