package io.github.sulfuro25.salati.core.notifications

internal fun enqueueOneReconciliationIfChanged(
    startedWith: AlarmRelevantSettingsFingerprint,
    current: AlarmRelevantSettingsFingerprint,
    enqueueFollowUp: () -> Unit
): Boolean {
    if (startedWith == current) return false
    enqueueFollowUp()
    return true
}
