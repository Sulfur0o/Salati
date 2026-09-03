package io.github.sulfuro25.salati.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertEquals(Color(0xFF09130F), DarkBackground)
        assertEquals(Color(0xFFF1F0EA), DarkOnBackground)
        assertEquals(Color(0xFF111D18), DarkSurface)
        assertEquals(Color(0xFF09130F), DarkSurfaceContainerLow)
        assertEquals(Color(0xFF182720), DarkSurfaceVariant)
        assertEquals(Color(0xFFB6BEB9), DarkOnSurfaceVariant)
        assertEquals(Color(0xFF62B287), DarkPrimary)
        assertEquals(Color(0xFF0C2618), DarkOnPrimary)
        assertEquals(Color(0xFF194D36), DarkPrimaryContainer)
        assertEquals(Color(0xFFD6F0E0), DarkOnPrimaryContainer)
        assertEquals(Color(0xFFC5A766), DarkSecondary)
        assertEquals(Color(0xFF62B287), DarkTertiary)
        assertEquals(Color(0xFF182720), DarkTertiaryContainer)
        assertEquals(Color(0xFF26392F), DarkOutline)
        assertEquals(Color(0xFF26392F), DarkOutlineVariant)
        assertEquals(Color(0xFFE26666), DarkError)
        assertEquals(Color(0xFF4A1A1A), DarkErrorContainer)
        assertEquals(Color(0xFFFFDAD6), DarkOnErrorContainer)
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
        assertContrastAtLeast(DarkOnErrorContainer, DarkErrorContainer, 4.5f)
    }

    private fun assertContrastAtLeast(foreground: Color, background: Color, minimum: Float) {
        val lighter = maxOf(foreground.luminance(), background.luminance())
        val darker = minOf(foreground.luminance(), background.luminance())
        val ratio = (lighter + 0.05f) / (darker + 0.05f)
        assertTrue("Expected contrast >= $minimum but was $ratio", ratio >= minimum)
    }
}
