package com.sulfuro.salati.ui.zakat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sulfuro.salati.R
import com.sulfuro.salati.theme.SalatiShapeTokens
import com.sulfuro.salati.theme.SalatiSpacing
import com.sulfuro.salati.ui.components.ValueSelectionRow

@Composable
internal fun ZakatStandardStep(
    selectedStandard: Int,
    onSelectStandard: (Int) -> Unit,
    currencyLabel: String,
    onOpenCurrency: () -> Unit,
    goldThresholdText: String,
    silverThresholdText: String,
    nisabThresholdText: String,
    ratesSummary: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)) {
        ZakatSectionHeading(title = stringResource(R.string.zakat_section_standard))

        // Side by side, each showing the threshold it actually produces. Stacked full-width
        // cards left half the screen empty, and named the two standards without saying what
        // choosing one would do to the number the user came for.
        Row(horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)) {
            StandardOption(
                selected = selectedStandard == STANDARD_GOLD,
                title = stringResource(R.string.zakat_standard_gold),
                detail = stringResource(R.string.zakat_standard_gold_detail),
                thresholdText = goldThresholdText,
                onClick = { onSelectStandard(STANDARD_GOLD) },
                modifier = Modifier.weight(1f)
            )
            StandardOption(
                selected = selectedStandard == STANDARD_SILVER,
                title = stringResource(R.string.zakat_standard_silver),
                detail = stringResource(R.string.zakat_standard_silver_detail),
                thresholdText = silverThresholdText,
                onClick = { onSelectStandard(STANDARD_SILVER) },
                modifier = Modifier.weight(1f)
            )
        }

        Text(
            text = stringResource(R.string.zakat_standard_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = SalatiSpacing.xxs)
        )

        Spacer(modifier = Modifier.height(SalatiSpacing.xxs))
        ZakatSectionHeading(title = stringResource(R.string.zakat_section_currency_rates))

        ZakatCard {
            ValueSelectionRow(
                title = stringResource(R.string.zakat_currency_label),
                value = currencyLabel,
                expanded = false,
                onExpandedChange = { onOpenCurrency() }
            )
            ZakatRowDivider()
            ratesSummary()
        }

        Spacer(modifier = Modifier.height(SalatiSpacing.xxs))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = SalatiShapeTokens.Card,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(SalatiSpacing.md),
                verticalArrangement = Arrangement.spacedBy(SalatiSpacing.xxs)
            ) {
                Text(
                    text = stringResource(R.string.zakat_standard_threshold),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = nisabThresholdText,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.zakat_nisab_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StandardOption(
    selected: Boolean,
    title: String,
    detail: String,
    thresholdText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = SalatiShapeTokens.Card,
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = if (selected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        }
    ) {
        Column(
            modifier = Modifier.padding(SalatiSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(SalatiSpacing.xs)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selected,
                    onClick = null,
                    modifier = Modifier.size(20.dp).clearAndSetSemantics {}
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
            Column {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Text(
                    text = thresholdText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 2 - cash and liquid assets
// ---------------------------------------------------------------------------
