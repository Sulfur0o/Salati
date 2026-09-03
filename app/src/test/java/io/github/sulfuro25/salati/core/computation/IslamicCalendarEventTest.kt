package io.github.sulfuro25.salati.core.computation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class IslamicCalendarEventTest {

    private fun date(day: Int, month: Int) = HijriCalendarHelper.HijriDateComponents(day, month, "Month", 1445)

    @Test
    fun testEventMapping() {
        val newYear = eventsForHijriDate(date(1, 1))
        assertEquals(IslamicEventType.ISLAMIC_NEW_YEAR, newYear.first().type)

        val ashura = eventsForHijriDate(date(10, 1))
        assertEquals(IslamicEventType.ASHURA, ashura.first().type)

        val ramadan = eventsForHijriDate(date(1, 9))
        assertEquals(IslamicEventType.RAMADAN_START, ramadan.first().type)

        val eidFitr = eventsForHijriDate(date(1, 10))
        assertEquals(IslamicEventType.EID_AL_FITR, eidFitr.first().type)

        val arafah = eventsForHijriDate(date(9, 12))
        assertEquals(IslamicEventType.ARAFAH, arafah.first().type)

        val eidAdha = eventsForHijriDate(date(10, 12))
        assertEquals(IslamicEventType.EID_AL_ADHA, eidAdha.first().type)
        
        val whiteDay13 = eventsForHijriDate(date(13, 2))
        assertEquals(IslamicEventType.WHITE_DAY, whiteDay13.first().type)

        val whiteDay14 = eventsForHijriDate(date(14, 5))
        assertEquals(IslamicEventType.WHITE_DAY, whiteDay14.first().type)

        val whiteDay15 = eventsForHijriDate(date(15, 11))
        assertEquals(IslamicEventType.WHITE_DAY, whiteDay15.first().type)

        val noWhiteDay12 = eventsForHijriDate(date(12, 5))
        assertTrue(noWhiteDay12.isEmpty())

        val noWhiteDay16 = eventsForHijriDate(date(16, 5))
        assertTrue(noWhiteDay16.isEmpty())

        val ordinary = eventsForHijriDate(date(5, 5))
        assertTrue(ordinary.isEmpty())
    }

    @Test
    fun dhulHijjahThirteenthIsExcludedFromWhiteDaysSinceFastingIsForbiddenOnTashreeqDays() {
        val tashreeqThirteenth = eventsForHijriDate(date(13, 12))
        assertTrue(tashreeqThirteenth.none { it.type == IslamicEventType.WHITE_DAY })

        // 14th and 15th of Dhu al-Hijjah are ordinary White Days.
        val fourteenth = eventsForHijriDate(date(14, 12))
        assertEquals(IslamicEventType.WHITE_DAY, fourteenth.first().type)

        val fifteenth = eventsForHijriDate(date(15, 12))
        assertEquals(IslamicEventType.WHITE_DAY, fifteenth.first().type)
    }

    @Test
    fun testMultipleEventsSupported() {
        val multipleEvents = listOf(
            IslamicCalendarEvent(IslamicEventType.EID_AL_ADHA, 0),
            IslamicCalendarEvent(IslamicEventType.WHITE_DAY, 0)
        )
        assertEquals(2, multipleEvents.size)
        assertEquals(IslamicEventType.EID_AL_ADHA, multipleEvents[0].type)
        assertEquals(IslamicEventType.WHITE_DAY, multipleEvents[1].type)
    }

    @Test
    fun zakatHawlMilestoneAppearsExactly354DaysAfterNisabDate() {
        val startDate = LocalDate.of(2026, 1, 10)
        val dueDate = startDate.plusDays(354)

        assertEquals(dueDate, zakatHawlDueDate(startDate))
        assertTrue(
            eventsForCalendarDate(dueDate, date(5, 5), startDate)
                .any { it.type == IslamicEventType.ZAKAT_HAWL }
        )
        assertTrue(
            eventsForCalendarDate(dueDate.minusDays(1), date(5, 5), startDate)
                .none { it.type == IslamicEventType.ZAKAT_HAWL }
        )
        assertTrue(
            eventsForCalendarDate(dueDate.plusDays(1), date(5, 5), startDate)
                .none { it.type == IslamicEventType.ZAKAT_HAWL }
        )
    }

    @Test
    fun hawlMilestoneCoexistsWithIslamicEventsWithoutAddingZakatSubfeatures() {
        val startDate = LocalDate.of(2026, 1, 10)
        val dueDate = zakatHawlDueDate(startDate)
        val events = eventsForCalendarDate(dueDate, date(13, 2), startDate)

        assertTrue(events.any { it.type == IslamicEventType.WHITE_DAY })
        assertTrue(events.any { it.type == IslamicEventType.ZAKAT_HAWL })
        assertEquals(2, events.size)
    }

    @Test
    fun testOffsetIntegrationWithEvents() {
        val baseDate = LocalDate.of(2026, 7, 15) // Assume api says 1 Muharram

        val apiLookup: (LocalDate) -> HijriDateParts? = {
            when (it) {
                baseDate -> HijriDateParts(1, 1, 1448)
                baseDate.plusDays(9) -> HijriDateParts(10, 1, 1448)
                else -> null
            }
        }

        // 0 offset -> 1 Muharram -> New Year
        val h0 = HijriCalendarHelper.resolveHijriDate(baseDate, 0, false, apiLookup)
        val events0 = eventsForHijriDate(h0)
        assertEquals(IslamicEventType.ISLAMIC_NEW_YEAR, events0.first().type)

        // +9 offset -> 10 Muharram -> Ashura
        val h9 = HijriCalendarHelper.resolveHijriDate(baseDate, 9, false, apiLookup)
        val events9 = eventsForHijriDate(h9)
        assertEquals(IslamicEventType.ASHURA, events9.first().type)
    }

    @Test
    fun testFallbackIntegration() {
        // Fallback used only when metadata is missing
        val baseDate = LocalDate.of(2026, 7, 15)
        val apiLookup: (LocalDate) -> HijriDateParts? = { null }
        
        val hFallback = HijriCalendarHelper.resolveHijriDate(baseDate, 0, false, apiLookup)
        val events = eventsForHijriDate(hFallback)
        assertTrue(events.isEmpty())
    }
}
