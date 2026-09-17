package com.sulfuro.salati.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import com.sulfuro.salati.R
import com.sulfuro.salati.theme.SalatiShapeTokens
import com.sulfuro.salati.theme.SalatiSpacing
import com.sulfuro.salati.theme.SalatiTypeTokens
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Below this, the card turns the colour of a deadline rather than of a fact. */
private const val URGENT_MINUTES = 15L
private const val MILLIS_PER_MINUTE = 60_000L

/**
 * How long is left to pray the prayer that is due.
 *
 * The list above says when each prayer came in, and the hero card says which is next.
 * Neither answers the question someone actually reaches for the phone with, which is
 * whether there is still time - so this says that, and says nothing at all in the one
 * stretch of the day when no prayer is due.
 */
@Composable
internal fun PrayerWindowCard(
    window: PrayerWindow?,
    timeFormat: DateTimeFormatter,
    modifier: Modifier = Modifier
) {
    PrayerWindowCard(window, timeFormat, rememberWindowTick(window), modifier)
}

@Composable
internal fun PrayerWindowCard(
    window: PrayerWindow?,
    timeFormat: DateTimeFormatter,
    now: Instant,
    modifier: Modifier = Modifier
) {
    if (window == null) {
        NoPrayerDueCard(modifier)
        return
    }

    val prayerName = stringResource(window.event.labelRes)
    val remainingMs = window.remainingMs(now)
    val remaining = formatWindowRemaining(remainingMs)
    val closesAt = timeFormat.format(window.closesAt)
    val urgent = remainingMs < URGENT_MINUTES * MILLIS_PER_MINUTE

    val accent = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val description = stringResource(
        R.string.daily_window_accessibility, remaining, prayerName, closesAt
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description },
        shape = SalatiShapeTokens.Card,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SalatiSpacing.md, vertical = SalatiSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(SalatiSpacing.xs)
        ) {
            Text(
                text = stringResource(R.string.daily_window_label, prayerName),
                style = MaterialTheme.typography.bodySmall
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(text = remaining, style = SalatiTypeTokens.PrayerTime, color = accent)
                Text(
                    text = stringResource(R.string.daily_window_until, closesAt),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End
                )
            }

            LinearProgressIndicator(
                progress = { window.elapsedFraction(now) },
                modifier = Modifier.fillMaxWidth(),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surface
            )
        }
    }
}

/**
 * Sunrise to Dhuhr, when nothing is owed. The card stays rather than disappearing, because
 * a row of content that comes and goes through the day reads as a glitch, and because
 * "nothing is due" is itself worth being told.
 */
@Composable
private fun NoPrayerDueCard(modifier: Modifier = Modifier) {
    val label = stringResource(R.string.daily_window_none)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = label },
        shape = SalatiShapeTokens.Card,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(
                horizontal = SalatiSpacing.md,
                vertical = SalatiSpacing.sm
            )
        )
    }
}

/**
 * A clock that only ticks when the card would change.
 *
 * The remaining time is rounded up to whole minutes, so it changes on the minute boundary
 * measured back from the close - not on the wall clock's minute, and not every second. One
 * recomposition a minute is the whole cost of this card.
 */
@Composable
private fun rememberWindowTick(window: PrayerWindow?): Instant {
    return produceState(initialValue = Instant.now(), window) {
        if (window == null) return@produceState
        while (true) {
            val now = Instant.now()
            value = now
            val remaining = window.remainingMs(now)
            if (remaining <= 0L) return@produceState
            val untilNextMinuteMark = remaining % MILLIS_PER_MINUTE
            delay(if (untilNextMinuteMark == 0L) MILLIS_PER_MINUTE else untilNextMinuteMark)
        }
    }.value
}
