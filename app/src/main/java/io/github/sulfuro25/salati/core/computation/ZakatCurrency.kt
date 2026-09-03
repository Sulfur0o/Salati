package io.github.sulfuro25.salati.core.computation

data class ZakatCurrencyOption(
    val code: String,
    val symbol: String,
    val displayName: String
)

val zakatCurrencyOptions = listOf(
    ZakatCurrencyOption("EUR", "€", "Euro"),
    ZakatCurrencyOption("USD", "$", "US Dollar"),
    ZakatCurrencyOption("GBP", "£", "British Pound"),
    ZakatCurrencyOption("MAD", "DH", "Moroccan Dirham"),
    ZakatCurrencyOption("DZD", "DA", "Algerian Dinar"),
    ZakatCurrencyOption("TND", "DT", "Tunisian Dinar"),
    ZakatCurrencyOption("SAR", "SAR", "Saudi Riyal"),
    ZakatCurrencyOption("AED", "AED", "UAE Dirham"),
    ZakatCurrencyOption("QAR", "QAR", "Qatari Riyal"),
    ZakatCurrencyOption("KWD", "KD", "Kuwaiti Dinar"),
    ZakatCurrencyOption("EGP", "E£", "Egyptian Pound"),
    ZakatCurrencyOption("TRY", "₺", "Turkish Lira"),
    ZakatCurrencyOption("PKR", "₨", "Pakistani Rupee"),
    ZakatCurrencyOption("INR", "₹", "Indian Rupee"),
    ZakatCurrencyOption("IDR", "Rp", "Indonesian Rupiah"),
    ZakatCurrencyOption("MYR", "RM", "Malaysian Ringgit"),
    ZakatCurrencyOption("CAD", "$", "Canadian Dollar")
).distinctBy { it.code }

private val zakatCurrencyByCode = zakatCurrencyOptions.associateBy { it.code }

fun zakatCurrencySymbolFor(code: String): String {
    return zakatCurrencyByCode[code]?.symbol ?: code
}
