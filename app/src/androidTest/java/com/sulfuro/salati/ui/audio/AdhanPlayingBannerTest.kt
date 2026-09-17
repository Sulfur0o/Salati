package com.sulfuro.salati.ui.audio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sulfuro.salati.R
import com.sulfuro.salati.core.audio.AdhanPlaybackService
import com.sulfuro.salati.theme.SalatiTheme
import com.sulfuro.salati.ui.components.AdhanPlayingBanner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The in-app way out of a recitation that started somewhere it cannot be allowed to run.
 *
 * The notification has always carried a Stop button, but the app is where people look
 * first - so the banner has to appear on its own, say which prayer is calling, and stop
 * the adhan on one tap. It also has to stay out of the way the rest of the time, which is
 * every moment except those few minutes.
 */
@RunWith(AndroidJUnit4::class)
class AdhanPlayingBannerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(id: Int): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private val dhuhr = AdhanPlaybackService.NowPlaying(
        adhanId = "makkah",
        prayerLabel = "Dhuhr",
        isPreview = false
    )

    private fun setBanner(
        nowPlaying: AdhanPlaybackService.NowPlaying?,
        onStop: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            SalatiTheme(darkTheme = false) {
                AdhanPlayingBanner(nowPlaying = nowPlaying, onStop = onStop)
            }
        }
    }

    @Test
    fun aPlayingAdhanIsAnnouncedWithTheStopButton() {
        setBanner(dhuhr)

        composeTestRule.onNodeWithText("Dhuhr").assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.adhan_playing)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.adhan_stop)).assertIsDisplayed()
    }

    @Test
    fun theStopButtonStopsTheAdhan() {
        var stops = 0
        setBanner(dhuhr, onStop = { stops++ })

        composeTestRule.onNodeWithText(string(R.string.adhan_stop)).performClick()

        assertEquals(1, stops)
    }

    /** Nothing is playing, so there is nothing to show and nothing to push the tabs down. */
    @Test
    fun silenceShowsNoBanner() {
        setBanner(null)

        composeTestRule.onNodeWithText(string(R.string.adhan_stop)).assertDoesNotExist()
    }

    /**
     * A settings audition is already beside its own stop button, on the row the user
     * tapped to start it. A second one across the top of the app would be in the way.
     */
    @Test
    fun aSettingsPreviewIsLeftToThePicker() {
        setBanner(dhuhr.copy(prayerLabel = "Masjid al-Haram", isPreview = true))

        composeTestRule.onNodeWithText(string(R.string.adhan_stop)).assertDoesNotExist()
    }

    /**
     * The label comes from an alarm that was scheduled elsewhere, so it can arrive empty.
     * The banner is the only stop button on screen: it has to appear either way.
     */
    @Test
    fun anAdhanWithNoPrayerNameStillOffersStop() {
        setBanner(dhuhr.copy(prayerLabel = ""))

        composeTestRule.onNodeWithText(string(R.string.adhan_playing)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.adhan_stop)).assertIsDisplayed()
    }
}
