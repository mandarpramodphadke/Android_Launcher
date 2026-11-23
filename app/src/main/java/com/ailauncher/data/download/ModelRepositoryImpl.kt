package com.ailauncher.data.download

import android.content.Context
import android.provider.OpenableColumns
import androidx.core.net.toUri
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ailauncher.di.ApplicationScope
import com.ailauncher.domain.model.ModelFileState
import com.ailauncher.domain.model.ModelSpec
import com.ailauncher.domain.repository.ModelRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns `filesDir/models/qwen3-1.7b-q4_k_m.gguf`: download (via [ModelDownloadWorker]), import of
 * a user-picked `.gguf`, deletion, and a unified [fileState] for the onboarding screen.
 */
@Singleton
class ModelRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    @ApplicationScope private val scope: CoroutineScope,
) : ModelRepository {

    override val spec: ModelSpec = ModelCatalog.DEFAULT

    private val modelsDir: File get() = ModelDownloadWorker.modelsDir(context)
    private val modelFile: File get() = File(modelsDir, spec.fileName)
    private val partFile: File get() = File(modelsDir, spec.fileName + ModelDownloadWorker.PART_SUFFIX)

    private val fileVersion = MutableStateFlow(0)
    private val importState = MutableStateFlow<ModelFileState?>(null)

    override val fileState: Flow<ModelFileState> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.WORK_NAME),
        fileVersion,
        importState,
    ) { infos, _, importing -> importing ?: computeState(infos.lastOrNull()) }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    private fun computeState(info: WorkInfo?): ModelFileState {
        readyModelFile()?.let { return ModelFileState.Ready(it.length(), it.absolutePath) }
        val partial = partFile.length()
        val total = spec.sizeBytes
        return when (info?.state) {
            WorkInfo.State.RUNNING -> {
                val progress = info.progress
                if (progress.getString(ModelDownloadWorker.KEY_PHASE) == ModelDownloadWorker.PHASE_VERIFYING) {
                    ModelFileState.Verifying(total)
                } else {
                    ModelFileState.Downloading(
                        downloadedBytes = progress.getLong(ModelDownloadWorker.KEY_DOWNLOADED, partial),
                        totalBytes = progress.getLong(ModelDownloadWorker.KEY_TOTAL, total),
                        bytesPerSecond = progress.getLong(ModelDownloadWorker.KEY_SPEED, 0L),
                    )
                }
            }
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ModelFileState.Downloading(partial, total, 0L)
            WorkInfo.State.FAILED -> ModelFileState.Failed(
                message = info.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "Download failed",
                downloadedBytes = partial,
                totalBytes = total,
            )
            WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED, null ->
                if (partial > 0) ModelFileState.Paused(partial, total) else ModelFileState.Missing
        }
    }

    override fun readyModelFile(): File? = modelFile.takeIf { it.isFile && it.length() >= MIN_MODEL_BYTES }

    override fun startDownload() {
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(ModelDownloadWorker.WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun pauseDownload() {
        workManager.cancelUniqueWork(ModelDownloadWorker.WORK_NAME)
    }

    override fun cancelDownload() {
        workManager.cancelUniqueWork(ModelDownloadWorker.WORK_NAME)
        scope.launch(Dispatchers.IO) {
            partFile.delete()
            fileVersion.value++
        }
    }

    override suspend fun importFrom(uri: String): Result<Unit> = withContext(Dispatchers.IO) {
        val source = uri.toUri()
        val resolver = context.contentResolver
        val tmp = File(modelsDir, spec.fileName + ".import")
        try {
            val size = resolver.query(source, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
            } ?: -1L
            if (size in 0 until MIN_MODEL_BYTES) throw IOException("That file is too small to be a language model.")
            if (size > 0 && ModelDownloadWorker.allocatableBytes(context, modelsDir) < size + IMPORT_SPACE_MARGIN) throw IOException("Not enough free storage to import the model.")

            importState.value = ModelFileState.Importing(0, size)
            val input = resolver.openInputStream(source) ?: throw IOException("Cannot open the selected file.")
            input.use { stream ->
                val header = ByteArray(8)
                if (stream.readNBytesCompat(header) != header.size || !isGguf(header)) {
                    throw IOException("The selected file is not a GGUF model.")
                }
                tmp.outputStream().buffered(1 shl 20).use { out ->
                    out.write(header)
                    var copied = header.size.toLong()
                    val buffer = ByteArray(1 shl 20)
                    var lastReport = 0L
                    while (true) {
                        val n = stream.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        copied += n
                        if (copied - lastReport >= REPORT_EVERY_BYTES) {
                            importState.value = ModelFileState.Importing(copied, size)
                            lastReport = copied
                        }
                    }
                }
            }
            modelFile.delete()
            if (!tmp.renameTo(modelFile)) throw IOException("Could not save the model file.")
            partFile.delete()
            Result.success(Unit)
        } catch (e: CancellationException) {
            tmp.delete()
            throw e
        } catch (e: Exception) {
            tmp.delete()
            Result.failure(e)
        } finally {
            importState.value = null
            fileVersion.value++
        }
    }

    override suspend fun deleteModel() = withContext(Dispatchers.IO) {
        workManager.cancelUniqueWork(ModelDownloadWorker.WORK_NAME)
        modelFile.delete()
        partFile.delete()
        fileVersion.value++
        Unit
    }

    /** GGUF files start with the ASCII magic "GGUF" followed by a little-endian uint32 version. */
    private fun isGguf(header: ByteArray): Boolean {
        val magic = header[0] == 'G'.code.toByte() && header[1] == 'G'.code.toByte() &&
            header[2] == 'U'.code.toByte() && header[3] == 'F'.code.toByte()
        val version = (header[4].toInt() and 0xFF) or ((header[5].toInt() and 0xFF) shl 8) or
            ((header[6].toInt() and 0xFF) shl 16) or ((header[7].toInt() and 0xFF) shl 24)
        return magic && version in 2..3
    }

    private fun java.io.InputStream.readNBytesCompat(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val n = read(buffer, total, buffer.size - total)
            if (n < 0) break
            total += n
        }
        return total
    }

    private companion object {
        const val MIN_MODEL_BYTES = 50L * 1024 * 1024
        const val IMPORT_SPACE_MARGIN = 64L * 1024 * 1024
        const val REPORT_EVERY_BYTES = 8L * 1024 * 1024
    }
}
