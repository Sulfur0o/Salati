package com.sulfuro.salati.core.alarms

sealed interface AlarmRegistryReadResult {
    data class Valid(
        val alarms: List<RegisteredAlarm>
    ) : AlarmRegistryReadResult

    data class Corrupted(
        val rawValue: String,
        val cause: Throwable
    ) : AlarmRegistryReadResult
}
