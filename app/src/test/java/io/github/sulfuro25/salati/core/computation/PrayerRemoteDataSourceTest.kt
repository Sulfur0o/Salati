package io.github.sulfuro25.salati.core.computation

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URI

class PrayerRemoteDataSourceTest {
    @Test
    fun urlUsesRequestedCoordinatesAndAladhanParameters() {
        var capturedUrl = ""
        val source = AladhanPrayerRemoteDataSource { url ->
            capturedUrl = url
            PrayerHttpResponse(200, "body")
        }

        val response = source.fetchMonth(PrayerMonthRequest(2027, 12, 5, 1, "3", 50.8503, 4.3517))
        val query = URI(capturedUrl).query.split("&").associate {
            it.substringBefore("=") to it.substringAfter("=")
        }

        assertEquals(200, response.statusCode)
        assertEquals("/v1/calendar/2027/12", URI(capturedUrl).path)
        assertEquals("50.8503", query["latitude"])
        assertEquals("4.3517", query["longitude"])
        assertEquals("5", query["method"])
        assertEquals("1", query["school"])
        assertEquals("3", query["latitudeAdjustmentMethod"])
    }
}
