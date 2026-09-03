package io.github.sulfuro25.salati.ui.dashboard

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import io.github.sulfuro25.salati.R
import io.github.sulfuro25.salati.core.computation.AladhanDayData
import io.github.sulfuro25.salati.core.computation.HijriCalendarHelper
import io.github.sulfuro25.salati.core.computation.MonthlyPrayerResult
import io.github.sulfuro25.salati.core.computation.PrayerRepository
import io.github.sulfuro25.salati.core.computation.SalatiPrayerTimes
import io.github.sulfuro25.salati.core.computation.HijriDateParts
import io.github.sulfuro25.salati.data.settings.CalculationSettings
import io.github.sulfuro25.salati.theme.SalatiSpacing
import io.github.sulfuro25.salati.theme.SalatiTypeTokens
import io.github.sulfuro25.salati.ui.components.PrayerTimeRow
import io.github.sulfuro25.salati.ui.components.SalatiErrorState
import io.github.sulfuro25.salati.ui.components.SalatiHeroCard
import io.github.sulfuro25.salati.ui.components.SalatiLoadingState
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun DashboardScreen(
    settings: CalculationSettings,
    onOpenQibla: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val zoneId = remember(settings.timezoneId) { ZoneId.of(settings.timezoneId) }
    val today = rememberCurrentDashboardDate(zoneId)
    val currentYearMonth = YearMonth.from(today)

    var monthlyData by remember { mutableStateOf<List<AladhanDayData>>(emptyList()) }
    var hijriMetadata by remember { mutableStateOf<Map<LocalDate, HijriDateParts>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var retryTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(currentYearMonth, today, settings.hijriOffset, settings.calculationMethod, settings.highLatitudeRule, settings.madhab, settings.latitude, settings.longitude, retryTrigger) {
        isLoading = true
        val result = PrayerRepository.getMonthlyPrayers(
            context,
            settings,
            currentYearMonth.year,
            currentYearMonth.monthValue
        )
        var dataList = (result as? MonthlyPrayerResult.Success)?.data.orEmpty()

        // Handle month-end boundary: fetch next month's day 1 for true astronomical Fajr
        val tomorrow = today.plusDays(1)
        val tomorrowMonth = YearMonth.from(tomorrow)
        if (tomorrowMonth != currentYearMonth) {
            val nextMonthResult = PrayerRepository.getMonthlyPrayers(
                context,
                settings,
                tomorrowMonth.year,
                tomorrowMonth.monthValue
            )
            if (nextMonthResult is MonthlyPrayerResult.Success) {
                dataList = dataList + nextMonthResult.data
            }
        }
        monthlyData = dataList

        hijriMetadata = PrayerRepository.getHijriMetadataRange(
            context = context,
            settings = settings,
            startDate = today,
            endDate = tomorrow
        )
        isLoading = false
    }

    val monthlyDataByDate = remember(monthlyData) {
        PrayerRepository.indexPrayerDataByDate(monthlyData)
    }

    val prayerTimes = remember(monthlyDataByDate, today, settings) {
        monthlyDataByDate[today]?.let {
            runCatching { PrayerRepository.parsePrayerTimes(it, settings) }.getOrNull()
        }
    }

    val tomorrowTimes = remember(monthlyDataByDate, today, settings) {
        val tomorrowData = monthlyDataByDate[today.plusDays(1)]
        if (tomorrowData != null) {
            runCatching { PrayerRepository.parsePrayerTimes(tomorrowData, settings) }.getOrNull()
        } else {
            prayerTimes?.let(::addExactDashboardFallbackDay)
        }
    }

    val displayLocale = LocalConfiguration.current.locales[0]
    val timeFormat = remember(displayLocale, zoneId) { dashboardTimeFormatter(displayLocale, zoneId) }
    val dateFormat = remember(displayLocale) { dashboardDateFormatter(displayLocale) }

    val isAfterMaghrib = rememberIsAfterMaghrib(prayerTimes?.maghrib, zoneId)
    val hijriDate = remember(today, settings.hijriOffset, isAfterMaghrib, hijriMetadata) {
        HijriCalendarHelper.resolveHijriDate(today, settings.hijriOffset, isAfterMaghrib) { date ->
            hijriMetadata[date]
        }
    }

    val nextPrayerInfo = if (prayerTimes != null && tomorrowTimes != null) {
        rememberNextPrayerInfo(prayerTimes, tomorrowTimes.fajr)
    } else {
        null
    }

    when {
        isLoading -> SalatiLoadingState(
            label = stringResource(R.string.daily_loading),
            modifier = modifier
        )
        prayerTimes == null -> SalatiErrorState(
            title = stringResource(R.string.daily_error_title),
            message = stringResource(R.string.daily_error_message),
            retryLabel = stringResource(R.string.daily_retry),
            onRetry = {
                isLoading = true
                retryTrigger++
            },
            modifier = modifier
        )
        else -> {
            val activeNextPrayer = checkNotNull(nextPrayerInfo)
            val nextEventName = stringResource(activeNextPrayer.event.labelRes)
            val nextEventTime = timeFormat.format(activeNextPrayer.eventInstant)
            val nextEventIsDisplayOnly = activeNextPrayer.event == DailyEvent.SUNRISE
            val heroAccessibility = if (nextEventIsDisplayOnly) {
                stringResource(
                    R.string.daily_event_display_only_accessibility,
                    nextEventName,
                    nextEventTime
                )
            } else {
                stringResource(
                    R.string.daily_event_accessibility,
                    nextEventName,
                    nextEventTime
                )
            }

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = SalatiSpacing.md, vertical = SalatiSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)
            ) {
                DailyScreenHeader(
                    title = stringResource(R.string.daily_today),
                    gregorianDate = dateFormat.format(today),
                    hijriDate = hijriDate.format(),
                    locationContext = stringResource(R.string.daily_location_context, settings.cityName),
                    onOpenQibla = onOpenQibla
                )

                SalatiHeroCard(
                    eventLabel = stringResource(R.string.daily_next_event),
                    eventName = nextEventName,
                    eventTime = nextEventTime,
                    countdownTarget = activeNextPrayer.eventInstant,
                    countdownFormatter = ::formatCountdown,
                    accessibilityDescription = heroAccessibility,
                    displayOnlyLabel = if (nextEventIsDisplayOnly) {
                        stringResource(R.string.daily_display_only)
                    } else {
                        null
                    }
                )

                Column(
                    modifier = Modifier.padding(top = SalatiSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(SalatiSpacing.xs)
                ) {
                    val entries = listOf(
                        DailyEvent.FAJR to prayerTimes.fajr,
                        DailyEvent.SUNRISE to prayerTimes.sunrise,
                        DailyEvent.DHUHR to prayerTimes.dhuhr,
                        DailyEvent.ASR to prayerTimes.asr,
                        DailyEvent.MAGHRIB to prayerTimes.maghrib,
                        DailyEvent.ISHA to prayerTimes.isha
                    )
                    entries.forEach { (event, instant) ->
                        val name = stringResource(event.labelRes)
                        val time = timeFormat.format(instant)
                        val isNext = event == activeNextPrayer.event
                        val isDisplayOnly = event == DailyEvent.SUNRISE
                        val semanticState = when {
                            isNext && isDisplayOnly -> stringResource(
                                R.string.daily_prayer_current_display_only_accessibility,
                                name,
                                time
                            )
                            isNext -> stringResource(
                                R.string.daily_prayer_current_accessibility,
                                name,
                                time
                            )
                            isDisplayOnly -> stringResource(
                                R.string.daily_prayer_display_only_accessibility,
                                name,
                                time
                            )
                            else -> stringResource(
                                R.string.daily_prayer_accessibility,
                                name,
                                time
                            )
                        }
                        PrayerTimeRow(
                            name = name,
                            time = time,
                            isCurrent = isNext,
                            isDisplayOnly = isDisplayOnly,
                            semanticState = semanticState
                        )
                    }
                }

                // Compact inline night calculations row at the bottom
                androidx.compose.material3.Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = SalatiSpacing.sm),
                    shape = io.github.sulfuro25.salati.theme.SalatiShapeTokens.Card,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SalatiSpacing.md, vertical = SalatiSpacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.daily_middle_of_night),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = timeFormat.format(prayerTimes.middleOfTheNight),
                                style = SalatiTypeTokens.PrayerTime.copy(fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp)),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = androidx.compose.ui.Alignment.End
                        ) {
                            Text(
                                text = stringResource(R.string.daily_last_third_of_night),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End
                            )
                            Text(
                                text = timeFormat.format(prayerTimes.lastThirdOfTheNight),
                                style = SalatiTypeTokens.PrayerTime.copy(fontSize = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp)),
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DailyScreenHeader(
    title: String,
    gregorianDate: String,
    hijriDate: String,
    locationContext: String,
    onOpenQibla: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = androidx.compose.ui.Alignment.Bottom
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() }
                )
                androidx.compose.material3.IconButton(
                    onClick = onOpenQibla,
                    modifier = Modifier.size(48.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Explore,
                        contentDescription = stringResource(R.string.qibla_open),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = gregorianDate,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Column(
            horizontalAlignment = androidx.compose.ui.Alignment.End,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = hijriDate,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(
                text = locationContext,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal enum class DailyEvent(@param:StringRes val labelRes: Int) {
    FAJR(R.string.prayer_fajr),
    SUNRISE(R.string.prayer_sunrise),
    DHUHR(R.string.prayer_dhuhr),
    ASR(R.string.prayer_asr),
    MAGHRIB(R.string.prayer_maghrib),
    ISHA(R.string.prayer_isha)
}

internal data class NextPrayerInfo(
    val event: DailyEvent,
    val eventInstant: Instant,
    val remainingMs: Long,
    val currentEvent: DailyEvent
)

internal fun getNextPrayer(
    times: SalatiPrayerTimes,
    tomorrowFajr: Instant,
    now: Instant
): NextPrayerInfo {
    val prayers = listOf(
        DailyEvent.FAJR to times.fajr,
        DailyEvent.SUNRISE to times.sunrise,
        DailyEvent.DHUHR to times.dhuhr,
        DailyEvent.ASR to times.asr,
        DailyEvent.MAGHRIB to times.maghrib,
        DailyEvent.ISHA to times.isha
    )

    for (index in prayers.indices) {
        val (event, prayerInstant) = prayers[index]
        if (prayerInstant > now) {
            val currentEvent = if (index == 0) DailyEvent.ISHA else prayers[index - 1].first
            return NextPrayerInfo(
                event = event,
                eventInstant = prayerInstant,
                remainingMs = Duration.between(now, prayerInstant).toMillis(),
                currentEvent = currentEvent
            )
        }
    }

    return NextPrayerInfo(
        event = DailyEvent.FAJR,
        eventInstant = tomorrowFajr,
        remainingMs = Duration.between(now, tomorrowFajr).toMillis(),
        currentEvent = DailyEvent.ISHA
    )
}

internal fun formatCountdown(ms: Long): String {
    val totalSecs = ms / 1000
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return String.format(Locale.US, "%02dh %02dm %02ds", hours, minutes, seconds)
}

internal fun zonedDateAt(epochMillis: Long, zoneId: ZoneId): LocalDate {
    return Instant.ofEpochMilli(epochMillis)
        .atZone(zoneId)
        .toLocalDate()
}

internal fun dashboardTimeFormatter(locale: Locale, zoneId: ZoneId): DateTimeFormatter {
    return DateTimeFormatter.ofPattern("HH:mm", locale)
        .withZone(zoneId)
}

internal fun dashboardDateFormatter(locale: Locale): DateTimeFormatter {
    return DateTimeFormatter.ofPattern("EEEE, d MMMM uuuu", locale)
}

internal fun addExactDashboardFallbackDay(times: SalatiPrayerTimes): SalatiPrayerTimes {
    val elapsedDay = Duration.ofHours(24)
    return times.copy(
        fajr = times.fajr.plus(elapsedDay),
        sunrise = times.sunrise.plus(elapsedDay),
        dhuhr = times.dhuhr.plus(elapsedDay),
        asr = times.asr.plus(elapsedDay),
        maghrib = times.maghrib.plus(elapsedDay),
        isha = times.isha.plus(elapsedDay)
    )
}

@Composable
private fun rememberCurrentDashboardDate(zoneId: ZoneId): LocalDate {
    return produceState(initialValue = LocalDate.now(zoneId), zoneId) {
        while (true) {
            val now = Instant.now()
            value = now.atZone(zoneId).toLocalDate()
            val nextMidnight = value.plusDays(1).atStartOfDay(zoneId).toInstant()
            delay(Duration.between(now, nextMidnight).toMillis().coerceAtLeast(1_000L) + 100L)
        }
    }.value
}

@Composable
private fun rememberIsAfterMaghrib(maghrib: Instant?, zoneId: ZoneId): Boolean {
    val initialNow = Instant.now()
    val initialValue = maghrib != null &&
        maghrib.atZone(zoneId).toLocalDate() == initialNow.atZone(zoneId).toLocalDate() &&
        !initialNow.isBefore(maghrib)
    return produceState(initialValue = initialValue, maghrib, zoneId) {
        while (true) {
            val now = Instant.now()
            val localDate = now.atZone(zoneId).toLocalDate()
            value = maghrib != null &&
                maghrib.atZone(zoneId).toLocalDate() == localDate &&
                !now.isBefore(maghrib)
            val nextBoundary = if (maghrib != null && now.isBefore(maghrib)) {
                maghrib
            } else {
                localDate.plusDays(1).atStartOfDay(zoneId).toInstant()
            }
            delay(Duration.between(now, nextBoundary).toMillis().coerceAtLeast(1_000L) + 100L)
        }
    }.value
}

@Composable
private fun rememberNextPrayerInfo(
    prayerTimes: SalatiPrayerTimes,
    tomorrowFajr: Instant
): NextPrayerInfo {
    return produceState(
        initialValue = getNextPrayer(prayerTimes, tomorrowFajr, Instant.now()),
        prayerTimes,
        tomorrowFajr
    ) {
        while (true) {
            val now = Instant.now()
            val nextPrayer = getNextPrayer(prayerTimes, tomorrowFajr, now)
            value = nextPrayer
            delay(
                Duration.between(now, nextPrayer.eventInstant)
                    .toMillis()
                    .coerceAtLeast(1_000L) + 100L
            )
        }
    }.value
}
