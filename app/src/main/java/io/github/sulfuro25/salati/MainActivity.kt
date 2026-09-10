package io.github.sulfuro25.salati

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import io.github.sulfuro25.salati.core.notifications.AlarmWorkScheduler
import io.github.sulfuro25.salati.core.notifications.PermissionStateRefreshController
import io.github.sulfuro25.salati.core.notifications.PrayerNotificationChannels
import io.github.sulfuro25.salati.core.notifications.PrayerSilentModeScheduler
import io.github.sulfuro25.salati.core.notifications.readAppPermissionState
import io.github.sulfuro25.salati.data.settings.CalculationSettings
import io.github.sulfuro25.salati.data.settings.SalatiPreferences
import io.github.sulfuro25.salati.theme.SalatiTheme
import io.github.sulfuro25.salati.ui.settings.LoadedSettingsCache
import io.github.sulfuro25.salati.ui.settings.applyAppLanguage
import io.github.sulfuro25.salati.core.audio.withoutRetiredAdhanChoices
import io.github.sulfuro25.salati.ui.settings.wrapContextForLanguage

class MainActivity : ComponentActivity() {
    private lateinit var permissionRefreshController: PermissionStateRefreshController
    private lateinit var preferences: SalatiPreferences

    // Below API 33 there is no platform per-app locale, so the chosen language has to be
    // baked into the Activity's own resources here. LoadedSettingsCache is written before
    // the language sheet asks for a recreate, so the rebuilt Activity picks up the new
    // choice on its first pass.
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(
            wrapContextForLanguage(newBase, LoadedSettingsCache.latest?.appLanguageCode)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = SalatiPreferences(applicationContext)
        // Deliberately this Activity and not applicationContext: below API 33 only the
        // Activity carries the app's chosen language, and the channel labels are what the
        // user reads in Android's notification settings. Re-running on every recreate is
        // also what re-labels them after a language change.
        PrayerNotificationChannels.create(this)
        permissionRefreshController = PermissionStateRefreshController(
            initialState = readAppPermissionState(this),
            enqueueRefresh = { AlarmWorkScheduler.enqueueRefresh(applicationContext) }
        )
        enableEdgeToEdge()

        triggerInitialScheduling()

        setContent {
            val settings by produceState(initialValue = LoadedSettingsCache.latest) {
                preferences.settings.collect { loaded ->
                    LoadedSettingsCache.latest = loaded
                    value = loaded
                }
            }
            val loadedSettings = settings
            val darkTheme = loadedSettings?.isDarkMode ?: isSystemInDarkTheme()

            DisposableEffect(darkTheme) {
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !darkTheme
                insetsController.isAppearanceLightNavigationBars = !darkTheme
                onDispose {}
            }

            if (loadedSettings == null) {
                SalatiTheme(darkTheme = darkTheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {}
                }
            } else {
                LaunchedEffect(loadedSettings.adhanSoundId, loadedSettings.fajrAdhanSoundId) {
                    io.github.sulfuro25.salati.core.audio.AdhanAudioStore.deleteRetired(applicationContext)
                    val cleaned = loadedSettings.withoutRetiredAdhanChoices()
                    if (cleaned != loadedSettings) {
                        preferences.updateSettings { cleaned }
                    }
                }
                LaunchedEffect(loadedSettings.appLanguageCode) {
                    if (loadedSettings.appLanguageCode != null) {
                        applyAppLanguage(this@MainActivity, loadedSettings.appLanguageCode)
                    }
                }
                SalatiTheme(darkTheme = darkTheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        MainNavigation(
                            settings = loadedSettings,
                            preferences = preferences
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::permissionRefreshController.isInitialized) {
            permissionRefreshController.onActivityResume(readAppPermissionState(this))
            PrayerSilentModeScheduler.requestActiveSessionReconciliation(applicationContext)
        }
    }

    private fun triggerInitialScheduling() {
        AlarmWorkScheduler.registerPeriodicMaintenance(applicationContext)
        AlarmWorkScheduler.enqueueRefresh(applicationContext)
    }
}
