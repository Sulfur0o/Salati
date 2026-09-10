package com.sulfuro.salati.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sulfuro.salati.R

/**
 * Salati's mark, as it actually is.
 *
 * This was drawn with Canvas for a while so it could take its colours from the theme. That
 * was a mistake: the real logo is frosted glass, a three-dimensional gold crescent and a
 * green edge glow, and a couple of `drawRoundRect` calls only ever produced a flat cartoon
 * of it. Recognisable branding beats a mark that recolours, so the artwork is used.
 *
 * It is cut from the 512px store icon, whose slate backdrop is not part of the mark, and
 * masked to the tile's own rounded corners. The tile carries its own dark ground, so it
 * sits correctly on the light theme's cream and the dark theme's near-black alike without
 * needing two files.
 *
 * A genuinely light variant would need light artwork, which does not exist: the current
 * source has its dark ground baked into the pixels rather than layered behind them.
 */
@Composable
fun SalatiLogo(
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 96.dp
) {
    Image(
        painter = painterResource(R.drawable.salati_logo),
        contentDescription = null,
        modifier = modifier
            .size(size)
            // One description on the image itself; the painter contributes none of its own.
            .semantics { this.contentDescription = contentDescription }
    )
}
