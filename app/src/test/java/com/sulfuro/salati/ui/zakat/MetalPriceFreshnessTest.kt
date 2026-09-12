package com.sulfuro.salati.ui.zakat

import com.sulfuro.salati.data.settings.CalculationSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a gold/silver quote is worth replacing.
 *
 * The screen that asks this is torn down and rebuilt on every tab switch, so the refresh
 * it runs on first composition used to run on every visit: two requests to a third-party
 * CDN, a spinner and a settings write, for figures the source republishes once a day.
 */
class MetalPriceFreshnessTest {

    private val now = 1_760_000_000_000L
    private val hour = 60L * 60 * 1000

    private fun settings(
        gold: Double = 70.0,
        silver: Double = 0.8,
        currency: String = "EUR",
        pricesCurrency: String = "EUR",
        updatedAt: Long = now
    ) = CalculationSettings().let {
        it.copy(
            zakat = it.zakat.copy(
                goldPrice = gold,
                silverPrice = silver,
                currencyCode = currency,
                pricesCurrencyCode = pricesCurrency,
                pricesUpdatedAt = updatedAt
            )
        )
    }

    @Test
    fun aQuoteFetchedMomentsAgoIsNotRefetched() {
        assertFalse(metalPricesAreStale(settings(updatedAt = now - 5 * 60 * 1000), now))
    }

    @Test
    fun aQuoteFromEarlierTodayIsStillGoodEnough() {
        assertFalse(metalPricesAreStale(settings(updatedAt = now - 5 * hour), now))
    }

    @Test
    fun aQuoteOlderThanTheFreshnessWindowIsReplaced() {
        assertTrue(metalPricesAreStale(settings(updatedAt = now - 7 * hour), now))
    }

    /** Prices are quoted per currency, so one fetched in another currency is not an answer. */
    @Test
    fun aQuoteInADifferentCurrencyIsAlwaysReplaced() {
        assertTrue(
            metalPricesAreStale(settings(currency = "GBP", pricesCurrency = "EUR"), now)
        )
    }

    @Test
    fun havingNeverFetchedCountsAsStale() {
        assertTrue(metalPricesAreStale(settings(updatedAt = 0L, pricesCurrency = ""), now))
    }

    /**
     * The defaults are placeholders, not a quote. Shipping them as if they were current
     * would put a made-up gold price behind a zakat figure.
     */
    @Test
    fun aMissingPriceIsStaleWhateverTheTimestampSays() {
        assertTrue(metalPricesAreStale(settings(gold = 0.0), now))
        assertTrue(metalPricesAreStale(settings(silver = 0.0), now))
    }
}
