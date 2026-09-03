package io.github.sulfuro25.salati.core.computation

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
     * Calculates effective gold price per gram based on carat (24k is pure base).
     */
    fun calculateEffectiveCaratPrice(
        pure24kPrice: Double,
        carat: Int
    ): Double {
        val multiplier = when (carat) {
            24 -> 1.0
            21 -> 21.0 / 24.0
            18 -> 18.0 / 24.0
            14 -> 14.0 / 24.0
            10 -> 10.0 / 24.0
            else -> 1.0
        }
        return pure24kPrice * multiplier
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
