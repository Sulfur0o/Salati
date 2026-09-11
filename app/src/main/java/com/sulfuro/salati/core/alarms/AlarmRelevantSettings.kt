package com.sulfuro.salati.core.alarms

import com.sulfuro.salati.data.settings.CalculationSettings

data class AlarmRelevantSettingsFingerprint(
    val calculationMethod: String,
    val madhab: String,
    val highLatitudeRule: String,
    val notificationsMuted: Boolean,
    val prePrayerMinutes: Int,
    val vibrateEnabled: Boolean,
    val soundEnabled: Boolean,
    val adhanSoundId: String?,
    val fajrAdhanSoundId: String?,
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
        calculationMethod = prayer.calculationMethod,
        madhab = prayer.madhab,
        highLatitudeRule = prayer.highLatitudeRule,
        notificationsMuted = alarms.notificationsMuted,
        prePrayerMinutes = alarms.prePrayerMinutes,
        vibrateEnabled = alarms.vibrateEnabled,
        soundEnabled = alarms.soundEnabled,
        adhanSoundId = alarms.adhanSoundId,
        fajrAdhanSoundId = alarms.fajrAdhanSoundId,
        whiteDaysReminder = alarms.whiteDaysReminder,
        silentModeAutomationEnabled = alarms.silentModeAutomationEnabled,
        silentModeMinutesAfterAdhan = alarms.silentModeMinutesAfterAdhan,
        silentModeDurationMinutes = alarms.silentModeDurationMinutes,
        hijriOffset = prayer.hijriOffset,
        latitude = location.latitude,
        longitude = location.longitude,
        timezoneId = location.timezoneId
    )
}

fun getAlarmSettingsRefreshTrigger(
    previous: CalculationSettings,
    updated: CalculationSettings
): AlarmSettingsRefreshTrigger {
    if (previous.alarmRelevantFingerprint() == updated.alarmRelevantFingerprint()) {
        return AlarmSettingsRefreshTrigger.NONE
    }
    return if (previous.alarms.notificationsMuted != updated.alarms.notificationsMuted ||
        previous.alarms.whiteDaysReminder != updated.alarms.whiteDaysReminder ||
        previous.alarms.silentModeAutomationEnabled != updated.alarms.silentModeAutomationEnabled
    ) {
        AlarmSettingsRefreshTrigger.IMMEDIATE
    } else {
        AlarmSettingsRefreshTrigger.DEBOUNCED
    }
}
