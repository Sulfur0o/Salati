package com.sulfuro.salati.core.computation

import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import java.util.Locale

/**
 * The shapes Aladhan sends dates and times in.
 *
 * Three files declared these for themselves - the mapper and the repository, which both
 * parse the same gregorian date field, and the on-device calculator, which formats times
 * back into the same shape so a locally computed day is indistinguishable from a fetched
 * one. Agreeing by coincidence is not the same as agreeing on purpose.
 *
 * Locale.ROOT throughout - this is a wire format, not something anyone reads.
 */
internal object AladhanFormats {

    /** `15-07-2026`. Strict, so an impossible date is refused rather than clamped. */
    val date: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-uuuu", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)

    /** `05:13`, once the trailing `(CET)` some payloads carry has been trimmed off. */
    val time: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
        .withResolverStyle(ResolverStyle.STRICT)
}
