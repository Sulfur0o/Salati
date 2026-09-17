package com.sulfuro.salati.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sulfuro.salati.R
import com.sulfuro.salati.core.permissions.AppPermissionState
import com.sulfuro.salati.core.permissions.readAppPermissionState
import com.sulfuro.salati.core.permissions.rememberNotificationPermissionRequest
import com.sulfuro.salati.core.work.enqueueAlarmSettingsRefreshIfNeeded
import com.sulfuro.salati.data.settings.CalculationSettings
import com.sulfuro.salati.data.settings.SalatiPreferences
import com.sulfuro.salati.theme.SalatiSpacing
import com.sulfuro.salati.widget.SalatiAppWidgetProvider
import java.util.Locale
import kotlinx.coroutines.launch


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
        Locale.setDefault(
            android.content.res.Resources.getSystem().configuration.locales[0]
        )
        return base
    }
    val target = Locale.forLanguageTag(langCode)
    Locale.setDefault(target)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    // Falls through to system settings once Android stops showing the dialog, so the
    // button cannot become a no-op.
    val requestNotificationPermission = rememberNotificationPermissionRequest {
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
                    // Widgets otherwise only redrew every half hour or when a prayer
                    // fired, so changing city, method or clock format left every one of
                    // them showing the old times - and muting notifications, which stops
                    // the alarms, took the prayer-time refresh away with it.
                    if (previous != updated) {
                        SalatiAppWidgetProvider.updateAllWidgets(appContext)
                    }
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

        SettingsAlarmsCard(settings = settings, saveSettings = saveSettings)

        SettingsDuringPrayerCard(
            settings = settings,
            permissionState = permissionState,
            saveSettings = saveSettings
        )

        SettingsAppearanceCard(settings = settings, saveSettings = saveSettings)

        SettingsSystemCard(
            settings = settings,
            permissionState = permissionState,
            onRequestNotificationPermission = requestNotificationPermission
        )

        Spacer(modifier = Modifier.height(SalatiSpacing.xl))
    }
}
