package com.sulfuro.salati.core.alarms

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.sulfuro.salati.R
import com.sulfuro.salati.core.audio.AdhanPlaybackService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], instrumentedPackages = ["androidx.loader.content"])
class AlarmReceiverTest {
    
    @Test
    fun `receiver ignores unknown kinds`() {
        val receiver = AlarmReceiver()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val intent = Intent(AlarmScheduler.ACTION_PRAYER_ALARM).apply {
            putExtra(AlarmScheduler.EXTRA_PRAYER_NAME, "Fajr")
            putExtra(AlarmScheduler.EXTRA_NOTIFICATION_KIND, "UNKNOWN_KIND")
        }
        
        receiver.onReceive(context, intent)
        
        val activeNotifications = shadowOf(notificationManager).activeNotifications
        assertEquals("Should suppress unknown kind", 0, activeNotifications.size)
    }

    @Test
    fun `receiver displays white days reminder`() {
        val receiver = AlarmReceiver()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        org.robolectric.Shadows.shadowOf(context as android.app.Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(notificationManager).setNotificationsEnabled(true)
        
        val intent = Intent(AlarmScheduler.ACTION_PRAYER_ALARM).apply {
            putExtra(AlarmScheduler.EXTRA_PRAYER_NAME, "white_days")
            putExtra(AlarmScheduler.EXTRA_NOTIFICATION_KIND, AlarmScheduler.KIND_WHITE_DAYS)
        }
        
        receiver.onReceive(context, intent)
        
        val activeNotifications = shadowOf(notificationManager).activeNotifications
        assertEquals("Should post white days reminder", 1, activeNotifications.size)
        
        val notification = activeNotifications.first()
        val title = notification.notification.extras.getString("android.title")
        assertEquals(context.getString(R.string.notification_white_days_title), title)
        assertEquals(300, notification.id)
    }

    @Test
    fun `receiver defaults to prayer kind if missing`() {
        val receiver = AlarmReceiver()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        org.robolectric.Shadows.shadowOf(context as android.app.Application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(notificationManager).setNotificationsEnabled(true)
        
        val intent = Intent(AlarmScheduler.ACTION_PRAYER_ALARM).apply {
            putExtra(AlarmScheduler.EXTRA_PRAYER_NAME, "Fajr")
        }
        
        receiver.onReceive(context, intent)
        
        val activeNotifications = shadowOf(notificationManager).activeNotifications
        assertEquals(1, activeNotifications.size)
        val title = activeNotifications.first().notification.extras.getString("android.title")
        assertEquals("Time for Fajr", title)
    }

    /**
     * Choosing "Vibration only" turns sound off but leaves the chosen recording stored -
     * it is a separate setting, and the user has not unchosen it. The alarm must honour
     * the alert style anyway: a full adhan at volume is the loudest thing this app can do,
     * and the one thing someone asking for vibration explicitly did not ask for.
     */
    @Test
    fun `a chosen adhan does not play when the alert style is vibration only`() {
        val context = prepared()

        AlarmReceiver().onReceive(context, prayerAlarm(sound = false, vibrate = true))

        assertNull(
            "Vibration only must not start adhan playback",
            shadowOf(context as android.app.Application).nextStartedService
        )
        assertEquals(
            "The prayer should still be notified, silently",
            1,
            shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .activeNotifications.size
        )
    }

    /** Silent is quieter still, and was letting the same recitation through. */
    @Test
    fun `a chosen adhan does not play when the alert style is silent`() {
        val context = prepared()

        AlarmReceiver().onReceive(context, prayerAlarm(sound = false, vibrate = false))

        assertNull(
            "Silent must not start adhan playback",
            shadowOf(context as android.app.Application).nextStartedService
        )
    }

    /** The other half of the contract: with sound on, the recitation still plays. */
    @Test
    fun `a chosen adhan still plays when sound is on`() {
        val context = prepared()

        AlarmReceiver().onReceive(context, prayerAlarm(sound = true, vibrate = true))

        val started = shadowOf(context as android.app.Application).nextStartedService
        assertNotNull("Sound on must start adhan playback", started)
        assertEquals(
            AdhanPlaybackService::class.java.name,
            started!!.component?.className
        )
    }

    /** A pre-prayer reminder is a heads-up, never a call to prayer. */
    @Test
    fun `a pre-prayer reminder never plays the adhan even with sound on`() {
        val context = prepared()

        AlarmReceiver().onReceive(
            context,
            prayerAlarm(sound = true, vibrate = true, kind = AlarmScheduler.KIND_PRE_PRAYER)
        )

        assertNull(shadowOf(context as android.app.Application).nextStartedService)
    }

    private fun prepared(): Context {
        val context = ApplicationProvider.getApplicationContext<Context>()
        shadowOf(context as android.app.Application)
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        shadowOf(context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .setNotificationsEnabled(true)
        return context
    }

    private fun prayerAlarm(
        sound: Boolean,
        vibrate: Boolean,
        kind: String = AlarmScheduler.KIND_PRAYER
    ): Intent = Intent(AlarmScheduler.ACTION_PRAYER_ALARM).apply {
        putExtra(AlarmScheduler.EXTRA_PRAYER_NAME, "Dhuhr")
        putExtra(AlarmScheduler.EXTRA_NOTIFICATION_KIND, kind)
        putExtra(AlarmScheduler.EXTRA_SOUND_ENABLED, sound)
        putExtra(AlarmScheduler.EXTRA_VIBRATE_ENABLED, vibrate)
        putExtra(AlarmScheduler.EXTRA_ADHAN_SOUND_ID, "mishary_alafasy")
    }
}
