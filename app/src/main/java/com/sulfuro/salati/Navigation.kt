package com.sulfuro.salati

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.sulfuro.salati.core.work.AlarmWorkScheduler
import com.sulfuro.salati.data.settings.CalculationSettings
import com.sulfuro.salati.data.settings.SalatiPreferences
import com.sulfuro.salati.ui.calendar.CalendarScreen
import com.sulfuro.salati.ui.components.AdhanPlayingBanner
import com.sulfuro.salati.ui.dashboard.DashboardScreen
import com.sulfuro.salati.ui.onboarding.OnboardingScreen
import com.sulfuro.salati.ui.qibla.QiblaScreen
import com.sulfuro.salati.ui.settings.SettingsScreen
import com.sulfuro.salati.ui.zakat.ZakatScreen
import com.sulfuro.salati.widget.SalatiAppWidgetProvider
import kotlinx.coroutines.launch

@Composable
fun MainNavigation(
    settings: CalculationSettings,
    preferences: SalatiPreferences
) {
    val scope = rememberCoroutineScope()

    val appContext = LocalContext.current.applicationContext

    if (!settings.hasCompletedOnboarding) {
        OnboardingScreen(
            currentSettings = settings,
            onComplete = { updated ->
                scope.launch {
                    preferences.updateSettings { updated.copy(hasCompletedOnboarding = true) }
                    AlarmWorkScheduler.enqueueSettingsRefreshDebounced(appContext)
                    SalatiAppWidgetProvider.updateAllWidgets(appContext)
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

    BackHandler(enabled = backStack.size == 1 && currentKey != Dashboard) {
        switchTab(Dashboard)
    }

    // Hoisted here (rather than inside ZakatScreen) because tab switches remove and
    // re-add nav entries, tearing down and recreating the screen's own remembered state.
    // Only the wizard position needs hoisting now; every answer lives in preferences.
    val zakatStep = rememberSaveable {
        mutableIntStateOf(0)
    }

    // Keeps each tab's saved state - scroll offsets above all - across switches. The back
    // stack is trimmed to one entry so that tabs stay a flat, depth-1 stack, which means
    // the screen being left is discarded outright; without this, coming back from Settings
    // dropped the user at the top of a calendar they had scrolled halfway down.
    val tabState = rememberSaveableStateHolder()

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        // Nothing at all unless an adhan is actually playing, in which case it is the
        // first thing on screen and it can stop it. Opening the app is what people do
        // when a recitation starts somewhere they cannot let it run.
        topBar = { AdhanPlayingBanner() },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets(0),
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        // 64dp is the compact height this bar was designed at, but it is a
                        // fixed height holding text that is not: at font scale 2 the longer
                        // labels wrapped and were cut off by the bar's own edge. It scales
                        // with the type now, capped so a very large setting cannot eat the
                        // screen.
                        .height(64.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.6f))
                ) {
                    // A quarter of the screen is all a label gets, and the longest of them
                    // stops fitting on one line somewhere past 1.6x - at 2x Compose broke it
                    // mid-word. The label still grows with the user's type size, it just
                    // stops growing at the point it would no longer fit beside its peers.
                    // Only an upper bound. Clamping the lower end too stopped the label
                    // shrinking for anyone who had chosen a font size below the default,
                    // which is the opposite of respecting the setting.
                    val labelScale = LocalDensity.current.fontScale.coerceAtMost(1.6f)
                    val navLabelStyle = MaterialTheme.typography.labelMedium.copy(
                        fontSize = with(LocalDensity.current) { (12.dp * labelScale).toSp() }
                    )

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
                        label = {
                            Text(
                                text = stringResource(R.string.nav_daily),
                                style = navLabelStyle,
                                maxLines = 1
                            )
                        },
                        alwaysShowLabel = true,
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = currentKey == Calendar,
                        onClick = { switchTab(Calendar) },
                        icon = { Icon(Icons.Default.Event, contentDescription = null) },
                        label = {
                            Text(
                                text = stringResource(R.string.nav_monthly),
                                style = navLabelStyle,
                                maxLines = 1
                            )
                        },
                        alwaysShowLabel = true,
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = currentKey == Zakat,
                        onClick = { switchTab(Zakat) },
                        icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                        label = {
                            Text(
                                text = stringResource(R.string.nav_zakat),
                                style = navLabelStyle,
                                maxLines = 1
                            )
                        },
                        alwaysShowLabel = true,
                        colors = navColors
                    )
                    NavigationBarItem(
                        selected = currentKey == Settings,
                        onClick = { switchTab(Settings) },
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = {
                            Text(
                                text = stringResource(R.string.nav_settings),
                                style = navLabelStyle,
                                maxLines = 1
                            )
                        },
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
                    tabState.SaveableStateProvider("tab_dashboard") {
                        DashboardScreen(
                            settings = settings,
                            onOpenQibla = { backStack.add(Qibla) },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    }
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
                    tabState.SaveableStateProvider("tab_calendar") {
                        CalendarScreen(
                            settings = settings,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    }
                }
                entry<Zakat> {
                    tabState.SaveableStateProvider("tab_zakat") {
                        ZakatScreen(
                            settings = settings,
                            preferences = preferences,
                            stepState = zakatStep,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    }
                }
                entry<Settings> {
                    tabState.SaveableStateProvider("tab_settings") {
                        SettingsScreen(
                            settings = settings,
                            preferences = preferences,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    }
                }
            }
        )
    }
}
