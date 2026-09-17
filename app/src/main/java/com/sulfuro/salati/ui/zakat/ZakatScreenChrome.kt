package com.sulfuro.salati.ui.zakat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sulfuro.salati.R
import com.sulfuro.salati.core.zakat.zakatCurrencyOptions
import com.sulfuro.salati.data.settings.CalculationSettings
import com.sulfuro.salati.theme.SalatiShapeTokens
import com.sulfuro.salati.theme.SalatiSpacing
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * The step you are on, and what you owe.
 *
 * This replaced five stacked rows: a "Zakat" headline the tab bar already carried, a
 * progress bar, a "Step 2 of 4 - Cash & assets" line, a heading that said the same thing
 * again, and an explainer paragraph. Two rows now, and the second of them is the answer
 * the tab exists to give - live, on every step, rather than three taps away.
 */
@Composable
internal fun ZakatHeader(
    step: Int,
    stepTitles: List<String>,
    dueValue: String,
    isEligible: Boolean,
    onJumpToStep: (Int) -> Unit
) {
    val current = step.coerceIn(0, ZAKAT_STEP_COUNT - 1)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = SalatiSpacing.md,
                end = SalatiSpacing.md,
                // The amount is a two-line block, so it reaches higher than the single-line
                // title it replaced and sat right up against the status bar without this.
                top = SalatiSpacing.xs,
                bottom = SalatiSpacing.xxs
            )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            val spokenTitle = stringResource(
                R.string.zakat_step_accessibility,
                current + 1,
                ZAKAT_STEP_COUNT,
                stepTitles[current]
            )
            Text(
                text = stepTitles[current],
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = SalatiSpacing.xs)
                    .semantics {
                        heading()
                        contentDescription = spokenTitle
                    }
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.zakat_due_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = dueValue,
                    style = if (isEligible) {
                        MaterialTheme.typography.headlineMedium
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                    fontWeight = FontWeight.Bold,
                    color = if (isEligible) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.xxs)
        ) {
            repeat(ZAKAT_STEP_COUNT) { index ->
                val reached = index <= current
                val weight by animateFloatAsState(
                    targetValue = if (reached) 1f else 0.35f,
                    label = "zakatStepSegment$index"
                )
                val jumpLabel = stringResource(R.string.zakat_step_jump, index + 1, stepTitles[index])
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .selectable(
                            selected = index == current,
                            role = Role.Tab,
                            onClick = { onJumpToStep(index) }
                        )
                        .semantics { contentDescription = jumpLabel },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary.copy(alpha = weight),
                                shape = RoundedCornerShape(2.5.dp)
                            )
                    )
                }
            }
        }
    }
}

@Composable
internal fun ZakatStepNavigation(
    step: Int,
    nextLabel: String,
    nextIsCalendarHandoff: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SalatiSpacing.md, vertical = SalatiSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onBack,
                enabled = step > 0,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.zakat_step_back))
            }
            Button(
                onClick = onNext,
                modifier = Modifier.weight(if (nextIsCalendarHandoff) 1.4f else 1f)
            ) {
                if (nextIsCalendarHandoff) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(SalatiSpacing.xs))
                }
                Text(text = nextLabel, maxLines = 1)
            }
        }
    }
}

/**
 * What the metals are worth per gram, when that was quoted, and the button that asks again.
 *
 * The prices themselves used not to be shown anywhere, which left a bare "Rates for 11 Sep"
 * floating between two cards with a refresh button beside it and no way to tell what it
 * would refresh.
 */
@Composable
internal fun MetalRatesRow(
    isRefreshing: Boolean,
    updatedAtMillis: Long,
    rateDate: String,
    hasFailed: Boolean,
    goldPriceText: String,
    silverPriceText: String,
    onRefresh: () -> Unit
) {
    val displayLocale = LocalConfiguration.current.locales[0]
    // The source's own quote date, when it gave one. "Updated 2 minutes ago" describes the
    // download, not the price, and the two can be days apart.
    val quotedOn = remember(rateDate, displayLocale) {
        runCatching {
            DateTimeFormatter.ofPattern("d MMM uuuu", displayLocale)
                .format(LocalDate.parse(rateDate))
        }.getOrNull()
    }
    val statusText = when {
        isRefreshing -> stringResource(R.string.zakat_price_updating)
        hasFailed -> stringResource(R.string.zakat_price_update_failed)
        quotedOn != null -> stringResource(R.string.zakat_price_quoted_on, quotedOn)
        updatedAtMillis > 0L -> stringResource(
            R.string.zakat_price_updated_at,
            formatPriceTimestamp(updatedAtMillis)
        )
        else -> stringResource(R.string.zakat_price_never_updated)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = SalatiSpacing.md, end = SalatiSpacing.xxs, top = SalatiSpacing.xs, bottom = SalatiSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = SalatiSpacing.xs)) {
            Text(
                text = stringResource(R.string.zakat_rates_label),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.zakat_rates_values, goldPriceText, silverPriceText),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (hasFailed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.secondary
                }
            )
        }
        IconButton(onClick = onRefresh, enabled = !isRefreshing, modifier = Modifier.size(48.dp)) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.zakat_refresh_price),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
internal fun CurrencyMismatchWarning(settings: CalculationSettings) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = SalatiShapeTokens.Control
    ) {
        Text(
            text = stringResource(
                R.string.zakat_currency_mismatch_warning,
                settings.zakat.pricesCurrencyCode,
                settings.zakat.currencyCode
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(SalatiSpacing.md)
        )
    }
}

internal fun currencyLabelFor(code: String): String {
    val option = zakatCurrencyOptions.firstOrNull { it.code == code } ?: return code
    val localized = runCatching {
        java.util.Currency.getInstance(option.code).getDisplayName()
    }.getOrDefault(option.displayName)
    return "${option.code} (${option.symbol}) — $localized"
}

private fun formatPriceTimestamp(epochMillis: Long): String {
    val formatter = java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.MEDIUM,
        java.text.DateFormat.SHORT
    )
    return formatter.format(java.util.Date(epochMillis))
}
