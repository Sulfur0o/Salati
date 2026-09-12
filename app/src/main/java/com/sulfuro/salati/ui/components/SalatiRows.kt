package com.sulfuro.salati.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sulfuro.salati.R
import com.sulfuro.salati.theme.SalatiShapeTokens
import com.sulfuro.salati.theme.SalatiSpacing
import com.sulfuro.salati.theme.SalatiTypeTokens

@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Surface(
        modifier = modifier,
        shape = SalatiShapeTokens.Pill,
        color = containerColor,
        contentColor = contentColor
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            modifier = Modifier.padding(
                horizontal = SalatiSpacing.sm,
                vertical = SalatiSpacing.xxs
            )
        )
    }
}

@Composable
fun PrayerTimeRow(
    name: String,
    time: String,
    isCurrent: Boolean,
    isDisplayOnly: Boolean,
    semanticState: String,
    modifier: Modifier = Modifier
) {
    val containerColor = when {
        isCurrent -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
    val contentColor = when {
        isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
        isDisplayOnly -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderColor = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 42.dp)
            .semantics(mergeDescendants = true) {
                stateDescription = semanticState
            },
        color = containerColor,
        contentColor = contentColor,
        shape = SalatiShapeTokens.Control,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SalatiSpacing.md, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)
            ) {
                if (isCurrent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier
                                .height(16.dp)
                                .width(3.5.dp),
                            shape = androidx.compose.foundation.shape.CircleShape
                        ) {}
                        Spacer(modifier = Modifier.width(5.dp))
                    }
                }
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (isCurrent) {
                    Surface(
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text(
                            text = stringResource(R.string.badge_next),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                        )
                    }
                }
            }
            Text(
                text = time,
                style = SalatiTypeTokens.PrayerTime
            )
        }
    }
}




@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SalatiSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(top = SalatiSpacing.xs, bottom = SalatiSpacing.xs, end = SalatiSpacing.sm), verticalArrangement = Arrangement.Center) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

@Composable
fun ValueSelectionRow(
    title: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp),
        color = Color.Transparent,
        onClick = { onExpandedChange(!expanded) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = SalatiSpacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1.2f).padding(top = SalatiSpacing.xs, bottom = SalatiSpacing.xs, end = SalatiSpacing.xs),
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.weight(0.8f, fill = false),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
                Spacer(modifier = Modifier.width(SalatiSpacing.xs))
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * One permission: what its state is, and - only when something needs doing about it - the
 * button that does it.
 *
 * These used to be chips that were themselves the control, which meant the only thing that
 * could be tapped looked exactly like a label. Nothing said "Granted" was inert and
 * "Denied" was actionable, so the row that needed attention was the one that looked least
 * like a button. Now the state is a pill and the action is a button beside it, and when a
 * permission is granted the button is absent rather than disabled: nothing to tap is
 * nothing to wonder about.
 *
 * Shared by Settings and onboarding. They had drifted into two different shapes - pill
 * right with the action on its own line below, versus pill left with the action beside it
 * - which meant the first permission screen a new user ever sees was the one that did not
 * match the rest of the app.
 *
 * @param description why the permission is wanted. Shown only while it is missing, and
 *   only where there is room to explain: onboarding passes it, Settings does not.
 */
@Composable
fun PermissionStatusRow(
    label: String,
    statusText: String,
    isAllowed: Boolean,
    actionText: String,
    actionDescription: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SalatiSpacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusPill(
                text = "$label · $statusText",
                containerColor = if (isAllowed) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.errorContainer
                },
                contentColor = if (isAllowed) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
                // Shrinks rather than pushing the button off the row when a translation
                // runs long, which Arabic and Dutch both do here.
                modifier = Modifier.weight(1f, fill = false)
            )

            if (!isAllowed) {
                Spacer(modifier = Modifier.width(SalatiSpacing.sm))
                FilledTonalButton(
                    onClick = onActionClick,
                    // Green, not the default tonal brass: the pill states the problem in
                    // red and the button is the way out of it, so it wears the colour of
                    // the state the user is heading towards.
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    // "Allow" on its own tells a screen reader nothing about which
                    // permission.
                    modifier = Modifier.semantics { contentDescription = actionDescription }
                ) {
                    Text(text = actionText)
                }
            }
        }

        if (!isAllowed && !description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A quantity on a small ordered scale: minutes before a prayer, days of Hijri offset.
 *
 * These were sliders, then - when the screen was cut to one row shape - lists in a modal
 * sheet, which meant a full-height sheet and two taps to move a number from 10 to 15. A
 * list is the right control for a choice between named things; this is not that. The row
 * keeps the anatomy of its neighbours, label on the left and the current value on the
 * right, and gains a step either side of the value.
 *
 * [onDecrease] and [onIncrease] walk the caller's list of allowed values rather than doing
 * arithmetic, so a scale with an uneven jump in it steps correctly. The buttons carry
 * their own spoken labels because a bare minus sign tells a screen reader nothing about
 * what it would decrease.
 */
@Composable
fun SettingStepperRow(
    title: String,
    value: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .padding(start = SalatiSpacing.md, end = SalatiSpacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(top = SalatiSpacing.xs, bottom = SalatiSpacing.xs, end = SalatiSpacing.xs),
            maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            val decreaseLabel = stringResource(R.string.settings_stepper_decrease, title)
            IconButton(
                onClick = onDecrease,
                enabled = canDecrease,
                modifier = Modifier.semantics { contentDescription = decreaseLabel }
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = null,
                    tint = if (canDecrease) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    }
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                // Wide enough that the row does not shuffle sideways as the value grows
                // from "Off" to "30 minutes before".
                modifier = Modifier.defaultMinSize(minWidth = 116.dp)
            )
            val increaseLabel = stringResource(R.string.settings_stepper_increase, title)
            IconButton(
                onClick = onIncrease,
                enabled = canIncrease,
                modifier = Modifier.semantics { contentDescription = increaseLabel }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = if (canIncrease) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    }
                )
            }
        }
    }
}
