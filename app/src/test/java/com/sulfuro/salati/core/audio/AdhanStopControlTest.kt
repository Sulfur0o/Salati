package com.sulfuro.salati.core.audio

import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sulfuro.salati.R
import com.sulfuro.salati.core.alerts.PrayerNotificationChannels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.nio.file.Files
import java.nio.file.Path

/**
 * An adhan must be stoppable in one gesture, wherever the user is when it starts.
 *
 * The recitation is the loudest thing this app does and it runs for minutes. It can begin
 * in a waiting room, a meeting or a shop, and until now the only control that ended it was
 * a low-importance notification: no screen, no sound, nothing to see unless you thought to
 * pull the shade down and scroll past the quiet notifications. These tests pin the three
 * ways out that replaced it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class AdhanStopControlTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val manager: NotificationManager
        get() = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * High importance is the whole point of the channel: it is what puts the notification,
     * and so the Stop button, in front of the user instead of at the bottom of the shade.
     */
    @Test
    fun theStopButtonArrivesOnScreenRatherThanInTheShade() {
        PrayerNotificationChannels.create(context)

        val channel = manager.getNotificationChannel(
            PrayerNotificationChannels.CHANNEL_ID_ADHAN_PLAYBACK
        )

        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
    }

    /**
     * Loud, though, is the recitation's job and not the notification's. Raising the
     * importance must not have bought a second alert on top of the adhan.
     */
    @Test
    fun thePlaybackChannelStillAddsNothingToTheRecitation() {
        PrayerNotificationChannels.create(context)

        val channel = manager.getNotificationChannel(
            PrayerNotificationChannels.CHANNEL_ID_ADHAN_PLAYBACK
        )

        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
    }

    /**
     * The importance of an existing channel belongs to the user, so the louder one had to
     * be a new channel. The old one is deleted rather than left behind as a second,
     * identically named entry in Android's notification settings.
     */
    @Test
    fun theOriginalQuietPlaybackChannelIsNotLeftBehind() {
        PrayerNotificationChannels.create(context)

        val ids = manager.notificationChannels.map { it.id }

        assertFalse("salati_adhan_playback" in ids)
        assertTrue(PrayerNotificationChannels.CHANNEL_ID_ADHAN_PLAYBACK in ids)
    }

    /**
     * Stop is an action on the notification, and dismissing the notification means the
     * same thing: on Android 14 a foreground service's notification can be swiped away,
     * and without the delete intent that would leave the adhan playing with no visible
     * way to end it at all.
     */
    @Test
    fun theOngoingNotificationOffersStopAndTreatsASwipeAsOne() {
        val notification = playbackNotification()

        val stop = context.getString(R.string.adhan_stop)
        assertTrue(notification.actions.orEmpty().any { it.title?.toString() == stop })
        assertNotNull("swiping the notification away has to stop it", notification.deleteIntent)
    }

    /** A prayer name and a stop button; nothing here is worth an unlock. */
    @Test
    fun theStopButtonIsReachableFromTheLockScreen() {
        val notification = playbackNotification()

        assertEquals(android.app.Notification.VISIBILITY_PUBLIC, notification.visibility)
        assertEquals(
            PrayerNotificationChannels.CHANNEL_ID_ADHAN_PLAYBACK,
            notification.channelId
        )
    }

    /**
     * The notification adds nothing to the recitation, but it has to do so without
     * joining a group.
     *
     * NotificationCompat.setSilent reads like exactly the right call here and is not: it
     * files the notification under a group named "silent" with GROUP_ALERT_SUMMARY, and a
     * grouped child that defers to its summary is never put on screen. That is how this
     * notification came to be invisible on a real phone even at high importance, so the
     * silence comes from the channel and the notification stays ungrouped.
     */
    // sound, vibrate and defaults are deprecated in favour of the channel, which is the
    // right place for them - and exactly why they have to be empty here.
    @Suppress("DEPRECATION")
    @Test
    fun nothingAboutTheNotificationSuppressesItsOwnAppearance() {
        val notification = playbackNotification()

        assertNull("a grouped notification can be held back by its summary", notification.group)
        assertNull(notification.sound)
        assertNull(notification.vibrate)
        assertEquals(0, notification.defaults)
    }

    /**
     * The third way out, and the one people reach for first: the app itself. Opening
     * Salati while a recitation plays has to offer the same button, on whatever tab.
     */
    @Test
    fun theAppItselfCarriesTheSameStopButton() {
        val navigation = source("Navigation.kt")
        val banner = source("ui/components/AdhanPlayingBanner.kt")

        assertTrue(
            "the banner is not mounted above the tabs",
            navigation.contains("topBar = { AdhanPlayingBanner() }")
        )
        assertTrue(banner.contains("AdhanPlaybackService.stop(appContext)"))
        // Read from the service, never tracked locally: a recitation ends by itself, and
        // a banner that did not know that would go on offering to stop silence.
        assertTrue(banner.contains("AdhanPlaybackService.nowPlaying"))
    }

    /** The notification the service posts for itself as the recitation starts. */
    private fun playbackNotification(): android.app.Notification =
        Robolectric.buildService(AdhanPlaybackService::class.java)
            .create()
            .get()
            .buildNotification("Dhuhr")

    private fun source(relative: String): String {
        val base = "src/main/java/com/sulfuro/salati/"
        val direct = Path.of(base + relative)
        val path = if (Files.exists(direct)) direct else Path.of("app").resolve(base + relative)
        return String(Files.readAllBytes(path))
    }
}
