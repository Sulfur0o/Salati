package com.sulfuro.salati.ui.zakat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
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
import com.sulfuro.salati.theme.SalatiSpacing
import java.util.Locale

/**
 * One piece of jewellery: a line when it is not being edited, five controls when it is.
 *
 * Every piece used to carry all five at once - a title, a separate description field that
 * repeated the title, a "Purity" label, a chip row and a weight field - so two pieces
 * filled the screen and the totals they added up to were never in sight.
 */
@Composable
internal fun MetalItem(
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
                    placeholder = String.format(displayLocale, "%.2f", 0.0),
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
private fun weightWithUnit(weightText: String, locale: Locale): String {
    val unit = stringResource(R.string.zakat_item_weight_unit)
    val shown = weightText.ifBlank { String.format(locale, "%.2f", 0.0) }
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
    textStyle: TextStyle,
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
internal fun PurityChipRow(
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
internal fun goldPurityLabel(purity: GoldPurity): String = when (purity) {
    GoldPurity.K24 -> stringResource(R.string.zakat_purity_24k)
    GoldPurity.K22 -> stringResource(R.string.zakat_purity_22k)
    GoldPurity.K21 -> stringResource(R.string.zakat_purity_21k)
    GoldPurity.K18 -> stringResource(R.string.zakat_purity_18k)
    GoldPurity.K14 -> stringResource(R.string.zakat_purity_14k)
}

@Composable
internal fun silverPurityLabel(purity: SilverPurity): String = when (purity) {
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
    return String.format(locale, "%.2f", value)
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
