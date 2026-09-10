package io.github.sulfuro25.salati.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import java.time.format.DateTimeFormatter

data class WidgetPrayerTimes(
    val fajr: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String
)

data class WidgetDataSnapshot(
    val city: String,
    val hijriDate: String,
    val times: WidgetPrayerTimes?,
    val nextPrayerName: String,
    val nextPrayerTime: String,
    val activePrayerIndex: Int // 0=Fajr, 1=Dhuhr, 2=Asr, 3=Maghrib, 4=Isha
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
        val uses24Hour = io.github.sulfuro25.salati.data.settings.resolveUses24HourClock(
            timeFormat,
            android.text.format.DateFormat.is24HourFormat(context)
        )
        val pattern = if (uses24Hour) "HH:mm" else "h:mm a"
        return DateTimeFormatter.ofPattern(pattern, java.util.Locale.getDefault()).format(parsed)
    }

    fun isUpcomingPrayer(
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
            val zoneId = io.github.sulfuro25.salati.data.settings.safeZoneId(settings.timezoneId)
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
            val shortCity = settings.cityName.substringBefore(",")

            if (timings == null) {
                return WidgetDataSnapshot(
                    city = shortCity,
                    hijriDate = hijriStr,
                    times = null,
                    nextPrayerName = context.getString(R.string.prayer_fajr),
                    nextPrayerTime = "--:--",
                    activePrayerIndex = 0
                )
            }

            val fajrRaw = cleanTime(timings.Fajr)
            val dhuhrRaw = cleanTime(timings.Dhuhr)
            val asrRaw = cleanTime(timings.Asr)
            val maghribRaw = cleanTime(timings.Maghrib)
            val ishaRaw = cleanTime(timings.Isha)

            val times = WidgetPrayerTimes(
                fajr = displayTime(context, fajrRaw, settings.timeFormat),
                dhuhr = displayTime(context, dhuhrRaw, settings.timeFormat),
                asr = displayTime(context, asrRaw, settings.timeFormat),
                maghrib = displayTime(context, maghribRaw, settings.timeFormat),
                isha = displayTime(context, ishaRaw, settings.timeFormat)
            )

            val prayerEntries = listOf(
                PrayerInfo(0, "Fajr", context.getString(R.string.prayer_fajr), fajrRaw, parseTime(fajrRaw)),
                PrayerInfo(1, "Dhuhr", context.getString(R.string.prayer_dhuhr), dhuhrRaw, parseTime(dhuhrRaw)),
                PrayerInfo(2, "Asr", context.getString(R.string.prayer_asr), asrRaw, parseTime(asrRaw)),
                PrayerInfo(3, "Maghrib", context.getString(R.string.prayer_maghrib), maghribRaw, parseTime(maghribRaw)),
                PrayerInfo(4, "Isha", context.getString(R.string.prayer_isha), ishaRaw, parseTime(ishaRaw))
            )

            val maghribLocal = parseTime(maghribRaw)
            val nextUpcoming = prayerEntries.firstOrNull { entry ->
                val pTime = entry.parsedTime ?: return@firstOrNull false
                isUpcomingPrayer(entry.key, pTime, currentTime, maghribLocal)
            }

            val (nextName, nextTime, activeIdx) = if (nextUpcoming != null) {
                Triple(
                    nextUpcoming.displayName,
                    displayTime(context, nextUpcoming.rawTime, settings.timeFormat),
                    nextUpcoming.index
                )
            } else {
                // If all prayers today have passed, upcoming is tomorrow's Fajr
                val tomorrow = today.plusDays(1)
                val tomorrowMonth = YearMonth.from(tomorrow)
                val tomorrowSchedule = if (tomorrowMonth == now) {
                    (result as MonthlyPrayerResult.Success).data
                        .firstOrNull { it.date.gregorian.day.toIntOrNull() == tomorrow.dayOfMonth }
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
                        ?.firstOrNull { it.date.gregorian.day.toIntOrNull() == tomorrow.dayOfMonth }
                }
                val tomorrowFajr = tomorrowSchedule?.timings?.Fajr?.let(::cleanTime) ?: fajrRaw
                Triple(
                    context.getString(R.string.prayer_fajr),
                    displayTime(context, tomorrowFajr, settings.timeFormat),
                    0 // Fajr
                )
            }

            WidgetDataSnapshot(
                city = shortCity,
                hijriDate = hijriStr,
                times = times,
                nextPrayerName = nextName,
                nextPrayerTime = nextTime,
                activePrayerIndex = activeIdx
            )
        } catch (e: Exception) {
            android.util.Log.e("SalatiWidget", "Failed to load widget snapshot", e)
            null
        }
    }

    private data class PrayerInfo(
        val index: Int,
        val key: String,
        val displayName: String,
        val rawTime: String,
        val parsedTime: LocalTime?
    )

    fun updateAllWidgets(context: Context, pendingResult: BroadcastReceiver.PendingResult? = null) {
        val appWidgetManager = AppWidgetManager.getInstance(context)

        val fullIds = appWidgetManager.getAppWidgetIds(ComponentName(context, SalatiAppWidgetProvider::class.java))
        val barIds = appWidgetManager.getAppWidgetIds(ComponentName(context, SalatiMinimalBarWidgetProvider::class.java))
        val compactIds = appWidgetManager.getAppWidgetIds(ComponentName(context, SalatiCompactWidgetProvider::class.java))
        val glanceIds = appWidgetManager.getAppWidgetIds(ComponentName(context, SalatiGlanceWidgetProvider::class.java))

        val hasAny = fullIds.isNotEmpty() || barIds.isNotEmpty() || compactIds.isNotEmpty() || glanceIds.isNotEmpty()

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
                    if (glanceIds.isNotEmpty()) {
                        SalatiGlanceWidgetProvider.applySnapshot(context, appWidgetManager, glanceIds, snapshot, pendingIntent)
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
}
