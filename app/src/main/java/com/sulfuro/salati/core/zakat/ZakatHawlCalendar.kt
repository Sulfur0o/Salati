package com.sulfuro.salati.core.zakat

import android.content.Intent
import android.provider.CalendarContract
import java.time.LocalDate
import java.time.ZoneId

const val ZAKAT_HAWL_DAYS = 354L

fun zakatHawlDueDate(startDate: LocalDate): LocalDate = startDate.plusDays(ZAKAT_HAWL_DAYS)

/**
 * Hands the Hawl review off to whatever calendar app the user already uses.
 *
 * [Intent.ACTION_INSERT] opens the calendar's own "new event" editor pre-filled, so
 * Salati never needs the READ_CALENDAR/WRITE_CALENDAR permissions - the user confirms
 * the event in an app they trust.
 */
object ZakatHawlCalendar {

    /**
     * Builds the pre-filled all-day event for the Hawl due date.
     *
     * @param dueDate the day the lunar year completes, i.e. [zakatHawlDueDate].
     * @param zoneId zone used to turn the all-day date into the epoch millis the
     *   calendar provider expects.
     */
    fun buildInsertIntent(
        title: String,
        description: String,
        dueDate: LocalDate,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Intent {
        val beginMillis = dueDate.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val endMillis = dueDate.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()

        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        }
    }

    /**
     * The Hawl due date for an assessment made on [from], one Hijri lunar year (354
     * days) later. Mirrors [zakatHawlDueDate] so the calendar entry and the in-app
     * milestone can never drift apart.
     */
    fun dueDateFrom(from: LocalDate): LocalDate = zakatHawlDueDate(from)
}
