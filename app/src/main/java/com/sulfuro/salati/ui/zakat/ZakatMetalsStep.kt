package com.sulfuro.salati.ui.zakat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sulfuro.salati.R
import com.sulfuro.salati.core.zakat.GoldPurity
import com.sulfuro.salati.core.zakat.SilverPurity
import com.sulfuro.salati.core.zakat.ZakatCalculator
import com.sulfuro.salati.core.zakat.ZakatGoldItem
import com.sulfuro.salati.core.zakat.ZakatSilverItem
import com.sulfuro.salati.theme.SalatiShapeTokens
import com.sulfuro.salati.theme.SalatiSpacing

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
