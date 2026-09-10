package com.sulfuro.salati.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sulfuro.salati.R
import com.sulfuro.salati.core.location.DeviceLocationProvider
import com.sulfuro.salati.core.location.DeviceLocationResult
import com.sulfuro.salati.core.location.PrayerLocationResolver
import com.sulfuro.salati.ui.settings.CitySearchSheet
import com.sulfuro.salati.core.notifications.readAppPermissionState
import com.sulfuro.salati.data.settings.CalculationSettings
import com.sulfuro.salati.theme.SalatiShapeTokens
import com.sulfuro.salati.theme.SalatiSpacing
import com.sulfuro.salati.ui.components.SalatiLogo
import com.sulfuro.salati.ui.components.PermissionStatusRow
import com.sulfuro.salati.ui.components.SalatiSectionCard
import com.sulfuro.salati.ui.settings.BatteryOptimizationHelpDialog
import kotlinx.coroutines.launch

import androidx.compose.foundation.layout.safeDrawingPadding

@Composable
fun OnboardingScreen(
    currentSettings: CalculationSettings,
    onComplete: (CalculationSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableIntStateOf(1) }
    var draftSettings by remember { mutableStateOf(currentSettings) }
    val totalSteps = 4

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(SalatiSpacing.md)
    ) {
        // Step Indicator Progress Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SalatiSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 1..totalSteps) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (i <= step) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(SalatiSpacing.sm))

        // Step Content with Animation
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier.weight(1f),
            label = "onboarding_step_content"
        ) { currentStep ->
            when (currentStep) {
                1 -> WelcomeStep(
                    onContinue = { step = 2 }
                )
                2 -> LocationStep(
                    currentSettings = draftSettings,
                    onLocationSelected = { updated ->
                        draftSettings = updated
                        step = 3
                    },
                    onBack = { step = 1 }
                )
                3 -> CalculationMethodStep(
                    currentMethod = draftSettings.calculationMethod,
                    onMethodSelected = { method ->
                        draftSettings = draftSettings.copy(calculationMethod = method)
                        step = 4
                    },
                    onBack = { step = 2 }
                )
                4 -> NotificationsStep(
                    draftSettings = draftSettings,
                    onSettingsChanged = { draftSettings = it },
                    onFinish = {
                        onComplete(draftSettings.copy(hasCompletedOnboarding = true))
                    },
                    onBack = { step = 3 }
                )
            }
        }
    }
}
