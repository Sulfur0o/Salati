package com.sulfuro.salati.core.computation

import kotlinx.serialization.Serializable

/**
 * Jewellery is rarely pure. Zakat is assessed on the *fine metal* a piece contains, so
 * every item is normalised to its pure-metal equivalent before it is valued against the
 * 24k gold / 999 silver rate the price feed quotes.
 */

/** Gold purities offered in the item editor, expressed in karat out of 24. */
enum class GoldPurity(val karat: Int) {
    K24(24),
    K22(22),
    K21(21),
    K18(18),
    K14(14);

    /** Fraction of the piece that is pure gold, e.g. 18k -> 0.75. */
    val fineness: Double get() = karat / 24.0

    companion object {
        val DEFAULT: GoldPurity = K24

        fun fromKarat(karat: Int): GoldPurity = entries.firstOrNull { it.karat == karat } ?: DEFAULT
    }
}

/** Silver purities offered in the item editor, expressed in parts per thousand. */
enum class SilverPurity(val millesimal: Int) {
    FINE_999(999),
    STERLING_925(925);

    /** Fraction of the piece that is fine silver, e.g. sterling -> 0.925. */
    val fineness: Double get() = millesimal / 1000.0

    companion object {
        val DEFAULT: SilverPurity = FINE_999

        fun fromMillesimal(millesimal: Int): SilverPurity =
            entries.firstOrNull { it.millesimal == millesimal } ?: DEFAULT
    }
}

/**
 * One gold piece the user owns. [karat] and [weightGrams] are stored rather than a
 * precomputed pure weight so the normalisation stays visible and re-derivable when the
 * user edits the entry later.
 */
@Serializable
data class ZakatGoldItem(
    val id: String,
    val label: String = "",
    val karat: Int = GoldPurity.DEFAULT.karat,
    val weightGrams: Double = 0.0
) {
    val purity: GoldPurity get() = GoldPurity.fromKarat(karat)
}

/** One silver piece the user owns. */
@Serializable
data class ZakatSilverItem(
    val id: String,
    val label: String = "",
    val millesimal: Int = SilverPurity.DEFAULT.millesimal,
    val weightGrams: Double = 0.0
) {
    val purity: SilverPurity get() = SilverPurity.fromMillesimal(millesimal)
}
