package io.github.sulfuro25.salati.core.notifications

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class PrayerSilentModeControllerTest {
    private lateinit var context: Context
    private lateinit var audioManager: AudioManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("prayer_silent_mode", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        Shadows.shadowOf(notificationManager).setNotificationPolicyAccessGranted(true)
        PrayerSilentModeScheduler.setAutomationEnabled(context, true)
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
    }

    @Test
    fun enteringAndEndingWindowRestoresPreviousRingerMode() {
        val end = System.currentTimeMillis() + 20 * 60_000L

        assertTrue(PrayerSilentModeController.enterSilentMode(context, end))
        assertEquals(AudioManager.RINGER_MODE_SILENT, audioManager.ringerMode)
        assertTrue(
            PrayerSilentModeController.restoreIfDue(context, end, end - 1L)
                is SilentModeRestoreResult.NotDue
        )
        assertEquals(AudioManager.RINGER_MODE_SILENT, audioManager.ringerMode)

        assertEquals(
            SilentModeRestoreResult.Restored,
            PrayerSilentModeController.restoreIfDue(context, end, end)
        )
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, audioManager.ringerMode)
    }

    @Test
    fun overlappingWindowExtendsSessionWithoutOverwritingOriginalMode() {
        val firstEnd = System.currentTimeMillis() + 15 * 60_000L
        val secondEnd = firstEnd + 10 * 60_000L

        assertTrue(PrayerSilentModeController.enterSilentMode(context, firstEnd))
        assertTrue(PrayerSilentModeController.enterSilentMode(context, secondEnd))
        assertTrue(
            PrayerSilentModeController.restoreIfDue(context, firstEnd, firstEnd)
                is SilentModeRestoreResult.NotDue
        )

        assertEquals(
            SilentModeRestoreResult.Restored,
            PrayerSilentModeController.restoreIfDue(context, secondEnd, secondEnd)
        )
        assertEquals(AudioManager.RINGER_MODE_VIBRATE, audioManager.ringerMode)
    }

    @Test
    fun restoreDoesNotOverrideManualRingerChangeDuringPrayer() {
        val end = System.currentTimeMillis() + 15 * 60_000L
        assertTrue(PrayerSilentModeController.enterSilentMode(context, end))
        audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL

        assertEquals(
            SilentModeRestoreResult.Restored,
            PrayerSilentModeController.restoreIfDue(context, end, end)
        )
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audioManager.ringerMode)
    }
}
