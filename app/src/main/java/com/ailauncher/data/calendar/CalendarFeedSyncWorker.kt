package com.ailauncher.data.calendar

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ailauncher.domain.repository.CalendarSourcesRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Re-reads subscribed calendars every few hours while the phone is online. */
@HiltWorker
class CalendarFeedSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val calendars: CalendarSourcesRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        calendars.refreshAll()
        return Result.success()
    }

    companion object {
        const val NAME = "calendar-feed-sync"
    }
}
