package io.github.sulfuro25.salati.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object SalatiShapeTokens {
    val Small = RoundedCornerShape(8.dp)
    val Control = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(20.dp)
    val Hero = RoundedCornerShape(24.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

val SalatiShapes = Shapes(
    extraSmall = SalatiShapeTokens.Small,
    small = SalatiShapeTokens.Control,
    medium = SalatiShapeTokens.Card,
    large = SalatiShapeTokens.Hero,
    extraLarge = SalatiShapeTokens.Hero
)