package io.github.sulfuro25.salati.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sulfuro25.salati.R
import io.github.sulfuro25.salati.data.settings.CalculationSettings
import io.github.sulfuro25.salati.theme.SalatiSpacing
import io.github.sulfuro25.salati.ui.components.SettingSection
import io.github.sulfuro25.salati.ui.components.ValueSelectionRow
import io.github.sulfuro25.salati.data.settings.TimeFormatPreference
import io.github.sulfuro25.salati.ui.components.SegmentedTabRow

/**
 * How the app presents itself: theme, language, clock format and the Hijri offset.
 */
@Composable
internal fun SettingsAppearanceCard(
    settings: CalculationSettings,
    saveSettings: ((CalculationSettings) -> CalculationSettings) -> Unit
) {
    val context = LocalContext.current

    val languageOptions = listOf(
        "" to stringResource(R.string.settings_language_system),
        "en" to stringResource(R.string.settings_language_en),
        "ar" to stringResource(R.string.settings_language_ar),
        "fr" to stringResource(R.string.settings_language_fr),
        "nl" to stringResource(R.string.settings_language_nl)
    )

    var showLanguageSheet by remember { mutableStateOf(false) }

    SettingSection(title = stringResource(R.string.settings_card_appearance_title)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SalatiSpacing.sm, horizontal = SalatiSpacing.md)
        ) {
            Text(
                text = stringResource(R.string.settings_theme_dark_mode),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.settings_theme_dark_mode_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(SalatiSpacing.sm))
            val themeOptionLabels = listOf(
                stringResource(R.string.settings_theme_option_system),
                stringResource(R.string.settings_theme_option_light),
                stringResource(R.string.settings_theme_option_dark)
            )
            val selectedThemeIndex = when (settings.isDarkMode) {
                null -> 0
                false -> 1
                true -> 2
            }
            SegmentedTabRow(
                tabs = themeOptionLabels,
                selectedTabIndex = selectedThemeIndex,
                onTabSelected = { index ->
                    val newValue = when (index) {
                        1 -> false
                        2 -> true
                        else -> null
                    }
                    saveSettings { it.copy(isDarkMode = newValue) }
                }
            )
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        val currentLangCode = settings.appLanguageCode ?: ""
        val currentLangName = languageOptions.firstOrNull { it.first == currentLangCode }?.second
            ?: stringResource(R.string.settings_language_system)
        ValueSelectionRow(
            title = stringResource(R.string.settings_language_label),
            value = currentLangName,
            expanded = showLanguageSheet,
            onExpandedChange = { showLanguageSheet = it }
        )

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SalatiSpacing.sm, horizontal = SalatiSpacing.md)
        ) {
            Text(
                text = stringResource(R.string.settings_time_format_title),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(SalatiSpacing.sm))
            val timeFormatOptions = listOf(
                TimeFormatPreference.SYSTEM,
                TimeFormatPreference.TWELVE_HOUR,
                TimeFormatPreference.TWENTY_FOUR_HOUR
            )
            val timeFormatLabels = listOf(
                stringResource(R.string.settings_time_format_system),
                stringResource(R.string.settings_time_format_12h),
                stringResource(R.string.settings_time_format_24h)
            )
            val selectedTimeFormat = timeFormatOptions.indexOf(settings.timeFormat)
                .coerceAtLeast(0)
            SegmentedTabRow(
                tabs = timeFormatLabels,
                selectedTabIndex = selectedTimeFormat,
                onTabSelected = { index ->
                    saveSettings { it.copy(timeFormat = timeFormatOptions[index]) }
                }
            )
        }

        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SalatiSpacing.xs, horizontal = SalatiSpacing.md)
        ) {
            var hijriDraft by remember(settings.hijriOffset) {
                mutableFloatStateOf(settings.hijriOffset.toFloat())
            }
            val currentOffset = hijriDraft.toInt()
            val offsetText = when {
                currentOffset == 0 -> stringResource(R.string.settings_calendar_hijri_offset_zero)
                currentOffset > 0 -> pluralStringResource(
                    R.plurals.settings_calendar_hijri_offset_plus, currentOffset, currentOffset
                )
                else -> pluralStringResource(
                    R.plurals.settings_calendar_hijri_offset_minus, -currentOffset, -currentOffset
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.settings_calendar_hijri_offset),
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = offsetText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            val hijriDesc = stringResource(
                R.string.settings_calendar_hijri_offset_accessibility, offsetText
            )
            SalatiSlider(
                value = hijriDraft,
                onValueChange = { hijriDraft = it },
                onValueChangeFinished = {
                    saveSettings { it.copy(hijriOffset = hijriDraft.toInt()) }
                },
                valueRange = -2f..2f,
                steps = 3,
                contentDescription = hijriDesc
            )
        }
    }

    if (showLanguageSheet) {
        OptionSelectionSheet(
            title = stringResource(R.string.settings_language_title),
            selectedId = settings.appLanguageCode ?: "",
            options = languageOptions,
            onSelect = { langCode ->
                val codeOrNull: String? = if (langCode.isEmpty()) null else langCode
                LoadedSettingsCache.latest = settings.copy(appLanguageCode = codeOrNull)
                val appContext = context.applicationContext
                CoroutineScope(Dispatchers.IO).launch {
                    val prefs = io.github.sulfuro25.salati.data.settings.SalatiPreferences(appContext)
                    prefs.updateSettings { it.copy(appLanguageCode = codeOrNull) }
                    withContext(Dispatchers.Main) {
                        applyAppLanguage(context, codeOrNull)
                    }
                }
            },
            onDismiss = { showLanguageSheet = false }
        )
    }
}
