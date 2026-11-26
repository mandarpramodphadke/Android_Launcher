package com.ailauncher.data.media

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.getSystemService
import androidx.core.graphics.scale
import com.ailauncher.data.notifications.AiNotificationListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/** The media session shown on the home screen (a song, podcast or video that is playing or paused). */
data class NowPlaying(
    val packageName: String,
    val appLabel: String,
    val title: String,
    val artist: String?,
    /** Album art, scaled down for the home screen; null when the player has none. */
    val art: Bitmap?,
    val isPlaying: Boolean,
    val canSkipNext: Boolean,
    val canSkipPrevious: Boolean,
    /** Identifies the session, so actions reach the player that was shown. */
    val sessionToken: Any,
)

/**
 * What is playing, read from the phone's active media sessions. Android only lists them to an
 * enabled notification listener, so this needs the "Notification access" the digest already asks
 * for (the digest itself can stay off). Nothing is stored or sent anywhere.
 */
@Singleton
class NowPlayingRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val sessions: MediaSessionManager? = context.getSystemService()
    private val listener = ComponentName(context, AiNotificationListener::class.java)
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var current: MediaController? = null

    /** The session to show (playing first, else the most recent paused one), or null. */
    val nowPlaying: Flow<NowPlaying?> = callbackFlow {
        val manager = sessions
        if (manager == null) {
            trySend(null)
            awaitClose()
            return@callbackFlow
        }
        var watched: MediaController? = null
        val controllerCallback = object : MediaController.Callback() {
            override fun onPlaybackStateChanged(state: PlaybackState?) {
                trySend(watched?.let(::describe))
            }

            override fun onMetadataChanged(metadata: MediaMetadata?) {
                trySend(watched?.let(::describe))
            }

            override fun onSessionDestroyed() {
                trySend(null)
            }
        }

        fun pick(controllers: List<MediaController>?) {
            val list = controllers.orEmpty().filter { it.metadata != null }
            val chosen = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
            if (chosen?.sessionToken != watched?.sessionToken) {
                watched?.unregisterCallback(controllerCallback)
                watched = chosen
                chosen?.registerCallback(controllerCallback, main)
            }
            current = chosen
            trySend(chosen?.let(::describe))
        }

        val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { pick(it) }
        val registered = try {
            manager.addOnActiveSessionsChangedListener(sessionsListener, listener, main)
            pick(manager.getActiveSessions(listener))
            true
        } catch (e: SecurityException) {
            // No notification access (yet).
            Log.d(TAG, "Media sessions need notification access", e)
            trySend(null)
            false
        }
        awaitClose {
            if (registered) runCatching { manager.removeOnActiveSessionsChangedListener(sessionsListener) }
            watched?.unregisterCallback(controllerCallback)
            current = null
        }
    }.conflate().distinctUntilChanged()

    fun playPause(item: NowPlaying) {
        val controller = controllerFor(item) ?: return
        if (controller.playbackState?.state == PlaybackState.STATE_PLAYING) controller.transportControls.pause() else controller.transportControls.play()
    }

    fun next(item: NowPlaying) {
        controllerFor(item)?.transportControls?.skipToNext()
    }

    fun previous(item: NowPlaying) {
        controllerFor(item)?.transportControls?.skipToPrevious()
    }

    /** Opens the player: the screen the session asks for, else the app itself. */
    fun open(item: NowPlaying): Boolean {
        val activity = controllerFor(item)?.sessionActivity
        if (activity != null && runCatching { activity.send() }.isSuccess) return true
        val launch = context.packageManager.getLaunchIntentForPackage(item.packageName) ?: return false
        return runCatching { context.startActivity(launch.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    private fun controllerFor(item: NowPlaying): MediaController? = current?.takeIf { it.sessionToken == item.sessionToken }

    private fun describe(controller: MediaController): NowPlaying? {
        val metadata = controller.metadata ?: return null
        val title = metadata.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: return null
        val artist = metadata.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: metadata.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val art = (
            metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: metadata.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
            )?.let(::thumbnail)
        val state = controller.playbackState
        val actions = state?.actions ?: 0L
        return NowPlaying(
            packageName = controller.packageName,
            appLabel = label(controller.packageName),
            title = title,
            artist = artist?.takeIf { it.isNotBlank() },
            art = art,
            isPlaying = state?.state == PlaybackState.STATE_PLAYING || state?.state == PlaybackState.STATE_BUFFERING,
            canSkipNext = actions and PlaybackState.ACTION_SKIP_TO_NEXT != 0L,
            canSkipPrevious = actions and PlaybackState.ACTION_SKIP_TO_PREVIOUS != 0L,
            sessionToken = controller.sessionToken,
        )
    }

    private fun label(packageName: String): String = try {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }

    /** Players hand over art up to full size; the home screen needs a small square. */
    private fun thumbnail(bitmap: Bitmap): Bitmap? = runCatching {
        if (bitmap.isRecycled) return null
        val max = ART_SIZE_PX
        if (bitmap.width <= max && bitmap.height <= max) return bitmap
        val ratio = max.toFloat() / maxOf(bitmap.width, bitmap.height)
        bitmap.scale((bitmap.width * ratio).toInt().coerceAtLeast(1), (bitmap.height * ratio).toInt().coerceAtLeast(1))
    }.getOrNull()

    private companion object {
        const val TAG = "NowPlayingRepository"
        const val ART_SIZE_PX = 160
    }
}
