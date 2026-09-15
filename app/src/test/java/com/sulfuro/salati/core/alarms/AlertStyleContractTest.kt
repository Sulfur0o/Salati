package com.sulfuro.salati.core.alarms

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.sulfuro.salati.core.alerts.PrayerNotificationChannels
import com.sulfuro.salati.core.audio.AdhanPlaybackService
import com.sulfuro.salati.data.settings.AlarmPreferences
import com.sulfuro.salati.data.settings.CalculationSettings
import com.sulfuro.salati.data.settings.adhanSoundIdFor
import com.sulfuro.salati.ui.settings.AlertStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The alert style is the authority, and nothing survives it.
 *
 * It is one row in Settings but it is stored as three independent booleans, and every
 * other alert setting - the chosen adhan, the Fajr recitation, vibration - sits beside it
 * rather than under it. That gap is where the bug came from: a recording stayed chosen
 * when the style changed, and the thing that played it asked whether one was chosen
 * instead of whether the style allowed sound.
 *
 * So this walks every style the Settings sheet offers, hands the alarm pipeline exactly
 * the flags that style produces, and states what each one is allowed to do. A new setting
 * that can make noise has to answer to this table or it fails here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], instrumentedPackages = ["androidx.loader.content"])
class AlertStyleContractTest {

    private lateinit var context: Context

    /** Every style is tested with a recording chosen, because that is the trap. */
    private val chosenAdhan = "mishary_alafasy"
    private val chosenFajrAdhan = "fajr_makkah"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as android.app.Application)
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .setNotificationsEnabled(true)
    }

    private fun settingsFor(style: String): CalculationSettings {
        val (muted, sound, vibrate) = AlertStyle.toFlags(style)
        return CalculationSettings(
            alarms = AlarmPreferences(
                notificationsMuted = muted,
                soundEnabled = sound,
                vibrateEnabled = vibrate,
                adhanSoundId = chosenAdhan,
                fajrAdhanSoundId = chosenFajrAdhan
            )
        )
    }

    /** Delivers a prayer alarm carrying exactly what this style would have scheduled. */
    private fun deliverPrayerAlarm(style: String, prayer: String = "Dhuhr") {
        val settings = settingsFor(style)
        val intent = Intent(AlarmScheduler.ACTION_PRAYER_ALARM).apply {
            putExtra(AlarmScheduler.EXTRA_PRAYER_NAME, prayer)
            putExtra(AlarmScheduler.EXTRA_NOTIFICATION_KIND, AlarmScheduler.KIND_PRAYER)
            putExtra(AlarmScheduler.EXTRA_SOUND_ENABLED, settings.alarms.soundEnabled)
            putExtra(AlarmScheduler.EXTRA_VIBRATE_ENABLED, settings.alarms.vibrateEnabled)
            // Deliberately attached whatever the style says: this is the alarm that was
            // registered before the style changed, or replayed from the registry after a
            // reboot. It is the case the receiver's own gate exists for, so delivering the
            // tidied-up version instead would leave that gate untested here.
            putExtra(
                AlarmScheduler.EXTRA_ADHAN_SOUND_ID,
                settings.adhanSoundIdFor(prayer.lowercase())
            )
        }
        AlarmReceiver().onReceive(context, intent)
    }

    private fun startedService(): String? =
        shadowOf(context as android.app.Application).nextStartedService?.component?.className

    private fun postedChannel(): String? =
        shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .activeNotifications.firstOrNull()?.notification?.channelId

    // ----- Styles that may not make a sound ---------------------------------------------

    @Test
    fun `vibration only notifies on the vibrating channel and plays nothing`() {
        deliverPrayerAlarm(AlertStyle.VIBRATION_ONLY)

        assertNull("Vibration only must not play a recording", startedService())
        assertEquals(PrayerNotificationChannels.CHANNEL_ID_VIBRATE, postedChannel())
    }

    @Test
    fun `silent notifies on the silent channel and plays nothing`() {
        deliverPrayerAlarm(AlertStyle.SILENT)

        assertNull("Silent must not play a recording", startedService())
        assertEquals(PrayerNotificationChannels.CHANNEL_ID_SILENT, postedChannel())
    }

    /** Fajr has its own recording, which must obey the style just as the general one does. */
    @Test
    fun `the Fajr recitation is refused by a silent style too`() {
        deliverPrayerAlarm(AlertStyle.VIBRATION_ONLY, prayer = "Fajr")

        assertNull("Fajr must not play its own recording either", startedService())
    }

    /** Muting stops the scheduler before an alarm exists, so nothing can arrive at all. */
    @Test
    fun `no notification schedules no alarms whatsoever`() = runBlocking {
        val result = AlarmScheduler.prepareAlarms(context, settingsFor(AlertStyle.NONE))

        assertEquals(AlarmPreparationResult.Disabled, result)
    }

    // ----- Styles that may ---------------------------------------------------------------

    @Test
    fun `sound and vibration plays the recording on the loud channel`() {
        deliverPrayerAlarm(AlertStyle.SOUND_AND_VIBRATION)

        assertEquals(AdhanPlaybackService::class.java.name, startedService())
    }

    @Test
    fun `sound only plays the recording`() {
        deliverPrayerAlarm(AlertStyle.SOUND_ONLY)

        assertEquals(AdhanPlaybackService::class.java.name, startedService())
    }

    /**
     * With no recording chosen the notification tone is the alert, so the channel still has
     * to carry the style - this is the path everyone who never downloads an adhan is on.
     */
    @Test
    fun `the channel follows the style when no recording is chosen`() {
        listOf(
            AlertStyle.SOUND_AND_VIBRATION to PrayerNotificationChannels.CHANNEL_ID_SOUND_VIBRATE,
            AlertStyle.SOUND_ONLY to PrayerNotificationChannels.CHANNEL_ID_SOUND_ONLY,
            AlertStyle.VIBRATION_ONLY to PrayerNotificationChannels.CHANNEL_ID_VIBRATE,
            AlertStyle.SILENT to PrayerNotificationChannels.CHANNEL_ID_SILENT
        ).forEach { (style, expectedChannel) ->
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancelAll()

            val (_, sound, vibrate) = AlertStyle.toFlags(style)
            AlarmReceiver().onReceive(
                context,
                Intent(AlarmScheduler.ACTION_PRAYER_ALARM).apply {
                    putExtra(AlarmScheduler.EXTRA_PRAYER_NAME, "Asr")
                    putExtra(AlarmScheduler.EXTRA_NOTIFICATION_KIND, AlarmScheduler.KIND_PRAYER)
                    putExtra(AlarmScheduler.EXTRA_SOUND_ENABLED, sound)
                    putExtra(AlarmScheduler.EXTRA_VIBRATE_ENABLED, vibrate)
                }
            )

            assertEquals("Wrong channel for $style", expectedChannel, postedChannel())
        }
    }
}
