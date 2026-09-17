package com.sulfuro.salati.ui.zakat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sulfuro.salati.R
import com.sulfuro.salati.theme.SalatiShapeTokens
import com.sulfuro.salati.theme.SalatiSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun ZakatSummaryStep(
    assessment: ZakatAssessment,
    formatAmount: (Double, Boolean) -> String,
    hawlStart: LocalDate?,
    hawlDue: LocalDate?,
    onChangeHawlDate: () -> Unit
) {
    val displayLocale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(displayLocale) {
        DateTimeFormatter.ofPattern("d MMM uuuu", displayLocale)
    }

    Column(verticalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)) {
        EligibilityCard(
            isEligible = assessment.isEligible,
            zakatDueText = formatAmount(assessment.zakatDue, true),
            rateOfText = stringResource(
                R.string.zakat_summary_rate_of,
                formatAmount(assessment.netWealth, true)
            ),
            belowNisabText = stringResource(
                R.string.zakat_summary_below_nisab,
                formatAmount(assessment.nisabThreshold, true)
            )
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = SalatiShapeTokens.Card,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(SalatiSpacing.md),
                verticalArrangement = Arrangement.spacedBy(SalatiSpacing.xs)
            ) {
                TotalsRow(
                    label = stringResource(R.string.zakat_summary_cash_line),
                    value = formatAmount(assessment.liquidAssets, true)
                )
                TotalsRow(
                    label = stringResource(
                        R.string.zakat_summary_gold_line,
                        formatGrams(assessment.pureGoldGrams)
                    ),
                    value = formatAmount(assessment.goldValue, true)
                )
                TotalsRow(
                    label = stringResource(
                        R.string.zakat_summary_silver_line,
                        formatGrams(assessment.fineSilverGrams)
                    ),
                    value = formatAmount(assessment.silverValue, true)
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                TotalsRow(
                    label = stringResource(R.string.zakat_summary_gross),
                    value = formatAmount(assessment.grossAssets, true)
                )
                val owesSomething = assessment.liabilities > 0.0
                TotalsRow(
                    label = stringResource(R.string.zakat_summary_liabilities),
                    // Nothing owed is not a deduction: a red "- EUR 0.00" announced a
                    // subtraction that never happened.
                    value = if (owesSomething) {
                        stringResource(
                            R.string.zakat_summary_negative,
                            formatAmount(assessment.liabilities, true)
                        )
                    } else {
                        formatAmount(0.0, true)
                    },
                    valueColor = if (owesSomething) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                TotalsRow(
                    label = stringResource(R.string.zakat_summary_net),
                    value = formatAmount(assessment.netWealth, true),
                    emphasised = true
                )
                TotalsRow(
                    label = stringResource(R.string.zakat_summary_nisab),
                    value = formatAmount(assessment.nisabThreshold, true)
                )
            }
        }

        HawlRow(
            dueText = hawlDue?.let(dateFormatter::format),
            reachedText = hawlStart?.let {
                stringResource(R.string.zakat_hawl_reached, dateFormatter.format(it))
            },
            onChange = onChangeHawlDate
        )
    }
}

/**
 * The Hawl milestone as one row rather than a card with its own heading, paragraph, two
 * text buttons, a divider, a helper line and a button. The calendar hand-off it used to
 * carry is now the step's primary action, in the bar at the foot of the screen, where the
 * last step otherwise had a greyed-out Next and nothing to do.
 */
@Composable
private fun HawlRow(dueText: String?, reachedText: String?, onChange: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SalatiShapeTokens.Card,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(
                start = SalatiSpacing.md,
                end = SalatiSpacing.sm,
                top = SalatiSpacing.sm,
                bottom = SalatiSpacing.sm
            ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = SalatiSpacing.xs)) {
                Text(
                    text = stringResource(R.string.zakat_hawl_next_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = dueText ?: stringResource(R.string.zakat_hawl_unset),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (reachedText != null) {
                    Text(
                        text = reachedText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            OutlinedButton(onClick = onChange) {
                Text(stringResource(R.string.zakat_hawl_change))
            }
        }
    }
}

/**
 * Asks for the day Zakat next falls due.
 *
 * The dialog names what it is asking for, because a bare "Select date" over a card that
 * shows two dates - when the Hawl completes, and when Nisab was reached - does not say
 * which of them is being changed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HawlDatePickerDialog(
    initialDate: LocalDate,
    canClear: Boolean,
    onDismiss: () -> Unit,
    onPicked: (LocalDate?) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toEpochDay() * 86_400_000L
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        onPicked(Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate())
                    }
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.hawl_date_confirm))
            }
        },
        dismissButton = {
            // Clearing the date used to be a button on the summary card. The card is a row
            // now, so it lives here - beside Cancel, which is where someone who has opened
            // the picker to change their mind will look for it.
            Row(horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.xxs)) {
                if (canClear) {
                    TextButton(
                        onClick = {
                            onPicked(null)
                            onDismiss()
                        }
                    ) {
                        Text(stringResource(R.string.hawl_clear_date))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.hawl_date_cancel))
                }
            }
        }
    ) {
        DatePicker(
            state = datePickerState,
            title = {
                Text(
                    text = stringResource(R.string.zakat_hawl_next_label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(
                        start = SalatiSpacing.xl,
                        end = SalatiSpacing.sm,
                        top = SalatiSpacing.md
                    )
                )
            }
        )
    }
}

@Composable
private fun EligibilityCard(
    isEligible: Boolean,
    zakatDueText: String,
    rateOfText: String,
    belowNisabText: String
) {
    if (!isEligible) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = SalatiShapeTokens.Card,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Text(
                text = belowNisabText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(SalatiSpacing.md)
            )
        }
        return
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SalatiShapeTokens.Card,
        color = MaterialTheme.colorScheme.primary
    ) {
        Column(
            modifier = Modifier.padding(SalatiSpacing.md),
            verticalArrangement = Arrangement.spacedBy(SalatiSpacing.xxs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.zakat_summary_above_nisab),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                )
                Text(
                    text = rateOfText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    textAlign = TextAlign.End
                )
            }
            Text(
                text = zakatDueText,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Chrome: header, navigation, rates
// ---------------------------------------------------------------------------
