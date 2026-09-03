package io.github.sulfuro25.salati.core.notifications

import io.github.sulfuro25.salati.data.settings.CalculationSettings

data class AlarmRelevantSettingsFingerprint(
    val calculationMethod: String,
    val madhab: String,
    val highLatitudeRule: String,
    val notificationsMuted: Boolean,
    val prePrayerMinutes: Int,
    val vibrateEnabled: Boolean,
    val soundEnabled: Boolean,
    val whiteDaysReminder: Boolean,
    val silentModeAutomationEnabled: Boolean,
    val silentModeMinutesAfterAdhan: Int,
    val silentModeDurationMinutes: Int,
    val hijriOffset: Int,
    val latitude: Double,
    val longitude: Double,
    val timezoneId: String
)

enum class AlarmSettingsRefreshTrigger {
    NONE,
    IMMEDIATE,
    DEBOUNCED
}

fun CalculationSettings.alarmRelevantFingerprint(): AlarmRelevantSettingsFingerprint {
    return AlarmRelevantSettingsFingerprint(
        calculationMethod = calculationMethod,
        madhab = madhab,
        highLatitudeRule = highLatitudeRule,
        notificationsMuted = notificationsMuted,
        prePrayerMinutes = prePrayerMinutes,
        vibrateEnabled = vibrateEnabled,
        soundEnabled = soundEnabled,
        whiteDaysReminder = whiteDaysReminder,
        silentModeAutomationEnabled = silentModeAutomationEnabled,
        silentModeMinutesAfterAdhan = silentModeMinutesAfterAdhan,
        silentModeDurationMinutes = silentModeDurationMinutes,
        hijriOffset = hijriOffset,
        latitude = latitude,
        longitude = longitude,
        timezoneId = timezoneId
    )
}

fun getAlarmSettingsRefreshTrigger(
    previous: CalculationSettings,
    updated: CalculationSettings
): AlarmSettingsRefreshTrigger {
    if (previous.alarmRelevantFingerprint() == updated.alarmRelevantFingerprint()) {
        return AlarmSettingsRefreshTrigger.NONE
    }
    return if (previous.notificationsMuted != updated.notificationsMuted ||
        previous.whiteDaysReminder != updated.whiteDaysReminder ||
        previous.silentModeAutomationEnabled != updated.silentModeAutomationEnabled
    ) {
        AlarmSettingsRefreshTrigger.IMMEDIATE
    } else {
        AlarmSettingsRefreshTrigger.DEBOUNCED
    }
}
