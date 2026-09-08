package io.github.sulfuro25.salati.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cbrt

class SalatiThemeTokensTest {

    @Test
    fun lightPaletteMatchesApprovedValues() {
        assertEquals(Color(0xFFF6F0E5), LightBackground)
        assertEquals(Color(0xFF26312C), LightOnBackground)
        assertEquals(Color(0xFFFFFDF8), LightSurface)
        assertEquals(Color(0xFFFFFDF8), LightSurfaceContainerLow)
        assertEquals(Color(0xFFEEE6D8), LightSurfaceVariant)
        assertEquals(Color(0xFF59635E), LightOnSurfaceVariant)
        assertEquals(Color(0xFF174B38), LightPrimary)
        assertEquals(Color(0xFFFFFFFF), LightOnPrimary)
        assertEquals(Color(0xFFD1E1D9), LightPrimaryContainer)
        assertEquals(Color(0xFF0F3225), LightOnPrimaryContainer)
        assertEquals(Color(0xFF7C5C24), LightSecondary)
        assertEquals(Color(0xFF174B38), LightTertiary)
        assertEquals(Color(0xFFE5EBE7), LightTertiaryContainer)
        assertEquals(Color(0xFFDCD2C3), LightOutline)
        assertEquals(Color(0xFFDCD2C3), LightOutlineVariant)
        assertEquals(Color(0xFFBA3333), LightError)
        assertEquals(Color(0xFFFCE8E8), LightErrorContainer)
        assertEquals(Color(0xFF7A1C1C), LightOnErrorContainer)
    }

    @Test
    fun darkPaletteMatchesApprovedValues() {
        assertEquals(Color(0xFF151E19), DarkBackground)
        assertEquals(Color(0xFFDCDCD2), DarkOnBackground)
        assertEquals(Color(0xFF1A2520), DarkSurface)
        assertEquals(Color(0xFF1F2C26), DarkSurfaceContainerLow)
        assertEquals(Color(0xFF1F2C26), DarkSurfaceVariant)
        assertEquals(Color(0xFF8C9A91), DarkOnSurfaceVariant)
        assertEquals(Color(0xFF46C08A), DarkPrimary)
        assertEquals(Color(0xFF06301E), DarkOnPrimary)
        assertEquals(Color(0xFF21503C), DarkPrimaryContainer)
        assertEquals(Color(0xFFBFE8D2), DarkOnPrimaryContainer)
        assertEquals(Color(0xFFD9BC7E), DarkSecondary)
        assertEquals(Color(0xFF46C08A), DarkTertiary)
        assertEquals(Color(0xFF1D3329), DarkTertiaryContainer)
        assertEquals(Color(0xFF4A5D54), DarkOutline)
        assertEquals(Color(0xFF33463D), DarkOutlineVariant)
        assertEquals(Color(0xFFE58080), DarkError)
        assertEquals(Color(0xFF4E2020), DarkErrorContainer)
        assertEquals(Color(0xFFFFD9D6), DarkOnErrorContainer)
    }

    @Test
    fun primaryTextPairsMeetNormalTextContrast() {
        assertContrastAtLeast(LightOnBackground, LightBackground, 4.5f)
        assertContrastAtLeast(LightOnBackground, LightSurface, 4.5f)
        assertContrastAtLeast(LightOnSurfaceVariant, LightSurfaceVariant, 3.5f)
        assertContrastAtLeast(LightOnPrimary, LightPrimary, 4.5f)
        assertContrastAtLeast(LightOnPrimaryContainer, LightPrimaryContainer, 4.5f)
        assertContrastAtLeast(LightOnErrorContainer, LightErrorContainer, 4.5f)

        assertContrastAtLeast(DarkOnBackground, DarkBackground, 4.5f)
        assertContrastAtLeast(DarkOnBackground, DarkSurface, 4.5f)
        assertContrastAtLeast(DarkOnSurfaceVariant, DarkSurfaceVariant, 3.5f)
        assertContrastAtLeast(DarkOnPrimary, DarkPrimary, 4.5f)
        assertContrastAtLeast(DarkOnPrimaryContainer, DarkPrimaryContainer, 4.5f)
        assertContrastAtLeast(DarkOnSecondary, DarkSecondary, 4.5f)
        assertContrastAtLeast(DarkOnSecondaryContainer, DarkSecondaryContainer, 4.5f)
        assertContrastAtLeast(DarkOnTertiaryContainer, DarkTertiaryContainer, 4.5f)
        assertContrastAtLeast(DarkOnErrorContainer, DarkErrorContainer, 4.5f)
        assertContrastAtLeast(DarkOnError, DarkError, 4.5f)
    }

    /**
     * Body text at 16.5:1 on a near-black ground is past the point where it starts to
     * halate, and it is the main reason the old dark theme read as harsh. The ceiling
     * matters as much as the floor.
     */
    @Test
    fun darkBodyTextIsNotLoudEnoughToHalate() {
        val body = contrast(DarkOnBackground, DarkBackground)
        assertTrue("Body text at $body:1 is too loud for a dark ground", body <= 13.5f)
        assertTrue("Body text at $body:1 is too quiet", body >= 11f)
    }

    /**
     * The light theme's calm comes from the gap between its two text weights. The old
     * dark palette compressed that gap to 1.81x, so everything on screen spoke at the
     * same volume; this holds the two themes to the same step.
     */
    @Test
    fun bothThemesSeparateBodyFromSecondaryTextBySimilarAmounts() {
        val lightStep = contrast(LightOnBackground, LightBackground) /
            contrast(LightOnSurfaceVariant, LightBackground)
        val darkStep = contrast(DarkOnBackground, DarkBackground) /
            contrast(DarkOnSurfaceVariant, DarkBackground)

        assertTrue("Light step collapsed to $lightStep", lightStep >= 2.0f)
        assertTrue("Dark step collapsed to $darkStep", darkStep >= 2.0f)
    }

    /**
     * The old ladder fitted every surface into 9.1 points of lightness, so nothing could
     * separate from anything else. Cards, sheets and dialogs each need their own level.
     */
    @Test
    fun theDarkElevationLadderRisesAndHasRoomToBreathe() {
        val ladder = listOf(
            DarkSurfaceContainerLowest,
            DarkBackground,
            DarkSurface,
            DarkSurfaceContainerLow,
            DarkSurfaceContainer,
            DarkSurfaceContainerHigh,
            DarkSurfaceContainerHighest
        )
        for (i in 1 until ladder.size) {
            assertTrue(
                "Level $i is not lighter than the one below it",
                lightness(ladder[i]) > lightness(ladder[i - 1])
            )
        }
        val spread = lightness(ladder.last()) - lightness(DarkBackground)
        assertTrue("Ladder spread of $spread L* is too flat", spread >= 15f)

        // A ground at L* 5 is black with a rumour of green; the colour has to be visible.
        val ground = lightness(DarkBackground)
        assertTrue("Ground at L* $ground is effectively black", ground >= 8f)
        assertTrue("Ground at L* $ground is no longer a dark theme", ground <= 14f)
    }

    /** In light mode the ladder descends from white, which is the Material convention. */
    @Test
    fun theLightElevationLadderDescendsFromWhite() {
        val ladder = listOf(
            LightSurfaceContainerLowest,
            LightSurfaceContainerLow,
            LightSurfaceContainer,
            LightSurfaceContainerHigh,
            LightSurfaceContainerHighest
        )
        for (i in 1 until ladder.size) {
            assertTrue(
                "Level $i is not darker than the one above it",
                lightness(ladder[i]) < lightness(ladder[i - 1])
            )
        }
    }

    /**
     * Any role left out of the scheme silently falls back to Material's baseline, which
     * is a purple-grey belonging to neither palette - that is how the dialogs ended up
     * rendering in #2B2930. This fails if a surface role is ever dropped again.
     */
    @Test
    fun everySurfaceRoleIsSalatisOwnAndNotAMaterialFallback() {
        assertSurfacesBelongToPalette(
            SalatiDarkColorScheme,
            setOf(
                DarkBackground, DarkSurface, DarkSurfaceVariant, DarkSurfaceDim,
                DarkSurfaceBright, DarkSurfaceContainerLowest, DarkSurfaceContainerLow,
                DarkSurfaceContainer, DarkSurfaceContainerHigh, DarkSurfaceContainerHighest
            )
        )
        assertSurfacesBelongToPalette(
            SalatiLightColorScheme,
            setOf(
                LightBackground, LightSurface, LightSurfaceVariant, LightSurfaceDim,
                LightSurfaceBright, LightSurfaceContainerLowest, LightSurfaceContainerLow,
                LightSurfaceContainer, LightSurfaceContainerHigh, LightSurfaceContainerHighest
            )
        )
    }

    /** Dividers have to do the same amount of work in both themes. */
    @Test
    fun dividersAreEquallyVisibleInBothThemes() {
        val light = lightness(LightSurface) - lightness(LightOutlineVariant)
        val dark = lightness(DarkOutlineVariant) - lightness(DarkSurface)

        assertTrue("Light divider separation of $light L* is too faint", light >= 10f)
        assertTrue("Dark divider separation of $dark L* is too faint", dark >= 10f)
    }

    private fun assertSurfacesBelongToPalette(scheme: ColorScheme, palette: Set<Color>) {
        val roles = mapOf(
            "background" to scheme.background,
            "surface" to scheme.surface,
            "surfaceVariant" to scheme.surfaceVariant,
            "surfaceDim" to scheme.surfaceDim,
            "surfaceBright" to scheme.surfaceBright,
            "surfaceContainerLowest" to scheme.surfaceContainerLowest,
            "surfaceContainerLow" to scheme.surfaceContainerLow,
            "surfaceContainer" to scheme.surfaceContainer,
            "surfaceContainerHigh" to scheme.surfaceContainerHigh,
            "surfaceContainerHighest" to scheme.surfaceContainerHighest
        )
        for ((name, color) in roles) {
            assertTrue(
                "$name is $color, which is not a Salati colour - it fell back to Material",
                color in palette
            )
        }
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun assertContrastAtLeast(foreground: Color, background: Color, minimum: Float) {
        val ratio = contrast(foreground, background)
        assertTrue("Expected contrast >= $minimum but was $ratio", ratio >= minimum)
    }

    /** CIE L*, which tracks perceived lightness where raw luminance does not. */
    private fun lightness(color: Color): Float {
        val y = color.luminance()
        return if (y > 0.008856f) 116f * cbrt(y) - 16f else 903.3f * y
    }
}
