package io.github.sulfuro25.salati.core.notifications

import android.content.Context
import android.util.Log
import io.github.sulfuro25.salati.core.computation.MonthlyPrayerResult
import io.github.sulfuro25.salati.core.computation.PrayerRepository
import io.github.sulfuro25.salati.core.computation.SalatiPrayerTimes
import io.github.sulfuro25.salati.data.settings.CalculationSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

data class PreparedAlarm(
    val requestCode: Int,
    val uri: String,
    val prayerKey: String,
    val isPreReminder: Boolean,
    val triggerAtMillis: Long,
    val silentModeAutomationEnabled: Boolean = false,
    val silentModeMinutesAfterAdhan: Int = 0,
    val silentModeDurationMinutes: Int = 20
)

object AlarmScheduler {
    private const val TAG = "AlarmScheduler"
    internal const val SCHEDULE_WINDOW_DAYS = 7

    const val ACTION_PRAYER_ALARM = "io.github.sulfuro25.salati.ACTION_PRAYER_ALARM"
    const val EXTRA_PRAYER_NAME = "EXTRA_PRAYER_NAME"
    const val EXTRA_IS_PRE_REMINDER = "EXTRA_IS_PRE_REMINDER"
    const val EXTRA_ALARM_TIME = "EXTRA_ALARM_TIME"
    const val EXTRA_VIBRATE_ENABLED = "EXTRA_VIBRATE_ENABLED"
    const val EXTRA_SOUND_ENABLED = "EXTRA_SOUND_ENABLED"
    const val EXTRA_ALARM_REQUEST_CODE = "EXTRA_ALARM_REQUEST_CODE"
    const val EXTRA_SILENT_MODE_AUTOMATION_ENABLED = "EXTRA_SILENT_MODE_AUTOMATION_ENABLED"
    const val EXTRA_SILENT_MODE_MINUTES_AFTER_ADHAN = "EXTRA_SILENT_MODE_MINUTES_AFTER_ADHAN"
    const val EXTRA_SILENT_MODE_DURATION_MINUTES = "EXTRA_SILENT_MODE_DURATION_MINUTES"

    const val EXTRA_NOTIFICATION_KIND = "EXTRA_NOTIFICATION_KIND"
    const val KIND_PRAYER = "KIND_PRAYER"
    const val KIND_PRE_PRAYER = "KIND_PRE_PRAYER"
    const val KIND_WHITE_DAYS = "KIND_WHITE_DAYS"

    internal val supportedNotificationPrayers = listOf(
        "Fajr" to 1,
        "Dhuhr" to 2,
        "Asr" to 3,
        "Maghrib" to 4,
        "Isha" to 5
    )

    internal fun isSupportedNotificationPrayer(name: String): Boolean {
        return supportedNotificationPrayers.any { it.first.equals(name, ignoreCase = true) }
    }

    internal fun isSupportedAlarmEvent(key: String): Boolean {
        return isSupportedNotificationPrayer(key) || key == "white_days"
    }

    fun getPrayerBaseId(name: String): Int {
        return supportedNotificationPrayers
            .firstOrNull { it.first.equals(name, ignoreCase = true) }
            ?.second
            ?: throw IllegalArgumentException("Unsupported notification prayer: $name")
    }

    internal fun getSchedulingDates(clock: Clock, zoneId: ZoneId): List<LocalDate> {
        val today = clock.instant().atZone(zoneId).toLocalDate()
        // Include yesterday so high-latitude Isha that rolled past local midnight
        // is not dropped when a refresh runs between 00:00 and that Instant.
        return (-1 until SCHEDULE_WINDOW_DAYS).map { today.plusDays(it.toLong()) }
    }

    internal fun getRequiredMonths(dates: List<LocalDate>): List<YearMonth> {
        return dates.map(YearMonth::from).distinct()
    }

    suspend fun prepareAlarms(
        context: Context,
        settings: CalculationSettings,
        requireCacheOnly: Boolean = false,
        clock: Clock = Clock.systemUTC()
    ): AlarmPreparationResult = withContext(Dispatchers.IO) {
        if (!settings.hasCompletedOnboarding || settings.notificationsMuted) {
            return@withContext AlarmPreparationResult.Disabled
        }

        val zoneId = ZoneId.of(settings.timezoneId)
        val dates = getSchedulingDates(clock, zoneId)

        val monthData = mutableMapOf<YearMonth, Map<LocalDate, io.github.sulfuro25.salati.core.computation.AladhanDayData>>()
        var anySuccess = false
        var firstFailure: AlarmPreparationResult? = null

        for (yearMonth in getRequiredMonths(dates)) {
            when (
                val result = PrayerRepository.getMonthlyPrayers(
                    context = context,
                    settings = settings,
                    year = yearMonth.year,
                    month = yearMonth.monthValue,
                    requireCacheOnly = requireCacheOnly
                )
            ) {
                is MonthlyPrayerResult.Success -> {
                    monthData[yearMonth] = PrayerRepository.indexPrayerDataByDate(result.data)
                    anySuccess = true
                }
                else -> {
                    if (firstFailure == null) {
                        firstFailure = result.toPreparationFailure()
                    }
                }
            }
        }

        if (!anySuccess) {
            return@withContext firstFailure ?: AlarmPreparationResult.CacheMiss
        }

        val hijriMetadata = PrayerRepository.getHijriMetadataRange(
            context = context,
            settings = settings,
            startDate = dates.first(),
            endDate = dates.last(),
            requireCacheOnly = requireCacheOnly
        )

        val timesByDate = linkedMapOf<LocalDate, SalatiPrayerTimes>()
        for (date in dates) {
            val dayData = monthData[YearMonth.from(date)]?.get(date) ?: continue
            val prayerTimes = try {
                PrayerRepository.parsePrayerTimes(dayData, settings)
            } catch (e: Exception) {
                continue
            }
            timesByDate[date] = prayerTimes
        }

        if (timesByDate.isEmpty()) {
            return@withContext firstFailure ?: invalidWindowData(dates.first())
        }

        buildPreparedAlarms(
            timesByDate = timesByDate,
            hijriMetadata = hijriMetadata,
            settings = settings,
            nowMillis = clock.millis()
        )
    }

    internal fun buildPreparedAlarms(
        timesByDate: Map<LocalDate, SalatiPrayerTimes>,
        hijriMetadata: Map<LocalDate, io.github.sulfuro25.salati.core.computation.HijriDateParts>,
        settings: CalculationSettings,
        nowMillis: Long
    ): AlarmPreparationResult.Success {
        val preparedAlarms = mutableListOf<PreparedAlarm>()

        fun addAlarm(name: String, time: Instant, prayerDate: LocalDate) {
            val timeMillis = time.toEpochMilli()
            if (timeMillis <= nowMillis) return

            val prayerKey = name.lowercase(Locale.ROOT)
            val prayerId = getPrayerBaseId(name)
            preparedAlarms += PreparedAlarm(
                requestCode = createAlarmRequestCode(prayerDate, prayerId, isPreReminder = false),
                uri = getAlarmUriString(prayerDate, name, isPreReminder = false),
                prayerKey = prayerKey,
                isPreReminder = false,
                triggerAtMillis = timeMillis,
                silentModeAutomationEnabled = settings.silentModeAutomationEnabled,
                silentModeMinutesAfterAdhan = settings.silentModeMinutesAfterAdhan,
                silentModeDurationMinutes = settings.silentModeDurationMinutes
            )

            if (settings.prePrayerMinutes > 0) {
                val preTimeMillis = timeMillis - settings.prePrayerMinutes * 60_000L
                if (preTimeMillis > nowMillis) {
                    preparedAlarms += PreparedAlarm(
                        requestCode = createAlarmRequestCode(prayerDate, prayerId, isPreReminder = true),
                        uri = getAlarmUriString(prayerDate, name, isPreReminder = true),
                        prayerKey = prayerKey,
                        isPreReminder = true,
                        triggerAtMillis = preTimeMillis
                    )
                }
            }
        }

        for ((date, times) in timesByDate) {
            addAlarm("Fajr", times.fajr, date)
            addAlarm("Dhuhr", times.dhuhr, date)
            addAlarm("Asr", times.asr, date)
            addAlarm("Maghrib", times.maghrib, date)
            addAlarm("Isha", times.isha, date)
        }

        if (settings.whiteDaysReminder) {
            val apiLookup: (LocalDate) -> io.github.sulfuro25.salati.core.computation.HijriDateParts? = { lookupDate ->
                hijriMetadata[lookupDate]
            }

            for ((date, times) in timesByDate) {
                val hijri = io.github.sulfuro25.salati.core.computation.HijriCalendarHelper.resolveHijriDate(
                    date, settings.hijriOffset, isAfterMaghrib = false, apiLookup
                )
                if (hijri.day == 12 && hijri.monthNumber != 12 && hijri.monthNumber != 9) {
                    val timeMillis = times.maghrib.toEpochMilli()
                    if (timeMillis > nowMillis) {
                        preparedAlarms += PreparedAlarm(
                            requestCode = createWhiteDaysRequestCode(date),
                            uri = getWhiteDaysUriString(date),
                            prayerKey = "white_days",
                            isPreReminder = false,
                            triggerAtMillis = timeMillis
                        )
                        break
                    }
                }
            }
        }

        return AlarmPreparationResult.Success(preparedAlarms)
    }

    internal fun getWhiteDaysUriString(date: LocalDate): String {
        return "salati://alarm/$date/white-days/main"
    }

    internal fun createWhiteDaysRequestCode(date: LocalDate): Int {
        val epochDay = date.toEpochDay()
        return (epochDay.toInt() shl 5) or (10 shl 1) or 0
    }

    internal fun getKnownAlarmIdentities(clock: Clock, zoneId: ZoneId): List<RegisteredAlarm> {
        val dates = getSchedulingDates(clock, zoneId)
        val prayerIdentities = dates.flatMap { date ->
            supportedNotificationPrayers.flatMap { (name, prayerId) ->
                listOf(false, true).map { isPreReminder ->
                    RegisteredAlarm(
                        requestCode = createAlarmRequestCode(date, prayerId, isPreReminder),
                        uri = getAlarmUriString(date, name, isPreReminder),
                        prayerKey = name.lowercase(Locale.ROOT),
                        isPreReminder = isPreReminder,
                        triggerAtMillis = 0L,
                        vibrateEnabled = false
                    )
                }
            }
        }
        val whiteDaysIdentities = dates.map { date ->
            RegisteredAlarm(
                requestCode = createWhiteDaysRequestCode(date),
                uri = getWhiteDaysUriString(date),
                prayerKey = "white_days",
                isPreReminder = false,
                triggerAtMillis = 0L,
                vibrateEnabled = false
            )
        }
        return prayerIdentities + whiteDaysIdentities
    }

    internal fun getAlarmUriString(
        timeMs: Long,
        name: String,
        isPreReminder: Boolean,
        zoneId: ZoneId
    ): String {
        val date = Instant.ofEpochMilli(timeMs)
            .atZone(zoneId)
            .toLocalDate()
        return getAlarmUriString(date, name, isPreReminder)
    }

    internal fun getAlarmUriString(date: LocalDate, name: String, isPreReminder: Boolean): String {
        val reminderType = if (isPreReminder) "pre" else "main"
        return "salati://alarm/$date/${name.lowercase(Locale.ROOT)}/$reminderType"
    }

    internal fun createAlarmRequestCode(
        date: LocalDate,
        prayerId: Int,
        isPreReminder: Boolean
    ): Int {
        require(prayerId in 1..5)
        val epochDay = date.toEpochDay()
        require(epochDay in 0..67_108_863)
        return (epochDay.toInt() shl 5) or
            (prayerId shl 1) or
            if (isPreReminder) 1 else 0
    }

    private fun invalidWindowData(date: LocalDate): AlarmPreparationResult.InvalidApiResponse {
        val cause = IllegalStateException("Prayer data does not contain $date")
        Log.e(TAG, cause.message.orEmpty())
        return AlarmPreparationResult.InvalidApiResponse(cause)
    }

    private fun MonthlyPrayerResult.toPreparationFailure(): AlarmPreparationResult {
        return when (this) {
            is MonthlyPrayerResult.CacheMiss -> AlarmPreparationResult.CacheMiss
            is MonthlyPrayerResult.TemporaryNetworkFailure -> {
                AlarmPreparationResult.TemporaryNetworkFailure(cause)
            }
            is MonthlyPrayerResult.RetryableServerFailure -> {
                AlarmPreparationResult.RetryableServerFailure(statusCode)
            }
            is MonthlyPrayerResult.PermanentHttpFailure -> {
                AlarmPreparationResult.PermanentHttpFailure(statusCode)
            }
            is MonthlyPrayerResult.InvalidApiResponse -> {
                AlarmPreparationResult.InvalidApiResponse(cause)
            }
            is MonthlyPrayerResult.InvalidCachedData -> {
                AlarmPreparationResult.InvalidCachedData(cause)
            }
            is MonthlyPrayerResult.InvalidConfiguration -> {
                AlarmPreparationResult.InvalidConfiguration(cause)
            }
            is MonthlyPrayerResult.Success -> error("Success is not a preparation failure")
        }
    }
}
