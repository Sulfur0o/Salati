package com.sulfuro.salati.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.sulfuro.salati.R
import com.sulfuro.salati.theme.SalatiShapeTokens
import com.sulfuro.salati.theme.SalatiSpacing

/** Which authority's angles to calculate by, defaulted rather than demanded. */
@Composable
internal fun CalculationMethodStep(
    currentMethod: String,
    onMethodSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    var selectedMethod by remember { mutableStateOf(currentMethod) }

    val methods = listOf(
        "MUSLIM_WORLD_LEAGUE" to stringResource(R.string.settings_method_mwl),
        "UMM_AL_QURA" to stringResource(R.string.settings_method_umm_al_qura),
        "ISNA" to stringResource(R.string.settings_method_isna),
        "EGYPT" to stringResource(R.string.settings_method_egypt),
        "KARACHI" to stringResource(R.string.settings_method_karachi),
        "DUBAI" to stringResource(R.string.settings_method_dubai),
        "KUWAIT" to stringResource(R.string.settings_method_kuwait),
        "QATAR" to stringResource(R.string.settings_method_qatar),
        "MOON_SIGHTING" to stringResource(R.string.settings_method_moon_sighting)
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)
        ) {
            Text(
                text = stringResource(R.string.onboarding_method_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = stringResource(R.string.onboarding_method_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(SalatiSpacing.xs))

            methods.forEach { (id, name) ->
                val isSelected = selectedMethod == id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedMethod = id },
                    shape = SalatiShapeTokens.Control,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = SalatiSpacing.md, vertical = SalatiSpacing.sm)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedMethod = id }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = SalatiSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onBack, shape = SalatiShapeTokens.Control) {
                Text(text = stringResource(R.string.onboarding_btn_back))
            }
            Button(
                onClick = { onMethodSelected(selectedMethod) },
                shape = SalatiShapeTokens.Control
            ) {
                Text(text = stringResource(R.string.onboarding_btn_continue))
            }
        }
    }
}
