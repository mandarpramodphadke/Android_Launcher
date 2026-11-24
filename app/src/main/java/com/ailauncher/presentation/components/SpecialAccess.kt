package com.ailauncher.presentation.components

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.ailauncher.data.notifications.AiNotificationListener

/** Runtime permissions and special-access screens used by the v2 tools (all data stays on-device). */
object SpecialAccess {

    /** Photos, videos and audio (MediaStore). */
    fun mediaPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun usageAccessIntent(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.fromParts("package", context.packageName, null))

    /** "All files access" (documents and archives); Android 11+. */
    fun allFilesAccessIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", context.packageName, null))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        }

    fun notificationListenerIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(context, AiNotificationListener::class.java).flattenToString())
        } else {
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        }

    /** Starts [intent], falling back to [fallback] (some OEMs drop the detailed screens). */
    fun open(context: Context, intent: Intent, fallback: Intent? = null): Boolean =
        runCatching { context.startActivity(intent) }.isSuccess ||
            (fallback != null && runCatching { context.startActivity(fallback) }.isSuccess)
}

/**
 * Prominent disclosure shown before the notification digest is switched on: what is read, where
 * it stays, and how long it is kept.
 */
@Composable
fun NotificationDigestDisclosure(onAccept: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.NotificationsActive, null) },
        title = { Text("Notification digest") },
        text = {
            Text(
                "AI Launcher will read the title and text of notifications you receive to show unread badges, " +
                    "one-tap pills (codes, meeting links, parcel tracking) and answer \"what did I miss?\".\n\n" +
                    "• Everything stays on this phone; nothing is uploaded.\n" +
                    "• Ongoing and media notifications are skipped.\n" +
                    "• Entries are deleted automatically after 24 hours, and you can turn this off at any time in Settings.\n\n" +
                    "Next, Android will ask you to allow notification access for AI Launcher.",
            )
        },
        confirmButton = { TextButton(onClick = onAccept) { Text("Turn on") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}
