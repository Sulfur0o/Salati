package io.github.sulfuro25.salati.ui.zakat

import io.github.sulfuro25.salati.core.computation.ZakatCalculator
import io.github.sulfuro25.salati.core.computation.zakatCurrencySymbolFor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sulfuro25.salati.R
import io.github.sulfuro25.salati.data.settings.CalculationSettings
import io.github.sulfuro25.salati.data.settings.SalatiPreferences
import io.github.sulfuro25.salati.theme.SalatiShapeTokens
import io.github.sulfuro25.salati.theme.SalatiSpacing
import io.github.sulfuro25.salati.core.computation.zakatHawlDueDate
import io.github.sulfuro25.salati.ui.components.SalatiSectionCard
import io.github.sulfuro25.salati.ui.components.StatusPill
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ZakatScreen(
    settings: CalculationSettings,
    preferences: SalatiPreferences,
    selectedStandardState: MutableState<Int>,
    cashState: MutableState<String>,
    goldWeightState: MutableState<String>,
    silverWeightState: MutableState<String>,
    modifier: Modifier = Modifier
) {
    var selectedStandard by selectedStandardState

    val calculatorScrollState = rememberScrollState()

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = SalatiSpacing.md, end = SalatiSpacing.md, bottom = 4.dp)
        ) {
            Text(
                text = stringResource(R.string.zakat_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() }
            )
            Text(
                text = stringResource(R.string.zakat_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            CalculatorTabContent(
                settings = settings,
                preferences = preferences,
                scrollState = calculatorScrollState,
                selectedStandard = selectedStandard,
                onSelectStandard = { selectedStandard = it },
                cashState = cashState,
                goldWeightState = goldWeightState,
                silverWeightState = silverWeightState
            )
        }
    }
}

@Composable
private fun PriceRefreshHeader(
    label: String,
    isRefreshing: Boolean,
    updatedAtMillis: Long,
    hasFailed: Boolean,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                modifier = Modifier.size(48.dp)
            ) {
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
        val statusText = when {
            isRefreshing -> stringResource(R.string.zakat_price_updating)
            hasFailed -> stringResource(R.string.zakat_price_update_failed)
            updatedAtMillis > 0L -> stringResource(
                R.string.zakat_price_updated_at,
                formatPriceTimestamp(updatedAtMillis)
            )
            else -> stringResource(R.string.zakat_price_never_updated)
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = if (hasFailed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

private fun formatPriceTimestamp(epochMillis: Long): String {
    val formatter = java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.MEDIUM,
        java.text.DateFormat.SHORT
    )
    return formatter.format(java.util.Date(epochMillis))
}

@Composable
private fun ZakatDueRow(
    label: String,
    amountText: String,
    isNisabReached: Boolean,
    labelStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    labelWeight: FontWeight = FontWeight.SemiBold,
    amountStyle: TextStyle = LocalTextStyle.current,
    amountWeight: FontWeight = FontWeight.Bold
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = labelStyle, fontWeight = labelWeight)
            Text(
                text = amountText,
                style = amountStyle,
                fontWeight = amountWeight,
                color = if (isNisabReached) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        StatusPill(
            text = stringResource(if (isNisabReached) R.string.zakat_status_nisab_reached else R.string.zakat_status_below_nisab),
            containerColor = if (isNisabReached) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            contentColor = if (isNisabReached) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun CalculatorTabContent(
    settings: CalculationSettings,
    preferences: SalatiPreferences,
    scrollState: ScrollState,
    selectedStandard: Int,
    onSelectStandard: (Int) -> Unit,
    cashState: MutableState<String>,
    goldWeightState: MutableState<String>,
    silverWeightState: MutableState<String>
) {
    val scope = rememberCoroutineScope()
    val parseDouble = { input: String -> ZakatCalculator.parseAmount(input) ?: 0.0 }
    val pricesMatchCurrency = ZakatCalculator.doPricesMatchCurrency(
        settings.zakatPricesCurrencyCode,
        settings.zakatCurrencyCode
    )
    val currencySymbol = io.github.sulfuro25.salati.core.computation.zakatCurrencySymbolFor(settings.zakatCurrencyCode)
    val pricePerGramUnit = "$currencySymbol/g"

    val goldPrice = settings.zakatGoldPrice
    val goldCarat = if (settings.zakatGoldCarat in setOf(24, 21, 18, 14, 10)) {
        settings.zakatGoldCarat
    } else {
        24
    }
    val effectiveGoldPrice = ZakatCalculator.calculateEffectiveCaratPrice(goldPrice, goldCarat)
    var goldPriceInput by remember(goldPrice) {
        mutableStateOf(String.format(Locale.US, "%.2f", goldPrice))
    }

    // Silver sits near 1.5 per gram, so it needs more decimals than gold to stay accurate
    // across the 595g Nisab threshold.
    var silverPriceInput by remember(settings.zakatSilverPrice) {
        mutableStateOf(String.format(Locale.US, "%.3f", settings.zakatSilverPrice))
    }

    var cashVal by cashState
    var goldJewelryWeight by goldWeightState
    var silverJewelryWeight by silverWeightState

    val goldNisabValue = settings.zakatNisabGram * goldPrice
    val silverNisabValue = settings.zakatNisabSilverGram * settings.zakatSilverPrice

    val activeNisabValue = if (selectedStandard == 0) goldNisabValue else silverNisabValue

    val cashAssets = parseDouble(cashVal)
    val goldWeight = parseDouble(goldJewelryWeight)
    val estimatedGoldValue = goldWeight * effectiveGoldPrice
    val silverWeight = parseDouble(silverJewelryWeight)
    val estimatedSilverValue = silverWeight * settings.zakatSilverPrice

    // In Islamic jurisprudence, Nisab is evaluated on the aggregate of all zakatable
    // wealth (Cash + Gold + Silver). If total wealth reaches the Nisab threshold,
    // Zakat (2.5%) is due across all qualifying assets.
    val totalWealth = cashAssets + estimatedGoldValue + estimatedSilverValue
    val isTotalNisabReached = totalWealth >= activeNisabValue && totalWealth > 0.0

    val isCashNisabReached = isTotalNisabReached && cashAssets > 0.0
    val cashZakatDue = if (isTotalNisabReached) cashAssets * 0.025 else 0.0

    val isGoldJewelryNisabReached = isTotalNisabReached && goldWeight > 0.0
    val goldJewelryZakatDue = if (isTotalNisabReached) estimatedGoldValue * 0.025 else 0.0

    val isSilverJewelryNisabReached = isTotalNisabReached && silverWeight > 0.0
    val silverJewelryZakatDue = if (isTotalNisabReached) estimatedSilverValue * 0.025 else 0.0

    val totalZakatDue = if (isTotalNisabReached) totalWealth * 0.025 else 0.0

    val updatePreferences = { transform: (CalculationSettings) -> CalculationSettings ->
        scope.launch {
            preferences.updateSettings(transform)
        }
    }

    var isRefreshingPrices by remember { mutableStateOf(false) }
    var priceRefreshFailed by remember { mutableStateOf(false) }

    suspend fun refreshMetalPrices() {
        isRefreshingPrices = true
        priceRefreshFailed = false
        when (
            val result = io.github.sulfuro25.salati.core.computation.MetalsPriceRepository
                .fetchLatestPrices(settings.zakatCurrencyCode)
        ) {
            is io.github.sulfuro25.salati.core.computation.MetalPricesResult.Success -> {
                updatePreferences {
                    it.copy(
                        zakatGoldPrice = result.prices.goldPricePerGram,
                        zakatSilverPrice = result.prices.silverPricePerGram,
                        zakatPricesUpdatedAt = result.prices.fetchedAtMillis,
                        zakatPricesCurrencyCode = result.prices.currencyCode
                    )
                }
            }
            io.github.sulfuro25.salati.core.computation.MetalPricesResult.Unavailable -> {
                priceRefreshFailed = true
            }
        }
        isRefreshingPrices = false
    }

    // Re-fetch whenever the selected currency changes, so displayed prices are always
    // quoted in the currency the amounts are labelled with.
    LaunchedEffect(settings.zakatCurrencyCode) { refreshMetalPrices() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = SalatiSpacing.md, end = SalatiSpacing.md, top = 4.dp, bottom = SalatiSpacing.md),
        verticalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)
    ) {
        if (!pricesMatchCurrency) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = SalatiShapeTokens.Control
            ) {
                Row(
                    modifier = Modifier.padding(SalatiSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Stored metal prices were quoted in ${settings.zakatPricesCurrencyCode}. Please tap 'Refresh' to calculate accurately in ${settings.zakatCurrencyCode}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            val cardBg = MaterialTheme.colorScheme.surfaceVariant
            val borderColor = MaterialTheme.colorScheme.outline
            val isGold = selectedStandard == 0
            val isSilver = selectedStandard == 1
            val goldTabBorderPath = remember { Path() }
            val silverTabBorderPath = remember { Path() }

            // Top-docked arched tabs outside the price card
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(49.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Gold Tab (Half-circle / arched, outside the card)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (isGold) 49.dp else 48.dp)
                        .zIndex(if (isGold) 2f else 0f)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp))
                        .background(if (isGold) cardBg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        .clickable { onSelectStandard(0) }
                        .drawBehind {
                            val strokePx = 1.dp.toPx()
                            val r = 16.dp.toPx()
                            val w = size.width
                            val h = size.height
                            val path = goldTabBorderPath.apply {
                                reset()
                                if (isGold) {
                                    moveTo(strokePx / 2f, h + 2.dp.toPx())
                                    lineTo(strokePx / 2f, r)
                                    arcTo(
                                        rect = Rect(strokePx / 2f, strokePx / 2f, strokePx / 2f + 2 * r, strokePx / 2f + 2 * r),
                                        startAngleDegrees = 180f,
                                        sweepAngleDegrees = 90f,
                                        forceMoveTo = false
                                    )
                                    lineTo(w - strokePx / 2f - r, strokePx / 2f)
                                    arcTo(
                                        rect = Rect(w - strokePx / 2f - 2 * r, strokePx / 2f, w - strokePx / 2f, strokePx / 2f + 2 * r),
                                        startAngleDegrees = 270f,
                                        sweepAngleDegrees = 90f,
                                        forceMoveTo = false
                                    )
                                    lineTo(w - strokePx / 2f, h + 2.dp.toPx())
                                } else {
                                    moveTo(strokePx / 2f, h - strokePx / 2f)
                                    lineTo(strokePx / 2f, r)
                                    arcTo(
                                        rect = Rect(strokePx / 2f, strokePx / 2f, strokePx / 2f + 2 * r, strokePx / 2f + 2 * r),
                                        startAngleDegrees = 180f,
                                        sweepAngleDegrees = 90f,
                                        forceMoveTo = false
                                    )
                                    lineTo(w - strokePx / 2f - r, strokePx / 2f)
                                    arcTo(
                                        rect = Rect(w - strokePx / 2f - 2 * r, strokePx / 2f, w - strokePx / 2f, strokePx / 2f + 2 * r),
                                        startAngleDegrees = 270f,
                                        sweepAngleDegrees = 90f,
                                        forceMoveTo = false
                                    )
                                    lineTo(w - strokePx / 2f, h - strokePx / 2f)
                                    lineTo(strokePx / 2f, h - strokePx / 2f)
                                    close()
                                }
                            }
                            drawPath(
                                path,
                                color = if (isGold) borderColor else borderColor.copy(alpha = 0.5f),
                                style = Stroke(width = strokePx)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.zakat_tab_gold),
                        style = if (isGold) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isGold) FontWeight.ExtraBold else FontWeight.Normal,
                        color = if (isGold) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = if (isGold) 0.5.sp else 0.sp
                    )
                }

                // Silver Tab (Half-circle / arched, outside the card)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(if (isSilver) 49.dp else 48.dp)
                        .zIndex(if (isSilver) 2f else 0f)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 0.dp))
                        .background(if (isSilver) cardBg else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                        .clickable { onSelectStandard(1) }
                        .drawBehind {
                            val strokePx = 1.dp.toPx()
                            val r = 16.dp.toPx()
                            val w = size.width
                            val h = size.height
                            val path = silverTabBorderPath.apply {
                                reset()
                                if (isSilver) {
                                    moveTo(strokePx / 2f, h + 2.dp.toPx())
                                    lineTo(strokePx / 2f, r)
                                    arcTo(
                                        rect = Rect(strokePx / 2f, strokePx / 2f, strokePx / 2f + 2 * r, strokePx / 2f + 2 * r),
                                        startAngleDegrees = 180f,
                                        sweepAngleDegrees = 90f,
                                        forceMoveTo = false
                                    )
                                    lineTo(w - strokePx / 2f - r, strokePx / 2f)
                                    arcTo(
                                        rect = Rect(w - strokePx / 2f - 2 * r, strokePx / 2f, w - strokePx / 2f, strokePx / 2f + 2 * r),
                                        startAngleDegrees = 270f,
                                        sweepAngleDegrees = 90f,
                                        forceMoveTo = false
                                    )
                                    lineTo(w - strokePx / 2f, h + 2.dp.toPx())
                                } else {
                                    moveTo(strokePx / 2f, h - strokePx / 2f)
                                    lineTo(strokePx / 2f, r)
                                    arcTo(
                                        rect = Rect(strokePx / 2f, strokePx / 2f, strokePx / 2f + 2 * r, strokePx / 2f + 2 * r),
                                        startAngleDegrees = 180f,
                                        sweepAngleDegrees = 90f,
                                        forceMoveTo = false
                                    )
                                    lineTo(w - strokePx / 2f - r, strokePx / 2f)
                                    arcTo(
                                        rect = Rect(w - strokePx / 2f - 2 * r, strokePx / 2f, w - strokePx / 2f, strokePx / 2f + 2 * r),
                                        startAngleDegrees = 270f,
                                        sweepAngleDegrees = 90f,
                                        forceMoveTo = false
                                    )
                                    lineTo(w - strokePx / 2f, h - strokePx / 2f)
                                    lineTo(strokePx / 2f, h - strokePx / 2f)
                                    close()
                                }
                            }
                            drawPath(
                                path,
                                color = if (isSilver) borderColor else borderColor.copy(alpha = 0.5f),
                                style = Stroke(width = strokePx)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.zakat_tab_silver),
                        style = if (isSilver) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSilver) FontWeight.ExtraBold else FontWeight.Normal,
                        color = if (isSilver) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = if (isSilver) 0.5.sp else 0.sp
                    )
                }
            }

            // Current Metal Price Card Body
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = (-1).dp)
                    .zIndex(1f),
                shape = RoundedCornerShape(
                    topStart = 0.dp,
                    topEnd = 0.dp,
                    bottomStart = 16.dp,
                    bottomEnd = 16.dp
                ),
                color = cardBg,
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(SalatiSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedStandard == 0) {
                        PriceRefreshHeader(
                            label = stringResource(R.string.zakat_current_gold_price),
                            isRefreshing = isRefreshingPrices,
                            updatedAtMillis = settings.zakatPricesUpdatedAt,
                            hasFailed = priceRefreshFailed,
                            onRefresh = { scope.launch { refreshMetalPrices() } }
                        )

                        OutlinedTextField(
                            value = goldPriceInput,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                                    goldPriceInput = input
                                    val price = input.toDoubleOrNull()
                                    if (price != null) {
                                        updatePreferences { it.copy(zakatGoldPrice = price) }
                                    }
                                }
                            },
                            label = { Text(stringResource(R.string.zakat_edit_gold_price)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            singleLine = true,
                            trailingIcon = { Text(pricePerGramUnit, modifier = Modifier.padding(end = 8.dp)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = SalatiShapeTokens.Control
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = SalatiShapeTokens.Control,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(stringResource(R.string.zakat_nisab_value_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        stringResource(R.string.zakat_nisab_gold_equiv),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = currencySymbol + " " + String.format(Locale.US, "%,.2f", activeNisabValue),
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    } else {
                        PriceRefreshHeader(
                            label = stringResource(R.string.zakat_current_silver_price),
                            isRefreshing = isRefreshingPrices,
                            updatedAtMillis = settings.zakatPricesUpdatedAt,
                            hasFailed = priceRefreshFailed,
                            onRefresh = { scope.launch { refreshMetalPrices() } }
                        )

                        OutlinedTextField(
                            value = silverPriceInput,
                            onValueChange = { input ->
                                if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,4}$"))) {
                                    silverPriceInput = input
                                    val price = input.toDoubleOrNull()
                                    if (price != null) {
                                        updatePreferences { it.copy(zakatSilverPrice = price) }
                                    }
                                }
                            },
                            label = { Text(stringResource(R.string.zakat_edit_silver_price)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                            singleLine = true,
                            trailingIcon = { Text(pricePerGramUnit, modifier = Modifier.padding(end = 8.dp)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = SalatiShapeTokens.Control
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = SalatiShapeTokens.Control,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(stringResource(R.string.zakat_nisab_value_title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                    Text(stringResource(R.string.zakat_nisab_silver_equiv), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(
                                    text = currencySymbol + " " + String.format(Locale.US, "%,.2f", activeNisabValue),
                                    fontWeight = FontWeight.Black,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        }

        SalatiSectionCard(
            bordered = true,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = SalatiSpacing.md, end = SalatiSpacing.md, top = SalatiSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(18.dp)
                            .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(2.dp))
                    )
                    Text(
                        text = stringResource(R.string.zakat_cash_title),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = SalatiSpacing.md, end = SalatiSpacing.md, bottom = SalatiSpacing.md, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = cashVal,
                        onValueChange = { cashVal = it },
                        label = { Text(stringResource(R.string.zakat_cash_assets_label)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        singleLine = true,
                        trailingIcon = { Text(settings.zakatCurrencyCode, modifier = Modifier.padding(end = 8.dp)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = SalatiShapeTokens.Control
                    )

                    Text(
                        text = stringResource(R.string.zakat_cash_assets_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    ZakatDueRow(
                        label = stringResource(R.string.zakat_cash_due),
                        amountText = currencySymbol + " " + String.format(Locale.US, "%.2f", cashZakatDue),
                        isNisabReached = isCashNisabReached,
                        labelWeight = FontWeight.Bold,
                        amountStyle = MaterialTheme.typography.titleMedium,
                        amountWeight = FontWeight.Black
                    )
                }
            }
        }

        SalatiSectionCard(
            bordered = true,
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = SalatiSpacing.md, end = SalatiSpacing.md, top = SalatiSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(18.dp)
                            .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(2.dp))
                    )
                    Text(
                        text = stringResource(R.string.zakat_jewelry_title),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = SalatiSpacing.md, end = SalatiSpacing.md, bottom = SalatiSpacing.md, top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (selectedStandard == 0) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.zakat_gold_jewelry_subtitle),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = stringResource(R.string.zakat_gold_carat_label),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(24, 21, 18, 14, 10).forEach { carat ->
                                    FilterChip(
                                        selected = goldCarat == carat,
                                        onClick = {
                                            updatePreferences { it.copy(zakatGoldCarat = carat) }
                                        },
                                        label = { Text(stringResource(R.string.zakat_gold_carat_value, carat)) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = goldJewelryWeight,
                                onValueChange = { goldJewelryWeight = it },
                                label = { Text(stringResource(R.string.zakat_gold_jewelry_label)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                singleLine = true,
                                trailingIcon = { Text(stringResource(R.string.zakat_unit_gram), modifier = Modifier.padding(end = 8.dp)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = SalatiShapeTokens.Control
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.zakat_estimated_value), style = MaterialTheme.typography.bodySmall)
                                Text(currencySymbol + " " + String.format(Locale.US, "%.2f", estimatedGoldValue), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                            ZakatDueRow(
                                label = stringResource(R.string.zakat_gold_due),
                                amountText = currencySymbol + " " + String.format(Locale.US, "%.2f", goldJewelryZakatDue),
                                isNisabReached = isGoldJewelryNisabReached
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.zakat_silver_jewelry_subtitle),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(stringResource(R.string.zakat_nisab_value_title), style = MaterialTheme.typography.bodySmall)
                                    Text(stringResource(R.string.zakat_nisab_silver_equiv), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(currencySymbol + " " + String.format(Locale.US, "%.2f", silverNisabValue), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                            OutlinedTextField(
                                value = silverJewelryWeight,
                                onValueChange = { silverJewelryWeight = it },
                                label = { Text(stringResource(R.string.zakat_silver_jewelry_label)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                singleLine = true,
                                trailingIcon = { Text(stringResource(R.string.zakat_unit_gram), modifier = Modifier.padding(end = 8.dp)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = SalatiShapeTokens.Control
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stringResource(R.string.zakat_estimated_value), style = MaterialTheme.typography.bodySmall)
                                Text(currencySymbol + " " + String.format(Locale.US, "%.2f", estimatedSilverValue), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                            }
                            ZakatDueRow(
                                label = stringResource(R.string.zakat_silver_due),
                                amountText = currencySymbol + " " + String.format(Locale.US, "%.2f", silverJewelryZakatDue),
                                isNisabReached = isSilverJewelryNisabReached
                            )
                        }
                    }
                }
            }
        }

        SalatiSectionCard(
            modifier = Modifier.fillMaxWidth(),
            bordered = true,
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(SalatiSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)
            ) {
                Text(
                    text = stringResource(R.string.zakat_total_due_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = currencySymbol + " " + String.format(Locale.US, "%,.2f", totalZakatDue),
                    style = TextStyle(
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Black,
                        fontFeatureSettings = "tnum"
                    ),
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        HawlTrackerCard(
            settings = settings,
            onStartDateChanged = { date ->
                updatePreferences { it.copy(zakatHawlStartEpochDay = date?.toEpochDay()) }
            }
        )

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HawlTrackerCard(
    settings: CalculationSettings,
    onStartDateChanged: (LocalDate?) -> Unit
) {
    val displayLocale = LocalConfiguration.current.locales[0]
    val formatter = remember(displayLocale) {
        DateTimeFormatter.ofPattern("d MMM uuuu", displayLocale)
    }
    val startDate = remember(settings.zakatHawlStartEpochDay) {
        settings.zakatHawlStartEpochDay?.let(LocalDate::ofEpochDay)
    }
    val dueDate = remember(startDate) { startDate?.let(::zakatHawlDueDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    SalatiSectionCard(
        bordered = true,
        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(SalatiSpacing.md),
            verticalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)
        ) {
            Text(
                text = stringResource(R.string.hawl_milestone_title),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (startDate != null && dueDate != null) {
                    stringResource(
                        R.string.hawl_milestone_summary,
                        formatter.format(dueDate),
                        formatter.format(startDate)
                    )
                } else {
                    stringResource(R.string.hawl_milestone_not_set)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)
            ) {
                if (dueDate != null) {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(stringResource(R.string.hawl_set_date))
                    }
                    TextButton(onClick = { onStartDateChanged(null) }) {
                        Text(stringResource(R.string.hawl_clear_date))
                    }
                } else {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(stringResource(R.string.hawl_set_date))
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val initialDate = startDate ?: LocalDate.now()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate.toEpochDay() * 86_400_000L
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedMillis ->
                            onStartDateChanged(
                                Instant.ofEpochMilli(selectedMillis)
                                    .atZone(ZoneId.of("UTC"))
                                    .toLocalDate()
                            )
                        }
                        showDatePicker = false
                    }
                ) {
                    Text(stringResource(R.string.hawl_date_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.hawl_date_cancel))
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}
