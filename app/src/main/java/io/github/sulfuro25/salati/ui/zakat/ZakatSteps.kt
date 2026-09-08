package io.github.sulfuro25.salati.ui.zakat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.sulfuro25.salati.R
import io.github.sulfuro25.salati.core.computation.GoldPurity
import io.github.sulfuro25.salati.core.computation.SilverPurity
import io.github.sulfuro25.salati.core.computation.ZakatCalculator
import io.github.sulfuro25.salati.core.computation.ZakatGoldItem
import io.github.sulfuro25.salati.core.computation.ZakatSilverItem
import io.github.sulfuro25.salati.theme.SalatiShapeTokens
import io.github.sulfuro25.salati.theme.SalatiSpacing
import io.github.sulfuro25.salati.ui.components.ValueSelectionRow

/** Nisab standards, in the order they are shown. */
internal const val STANDARD_GOLD = 0
internal const val STANDARD_SILVER = 1

@Composable
internal fun StepHeading(title: String, explainer: String) {
    Column(verticalArrangement = Arrangement.spacedBy(SalatiSpacing.xxs)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = explainer,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Currency-amount input. The typed text is kept locally so partial entries such as
 * "12." stay editable; only successfully parsed values are pushed upward.
 */
@Composable
internal fun MoneyField(
    label: String,
    initialValue: Double,
    currencySymbol: String,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next
) {
    // Seeded once per composition of this field: re-seeding from the persisted value on
    // every keystroke would fight the cursor and reformat mid-edit.
    var text by rememberSaveable(label) {
        mutableStateOf(if (initialValue > 0.0) trimTrailingZeros(initialValue) else "")
    }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            onValueChange(ZakatCalculator.parseAmount(raw) ?: 0.0)
        },
        label = { Text(label) },
        supportingText = supportingText?.let { { Text(it) } },
        prefix = { Text(currencySymbol) },
        singleLine = true,
        shape = SalatiShapeTokens.Control,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = imeAction
        ),
        modifier = modifier.fillMaxWidth()
    )
}

private fun trimTrailingZeros(value: Double): String {
    val text = java.lang.String.format(java.util.Locale.US, "%.2f", value)
    return text.trimEnd('0').trimEnd('.').ifEmpty { "0" }
}

// ---------------------------------------------------------------------------
// Step 1 - Nisab standard and currency
// ---------------------------------------------------------------------------

@Composable
internal fun ZakatStandardStep(
    selectedStandard: Int,
    onSelectStandard: (Int) -> Unit,
    currencyLabel: String,
    onOpenCurrency: () -> Unit,
    nisabThresholdText: String,
    priceSummary: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(SalatiSpacing.md)) {
        StepHeading(
            title = stringResource(R.string.zakat_standard_heading),
            explainer = stringResource(R.string.zakat_standard_explainer)
        )

        StandardOption(
            selected = selectedStandard == STANDARD_GOLD,
            title = stringResource(R.string.zakat_standard_gold),
            detail = stringResource(R.string.zakat_standard_gold_detail),
            onClick = { onSelectStandard(STANDARD_GOLD) }
        )
        StandardOption(
            selected = selectedStandard == STANDARD_SILVER,
            title = stringResource(R.string.zakat_standard_silver),
            detail = stringResource(R.string.zakat_standard_silver_detail),
            onClick = { onSelectStandard(STANDARD_SILVER) }
        )

        ValueSelectionRow(
            title = stringResource(R.string.zakat_currency_label),
            value = currencyLabel,
            expanded = false,
            onExpandedChange = { onOpenCurrency() }
        )
        Text(
            text = stringResource(R.string.zakat_currency_helper),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        priceSummary()

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = SalatiShapeTokens.Card,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Column(
                modifier = Modifier.padding(SalatiSpacing.md),
                verticalArrangement = Arrangement.spacedBy(SalatiSpacing.xxs)
            ) {
                Text(
                    text = stringResource(R.string.zakat_standard_threshold),
                    style = MaterialTheme.typography.labelLarge
                )
                Text(
                    text = nisabThresholdText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black
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
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = SalatiShapeTokens.Card,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier.padding(SalatiSpacing.md),
            horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = null,
                modifier = Modifier.clearAndSetSemantics {}
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Step 2 - Cash and liquid assets
// ---------------------------------------------------------------------------

@Composable
internal fun ZakatCashStep(
    currencySymbol: String,
    cashOnHand: Double,
    bankBalance: Double,
    investments: Double,
    receivables: Double,
    liabilities: Double,
    subtotalText: String,
    onCashOnHandChange: (Double) -> Unit,
    onBankBalanceChange: (Double) -> Unit,
    onInvestmentsChange: (Double) -> Unit,
    onReceivablesChange: (Double) -> Unit,
    onLiabilitiesChange: (Double) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(SalatiSpacing.md)) {
        StepHeading(
            title = stringResource(R.string.zakat_cash_heading),
            explainer = stringResource(R.string.zakat_cash_explainer)
        )

        MoneyField(
            label = stringResource(R.string.zakat_cash_on_hand),
            initialValue = cashOnHand,
            currencySymbol = currencySymbol,
            onValueChange = onCashOnHandChange
        )
        MoneyField(
            label = stringResource(R.string.zakat_bank_balance),
            initialValue = bankBalance,
            currencySymbol = currencySymbol,
            onValueChange = onBankBalanceChange
        )
        MoneyField(
            label = stringResource(R.string.zakat_investments),
            initialValue = investments,
            currencySymbol = currencySymbol,
            onValueChange = onInvestmentsChange
        )
        MoneyField(
            label = stringResource(R.string.zakat_receivables),
            initialValue = receivables,
            currencySymbol = currencySymbol,
            onValueChange = onReceivablesChange
        )

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        Text(
            text = stringResource(R.string.zakat_liabilities_heading),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        MoneyField(
            label = stringResource(R.string.zakat_liabilities),
            initialValue = liabilities,
            currencySymbol = currencySymbol,
            supportingText = stringResource(R.string.zakat_liabilities_helper),
            onValueChange = onLiabilitiesChange,
            imeAction = ImeAction.Done
        )

        TotalsRow(
            label = stringResource(R.string.zakat_cash_subtotal),
            value = subtotalText,
            emphasised = true
        )
    }
}

// ---------------------------------------------------------------------------
// Step 3 - Multi-carat precious metals
// ---------------------------------------------------------------------------

@Composable
internal fun ZakatMetalsStep(
    goldItems: List<ZakatGoldItem>,
    silverItems: List<ZakatSilverItem>,
    totalPureGoldText: String,
    totalFineSilverText: String,
    onAddGoldItem: () -> Unit,
    onUpdateGoldItem: (ZakatGoldItem) -> Unit,
    onEditGoldItemText: (ZakatGoldItem) -> Unit,
    onRemoveGoldItem: (String) -> Unit,
    onAddSilverItem: () -> Unit,
    onUpdateSilverItem: (ZakatSilverItem) -> Unit,
    onEditSilverItemText: (ZakatSilverItem) -> Unit,
    onRemoveSilverItem: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(SalatiSpacing.md)) {
        StepHeading(
            title = stringResource(R.string.zakat_metals_heading),
            explainer = stringResource(R.string.zakat_metals_explainer)
        )

        MetalGroupHeader(
            title = stringResource(R.string.zakat_gold_items_title),
            totalText = totalPureGoldText
        )
        if (goldItems.isEmpty()) {
            EmptyItemsHint(stringResource(R.string.zakat_gold_empty))
        } else {
            goldItems.forEachIndexed { index, item ->
                GoldItemCard(
                    item = item,
                    index = index,
                    onUpdate = onUpdateGoldItem,
                    onEditText = onEditGoldItemText,
                    onRemove = { onRemoveGoldItem(item.id) }
                )
            }
        }
        AssistChip(
            onClick = onAddGoldItem,
            label = { Text(stringResource(R.string.zakat_add_gold_item)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                )
            }
        )

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        MetalGroupHeader(
            title = stringResource(R.string.zakat_silver_items_title),
            totalText = totalFineSilverText
        )
        if (silverItems.isEmpty()) {
            EmptyItemsHint(stringResource(R.string.zakat_silver_empty))
        } else {
            silverItems.forEachIndexed { index, item ->
                SilverItemCard(
                    item = item,
                    index = index,
                    onUpdate = onUpdateSilverItem,
                    onEditText = onEditSilverItemText,
                    onRemove = { onRemoveSilverItem(item.id) }
                )
            }
        }
        AssistChip(
            onClick = onAddSilverItem,
            label = { Text(stringResource(R.string.zakat_add_silver_item)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize)
                )
            }
        )
    }
}

@Composable
private fun MetalGroupHeader(title: String, totalText: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = totalText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyItemsHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun GoldItemCard(
    item: ZakatGoldItem,
    index: Int,
    onUpdate: (ZakatGoldItem) -> Unit,
    onEditText: (ZakatGoldItem) -> Unit,
    onRemove: () -> Unit
) {
    ItemCard(
        defaultLabel = stringResource(R.string.zakat_item_default_label, index + 1),
        label = item.label,
        weightGrams = item.weightGrams,
        itemKey = item.id,
        pureWeightText = stringResource(
            R.string.zakat_item_pure_weight,
            formatGrams(ZakatCalculator.normalizePureGoldWeight(item.weightGrams, item.karat))
        ),
        // Typed fields go through onEditText, which the screen coalesces; the purity chips
        // are a tap and have to redraw at once, so they take the immediate path.
        onLabelChange = { onEditText(item.copy(label = it)) },
        onWeightChange = { onEditText(item.copy(weightGrams = it)) },
        onRemove = onRemove
    ) {
        PurityChipRow(
            options = GoldPurity.entries.map { it.karat to goldPurityLabel(it) },
            selectedValue = item.karat,
            onSelect = { onUpdate(item.copy(karat = it)) }
        )
    }
}

@Composable
private fun SilverItemCard(
    item: ZakatSilverItem,
    index: Int,
    onUpdate: (ZakatSilverItem) -> Unit,
    onEditText: (ZakatSilverItem) -> Unit,
    onRemove: () -> Unit
) {
    ItemCard(
        defaultLabel = stringResource(R.string.zakat_item_default_label, index + 1),
        label = item.label,
        weightGrams = item.weightGrams,
        itemKey = item.id,
        pureWeightText = stringResource(
            R.string.zakat_item_pure_weight,
            formatGrams(ZakatCalculator.normalizeFineSilverWeight(item.weightGrams, item.millesimal))
        ),
        onLabelChange = { onEditText(item.copy(label = it)) },
        onWeightChange = { onEditText(item.copy(weightGrams = it)) },
        onRemove = onRemove
    ) {
        PurityChipRow(
            options = SilverPurity.entries.map { it.millesimal to silverPurityLabel(it) },
            selectedValue = item.millesimal,
            onSelect = { onUpdate(item.copy(millesimal = it)) }
        )
    }
}

@Composable
private fun ItemCard(
    defaultLabel: String,
    label: String,
    weightGrams: Double,
    itemKey: String,
    pureWeightText: String,
    onLabelChange: (String) -> Unit,
    onWeightChange: (Double) -> Unit,
    onRemove: () -> Unit,
    purityPicker: @Composable () -> Unit
) {
    var weightText by rememberSaveable(itemKey) {
        mutableStateOf(if (weightGrams > 0.0) trimTrailingZeros(weightGrams) else "")
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SalatiShapeTokens.Card,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(SalatiSpacing.md),
            verticalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = label.ifBlank { defaultLabel },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.zakat_item_remove),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            OutlinedTextField(
                value = label,
                onValueChange = onLabelChange,
                label = { Text(stringResource(R.string.zakat_item_label)) },
                placeholder = { Text(stringResource(R.string.zakat_item_label_hint)) },
                singleLine = true,
                shape = SalatiShapeTokens.Control,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = stringResource(R.string.zakat_item_purity),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            purityPicker()

            OutlinedTextField(
                value = weightText,
                onValueChange = { raw ->
                    weightText = raw
                    onWeightChange(ZakatCalculator.parseAmount(raw) ?: 0.0)
                },
                label = { Text(stringResource(R.string.zakat_item_weight)) },
                singleLine = true,
                shape = SalatiShapeTokens.Control,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = pureWeightText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PurityChipRow(
    options: List<Pair<Int, String>>,
    selectedValue: Int,
    onSelect: (Int) -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.xs)
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selectedValue,
                onClick = { onSelect(value) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

@Composable
private fun goldPurityLabel(purity: GoldPurity): String = when (purity) {
    GoldPurity.K24 -> stringResource(R.string.zakat_purity_24k)
    GoldPurity.K22 -> stringResource(R.string.zakat_purity_22k)
    GoldPurity.K21 -> stringResource(R.string.zakat_purity_21k)
    GoldPurity.K18 -> stringResource(R.string.zakat_purity_18k)
    GoldPurity.K14 -> stringResource(R.string.zakat_purity_14k)
}

@Composable
private fun silverPurityLabel(purity: SilverPurity): String = when (purity) {
    SilverPurity.FINE_999 -> stringResource(R.string.zakat_purity_fine_silver)
    SilverPurity.STERLING_925 -> stringResource(R.string.zakat_purity_sterling)
}

/**
 * A weight as the user reads it. Composable so it follows the display locale, the same as
 * the money totals beside it - fixed at US, an Arabic reader got "85.00 g" in Western
 * digits next to an amount written in Arabic-Indic ones.
 *
 * Not to be confused with [trimTrailingZeros], which fills a text field and stays at
 * [java.util.Locale.US] on purpose: that string has to survive being parsed back.
 */
@Composable
internal fun formatGrams(value: Double): String {
    val locale = LocalConfiguration.current.locales[0]
    return java.lang.String.format(locale, "%.2f", value)
}

// ---------------------------------------------------------------------------
// Step 4 - Summary
// ---------------------------------------------------------------------------

@Composable
internal fun TotalsRow(
    label: String,
    value: String,
    emphasised: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (emphasised) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (emphasised) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.size(SalatiSpacing.sm))
        Text(
            text = value,
            style = if (emphasised) {
                MaterialTheme.typography.titleSmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = FontWeight.Bold,
            color = if (emphasised) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
    }
}

@Composable
internal fun SectionSpacer() {
    Spacer(modifier = Modifier.height(SalatiSpacing.xs))
}
