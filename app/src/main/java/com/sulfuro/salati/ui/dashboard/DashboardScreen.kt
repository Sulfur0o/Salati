package com.sulfuro.salati.ui.dashboard

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
import com.sulfuro.salati.R
import com.sulfuro.salati.core.computation.AladhanDayData
import com.sulfuro.salati.core.computation.HijriCalendarHelper
import com.sulfuro.salati.core.computation.MonthlyPrayerResult
import com.sulfuro.salati.core.computation.PrayerRepository
import com.sulfuro.salati.core.computation.SalatiPrayerTimes
import com.sulfuro.salati.core.computation.HijriDateParts
import com.sulfuro.salati.data.settings.CalculationSettings
import com.sulfuro.salati.data.settings.safeZoneId
import com.sulfuro.salati.theme.SalatiSpacing
import com.sulfuro.salati.theme.SalatiTypeTokens
import com.sulfuro.salati.ui.components.PrayerTimeRow
import com.sulfuro.salati.core.computation.PrayerDataOrigin
import com.sulfuro.salati.ui.components.SalatiComputedLocallyNotice
import com.sulfuro.salati.ui.components.SalatiErrorState
import com.sulfuro.salati.ui.components.SalatiHeroCard
import com.sulfuro.salati.ui.components.SalatiLoadingState
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
    val zoneId = remember(settings.timezoneId) { settings.safeZoneId() }
    val today = rememberCurrentDashboardDate(zoneId)
    val currentYearMonth = YearMonth.from(today)

    var monthlyData by remember { mutableStateOf<List<AladhanDayData>>(emptyList()) }
    var hijriMetadata by remember { mutableStateOf<Map<LocalDate, HijriDateParts>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var retryTrigger by remember { mutableIntStateOf(0) }
    var timesWereComputedLocally by remember { mutableStateOf(false) }

    LaunchedEffect(currentYearMonth, today, settings.hijriOffset, settings.calculationMethod, settings.highLatitudeRule, settings.madhab, settings.latitude, settings.longitude, retryTrigger) {
        isLoading = true
        val result = PrayerRepository.getMonthlyPrayers(
            context,
            settings,
            currentYearMonth.year,
            currentYearMonth.monthValue
        )
        var dataList = (result as? MonthlyPrayerResult.Success)?.data.orEmpty()
        timesWereComputedLocally =
            (result as? MonthlyPrayerResult.Success)?.origin == PrayerDataOrigin.ON_DEVICE

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
    val is24Hour = com.sulfuro.salati.data.settings.resolveUses24HourClock(
        settings.timeFormat,
        android.text.format.DateFormat.is24HourFormat(context)
    )
    val timeFormat = remember(displayLocale, zoneId, is24Hour) { dashboardTimeFormatter(displayLocale, zoneId, is24Hour) }
    val dateFormat = remember(displayLocale) { dashboardDateFormatter(displayLocale) }

    val isAfterMaghrib = rememberIsAfterMaghrib(prayerTimes?.maghrib, zoneId)
    val hijriDate = remember(today, settings.hijriOffset, isAfterMaghrib, hijriMetadata) {
        HijriCalendarHelper.resolveHijriDate(today, settings.hijriOffset, isAfterMaghrib) { date ->
            hijriMetadata[date]
        }
    }

    val nextPrayerInfo = if (prayerTimes != null) {
        val effectiveTomorrowFajr = tomorrowTimes?.fajr ?: prayerTimes.fajr.plus(java.time.Duration.ofDays(1))
        rememberNextPrayerInfo(prayerTimes, effectiveTomorrowFajr)
    } else {
        null
    }

    when {
        isLoading -> SalatiLoadingState(
            label = stringResource(R.string.daily_loading),
            modifier = modifier
        )
        prayerTimes == null || nextPrayerInfo == null -> SalatiErrorState(
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
            val activeNextPrayer = nextPrayerInfo
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
                    .padding(horizontal = SalatiSpacing.md, vertical = SalatiSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(SalatiSpacing.xs)
            ) {
                DailyScreenHeader(
                    title = stringResource(R.string.daily_today),
                    gregorianDate = dateFormat.format(today),
                    hijriDate = hijriDate.format(),
                    locationContext = stringResource(R.string.daily_location_context, settings.cityName),
                    onOpenQibla = onOpenQibla
                )

                if (timesWereComputedLocally) {
                    SalatiComputedLocallyNotice(
                        label = stringResource(R.string.daily_computed_locally)
                    )
                }

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
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(SalatiSpacing.xxs)
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
                        .padding(bottom = SalatiSpacing.xs),
                    shape = com.sulfuro.salati.theme.SalatiShapeTokens.Card,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SalatiSpacing.md, vertical = SalatiSpacing.xs),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(0.95f)) {
                            Text(
                                text = stringResource(R.string.daily_middle_of_night),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = timeFormat.format(prayerTimes.middleOfTheNight),
                                style = SalatiTypeTokens.PrayerTime.copy(fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp)),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(
                            modifier = Modifier.weight(1.05f),
                            horizontalAlignment = androidx.compose.ui.Alignment.End
                        ) {
                            Text(
                                text = stringResource(R.string.daily_last_third_of_night),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = timeFormat.format(prayerTimes.lastThirdOfTheNight),
                                style = SalatiTypeTokens.PrayerTime.copy(fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp)),
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
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1.1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.semantics { heading() },
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                androidx.compose.material3.IconButton(
                    onClick = onOpenQibla,
                    modifier = Modifier.size(36.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Explore,
                        contentDescription = stringResource(R.string.qibla_open),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Text(
                text = gregorianDate,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        Column(
            modifier = Modifier.weight(0.9f),
            horizontalAlignment = androidx.compose.ui.Alignment.End,
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                text = hijriDate,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
            Text(
                text = locationContext,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                textAlign = TextAlign.End
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
    val totalSecs = maxOf(0L, ms / 1000)
    val hours = totalSecs / 3600
    val minutes = (totalSecs % 3600) / 60
    val seconds = totalSecs % 60
    return String.format(Locale.ROOT, "%02dh %02dm %02ds", hours, minutes, seconds)
}

internal fun zonedDateAt(epochMillis: Long, zoneId: ZoneId): LocalDate {
    return Instant.ofEpochMilli(epochMillis)
        .atZone(zoneId)
        .toLocalDate()
}

internal fun dashboardTimeFormatter(locale: Locale, zoneId: ZoneId, is24Hour: Boolean = true): DateTimeFormatter {
    val pattern = if (is24Hour) "HH:mm" else "h:mm a"
    return DateTimeFormatter.ofPattern(pattern, locale)
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
