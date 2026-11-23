package com.ailauncher.data.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import com.ailauncher.domain.model.UpcomingEvent
import com.ailauncher.domain.repository.ReminderRepository
import com.ailauncher.domain.repository.UpcomingEventsProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/** Next system alarm (any clock app) or next app reminder, whichever comes first. */
@Singleton
class UpcomingEventsProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminders: ReminderRepository,
) : UpcomingEventsProvider {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    /** Emits the next alarm-clock trigger time, refreshed on ACTION_NEXT_ALARM_CLOCK_CHANGED. */
    val nextAlarmMillis: Flow<Long?> = callbackFlow {
        fun current() = alarmManager?.nextAlarmClock?.triggerTime
        trySend(current())
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(current())
            }
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    private val minuteTicker: Flow<Long> = flow {
        while (true) {
            val now = System.currentTimeMillis()
            emit(now)
            delay(60_000L - now % 60_000L)
        }
    }

    override val nextEvent: Flow<UpcomingEvent?> =
        combine(nextAlarmMillis, reminders.observeUpcoming(), minuteTicker) { alarm, upcoming, now ->
            val alarmEvent = alarm?.takeIf { it > now }?.let { UpcomingEvent(UpcomingEvent.Kind.ALARM, "Alarm", it) }
            val reminderEvent = upcoming.firstOrNull { it.triggerAtMillis > now }
                ?.let { UpcomingEvent(UpcomingEvent.Kind.REMINDER, it.title, it.triggerAtMillis) }
            listOfNotNull(alarmEvent, reminderEvent).minByOrNull { it.triggerAtMillis }
        }.distinctUntilChanged()
}
