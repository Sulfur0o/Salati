package com.sulfuro.salati.core.computation

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.ZoneId

/**
 * Switching tabs tears a screen down and re-runs its loader, so without a memory cache
 * every return to Daily or Monthly re-reads a file and re-parses a month of JSON that has
 * not changed. These tests cover that the cache serves those repeats, stays bounded, and
 * never lets a month computed during an outage outlive it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class PrayerMemoryCacheTest {

    private val request = PrayerMonthRequest(2026, 7, 3, 0, "1", 50.8503, 4.3517)
    private val days = LocalPrayerTimeCalculator.calculateMonth(request, ZoneId.of("Europe/Brussels"))

    private fun requestFor(month: Int) = request.copy(month = month)

    @Test
    fun returnsWhatWasStoredForTheSameRequest() {
        val cache = LruPrayerMemoryCache()
        cache.put(request, days)

        assertSame(days, cache.get(request))
        // A different month, method or place is a different question.
        assertNull(cache.get(requestFor(8)))
        assertNull(cache.get(request.copy(methodId = 2)))
        assertNull(cache.get(request.copy(latitude = 33.5731)))
    }

    @Test
    fun evictsTheLeastRecentlyUsedMonthOnceFull() {
        val cache = LruPrayerMemoryCache(maxEntries = 3)
        cache.put(requestFor(1), days)
        cache.put(requestFor(2), days)
        cache.put(requestFor(3), days)

        // Touching January makes February the coldest entry.
        cache.get(requestFor(1))
        cache.put(requestFor(4), days)

        assertEquals(3, cache.size())
        assertNull("February should have been evicted", cache.get(requestFor(2)))
        assertTrue(cache.get(requestFor(1)) != null)
        assertTrue(cache.get(requestFor(4)) != null)
    }

    @Test
    fun neverStoresAnEmptyMonth() {
        val cache = LruPrayerMemoryCache()
        cache.put(request, emptyList())

        // An empty list would otherwise be cached as though it were a real answer and
        // suppress the retry that would have fetched a real one.
        assertNull(cache.get(request))
        assertEquals(0, cache.size())
    }

    @Test
    fun invalidateAndClearRemoveEntries() {
        val cache = LruPrayerMemoryCache()
        cache.put(requestFor(1), days)
        cache.put(requestFor(2), days)

        cache.invalidate(requestFor(1))
        assertNull(cache.get(requestFor(1)))
        assertTrue(cache.get(requestFor(2)) != null)

        cache.clear()
        assertEquals(0, cache.size())
    }

    @Test
    fun aWarmMonthIsServedWithoutTouchingDiskOrNetwork() {
        val cache = LruPrayerMemoryCache()
        var cacheReads = 0
        var remoteCalls = 0
        val disk = object : PrayerCacheDataSource {
            override fun read(request: PrayerMonthRequest): PrayerCacheReadResult {
                cacheReads++
                return PrayerCacheReadResult.Missing
            }
            override fun write(request: PrayerMonthRequest, rawJson: String) =
                PrayerCacheWriteResult.Success
            override fun invalidate(request: PrayerMonthRequest) =
                PrayerCacheInvalidationResult.Success
        }
        val remote = PrayerRemoteDataSource {
            remoteCalls++
            PrayerHttpResponse(200, "{}")
        }
        val parser = PrayerResponseParser { PrayerResponseParseResult.Success(days) }

        repeat(3) {
            PrayerRepository.getMonthlyPrayers(
                request = request,
                requireCacheOnly = false,
                cacheDataSource = disk,
                remoteDataSource = remote,
                responseParser = parser,
                memoryCache = cache
            )
        }

        // The first pass fills the cache; the next two are answered from memory.
        assertEquals(1, cacheReads)
        assertEquals(1, remoteCalls)
    }

    @Test
    fun aMonthComputedDuringAnOutageIsNotRemembered() {
        val cache = LruPrayerMemoryCache()
        val disk = object : PrayerCacheDataSource {
            override fun read(request: PrayerMonthRequest) = PrayerCacheReadResult.Missing
            override fun write(request: PrayerMonthRequest, rawJson: String) =
                PrayerCacheWriteResult.Success
            override fun invalidate(request: PrayerMonthRequest) =
                PrayerCacheInvalidationResult.Success
        }
        val offline = PrayerRemoteDataSource { throw java.io.IOException("offline") }

        val result = PrayerRepository.getMonthlyPrayers(
            request = request,
            requireCacheOnly = false,
            cacheDataSource = disk,
            remoteDataSource = offline,
            responseParser = PrayerResponseParser { PrayerResponseParseResult.Success(days) },
            fallbackZoneId = ZoneId.of("Europe/Brussels"),
            memoryCache = cache
        )

        assertTrue(result is MonthlyPrayerResult.Success)
        assertEquals(PrayerDataOrigin.ON_DEVICE, (result as MonthlyPrayerResult.Success).origin)
        // Caching it would let a stand-in outlive the outage it was made for, and keep
        // the app from picking up the real month once the network returns.
        assertEquals(0, cache.size())
    }
}
