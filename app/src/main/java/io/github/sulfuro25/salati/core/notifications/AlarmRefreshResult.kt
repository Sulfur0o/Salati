package io.github.sulfuro25.salati.core.notifications

sealed interface AlarmRefreshResult {
    data class Success(val scheduledCount: Int) : AlarmRefreshResult
    data class SuccessWithStaleAlarms(val scheduledCount: Int, val staleCount: Int) : AlarmRefreshResult
    data class Disabled(val warning: String? = null) : AlarmRefreshResult
    data class DisabledWithWarning(
        val warning: String,
        val retryRecommended: Boolean
    ) : AlarmRefreshResult
    data class Recovered(val restoredCount: Int) : AlarmRefreshResult
    data object CacheMiss : AlarmRefreshResult
    data class PartiallyRecovered(
        val restoredCount: Int,
        val failedCount: Int,
        val cause: Throwable
    ) : AlarmRefreshResult
    data class TemporaryFailure(val cause: Throwable) : AlarmRefreshResult
    data class PermanentFailure(val cause: Throwable) : AlarmRefreshResult
}

sealed interface AlarmPreparationResult {
    data class Success(val alarms: List<PreparedAlarm>) : AlarmPreparationResult
    data object Disabled : AlarmPreparationResult
    data object CacheMiss : AlarmPreparationResult
    data class TemporaryNetworkFailure(val cause: Throwable) : AlarmPreparationResult
    data class RetryableServerFailure(val statusCode: Int) : AlarmPreparationResult
    data class PermanentHttpFailure(val statusCode: Int) : AlarmPreparationResult
    data class InvalidConfiguration(val cause: Throwable) : AlarmPreparationResult
    data class InvalidCachedData(val cause: Throwable) : AlarmPreparationResult
    data class InvalidApiResponse(val cause: Throwable) : AlarmPreparationResult
}
