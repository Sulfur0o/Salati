package com.sulfuro.salati.ui.settings

import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sulfuro.salati.R
import com.sulfuro.salati.data.settings.CalculationSettings
import com.sulfuro.salati.ui.components.SettingSection
import com.sulfuro.salati.ui.components.SettingStepperRow
import com.sulfuro.salati.ui.components.ValueSelectionRow
import com.sulfuro.salati.data.settings.TimeFormatPreference

/**
 * How the app presents itself: theme, language, clock and the Hijri offset.
 *
 * Every one of these used to wear a different control. Theme and clock were segmented
 * rows with their titles stacked above them, the Hijri offset was a slider with its value
 * on the right, and language was a value row - so in a single card the eye had to read
 * left-to-right, then top-to-bottom, then left-to-right again. They are all value rows
 * now: title on the left, the current answer on the right, a sheet to change it. One shape
 * costs a tap on the two that used to be inline and buys a card you can scan.
 */
@Composable
internal fun SettingsAppearanceCard(
    settings: CalculationSettings,
    saveSettings: ((CalculationSettings) -> CalculationSettings) -> Unit
) {
    val context = LocalContext.current

    val themeOptions = listOf(
        "system" to stringResource(R.string.settings_theme_option_system),
        "light" to stringResource(R.string.settings_theme_option_light),
        "dark" to stringResource(R.string.settings_theme_option_dark)
    )
    val languageOptions = listOf(
        "" to stringResource(R.string.settings_language_system),
        "en" to stringResource(R.string.settings_language_en),
        "ar" to stringResource(R.string.settings_language_ar),
        "fr" to stringResource(R.string.settings_language_fr),
        "nl" to stringResource(R.string.settings_language_nl)
    )
    val clockOptions = listOf(
        TimeFormatPreference.SYSTEM to stringResource(R.string.settings_time_format_system),
        TimeFormatPreference.TWELVE_HOUR to stringResource(R.string.settings_time_format_12h),
        TimeFormatPreference.TWENTY_FOUR_HOUR to stringResource(R.string.settings_time_format_24h)
    )
    // -2..2, the range the slider offered.
    val hijriStops = (-2..2).toList()
    val hijriIndex = hijriStops.indexOf(settings.prayer.hijriOffset).coerceAtLeast(0)

    var showThemeSheet by remember { mutableStateOf(false) }
    var showLanguageSheet by remember { mutableStateOf(false) }
    var showClockSheet by remember { mutableStateOf(false) }

    // "Follow the system" is an absence of choice, not a third value, so it has to survive
    // as null rather than being flattened into light.
    val selectedTheme = when (settings.appearance.isDarkMode) {
        null -> "system"
        false -> "light"
        true -> "dark"
    }
    val currentLangCode = settings.appearance.appLanguageCode ?: ""

    SettingSection(title = stringResource(R.string.settings_card_display_title)) {
        ValueSelectionRow(
            title = stringResource(R.string.settings_theme_label),
            value = themeOptions.first { it.first == selectedTheme }.second,
            expanded = showThemeSheet,
            onExpandedChange = { showThemeSheet = it }
        )
        SettingsDivider()

        ValueSelectionRow(
            title = stringResource(R.string.settings_language_label),
            value = languageOptions.firstOrNull { it.first == currentLangCode }?.second
                ?: stringResource(R.string.settings_language_system),
            expanded = showLanguageSheet,
            onExpandedChange = { showLanguageSheet = it }
        )
        SettingsDivider()

        ValueSelectionRow(
            title = stringResource(R.string.settings_time_format_title),
            value = clockOptions.firstOrNull { it.first == settings.appearance.timeFormat }?.second
                ?: stringResource(R.string.settings_time_format_system),
            expanded = showClockSheet,
            onExpandedChange = { showClockSheet = it }
        )
        SettingsDivider()

        SettingStepperRow(
            title = stringResource(R.string.settings_calendar_hijri_offset),
            value = hijriOffsetLabel(settings.prayer.hijriOffset),
            canDecrease = hijriIndex > 0,
            canIncrease = hijriIndex < hijriStops.lastIndex,
            onDecrease = {
                saveSettings { it.copy(prayer = it.prayer.copy(hijriOffset = hijriStops[hijriIndex - 1])) }
            },
            onIncrease = {
                saveSettings { it.copy(prayer = it.prayer.copy(hijriOffset = hijriStops[hijriIndex + 1])) }
            }
        )
    }

    if (showThemeSheet) {
        OptionSelectionSheet(
            title = stringResource(R.string.settings_theme_label),
            subtitle = stringResource(R.string.settings_theme_dark_mode_desc),
            selectedId = selectedTheme,
            options = themeOptions,
            onSelect = { id ->
                val isDark: Boolean? = when (id) {
                    "light" -> false
                    "dark" -> true
                    else -> null
                }
                saveSettings { it.copy(appearance = it.appearance.copy(isDarkMode = isDark)) }
            },
            onDismiss = { showThemeSheet = false }
        )
    }

    if (showClockSheet) {
        OptionSelectionSheet(
            title = stringResource(R.string.settings_time_format_title),
            selectedId = settings.appearance.timeFormat,
            options = clockOptions,
            onSelect = { id ->
                saveSettings {
                    it.copy(appearance = it.appearance.copy(timeFormat = id))
                }
            },
            onDismiss = { showClockSheet = false }
        )
    }

    if (showLanguageSheet) {
        OptionSelectionSheet(
            title = stringResource(R.string.settings_language_title),
            selectedId = currentLangCode,
            options = languageOptions,
            onSelect = { langCode ->
                val codeOrNull: String? = if (langCode.isEmpty()) null else langCode
                LoadedSettingsCache.latest = settings.copy(appearance = settings.appearance.copy(appLanguageCode = codeOrNull))
                val appContext = context.applicationContext
                CoroutineScope(Dispatchers.IO).launch {
                    val prefs = com.sulfuro.salati.data.settings.SalatiPreferences(appContext)
                    prefs.updateSettings { it.copy(appearance = it.appearance.copy(appLanguageCode = codeOrNull)) }
                    withContext(Dispatchers.Main) {
                        applyAppLanguage(context, codeOrNull)
                    }
                }
            },
            onDismiss = { showLanguageSheet = false }
        )
    }
}

/** "0 days", "+1 day", "-2 days" - the same wording the slider used to show. */
@Composable
private fun hijriOffsetLabel(offset: Int): String = when {
    offset == 0 -> stringResource(R.string.settings_calendar_hijri_offset_zero)
    offset > 0 -> pluralStringResource(R.plurals.settings_calendar_hijri_offset_plus, offset, offset)
    else -> pluralStringResource(R.plurals.settings_calendar_hijri_offset_minus, -offset, -offset)
}

/**
 * The rule between rows. It was written out at every call site, which is how one of them
 * came to be missing.
 */
@Composable
internal fun SettingsDivider() {
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}
