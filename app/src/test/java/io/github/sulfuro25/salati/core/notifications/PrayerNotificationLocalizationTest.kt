package io.github.sulfuro25.salati.core.notifications

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.sulfuro25.salati.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class PrayerNotificationLocalizationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val receiver = AlarmReceiver()

    @Test
    fun localizedPrayerNameResolvesAllKeys() {
        val keys = listOf("fajr", "sunrise", "dhuhr", "asr", "maghrib", "isha", "white_days")
        for (key in keys) {
            val localized = receiver.localizedPrayerName(context, key)
            assertNotEquals("", localized)
        }
        assertEquals(context.getString(R.string.prayer_fajr), receiver.localizedPrayerName(context, "fajr"))
        assertEquals(context.getString(R.string.prayer_dhuhr), receiver.localizedPrayerName(context, "dhuhr"))
        assertEquals(context.getString(R.string.prayer_asr), receiver.localizedPrayerName(context, "asr"))
        assertEquals(context.getString(R.string.prayer_maghrib), receiver.localizedPrayerName(context, "maghrib"))
        assertEquals(context.getString(R.string.prayer_isha), receiver.localizedPrayerName(context, "isha"))
        assertEquals(context.getString(R.string.event_white_day), receiver.localizedPrayerName(context, "white_days"))
    }
}
