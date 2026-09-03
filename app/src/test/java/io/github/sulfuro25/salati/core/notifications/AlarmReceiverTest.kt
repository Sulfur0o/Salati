package io.github.sulfuro25.salati.core.notifications

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import io.github.sulfuro25.salati.R
import org.junit.Assert.assertEquals
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
}
