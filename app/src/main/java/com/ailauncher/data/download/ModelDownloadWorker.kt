package com.ailauncher.data.download

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.storage.StorageManager
import android.os.SystemClock
import android.text.format.Formatter
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ailauncher.R
import com.ailauncher.data.reminders.Notifications
import com.ailauncher.domain.model.ModelSpec
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * Foreground (dataSync) WorkManager job that downloads the GGUF from Hugging Face.
 *
 *  * Resumable: bytes go to `<name>.part`; a restart sends `Range: bytes=<size>-`.
 *  * Pause = cancelling the unique work (the partial file is kept), Resume = enqueue again.
 *  * Reports downloaded bytes, total and an exponentially-smoothed speed via setProgress.
 *  * Verifies the SHA-256 before atomically renaming the file into place.
 */
@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val okHttpClient: OkHttpClient,
) : CoroutineWorker(appContext, params) {

    private val spec: ModelSpec = ModelCatalog.DEFAULT

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val dir = modelsDir(applicationContext)
        val target = File(dir, spec.fileName)
        val part = File(dir, spec.fileName + PART_SUFFIX)
        if (target.length() == spec.sizeBytes) return@withContext Result.success()

        val needed = spec.sizeBytes - part.length() + FREE_SPACE_MARGIN
        if (allocatableBytes(applicationContext, dir) < needed) {
            return@withContext failure(
                "Not enough free storage: ${Formatter.formatShortFileSize(applicationContext, needed)} needed.",
            )
        }

        promoteToForeground(part.length())
        try {
            download(part)
        } catch (e: IOException) {
            Log.w(TAG, "Download interrupted", e)
            return@withContext if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else failure(e.message ?: "Network error")
        }
        if (isStopped) return@withContext Result.failure()

        setProgress(workDataOf(KEY_PHASE to PHASE_VERIFYING, KEY_DOWNLOADED to part.length(), KEY_TOTAL to spec.sizeBytes))
        val expected = spec.sha256
        if (expected != null) {
            val actual = sha256(part)
            if (!actual.equals(expected, ignoreCase = true)) {
                part.delete()
                return@withContext failure("The download was corrupted (checksum mismatch). Please try again.")
            }
        }
        target.delete()
        if (!part.renameTo(target)) return@withContext failure("Could not save the model file.")
        Result.success()
    }

    private suspend fun download(part: File) {
        var downloaded = part.length()
        if (downloaded >= spec.sizeBytes) return

        val request = Request.Builder()
            .url(spec.downloadUrl)
            .header("User-Agent", "AiLauncher/1.0 (Android)")
            .apply { if (downloaded > 0) header("Range", "bytes=$downloaded-") }
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val append = when (response.code) {
                206 -> true
                200 -> {
                    downloaded = 0 // server ignored the range
                    false
                }
                416 -> return // nothing left to fetch; verification decides
                else -> throw IOException("Server returned HTTP ${response.code}")
            }
            val total = parseContentRangeTotal(response.header("Content-Range"))
                ?: response.body.contentLength().takeIf { it > 0 }?.plus(downloaded)
                ?: spec.sizeBytes

            FileOutputStream(part, append).use { out ->
                val input = response.body.byteStream()
                val buffer = ByteArray(BUFFER_BYTES)
                var windowStart = SystemClock.elapsedRealtime()
                var windowBytes = 0L
                var speed = 0L
                while (true) {
                    if (isStopped) return
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    downloaded += read
                    windowBytes += read
                    val now = SystemClock.elapsedRealtime()
                    val elapsed = now - windowStart
                    if (elapsed >= PROGRESS_INTERVAL_MS) {
                        val instant = windowBytes * 1000 / elapsed
                        speed = if (speed == 0L) instant else (speed * 0.7 + instant * 0.3).toLong()
                        setProgress(
                            workDataOf(
                                KEY_PHASE to PHASE_DOWNLOADING,
                                KEY_DOWNLOADED to downloaded,
                                KEY_TOTAL to total,
                                KEY_SPEED to speed,
                            ),
                        )
                        updateNotification(downloaded, total)
                        windowStart = now
                        windowBytes = 0
                    }
                }
                out.fd.sync()
            }
            if (downloaded < total) throw IOException("Connection closed after $downloaded of $total bytes")
        }
    }

    private suspend fun promoteToForeground(downloaded: Long) {
        try {
            setForeground(foregroundInfo(downloaded, spec.sizeBytes))
        } catch (e: IllegalStateException) {
            // Android 12+ forbids starting a foreground service from the background; the work
            // continues as a regular (time-limited) worker and resumes later if stopped.
            Log.w(TAG, "Running download without foreground service", e)
        }
    }

    private suspend fun updateNotification(downloaded: Long, total: Long) {
        runCatching { setForeground(foregroundInfo(downloaded, total)) }
    }

    private fun foregroundInfo(downloaded: Long, total: Long): ForegroundInfo {
        Notifications.createChannels(applicationContext)
        val percent = if (total > 0) (downloaded * 100 / total).toInt() else 0
        val pause = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
        val notification = NotificationCompat.Builder(applicationContext, Notifications.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_stat_sparkle)
            .setContentTitle(applicationContext.getString(R.string.download_notification_title))
            .setContentText(
                "${Formatter.formatShortFileSize(applicationContext, downloaded)} / " +
                    Formatter.formatShortFileSize(applicationContext, total),
            )
            .setProgress(100, percent, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, applicationContext.getString(R.string.download_notification_pause), pause)
            .build()
        return ForegroundInfo(Notifications.DOWNLOAD_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private fun failure(message: String) = Result.failure(workDataOf(KEY_ERROR to message))

    companion object {
        private const val TAG = "ModelDownloadWorker"
        const val WORK_NAME = "model-download"
        const val PART_SUFFIX = ".part"
        const val KEY_PHASE = "phase"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"
        const val KEY_SPEED = "speed"
        const val KEY_ERROR = "error"
        const val PHASE_DOWNLOADING = "downloading"
        const val PHASE_VERIFYING = "verifying"
        private const val MAX_ATTEMPTS = 8
        private const val BUFFER_BYTES = 256 * 1024
        private const val PROGRESS_INTERVAL_MS = 750L
        private const val FREE_SPACE_MARGIN = 64L * 1024 * 1024

        fun modelsDir(context: Context): File = File(context.filesDir, "models").apply { mkdirs() }

        /**
         * Bytes the app can allocate in [dir], including cached data the system would clear for us
         * (File.usableSpace alone under-reports free space on most devices).
         */
        fun allocatableBytes(context: Context, dir: File): Long = runCatching {
            val storage = context.getSystemService(StorageManager::class.java)
            storage.getAllocatableBytes(storage.getUuidForPath(dir))
        }.getOrElse { dir.usableSpace }

        internal fun parseContentRangeTotal(header: String?): Long? =
            header?.substringAfterLast('/')?.trim()?.toLongOrNull()

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered(1 shl 20).use { input ->
                val buffer = ByteArray(1 shl 20)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
