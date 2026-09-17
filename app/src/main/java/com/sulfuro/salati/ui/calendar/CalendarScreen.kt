package com.sulfuro.salati.ui.calendar

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.sulfuro.salati.R
import com.sulfuro.salati.core.computation.AladhanDayData
import com.sulfuro.salati.core.computation.HijriCalendarHelper
import com.sulfuro.salati.core.computation.HijriDateParts
import com.sulfuro.salati.core.computation.IslamicCalendarEvent
import com.sulfuro.salati.core.computation.MonthlyPrayerResult
import com.sulfuro.salati.core.computation.PrayerDataOrigin
import com.sulfuro.salati.core.computation.PrayerRepository
import com.sulfuro.salati.core.computation.SalatiPrayerTimes
import com.sulfuro.salati.core.computation.eventsForCalendarDate
import com.sulfuro.salati.core.prayer.byEvent
import com.sulfuro.salati.data.settings.CalculationSettings
import com.sulfuro.salati.data.settings.safeZoneId
import com.sulfuro.salati.theme.SalatiShapeTokens
import com.sulfuro.salati.theme.SalatiSpacing
import com.sulfuro.salati.ui.components.SalatiComputedLocallyNotice
import com.sulfuro.salati.ui.components.SalatiErrorState
import com.sulfuro.salati.ui.components.SalatiLoadingState
import com.sulfuro.salati.ui.components.SalatiSectionCard
import com.sulfuro.salati.ui.format.localDateAt
import com.sulfuro.salati.ui.format.longDateFormatter
import com.sulfuro.salati.ui.format.monthHeading
import com.sulfuro.salati.ui.format.prayerTimeFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun CalendarScreen(
    settings: CalculationSettings,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val displayLocale = LocalConfiguration.current.locales[0]
    val zoneId = remember(settings.location.timezoneId) { settings.safeZoneId() }
    val is24Hour = com.sulfuro.salati.data.settings.resolveUses24HourClock(
        settings.appearance.timeFormat,
        android.text.format.DateFormat.is24HourFormat(context)
    )
    val today = remember(zoneId) { localDateAt(System.currentTimeMillis(), zoneId) }
    var currentYearMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var monthlyData by remember { mutableStateOf<List<AladhanDayData>>(emptyList()) }
    var hijriMetadata by remember { mutableStateOf<Map<LocalDate, HijriDateParts>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var retryTrigger by remember { mutableIntStateOf(0) }
    var timesWereComputedLocally by remember { mutableStateOf(false) }
    val hawlStartDate = remember(settings.zakat.hawlStartEpochDay) {
        settings.zakat.hawlStartEpochDay?.let(LocalDate::ofEpochDay)
    }

    LaunchedEffect(currentYearMonth, settings.prayer.hijriOffset, settings.prayer.calculationMethod, settings.prayer.highLatitudeRule, settings.prayer.madhab, settings.location.latitude, settings.location.longitude, retryTrigger) {
        isLoading = true
        val result = PrayerRepository.getMonthlyPrayers(
            context, settings, currentYearMonth.year, currentYearMonth.monthValue
        )
        monthlyData = (result as? MonthlyPrayerResult.Success)?.data.orEmpty()
        timesWereComputedLocally =
            (result as? MonthlyPrayerResult.Success)?.origin == PrayerDataOrigin.ON_DEVICE
        hijriMetadata = PrayerRepository.getHijriMetadataRange(
            context = context,
            settings = settings,
            startDate = currentYearMonth.atDay(1),
            endDate = currentYearMonth.atEndOfMonth()
        )
        isLoading = false
    }

    var selectedDayIndex by remember(currentYearMonth) {
        mutableIntStateOf(initialCalendarDay(currentYearMonth, today))
    }
    val selectedDate = remember(selectedDayIndex, currentYearMonth) {
        currentYearMonth.atDay(selectedDayIndex)
    }
    val monthlyDataByDate = remember(monthlyData) {
        PrayerRepository.indexPrayerDataByDate(monthlyData)
    }
    val selectedDayData = monthlyDataByDate[selectedDate]
    val selectedDayTimes = remember(selectedDayData, settings) {
        selectedDayData?.let {
            runCatching { PrayerRepository.parsePrayerTimes(it, settings) }.getOrNull()
        }
    }
    val apiLookup: (LocalDate) -> HijriDateParts? = remember(hijriMetadata) {
        { date -> hijriMetadata[date] }
    }

    val selectedDayHijri = remember(selectedDate, settings, selectedDayTimes, apiLookup) {
        val now = Instant.ofEpochMilli(System.currentTimeMillis())
        val afterMaghrib = selectedDate == today && selectedDayTimes != null &&
            !now.isBefore(selectedDayTimes.maghrib)
        HijriCalendarHelper.resolveHijriDate(selectedDate, settings.prayer.hijriOffset, afterMaghrib, apiLookup)
    }

    val dayEvents = remember(currentYearMonth, settings.prayer.hijriOffset, apiLookup, hawlStartDate) {
        val map = mutableMapOf<Int, List<IslamicCalendarEvent>>()
        val daysInMonth = currentYearMonth.lengthOfMonth()
        for (day in 1..daysInMonth) {
            val date = currentYearMonth.atDay(day)
            val hijri = HijriCalendarHelper.resolveHijriDate(date, settings.prayer.hijriOffset, false, apiLookup)
            val events = eventsForCalendarDate(date, hijri, hawlStartDate)
            if (events.isNotEmpty()) {
                map[day] = events
            }
        }
        map
    }
    val monthName = remember(currentYearMonth) {
        monthHeading(currentYearMonth, displayLocale)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SalatiSpacing.xs, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        MonthNavigationHeader(
            monthName = monthName,
            showTodayAction = currentYearMonth != YearMonth.from(today),
            onPrevious = { currentYearMonth = currentYearMonth.minusMonths(1) },
            onNext = { currentYearMonth = currentYearMonth.plusMonths(1) },
            onToday = { currentYearMonth = YearMonth.from(today) }
        )
        when {
            isLoading -> SalatiLoadingState(
                label = stringResource(R.string.monthly_loading),
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 240.dp)
            )
            monthlyData.isEmpty() -> SalatiErrorState(
                title = stringResource(R.string.monthly_error_title),
                message = stringResource(R.string.monthly_error_message),
                retryLabel = stringResource(R.string.monthly_retry),
                onRetry = { isLoading = true; retryTrigger++ },
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 240.dp)
            )
            else -> {
                if (timesWereComputedLocally) {
                    SalatiComputedLocallyNotice(
                        label = stringResource(R.string.daily_computed_locally)
                    )
                }
                SalatiSectionCard(modifier = Modifier.fillMaxWidth()) {
                    CalendarMonthGrid(
                        yearMonth = currentYearMonth,
                        selectedDay = selectedDayIndex,
                        today = today,
                        events = dayEvents,
                        apiLookup = apiLookup,
                        hijriOffset = settings.prayer.hijriOffset,
                        onDaySelected = { selectedDayIndex = it },
                        modifier = Modifier.padding(start = SalatiSpacing.sm, end = SalatiSpacing.sm, top = SalatiSpacing.sm, bottom = 4.dp)
                    )
                    Text(
                        text = stringResource(R.string.calendar_calculated_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = SalatiSpacing.sm, start = SalatiSpacing.md, end = SalatiSpacing.md)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                selectedDayTimes?.let { times ->
                    SelectedDayPrayerDetails(
                        gregorianDate = longDateFormatter(displayLocale).format(selectedDate),
                        hijriDate = selectedDayHijri.format(),
                        events = dayEvents[selectedDayIndex] ?: emptyList(),
                        locationContext = stringResource(R.string.daily_location_context, settings.location.cityName),
                        prayerTimes = times,
                        timeFormatter = remember(displayLocale, zoneId, is24Hour) {
                            prayerTimeFormatter(displayLocale, zoneId, is24Hour)
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun MonthNavigationHeader(
    monthName: String,
    showTodayAction: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 0.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.monthly_previous_month),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
            Text(
                text = monthName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                // It looks like a heading and it is the thing the rest of the screen is
                // about, so screen-reader heading navigation should land on it.
                modifier = Modifier.semantics { heading() }
            )
            if (showTodayAction) {
                TextButton(
                    onClick = onToday,
                    modifier = Modifier.heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = SalatiSpacing.sm, vertical = 0.dp)
                ) {
                    Text(
                        text = stringResource(R.string.monthly_today_action),
                        color = MaterialTheme.colorScheme.tertiary,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.monthly_next_month),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
internal fun CalendarMonthGrid(
    yearMonth: YearMonth,
    selectedDay: Int,
    today: LocalDate,
    events: Map<Int, List<IslamicCalendarEvent>>,
    apiLookup: (LocalDate) -> HijriDateParts?,
    hijriOffset: Int,
    onDaySelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val weekdayLabels = monthlyWeekdayLabels().map { stringResource(it) }
    val weekdayDescriptions = monthlyWeekdayDescriptions().map { stringResource(it) }
    val selectedLabel = stringResource(R.string.monthly_selected_state)
    val todayLabel = stringResource(R.string.monthly_today_state)
    val todaySelectedLabel = stringResource(R.string.monthly_today_selected_state)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Column(
            modifier = Modifier.padding(2.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Row(Modifier.fillMaxWidth()) {
                weekdayLabels.forEachIndexed { index, label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f).semantics {
                            contentDescription = weekdayDescriptions[index]
                        },
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            val offset = mondayFirstOffset(yearMonth)
            val totalCells = offset + yearMonth.lengthOfMonth()
            (0 until totalCells).chunked(7).forEach { chunk ->
                Row(Modifier.fillMaxWidth()) {
                    for (column in 0..6) {
                        val cellIndex = chunk.getOrNull(column)
                        if (cellIndex == null || cellIndex < offset) {
                            Spacer(Modifier.weight(1f).aspectRatio(1.15f))
                        } else {
                            val day = cellIndex - offset + 1
                            val date = yearMonth.atDay(day)
                            CalendarDateCell(
                                date = date,
                                isSelected = day == selectedDay,
                                isToday = date == today,
                                events = events[day] ?: emptyList(),
                                apiLookup = apiLookup,
                                hijriOffset = hijriOffset,
                                selectedState = selectedLabel,
                                todayState = todayLabel,
                                todaySelectedState = todaySelectedLabel,
                                onClick = { onDaySelected(day) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CalendarDateCell(
    date: LocalDate,
    isSelected: Boolean,
    isToday: Boolean,
    events: List<IslamicCalendarEvent>,
    apiLookup: (LocalDate) -> HijriDateParts?,
    hijriOffset: Int,
    selectedState: String,
    todayState: String,
    todaySelectedState: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayLocale = LocalConfiguration.current.locales[0]
    val eventNames = events.map { stringResource(it.nameRes) }.joinToString(", ")
    
    val state = when {
        isToday && isSelected -> todaySelectedState
        isToday -> todayState
        isSelected -> selectedState
        else -> null
    }

    val gregorianFormatter = remember(displayLocale) {
        DateTimeFormatter.ofPattern("d MMMM yyyy", displayLocale)
    }
    val gregorianDateStr = remember(date, gregorianFormatter) { date.format(gregorianFormatter) }
    val hijriDateStr = remember(date, hijriOffset, apiLookup) {
        val hijri = HijriCalendarHelper.resolveHijriDate(date, hijriOffset, false, apiLookup)
        "${hijri.day} ${hijri.monthName} ${hijri.year}"
    }

    // The label is what the day *is*; "today" and "selected" are what it currently *is like*,
    // and those belong in stateDescription so a screen reader can re-announce the state alone
    // when it changes rather than reading the whole date again. Appending them to the label
    // also left the node unselected as far as the framework was concerned, so selection was
    // spoken but not exposed.
    val contentDesc = remember(gregorianDateStr, hijriDateStr, eventNames) {
        buildString {
            append(gregorianDateStr)
            append(", ")
            append(hijriDateStr)
            if (eventNames.isNotEmpty()) {
                append(", ")
                append(eventNames)
            }
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1.15f)
            .semantics(mergeDescendants = true) {
                contentDescription = contentDesc
                selected = isSelected
                state?.let { stateDescription = it }
            }
            .padding(4.dp)
            .clip(SalatiShapeTokens.Control)
            .clickable(onClick = onClick)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .border(
                width = if (isToday) 1.5.dp else 0.dp,
                color = if (isToday) MaterialTheme.colorScheme.secondary else Color.Transparent,
                shape = SalatiShapeTokens.Control
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                isToday -> MaterialTheme.colorScheme.secondary
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1
        )
        if (events.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary)
            )
        }
    }
}

@Composable
internal fun SelectedDayPrayerDetails(
    gregorianDate: String,
    hijriDate: String,
    events: List<IslamicCalendarEvent>,
    locationContext: String,
    prayerTimes: SalatiPrayerTimes,
    timeFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier
) {
    val eventText = events.map { stringResource(it.nameRes) }.joinToString(" • ")

    SalatiSectionCard(modifier = modifier.fillMaxWidth().padding(top = 0.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = SalatiSpacing.md, end = SalatiSpacing.md, top = 6.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = gregorianDate,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$hijriDate  ·  $locationContext",
                    style = MaterialTheme.typography.labelMedium.copy(letterSpacing = TextUnit(0.3f, TextUnitType.Sp)),
                    color = MaterialTheme.colorScheme.tertiary
                )
                if (events.isNotEmpty()) {
                    Text(
                        text = eventText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Two to a row, in the order of the day: Fajr and sunrise, Dhuhr and Asr,
                // Maghrib and Isha.
                prayerTimes.byEvent().chunked(2).forEach { pair ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { (event, instant) ->
                            Box(modifier = Modifier.weight(1f)) {
                                CompactPrayerTimeItem(
                                    name = stringResource(event.labelRes),
                                    time = timeFormatter.format(instant),
                                    isDisplayOnly = !event.isPrayer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CompactPrayerTimeItem(
    name: String,
    time: String,
    isDisplayOnly: Boolean,
    modifier: Modifier = Modifier
) {
    val contentColor = if (isDisplayOnly) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    val timeColor = if (isDisplayOnly) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary

    val semanticState = if (isDisplayOnly) {
        stringResource(R.string.daily_prayer_display_only_accessibility, name, time)
    } else {
        stringResource(R.string.daily_prayer_accessibility, name, time)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(SalatiShapeTokens.Control)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            // Merged so the row is one thing to a screen reader: without this the state
            // description sat on a container with no text of its own while the name, the
            // "Display only" label and the time were announced as three separate nodes.
            .semantics(mergeDescendants = true) {
                stateDescription = semanticState
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor
            )
        }
        Text(
            text = time,
            style = com.sulfuro.salati.theme.SalatiTypeTokens.PrayerTime.copy(fontSize = TextUnit(16f, TextUnitType.Sp)),
            color = timeColor
        )
    }
}

@StringRes
internal fun monthlyWeekdayLabels(): List<Int> = listOf(
    R.string.monthly_weekday_monday_short, R.string.monthly_weekday_tuesday_short,
    R.string.monthly_weekday_wednesday_short, R.string.monthly_weekday_thursday_short,
    R.string.monthly_weekday_friday_short, R.string.monthly_weekday_saturday_short,
    R.string.monthly_weekday_sunday_short
)

@StringRes
internal fun monthlyWeekdayDescriptions(): List<Int> = listOf(
    R.string.monthly_weekday_monday, R.string.monthly_weekday_tuesday,
    R.string.monthly_weekday_wednesday, R.string.monthly_weekday_thursday,
    R.string.monthly_weekday_friday, R.string.monthly_weekday_saturday,
    R.string.monthly_weekday_sunday
)

internal fun initialCalendarDay(yearMonth: YearMonth, today: LocalDate): Int =
    if (yearMonth == YearMonth.from(today)) today.dayOfMonth else 1

internal fun mondayFirstOffset(yearMonth: YearMonth): Int = yearMonth.atDay(1).dayOfWeek.value - 1
