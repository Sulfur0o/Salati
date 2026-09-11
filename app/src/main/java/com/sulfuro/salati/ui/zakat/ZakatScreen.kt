package com.sulfuro.salati.ui.zakat

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sulfuro.salati.R
import com.sulfuro.salati.core.zakat.MetalPricesResult
import com.sulfuro.salati.core.zakat.MetalsPriceRepository
import com.sulfuro.salati.core.zakat.ZakatCalculator
import com.sulfuro.salati.core.zakat.ZakatGoldItem
import com.sulfuro.salati.core.zakat.ZakatHawlCalendar
import com.sulfuro.salati.core.zakat.ZakatSilverItem
import com.sulfuro.salati.core.zakat.zakatCurrencySymbolFor
import com.sulfuro.salati.core.zakat.zakatCurrencyOptions
import com.sulfuro.salati.core.zakat.zakatHawlDueDate
import com.sulfuro.salati.data.settings.CalculationSettings
import com.sulfuro.salati.data.settings.SalatiPreferences
import com.sulfuro.salati.theme.SalatiShapeTokens
import com.sulfuro.salati.theme.SalatiSpacing
import com.sulfuro.salati.ui.components.StatusPill
import com.sulfuro.salati.ui.settings.CurrencySelectionSheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

internal const val ZAKAT_STEP_COUNT = 4

/**
 * How long typing has to stop before the assessment is written to disk. Long enough to
 * swallow a burst of keystrokes, short enough that leaving the screen straight after the
 * last character still finds the edit queued rather than lost.
 */
private const val TYPING_SETTLE_MILLIS = 400L

private fun CalculationSettings.withGoldItem(edited: ZakatGoldItem): CalculationSettings =
    copy(zakat = zakat.copy(goldItems = zakat.goldItems.map { if (it.id == edited.id) edited else it }))

private fun CalculationSettings.withSilverItem(edited: ZakatSilverItem): CalculationSettings =
    copy(zakat = zakat.copy(silverItems = zakat.silverItems.map { if (it.id == edited.id) edited else it }))

/**
 * Four-step Zakat walkthrough.
 *
 * The assessment is long enough that a single scrolling form buries the parts that
 * actually need thought, so it is split into: pick a Nisab standard, enter liquid
 * wealth, itemise precious metals at their real purities, then review and optionally
 * book the next Hawl in the user's own calendar.
 *
 * Every answer is persisted to [SalatiPreferences] rather than held in screen state: the
 * tab bar tears this screen down on every switch, and the assessment is revisited a lunar
 * year later, so losing the inputs is the one outcome worth spending writes to avoid.
 * Typing is coalesced rather than written per character - see [TYPING_SETTLE_MILLIS] - and
 * whatever is still queued when the screen goes away is handed to a scope that outlives it.
 */
@Composable
fun ZakatScreen(
    settings: CalculationSettings,
    preferences: SalatiPreferences,
    stepState: MutableState<Int>,
    modifier: Modifier = Modifier
) {
    var step by stepState
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val displayLocale = LocalConfiguration.current.locales[0]

    val currencySymbol = zakatCurrencySymbolFor(settings.zakat.currencyCode)
    val formatAmount = remember(displayLocale, currencySymbol) {
        { amount: Double, grouping: Boolean ->
            val pattern = if (grouping) "%,.2f" else "%.2f"
            currencySymbol + " " + java.lang.String.format(displayLocale, pattern, amount)
        }
    }

    // Text fields fire a transform on every keystroke, and each write serialises the whole
    // settings document - jewellery lists included - and fsyncs it. Typing "1250" is four
    // of those. So keystroke edits are queued and flushed once typing stops, while
    // discrete actions (choosing a currency, adding or deleting an item, picking a date)
    // still write immediately, because their result has to appear on screen at once.
    //
    // Ordering is kept by composing whatever is queued into the immediate write rather
    // than racing it: a pending weight edit followed by "remove this item" applies in
    // that order, and never resurrects the item.
    val pendingEdit = remember { mutableStateOf<((CalculationSettings) -> CalculationSettings)?>(null) }

    val update = remember(preferences, scope) {
        { transform: (CalculationSettings) -> CalculationSettings ->
            val queued = pendingEdit.value
            pendingEdit.value = null
            val composed = if (queued == null) transform else { c: CalculationSettings -> transform(queued(c)) }
            scope.launch { preferences.updateSettings(composed) }
            Unit
        }
    }

    val updateWhileTyping = remember {
        { transform: (CalculationSettings) -> CalculationSettings ->
            val queued = pendingEdit.value
            pendingEdit.value =
                if (queued == null) transform else { c: CalculationSettings -> transform(queued(c)) }
        }
    }

    LaunchedEffect(pendingEdit.value) {
        val transform = pendingEdit.value ?: return@LaunchedEffect
        delay(TYPING_SETTLE_MILLIS)
        preferences.updateSettings(transform)
        // Only clear what was actually written. Anything typed since is a newer queue
        // that has already restarted this effect.
        if (pendingEdit.value === transform) pendingEdit.value = null
    }

    DisposableEffect(preferences) {
        onDispose {
            // Switching tabs destroys this screen mid-edit, taking `scope` with it, so the
            // last few characters have to be written by someone who outlives it.
            pendingEdit.value?.let { preferences.updateSettingsDetached(it) }
            pendingEdit.value = null
        }
    }

    var showCurrencySheet by remember { mutableStateOf(false) }
    var isRefreshingPrices by remember { mutableStateOf(false) }
    var priceRefreshFailed by remember { mutableStateOf(false) }

    suspend fun refreshMetalPrices() {
        isRefreshingPrices = true
        priceRefreshFailed = false
        when (val result = MetalsPriceRepository.fetchLatestPrices(settings.zakat.currencyCode)) {
            is MetalPricesResult.Success -> update {
                it.copy(zakat = it.zakat.copy(goldPrice = result.prices.goldPricePerGram, silverPrice = result.prices.silverPricePerGram, pricesUpdatedAt = result.prices.fetchedAtMillis, pricesCurrencyCode = result.prices.currencyCode))
            }
            MetalPricesResult.Unavailable -> priceRefreshFailed = true
        }
        isRefreshingPrices = false
    }

    // Prices are quoted per currency, so a currency change invalidates what is stored.
    LaunchedEffect(settings.zakat.currencyCode) { refreshMetalPrices() }

    val assessment = rememberZakatAssessment(settings)

    Column(modifier = modifier.fillMaxSize()) {
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
            ZakatStepIndicator(currentStep = step)
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = SalatiSpacing.md, vertical = SalatiSpacing.xs)
            ) {
                if (!assessment.pricesMatchCurrency) {
                    CurrencyMismatchWarning(settings)
                    Spacer(modifier = Modifier.height(SalatiSpacing.sm))
                }

                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        val forward = targetState > initialState
                        val offset = if (forward) 1 else -1
                        (slideInHorizontally(tween(250)) { width -> offset * width / 4 } + fadeIn(tween(250)))
                            .togetherWith(
                                slideOutHorizontally(tween(200)) { width -> -offset * width / 4 } +
                                    fadeOut(tween(200))
                            )
                    },
                    label = "zakatStepContent"
                ) { targetStep ->
                    when (targetStep) {
                        0 -> ZakatStandardStep(
                            selectedStandard = assessment.standard,
                            onSelectStandard = { value -> update { it.copy(zakat = it.zakat.copy(standard = value)) } },
                            currencyLabel = currencyLabelFor(settings.zakat.currencyCode),
                            onOpenCurrency = { showCurrencySheet = true },
                            nisabThresholdText = formatAmount(assessment.nisabThreshold, true),
                            priceSummary = {
                                PriceRefreshHeader(
                                    isRefreshing = isRefreshingPrices,
                                    updatedAtMillis = settings.zakat.pricesUpdatedAt,
                                    hasFailed = priceRefreshFailed,
                                    onRefresh = { scope.launch { refreshMetalPrices() } }
                                )
                            }
                        )

                        1 -> ZakatCashStep(
                            currencySymbol = currencySymbol,
                            cashOnHand = settings.zakat.cashOnHand,
                            bankBalance = settings.zakat.bankBalance,
                            investments = settings.zakat.investments,
                            receivables = settings.zakat.receivables,
                            liabilities = settings.zakat.liabilities,
                            subtotalText = formatAmount(assessment.liquidAssets, true),
                            onCashOnHandChange = { v -> updateWhileTyping { it.copy(zakat = it.zakat.copy(cashOnHand = v)) } },
                            onBankBalanceChange = { v -> updateWhileTyping { it.copy(zakat = it.zakat.copy(bankBalance = v)) } },
                            onInvestmentsChange = { v -> updateWhileTyping { it.copy(zakat = it.zakat.copy(investments = v)) } },
                            onReceivablesChange = { v -> updateWhileTyping { it.copy(zakat = it.zakat.copy(receivables = v)) } },
                            onLiabilitiesChange = { v -> updateWhileTyping { it.copy(zakat = it.zakat.copy(liabilities = v)) } }
                        )

                        2 -> ZakatMetalsStep(
                            goldItems = settings.zakat.goldItems,
                            silverItems = settings.zakat.silverItems,
                            totalPureGoldText = stringResource(
                                R.string.zakat_gold_total_pure,
                                formatGrams(assessment.pureGoldGrams)
                            ),
                            totalFineSilverText = stringResource(
                                R.string.zakat_silver_total_fine,
                                formatGrams(assessment.fineSilverGrams)
                            ),
                            onAddGoldItem = {
                                update {
                                    it.copy(zakat = it.zakat.copy(goldItems = it.zakat.goldItems +
                                            ZakatGoldItem(id = UUID.randomUUID().toString())))
                                }
                            },
                            onUpdateGoldItem = { edited ->
                                update { current -> current.withGoldItem(edited) }
                            },
                            onEditGoldItemText = { edited ->
                                updateWhileTyping { current -> current.withGoldItem(edited) }
                            },
                            onRemoveGoldItem = { id ->
                                update { current ->
                                    current.copy(zakat = current.zakat.copy(goldItems = current.zakat.goldItems.filterNot { it.id == id }))
                                }
                            },
                            onAddSilverItem = {
                                update {
                                    it.copy(zakat = it.zakat.copy(silverItems = it.zakat.silverItems +
                                            ZakatSilverItem(id = UUID.randomUUID().toString())))
                                }
                            },
                            onUpdateSilverItem = { edited ->
                                update { current -> current.withSilverItem(edited) }
                            },
                            onEditSilverItemText = { edited ->
                                updateWhileTyping { current -> current.withSilverItem(edited) }
                            },
                            onRemoveSilverItem = { id ->
                                update { current ->
                                    current.copy(zakat = current.zakat.copy(silverItems = current.zakat.silverItems.filterNot { it.id == id }))
                                }
                            }
                        )

                        else -> ZakatSummaryStep(
                            settings = settings,
                            assessment = assessment,
                            formatAmount = formatAmount,
                            onStartDateChanged = { date ->
                                update { it.copy(zakat = it.zakat.copy(hawlStartEpochDay = date?.toEpochDay())) }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(SalatiSpacing.xl))
            }
        }

        ZakatStepNavigation(
            step = step,
            onBack = { step = (step - 1).coerceAtLeast(0) },
            onNext = { step = (step + 1).coerceAtMost(ZAKAT_STEP_COUNT - 1) }
        )
    }

    if (showCurrencySheet) {
        CurrencySelectionSheet(
            selectedCode = settings.zakat.currencyCode,
            onSelect = { code -> update { it.copy(zakat = it.zakat.copy(currencyCode = code)) } },
            onDismiss = { showCurrencySheet = false }
        )
    }
}

// ---------------------------------------------------------------------------
// Derived assessment
// ---------------------------------------------------------------------------

internal data class ZakatAssessment(
    val standard: Int,
    val liquidAssets: Double,
    val pureGoldGrams: Double,
    val fineSilverGrams: Double,
    val goldValue: Double,
    val silverValue: Double,
    val grossAssets: Double,
    val liabilities: Double,
    val netWealth: Double,
    val nisabThreshold: Double,
    val isEligible: Boolean,
    val zakatDue: Double,
    val pricesMatchCurrency: Boolean
)

@Composable
private fun rememberZakatAssessment(settings: CalculationSettings): ZakatAssessment =
    remember(settings) { computeAssessment(settings) }

/**
 * Pure derivation of the whole assessment, kept out of the composables so the arithmetic
 * can be exercised directly in tests.
 */
internal fun computeAssessment(settings: CalculationSettings): ZakatAssessment {
    val liquid = settings.zakat.cashOnHand +
        settings.zakat.bankBalance +
        settings.zakat.investments +
        settings.zakat.receivables

    val pureGold = ZakatCalculator.totalPureGoldWeight(settings.zakat.goldItems)
    val fineSilver = ZakatCalculator.totalFineSilverWeight(settings.zakat.silverItems)
    val goldValue = ZakatCalculator.valueForPureWeight(pureGold, settings.zakat.goldPrice)
    val silverValue = ZakatCalculator.valueForPureWeight(fineSilver, settings.zakat.silverPrice)

    val nisab = if (settings.zakat.standard == STANDARD_SILVER) {
        ZakatCalculator.calculateNisabValue(settings.zakat.nisabSilverGram, settings.zakat.silverPrice)
    } else {
        ZakatCalculator.calculateNisabValue(settings.zakat.nisabGram, settings.zakat.goldPrice)
    }

    val result = ZakatCalculator.computeZakat(
        cash = settings.zakat.cashOnHand + settings.zakat.bankBalance,
        goldValue = goldValue,
        silverValue = silverValue,
        otherAssets = settings.zakat.investments + settings.zakat.receivables,
        shortTermLiabilities = settings.zakat.liabilities,
        nisabThreshold = nisab
    )

    return ZakatAssessment(
        standard = settings.zakat.standard,
        liquidAssets = liquid,
        pureGoldGrams = pureGold,
        fineSilverGrams = fineSilver,
        goldValue = goldValue,
        silverValue = silverValue,
        grossAssets = result.totalAssets,
        liabilities = settings.zakat.liabilities,
        netWealth = result.netWealth,
        nisabThreshold = result.nisabThreshold,
        isEligible = result.isEligible,
        zakatDue = result.zakatDue,
        pricesMatchCurrency = ZakatCalculator.doPricesMatchCurrency(
            settings.zakat.pricesCurrencyCode,
            settings.zakat.currencyCode
        )
    )
}

// ---------------------------------------------------------------------------
// Step 4 - summary, eligibility badge and Hawl calendar hand-off
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZakatSummaryStep(
    settings: CalculationSettings,
    assessment: ZakatAssessment,
    formatAmount: (Double, Boolean) -> String,
    onStartDateChanged: (LocalDate?) -> Unit
) {
    val context = LocalContext.current
    val displayLocale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(displayLocale) {
        DateTimeFormatter.ofPattern("d MMM uuuu", displayLocale)
    }
    val hawlStart = remember(settings.zakat.hawlStartEpochDay) {
        settings.zakat.hawlStartEpochDay?.let(LocalDate::ofEpochDay)
    }
    val hawlDue = remember(hawlStart) { hawlStart?.let(::zakatHawlDueDate) }
    var showDatePicker by remember { mutableStateOf(false) }

    val calendarTitle = stringResource(R.string.zakat_hawl_calendar_title)
    val calendarDescription = stringResource(R.string.zakat_hawl_calendar_description)
    val calendarUnavailable = stringResource(R.string.zakat_hawl_calendar_unavailable)

    Column(verticalArrangement = Arrangement.spacedBy(SalatiSpacing.md)) {
        StepHeading(
            title = stringResource(R.string.zakat_summary_heading),
            explainer = stringResource(R.string.zakat_summary_breakdown)
        )

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = SalatiShapeTokens.Card,
            color = MaterialTheme.colorScheme.surfaceVariant,
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
                TotalsRow(
                    label = stringResource(R.string.zakat_summary_liabilities),
                    value = formatAmount(assessment.liabilities, true)
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

        EligibilityCard(
            isEligible = assessment.isEligible,
            zakatDueText = formatAmount(assessment.zakatDue, true),
            belowNisabText = stringResource(
                R.string.zakat_summary_below_nisab,
                formatAmount(assessment.nisabThreshold, true)
            )
        )

        // Hawl milestone: the in-app record plus a hand-off to the user's own calendar.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = SalatiShapeTokens.Card,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(SalatiSpacing.md),
                verticalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)
            ) {
                Text(
                    text = stringResource(R.string.hawl_milestone_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (hawlStart != null && hawlDue != null) {
                        stringResource(
                            R.string.hawl_milestone_summary,
                            dateFormatter.format(hawlDue),
                            dateFormatter.format(hawlStart)
                        )
                    } else {
                        stringResource(R.string.hawl_milestone_not_set)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)) {
                    TextButton(onClick = { showDatePicker = true }) {
                        Text(stringResource(R.string.hawl_set_date))
                    }
                    if (hawlStart != null) {
                        TextButton(onClick = { onStartDateChanged(null) }) {
                            Text(stringResource(R.string.hawl_clear_date))
                        }
                    }
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                val calendarDueDate = hawlDue ?: ZakatHawlCalendar.dueDateFrom(LocalDate.now())
                Text(
                    text = stringResource(
                        R.string.zakat_hawl_calendar_helper,
                        dateFormatter.format(calendarDueDate)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(
                    onClick = {
                        val intent = ZakatHawlCalendar.buildInsertIntent(
                            title = calendarTitle,
                            description = calendarDescription,
                            dueDate = calendarDueDate
                        )
                        try {
                            context.startActivity(intent)
                        } catch (notFound: ActivityNotFoundException) {
                            Toast.makeText(context, calendarUnavailable, Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Event,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(SalatiSpacing.xs))
                    Text(stringResource(R.string.zakat_hawl_calendar_action))
                }
            }
        }
    }

    if (showDatePicker) {
        val initialDate = hawlStart ?: LocalDate.now()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDate.toEpochDay() * 86_400_000L
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            onStartDateChanged(
                                Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()
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

@Composable
private fun EligibilityCard(
    isEligible: Boolean,
    zakatDueText: String,
    belowNisabText: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = SalatiShapeTokens.Card,
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(SalatiSpacing.md),
            verticalArrangement = Arrangement.spacedBy(SalatiSpacing.xs)
        ) {
            if (isEligible) {
                StatusPill(
                    text = stringResource(R.string.zakat_summary_due_badge),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
                Text(
                    text = zakatDueText,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Text(
                    text = belowNisabText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Chrome: step indicator, navigation, price header
// ---------------------------------------------------------------------------

@Composable
private fun ZakatStepIndicator(currentStep: Int) {
    val stepTitles = listOf(
        stringResource(R.string.zakat_step_1_title),
        stringResource(R.string.zakat_step_2_title),
        stringResource(R.string.zakat_step_3_title),
        stringResource(R.string.zakat_step_4_title)
    )
    val label = stringResource(
        R.string.zakat_step_accessibility,
        currentStep + 1,
        ZAKAT_STEP_COUNT,
        stepTitles[currentStep.coerceIn(0, ZAKAT_STEP_COUNT - 1)]
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(SalatiSpacing.xxs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(ZAKAT_STEP_COUNT) { index ->
                val reached = index <= currentStep
                val weight by animateFloatAsState(
                    targetValue = if (reached) 1f else 0.35f,
                    label = "zakatStepSegment$index"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = weight),
                            shape = RoundedCornerShape(2.dp)
                        )
                )
            }
        }
        Text(
            text = stringResource(R.string.zakat_step_indicator, currentStep + 1, ZAKAT_STEP_COUNT) +
                " · " + stepTitles[currentStep.coerceIn(0, ZAKAT_STEP_COUNT - 1)],
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ZakatStepNavigation(
    step: Int,
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
                enabled = step < ZAKAT_STEP_COUNT - 1,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    stringResource(
                        if (step == ZAKAT_STEP_COUNT - 2) {
                            R.string.zakat_step_finish
                        } else {
                            R.string.zakat_step_next
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun PriceRefreshHeader(
    isRefreshing: Boolean,
    updatedAtMillis: Long,
    hasFailed: Boolean,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
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
            },
            modifier = Modifier.weight(1f)
        )
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
private fun CurrencyMismatchWarning(settings: CalculationSettings) {
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

private fun currencyLabelFor(code: String): String {
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
