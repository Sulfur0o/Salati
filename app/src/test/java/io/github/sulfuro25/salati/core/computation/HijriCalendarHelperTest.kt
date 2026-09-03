package io.github.sulfuro25.salati.core.computation

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.util.TimeZone

@RunWith(AndroidJUnit4::class)
@Config(sdk = [24], manifest = Config.NONE)
class HijriCalendarHelperTest {
    private val date = LocalDate.of(2026, 7, 15)

    @Test
    fun knownDateAndOffsetsRemainUnchanged() {
        assertEquals(
            HijriCalendarHelper.HijriDateComponents(1, 2, "Safar", 1448),
            HijriCalendarHelper.getHijriDate(date, 0)
        )
        assertEquals(
            HijriCalendarHelper.HijriDateComponents(2, 2, "Safar", 1448),
            HijriCalendarHelper.getHijriDate(date, 1)
        )
        assertEquals(
            HijriCalendarHelper.HijriDateComponents(29, 1, "Muharram", 1448),
            HijriCalendarHelper.getHijriDate(date, -1)
        )
    }

    @Test
    fun afterMaghribAddsExactlyOneHijriDay() {
        assertEquals(
            HijriCalendarHelper.getHijriDate(date, 1, false),
            HijriCalendarHelper.getHijriDate(date, 0, true)
        )
    }

    @Test
    fun deviceTimezoneCannotAffectLocalDateConversion() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"))
            val honolulu = HijriCalendarHelper.getHijriDate(date, 0)
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            val tokyo = HijriCalendarHelper.getHijriDate(date, 0)
            assertEquals(honolulu, tokyo)
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
