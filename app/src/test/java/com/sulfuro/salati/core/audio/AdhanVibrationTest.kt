package com.sulfuro.salati.core.audio

import com.sulfuro.salati.core.alerts.PrayerAlertNotification
import com.sulfuro.salati.core.alerts.PrayerNotificationChannels
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Taking over the alert means taking over all of it.
 *
 * A prayer whose adhan plays never posts the notification that would have vibrated, and
 * the playback channel cannot vibrate in its place - it is silent on purpose so it adds
 * nothing on top of the recitation, and a silent channel has no vibration either. So
 * someone who chose "Sound and vibration" was getting only the sound, which is the half
 * you miss with the phone in a pocket.
 */
class AdhanVibrationTest {

    private fun alert(vibrate: Boolean) = PrayerAlertNotification.Content(
        notificationId = 1,
        channelId = PrayerNotificationChannels.CHANNEL_ID_SOUND_VIBRATE,
        title = "Time for Dhuhr",
        text = "Dhuhr",
        soundEnabled = true,
        vibrateEnabled = vibrate
    )

    @Test
    fun `a recitation vibrates when the alert it replaces would have`() {
        assertTrue(
            AdhanPlaybackService.shouldVibrateOnStart(isPreview = false, alert = alert(vibrate = true))
        )
    }

    @Test
    fun `a recitation stays still when vibration is off`() {
        assertFalse(
            AdhanPlaybackService.shouldVibrateOnStart(isPreview = false, alert = alert(vibrate = false))
        )
    }

    /** Auditioning a reciter in Settings announces nothing, so it buzzes at nobody. */
    @Test
    fun `a settings audition never vibrates`() {
        assertFalse(
            AdhanPlaybackService.shouldVibrateOnStart(isPreview = true, alert = alert(vibrate = true))
        )
        assertFalse(AdhanPlaybackService.shouldVibrateOnStart(isPreview = true, alert = null))
    }

    /**
     * No alert means nobody entrusted this playback with an alert to replace - there is no
     * stated preference to honour, so it does not invent one.
     */
    @Test
    fun `playback with no alert to replace does not vibrate`() {
        assertFalse(AdhanPlaybackService.shouldVibrateOnStart(isPreview = false, alert = null))
    }
}
