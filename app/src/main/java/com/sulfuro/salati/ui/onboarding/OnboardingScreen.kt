package com.sulfuro.salati.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.sulfuro.salati.data.settings.CalculationSettings
import com.sulfuro.salati.theme.SalatiSpacing
import kotlinx.serialization.json.Json

@Composable
fun OnboardingScreen(
    currentSettings: CalculationSettings,
    onComplete: (CalculationSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    // Saveable rather than merely remembered: a rotation, a theme switch or a language
    // change recreates the activity, and losing the step meant losing the location the
    // user had just waited for GPS to find.
    var step by rememberSaveable { mutableIntStateOf(1) }
    var draftSettings by rememberSaveable(stateSaver = OnboardingDraftSaver) {
        mutableStateOf(currentSettings)
    }
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
                    currentMethod = draftSettings.prayer.calculationMethod,
                    onMethodSelected = { method ->
                        draftSettings = draftSettings.copy(prayer = draftSettings.prayer.copy(calculationMethod = method))
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

private val OnboardingDraftJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * Settings are JSON everywhere else in the app, so the half-finished draft crosses an
 * activity restart the same way rather than earning a Parcelable of its own.
 */
private val OnboardingDraftSaver: Saver<CalculationSettings, String> = Saver(
    save = { OnboardingDraftJson.encodeToString(CalculationSettings.serializer(), it) },
    restore = { OnboardingDraftJson.decodeFromString(CalculationSettings.serializer(), it) }
)
