package com.ailauncher.data.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ailauncher.di.ApplicationScope
import com.ailauncher.domain.repository.ReminderRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Fires a locally scheduled reminder: posts the notification and marks it as done. */
@AndroidEntryPoint
class ReminderAlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var reminders: ReminderRepository

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val id = intent.getLongExtra(EXTRA_ID, -1L).takeIf { it >= 0 } ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Reminder"
        Notifications.showReminder(context, id, title)
        val pending = goAsync()
        scope.launch {
            try {
                reminders.markFired(id)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.ailauncher.action.FIRE_REMINDER"
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_TITLE = "reminder_title"
    }
}

/**
 * AlarmManager alarms are cleared on reboot and on app update; re-arm pending reminders. Also
 * re-arms as exact alarms once the user grants "Alarms & reminders".
 */
@AndroidEntryPoint
class ReminderRescheduleReceiver : BroadcastReceiver() {

    @Inject lateinit var reminders: ReminderRepository

    @Inject @ApplicationScope
    lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            -> {
                val pending = goAsync()
                scope.launch {
                    try {
                        reminders.rescheduleAll()
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
