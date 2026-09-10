package com.sulfuro.salati.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val BaseStyle = TextStyle(fontFamily = FontFamily.Default)

object SalatiTypeTokens {
    val Countdown = BaseStyle.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-1.0).sp,
        fontFeatureSettings = "tnum"
    )
    val PrayerTime = BaseStyle.copy(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        fontFeatureSettings = "tnum"
    )
    val MetadataTabular = BaseStyle.copy(
        fontWeight = FontWeight.Medium,
        fontSize = 11.5.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.5.sp,
        fontFeatureSettings = "tnum"
    )
}

val SalatiTypography = Typography(
    headlineLarge = BaseStyle.copy(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp),
    headlineMedium = BaseStyle.copy(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.5).sp),
    titleLarge = BaseStyle.copy(fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = BaseStyle.copy(fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = BaseStyle.copy(fontWeight = FontWeight.Normal, fontSize = 14.5.sp, lineHeight = 20.sp),
    bodyMedium = BaseStyle.copy(fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp),
    bodySmall = BaseStyle.copy(fontWeight = FontWeight.Medium, fontSize = 11.5.sp, lineHeight = 15.sp, letterSpacing = 0.3.sp),
    labelLarge = BaseStyle.copy(fontWeight = FontWeight.Medium, fontSize = 13.5.sp, lineHeight = 18.sp, letterSpacing = 0.8.sp)
)

@Deprecated("Use SalatiTypography", ReplaceWith("SalatiTypography"))
val Typography = SalatiTypography