package com.ailauncher.data.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ailauncher.R
import com.ailauncher.presentation.MainActivity

object Notifications {
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_DOWNLOADS = "model_downloads"
    const val DOWNLOAD_NOTIFICATION_ID = 1001

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_REMINDERS, context.getString(R.string.channel_reminders_name), NotificationManager.IMPORTANCE_HIGH)
                    .apply { description = context.getString(R.string.channel_reminders_description) },
                NotificationChannel(CHANNEL_DOWNLOADS, context.getString(R.string.channel_downloads_name), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = context.getString(R.string.channel_downloads_description) },
            ),
        )
    }

    fun canPost(context: Context): Boolean {
        // POST_NOTIFICATIONS is a runtime permission only on Android 13+; earlier versions grant it.
        val permitted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return permitted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun showReminder(context: Context, reminderId: Long, title: String) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context,
            reminderId.toInt(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_sparkle)
            .setContentTitle(title)
            .setContentText("Reminder from your AI assistant")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(REMINDER_TAG, reminderId.toInt(), notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    private const val REMINDER_TAG = "reminder"
}
