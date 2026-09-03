package io.github.sulfuro25.salati

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

class MainActivity : ComponentActivity() {
    private lateinit var permissionRefreshController: PermissionStateRefreshController
    private lateinit var preferences: SalatiPreferences


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        preferences = SalatiPreferences(applicationContext)
        PrayerNotificationChannels.create(applicationContext)
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
            if (loadedSettings == null) {
                SalatiTheme(darkTheme = isSystemInDarkTheme()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {}
                }
            } else {
                LaunchedEffect(loadedSettings.appLanguageCode) {
                    applyAppLanguage(this@MainActivity, loadedSettings.appLanguageCode)
                }
                val darkTheme = loadedSettings.isDarkMode ?: isSystemInDarkTheme()
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
