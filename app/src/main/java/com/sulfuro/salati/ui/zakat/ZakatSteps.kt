package com.sulfuro.salati.ui.zakat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sulfuro.salati.R
import com.sulfuro.salati.core.zakat.GoldPurity
import com.sulfuro.salati.core.zakat.SilverPurity
import com.sulfuro.salati.core.zakat.ZakatCalculator
import com.sulfuro.salati.core.zakat.ZakatGoldItem
import com.sulfuro.salati.core.zakat.ZakatSilverItem
import com.sulfuro.salati.theme.SalatiShapeTokens
import com.sulfuro.salati.theme.SalatiSpacing
import com.sulfuro.salati.ui.components.ValueSelectionRow

/** Nisab standards, in the order they are shown. */
internal const val STANDARD_GOLD = 0
internal const val STANDARD_SILVER = 1

// ---------------------------------------------------------------------------
// Shared furniture
//
// Every step is now the same two shapes: a section label, and a card of rows
// under it. That is what replaced a per-step heading and explainer paragraph -
// the step's name is already in the header, so printing it again as a title and
// then explaining it cost five rows of chrome before the first control.
// ---------------------------------------------------------------------------

/**
 * The label above a card, with an optional figure on the right - a running total, or the
 * currency every amount below is quoted in.
 */
@Composable
internal fun ZakatSectionHeading(title: String, trailing: String? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = SalatiSpacing.xxs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (trailing != null) {
            Spacer(modifier = Modifier.width(SalatiSpacing.xs))
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

/** The bordered card that a group of rows sits in. */
@Composable
internal fun ZakatCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = SalatiShapeTokens.Card,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(content = content)
    }
}

/** The rule between two rows of a card. */
@Composable
internal fun ZakatRowDivider() {
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

private fun trimTrailingZeros(value: Double, locale: java.util.Locale): String {
    val text = java.lang.String.format(locale, "%.2f", value)
    val separator = java.text.DecimalFormatSymbols.getInstance(locale).decimalSeparator
    if (!text.contains(separator)) return text.ifEmpty { "0" }
    return text.trimEnd('0').trimEnd(separator).ifEmpty { "0" }
}

/**
 * One amount in a ledger: what it is on the left, what it is worth on the right, edited
 * where it is read.
 *
 * This replaced a full outlined text field per amount. Six of those ran to 58dp each with
 * their own border and floating label, which pushed the subtotal off the bottom of the
 * screen - so the one number that told you whether the entries were right was the one you
 * could never see. They also rendered unevenly: a filled field floats its label up onto
 * the border while an empty one keeps it inside, so a column of six looked ragged.
 *
 * The typed text is kept locally so partial entries such as "12." stay editable; only
 * successfully parsed values are pushed upward.
 */
@Composable
internal fun MoneyRow(
    label: String,
    initialValue: Double,
    currencySymbol: String,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    imeAction: ImeAction = ImeAction.Next
) {
    // The totals beside these are formatted for the display locale, so a seeded "1250.5"
    // sitting above "EUR 1.250,50" read as two different numbers. parseAmount already
    // accepts either separator, and Arabic-Indic digits besides.
    val displayLocale = LocalConfiguration.current.locales[0]
    // Seeded once per composition of this row: re-seeding from the persisted value on
    // every keystroke would fight the cursor and reformat mid-edit.
    var text by rememberSaveable(label) {
        mutableStateOf(if (initialValue > 0.0) trimTrailingZeros(initialValue, displayLocale) else "")
    }
    var focused by rememberSaveable(label) { mutableStateOf(false) }

    val accent = MaterialTheme.colorScheme.primary
    val zero = remember(displayLocale) { java.lang.String.format(displayLocale, "%.2f", 0.0) }
    val valueColor = when {
        focused -> accent
        text.isBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (focused) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent
            )
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = SalatiSpacing.lg, vertical = SalatiSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = SalatiSpacing.sm)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (focused) FontWeight.Medium else FontWeight.Normal,
                color = if (focused) accent else MaterialTheme.colorScheme.onSurface
            )
            if (supportingText != null) {
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .drawBehind {
                    if (!focused) return@drawBehind
                    val y = size.height - 1.dp.toPx()
                    drawLine(
                        color = accent,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 2.dp.toPx()
                    )
                }
                .padding(bottom = 3.dp)
        ) {
            Text(
                text = currencySymbol,
                style = MaterialTheme.typography.bodyLarge,
                color = valueColor
            )
            Spacer(modifier = Modifier.width(SalatiSpacing.xxs))
            // A text field takes every pixel it is offered, so left to itself it swallows
            // the row and squeezes the label to one letter per line. An invisible copy of
            // the value sets the width instead, which also keeps the currency symbol
            // beside the number rather than stranded in a column of its own.
            Box(
                modifier = Modifier.widthIn(min = 56.dp, max = 156.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = text.ifEmpty { zero },
                    style = MaterialTheme.typography.titleMedium,
                    // Doubles as the placeholder: an empty row reads "0.00", not blank.
                    color = if (text.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        Color.Transparent
                    },
                    maxLines = 1,
                    // Room for the caret to sit after the last digit. Cleared from the
                    // semantics tree because it is a measuring stick, not content: left in,
                    // it makes every amount appear twice, once to a screen reader and once
                    // to anything looking the row up by its value.
                    modifier = Modifier.padding(end = 3.dp).clearAndSetSemantics {}
                )
                BasicTextField(
                    value = text,
                    onValueChange = { raw ->
                        text = raw
                        onValueChange(ZakatCalculator.parseAmount(raw) ?: 0.0)
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = valueColor,
                        textAlign = TextAlign.End
                    ),
                    cursorBrush = SolidColor(accent),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = imeAction
                    ),
                    modifier = Modifier
                        .matchParentSize()
                        .semantics { contentDescription = label }
                        .onFocusChanged { focused = it.isFocused }
                )
            }
        }
    }
}

/** A row that reads as a total rather than as an input. */
@Composable
internal fun ZakatTotalRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .defaultMinSize(minHeight = 54.dp)
            .padding(horizontal = SalatiSpacing.lg, vertical = SalatiSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f).padding(end = SalatiSpacing.xs)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End
        )
    }
}

// ---------------------------------------------------------------------------
// Step 1 - the basis: which Nisab standard, in which currency, at which rates
// ---------------------------------------------------------------------------

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

@Composable
internal fun ZakatCashStep(
    currencySymbol: String,
    currencyCode: String,
    cashOnHand: Double,
    bankBalance: Double,
    investments: Double,
    receivables: Double,
    businessInventory: Double,
    liabilities: Double,
    subtotalText: String,
    onCashOnHandChange: (Double) -> Unit,
    onBankBalanceChange: (Double) -> Unit,
    onInvestmentsChange: (Double) -> Unit,
    onReceivablesChange: (Double) -> Unit,
    onBusinessInventoryChange: (Double) -> Unit,
    onLiabilitiesChange: (Double) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)) {
        ZakatSectionHeading(
            title = stringResource(R.string.zakat_section_own),
            trailing = currencyCode
        )

        ZakatCard {
            MoneyRow(
                label = stringResource(R.string.zakat_cash_on_hand),
                initialValue = cashOnHand,
                currencySymbol = currencySymbol,
                onValueChange = onCashOnHandChange
            )
            ZakatRowDivider()
            MoneyRow(
                label = stringResource(R.string.zakat_bank_balance),
                initialValue = bankBalance,
                currencySymbol = currencySymbol,
                onValueChange = onBankBalanceChange
            )
            ZakatRowDivider()
            MoneyRow(
                label = stringResource(R.string.zakat_investments),
                initialValue = investments,
                currencySymbol = currencySymbol,
                onValueChange = onInvestmentsChange
            )
            ZakatRowDivider()
            MoneyRow(
                label = stringResource(R.string.zakat_receivables),
                initialValue = receivables,
                currencySymbol = currencySymbol,
                onValueChange = onReceivablesChange
            )
            ZakatRowDivider()
            // Trade goods belong with the cash they were bought with, and all four schools
            // agree they are zakatable - at cost, which is the number a shopkeeper actually
            // knows, rather than the margin they hope for.
            MoneyRow(
                label = stringResource(R.string.zakat_business_inventory),
                initialValue = businessInventory,
                currencySymbol = currencySymbol,
                supportingText = stringResource(R.string.zakat_business_inventory_helper),
                onValueChange = onBusinessInventoryChange
            )
            ZakatRowDivider()
            ZakatTotalRow(
                label = stringResource(R.string.zakat_cash_total),
                value = subtotalText
            )
        }

        Spacer(modifier = Modifier.height(SalatiSpacing.xxs))
        ZakatSectionHeading(title = stringResource(R.string.zakat_section_owe))

        ZakatCard {
            MoneyRow(
                label = stringResource(R.string.zakat_liabilities),
                initialValue = liabilities,
                currencySymbol = currencySymbol,
                supportingText = stringResource(R.string.zakat_liabilities_helper),
                onValueChange = onLiabilitiesChange,
                imeAction = ImeAction.Done
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Step 3 - multi-carat precious metals
// ---------------------------------------------------------------------------

@Composable
internal fun ZakatMetalsStep(
    declaresMetals: Boolean?,
    onDeclaresMetalsChange: (Boolean) -> Unit,
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
    // Only one piece is open at a time. A piece is five controls while it is being edited
    // and one line once it is not, which is what lets a jewellery box fit on a screen.
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }
    val ids = goldItems.map { it.id } + silverItems.map { it.id }
    var knownIds by remember { mutableStateOf(ids.toSet()) }
    LaunchedEffect(ids) {
        // A piece you have just added has nothing in it, so it opens itself.
        val fresh = ids.filterNot { it in knownIds }
        if (fresh.isNotEmpty()) expandedId = fresh.last()
        knownIds = ids.toSet()
    }

    Column(verticalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)) {
        // The question is asked at full size once, because the answer turns on which
        // school the user follows and that needs saying. Once answered it shrinks to two
        // chips: keeping the heading, the paragraph and two explained cards on screen
        // meant two headings and two explainers stacked above the first item.
        if (declaresMetals == null) {
            MetalsChoiceQuestion(onDeclaresMetalsChange)
            return@Column
        }

        Row(horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.xs)) {
            FilterChip(
                selected = declaresMetals == false,
                onClick = { onDeclaresMetalsChange(false) },
                label = { Text(stringResource(R.string.zakat_metals_chip_skip)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
            FilterChip(
                selected = declaresMetals == true,
                onClick = { onDeclaresMetalsChange(true) },
                label = { Text(stringResource(R.string.zakat_metals_chip_declare)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }

        if (declaresMetals == false) {
            Text(
                text = stringResource(R.string.zakat_metals_skipped_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        ZakatSectionHeading(
            title = stringResource(R.string.zakat_gold_items_title),
            trailing = totalPureGoldText
        )
        ZakatCard {
            goldItems.forEachIndexed { index, item ->
                val purityLabel = goldPurityLabel(GoldPurity.entries.first { it.karat == item.karat })
                val pureGrams = formatGrams(
                    ZakatCalculator.normalizePureGoldWeight(item.weightGrams, item.karat)
                )
                MetalItem(
                    expanded = expandedId == item.id,
                    onExpandedChange = { expandedId = if (it) item.id else null },
                    defaultLabel = stringResource(R.string.zakat_item_default_label, index + 1),
                    label = item.label,
                    weightGrams = item.weightGrams,
                    itemKey = item.id,
                    summaryText = stringResource(
                        R.string.zakat_item_summary_gold,
                        purityLabel,
                        pureGrams
                    ),
                    pureWeightText = stringResource(R.string.zakat_item_pure_weight, pureGrams),
                    // Typed fields go through onEditText, which the screen coalesces; the
                    // purity chips are a tap and have to redraw at once, so they take the
                    // immediate path.
                    onLabelChange = { onEditGoldItemText(item.copy(label = it)) },
                    onWeightChange = { onEditGoldItemText(item.copy(weightGrams = it)) },
                    onRemove = { onRemoveGoldItem(item.id) }
                ) {
                    PurityChipRow(
                        options = GoldPurity.entries.map { it.karat to goldPurityLabel(it) },
                        selectedValue = item.karat,
                        onSelect = { onUpdateGoldItem(item.copy(karat = it)) }
                    )
                }
                ZakatRowDivider()
            }
            AddPieceRow(
                label = stringResource(R.string.zakat_add_gold_item),
                onClick = onAddGoldItem
            )
        }

        Spacer(modifier = Modifier.height(SalatiSpacing.xxs))
        ZakatSectionHeading(
            title = stringResource(R.string.zakat_silver_items_title),
            trailing = totalFineSilverText
        )
        ZakatCard {
            silverItems.forEachIndexed { index, item ->
                val purityLabel =
                    silverPurityLabel(SilverPurity.entries.first { it.millesimal == item.millesimal })
                val fineGrams = formatGrams(
                    ZakatCalculator.normalizeFineSilverWeight(item.weightGrams, item.millesimal)
                )
                MetalItem(
                    expanded = expandedId == item.id,
                    onExpandedChange = { expandedId = if (it) item.id else null },
                    defaultLabel = stringResource(R.string.zakat_item_default_label, index + 1),
                    label = item.label,
                    weightGrams = item.weightGrams,
                    itemKey = item.id,
                    summaryText = stringResource(
                        R.string.zakat_item_summary_silver,
                        purityLabel,
                        fineGrams
                    ),
                    pureWeightText = stringResource(R.string.zakat_item_fine_weight, fineGrams),
                    onLabelChange = { onEditSilverItemText(item.copy(label = it)) },
                    onWeightChange = { onEditSilverItemText(item.copy(weightGrams = it)) },
                    onRemove = { onRemoveSilverItem(item.id) }
                ) {
                    PurityChipRow(
                        options = SilverPurity.entries.map { it.millesimal to silverPurityLabel(it) },
                        selectedValue = item.millesimal,
                        onSelect = { onUpdateSilverItem(item.copy(millesimal = it)) }
                    )
                }
                ZakatRowDivider()
            }
            AddPieceRow(
                label = stringResource(R.string.zakat_add_silver_item),
                onClick = onAddSilverItem
            )
        }
    }
}

@Composable
private fun MetalsChoiceQuestion(onDeclaresMetalsChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)) {
        Text(
            text = stringResource(R.string.zakat_metals_choice_heading),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.zakat_metals_choice_explainer),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ChoiceCard(
            title = stringResource(R.string.zakat_metals_skip_title),
            detail = stringResource(R.string.zakat_metals_skip_detail),
            onClick = { onDeclaresMetalsChange(false) }
        )
        ChoiceCard(
            title = stringResource(R.string.zakat_metals_declare_title),
            detail = stringResource(R.string.zakat_metals_declare_detail),
            onClick = { onDeclaresMetalsChange(true) }
        )
    }
}

@Composable
private fun ChoiceCard(title: String, detail: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = false, role = Role.RadioButton, onClick = onClick),
        shape = SalatiShapeTokens.Card,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = SalatiSpacing.md, vertical = SalatiSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = false,
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

@Composable
private fun AddPieceRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 52.dp)
            .padding(horizontal = SalatiSpacing.lg, vertical = SalatiSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * One piece of jewellery: a line when it is not being edited, five controls when it is.
 *
 * Every piece used to carry all five at once - a title, a separate description field that
 * repeated the title, a "Purity" label, a chip row and a weight field - so two pieces
 * filled the screen and the totals they added up to were never in sight.
 */
@Composable
private fun MetalItem(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    defaultLabel: String,
    label: String,
    weightGrams: Double,
    itemKey: String,
    summaryText: String,
    pureWeightText: String,
    onLabelChange: (String) -> Unit,
    onWeightChange: (Double) -> Unit,
    onRemove: () -> Unit,
    purityPicker: @Composable () -> Unit
) {
    // Same reasoning as MoneyRow: the gram totals beside this are locale-formatted.
    val displayLocale = LocalConfiguration.current.locales[0]
    var weightText by rememberSaveable(itemKey) {
        mutableStateOf(if (weightGrams > 0.0) trimTrailingZeros(weightGrams, displayLocale) else "")
    }
    // The name needs a local copy for the same reason the weight does, and for a while it
    // did not have one: label edits take the coalesced write path, which by design holds
    // the settings unchanged until typing stops. A field bound straight to the stored
    // value therefore got its own character handed back to it as the empty string on the
    // next frame, so nothing could be typed into it at all.
    var labelText by rememberSaveable(itemKey) { mutableStateOf(label) }
    val shownLabel = labelText.ifBlank { defaultLabel }

    if (!expanded) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = { onExpandedChange(true) })
                .defaultMinSize(minHeight = 62.dp)
                .padding(start = SalatiSpacing.lg, end = SalatiSpacing.sm, top = SalatiSpacing.xs, bottom = SalatiSpacing.xs)
                // Merged so the row is one thing to a screen reader - its name, what it is
                // made of and what it weighs - rather than four separate stops.
                .semantics(mergeDescendants = true) {},
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = SalatiSpacing.xs)) {
                Text(
                    text = shownLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = weightWithUnit(weightText, displayLocale),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = stringResource(R.string.zakat_item_edit),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(
                start = SalatiSpacing.lg,
                end = SalatiSpacing.xs,
                top = SalatiSpacing.xs,
                bottom = SalatiSpacing.sm
            ),
        verticalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UnderlinedField(
                value = labelText,
                onValueChange = { raw ->
                    labelText = raw
                    onLabelChange(raw)
                },
                placeholder = stringResource(R.string.zakat_item_label_hint),
                contentDescription = stringResource(R.string.zakat_item_label),
                textStyle = MaterialTheme.typography.titleMedium,
                imeAction = ImeAction.Next,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onExpandedChange(false) }) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.zakat_item_collapse),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.zakat_item_remove),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }

        purityPicker()

        Row(
            modifier = Modifier.fillMaxWidth().padding(end = SalatiSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                UnderlinedField(
                    value = weightText,
                    onValueChange = { raw ->
                        weightText = raw
                        onWeightChange(ZakatCalculator.parseAmount(raw) ?: 0.0)
                    },
                    placeholder = java.lang.String.format(displayLocale, "%.2f", 0.0),
                    contentDescription = stringResource(R.string.zakat_item_weight),
                    textStyle = MaterialTheme.typography.headlineMedium,
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                    modifier = Modifier.widthIn(min = 96.dp, max = 148.dp)
                )
                Spacer(modifier = Modifier.width(SalatiSpacing.xxs))
                Text(
                    text = stringResource(R.string.zakat_item_weight_unit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            Text(
                text = pureWeightText,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

/** The weight as it reads on a collapsed row: "22.00 g", or nothing at all while unset. */
@Composable
private fun weightWithUnit(weightText: String, locale: java.util.Locale): String {
    val unit = stringResource(R.string.zakat_item_weight_unit)
    val shown = weightText.ifBlank { java.lang.String.format(locale, "%.2f", 0.0) }
    return "$shown $unit"
}

/**
 * A text field with nothing around it but a rule underneath. Used where a boxed field
 * would be a second border inside a card that already has one.
 */
@Composable
private fun UnderlinedField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    contentDescription: String,
    textStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next
) {
    val accent = MaterialTheme.colorScheme.primary
    val rule = MaterialTheme.colorScheme.outlineVariant
    var focused by remember { mutableStateOf(false) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(accent),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        modifier = modifier
            .drawBehind {
                val y = size.height - 1.dp.toPx()
                drawLine(
                    color = if (focused) accent else rule,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = if (focused) 2.dp.toPx() else 1.dp.toPx()
                )
            }
            .padding(bottom = 4.dp)
            .semantics { this.contentDescription = contentDescription }
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = textStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            inner()
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PurityChipRow(
    options: List<Pair<Int, String>>,
    selectedValue: Int,
    onSelect: (Int) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(end = SalatiSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.xxs)
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
 * Not to be confused with [trimTrailingZeros], which fills a text field and stays at the
 * display locale on purpose: that string has to survive being parsed back.
 */
@Composable
internal fun formatGrams(value: Double): String {
    val locale = LocalConfiguration.current.locales[0]
    return java.lang.String.format(locale, "%.2f", value)
}

// ---------------------------------------------------------------------------
// Step 4 - summary
// ---------------------------------------------------------------------------

@Composable
internal fun TotalsRow(
    label: String,
    value: String,
    emphasised: Boolean = false,
    valueColor: Color? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = if (emphasised) {
                MaterialTheme.typography.bodyLarge
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (emphasised) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1.1f)
        )
        Spacer(modifier = Modifier.size(SalatiSpacing.xs))
        Text(
            text = value,
            style = if (emphasised) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            fontWeight = if (emphasised) FontWeight.Bold else FontWeight.Medium,
            color = valueColor ?: if (emphasised) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.9f, fill = false)
        )
    }
}
