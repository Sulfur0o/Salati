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
import androidx.compose.foundation.selection.selectable
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sulfuro.salati.R
import com.sulfuro.salati.core.zakat.MetalPricesResult
import com.sulfuro.salati.core.zakat.MetalsPriceRepository
import com.sulfuro.salati.core.zakat.ZakatCalculator
import com.sulfuro.salati.core.zakat.ZakatGoldItem
import com.sulfuro.salati.core.zakat.ZakatHawlCalendar
import com.sulfuro.salati.core.zakat.ZakatSilverItem
import com.sulfuro.salati.core.zakat.zakatCurrencyOptions
import com.sulfuro.salati.core.zakat.zakatCurrencySymbolFor
import com.sulfuro.salati.core.zakat.zakatHawlDueDate
import com.sulfuro.salati.core.zakat.zakatHawlStartDate
import com.sulfuro.salati.data.settings.CalculationSettings
import com.sulfuro.salati.data.settings.SalatiPreferences
import com.sulfuro.salati.theme.SalatiShapeTokens
import com.sulfuro.salati.theme.SalatiSpacing
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

/**
 * How long a fetched gold/silver quote is treated as current.
 *
 * The rate source republishes once a day, so anything shorter buys nothing. Six hours
 * keeps a price the user looks at in the evening from being the one fetched that morning
 * while still costing at most a handful of requests a day.
 */
private const val PRICE_FRESHNESS_MILLIS = 6L * 60 * 60 * 1000

/**
 * Whether the stored metal prices are worth replacing before showing them.
 *
 * This screen is destroyed and rebuilt on every tab switch, so the refresh it runs on
 * first composition ran on *every visit* - two HTTP requests to a third-party CDN, a
 * spinner, and a settings write, for numbers that change once a day. `pricesUpdatedAt` was
 * being stored and never consulted; this is what consults it.
 */
internal fun metalPricesAreStale(
    settings: CalculationSettings,
    nowMillis: Long = System.currentTimeMillis()
): Boolean {
    val zakat = settings.zakat
    if (zakat.goldPrice <= 0.0 || zakat.silverPrice <= 0.0) return true
    // Quotes are per currency, so one fetched in another currency is not an answer here.
    if (!zakat.pricesCurrencyCode.equals(zakat.currencyCode, ignoreCase = true)) return true
    if (zakat.pricesUpdatedAt <= 0L) return true
    return nowMillis - zakat.pricesUpdatedAt >= PRICE_FRESHNESS_MILLIS
}

private fun CalculationSettings.withGoldItem(edited: ZakatGoldItem): CalculationSettings =
    copy(zakat = zakat.copy(goldItems = zakat.goldItems.map { if (it.id == edited.id) edited else it }))

private fun CalculationSettings.withSilverItem(edited: ZakatSilverItem): CalculationSettings =
    copy(zakat = zakat.copy(silverItems = zakat.silverItems.map { if (it.id == edited.id) edited else it }))

/**
 * Four-step Zakat walkthrough.
 *
 * The assessment is long enough that a single scrolling form buries the parts that
 * actually need thought, so it is split into: settle the basis, enter liquid wealth,
 * itemise precious metals at their real purities, then review and optionally book the next
 * Hawl in the user's own calendar.
 *
 * The amount due rides in the header on every step rather than waiting at the end. It is
 * the thing people open this tab for, and keeping it three taps away meant typing an
 * amount gave no feedback at all until the walkthrough was finished.
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
    var showDatePicker by remember { mutableStateOf(false) }

    suspend fun refreshMetalPrices() {
        isRefreshingPrices = true
        priceRefreshFailed = false
        when (val result = MetalsPriceRepository.fetchLatestPrices(settings.zakat.currencyCode)) {
            is MetalPricesResult.Success -> update {
                it.copy(zakat = it.zakat.copy(goldPrice = result.prices.goldPricePerGram, silverPrice = result.prices.silverPricePerGram, pricesUpdatedAt = result.prices.fetchedAtMillis, pricesCurrencyCode = result.prices.currencyCode, pricesRateDate = result.prices.rateDate))
            }
            MetalPricesResult.Unavailable -> priceRefreshFailed = true
        }
        isRefreshingPrices = false
    }

    // Prices are quoted per currency, so a currency change invalidates what is stored -
    // but simply arriving on this screen does not. The manual refresh button beside the
    // figures is the way to insist.
    LaunchedEffect(settings.zakat.currencyCode) {
        if (metalPricesAreStale(settings)) refreshMetalPrices()
    }

    val assessment = rememberZakatAssessment(settings)

    val hawlStart = remember(settings.zakat.hawlStartEpochDay) {
        settings.zakat.hawlStartEpochDay?.let(LocalDate::ofEpochDay)
    }
    val hawlDue = remember(hawlStart) { hawlStart?.let(::zakatHawlDueDate) }
    val calendarTitle = stringResource(R.string.zakat_hawl_calendar_title)
    val calendarDescription = stringResource(R.string.zakat_hawl_calendar_description)
    val calendarUnavailable = stringResource(R.string.zakat_hawl_calendar_unavailable)
    val saveHawlToCalendar = {
        val dueDate = hawlDue ?: ZakatHawlCalendar.dueDateFrom(LocalDate.now())
        val intent = ZakatHawlCalendar.buildInsertIntent(
            title = calendarTitle,
            description = calendarDescription,
            dueDate = dueDate
        )
        try {
            context.startActivity(intent)
        } catch (notFound: ActivityNotFoundException) {
            Toast.makeText(context, calendarUnavailable, Toast.LENGTH_LONG).show()
        }
    }

    val stepTitles = listOf(
        stringResource(R.string.zakat_step_1_title),
        stringResource(R.string.zakat_step_2_title),
        stringResource(R.string.zakat_step_3_title),
        stringResource(R.string.zakat_step_4_title)
    )

    val goToStep = { target: Int ->
        step = target.coerceIn(0, ZAKAT_STEP_COUNT - 1)
        // One scroll state is shared across the steps, so without this, moving on from the
        // bottom of a long step opened the next one part-way down.
        scope.launch { scrollState.animateScrollTo(0) }
        Unit
    }

    Column(modifier = modifier.fillMaxSize()) {
        ZakatHeader(
            step = step,
            stepTitles = stepTitles,
            dueValue = if (assessment.isEligible) {
                formatAmount(assessment.zakatDue, true)
            } else {
                stringResource(R.string.zakat_due_below_nisab)
            },
            isEligible = assessment.isEligible,
            onJumpToStep = goToStep
        )

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
                            goldThresholdText = formatAmount(assessment.goldThreshold, true),
                            silverThresholdText = formatAmount(assessment.silverThreshold, true),
                            nisabThresholdText = formatAmount(assessment.nisabThreshold, true),
                            ratesSummary = {
                                MetalRatesRow(
                                    isRefreshing = isRefreshingPrices,
                                    updatedAtMillis = settings.zakat.pricesUpdatedAt,
                                    rateDate = settings.zakat.pricesRateDate,
                                    hasFailed = priceRefreshFailed,
                                    goldPriceText = formatAmount(settings.zakat.goldPrice, false),
                                    silverPriceText = formatAmount(settings.zakat.silverPrice, false),
                                    onRefresh = { scope.launch { refreshMetalPrices() } }
                                )
                            }
                        )

                        1 -> ZakatCashStep(
                            currencySymbol = currencySymbol,
                            currencyCode = settings.zakat.currencyCode,
                            cashOnHand = settings.zakat.cashOnHand,
                            bankBalance = settings.zakat.bankBalance,
                            investments = settings.zakat.investments,
                            receivables = settings.zakat.receivables,
                            businessInventory = settings.zakat.businessInventory,
                            liabilities = settings.zakat.liabilities,
                            subtotalText = formatAmount(assessment.liquidAssets, true),
                            onCashOnHandChange = { v -> updateWhileTyping { it.copy(zakat = it.zakat.copy(cashOnHand = v)) } },
                            onBankBalanceChange = { v -> updateWhileTyping { it.copy(zakat = it.zakat.copy(bankBalance = v)) } },
                            onInvestmentsChange = { v -> updateWhileTyping { it.copy(zakat = it.zakat.copy(investments = v)) } },
                            onReceivablesChange = { v -> updateWhileTyping { it.copy(zakat = it.zakat.copy(receivables = v)) } },
                            onBusinessInventoryChange = { v -> updateWhileTyping { it.copy(zakat = it.zakat.copy(businessInventory = v)) } },
                            onLiabilitiesChange = { v -> updateWhileTyping { it.copy(zakat = it.zakat.copy(liabilities = v)) } }
                        )

                        2 -> ZakatMetalsStep(
                            declaresMetals = settings.zakat.declaresMetals
                                ?: if (declaresMetalsOrDefault(settings)) true else null,
                            onDeclaresMetalsChange = { declaring ->
                                update { it.copy(zakat = it.zakat.copy(declaresMetals = declaring)) }
                            },
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
                            assessment = assessment,
                            formatAmount = formatAmount,
                            hawlStart = hawlStart,
                            hawlDue = hawlDue,
                            onChangeHawlDate = { showDatePicker = true }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(SalatiSpacing.xl))
            }
        }

        ZakatStepNavigation(
            step = step,
            nextLabel = if (step < ZAKAT_STEP_COUNT - 1) {
                stepTitles[step + 1]
            } else {
                stringResource(R.string.zakat_hawl_remind)
            },
            nextIsCalendarHandoff = step == ZAKAT_STEP_COUNT - 1,
            onBack = { goToStep(step - 1) },
            onNext = {
                if (step == ZAKAT_STEP_COUNT - 1) saveHawlToCalendar() else goToStep(step + 1)
            }
        )
    }

    if (showCurrencySheet) {
        CurrencySelectionSheet(
            selectedCode = settings.zakat.currencyCode,
            onSelect = { code -> update { it.copy(zakat = it.zakat.copy(currencyCode = code)) } },
            onDismiss = { showCurrencySheet = false }
        )
    }

    if (showDatePicker) {
        HawlDatePickerDialog(
            // The date asked for is the one the row shows: when Zakat next falls due. An
            // unset Hawl opened on today, which is never the answer - it is the one date
            // that cannot be a due date - so every user had to page forward a year by hand.
            initialDate = hawlDue ?: ZakatHawlCalendar.dueDateFrom(LocalDate.now()),
            canClear = hawlStart != null,
            onDismiss = { showDatePicker = false },
            onPicked = { due ->
                // What is stored is still the start, which is what the calendar badge and
                // the milestone are derived from.
                update {
                    it.copy(
                        zakat = it.zakat.copy(
                            hawlStartEpochDay = due?.let(::zakatHawlStartDate)?.toEpochDay()
                        )
                    )
                }
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Derived assessment
// ---------------------------------------------------------------------------

internal data class ZakatAssessment(
    val standard: Int,
    val liquidAssets: Double,
    val declaresMetals: Boolean,
    val pureGoldGrams: Double,
    val fineSilverGrams: Double,
    val goldValue: Double,
    val silverValue: Double,
    val grossAssets: Double,
    val liabilities: Double,
    val netWealth: Double,
    val goldThreshold: Double,
    val silverThreshold: Double,
    val nisabThreshold: Double,
    val isEligible: Boolean,
    val zakatDue: Double,
    val pricesMatchCurrency: Boolean
)

@Composable
private fun rememberZakatAssessment(settings: CalculationSettings): ZakatAssessment =
    remember(settings) { computeAssessment(settings) }

/**
 * Whether gold and silver count, for someone who has not been asked yet.
 *
 * The question is new, so every existing install arrives with no answer on file. Anyone
 * who had already listed pieces plainly meant to declare them, and it would be wrong to
 * quietly drop them from a figure the user has already seen - so having pieces is taken
 * as the answer until they say otherwise. An empty list means the question is genuinely
 * open, and nothing is owed on metals while it stays that way.
 */
internal fun declaresMetalsOrDefault(settings: CalculationSettings): Boolean =
    settings.zakat.declaresMetals
        ?: (settings.zakat.goldItems.isNotEmpty() || settings.zakat.silverItems.isNotEmpty())

/**
 * Pure derivation of the whole assessment, kept out of the composables so the arithmetic
 * can be exercised directly in tests.
 */
internal fun computeAssessment(settings: CalculationSettings): ZakatAssessment {
    val liquid = settings.zakat.cashOnHand +
        settings.zakat.bankBalance +
        settings.zakat.investments +
        settings.zakat.receivables +
        settings.zakat.businessInventory

    // Pieces stay on file when the user chooses to skip, so that changing their mind
    // costs nothing - but nothing they entered counts while the answer is no.
    val declaringMetals = declaresMetalsOrDefault(settings)
    val pureGold = if (declaringMetals) {
        ZakatCalculator.totalPureGoldWeight(settings.zakat.goldItems)
    } else {
        0.0
    }
    val fineSilver = if (declaringMetals) {
        ZakatCalculator.totalFineSilverWeight(settings.zakat.silverItems)
    } else {
        0.0
    }
    val goldValue = ZakatCalculator.valueForPureWeight(pureGold, settings.zakat.goldPrice)
    val silverValue = ZakatCalculator.valueForPureWeight(fineSilver, settings.zakat.silverPrice)

    // Both are derived, not just the chosen one: step 1 shows each standard beside the
    // threshold it produces, so the choice is between two amounts rather than two names.
    val goldThreshold =
        ZakatCalculator.calculateNisabValue(settings.zakat.nisabGram, settings.zakat.goldPrice)
    val silverThreshold =
        ZakatCalculator.calculateNisabValue(settings.zakat.nisabSilverGram, settings.zakat.silverPrice)
    val nisab = if (settings.zakat.standard == STANDARD_SILVER) silverThreshold else goldThreshold

    val result = ZakatCalculator.computeZakat(
        cash = settings.zakat.cashOnHand + settings.zakat.bankBalance,
        goldValue = goldValue,
        silverValue = silverValue,
        otherAssets = settings.zakat.investments +
            settings.zakat.receivables +
            settings.zakat.businessInventory,
        shortTermLiabilities = settings.zakat.liabilities,
        nisabThreshold = nisab
    )

    return ZakatAssessment(
        standard = settings.zakat.standard,
        liquidAssets = liquid,
        declaresMetals = declaringMetals,
        pureGoldGrams = pureGold,
        fineSilverGrams = fineSilver,
        goldValue = goldValue,
        silverValue = silverValue,
        grossAssets = result.totalAssets,
        liabilities = settings.zakat.liabilities,
        netWealth = result.netWealth,
        goldThreshold = goldThreshold,
        silverThreshold = silverThreshold,
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
// Step 4 - summary, eligibility and the Hawl record
// ---------------------------------------------------------------------------

@Composable
private fun ZakatSummaryStep(
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
private fun HawlDatePickerDialog(
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

/**
 * The step you are on, and what you owe.
 *
 * This replaced five stacked rows: a "Zakat" headline the tab bar already carried, a
 * progress bar, a "Step 2 of 4 - Cash & assets" line, a heading that said the same thing
 * again, and an explainer paragraph. Two rows now, and the second of them is the answer
 * the tab exists to give - live, on every step, rather than three taps away.
 */
@Composable
private fun ZakatHeader(
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
private fun ZakatStepNavigation(
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
private fun MetalRatesRow(
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
