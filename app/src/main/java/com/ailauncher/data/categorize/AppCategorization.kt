package com.ailauncher.data.categorize

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ailauncher.domain.model.CategorizationProgress
import com.ailauncher.domain.model.LlmException
import com.ailauncher.domain.repository.CategorizationScheduler
import com.ailauncher.domain.usecase.CategorizeAppsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Background `sort_apps` pass. Results are persisted batch by batch, so a pre-empted, stopped or
 * retried run resumes where it left off.
 */
@HiltWorker
class AppCategorizationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val categorizeApps: CategorizeAppsUseCase,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        val force = inputData.getBoolean(KEY_FORCE, false)
        val assigned = categorizeApps(force) { progress ->
            setProgress(workDataOf(KEY_DONE to progress.done, KEY_TOTAL to progress.total))
        }
        Result.success(workDataOf(KEY_DONE to assigned))
    } catch (e: LlmException.Preempted) {
        Result.retry() // the user started chatting; try again later
    } catch (e: LlmException.Overheated) {
        Result.retry()
    } catch (e: LlmException.InsufficientMemory) {
        Result.retry()
    } catch (e: LlmException) {
        Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Categorization failed")))
    }

    companion object {
        const val WORK_NAME = "app-categorization"
        const val KEY_FORCE = "force"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
    }
}

@Singleton
class WorkManagerCategorizationScheduler @Inject constructor(
    private val workManager: WorkManager,
) : CategorizationScheduler {

    override val progress: Flow<CategorizationProgress> =
        workManager.getWorkInfosForUniqueWorkFlow(AppCategorizationWorker.WORK_NAME)
            .map { infos ->
                val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
                val queued = infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }
                val failed = infos.lastOrNull()?.takeIf { it.state == WorkInfo.State.FAILED }
                CategorizationProgress(
                    running = running != null || queued,
                    categorized = running?.progress?.getInt(AppCategorizationWorker.KEY_DONE, 0) ?: 0,
                    total = running?.progress?.getInt(AppCategorizationWorker.KEY_TOTAL, 0) ?: 0,
                    message = failed?.outputData?.getString(AppCategorizationWorker.KEY_ERROR),
                )
            }
            .distinctUntilChanged()

    override fun schedule(force: Boolean) {
        val request = OneTimeWorkRequestBuilder<AppCategorizationWorker>()
            .setInputData(workDataOf(AppCategorizationWorker.KEY_FORCE to force))
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(AppCategorizationWorker.WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }
}
