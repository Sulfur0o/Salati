package com.sulfuro.salati.core.zakat

object ZakatCalculator {

    /**
     * Parses a localized number string into a non-negative Double.
     * Supports Western digits (0-9), Eastern Arabic/Indic digits (٠-٩),
     * Persian digits (۰-۹), and both '.' and ',' decimal separators.
     * Returns null if input is empty, non-numeric, negative, or infinite.
     */
    fun parseAmount(input: String?): Double? {
        if (input == null) return null
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // Normalize Arabic-Indic and Eastern Arabic/Persian digits to Western ASCII digits
        val normalizedDigits = buildString(trimmed.length) {
            for (ch in trimmed) {
                when (ch) {
                    in '٠'..'٩' -> append(ch - '٠')
                    in '۰'..'۹' -> append(ch - '۰')
                    else -> append(ch)
                }
            }
        }

        // Clean whitespace
        val clean = normalizedDigits.replace("\\s+".toRegex(), "")

        // Handle comma as decimal separator if there is only one comma and no dot
        val standardDecimal = if (clean.count { it == ',' } == 1 && !clean.contains('.')) {
            clean.replace(',', '.')
        } else {
            // Remove thousand/grouping commas
            clean.replace(",", "")
        }

        val parsed = standardDecimal.toDoubleOrNull() ?: return null
        if (parsed.isNaN() || parsed.isInfinite() || parsed < 0.0) return null
        return parsed
    }

    /**
     * Calculates Nisab threshold in currency value.
     */
    fun calculateNisabValue(
        nisabGrams: Double,
        pricePerGram: Double
    ): Double {
        return nisabGrams * pricePerGram
    }

    /**
     * Normalises a gold piece to the pure 24k weight it contains: `weight × karat / 24`.
     *
     * Zakat is owed on the fine metal, and the price feed quotes pure gold, so mixing
     * karats only works once every piece is expressed on that same 24k basis. Unusable
     * inputs (negative, non-finite, or an unknown karat) contribute nothing rather than
     * silently inflating the total.
     */
    fun normalizePureGoldWeight(weightGrams: Double, karat: Int): Double {
        if (!weightGrams.isFinite() || weightGrams <= 0.0) return 0.0
        val purity = GoldPurity.entries.firstOrNull { it.karat == karat } ?: return 0.0
        return weightGrams * purity.fineness
    }

    /** Sums [normalizePureGoldWeight] across every piece, in grams of pure 24k gold. */
    fun totalPureGoldWeight(items: List<ZakatGoldItem>): Double {
        return items.sumOf { normalizePureGoldWeight(it.weightGrams, it.karat) }
    }

    /**
     * Normalises a silver piece to the fine (999) weight it contains, e.g. sterling
     * silver counts for 92.5% of its gram weight.
     */
    fun normalizeFineSilverWeight(weightGrams: Double, millesimal: Int): Double {
        if (!weightGrams.isFinite() || weightGrams <= 0.0) return 0.0
        val purity = SilverPurity.entries.firstOrNull { it.millesimal == millesimal } ?: return 0.0
        return weightGrams * purity.fineness
    }

    /** Sums [normalizeFineSilverWeight] across every piece, in grams of fine silver. */
    fun totalFineSilverWeight(items: List<ZakatSilverItem>): Double {
        return items.sumOf { normalizeFineSilverWeight(it.weightGrams, it.millesimal) }
    }

    /** Market value of a normalised pure-metal weight at the quoted per-gram rate. */
    fun valueForPureWeight(pureWeightGrams: Double, pricePerGram: Double): Double {
        if (!pureWeightGrams.isFinite() || !pricePerGram.isFinite()) return 0.0
        if (pureWeightGrams <= 0.0 || pricePerGram <= 0.0) return 0.0
        return pureWeightGrams * pricePerGram
    }

    /**
     * Checks if metal prices match the user's selected active currency.
     */
    fun doPricesMatchCurrency(
        pricesCurrencyCode: String,
        activeCurrencyCode: String
    ): Boolean {
        if (pricesCurrencyCode.isEmpty()) return true
        return pricesCurrencyCode.equals(activeCurrencyCode, ignoreCase = true)
    }

    data class ZakatCalculationResult(
        val totalAssets: Double,
        val netWealth: Double,
        val nisabThreshold: Double,
        val isEligible: Boolean,
        val zakatDue: Double
    )

    fun computeZakat(
        cash: Double,
        goldValue: Double,
        silverValue: Double,
        otherAssets: Double,
        shortTermLiabilities: Double,
        nisabThreshold: Double
    ): ZakatCalculationResult {
        val totalAssets = cash + goldValue + silverValue + otherAssets
        val netWealth = maxOf(0.0, totalAssets - shortTermLiabilities)
        val isEligible = netWealth >= nisabThreshold && nisabThreshold > 0.0
        val zakatDue = if (isEligible) netWealth * 0.025 else 0.0

        return ZakatCalculationResult(
            totalAssets = totalAssets,
            netWealth = netWealth,
            nisabThreshold = nisabThreshold,
            isEligible = isEligible,
            zakatDue = zakatDue
        )
    }
}
