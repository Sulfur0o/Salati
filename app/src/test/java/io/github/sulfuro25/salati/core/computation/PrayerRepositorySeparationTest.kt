package io.github.sulfuro25.salati.core.computation

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class PrayerRepositorySeparationTest {
    private val request = PrayerMonthRequest(2026, 7, 3, 0, "1", 50.8503, 4.3517)
    private val days = listOf(sampleDayData())

    @Test
    fun cacheHitSkipsRemoteAndUsesInjectedParser() {
        val cache = FakeCache(readResult = PrayerCacheReadResult.Success("cached"))
        val parserInputs = mutableListOf<String>()
        var remoteCalls = 0

        val result = runRepository(
            cache = cache,
            remote = PrayerRemoteDataSource {
                remoteCalls++
                PrayerHttpResponse(200, "remote")
            },
            parser = PrayerResponseParser { raw ->
                parserInputs += raw
                PrayerResponseParseResult.Success(days)
            }
        )

        assertEquals(MonthlyPrayerResult.Success(days), result)
        assertEquals(listOf("cached"), parserInputs)
        assertEquals(0, remoteCalls)
    }

    @Test
    fun cacheOnlyMissNeverInvokesRemote() {
        var remoteCalls = 0
        val result = runRepository(
            cache = FakeCache(PrayerCacheReadResult.Missing),
            remote = PrayerRemoteDataSource {
                remoteCalls++
                PrayerHttpResponse(200, "remote")
            },
            requireCacheOnly = true
        )

        assertEquals(MonthlyPrayerResult.CacheMiss, result)
        assertEquals(0, remoteCalls)
    }

    @Test
    fun cacheMissInvokesRemoteAndUsesSameParserBoundary() {
        val cache = FakeCache(PrayerCacheReadResult.Missing)
        val inputs = mutableListOf<String>()
        val parser = PrayerResponseParser { raw ->
            inputs += raw
            PrayerResponseParseResult.Success(days)
        }

        val remoteResult = runRepository(cache, parser = parser)
        val cacheResult = runRepository(
            FakeCache(PrayerCacheReadResult.Success("cached")),
            parser = parser
        )

        assertEquals(MonthlyPrayerResult.Success(days), remoteResult)
        assertEquals(MonthlyPrayerResult.Success(days), cacheResult)
        assertEquals(listOf("remote", "cached"), inputs)
        assertEquals(listOf("remote"), cache.writes)
    }

    @Test
    fun invalidCacheOnlyDataIsInvalidatedAndClassifiedAsCachedData() {
        val parseFailure = IllegalArgumentException("bad cache")
        val cache = FakeCache(PrayerCacheReadResult.Success("invalid"))

        val result = runRepository(
            cache = cache,
            requireCacheOnly = true,
            parser = PrayerResponseParser { PrayerResponseParseResult.Failure(parseFailure) }
        )

        assertTrue(result is MonthlyPrayerResult.InvalidCachedData)
        assertEquals(1, cache.invalidations)
    }

    @Test
    fun invalidNetworkPathCacheIsInvalidatedBeforeRemoteFallback() {
        val events = mutableListOf<String>()
        val cache = FakeCache(PrayerCacheReadResult.Success("invalid"), events = events)

        val result = runRepository(
            cache = cache,
            remote = PrayerRemoteDataSource {
                events += "remote"
                PrayerHttpResponse(200, "valid")
            },
            parser = PrayerResponseParser { raw ->
                if (raw == "invalid") PrayerResponseParseResult.Failure(Exception("bad"))
                else PrayerResponseParseResult.Success(days)
            }
        )

        assertEquals(MonthlyPrayerResult.Success(days), result)
        assertTrue(events.indexOf("invalidate") < events.indexOf("remote"))
        assertEquals(1, cache.reads)
    }

    @Test
    fun invalidationFailureIsObservableAndRemoteFallbackStillProceeds() {
        val failure = IOException("cannot delete")
        val cache = FakeCache(
            readResult = PrayerCacheReadResult.Success("invalid"),
            invalidationResult = PrayerCacheInvalidationResult.Failure(failure)
        )

        val result = runRepository(
            cache = cache,
            parser = PrayerResponseParser { raw ->
                if (raw == "invalid") PrayerResponseParseResult.Failure(Exception("bad"))
                else PrayerResponseParseResult.Success(days)
            }
        )

        assertEquals(MonthlyPrayerResult.Success(days), result)
        assertEquals(PrayerCacheInvalidationResult.Failure(failure), cache.lastInvalidationResult)
        assertEquals(1, cache.reads)
    }

    @Test
    fun validRemoteDataIsReturnedWhenCacheWriteFails() {
        val cache = FakeCache(
            readResult = PrayerCacheReadResult.Missing,
            writeResult = PrayerCacheWriteResult.Failure(IOException("disk full"))
        )

        val result = runRepository(cache)

        assertEquals(MonthlyPrayerResult.Success(days), result)
        assertEquals(listOf("remote"), cache.writes)
    }

    @Test
    fun invalidRemoteJsonIsClassifiedAsApiFailure() {
        val failure = IllegalArgumentException("bad remote")
        val result = runRepository(
            FakeCache(PrayerCacheReadResult.Missing),
            parser = PrayerResponseParser { PrayerResponseParseResult.Failure(failure) }
        )

        assertEquals(MonthlyPrayerResult.InvalidApiResponse(failure), result)
    }

    @Test
    fun allTransportAndHttpClassificationsRemainTyped() {
        val cache = FakeCache(PrayerCacheReadResult.Missing)
        val temporary = runRepository(cache, PrayerRemoteDataSource { throw IOException("offline") })
        val retryable = runRepository(cache, PrayerRemoteDataSource { PrayerHttpResponse(503, "") })
        val limited = runRepository(cache, PrayerRemoteDataSource { PrayerHttpResponse(429, "") })
        val permanent = runRepository(cache, PrayerRemoteDataSource { PrayerHttpResponse(404, "") })
        val unusual = runRepository(cache, PrayerRemoteDataSource { PrayerHttpResponse(302, "") })

        assertTrue(temporary is MonthlyPrayerResult.TemporaryNetworkFailure)
        assertEquals(MonthlyPrayerResult.RetryableServerFailure(503), retryable)
        assertEquals(MonthlyPrayerResult.RetryableServerFailure(429), limited)
        assertEquals(MonthlyPrayerResult.PermanentHttpFailure(404), permanent)
        assertEquals(MonthlyPrayerResult.PermanentHttpFailure(302), unusual)
    }

    private fun runRepository(
        cache: FakeCache,
        remote: PrayerRemoteDataSource = PrayerRemoteDataSource {
            PrayerHttpResponse(200, "remote")
        },
        requireCacheOnly: Boolean = false,
        parser: PrayerResponseParser = PrayerResponseParser {
            PrayerResponseParseResult.Success(days)
        }
    ): MonthlyPrayerResult {
        return PrayerRepository.getMonthlyPrayers(
            request,
            requireCacheOnly,
            cache,
            remote,
            parser
        )
    }

    private class FakeCache(
        private val readResult: PrayerCacheReadResult,
        private val writeResult: PrayerCacheWriteResult = PrayerCacheWriteResult.Success,
        private val invalidationResult: PrayerCacheInvalidationResult =
            PrayerCacheInvalidationResult.Success,
        private val events: MutableList<String> = mutableListOf()
    ) : PrayerCacheDataSource {
        var reads = 0
        var invalidations = 0
        var lastInvalidationResult: PrayerCacheInvalidationResult? = null
        val writes = mutableListOf<String>()

        override fun read(request: PrayerMonthRequest): PrayerCacheReadResult {
            reads++
            events += "read"
            return readResult
        }

        override fun write(
            request: PrayerMonthRequest,
            rawJson: String
        ): PrayerCacheWriteResult {
            writes += rawJson
            events += "write"
            return writeResult
        }

        override fun invalidate(request: PrayerMonthRequest): PrayerCacheInvalidationResult {
            invalidations++
            events += "invalidate"
            lastInvalidationResult = invalidationResult
            return invalidationResult
        }
    }
}
