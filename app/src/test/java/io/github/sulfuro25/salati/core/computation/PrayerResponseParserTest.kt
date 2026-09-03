package io.github.sulfuro25.salati.core.computation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrayerResponseParserTest {
    @Test
    fun validResponseIsParsed() {
        val result = AladhanPrayerResponseParser.parse(validJson())

        assertTrue(result is PrayerResponseParseResult.Success)
        result as PrayerResponseParseResult.Success
        assertEquals(1, result.data.size)
        assertEquals("15-07-2026", result.data.single().date.gregorian.date)
    }

    @Test
    fun malformedJsonIsParserFailure() {
        assertTrue(
            AladhanPrayerResponseParser.parse("{broken") is PrayerResponseParseResult.Failure
        )
    }

    @Test
    fun unsuccessfulOrEmptyResponseIsParserFailure() {
        val unsuccessful = validJson().replace("\"code\":200", "\"code\":400")
        val empty = """{"code":200,"status":"OK","data":[]}"""

        assertTrue(
            AladhanPrayerResponseParser.parse(unsuccessful) is PrayerResponseParseResult.Failure
        )
        assertTrue(AladhanPrayerResponseParser.parse(empty) is PrayerResponseParseResult.Failure)
    }
}

internal fun validJson(): String = """
    {
      "code":200,
      "status":"OK",
      "data":[{
        "timings":{
          "Fajr":"03:23","Sunrise":"05:46","Dhuhr":"13:48","Asr":"18:06",
          "Sunset":"21:51","Maghrib":"21:51","Isha":"00:05",
          "Midnight":"01:48","Lastthird":"03:07"
        },
        "date":{
          "readable":"15 Jul 2026","timestamp":"1784091600",
          "gregorian":{"date":"15-07-2026","day":"15","month":{"number":7},"year":"2026"}
        }
      }]
    }
""".trimIndent()

internal fun sampleDayData(date: String = "15-07-2026"): AladhanDayData = AladhanDayData(
    timings = AladhanTimings(
        Fajr = "03:23",
        Sunrise = "05:46",
        Dhuhr = "13:48",
        Asr = "18:06",
        Sunset = "21:51",
        Maghrib = "21:51",
        Isha = "00:05",
        Midnight = "01:48",
        Lastthird = "03:07"
    ),
    date = AladhanDate(
        readable = date,
        timestamp = "0",
        gregorian = AladhanGregorianDate(
            date = date,
            day = date.substringBefore("-"),
            month = AladhanMonth(date.substringAfter("-").substringBefore("-").toInt()),
            year = date.substringAfterLast("-")
        )
    )
)
