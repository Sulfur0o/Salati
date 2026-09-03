package io.github.sulfuro25.salati.core.notifications

import android.Manifest
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.sulfuro25.salati.R
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [28], manifest = Config.NONE)
class PrayerNotificationChannelsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun channelDefinitionsAreStableAndIdempotent() {
        PrayerNotificationChannels.create(context)
        PrayerNotificationChannels.create(context)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channels = manager.notificationChannels.filter {
            it.id == PrayerNotificationChannels.CHANNEL_ID_VIBRATE ||
                it.id == PrayerNotificationChannels.CHANNEL_ID_SILENT
        }
        assertEquals(2, channels.size)

        val vibrate = manager.getNotificationChannel(PrayerNotificationChannels.CHANNEL_ID_VIBRATE)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, vibrate.importance)
        assertTrue(vibrate.shouldVibrate())
        assertArrayEquals(PrayerNotificationChannels.VIBRATION_PATTERN, vibrate.vibrationPattern)
        assertNull(vibrate.sound)

        val silent = manager.getNotificationChannel(PrayerNotificationChannels.CHANNEL_ID_SILENT)
        assertEquals(NotificationManager.IMPORTANCE_LOW, silent.importance)
        assertFalse(silent.shouldVibrate())
        assertNull(silent.sound)
    }

    @Test
    fun alarmReceiverUsesDedicatedNotificationIcon() {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_PRAYER_ALARM
            putExtra(AlarmScheduler.EXTRA_PRAYER_NAME, "fajr")
            putExtra(AlarmScheduler.EXTRA_IS_PRE_REMINDER, false)
            putExtra(AlarmScheduler.EXTRA_VIBRATE_ENABLED, true)
        }

        AlarmReceiver().onReceive(context, intent)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = Shadows.shadowOf(manager).allNotifications.single()
        assertEquals(R.drawable.ic_notification, notification.smallIcon.resId)
        assertEquals(PrayerNotificationChannels.CHANNEL_ID_VIBRATE, notification.channelId)
    }

    @Test
    @Config(sdk = [33], manifest = Config.NONE)
    fun notificationPermissionDenialOrRevocationDoesNotCrash() {
        val application = context.applicationContext as Application
        Shadows.shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmScheduler.ACTION_PRAYER_ALARM
            putExtra(AlarmScheduler.EXTRA_PRAYER_NAME, "isha")
        }

        AlarmReceiver().onReceive(context, intent)

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assertTrue(Shadows.shadowOf(manager).allNotifications.isEmpty())
    }
}
