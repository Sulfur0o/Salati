package com.sulfuro.salati.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Light - warm ivory ground, deep emerald ink, brass accent.
//
// The ground is the point: at L* 95 it still carries a chroma of 17, so it
// reads as paper rather than as white. The dark palette below is built to be
// its sibling rather than its inverse.
// ---------------------------------------------------------------------------
val LightBackground = Color(0xFFF6F0E5) // Warm ivory
val LightOnBackground = Color(0xFF26312C) // Text
val LightSurface = Color(0xFFFFFDF8) // Surface
val LightSurfaceVariant = Color(0xFFEEE6D8) // Secondary surface
val LightOnSurfaceVariant = Color(0xFF59635E) // Secondary text (meets 4.5:1 contrast)

// Container ladder. In light mode it descends from white as elevation rises,
// which is the Material convention and the opposite of the dark ladder.
val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFFFFDF8)
val LightSurfaceContainer = Color(0xFFF7F1E7)
val LightSurfaceContainerHigh = Color(0xFFF2EBDF)
val LightSurfaceContainerHighest = Color(0xFFEDE5D7)
val LightSurfaceDim = Color(0xFFE4DCCE)
val LightSurfaceBright = Color(0xFFFFFDF8)

val LightPrimary = Color(0xFF174B38) // Deep emerald
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFD1E1D9) // Pale green/sage
val LightOnPrimaryContainer = Color(0xFF0F3225)
val LightSecondary = Color(0xFF7C5C24) // Restrained darker brass (meets 4.5:1 contrast)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFEEE6D8)
val LightOnSecondaryContainer = Color(0xFF4C3C1E)
val LightTertiary = Color(0xFF174B38)
val LightTertiaryContainer = Color(0xFFE5EBE7)
val LightOutline = Color(0xFFDCD2C3) // Refined outline
val LightOutlineVariant = Color(0xFFDCD2C3)
val LightError = Color(0xFFBA3333) // Accessible error red (meets 4.5:1 contrast)
val LightErrorContainer = Color(0xFFFCE8E8)
val LightOnErrorContainer = Color(0xFF7A1C1C) // Deep dark red for high contrast against LightErrorContainer
val LightInverseSurface = Color(0xFF2B3631)
val LightInverseOnSurface = Color(0xFFF1EDE4)
val LightInversePrimary = Color(0xFF7FCFA5)

// ---------------------------------------------------------------------------
// Dark - emerald night.
//
// The previous dark palette sat its ground at L* 5, which is effectively black:
// the green in the hex was real but invisible at that darkness, so the theme
// read as an absence rather than as a material, and every elevation step had to
// be tiny to avoid a jump - four levels inside 9 points of lightness.
//
// This palette lifts the ground to L* 10 and opens the ladder to six levels
// across 17.7 points. It also turns the text down: near-white on near-black
// measured 16.5:1, past the point where text starts to halate, and it left
// secondary text only 1.81x quieter than body where the light theme has a 2.16x
// step. That step is where the light theme's calm comes from, so it is matched
// here at 2.13x rather than inverted away.
// ---------------------------------------------------------------------------
val DarkBackground = Color(0xFF151E19) // L* 10.2, warm-green
val DarkOnBackground = Color(0xFFDCDCD2) // Body text, 12.35:1 (was 16.54:1)
val DarkSurface = Color(0xFF1A2520)
val DarkSurfaceVariant = Color(0xFF1F2C26) // Raised surface: cards, the hero
val DarkOnSurfaceVariant = Color(0xFF8C9A91) // Secondary text, 5.80:1 (was 9.12:1)

// Container ladder, ascending with elevation. Every level is filled in: any
// role left unset falls back to Material's baseline, which in dark mode is a
// purple-grey belonging to neither Salati palette.
val DarkSurfaceContainerLowest = Color(0xFF101814)
val DarkSurfaceContainerLow = Color(0xFF1F2C26)
val DarkSurfaceContainer = Color(0xFF25342D)
val DarkSurfaceContainerHigh = Color(0xFF2C3D35)
val DarkSurfaceContainerHighest = Color(0xFF33463D)
val DarkSurfaceDim = Color(0xFF151E19)
val DarkSurfaceBright = Color(0xFF2C3D35)

val DarkPrimary = Color(0xFF46C08A) // Emerald, deepened from the old mint
val DarkOnPrimary = Color(0xFF06301E)
val DarkPrimaryContainer = Color(0xFF21503C)
val DarkOnPrimaryContainer = Color(0xFFBFE8D2)

/**
 * Brass. The most distinctive colour either palette owns, and on a dark ground
 * the one worth spending on the thing the screen exists to show - see
 * [SalatiAccents.countdown].
 */
val DarkSecondary = Color(0xFFD9BC7E)
val DarkOnSecondary = Color(0xFF3A2C0B)
val DarkSecondaryContainer = Color(0xFF3A2E14)
val DarkOnSecondaryContainer = Color(0xFFEBD7A6)
val DarkTertiary = Color(0xFF46C08A)
val DarkTertiaryContainer = Color(0xFF1D3329)
val DarkOnTertiaryContainer = Color(0xFFC8E6D5)
val DarkOutline = Color(0xFF4A5D54)
val DarkOutlineVariant = Color(0xFF33463D) // Dividers: 14.4 dL* on surface, matching light's 14.7
val DarkError = Color(0xFFE58080)
val DarkOnError = Color(0xFF3E0E0E)
val DarkErrorContainer = Color(0xFF4E2020)
val DarkOnErrorContainer = Color(0xFFFFD9D6)
val DarkInverseSurface = Color(0xFFDCDCD2)
val DarkInverseOnSurface = Color(0xFF1A2520)
val DarkInversePrimary = Color(0xFF174B38)
