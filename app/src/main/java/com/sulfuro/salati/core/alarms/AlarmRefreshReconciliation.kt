package com.sulfuro.salati.core.alarms

internal fun enqueueOneReconciliationIfChanged(
    startedWith: AlarmRelevantSettingsFingerprint,
    current: AlarmRelevantSettingsFingerprint,
    enqueueFollowUp: () -> Unit
): Boolean {
    if (startedWith == current) return false
    enqueueFollowUp()
    return true
}
