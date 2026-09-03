package io.github.sulfuro25.salati.ui.qibla

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.sulfuro25.salati.R
import io.github.sulfuro25.salati.core.computation.QiblaCalculator
import io.github.sulfuro25.salati.core.sensors.CompassAccuracy
import io.github.sulfuro25.salati.core.sensors.rememberCompassReading
import io.github.sulfuro25.salati.data.settings.CalculationSettings
import io.github.sulfuro25.salati.theme.SalatiShapeTokens
import io.github.sulfuro25.salati.theme.SalatiSpacing
import io.github.sulfuro25.salati.ui.components.StatusPill
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val CardinalDirections = listOf("N" to 0f, "E" to 90f, "S" to 180f, "W" to 270f)

@Composable
fun QiblaScreen(
    settings: CalculationSettings,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val qiblaBearing = remember(settings.latitude, settings.longitude) {
        QiblaCalculator.bearingToKaaba(settings.latitude, settings.longitude)
    }
    val compass by rememberCompassReading(settings.latitude, settings.longitude)
    val heading = compass.trueHeadingDegrees

    val isAligned = heading != null && QiblaCalculator.isAligned(heading, qiblaBearing)

    // Continuous angle accumulation ensures animateFloatAsState takes the shortest arc
    // when crossing True North (0° / 360°) rather than spinning 358° around the dial.
    var continuousTargetAngle by remember {
        mutableFloatStateOf(if (heading != null) -heading else 0f)
    }
    LaunchedEffect(heading) {
        if (heading != null) {
            val target = -heading
            val diff = QiblaCalculator.shortestRotation(continuousTargetAngle, target)
            continuousTargetAngle += diff
        }
    }

    val animatedRoseRotation = animateFloatAsState(
        targetValue = continuousTargetAngle,
        label = "compassRoseRotation"
    )

    val bearingText = String.format(Locale.US, "%.0f°", qiblaBearing)
    val compassPoint = remember(qiblaBearing) { QiblaCalculator.compassPointFor(qiblaBearing) }
    val screenDescription = stringResource(
        R.string.qibla_accessibility,
        bearingText,
        compassPoint,
        settings.cityName
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = SalatiSpacing.md),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.qibla_back),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = stringResource(R.string.qibla_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { heading() }
            )
        }

        Text(
            text = settings.cityName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(SalatiSpacing.lg))

        val roseColor = MaterialTheme.colorScheme.outline
        val markerColor = if (isAligned) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.secondary
        }
        val cardinalColor = MaterialTheme.colorScheme.onSurfaceVariant
        val northColor = MaterialTheme.colorScheme.error
        val pointerColor = MaterialTheme.colorScheme.primary

        val northPaint = remember(northColor) {
            android.graphics.Paint().apply {
                color = northColor.toArgb()
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                isFakeBoldText = true
            }
        }
        val cardinalPaint = remember(cardinalColor) {
            android.graphics.Paint().apply {
                color = cardinalColor.toArgb()
                textAlign = android.graphics.Paint.Align.CENTER
                isAntiAlias = true
                isFakeBoldText = false
            }
        }
        val pointerPath = remember { Path() }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .semantics { contentDescription = screenDescription },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        rotationZ = if (heading != null) animatedRoseRotation.value else 0f
                    }
            ) {
                drawCompassRose(
                    qiblaBearing = qiblaBearing.toFloat(),
                    roseColor = roseColor,
                    northPaint = northPaint,
                    cardinalPaint = cardinalPaint,
                    markerColor = markerColor,
                    labelCounterRotationDegrees = if (heading != null) {
                        animatedRoseRotation.value
                    } else {
                        0f
                    }
                )
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawFixedCompassOverlay(
                    roseColor = roseColor,
                    pointerColor = pointerColor,
                    pointerPath = pointerPath
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = bearingText,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = compassPoint,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(SalatiSpacing.lg))

        when {
            !compass.isAvailable -> {
                StatusPill(
                    text = stringResource(R.string.qibla_no_sensor),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(SalatiSpacing.sm))
                Text(
                    text = stringResource(R.string.qibla_no_sensor_hint, bearingText, compassPoint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            isAligned -> StatusPill(
                text = stringResource(R.string.qibla_aligned),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
            else -> StatusPill(
                text = stringResource(R.string.qibla_turn_to_align),
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (compass.accuracy == CompassAccuracy.NEEDS_CALIBRATION) {
            Spacer(modifier = Modifier.height(SalatiSpacing.sm))
            Surface(
                shape = SalatiShapeTokens.Card,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Text(
                    text = stringResource(R.string.qibla_calibrate),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(SalatiSpacing.sm)
                )
            }
        }

        Spacer(modifier = Modifier.height(SalatiSpacing.md))
        Text(
            text = stringResource(R.string.qibla_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(SalatiSpacing.xl))
    }
}

private fun DrawScope.drawCompassRose(
    qiblaBearing: Float,
    roseColor: Color,
    northPaint: android.graphics.Paint,
    cardinalPaint: android.graphics.Paint,
    markerColor: Color,
    labelCounterRotationDegrees: Float
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = min(size.width, size.height) / 2f * 0.86f

    for (tick in 0 until 72) {
        val angle = Math.toRadians((tick * 5).toDouble())
        val isMajor = tick % 9 == 0
        val tickLength = if (isMajor) 14.dp.toPx() else 7.dp.toPx()
        val start = Offset(
            center.x + (radius - tickLength) * sin(angle).toFloat(),
            center.y - (radius - tickLength) * cos(angle).toFloat()
        )
        val end = Offset(
            center.x + radius * sin(angle).toFloat(),
            center.y - radius * cos(angle).toFloat()
        )
        drawLine(
            color = roseColor.copy(alpha = if (isMajor) 0.7f else 0.3f),
            start = start,
            end = end,
            strokeWidth = if (isMajor) 2.5f.dp.toPx() else 1.dp.toPx()
        )
    }

    CardinalDirections.forEach { (label, bearing) ->
        val angle = Math.toRadians(bearing.toDouble())
        val labelRadius = radius - 34.dp.toPx()
        val x = center.x + labelRadius * sin(angle).toFloat()
        val y = center.y - labelRadius * cos(angle).toFloat()
        val paint = if (label == "N") northPaint else cardinalPaint
        paint.textSize = 17.dp.toPx()
        drawContext.canvas.nativeCanvas.apply {
            // The rose rotates as a graphics layer; counter-rotate glyphs so they stay upright.
            save()
            rotate(-labelCounterRotationDegrees, x, y)
            drawText(label, x, y + paint.textSize / 3f, paint)
            restore()
        }
    }

    // Kaaba marker sits at the Qibla bearing within the rose.
    val qiblaAngle = Math.toRadians(qiblaBearing.toDouble())
    val markerRadius = radius * 0.78f
    val markerCenter = Offset(
        center.x + markerRadius * sin(qiblaAngle).toFloat(),
        center.y - markerRadius * cos(qiblaAngle).toFloat()
    )
    drawLine(
        color = markerColor,
        start = center,
        end = markerCenter,
        strokeWidth = 5.dp.toPx()
    )
    drawCircle(color = markerColor, radius = 15.dp.toPx(), center = markerCenter)
    drawCircle(
        color = markerColor,
        radius = 22.dp.toPx(),
        center = markerCenter,
        style = Stroke(width = 2.dp.toPx())
    )
}

private fun DrawScope.drawFixedCompassOverlay(
    roseColor: Color,
    pointerColor: Color,
    pointerPath: Path
) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val radius = min(size.width, size.height) / 2f * 0.86f

    drawCircle(color = roseColor.copy(alpha = 0.35f), radius = radius, center = center, style = Stroke(width = 2.dp.toPx()))
    drawCircle(color = roseColor.copy(alpha = 0.18f), radius = radius * 0.72f, center = center, style = Stroke(width = 1.dp.toPx()))

    // Fixed pointer at the top: the direction the device is currently facing.
    pointerPath.reset()
    pointerPath.moveTo(center.x, center.y - radius - 10.dp.toPx())
    pointerPath.lineTo(center.x - 9.dp.toPx(), center.y - radius + 10.dp.toPx())
    pointerPath.lineTo(center.x + 9.dp.toPx(), center.y - radius + 10.dp.toPx())
    pointerPath.close()
    drawPath(pointerPath, color = pointerColor)

    drawCircle(color = pointerColor, radius = 6.dp.toPx(), center = center)
}

private fun Color.toArgb(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt()
)
