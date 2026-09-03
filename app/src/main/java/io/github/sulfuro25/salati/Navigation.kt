package io.github.sulfuro25.salati

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import io.github.sulfuro25.salati.data.settings.CalculationSettings
import io.github.sulfuro25.salati.data.settings.SalatiPreferences
import kotlinx.coroutines.launch
import io.github.sulfuro25.salati.ui.calendar.CalendarScreen
import io.github.sulfuro25.salati.ui.dashboard.DashboardScreen
import io.github.sulfuro25.salati.ui.qibla.QiblaScreen
import io.github.sulfuro25.salati.ui.settings.SettingsScreen
import io.github.sulfuro25.salati.ui.zakat.ZakatScreen

@Composable
fun MainNavigation(
    settings: CalculationSettings,
    preferences: SalatiPreferences
) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext

    if (!settings.hasCompletedOnboarding) {
        io.github.sulfuro25.salati.ui.onboarding.OnboardingScreen(
            currentSettings = settings,
            onComplete = { updated ->
                scope.launch {
                    preferences.updateSettings { updated.copy(hasCompletedOnboarding = true) }
                    io.github.sulfuro25.salati.core.notifications.AlarmWorkScheduler.enqueueSettingsRefreshDebounced(appContext)
                    io.github.sulfuro25.salati.widget.SalatiAppWidgetProvider.updateAllWidgets(appContext)
                }
            }
        )
        return
    }

    val backStack = rememberNavBackStack(Dashboard as NavKey)
    val currentKey = backStack.lastOrNull() ?: Dashboard

    // Tabs are a flat, depth-1 stack. Qibla is pushed on top of Daily, so switching tabs has
    // to collapse back to a single entry rather than assuming exactly one is present.
    // The target is added before trimming so the stack is never momentarily empty.
    val switchTab: (NavKey) -> Unit = { target ->
        if (currentKey != target) {
            backStack.add(target)
            while (backStack.size > 1) {
                backStack.removeAt(0)
            }
        }
    }

    // Hoisted here (rather than inside ZakatScreen) because tab switches remove and
    // re-add nav entries, tearing down and recreating the screen's own remembered state.
    val zakatSelectedStandard = androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(0)
    }
    val zakatCashInput = androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf("")
    }
    val zakatGoldWeightInput = androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf("")
    }
    val zakatSilverWeightInput = androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf("")
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                NavigationBar(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0),
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .height(64.dp)
                ) {
                    val navColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )

                    NavigationBarItem(
                        selected = currentKey == Dashboard || currentKey == Qibla,
                        onClick = { switchTab(Dashboard) },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text(androidx.compose.ui.res.stringResource(R.string.nav_daily)) },
                        alwaysShowLabel = true,
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = currentKey == Calendar,
                        onClick = { switchTab(Calendar) },
                        icon = { Icon(Icons.Default.Event, contentDescription = null) },
                        label = { Text(androidx.compose.ui.res.stringResource(R.string.nav_monthly)) },
                        alwaysShowLabel = true,
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = currentKey == Zakat,
                        onClick = { switchTab(Zakat) },
                        icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                        label = { Text(androidx.compose.ui.res.stringResource(R.string.nav_zakat)) },
                        alwaysShowLabel = true,
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = currentKey == Settings,
                        onClick = { switchTab(Settings) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text(androidx.compose.ui.res.stringResource(R.string.nav_settings)) },
                        alwaysShowLabel = true,
                        colors = navColors
                    )
                }
            }
        }
    ) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            onBack = { 
                backStack.removeLastOrNull() 
            },
            entryProvider = entryProvider {
                entry<Dashboard> {
                    DashboardScreen(
                        settings = settings,
                        onOpenQibla = { backStack.add(Qibla) },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
                entry<Qibla> {
                    QiblaScreen(
                        settings = settings,
                        onBack = { backStack.removeLastOrNull() },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
                entry<Calendar> {
                    CalendarScreen(
                        settings = settings,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
                entry<Zakat> {
                    ZakatScreen(
                        settings = settings,
                        preferences = preferences,
                        selectedStandardState = zakatSelectedStandard,
                        cashState = zakatCashInput,
                        goldWeightState = zakatGoldWeightInput,
                        silverWeightState = zakatSilverWeightInput,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
                entry<Settings> {
                    SettingsScreen(
                        settings = settings,
                        preferences = preferences,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        )
    }
}
