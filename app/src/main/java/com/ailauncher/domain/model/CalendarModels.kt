package com.ailauncher.domain.model

/** A calendar in Android's calendar storage: Google, Exchange/Outlook, Samsung, local, CalDAV… */
data class DeviceCalendar(
    val id: Long,
    val name: String,
    /** The account it syncs with (an email address, or "Phone" for a local calendar). */
    val account: String,
    val accountType: String,
    val colorArgb: Int,
    val writable: Boolean,
    val primary: Boolean,
)

/** A calendar subscribed to by link (an .ics address) or imported from an .ics file. */
data class CalendarFeed(
    val id: Long,
    val name: String,
    /** The subscription link; null for an imported file. */
    val url: String?,
    val colorArgb: Int,
    val lastSyncedAt: Long?,
    val lastError: String?,
    val eventCount: Int,
) {
    val isFile: Boolean get() = url == null
}
