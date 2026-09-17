package com.sulfuro.salati.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.sulfuro.salati.MainActivity
import com.sulfuro.salati.R
import com.sulfuro.salati.core.computation.HijriCalendarHelper
import com.sulfuro.salati.core.computation.HijriDateParts
import com.sulfuro.salati.core.computation.MonthlyPrayerResult
import com.sulfuro.salati.core.computation.PrayerRepository
import com.sulfuro.salati.core.prayer.Prayer
import com.sulfuro.salati.data.settings.SalatiPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** The five prayer times a widget shows, ready to draw. */
@JvmInline
value class WidgetPrayerTimes(private val byPrayer: Map<Prayer, String>) {
    operator fun get(prayer: Prayer): String? = byPrayer[prayer]
}

data class WidgetDataSnapshot(
    val city: String,
    val hijriDate: String,
    val times: WidgetPrayerTimes?,
    val nextPrayerName: String,
    val nextPrayerTime: String,
    /** The prayer to light up: the next one due. */
    val activePrayer: Prayer
)

object SalatiWidgetData {
    private val TIME_FORMATTER =
        DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.ROOT)

    fun cleanTime(raw: String): String {
        return raw.trim().substringBefore(" ").trim()
    }

    fun parseTime(raw: String): LocalTime? {
        return runCatching { LocalTime.parse(cleanTime(raw), TIME_FORMATTER) }.getOrNull()
    }

    fun displayTime(context: Context, raw: String, timeFormat: String?): String {
        val parsed = parseTime(raw) ?: return cleanTime(raw)
        val uses24Hour = com.sulfuro.salati.data.settings.resolveUses24HourClock(
            timeFormat,
            android.text.format.DateFormat.is24HourFormat(context)
        )
        val pattern = if (uses24Hour) "HH:mm" else "h:mm a"
        return DateTimeFormatter.ofPattern(pattern, java.util.Locale.getDefault()).format(parsed)
    }

    fun isUpcomingPrayer(
        prayer: Prayer,
        prayerTime: LocalTime,
        currentTime: LocalTime,
        maghribTime: LocalTime?
    ): Boolean {
        val rollsPastMidnight = prayer == Prayer.ISHA &&
            maghribTime != null &&
            !prayerTime.isAfter(maghribTime)
        return if (rollsPastMidnight) {
            currentTime.isAfter(maghribTime) || !currentTime.isAfter(prayerTime)
        } else {
            prayerTime.isAfter(currentTime)
        }
    }

    fun createLaunchPendingIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    suspend fun loadWidgetSnapshot(context: Context): WidgetDataSnapshot? {
        return try {
            val preferences = SalatiPreferences(context)
            val settings = preferences.settings.first()
            val zoneId = com.sulfuro.salati.data.settings.safeZoneId(settings.location.timezoneId)
            val now = YearMonth.now(zoneId)
            val today = LocalDate.now(zoneId)
            val currentTime = LocalTime.now(zoneId)

            val result = PrayerRepository.getMonthlyPrayers(
                context = context,
                settings = settings,
                year = now.year,
                month = now.monthValue,
                requireCacheOnly = true,
                allowOnDeviceFallback = true
            )

            val daysByDate = (result as? MonthlyPrayerResult.Success)?.data
                ?.let { PrayerRepository.indexPrayerDataByDate(it) }
                .orEmpty()
            val todaySchedule = daysByDate[today]

            val timings = todaySchedule?.timings

            val isAfterMaghrib = if (timings != null) {
                val maghribLocal = parseTime(timings.Maghrib)
                maghribLocal != null && currentTime.isAfter(maghribLocal)
            } else false

            val resolvedHijriDate = HijriCalendarHelper.resolveHijriDate(
                gregorianDate = today,
                offsetDays = settings.prayer.hijriOffset,
                isAfterMaghrib = isAfterMaghrib
            ) { targetDate ->
                daysByDate[targetDate]
                    ?.date?.hijri?.let { h ->
                        val dayInt = h.day.toIntOrNull()
                        val yearInt = h.year.toIntOrNull()
                        if (dayInt != null && yearInt != null) {
                            HijriDateParts(dayInt, h.month.number, yearInt)
                        } else null
                    }
            }

            val hijriStr = resolvedHijriDate.format()
            val shortCity = settings.location.cityName.substringBefore(",")

            if (timings == null) {
                return WidgetDataSnapshot(
                    city = shortCity,
                    hijriDate = hijriStr,
                    times = null,
                    nextPrayerName = context.getString(R.string.prayer_fajr),
                    nextPrayerTime = "--:--",
                    activePrayer = Prayer.FAJR
                )
            }

            // The one place the API's own field names are matched to ours. Everything
            // below works off Prayer, so a change to the set of prayers lands here alone.
            val rawTimes = mapOf(
                Prayer.FAJR to cleanTime(timings.Fajr),
                Prayer.DHUHR to cleanTime(timings.Dhuhr),
                Prayer.ASR to cleanTime(timings.Asr),
                Prayer.MAGHRIB to cleanTime(timings.Maghrib),
                Prayer.ISHA to cleanTime(timings.Isha)
            )
            val fajrRaw = rawTimes.getValue(Prayer.FAJR)

            val times = WidgetPrayerTimes(
                rawTimes.mapValues { (_, raw) ->
                    displayTime(context, raw, settings.appearance.timeFormat)
                }
            )

            val maghribLocal = parseTime(rawTimes.getValue(Prayer.MAGHRIB))
            val nextUpcoming = Prayer.prayed.firstOrNull { prayer ->
                val pTime = parseTime(rawTimes.getValue(prayer)) ?: return@firstOrNull false
                isUpcomingPrayer(prayer, pTime, currentTime, maghribLocal)
            }

            val (nextName, nextTime, activePrayer) = if (nextUpcoming != null) {
                Triple(
                    context.getString(nextUpcoming.labelRes),
                    displayTime(context, rawTimes.getValue(nextUpcoming), settings.appearance.timeFormat),
                    nextUpcoming
                )
            } else {
                // If all prayers today have passed, upcoming is tomorrow's Fajr
                val tomorrow = today.plusDays(1)
                val tomorrowMonth = YearMonth.from(tomorrow)
                val tomorrowSchedule = if (tomorrowMonth == now) {
                    daysByDate[tomorrow]
                } else {
                    val nextMonthResult = PrayerRepository.getMonthlyPrayers(
                        context = context,
                        settings = settings,
                        year = tomorrowMonth.year,
                        month = tomorrowMonth.monthValue,
                        requireCacheOnly = true,
                        allowOnDeviceFallback = true
                    )
                    (nextMonthResult as? MonthlyPrayerResult.Success)?.data
                        ?.let { PrayerRepository.indexPrayerDataByDate(it) }
                        ?.get(tomorrow)
                }
                val tomorrowFajr = tomorrowSchedule?.timings?.Fajr?.let(::cleanTime) ?: fajrRaw
                Triple(
                    context.getString(R.string.prayer_fajr),
                    displayTime(context, tomorrowFajr, settings.appearance.timeFormat),
                    Prayer.FAJR
                )
            }

            WidgetDataSnapshot(
                city = shortCity,
                hijriDate = hijriStr,
                times = times,
                nextPrayerName = nextName,
                nextPrayerTime = nextTime,
                activePrayer = activePrayer
            )
        } catch (e: Exception) {
            android.util.Log.e("SalatiWidget", "Failed to load widget snapshot", e)
            null
        }
    }

    /**
     * Redraws one provider's own widgets.
     *
     * Each of the four providers used to answer its own APPWIDGET_UPDATE by redrawing all
     * four kinds, so a broadcast to all of them - after a reboot, say - did the same work
     * four times over, with four pending results held open and four reads of the same
     * prayer data.
     */
    fun updateWidgets(
        context: Context,
        pendingResult: BroadcastReceiver.PendingResult?,
        appWidgetIds: IntArray,
        apply: (WidgetDataSnapshot, PendingIntent) -> Unit
    ) {
        if (appWidgetIds.isEmpty()) {
            pendingResult?.finish()
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val snapshot = loadWidgetSnapshot(context) ?: return@launch
                apply(snapshot, createLaunchPendingIntent(context))
            } catch (e: Exception) {
                android.util.Log.e("SalatiWidget", "Error updating widgets", e)
            } finally {
                pendingResult?.finish()
            }
        }
    }

    /** Redraws every placed widget of every kind. For callers outside the providers. */
    fun updateAllWidgets(context: Context, pendingResult: BroadcastReceiver.PendingResult? = null) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        val fullIds = appWidgetManager.getAppWidgetIds(ComponentName(context, SalatiAppWidgetProvider::class.java))
        val barIds = appWidgetManager.getAppWidgetIds(ComponentName(context, SalatiMinimalBarWidgetProvider::class.java))
        val compactIds = appWidgetManager.getAppWidgetIds(ComponentName(context, SalatiCompactWidgetProvider::class.java))
        val hasAny = fullIds.isNotEmpty() || barIds.isNotEmpty() || compactIds.isNotEmpty()

        if (hasAny) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val snapshot = loadWidgetSnapshot(context) ?: return@launch
                    val pendingIntent = createLaunchPendingIntent(context)

                    if (fullIds.isNotEmpty()) {
                        SalatiAppWidgetProvider.applySnapshot(context, appWidgetManager, fullIds, snapshot, pendingIntent)
                    }
                    if (barIds.isNotEmpty()) {
                        SalatiMinimalBarWidgetProvider.applySnapshot(context, appWidgetManager, barIds, snapshot, pendingIntent)
                    }
                    if (compactIds.isNotEmpty()) {
                        SalatiCompactWidgetProvider.applySnapshot(context, appWidgetManager, compactIds, snapshot, pendingIntent)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SalatiWidget", "Error updating all widgets", e)
                } finally {
                    pendingResult?.finish()
                }
            }
        } else {
            pendingResult?.finish()
        }
    }

    fun setViewTextColor(
        views: android.widget.RemoteViews,
        context: Context,
        viewId: Int,
        colorResId: Int
    ) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            views.setColor(viewId, "setTextColor", colorResId)
        } else {
            val isSystemNight = (android.content.res.Resources.getSystem().configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val targetUiMode = if (isSystemNight) {
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            } else {
                android.content.res.Configuration.UI_MODE_NIGHT_NO
            }
            val systemConfig = android.content.res.Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or targetUiMode
            }
            val themedContext = context.createConfigurationContext(systemConfig)
            views.setTextColor(viewId, themedContext.getColor(colorResId))
        }
    }
}
