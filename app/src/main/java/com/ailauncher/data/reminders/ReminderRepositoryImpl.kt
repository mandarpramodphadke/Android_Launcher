package com.ailauncher.data.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ailauncher.data.local.dao.ReminderDao
import com.ailauncher.data.local.entity.ReminderEntity
import com.ailauncher.domain.model.Reminder
import com.ailauncher.domain.model.ReminderStatus
import com.ailauncher.domain.repository.ReminderRepository
import com.ailauncher.domain.repository.ReminderScheduleResult
import com.ailauncher.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `create_reminder` backend.
 *
 *  * If the user keeps reminders in their calendar and a writable calendar account exists, the
 *    reminder becomes a CalendarContract event with a pop-up alert (synced to all their devices).
 *  * Otherwise it is stored in Room and an exact AlarmManager alarm posts a local notification
 *    (falling back to an inexact, Doze-friendly alarm when SCHEDULE_EXACT_ALARM is not granted).
 */
@Singleton
class ReminderRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: ReminderDao,
    private val calendar: CalendarDataSource,
    private val settingsRepository: SettingsRepository,
) : ReminderRepository {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun observeUpcoming(): Flow<List<Reminder>> =
        dao.observeScheduled().map { rows -> rows.map { it.toDomain() } }

    override suspend fun schedule(title: String, triggerAtMillis: Long): ReminderScheduleResult = withContext(Dispatchers.IO) {
        val useCalendar = settingsRepository.launcherSettings.first().saveRemindersToCalendar
        val calendarId = if (useCalendar) calendar.primaryWritableCalendarId() else null
        val eventId = calendarId?.let { calendar.insertReminderEvent(it, title, triggerAtMillis) }

        val entity = ReminderEntity(
            title = title,
            triggerAtMillis = triggerAtMillis,
            calendarEventId = eventId,
            status = ReminderStatus.SCHEDULED.name,
            createdAt = System.currentTimeMillis(),
        )
        val saved = entity.copy(id = dao.insert(entity))
        if (eventId != null) {
            ReminderScheduleResult.InCalendar(saved.toDomain())
        } else {
            ReminderScheduleResult.Local(saved.toDomain(), exact = arm(saved))
        }
    }

    override suspend fun remindersBetween(startMillis: Long, endMillis: Long): List<Reminder> =
        dao.between(startMillis, endMillis).map { it.toDomain() }

    override suspend fun markFired(id: Long) = dao.setStatus(id, ReminderStatus.FIRED.name)

    override suspend fun cancel(id: Long) {
        val reminder = dao.get(id) ?: return
        reminder.calendarEventId?.let(calendar::deleteEvent)
        alarmManager?.cancel(pendingIntent(reminder.id, reminder.title))
        dao.setStatus(id, ReminderStatus.CANCELLED.name)
    }

    /** Re-arms local alarms after reboot/update and fires the ones missed while powered off. */
    override suspend fun rescheduleAll() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        for (reminder in dao.scheduled()) {
            if (reminder.calendarEventId != null) {
                if (reminder.triggerAtMillis < now) dao.setStatus(reminder.id, ReminderStatus.FIRED.name)
                continue
            }
            if (reminder.triggerAtMillis <= now) {
                Notifications.showReminder(context, reminder.id, reminder.title)
                dao.setStatus(reminder.id, ReminderStatus.FIRED.name)
            } else {
                arm(reminder)
            }
        }
    }

    /** @return true if an exact alarm was scheduled. */
    private fun arm(reminder: ReminderEntity): Boolean {
        val manager = alarmManager ?: return false
        val operation = pendingIntent(reminder.id, reminder.title)
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
        return try {
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, operation)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, operation)
            }
            exact
        } catch (e: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, operation)
            false
        }
    }

    private fun pendingIntent(id: Long, title: String): PendingIntent = PendingIntent.getBroadcast(
        context,
        id.toInt(),
        Intent(context, ReminderAlarmReceiver::class.java)
            .setAction(ReminderAlarmReceiver.ACTION_FIRE)
            .putExtra(ReminderAlarmReceiver.EXTRA_ID, id)
            .putExtra(ReminderAlarmReceiver.EXTRA_TITLE, title),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ReminderEntity.toDomain() = Reminder(
        id = id,
        title = title,
        triggerAtMillis = triggerAtMillis,
        calendarEventId = calendarEventId,
        status = ReminderStatus.entries.firstOrNull { it.name == status } ?: ReminderStatus.SCHEDULED,
        createdAt = createdAt,
    )
}
