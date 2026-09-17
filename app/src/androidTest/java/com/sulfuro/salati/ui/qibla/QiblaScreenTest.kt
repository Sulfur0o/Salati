package com.sulfuro.salati.ui.qibla

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sulfuro.salati.R
import com.sulfuro.salati.core.computation.QiblaCalculator
import com.sulfuro.salati.core.sensors.CompassAccuracy
import com.sulfuro.salati.core.sensors.CompassReading
import com.sulfuro.salati.data.settings.CalculationSettings
import com.sulfuro.salati.data.settings.LocationSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Qibla screen, which had no test of its own: the bearing arithmetic underneath it is
 * covered by QiblaCalculatorTest, but nothing checked that the screen shows that answer,
 * shows it for the location the user actually saved, or says the right thing in each of
 * the states a compass can be in.
 *
 * Those states are why the screen now takes a [CompassReading] rather than only reading the
 * magnetometer itself: no sensor, no fix yet, aligned, and needs calibrating are all real,
 * and none of them can be produced on demand by a test holding a phone still.
 */
@RunWith(AndroidJUnit4::class)
class QiblaScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(id: Int, vararg args: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    /** London: far enough from Mecca that a swapped latitude and longitude would show. */
    private val london = CalculationSettings(
        location = LocationSettings(
            latitude = 51.5074,
            longitude = -0.1278,
            cityName = "London, United Kingdom"
        )
    )

    private val londonBearing = QiblaCalculator.bearingToKaaba(51.5074, -0.1278)

    /**
     * Formatted the way the screen formats it, in the locale the screen reads off the
     * configuration. The *value* is ground-truthed in QiblaCalculatorTest; what matters
     * here is that the screen fed the calculator the coordinates the user saved.
     */
    private fun bearingText(bearing: Double = londonBearing): String {
        val locale = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.configuration.locales[0]
        return String.format(locale, "%.0f°", bearing)
    }

    private fun showLondonWith(
        compass: CompassReading,
        onBack: () -> Unit = {}
    ) {
        composeTestRule.setContent {
            QiblaScreen(settings = london, onBack = onBack, compass = compass)
        }
    }

    /**
     * The bearing on screen has to come from the saved location, not from a default and
     * not from the coordinates the other way round. London's Qibla is east-south-east; with
     * the pair swapped it would point somewhere else entirely.
     */
    @Test
    fun theSavedLocationDecidesWhatTheScreenSays() {
        showLondonWith(CompassReading(trueHeadingDegrees = 0f, accuracy = CompassAccuracy.HIGH))

        composeTestRule.onNodeWithText("London, United Kingdom").assertIsDisplayed()
        composeTestRule.onNodeWithText(bearingText()).assertIsDisplayed()
        composeTestRule.onNodeWithText("ESE").assertIsDisplayed()
        assertEquals("ESE", QiblaCalculator.compassPointFor(londonBearing))
    }

    /**
     * A phone with no magnetometer is not a broken Qibla screen: the direction from the
     * saved location is still known, and the screen has to say it in words since it cannot
     * point. A user on a budget handset lands on this branch permanently.
     */
    @Test
    fun aPhoneWithNoCompassIsStillToldWhereTheQiblaIs() {
        showLondonWith(
            CompassReading(trueHeadingDegrees = null, accuracy = CompassAccuracy.UNAVAILABLE)
        )

        composeTestRule.onNodeWithText(string(R.string.qibla_no_sensor))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(
            string(R.string.qibla_no_sensor_hint, bearingText(), "ESE")
        ).performScrollTo().assertIsDisplayed()
    }

    /** Within the tolerance the calculator allows, the screen should say so plainly. */
    @Test
    fun pointingAtTheQiblaSaysSo() {
        showLondonWith(
            CompassReading(
                trueHeadingDegrees = londonBearing.toFloat(),
                accuracy = CompassAccuracy.HIGH
            )
        )

        assertTrue(QiblaCalculator.isAligned(londonBearing.toFloat(), londonBearing))
        composeTestRule.onNodeWithText(string(R.string.qibla_aligned))
            .performScrollTo()
            .assertIsDisplayed()
    }

    /** A quarter turn away is not "close enough", and the screen must not claim it is. */
    @Test
    fun pointingAQuarterTurnAwayAsksYouToKeepTurning() {
        showLondonWith(
            CompassReading(
                trueHeadingDegrees = (londonBearing + 90.0).toFloat(),
                accuracy = CompassAccuracy.HIGH
            )
        )

        composeTestRule.onNodeWithText(string(R.string.qibla_turn_to_align))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.qibla_aligned)).assertDoesNotExist()
    }

    /**
     * Before the first sensor fix the heading is null. The screen must still draw, still
     * show the bearing, and must not congratulate anyone on facing the Qibla by accident:
     * every user sees this state for the first fraction of a second.
     */
    @Test
    fun theScreenIsUsableBeforeTheFirstSensorFix() {
        showLondonWith(
            CompassReading(trueHeadingDegrees = null, accuracy = CompassAccuracy.MEDIUM)
        )

        composeTestRule.onNodeWithText(bearingText()).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.qibla_turn_to_align))
            .performScrollTo()
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.qibla_aligned)).assertDoesNotExist()
    }

    /**
     * An uncalibrated magnetometer can be wrong by tens of degrees, so a Qibla reading
     * taken from one is worth nothing until the user waves the phone about. The advice has
     * to appear, and it appears alongside the usual status rather than replacing it.
     */
    @Test
    fun anUncalibratedCompassSaysHowToFixItself() {
        showLondonWith(
            CompassReading(
                trueHeadingDegrees = londonBearing.toFloat(),
                accuracy = CompassAccuracy.NEEDS_CALIBRATION
            )
        )

        composeTestRule.onNodeWithText(string(R.string.qibla_calibrate))
            .performScrollTo()
            .assertIsDisplayed()
    }

    /** A calibrated compass must not nag. */
    @Test
    fun aHealthyCompassKeepsTheCalibrationAdviceToItself() {
        showLondonWith(
            CompassReading(
                trueHeadingDegrees = londonBearing.toFloat(),
                accuracy = CompassAccuracy.HIGH
            )
        )

        composeTestRule.onNodeWithText(string(R.string.qibla_calibrate)).assertDoesNotExist()
    }

    /**
     * The dial is two canvases and says nothing to a screen reader by itself, so it carries
     * the whole reading - direction, compass point, and the city it was computed for - as
     * one description.
     */
    @Test
    fun theDialReadsTheWholeDirectionAloud() {
        showLondonWith(CompassReading(trueHeadingDegrees = 0f, accuracy = CompassAccuracy.HIGH))

        composeTestRule.onNodeWithContentDescription(
            string(R.string.qibla_accessibility, bearingText(), "ESE", "London, United Kingdom")
        ).assertIsDisplayed()
    }

    /** The screen opens on top of the daily view; this arrow is the only way out of it. */
    @Test
    fun theBackArrowGoesBack() {
        var wentBack = false
        showLondonWith(
            CompassReading(trueHeadingDegrees = 0f, accuracy = CompassAccuracy.HIGH),
            onBack = { wentBack = true }
        )

        composeTestRule.onNodeWithContentDescription(string(R.string.qibla_back)).performClick()
        assertTrue(wentBack)
    }
}
