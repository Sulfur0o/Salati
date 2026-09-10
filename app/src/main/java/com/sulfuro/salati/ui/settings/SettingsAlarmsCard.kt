package com.sulfuro.salati.ui.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sulfuro.salati.R
import com.sulfuro.salati.core.audio.AdhanAudioStore
import com.sulfuro.salati.core.alarms.PrayerSilentModeController
import com.sulfuro.salati.core.alarms.PrayerSilentModeScheduler
import com.sulfuro.salati.data.settings.CalculationSettings
import com.sulfuro.salati.theme.SalatiSpacing
import com.sulfuro.salati.ui.components.SettingSection
import com.sulfuro.salati.ui.components.ValueSelectionRow
import androidx.compose.runtime.saveable.rememberSaveable
import com.sulfuro.salati.core.permissions.AppPermissionState
import com.sulfuro.salati.ui.components.ExpandableSettingSection
import com.sulfuro.salati.ui.components.SegmentedTabRow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Everything that decides whether, and how loudly, the phone speaks at prayer time.
 */
@Composable
internal fun SettingsAlarmsCard(
    settings: CalculationSettings,
    permissionState: AppPermissionState,
    saveSettings: ((CalculationSettings) -> CalculationSettings) -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()

    var showAdhanSheet by remember { mutableStateOf(false) }
    var showFajrAdhanSheet by remember { mutableStateOf(false) }
    var showAlarmsAdvanced by rememberSaveable { mutableStateOf(false) }

    SettingSection(title = stringResource(R.string.settings_card_alarms_title)) {
        SettingToggleRow(
            title = stringResource(R.string.settings_reminders_mute),
            supportingText = stringResource(R.string.settings_reminders_mute_description),
            checked = settings.notificationsMuted,
            onCheckedChange = { isChecked -> saveSettings { it.copy(notificationsMuted = isChecked) } }
        )
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        SettingToggleRow(
            title = stringResource(R.string.settings_reminders_sound),
            supportingText = stringResource(R.string.settings_reminders_sound_description),
            checked = settings.soundEnabled,
            onCheckedChange = { isChecked -> saveSettings { it.copy(soundEnabled = isChecked) } }
        )
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // Backup restores the chosen adhan's name but deliberately not its audio, so
        // on a new device the recording can be named yet absent. The alarm already
        // falls back to the notification tone in that case; this stops the row from
        // claiming otherwise.
        val chosenAdhanId = settings.adhanSoundId
        val chosenAdhanIsOnDisk by produceState(initialValue = false, chosenAdhanId) {
            value = !chosenAdhanId.isNullOrBlank() &&
                withContext(Dispatchers.IO) { AdhanAudioStore.isDownloaded(appContext, chosenAdhanId) }
        }
        val selectedAdhanLabel = if (chosenAdhanIsOnDisk) {
            settings.adhanSoundName ?: chosenAdhanId.orEmpty()
        } else {
            stringResource(R.string.settings_adhan_device_tone)
        }
        ValueSelectionRow(
            title = stringResource(R.string.settings_adhan_sound_row),
            value = selectedAdhanLabel,
            expanded = showAdhanSheet,
            onExpandedChange = { showAdhanSheet = it }
        )
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        // Fajr is offered separately because its call is different, not because someone
        // might prefer variety: the dawn adhan adds "prayer is better than sleep".
        val chosenFajrAdhanId = settings.fajrAdhanSoundId
        val chosenFajrAdhanIsOnDisk by produceState(initialValue = false, chosenFajrAdhanId) {
            value = !chosenFajrAdhanId.isNullOrBlank() &&
                withContext(Dispatchers.IO) { AdhanAudioStore.isDownloaded(appContext, chosenFajrAdhanId) }
        }
        ValueSelectionRow(
            title = stringResource(R.string.settings_adhan_fajr_row),
            value = if (chosenFajrAdhanIsOnDisk) {
                settings.fajrAdhanSoundName ?: chosenFajrAdhanId.orEmpty()
            } else {
                stringResource(R.string.settings_adhan_fajr_same)
            },
            expanded = showFajrAdhanSheet,
            onExpandedChange = { showFajrAdhanSheet = it }
        )
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        SettingToggleRow(
            title = stringResource(R.string.settings_reminders_vibration),
            supportingText = stringResource(R.string.settings_reminders_vibration_description),
            checked = settings.vibrateEnabled,
            onCheckedChange = { isChecked -> saveSettings { it.copy(vibrateEnabled = isChecked) } }
        )
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        SettingToggleRow(
            title = stringResource(R.string.settings_silent_mode_title),
            supportingText = stringResource(R.string.settings_silent_mode_description),
            checked = settings.silentModeAutomationEnabled,
            onCheckedChange = { isChecked ->
                PrayerSilentModeScheduler.setAutomationEnabled(appContext, isChecked)
                saveSettings { it.copy(silentModeAutomationEnabled = isChecked) }
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

        if (settings.silentModeAutomationEnabled) {
            val offsetOptions = listOf(0, 5, 10, 15)
            val offsetLabels = listOf(
                stringResource(R.string.settings_silent_mode_offset_now),
                stringResource(R.string.settings_silent_mode_offset_5),
                stringResource(R.string.settings_silent_mode_offset_10),
                stringResource(R.string.settings_silent_mode_offset_15)
            )
            val selectedOffset = offsetOptions.indexOf(settings.silentModeMinutesAfterAdhan)
                .coerceAtLeast(0)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SalatiSpacing.md, vertical = SalatiSpacing.xs),
                verticalArrangement = Arrangement.spacedBy(SalatiSpacing.xs)
            ) {
                Text(
                    text = stringResource(R.string.settings_silent_mode_offset_title),
                    style = MaterialTheme.typography.bodyLarge
                )
                SegmentedTabRow(
                    tabs = offsetLabels,
                    selectedTabIndex = selectedOffset,
                    onTabSelected = { index ->
                        saveSettings { it.copy(silentModeMinutesAfterAdhan = offsetOptions[index]) }
                    }
                )

                Spacer(modifier = Modifier.height(SalatiSpacing.xs))

                val durationOptions = listOf(15, 20, 30)
                val durationLabels = listOf(
                    stringResource(R.string.settings_silent_mode_duration_15),
                    stringResource(R.string.settings_silent_mode_duration_20),
                    stringResource(R.string.settings_silent_mode_duration_30)
                )
                val selectedDuration = durationOptions.indexOf(settings.silentModeDurationMinutes)
                    .coerceAtLeast(0)
                Text(
                    text = stringResource(R.string.settings_silent_mode_duration_title),
                    style = MaterialTheme.typography.bodyLarge
                )
                SegmentedTabRow(
                    tabs = durationLabels,
                    selectedTabIndex = selectedDuration,
                    onTabSelected = { index ->
                        saveSettings { it.copy(silentModeDurationMinutes = durationOptions[index]) }
                    }
                )
            }
        }

        ExpandableSettingSection(
            sectionName = stringResource(R.string.settings_card_alarms_title),
            expanded = showAlarmsAdvanced,
            onExpandedChange = { showAlarmsAdvanced = it }
        ) {
            SettingToggleRow(
                title = stringResource(R.string.settings_reminders_white_days_title),
                supportingText = stringResource(R.string.settings_reminders_white_days_description),
                checked = settings.whiteDaysReminder,
                onCheckedChange = { isChecked -> saveSettings { it.copy(whiteDaysReminder = isChecked) } }
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = SalatiSpacing.xs, horizontal = SalatiSpacing.md)
            ) {
                var prePrayerDraft by remember(settings.prePrayerMinutes) {
                    mutableFloatStateOf(settings.prePrayerMinutes.toFloat())
                }
                val prePrayerMinutes = prePrayerDraft.toInt()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.settings_reminders_pre_prayer),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        // Zero disables the reminder outright in AlarmScheduler, so
                        // labelling it "0 minutes before" would promise an alert that
                        // never arrives.
                        text = if (prePrayerMinutes == 0) {
                            stringResource(R.string.settings_reminders_pre_prayer_off)
                        } else {
                            pluralStringResource(
                                R.plurals.settings_reminders_pre_prayer_value,
                                prePrayerMinutes,
                                prePrayerMinutes
                            )
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                val prePrayerDesc = if (prePrayerMinutes == 0) {
                    stringResource(R.string.settings_reminders_pre_prayer_off_accessibility)
                } else {
                    pluralStringResource(
                        R.plurals.settings_reminders_pre_prayer_accessibility,
                        prePrayerMinutes,
                        prePrayerMinutes
                    )
                }
                SalatiSlider(
                    value = prePrayerDraft,
                    onValueChange = { prePrayerDraft = it },
                    onValueChangeFinished = {
                        saveSettings { it.copy(prePrayerMinutes = prePrayerDraft.toInt()) }
                    },
                    valueRange = 0f..30f,
                    steps = 5,
                    contentDescription = prePrayerDesc
                )
            }
        }
    }

    if (showAdhanSheet) {
        AdhanSoundSheet(
            selectedId = settings.adhanSoundId,
            onSelect = { option ->
                saveSettings { it.copy(adhanSoundId = option?.id, adhanSoundName = option?.name) }
            },
            onDismiss = { showAdhanSheet = false }
        )
    }

    if (showFajrAdhanSheet) {
        AdhanSoundSheet(
            selectedId = settings.fajrAdhanSoundId,
            onSelect = { option ->
                saveSettings {
                    it.copy(fajrAdhanSoundId = option?.id, fajrAdhanSoundName = option?.name)
                }
            },
            onDismiss = { showFajrAdhanSheet = false },
            fajr = true
        )
    }
}
