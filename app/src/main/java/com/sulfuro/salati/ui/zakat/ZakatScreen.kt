package com.sulfuro.salati.ui.zakat

import android.content.ActivityNotFoundException
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sulfuro.salati.R
import com.sulfuro.salati.core.zakat.MetalPricesResult
import com.sulfuro.salati.core.zakat.MetalsPriceRepository
import com.sulfuro.salati.core.zakat.ZakatGoldItem
import com.sulfuro.salati.core.zakat.ZakatHawlCalendar
import com.sulfuro.salati.core.zakat.ZakatSilverItem
import com.sulfuro.salati.core.zakat.zakatCurrencySymbolFor
import com.sulfuro.salati.core.zakat.zakatHawlDueDate
import com.sulfuro.salati.core.zakat.zakatHawlStartDate
import com.sulfuro.salati.data.settings.CalculationSettings
import com.sulfuro.salati.data.settings.SalatiPreferences
import com.sulfuro.salati.theme.SalatiSpacing
import com.sulfuro.salati.ui.settings.CurrencySelectionSheet
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
