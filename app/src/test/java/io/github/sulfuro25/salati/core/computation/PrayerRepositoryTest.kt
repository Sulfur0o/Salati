package io.github.sulfuro25.salati.core.computation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.sulfuro25.salati.data.settings.CalculationSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class PrayerRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun aladhanMethodIdsAreCorrectAndUnknownMethodIsRejected() {
        assertEquals(5, PrayerRepository.getAladhanMethodId("EGYPT"))
        assertEquals(1, PrayerRepository.getAladhanMethodId("KARACHI"))
        assertEquals(3, PrayerRepository.getAladhanMethodId("MUSLIM_WORLD_LEAGUE"))
        assertEquals(2, PrayerRepository.getAladhanMethodId("ISNA"))
        assertEquals(4, PrayerRepository.getAladhanMethodId("UMM_AL_QURA"))
        assertEquals(9, PrayerRepository.getAladhanMethodId("KUWAIT"))
        assertEquals(10, PrayerRepository.getAladhanMethodId("QATAR"))
        assertEquals(16, PrayerRepository.getAladhanMethodId("DUBAI"))
        assertEquals(11, PrayerRepository.getAladhanMethodId("SINGAPORE"))
        assertEquals(15, PrayerRepository.getAladhanMethodId("MOON_SIGHTING"))
        assertEquals(null, PrayerRepository.getAladhanMethodId("UNKNOWN"))
    }

    @Test
    fun apiUrlUsesRequestedCoordinatesMadhabAndHighLatitudeParameters() {
        val url = PrayerRepository.buildApiUrl(2026, 7, 5, 1, "3", 33.5731, -7.5898)
        val query = java.net.URI(url).query
            .split("&")
            .associate { part -> part.substringBefore("=") to part.substringAfter("=") }

        assertEquals("33.5731", query["latitude"])
        assertEquals("-7.5898", query["longitude"])
        assertEquals("5", query["method"])
        assertEquals("1", query["school"])
        assertEquals("3", query["latitudeAdjustmentMethod"])
    }

    @Test
    fun cacheKeyContainsEveryCalculationDimension() {
        val base = PrayerRepository.getCacheFileName(2026, 7, 3, 0, "1", 50.8503, 4.3517)
        assertTrue(base.contains("lat50p850_lon4p352"))
        assertTrue(base.contains("2026_7"))
        assertTrue(base.contains("m3"))
        assertTrue(base.contains("s0"))
        assertTrue(base.contains("h1"))
        assertNotEquals(base, PrayerRepository.getCacheFileName(2027, 7, 3, 0, "1", 50.8503, 4.3517))
        assertNotEquals(base, PrayerRepository.getCacheFileName(2026, 8, 3, 0, "1", 50.8503, 4.3517))
        assertNotEquals(base, PrayerRepository.getCacheFileName(2026, 7, 5, 0, "1", 50.8503, 4.3517))
        assertNotEquals(base, PrayerRepository.getCacheFileName(2026, 7, 3, 1, "1", 50.8503, 4.3517))
        assertNotEquals(base, PrayerRepository.getCacheFileName(2026, 7, 3, 0, "3", 50.8503, 4.3517))
        assertNotEquals(base, PrayerRepository.getCacheFileName(2026, 7, 3, 0, "1", 33.5731, -7.5898))
    }

    @Test
    fun cacheOnlyMissNeverOpensNetworkPath() = runBlocking {
        deleteDefaultCache(2097, 11)
        var networkCalls = 0
        val result = PrayerRepository.getMonthlyPrayers(
            context = context,
            settings = CalculationSettings(),
            year = 2097,
            month = 11,
            requireCacheOnly = true,
            apiClient = PrayerApiClient {
                networkCalls++
                throw IOException("network path must not open")
            }
        )

        assertTrue(result is MonthlyPrayerResult.CacheMiss)
        assertEquals(0, networkCalls)
    }

    /**
     * Being offline used to leave the user with no prayer times and no alarms. The error
     * taxonomy still exists underneath - [PrayerRepositorySeparationTest] pins it - but it
     * is no longer what the app is handed, because a computed month beats nothing.
     */
    @Test
    fun networkFailureFallsBackToTimesComputedOnDevice() = runBlocking {
        deleteDefaultCache(2096, 10)
        val result = PrayerRepository.getMonthlyPrayers(
            context = context,
            settings = CalculationSettings(),
            year = 2096,
            month = 10,
            apiClient = PrayerApiClient { throw IOException("offline") }
        )

        assertTrue(result is MonthlyPrayerResult.Success)
        val success = result as MonthlyPrayerResult.Success
        assertEquals(PrayerDataOrigin.ON_DEVICE, success.origin)
        assertEquals(31, success.data.size)
        // Usable, not merely present: every day maps to an ordered set of instants.
        val times = PrayerRepository.parsePrayerTimes(success.data.first(), CalculationSettings())
        assertTrue(times.fajr.isBefore(times.sunrise))
        assertTrue(times.dhuhr.isBefore(times.asr))
        assertTrue(times.asr.isBefore(times.maghrib))
        assertTrue(times.maghrib.isBefore(times.isha))
    }

    @Test
    fun serverFailuresAlsoFallBackAndAreNeverCached() = runBlocking {
        deleteDefaultCache(2095, 9)
        for (statusCode in listOf(503, 429, 400)) {
            val result = PrayerRepository.getMonthlyPrayers(
                context,
                CalculationSettings(),
                2095,
                9,
                apiClient = PrayerApiClient { PrayerHttpResponse(statusCode, "") }
            )
            assertTrue("HTTP $statusCode should still yield times", result is MonthlyPrayerResult.Success)
            assertEquals(PrayerDataOrigin.ON_DEVICE, (result as MonthlyPrayerResult.Success).origin)
        }

        // A computed month must never be written to disk, or it would shadow the real
        // one the next successful fetch brings back.
        val cacheFile = PrayerRepository.getCacheFile(
            context, 2095, 9, 3, 0, "3",
            CalculationSettings().latitude, CalculationSettings().longitude
        )
        assertTrue("computed months must not be cached", !cacheFile.exists())
    }

    @Test
    fun cacheOnlyCallersStillSeeTheMissSoTheyGoAndFetch() = runBlocking {
        deleteDefaultCache(2094, 8)
        // The cheap path exists to find out whether authoritative data is already on
        // disk. Answering it with a computed month would stop the network refresh that
        // the miss is supposed to trigger.
        val result = PrayerRepository.getMonthlyPrayers(
            context = context,
            settings = CalculationSettings(),
            year = 2094,
            month = 8,
            requireCacheOnly = true,
            apiClient = PrayerApiClient { throw IOException("network path must not open") }
        )

        assertTrue(result is MonthlyPrayerResult.CacheMiss)
    }

    /**
     * The widget is the exception: it renders times and never decides whether to fetch
     * them, so answering it from the on-device calculation cannot suppress a refresh. It
     * runs inside a broadcast's goAsync window, which is why it declines the network and
     * still needs an answer.
     */
    @Test
    fun aDisplayOnlyCallerCanTakeAComputedMonthWithoutOpeningTheNetwork() = runBlocking {
        deleteDefaultCache(2093, 7)
        var networkCalls = 0
        val result = PrayerRepository.getMonthlyPrayers(
            context = context,
            settings = CalculationSettings(),
            year = 2093,
            month = 7,
            requireCacheOnly = true,
            apiClient = PrayerApiClient {
                networkCalls++
                throw IOException("network path must not open")
            },
            allowOnDeviceFallback = true
        )

        assertTrue(result is MonthlyPrayerResult.Success)
        assertEquals(PrayerDataOrigin.ON_DEVICE, (result as MonthlyPrayerResult.Success).origin)
        assertEquals(31, result.data.size)
        assertEquals(0, networkCalls)
    }

    @Test
    fun unsupportedConfigurationIsRejectedBeforeNetwork() = runBlocking {
        var networkCalls = 0
        val result = PrayerRepository.getMonthlyPrayers(
            context,
            CalculationSettings(calculationMethod = "UNSUPPORTED"),
            2026,
            7,
            apiClient = PrayerApiClient {
                networkCalls++
                PrayerHttpResponse(200, "")
            }
        )

        assertTrue(result is MonthlyPrayerResult.InvalidConfiguration)
        assertEquals(0, networkCalls)
    }

    @Test
    fun savedTimezoneIsUsedAsFallbackWhenApiOmitsMeta() {
        val dayData = sampleDayData()
        val brussels = PrayerRepository.parsePrayerTimes(
            dayData,
            CalculationSettings(timezoneId = "Europe/Brussels")
        )
        val newYork = PrayerRepository.parsePrayerTimes(
            dayData,
            CalculationSettings(timezoneId = "America/New_York")
        )

        assertNotEquals(brussels, newYork)
    }

    @Test
    fun apiSuppliedMetaTimezoneOverridesSettingsFallback() {
        val dayData = sampleDayData().copy(meta = AladhanMeta(timezone = "Africa/Casablanca"))
        val viaMeta = PrayerRepository.parsePrayerTimes(
            dayData,
            CalculationSettings(timezoneId = "Europe/Brussels")
        )
        val viaExplicitZone = SalatiPrayerTimeMapper.map(dayData, java.time.ZoneId.of("Europe/Brussels"))

        assertEquals(viaExplicitZone, viaMeta)
    }

    @Test
    fun indexPrayerDataByDateKeysRowsByTheirOwnReportedDateNotArrayPosition() {
        val day1 = sampleDayData("01-07-2026")
        val day2 = sampleDayData("02-07-2026")
        val day3 = sampleDayData("03-07-2026")

        // Deliberately out of order and with a duplicate to prove positional indexing
        // isn't used: a naive `list[dayOfMonth - 1]` lookup would misassociate every
        // date once a row is missing, duplicated, or reordered like this.
        val outOfOrderWithDuplicate = listOf(day2, day1, day1, day3)

        val indexed = PrayerRepository.indexPrayerDataByDate(outOfOrderWithDuplicate)

        assertEquals(3, indexed.size)
        assertEquals(day1, indexed[java.time.LocalDate.of(2026, 7, 1)])
        assertEquals(day2, indexed[java.time.LocalDate.of(2026, 7, 2)])
        assertEquals(day3, indexed[java.time.LocalDate.of(2026, 7, 3)])
        assertEquals(null, indexed[java.time.LocalDate.of(2026, 7, 4)])
    }

    @Test
    fun indexPrayerDataByDateSkipsRowsWithUnparseableDates() {
        val valid = sampleDayData("01-07-2026")
        val malformed = valid.copy(date = valid.date.copy(gregorian = valid.date.gregorian.copy(date = "not-a-date")))

        val indexed = PrayerRepository.indexPrayerDataByDate(listOf(valid, malformed))

        assertEquals(1, indexed.size)
        assertEquals(valid, indexed[java.time.LocalDate.of(2026, 7, 1)])
    }

    private fun deleteDefaultCache(year: Int, month: Int) {
        PrayerRepository.getCacheFile(context, year, month, 3, 0, "1", 50.8503, 4.3517).delete()
    }
}
