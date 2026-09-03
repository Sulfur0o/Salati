package io.github.sulfuro25.salati.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import io.github.sulfuro25.salati.MainActivity
import io.github.sulfuro25.salati.R
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
        private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, SalatiAppWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        updateWidgets(context, appWidgetManager, appWidgetIds)
                    } catch (e: Exception) {
                        android.util.Log.e("SalatiWidget", "Error updating widget from updateAllWidgets", e)
                    }
                }
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
                val zoneId = runCatching { ZoneId.of(settings.timezoneId) }.getOrElse { ZoneId.systemDefault() }
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

                for (appWidgetId in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.salati_widget_layout)
                    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

                    val shortCity = settings.cityName.substringBefore(",")
                    views.setTextViewText(R.id.widget_location, shortCity)

                    if (todaySchedule != null) {
                        val hijri = todaySchedule.date.hijri
                        val hijriStr = if (hijri != null) "${hijri.day} / ${hijri.month.number} AH" else todaySchedule.date.readable
                        views.setTextViewText(R.id.widget_hijri_date, hijriStr)
                    }

                    if (timings != null) {
                        val fajrTime = cleanTime(timings.Fajr)
                        val dhuhrTime = cleanTime(timings.Dhuhr)
                        val asrTime = cleanTime(timings.Asr)
                        val maghribTime = cleanTime(timings.Maghrib)
                        val ishaTime = cleanTime(timings.Isha)

                        views.setTextViewText(R.id.widget_time_fajr, fajrTime)
                        views.setTextViewText(R.id.widget_time_dhuhr, dhuhrTime)
                        views.setTextViewText(R.id.widget_time_asr, asrTime)
                        views.setTextViewText(R.id.widget_time_maghrib, maghribTime)
                        views.setTextViewText(R.id.widget_time_isha, ishaTime)

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
                            views.setTextViewText(R.id.widget_next_prayer_time, nextTimeStr)
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
                            views.setTextViewText(R.id.widget_next_prayer_time, tomorrowFajr)
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
