package com.ailauncher.data.reminders

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.ailauncher.domain.model.DeviceCalendar
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

data class CalendarEvent(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val location: String?,
)

/** Thin wrapper over CalendarContract for writing reminders and reading today's agenda. */
@Singleton
class CalendarDataSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun canRead(): Boolean = granted(Manifest.permission.READ_CALENDAR)

    fun canWrite(): Boolean = granted(Manifest.permission.WRITE_CALENDAR) && canRead()

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Every calendar on the phone, of every account (Google, Exchange/Outlook, Samsung, local…). */
    fun calendars(): List<DeviceCalendar> {
        if (!canRead()) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        return try {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val type = c.getString(3).orEmpty()
                        add(
                            DeviceCalendar(
                                id = c.getLong(0),
                                name = c.getString(1)?.takeIf { it.isNotBlank() } ?: "Calendar",
                                account = if (type == CalendarContract.ACCOUNT_TYPE_LOCAL) "On this phone" else c.getString(2).orEmpty(),
                                accountType = type,
                                colorArgb = c.getInt(4),
                                writable = c.getInt(5) >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                                primary = c.getInt(6) == 1,
                            ),
                        )
                    }
                }
            }.orEmpty().sortedWith(compareBy({ it.account.lowercase() }, { !it.primary }, { it.name.lowercase() }))
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    /**
     * The calendar new reminders go to: [preferred] when it still exists and can be written,
     * otherwise the primary writable synced calendar.
     */
    fun primaryWritableCalendarId(preferred: Long? = null): Long? {
        if (!canWrite()) return null
        if (preferred != null && calendars().any { it.id == preferred && it.writable }) return preferred
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.ACCOUNT_TYPE,
        )
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1 AND " +
            "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"
        return try {
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, selection, null, null)?.use { c ->
                var fallback: Long? = null
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val primary = c.getInt(1) == 1
                    val local = c.getString(2) == CalendarContract.ACCOUNT_TYPE_LOCAL
                    if (primary && !local) return id
                    if (fallback == null && !local) fallback = id
                }
                fallback
            }
        } catch (e: SecurityException) {
            null
        }
    }

    /** Inserts an event with a pop-up reminder at its start time. Returns the event id. */
    fun insertReminderEvent(calendarId: Long, title: String, startMillis: Long): Long? = try {
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, "Created by your AI Launcher assistant")
            put(CalendarContract.Events.DTSTART, startMillis)
            put(CalendarContract.Events.DTEND, startMillis + EVENT_DURATION_MS)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 1)
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        val eventId = ContentUris.parseId(uri)
        context.contentResolver.insert(
            CalendarContract.Reminders.CONTENT_URI,
            ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.MINUTES, 0)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
            },
        )
        eventId
    } catch (e: Exception) {
        Log.w(TAG, "Could not write calendar event", e)
        null
    }

    fun deleteEvent(eventId: Long) {
        runCatching {
            context.contentResolver.delete(ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId), null, null)
        }
    }

    /**
     * Event instances (recurring events expanded) overlapping [startMillis, endMillis), from every
     * calendar except [hiddenCalendarIds].
     */
    fun eventsBetween(startMillis: Long, endMillis: Long, hiddenCalendarIds: Set<Long> = emptySet()): List<CalendarEvent> {
        if (!canRead()) return emptyList()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, startMillis)
            ContentUris.appendId(it, endMillis)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_ID,
        )
        return try {
            context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
                buildList {
                    while (c.moveToNext() && size < MAX_EVENTS) {
                        if (c.getLong(5) in hiddenCalendarIds) continue
                        add(
                            CalendarEvent(
                                title = c.getString(0)?.takeIf { it.isNotBlank() } ?: "(No title)",
                                startMillis = c.getLong(1),
                                endMillis = c.getLong(2),
                                allDay = c.getInt(3) == 1,
                                location = c.getString(4)?.takeIf { it.isNotBlank() },
                            ),
                        )
                    }
                }
            } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    private companion object {
        const val TAG = "CalendarDataSource"
        const val EVENT_DURATION_MS = 15 * 60 * 1000L
        const val MAX_EVENTS = 25
    }
}
