package io.github.sulfuro25.salati.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.sulfuro25.salati.theme.SalatiSpacing

@Composable
fun SalatiLoadingState(
    label: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(SalatiSpacing.md)
        ) {
            CircularProgressIndicator()
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
fun SalatiErrorState(
    title: String,
    message: String,
    retryLabel: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        SalatiSectionCard(
            title = title,
            modifier = Modifier.padding(SalatiSpacing.lg),
            containerColor = MaterialTheme.colorScheme.errorContainer
        ) {
            Text(
                text = message,
                textAlign = TextAlign.Start,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Button(
                onClick = onRetry,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp)
            ) {
                Text(retryLabel)
            }
        }
    }
}

/**
 * Tells the user, quietly, that the times on screen were worked out on the device
 * because the prayer service could not be reached.
 *
 * Prayer times carry a duty of honesty that most app data does not: someone deciding
 * when to pray deserves to know the answer is Salati's own arithmetic rather than the
 * service it normally defers to. It is a one-line note rather than a banner because the
 * times are accurate to the minute, so alarming the reader would be its own kind of
 * dishonesty.
 */
@Composable
fun SalatiComputedLocallyNotice(
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = SalatiSpacing.sm, vertical = SalatiSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
