package com.sulfuro.salati.core.computation

import com.sulfuro.salati.R
import com.sulfuro.salati.core.zakat.zakatHawlDueDate
import java.time.LocalDate

enum class IslamicEventType {
    ISLAMIC_NEW_YEAR,
    ASHURA,
    RAMADAN_START,
    EID_AL_FITR,
    ARAFAH,
    EID_AL_ADHA,
    WHITE_DAY,
    ZAKAT_HAWL
}

data class IslamicCalendarEvent(
    val type: IslamicEventType,
    val nameRes: Int
)

fun eventsForHijriDate(date: HijriCalendarHelper.HijriDateComponents): List<IslamicCalendarEvent> {
    val events = mutableListOf<IslamicCalendarEvent>()
    
    val day = date.day
    val month = date.monthNumber
    
    when {
        day == 1 && month == 1 -> events.add(IslamicCalendarEvent(IslamicEventType.ISLAMIC_NEW_YEAR, R.string.event_islamic_new_year))
        day == 10 && month == 1 -> events.add(IslamicCalendarEvent(IslamicEventType.ASHURA, R.string.event_ashura))
        day == 1 && month == 9 -> events.add(IslamicCalendarEvent(IslamicEventType.RAMADAN_START, R.string.event_ramadan_start))
        day == 1 && month == 10 -> events.add(IslamicCalendarEvent(IslamicEventType.EID_AL_FITR, R.string.event_eid_al_fitr))
        day == 9 && month == 12 -> events.add(IslamicCalendarEvent(IslamicEventType.ARAFAH, R.string.event_arafah))
        day == 10 && month == 12 -> events.add(IslamicCalendarEvent(IslamicEventType.EID_AL_ADHA, R.string.event_eid_al_adha))
    }
    
    // Voluntary White Days fasting is not observed during Ramadan (month 9, obligatory fasting),
    // and the 11th-13th of Dhu al-Hijjah are Tashreeq days when fasting is forbidden.
    val isRamadan = month == 9
    val isTashreeqDay = month == 12 && day in 11..13
    if (day in 13..15 && !isRamadan && !isTashreeqDay) {
        events.add(IslamicCalendarEvent(IslamicEventType.WHITE_DAY, R.string.event_white_day))
    }
    
    return events
}

fun eventsForCalendarDate(
    gregorianDate: LocalDate,
    hijriDate: HijriCalendarHelper.HijriDateComponents,
    zakatHawlStartDate: LocalDate?
): List<IslamicCalendarEvent> {
    val events = eventsForHijriDate(hijriDate).toMutableList()
    if (zakatHawlStartDate != null &&
        gregorianDate == zakatHawlDueDate(zakatHawlStartDate)
    ) {
        events += IslamicCalendarEvent(IslamicEventType.ZAKAT_HAWL, R.string.event_zakat_hawl)
    }
    return events
}
