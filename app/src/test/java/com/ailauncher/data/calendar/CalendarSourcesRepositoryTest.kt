package com.ailauncher.data.calendar

import android.app.Application
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ailauncher.data.local.AppDatabase
import com.ailauncher.data.reminders.CalendarDataSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Importing, refreshing and removing an .ics calendar, through the real repository and Room. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class CalendarSourcesRepositoryTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    private val repository = CalendarSourcesRepositoryImpl(context, db.calendarFeedDao(), CalendarDataSource(context), OkHttpClient())

    @After
    fun tearDown() = db.close()

    @Test
    fun importsAFileAndMergesItsEvents() = runTest {
        val zone = ZoneId.systemDefault()
        val tomorrow = LocalDate.now(zone).plusDays(1)
        val day = tomorrow.toString().replace("-", "")
        val file = File(context.cacheDir, "timetable.ics").apply {
            writeText(
                "BEGIN:VCALENDAR\r\nX-WR-CALNAME:School timetable\r\n" +
                    "BEGIN:VEVENT\r\nUID:1\r\nSUMMARY:Maths\r\nDTSTART:${day}T090000\r\nDTEND:${day}T100000\r\nRRULE:FREQ=DAILY;COUNT=3\r\nEND:VEVENT\r\n" +
                    "END:VCALENDAR\r\n",
            )
        }
        val feed = repository.importFile(null, Uri.fromFile(file).toString()).getOrThrow()
        assertThat(feed.name).isEqualTo("School timetable")
        assertThat(feed.isFile).isTrue()
        assertThat(feed.eventCount).isEqualTo(3)

        val start = tomorrow.atStartOfDay(zone).toInstant().toEpochMilli()
        val events = repository.feedEventsBetween(start, start + 86_400_000L)
        assertThat(events.map { it.title }).containsExactly("Maths")
        assertThat(events.single().startMillis).isEqualTo(tomorrow.atTime(9, 0).atZone(zone).toInstant().toEpochMilli())

        assertThat(repository.refresh(feed.id).getOrThrow().eventCount).isEqualTo(3)
        repository.remove(feed.id)
        assertThat(repository.feeds.first()).isEmpty()
        assertThat(repository.feedEventsBetween(0, Long.MAX_VALUE)).isEmpty()
    }

    @Test
    fun rejectsFilesAndLinksThatAreNotCalendars() = runTest {
        val file = File(context.cacheDir, "notes.txt").apply { writeText("just some notes") }
        assertThat(repository.importFile(null, Uri.fromFile(file).toString()).isFailure).isTrue()
        assertThat(repository.subscribe("", "ftp://example.com/cal.ics").exceptionOrNull()?.message).contains("https://")
        assertThat(CalendarSourcesRepositoryImpl.normalizeLink("webcal://p01-calendars.icloud.com/published/2/abc")).isEqualTo("https://p01-calendars.icloud.com/published/2/abc")
        assertThat(CalendarSourcesRepositoryImpl.normalizeLink(" https://outlook.live.com/owa/calendar/x/reachcalendar.ics ")).isEqualTo("https://outlook.live.com/owa/calendar/x/reachcalendar.ics")
        assertThat(CalendarSourcesRepositoryImpl.normalizeLink("http://insecure.example.com/a.ics")).isNull()
        assertThat(CalendarSourcesRepositoryImpl.calendarName("BEGIN:VCALENDAR\nX-WR-CALNAME:Holidays in India\n")).isEqualTo("Holidays in India")
    }
}
