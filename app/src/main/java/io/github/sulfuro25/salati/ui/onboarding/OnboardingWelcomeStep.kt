package io.github.sulfuro25.salati.ui.onboarding

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.sulfuro25.salati.R
import io.github.sulfuro25.salati.theme.SalatiShapeTokens
import io.github.sulfuro25.salati.theme.SalatiSpacing
import io.github.sulfuro25.salati.ui.components.SalatiLogo

/** The first thing anyone sees of Salati: the mark, and what the app is for. */
@Composable
internal fun WelcomeStep(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(SalatiSpacing.md)
        ) {
            Spacer(modifier = Modifier.height(SalatiSpacing.md))

            SalatiLogo(
                contentDescription = stringResource(R.string.app_logo_description),
                size = 96.dp
            )

            Text(
                text = stringResource(R.string.onboarding_welcome_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = stringResource(R.string.onboarding_welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(SalatiSpacing.sm))

            FeatureOverviewCard(
                icon = Icons.Default.AccessTime,
                title = stringResource(R.string.onboarding_feat_prayers_title),
                description = stringResource(R.string.onboarding_feat_prayers_desc)
            )

            FeatureOverviewCard(
                icon = Icons.Default.Explore,
                title = stringResource(R.string.onboarding_feat_qibla_title),
                description = stringResource(R.string.onboarding_feat_qibla_desc)
            )

            FeatureOverviewCard(
                icon = Icons.Default.CalendarMonth,
                title = stringResource(R.string.onboarding_feat_calendar_title),
                description = stringResource(R.string.onboarding_feat_calendar_desc)
            )

            FeatureOverviewCard(
                icon = Icons.Default.VolunteerActivism,
                title = stringResource(R.string.onboarding_feat_zakat_title),
                description = stringResource(R.string.onboarding_feat_zakat_desc)
            )
        }

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SalatiSpacing.md),
            shape = SalatiShapeTokens.Control
        ) {
            Text(
                text = stringResource(R.string.onboarding_btn_get_started),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FeatureOverviewCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = SalatiShapeTokens.Control,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier.padding(SalatiSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
