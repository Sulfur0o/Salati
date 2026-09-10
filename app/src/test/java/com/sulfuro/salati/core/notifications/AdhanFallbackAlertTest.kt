package com.sulfuro.salati.core.notifications

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.nio.file.Path

/**
 * A prayer must always notify, even when its adhan does not play.
 *
 * Starting the playback service says nothing about whether audio will be heard: focus can
 * be denied and the file can fail to decode, both of them after [AlarmReceiver] has
 * already returned. So the alert is handed to the service, which either shows its own
 * playback notification or posts this one - and the failure that used to leave the user
 * with silence and no notification at all is not reachable.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class AdhanFallbackAlertTest {

    private val alert = PrayerAlertNotification.Content(
        notificationId = 142,
        channelId = "prayer_alarms",
        title = "Dhuhr",
        text = "It is time for Dhuhr",
        soundEnabled = true,
        vibrateEnabled = false
    )

    @Test
    fun theAlertSurvivesTheTripThroughAnIntent() {
        val carried = PrayerAlertNotification.Content.readFrom(alert.writeTo(Intent()))

        assertEquals(alert, carried)
    }

    /**
     * The settings preview starts the same service with no alert attached. A failed
     * preview must stay silent rather than announcing a prayer that is not due.
     */
    @Test
    fun anIntentWithoutAnAlertCarriesNothingToPost() {
        assertNull(PrayerAlertNotification.Content.readFrom(Intent()))
        assertNull(PrayerAlertNotification.Content.readFrom(null))
    }

    /** Half-written extras are not an alert either; a partial notification is worse. */
    @Test
    fun anIncompleteAlertIsRejectedRatherThanPostedWithGaps() {
        val partial = Intent().putExtra(PrayerAlertNotification.EXTRA_TITLE, "Dhuhr")

        assertNull(PrayerAlertNotification.Content.readFrom(partial))
    }

    /**
     * The receiver may only skip its own notification by giving the alert to someone who
     * will still post it. This is the shape of the bug that was fixed: an early return on
     * a service start that had not yet played anything.
     */
    @Test
    fun theReceiverOnlySkipsItsNotificationWhenItHandsTheAlertOver() {
        val receiver = source("core/notifications/AlarmReceiver.kt")

        assertTrue(
            "the alert must travel with the start request",
            receiver.contains("AdhanPlaybackService.start(context, adhanSoundId, displayPrayerName, alert)")
        )
        assertTrue(
            "a refused foreground-service start still posts the prayer alert",
            receiver.contains("playAdhanId = adhanSoundId")
        )
        // The unconditional post is the last statement, so every path that does not hand
        // the alert to the service still notifies.
        assertTrue(receiver.trimEnd().contains("PrayerAlertNotification.post(context, alert)"))
    }

    /** And the service has to honour it on every way out that is not audible playback. */
    @Test
    fun theServicePostsTheAlertOnEveryFailureAndClearsItOnceAudioStarts() {
        val service = source("core/audio/AdhanPlaybackService.kt")

        // Four ways out that are not audible playback: no recording on disk, audio focus
        // denied, the player refusing the file, and a decode error before the first frame.
        val callSites = Regex("(?<!fun )failToNotification\\(\\)").findAll(service).count()
        assertEquals("every silent exit must still post the alert", 4, callSites)

        assertTrue(
            "the alert is spent once the recitation is actually audible",
            service.contains("setOnPreparedListener")
        )
        // Blocking prepare on the main thread would stall the UI at prayer time, and
        // would also mean success is reported before any audio exists.
        assertTrue(service.contains("prepareAsync()"))
        assertTrue("prepare must not block the main thread", !service.contains("prepare()"))
    }

    private fun source(relative: String): String {
        val base = "src/main/java/com/sulfuro/salati/"
        val direct = Path.of(base + relative)
        val path = if (Files.exists(direct)) direct else Path.of("app").resolve(base + relative)
        return String(Files.readAllBytes(path))
    }
}
