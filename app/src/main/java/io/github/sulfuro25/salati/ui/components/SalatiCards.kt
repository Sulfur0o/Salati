package io.github.sulfuro25.salati.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import io.github.sulfuro25.salati.theme.SalatiShapeTokens
import io.github.sulfuro25.salati.theme.SalatiSpacing
import io.github.sulfuro25.salati.theme.SalatiTypeTokens
import kotlinx.coroutines.delay
import java.time.Instant

@Composable
fun SalatiHeroCard(
    eventLabel: String,
    eventName: String,
    eventTime: String,
    countdownTarget: Instant,
    countdownFormatter: (Long) -> String,
    accessibilityDescription: String,
    displayOnlyLabel: String?,
    modifier: Modifier = Modifier
) {
    var remainingMillis by remember(countdownTarget) {
        mutableLongStateOf((countdownTarget.toEpochMilli() - System.currentTimeMillis()).coerceAtLeast(0L))
    }
    LaunchedEffect(countdownTarget) {
        while (true) {
            val now = System.currentTimeMillis()
            remainingMillis = (countdownTarget.toEpochMilli() - now).coerceAtLeast(0L)
            delay((1_000L - now % 1_000L).coerceAtLeast(1L))
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = accessibilityDescription
            },
        shape = SalatiShapeTokens.Card,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SalatiSpacing.md, horizontal = SalatiSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SalatiSpacing.xs)
        ) {
            Text(
                text = countdownFormatter(remainingMillis),
                style = SalatiTypeTokens.Countdown,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$eventLabel: $eventName",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = eventTime,
                    style = SalatiTypeTokens.PrayerTime,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            displayOnlyLabel?.let {
                StatusPill(
                    text = it,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

@Composable
fun SalatiSectionCard(
    title: String,
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit
) {
    SalatiSectionCard(modifier = modifier, containerColor = containerColor) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() }
        )
        content()
    }
}

@Composable
fun SalatiSectionCard(
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Transparent,
    bordered: Boolean = false,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = if (bordered) SalatiShapeTokens.Card else androidx.compose.ui.graphics.RectangleShape,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onBackground,
        border = if (bordered) BorderStroke(1.dp, MaterialTheme.colorScheme.outline) else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SalatiSpacing.md),
            verticalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)
        ) {
            content()
        }
    }
}

@Composable
fun SalatiHeroCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = androidx.compose.ui.graphics.Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) {
        content()
    }
}

@Composable
fun SettingSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = SalatiSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(SalatiSpacing.xs)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(horizontal = SalatiSpacing.md)
                .semantics { heading() }
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = SalatiShapeTokens.Card,
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                content()
            }
        }
    }
}
