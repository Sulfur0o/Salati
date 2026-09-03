package io.github.sulfuro25.salati.core.computation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetalsPriceRepositoryTest {

    @Test
    fun endpointsAreRequestedForTheSelectedCurrencyWithAFallbackHost() {
        val endpoints = MetalsPriceRepository.endpointsFor("USD")

        assertEquals(2, endpoints.size)
        // Currency must be lowercased into the path, and must follow the user's selection
        // rather than being pinned to EUR.
        assertTrue(endpoints.all { it.endsWith("/currencies/usd.json") })
        assertTrue(endpoints[0].contains("cdn.jsdelivr.net"))
        assertTrue(endpoints[1].contains("currency-api.pages.dev"))
    }

    @Test
    fun pricesAreParsedFromTheCurrencyKeyedPayload() {
        // 1 EUR = 0.00032 troy oz of gold -> 3125 EUR/oz -> ~100.47 EUR/g
        val body = """
            {"date":"2026-07-18","eur":{"xau":0.00032,"xag":0.0268,"usd":1.09}}
        """.trimIndent()

        val prices = MetalsPriceRepository.parsePrices(body, "EUR")

        assertNotNull(prices)
        prices!!
        assertEquals("EUR", prices.currencyCode)
        assertEquals("2026-07-18", prices.rateDate)
        assertEquals((1.0 / 0.00032) / 31.1034768, prices.goldPricePerGram, 1e-9)
        assertEquals((1.0 / 0.0268) / 31.1034768, prices.silverPricePerGram, 1e-9)
        assertTrue(prices.fetchedAtMillis > 0L)
    }

    @Test
    fun nonEuroCurrencyPayloadIsParsedFromItsOwnKey() {
        val body = """
            {"date":"2026-07-18","mad":{"xau":0.0000305,"xag":0.00256}}
        """.trimIndent()

        val prices = MetalsPriceRepository.parsePrices(body, "mad")

        assertNotNull(prices)
        assertEquals("MAD", prices!!.currencyCode)
        assertEquals((1.0 / 0.0000305) / 31.1034768, prices.goldPricePerGram, 1e-9)
    }

    @Test
    fun fullPrecisionIsRetainedSoSilverDoesNotLoseAccuracy() {
        val body = """{"date":"2026-07-18","eur":{"xau":0.00032,"xag":0.0268}}"""

        val prices = MetalsPriceRepository.parsePrices(body, "EUR")!!

        // Rounding silver to 2 decimals used to cost up to ~3 currency units across the
        // 595g silver Nisab; the parsed value must keep more precision than that.
        val roundedToCents = Math.round(prices.silverPricePerGram * 100.0) / 100.0
        assertTrue(prices.silverPricePerGram != roundedToCents)
    }

    @Test
    fun malformedOrMissingRatesAreRejectedRatherThanProducingBogusPrices() {
        assertNull(MetalsPriceRepository.parsePrices("not json", "EUR"))
        // Currency key absent from the payload.
        assertNull(MetalsPriceRepository.parsePrices("""{"date":"x","usd":{"xau":0.1}}""", "EUR"))
        // Missing silver rate.
        assertNull(MetalsPriceRepository.parsePrices("""{"date":"x","eur":{"xau":0.1}}""", "EUR"))
        // Zero and negative rates would invert into infinite/negative prices.
        assertNull(MetalsPriceRepository.parsePrices("""{"date":"x","eur":{"xau":0,"xag":0.02}}""", "EUR"))
        assertNull(MetalsPriceRepository.parsePrices("""{"date":"x","eur":{"xau":-1,"xag":0.02}}""", "EUR"))
    }
}
