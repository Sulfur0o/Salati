package com.sulfuro.salati.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.sulfuro.salati.R
import com.sulfuro.salati.core.audio.AdhanAudioStore
import com.sulfuro.salati.core.alarms.PrayerSilentModeController
import com.sulfuro.salati.core.alarms.PrayerSilentModeScheduler
import com.sulfuro.salati.data.settings.CalculationSettings
import com.sulfuro.salati.ui.components.SettingSection
import com.sulfuro.salati.ui.components.SettingStepperRow
import com.sulfuro.salati.ui.components.ValueSelectionRow
import com.sulfuro.salati.core.permissions.AppPermissionState
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The five ways an alert can arrive, as one choice instead of three switches.
 *
 * Muted, Sound and Vibration were independent toggles, which is eight combinations of
 * which only five mean anything and none of which were named. A user had to work out for
 * themselves what "mute off, sound off, vibration on" did. The stored fields are
 * unchanged - the alarm code and the backup format still read the same three booleans -
 * but there is now exactly one row, and every option says what will happen.
 */
internal object AlertStyle {
    const val SOUND_AND_VIBRATION = "sound_vibration"
    const val SOUND_ONLY = "sound"
    const val VIBRATION_ONLY = "vibration"
    const val SILENT = "silent"
    const val NONE = "none"

    fun of(muted: Boolean, sound: Boolean, vibrate: Boolean): String = when {
        muted -> NONE
        sound && vibrate -> SOUND_AND_VIBRATION
        sound -> SOUND_ONLY
        vibrate -> VIBRATION_ONLY
        else -> SILENT
    }

    /** (muted, sound, vibrate) for an option id. */
    fun toFlags(id: String): Triple<Boolean, Boolean, Boolean> = when (id) {
        NONE -> Triple(true, false, false)
        SOUND_AND_VIBRATION -> Triple(false, true, true)
        SOUND_ONLY -> Triple(false, true, false)
        VIBRATION_ONLY -> Triple(false, false, true)
        else -> Triple(false, false, false)
    }

    /** Choosing a recording is pointless when nothing will play one. */
    fun playsAudio(id: String): Boolean = id == SOUND_AND_VIBRATION || id == SOUND_ONLY
}

/** What arrives at a prayer time, and what it sounds like. */
@Composable
internal fun SettingsAlarmsCard(
    settings: CalculationSettings,
    saveSettings: ((CalculationSettings) -> CalculationSettings) -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext

    var showAlertStyleSheet by remember { mutableStateOf(false) }
    var showAdhanSheet by remember { mutableStateOf(false) }
    var showFajrAdhanSheet by remember { mutableStateOf(false) }

    val alertOptions = listOf(
        AlertStyle.SOUND_AND_VIBRATION to stringResource(R.string.settings_alerts_sound_vibration),
        AlertStyle.SOUND_ONLY to stringResource(R.string.settings_alerts_sound_only),
        AlertStyle.VIBRATION_ONLY to stringResource(R.string.settings_alerts_vibration_only),
        AlertStyle.SILENT to stringResource(R.string.settings_alerts_silent),
        AlertStyle.NONE to stringResource(R.string.settings_alerts_none)
    )
    val alertStyle = AlertStyle.of(
        settings.alarms.notificationsMuted,
        settings.alarms.soundEnabled,
        settings.alarms.vibrateEnabled
    )

    // 0..30 in fives. The stepper walks this list, so zero stays reachable as "Off" and
    // nothing between the stops is.
    val prePrayerStops = listOf(0, 5, 10, 15, 20, 25, 30)
    val prePrayerIndex = prePrayerStops.indexOf(settings.alarms.prePrayerMinutes).coerceAtLeast(0)

    SettingSection(title = stringResource(R.string.settings_card_notifications_title)) {
        ValueSelectionRow(
            title = stringResource(R.string.settings_alerts_label),
            value = alertOptions.first { it.first == alertStyle }.second,
            expanded = showAlertStyleSheet,
            onExpandedChange = { showAlertStyleSheet = it }
        )

        // The two adhan rows used to stay live while everything was muted, so it was
        // possible to spend a while picking a reciter that would never be heard.
        if (AlertStyle.playsAudio(alertStyle)) {
            SettingsDivider()

            // Backup restores the chosen adhan's name but deliberately not its audio, so
            // on a new device the recording can be named yet absent. The alarm already
            // falls back to the notification tone in that case; this stops the row from
            // claiming otherwise.
            val chosenAdhanId = settings.alarms.adhanSoundId
            val chosenAdhanIsOnDisk by produceState(initialValue = false, chosenAdhanId) {
                value = !chosenAdhanId.isNullOrBlank() &&
                    withContext(Dispatchers.IO) { AdhanAudioStore.isDownloaded(appContext, chosenAdhanId) }
            }
            ValueSelectionRow(
                title = stringResource(R.string.settings_adhan_sound_row),
                value = if (chosenAdhanIsOnDisk) {
                    settings.alarms.adhanSoundName ?: chosenAdhanId.orEmpty()
                } else {
                    stringResource(R.string.settings_adhan_device_tone)
                },
                expanded = showAdhanSheet,
                onExpandedChange = { showAdhanSheet = it }
            )
            SettingsDivider()

            // Fajr is offered separately because its call is different, not because someone
            // might prefer variety: the dawn adhan adds "prayer is better than sleep".
            val chosenFajrAdhanId = settings.alarms.fajrAdhanSoundId
            val chosenFajrAdhanIsOnDisk by produceState(initialValue = false, chosenFajrAdhanId) {
                value = !chosenFajrAdhanId.isNullOrBlank() &&
                    withContext(Dispatchers.IO) { AdhanAudioStore.isDownloaded(appContext, chosenFajrAdhanId) }
            }
            ValueSelectionRow(
                title = stringResource(R.string.settings_adhan_fajr_row),
                value = if (chosenFajrAdhanIsOnDisk) {
                    settings.alarms.fajrAdhanSoundName ?: chosenFajrAdhanId.orEmpty()
                } else {
                    stringResource(R.string.settings_adhan_fajr_same)
                },
                expanded = showFajrAdhanSheet,
                onExpandedChange = { showFajrAdhanSheet = it }
            )
        }

        SettingsDivider()
        SettingStepperRow(
            title = stringResource(R.string.settings_reminders_pre_prayer),
            value = prePrayerLabel(settings.alarms.prePrayerMinutes),
            canDecrease = prePrayerIndex > 0,
            canIncrease = prePrayerIndex < prePrayerStops.lastIndex,
            onDecrease = {
                saveSettings { it.copy(alarms = it.alarms.copy(prePrayerMinutes = prePrayerStops[prePrayerIndex - 1])) }
            },
            onIncrease = {
                saveSettings { it.copy(alarms = it.alarms.copy(prePrayerMinutes = prePrayerStops[prePrayerIndex + 1])) }
            }
        )

        SettingsDivider()
        // Was behind "Advanced", which is a strange place for the only reminder in the app
        // that is not about prayer.
        SettingToggleRow(
            title = stringResource(R.string.settings_reminders_white_days_title),
            supportingText = stringResource(R.string.settings_reminders_white_days_description),
            checked = settings.alarms.whiteDaysReminder,
            onCheckedChange = { isChecked ->
                saveSettings { it.copy(alarms = it.alarms.copy(whiteDaysReminder = isChecked)) }
            }
        )
    }

    if (showAlertStyleSheet) {
        OptionSelectionSheet(
            title = stringResource(R.string.settings_alerts_label),
            selectedId = alertStyle,
            options = alertOptions,
            onSelect = { id ->
                val (muted, sound, vibrate) = AlertStyle.toFlags(id)
                saveSettings {
                    it.copy(
                        alarms = it.alarms.copy(
                            notificationsMuted = muted,
                            soundEnabled = sound,
                            vibrateEnabled = vibrate
                        )
                    )
                }
            },
            onDismiss = { showAlertStyleSheet = false }
        )
    }

    if (showAdhanSheet) {
        AdhanSoundSheet(
            selectedId = settings.alarms.adhanSoundId,
            onSelect = { option ->
                saveSettings { it.copy(alarms = it.alarms.copy(adhanSoundId = option?.id, adhanSoundName = option?.name)) }
            },
            onDismiss = { showAdhanSheet = false }
        )
    }

    if (showFajrAdhanSheet) {
        AdhanSoundSheet(
            selectedId = settings.alarms.fajrAdhanSoundId,
            onSelect = { option ->
                saveSettings {
                    it.copy(alarms = it.alarms.copy(fajrAdhanSoundId = option?.id, fajrAdhanSoundName = option?.name))
                }
            },
            onDismiss = { showFajrAdhanSheet = false },
            fajr = true
        )
    }
}

/**
 * What the phone does once the adhan has been called.
 *
 * Its own section: silencing the ringer is something the phone does to itself, not a kind
 * of alert, and it sat in the middle of the alert settings looking like one.
 */
@Composable
internal fun SettingsDuringPrayerCard(
    settings: CalculationSettings,
    permissionState: AppPermissionState,
    saveSettings: ((CalculationSettings) -> CalculationSettings) -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()

    val offsetStops = listOf(0, 5, 10, 15)
    val offsetLabels = listOf(
        stringResource(R.string.settings_silent_mode_offset_now),
        stringResource(R.string.settings_silent_mode_offset_5),
        stringResource(R.string.settings_silent_mode_offset_10),
        stringResource(R.string.settings_silent_mode_offset_15)
    )
    // 15, 20, 30: the jump at the end is uneven, which is why the stepper walks the list
    // rather than adding a fixed amount.
    val durationStops = listOf(15, 20, 30)
    val durationLabels = listOf(
        stringResource(R.string.settings_silent_mode_duration_15),
        stringResource(R.string.settings_silent_mode_duration_20),
        stringResource(R.string.settings_silent_mode_duration_30)
    )
    val offsetIndex = offsetStops.indexOf(settings.alarms.silentModeMinutesAfterAdhan).coerceAtLeast(0)
    val durationIndex = durationStops.indexOf(settings.alarms.silentModeDurationMinutes).coerceAtLeast(0)

    SettingSection(title = stringResource(R.string.settings_card_during_prayer_title)) {
        SettingToggleRow(
            title = stringResource(R.string.settings_silent_mode_title),
            supportingText = stringResource(R.string.settings_silent_mode_description),
            checked = settings.alarms.silentModeAutomationEnabled,
            onCheckedChange = { isChecked ->
                PrayerSilentModeScheduler.setAutomationEnabled(appContext, isChecked)
                saveSettings { it.copy(alarms = it.alarms.copy(silentModeAutomationEnabled = isChecked)) }
                if (isChecked && !permissionState.notificationPolicyAccess) {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                        )
                    }
                } else if (!isChecked) {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            PrayerSilentModeController.forceRestore(appContext)
                        }
                    }
                }
            }
        )

        if (settings.alarms.silentModeAutomationEnabled) {
            SettingsDivider()
            SettingStepperRow(
                title = stringResource(R.string.settings_silent_mode_offset_title),
                value = offsetLabels[offsetIndex],
                canDecrease = offsetIndex > 0,
                canIncrease = offsetIndex < offsetStops.lastIndex,
                onDecrease = {
                    saveSettings { it.copy(alarms = it.alarms.copy(silentModeMinutesAfterAdhan = offsetStops[offsetIndex - 1])) }
                },
                onIncrease = {
                    saveSettings { it.copy(alarms = it.alarms.copy(silentModeMinutesAfterAdhan = offsetStops[offsetIndex + 1])) }
                }
            )
            SettingsDivider()
            SettingStepperRow(
                title = stringResource(R.string.settings_silent_mode_duration_title),
                value = durationLabels[durationIndex],
                canDecrease = durationIndex > 0,
                canIncrease = durationIndex < durationStops.lastIndex,
                onDecrease = {
                    saveSettings { it.copy(alarms = it.alarms.copy(silentModeDurationMinutes = durationStops[durationIndex - 1])) }
                },
                onIncrease = {
                    saveSettings { it.copy(alarms = it.alarms.copy(silentModeDurationMinutes = durationStops[durationIndex + 1])) }
                }
            )
        }
    }
}

/**
 * Zero disables the reminder outright in AlarmScheduler, so labelling it "0 minutes
 * before" would promise an alert that never arrives.
 */
@Composable
private fun prePrayerLabel(minutes: Int): String =
    if (minutes == 0) {
        stringResource(R.string.settings_reminders_pre_prayer_off)
    } else {
        pluralStringResource(R.plurals.settings_reminders_pre_prayer_value, minutes, minutes)
    }
