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
import com.ailauncher.domain.model.ModelConfig
import com.ailauncher.domain.model.ModelFileState
import com.ailauncher.domain.model.ModelOption
import com.ailauncher.domain.model.ModelSpec
import com.ailauncher.domain.repository.ModelRepository
import com.ailauncher.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns `filesDir/models/`: the catalog models (download via [ModelDownloadWorker], one at a time),
 * user-imported `.gguf` files under `models/imported/`, the active-model choice
 * (ModelConfig.activeModelId) and a unified [fileState] of the active model for the setup screen.
 */
@Singleton
class ModelRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager,
    private val settingsRepository: SettingsRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : ModelRepository {

    override val catalog: List<ModelSpec> = ModelCatalog.all

    private val modelsDir: File get() = ModelDownloadWorker.modelsDir(context)
    private val importedDir: File get() = File(modelsDir, IMPORTED_DIR).apply { mkdirs() }

    private val fileVersion = MutableStateFlow(0)
    private val importState = MutableStateFlow<ModelFileState?>(null)

    private val activeId: StateFlow<String> = settingsRepository.modelConfig
        .map { it.activeModelId }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, ModelConfig.DEFAULT_MODEL_ID)

    override val spec: ModelSpec get() = specFor(activeId.value) ?: ModelCatalog.DEFAULT

    private val workInfos = workManager.getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.WORK_NAME)

    override val fileState: Flow<ModelFileState> = combine(workInfos, fileVersion, importState, activeId) { infos, _, importing, id ->
        importing ?: computeState(specFor(id) ?: ModelCatalog.DEFAULT, infos.lastOrNull())
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    override val options: Flow<List<ModelOption>> = combine(workInfos, fileVersion, importState, activeId) { infos, _, importing, id ->
        val info = infos.lastOrNull()
        val specs = catalog + importedSpecs()
        val active = specs.firstOrNull { it.id == id }?.id ?: ModelCatalog.DEFAULT.id
        specs.map { option ->
            val state = if (option.id == active && importing != null) importing else computeState(option, info)
            ModelOption(option, state, active = option.id == active)
        }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    private fun fileOf(model: ModelSpec) = File(modelsDir, model.fileName)
    private fun partOf(model: ModelSpec) = File(modelsDir, model.fileName + ModelDownloadWorker.PART_SUFFIX)
    private fun readyFile(model: ModelSpec): File? = fileOf(model).takeIf { it.isFile && it.length() >= MIN_MODEL_BYTES }

    private fun importedSpecs(): List<ModelSpec> =
        importedDir.listFiles { file -> file.isFile && file.name.endsWith(GGUF_SUFFIX, ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            ?.map(::importedSpec)
            .orEmpty()

    private fun importedSpec(file: File) = ModelSpec(
        id = ModelSpec.IMPORTED_PREFIX + file.name,
        displayName = file.nameWithoutExtension,
        fileName = "$IMPORTED_DIR/${file.name}",
        downloadUrl = "",
        sizeBytes = file.length(),
        sha256 = null,
        license = "Your file",
        summary = "Imported GGUF. Qwen3 / ChatML chat models work best with the launcher's tools.",
    )

    private fun specFor(id: String): ModelSpec? = ModelCatalog.byId(id) ?: if (id.startsWith(ModelSpec.IMPORTED_PREFIX)) {
        File(importedDir, id.removePrefix(ModelSpec.IMPORTED_PREFIX)).takeIf { it.isFile }?.let(::importedSpec)
    } else {
        null
    }

    private fun computeState(model: ModelSpec, info: WorkInfo?): ModelFileState {
        readyFile(model)?.let { return ModelFileState.Ready(it.length(), it.absolutePath) }
        if (model.imported) return ModelFileState.Missing
        val partial = partOf(model).length()
        val total = model.sizeBytes
        // Downloads are tagged with their model; untagged work predates the model switcher (1.7B).
        val ownsWork = info != null && (
            info.tags.contains(ModelDownloadWorker.TAG_MODEL_PREFIX + model.id) ||
                (info.tags.none { it.startsWith(ModelDownloadWorker.TAG_MODEL_PREFIX) } && model.id == ModelCatalog.DEFAULT.id)
            )
        return when (if (ownsWork) info.state else null) {
            WorkInfo.State.RUNNING -> {
                val progress = info!!.progress
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
                message = info!!.outputData.getString(ModelDownloadWorker.KEY_ERROR) ?: "Download failed",
                downloadedBytes = partial,
                totalBytes = total,
            )
            WorkInfo.State.SUCCEEDED, WorkInfo.State.CANCELLED, null ->
                if (partial > 0) ModelFileState.Paused(partial, total) else ModelFileState.Missing
        }
    }

    override fun readyModelFile(): File? = readyFile(spec)

    override suspend fun activeModelFile(): File? = withContext(Dispatchers.IO) {
        val id = settingsRepository.modelConfig.first().activeModelId
        (specFor(id) ?: ModelCatalog.DEFAULT).let(::readyFile)
    }

    override fun startDownload() {
        val model = spec
        if (model.imported || model.downloadUrl.isEmpty()) return
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setInputData(ModelDownloadWorker.inputFor(model))
            .addTag(ModelDownloadWorker.TAG_MODEL_PREFIX + model.id)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresStorageNotLow(true)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        // One download at a time: starting another model pauses the current one (its .part is kept).
        workManager.enqueueUniqueWork(ModelDownloadWorker.WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun pauseDownload() {
        workManager.cancelUniqueWork(ModelDownloadWorker.WORK_NAME)
    }

    override fun cancelDownload() {
        val model = spec
        workManager.cancelUniqueWork(ModelDownloadWorker.WORK_NAME)
        scope.launch(Dispatchers.IO) {
            partOf(model).delete()
            fileVersion.value++
        }
    }

    override suspend fun select(id: String) {
        val target = withContext(Dispatchers.IO) { specFor(id) } ?: return
        settingsRepository.updateModelConfig { it.copy(activeModelId = target.id) }
        // Callers start a download or load right after selecting: wait until the choice is visible.
        activeId.first { it == target.id }
    }

    override suspend fun importFrom(uri: String): Result<Unit> = withContext(Dispatchers.IO) {
        val source = uri.toUri()
        val resolver = context.contentResolver
        var tmp: File? = null
        try {
            val (displayName, size) = resolver.query(source, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    (if (c.isNull(0)) null else c.getString(0)) to (if (c.isNull(1)) -1L else c.getLong(1))
                } else {
                    null to -1L
                }
            } ?: (null to -1L)
            if (size in 0 until MIN_MODEL_BYTES) throw IOException("That file is too small to be a language model.")
            if (size > 0 && ModelDownloadWorker.allocatableBytes(context, modelsDir) < size + IMPORT_SPACE_MARGIN) throw IOException("Not enough free storage to import the model.")

            val target = uniqueImportFile(displayName)
            tmp = File(importedDir, target.name + ".import")
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
            if (!tmp.renameTo(target)) throw IOException("Could not save the model file.")
            tmp = null
            fileVersion.value++
            select(ModelSpec.IMPORTED_PREFIX + target.name)
            Result.success(Unit)
        } catch (e: CancellationException) {
            tmp?.delete()
            throw e
        } catch (e: Exception) {
            tmp?.delete()
            Result.failure(e)
        } finally {
            importState.value = null
            fileVersion.value++
        }
    }

    /** `models/imported/<sanitised display name>.gguf`, numbered if the name is taken. */
    private fun uniqueImportFile(displayName: String?): File {
        val base = displayName.orEmpty()
            .removeSuffix(GGUF_SUFFIX).removeSuffix(GGUF_SUFFIX.uppercase())
            .replace(Regex("""[^A-Za-z0-9._-]+"""), "-")
            .trim('-', '.')
            .take(80)
            .ifEmpty { "model" }
        var candidate = File(importedDir, base + GGUF_SUFFIX)
        var n = 2
        while (candidate.exists()) candidate = File(importedDir, "$base-${n++}$GGUF_SUFFIX")
        return candidate
    }

    override suspend fun deleteModel() = delete(spec.id)

    override suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val model = specFor(id) ?: return@withContext
        val downloading = workInfos.first().lastOrNull()?.tags?.contains(ModelDownloadWorker.TAG_MODEL_PREFIX + model.id) == true
        if (downloading || model.id == spec.id) workManager.cancelUniqueWork(ModelDownloadWorker.WORK_NAME)
        fileOf(model).delete()
        partOf(model).delete()
        fileVersion.value++
        // An imported model is gone for good: fall back to the default.
        if (model.imported && activeId.value == model.id) select(ModelCatalog.DEFAULT.id)
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
        const val IMPORTED_DIR = "imported"
        const val GGUF_SUFFIX = ".gguf"
        const val MIN_MODEL_BYTES = 50L * 1024 * 1024
        const val IMPORT_SPACE_MARGIN = 64L * 1024 * 1024
        const val REPORT_EVERY_BYTES = 8L * 1024 * 1024
    }
}
