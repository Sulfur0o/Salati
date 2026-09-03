package io.github.sulfuro25.salati.core.computation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.sulfuro25.salati.data.settings.CalculationSettings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class HijriMetadataLoaderTest {

    private lateinit var context: Context
    private val settings = CalculationSettings()

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun testGetHijriMetadataRange() = runBlocking {
        // Just verify it doesn't crash and returns empty map if offline or something
        val startDate = LocalDate.of(2026, 7, 15)
        val endDate = LocalDate.of(2026, 7, 16)
        
        val map = PrayerRepository.getHijriMetadataRange(context, settings, startDate, endDate)
        assertNotNull(map)
    }
}
