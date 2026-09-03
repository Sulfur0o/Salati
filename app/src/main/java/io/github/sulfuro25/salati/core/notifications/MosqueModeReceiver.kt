package io.github.sulfuro25.salati.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MosqueModeReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_ENTER_SILENT_MODE =
            "io.github.sulfuro25.salati.ACTION_ENTER_SILENT_MODE"
        const val ACTION_RESTORE_RINGER_MODE =
            "io.github.sulfuro25.salati.ACTION_RESTORE_RINGER_MODE"
        const val ACTION_RECONCILE_SILENT_MODE =
            "io.github.sulfuro25.salati.ACTION_RECONCILE_SILENT_MODE"
        const val EXTRA_PRAYER_REQUEST_CODE = "EXTRA_PRAYER_REQUEST_CODE"
        const val EXTRA_SESSION_END_MILLIS = "EXTRA_SESSION_END_MILLIS"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_ENTER_SILENT_MODE -> {
                        val sessionEnd = intent.getLongExtra(EXTRA_SESSION_END_MILLIS, 0L)
                        PrayerSilentModeController.enterSilentMode(appContext, sessionEnd)
                    }
                    ACTION_RESTORE_RINGER_MODE -> {
                        val sessionEnd = intent.getLongExtra(EXTRA_SESSION_END_MILLIS, 0L)
                        when (
                            val result = PrayerSilentModeController.restoreIfDue(
                                appContext,
                                sessionEnd
                            )
                        ) {
                            is SilentModeRestoreResult.NotDue -> {
                                val prayerRequestCode = intent.getIntExtra(
                                    EXTRA_PRAYER_REQUEST_CODE,
                                    result.activeUntilMillis.hashCode()
                                )
                                PrayerSilentModeScheduler.rescheduleRestore(
                                    appContext,
                                    prayerRequestCode,
                                    result.activeUntilMillis
                                )
                            }
                            else -> Unit
                        }
                    }
                    ACTION_RECONCILE_SILENT_MODE -> {
                        PrayerSilentModeScheduler.reconcileActiveSession(appContext)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
