package io.github.sulfuro25.salati.core.notifications

import android.content.Context
import io.github.sulfuro25.salati.data.settings.CalculationSettings

fun enqueueAlarmSettingsRefreshIfNeeded(
    context: Context,
    previous: CalculationSettings,
    updated: CalculationSettings
): AlarmSettingsRefreshTrigger {
    val trigger = getAlarmSettingsRefreshTrigger(previous, updated)
    when (trigger) {
        AlarmSettingsRefreshTrigger.NONE -> Unit
        AlarmSettingsRefreshTrigger.IMMEDIATE -> AlarmWorkScheduler.enqueueRefresh(context)
        AlarmSettingsRefreshTrigger.DEBOUNCED -> {
            AlarmWorkScheduler.enqueueSettingsRefreshDebounced(context)
        }
    }
    return trigger
}
