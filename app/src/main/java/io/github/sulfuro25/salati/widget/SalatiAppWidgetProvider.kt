package io.github.sulfuro25.salati.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.sulfuro25.salati.MainActivity
import io.github.sulfuro25.salati.R
import io.github.sulfuro25.salati.core.computation.HijriCalendarHelper
import io.github.sulfuro25.salati.core.computation.HijriDateParts
import io.github.sulfuro25.salati.core.computation.MonthlyPrayerResult
import io.github.sulfuro25.salati.core.computation.PrayerRepository
import io.github.sulfuro25.salati.data.settings.SalatiPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SalatiAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                updateWidgets(context, appWidgetManager, appWidgetIds)
            } catch (e: Exception) {
                android.util.Log.e("SalatiWidget", "Error updating widget from onUpdate", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        /** Parses the API's fixed 24-hour timing strings; never used for display. */
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

        /**
         * Renders a parsed timing for the widget, honouring the device's 12/24-hour
         * setting the same way the Daily and Monthly screens do.
         */
        private fun displayTime(context: Context, raw: String, timeFormat: String?): String {
            val parsed = parseTime(raw) ?: return cleanTime(raw)
            val uses24Hour = io.github.sulfuro25.salati.data.settings.resolveUses24HourClock(
                timeFormat,
                android.text.format.DateFormat.is24HourFormat(context)
            )
            val pattern = if (uses24Hour) "HH:mm" else "h:mm a"
            return DateTimeFormatter.ofPattern(pattern, java.util.Locale.getDefault()).format(parsed)
        }

        fun updateAllWidgets(context: Context, pendingResult: BroadcastReceiver.PendingResult? = null) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, SalatiAppWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        updateWidgets(context, appWidgetManager, appWidgetIds)
                    } catch (e: Exception) {
                        android.util.Log.e("SalatiWidget", "Error updating widget from updateAllWidgets", e)
                    } finally {
                        pendingResult?.finish()
                    }
                }
            } else {
                pendingResult?.finish()
            }
        }

        private suspend fun updateWidgets(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            try {
                val pendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val preferences = SalatiPreferences(context)
                val settings = preferences.settings.first()
                val zoneId = io.github.sulfuro25.salati.data.settings.safeZoneId(settings.timezoneId)
                val now = YearMonth.now(zoneId)
                val today = LocalDate.now(zoneId)
                val currentTime = LocalTime.now(zoneId)

                val result = PrayerRepository.getMonthlyPrayers(
                    context = context,
                    settings = settings,
                    year = now.year,
                    month = now.monthValue,
                    requireCacheOnly = false
                )

                val todaySchedule = (result as? MonthlyPrayerResult.Success)?.data
                    ?.firstOrNull { it.date.gregorian.day.toIntOrNull() == today.dayOfMonth }

                val timings = todaySchedule?.timings

                val isAfterMaghrib = if (timings != null) {
                    val maghribLocal = parseTime(timings.Maghrib)
                    maghribLocal != null && currentTime.isAfter(maghribLocal)
                } else false

                val resolvedHijriDate = HijriCalendarHelper.resolveHijriDate(
                    gregorianDate = today,
                    offsetDays = settings.hijriOffset,
                    isAfterMaghrib = isAfterMaghrib
                ) { targetDate ->
                    (result as? MonthlyPrayerResult.Success)?.data
                        ?.firstOrNull { it.date.gregorian.day.toIntOrNull() == targetDate.dayOfMonth && it.date.gregorian.month.number == targetDate.monthValue }
                        ?.date?.hijri?.let { h ->
                            val dayInt = h.day.toIntOrNull()
                            val yearInt = h.year.toIntOrNull()
                            if (dayInt != null && yearInt != null) {
                                HijriDateParts(dayInt, h.month.number, yearInt)
                            } else null
                        }
                }

                val hijriStr = resolvedHijriDate.format()

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.salati_widget_layout)
                    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                    val shortCity = settings.cityName.substringBefore(",")
                    views.setTextViewText(R.id.widget_location, shortCity)
                    views.setTextViewText(R.id.widget_hijri_date, hijriStr)

                    if (timings != null) {
                        val fajrTime = cleanTime(timings.Fajr)
                        val dhuhrTime = cleanTime(timings.Dhuhr)
                        val asrTime = cleanTime(timings.Asr)
                        val maghribTime = cleanTime(timings.Maghrib)
                        val ishaTime = cleanTime(timings.Isha)

                        views.setTextViewText(R.id.widget_time_fajr, displayTime(context, fajrTime, settings.timeFormat))
                        views.setTextViewText(R.id.widget_time_dhuhr, displayTime(context, dhuhrTime, settings.timeFormat))
                        views.setTextViewText(R.id.widget_time_asr, displayTime(context, asrTime, settings.timeFormat))
                        views.setTextViewText(R.id.widget_time_maghrib, displayTime(context, maghribTime, settings.timeFormat))
                        views.setTextViewText(R.id.widget_time_isha, displayTime(context, ishaTime, settings.timeFormat))

                        // Determine next prayer
                        val prayers = listOf(
                            Triple("Fajr", context.getString(R.string.prayer_fajr), parseTime(fajrTime)),
                            Triple("Dhuhr", context.getString(R.string.prayer_dhuhr), parseTime(dhuhrTime)),
                            Triple("Asr", context.getString(R.string.prayer_asr), parseTime(asrTime)),
                            Triple("Maghrib", context.getString(R.string.prayer_maghrib), parseTime(maghribTime)),
                            Triple("Isha", context.getString(R.string.prayer_isha), parseTime(ishaTime))
                        )

                        val maghribLocal = parseTime(maghribTime)
                        val nextUpcoming = prayers.firstOrNull { entry ->
                            val prayerTime = entry.third ?: return@firstOrNull false
                            isUpcomingPrayer(entry.first, prayerTime, currentTime, maghribLocal)
                        }

                        if (nextUpcoming != null) {
                            views.setTextViewText(R.id.widget_next_prayer_name, nextUpcoming.second)
                            val nextTimeStr = when (nextUpcoming.first) {
                                "Fajr" -> fajrTime
                                "Dhuhr" -> dhuhrTime
                                "Asr" -> asrTime
                                "Maghrib" -> maghribTime
                                else -> ishaTime
                            }
                            views.setTextViewText(R.id.widget_next_prayer_time, displayTime(context, nextTimeStr, settings.timeFormat))
                        } else {
                            views.setTextViewText(R.id.widget_next_prayer_name, context.getString(R.string.prayer_fajr))
                            val tomorrow = today.plusDays(1)
                            val tomorrowMonth = YearMonth.from(tomorrow)
                            val tomorrowSchedule = if (tomorrowMonth == now) {
                                result.data
                                    .firstOrNull { it.date.gregorian.day.toIntOrNull() == tomorrow.dayOfMonth }
                            } else {
                                val nextMonthResult = PrayerRepository.getMonthlyPrayers(
                                    context = context,
                                    settings = settings,
                                    year = tomorrowMonth.year,
                                    month = tomorrowMonth.monthValue,
                                    requireCacheOnly = true
                                )
                                (nextMonthResult as? MonthlyPrayerResult.Success)?.data
                                    ?.firstOrNull { it.date.gregorian.day.toIntOrNull() == tomorrow.dayOfMonth }
                            }
                            val tomorrowFajr = tomorrowSchedule?.timings?.Fajr?.let(::cleanTime) ?: fajrTime
                            views.setTextViewText(R.id.widget_next_prayer_time, displayTime(context, tomorrowFajr, settings.timeFormat))
                        }
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            } catch (e: Exception) {
                android.util.Log.e("SalatiWidget", "Failed to update widgets", e)
            }
        }

        private fun cleanTime(raw: String): String {
            return raw.substringBefore(" ").trim()
        }

        private fun parseTime(raw: String): LocalTime? {
            return runCatching { LocalTime.parse(cleanTime(raw), TIME_FORMATTER) }.getOrNull()
        }

        private fun isUpcomingPrayer(
            prayerKey: String,
            prayerTime: LocalTime,
            currentTime: LocalTime,
            maghribTime: LocalTime?
        ): Boolean {
            val rollsPastMidnight = prayerKey == "Isha" &&
                maghribTime != null &&
                !prayerTime.isAfter(maghribTime)
            return if (rollsPastMidnight) {
                currentTime.isAfter(maghribTime) || !currentTime.isAfter(prayerTime)
            } else {
                prayerTime.isAfter(currentTime)
            }
        }
    }
}
