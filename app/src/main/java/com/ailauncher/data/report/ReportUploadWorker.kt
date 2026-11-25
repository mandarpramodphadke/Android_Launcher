package com.ailauncher.data.report

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Posts one AI-reply report to the developer's Google Form; waits for a connection and retries. */
@HiltWorker
class ReportUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val okHttpClient: OkHttpClient,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(KEY_URL)
        val file = inputData.getString(KEY_FILE)?.let(::File)?.takeIf { it.isFile } ?: return@withContext Result.failure()
        // The destination comes from the build configuration and is always a Google Form.
        if (url == null || !url.startsWith("https://docs.google.com/forms/")) {
            file.delete()
            return@withContext Result.failure()
        }
        val request = Request.Builder()
            .url(url)
            .post(file.readText().toRequestBody(FORM_MEDIA_TYPE))
            .build()
        try {
            okHttpClient.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        file.delete()
                        Result.success()
                    }
                    (response.code >= 500 || response.code == 429) && runAttemptCount < MAX_ATTEMPTS -> Result.retry()
                    else -> {
                        Log.w(TAG, "Report rejected (HTTP ${response.code}); check report.form.url")
                        file.delete()
                        Result.failure()
                    }
                }
            }
        } catch (e: IOException) {
            if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                file.delete()
                Result.failure()
            }
        }
    }

    companion object {
        private const val TAG = "ReportUpload"
        private const val KEY_URL = "url"
        private const val KEY_FILE = "file"
        private const val MAX_ATTEMPTS = 10
        private val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded".toMediaType()

        fun enqueue(workManager: WorkManager, actionUrl: String, body: File) {
            val request = OneTimeWorkRequestBuilder<ReportUploadWorker>()
                .setInputData(workDataOf(KEY_URL to actionUrl, KEY_FILE to body.absolutePath))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            workManager.enqueue(request)
        }
    }
}
