package com.sulfuro.salati.ui.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sulfuro.salati.R
import com.sulfuro.salati.core.audio.AdhanAudioStore
import com.sulfuro.salati.core.audio.AdhanOption
import com.sulfuro.salati.core.zakat.zakatCurrencyOptions
import com.sulfuro.salati.core.location.DeviceLocationProvider
import com.sulfuro.salati.core.location.DeviceLocationResult
import com.sulfuro.salati.core.location.PrayerLocationResolver
import com.sulfuro.salati.core.work.enqueueAlarmSettingsRefreshIfNeeded
import com.sulfuro.salati.core.alarms.PrayerSilentModeController
import com.sulfuro.salati.core.alarms.PrayerSilentModeScheduler
import com.sulfuro.salati.core.permissions.readAppPermissionState
import com.sulfuro.salati.data.settings.CalculationSettings
import com.sulfuro.salati.data.settings.SalatiPreferences
import com.sulfuro.salati.theme.SalatiSpacing
import com.sulfuro.salati.ui.components.SettingRow
import com.sulfuro.salati.ui.components.SettingSection
import com.sulfuro.salati.ui.components.ValueSelectionRow
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import com.sulfuro.salati.core.permissions.AppPermissionState
import com.sulfuro.salati.data.settings.TimeFormatPreference
import com.sulfuro.salati.ui.components.ExpandableSettingSection
import com.sulfuro.salati.ui.components.SegmentedTabRow
import androidx.core.app.LocaleManagerCompat
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


internal object LoadedSettingsCache {
    @Volatile
    var latest: CalculationSettings? = null
}

internal fun localeTagsForLanguageCode(langCode: String?): String {
    return langCode.orEmpty()
}

internal fun shouldUpdateApplicationLocales(currentTags: String, langCode: String?): Boolean {
    return currentTags != localeTagsForLanguageCode(langCode)
}

/**
 * Language tag the current Activity's resources were built with on API < 33, where ""
 * means "follow the system". Written by [wrapContextForLanguage] from
 * `MainActivity.attachBaseContext`, so it always describes the resources actually in
 * use rather than whatever the deprecated configuration override happened to leave
 * behind. [applyAppLanguage] compares against it, which is what keeps a language change
 * to exactly one recreate instead of a recreate loop.
 */
@Volatile
internal var attachedLanguageTag: String = ""

/**
 * Builds the context an Activity should run on for [langCode] (API < 33). Returns [base]
 * unchanged for "system default"; otherwise returns a configuration context whose
 * resources genuinely carry the chosen locale, which survives `recreate()` because
 * `attachBaseContext` re-applies it on every Activity instance.
 */
internal fun wrapContextForLanguage(
    base: android.content.Context,
    langCode: String?
): android.content.Context {
    attachedLanguageTag = localeTagsForLanguageCode(langCode)
    if (langCode.isNullOrEmpty()) {
        java.util.Locale.setDefault(
            android.content.res.Resources.getSystem().configuration.locales[0]
        )
        return base
    }
    val target = java.util.Locale.forLanguageTag(langCode)
    java.util.Locale.setDefault(target)
    val config = android.content.res.Configuration(base.resources.configuration)
    config.setLocale(target)
    config.setLayoutDirection(target)
    return base.createConfigurationContext(config)
}

internal fun applyAppLanguage(context: android.content.Context, langCode: String?) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        val localeManager = context.getSystemService(android.app.LocaleManager::class.java)
        if (localeManager != null) {
            val localeList = if (langCode.isNullOrEmpty()) {
                android.os.LocaleList.getEmptyLocaleList()
            } else {
                android.os.LocaleList.forLanguageTags(langCode)
            }
            if (shouldUpdateApplicationLocales(localeManager.applicationLocales.toLanguageTags(), langCode)) {
                localeManager.applicationLocales = localeList
            }
        }
    }
    if (localeTagsForLanguageCode(langCode) != attachedLanguageTag) {
        (context as? android.app.Activity)?.recreate()
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: CalculationSettings,
    preferences: SalatiPreferences,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val appContext = context.applicationContext

    // Permission state is read here and passed down, because two cards report on it and
    // the launcher's result has to land where it is observed.
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionState by remember { mutableStateOf(readAppPermissionState(context)) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        permissionState = readAppPermissionState(context)
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionState = readAppPermissionState(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Remembered, not rebuilt: this is passed to all four cards, and a fresh lambda on
    // every recomposition is a changed parameter, which makes them all recompose no
    // matter how stable everything else is.
    val saveSettings = remember(preferences, appContext, scope) {
        { transform: (CalculationSettings) -> CalculationSettings ->
            scope.launch {
                var settingsChange: Pair<CalculationSettings, CalculationSettings>? = null
                preferences.updateSettings { current ->
                    val updated = transform(current)
                    settingsChange = current to updated
                    updated
                }
                settingsChange?.let { (previous, updated) ->
                    enqueueAlarmSettingsRefreshIfNeeded(appContext, previous, updated)
                }
            }
            Unit
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = SalatiSpacing.md, end = SalatiSpacing.md, bottom = SalatiSpacing.md),
        verticalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        SettingsLocationCard(settings = settings, saveSettings = saveSettings)

        SettingsAlarmsCard(
            settings = settings,
            permissionState = permissionState,
            saveSettings = saveSettings
        )

        SettingsAppearanceCard(settings = settings, saveSettings = saveSettings)

        SettingsSystemCard(
            settings = settings,
            permissionState = permissionState,
            onRequestNotificationPermission = {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        )

        Spacer(modifier = Modifier.height(SalatiSpacing.xl))
    }
}
