package com.sulfuro.salati.ui.zakat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sulfuro.salati.core.zakat.ZakatCalculator
import com.sulfuro.salati.theme.SalatiShapeTokens
import com.sulfuro.salati.theme.SalatiSpacing

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

internal fun trimTrailingZeros(value: Double, locale: java.util.Locale): String {
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
