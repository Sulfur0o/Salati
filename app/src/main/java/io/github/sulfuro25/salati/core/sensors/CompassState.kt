package io.github.sulfuro25.salati.core.sensors

import android.content.Context
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.sulfuro25.salati.core.computation.QiblaCalculator

/**
 * Beyond this pitch the phone is considered to be held upright rather than flat, and the
 * heading is taken from the screen normal instead of the top edge.
 */
private const val UPRIGHT_PITCH_THRESHOLD_DEGREES = 30.0

private fun currentDisplayRotation(context: Context): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching { context.display.rotation }.getOrNull() ?: Surface.ROTATION_0
    } else {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)
            ?.defaultDisplay?.rotation ?: Surface.ROTATION_0
    }
}

enum class CompassAccuracy {
    /** Device has no usable orientation sensor at all. */
    UNAVAILABLE,

    /** Readings are arriving but the magnetometer needs calibration. */
    NEEDS_CALIBRATION,
    MEDIUM,
    HIGH
}

data class CompassReading(
    /** Device heading in degrees clockwise from **true** north, or null before the first fix. */
    val trueHeadingDegrees: Float?,
    val accuracy: CompassAccuracy
) {
    val isAvailable: Boolean get() = accuracy != CompassAccuracy.UNAVAILABLE
}

/**
 * Observes the device's orientation while the composable is resumed.
 *
 * Two things matter for a Qibla compass that a naive implementation gets wrong:
 *
 *  1. The sensor reports heading relative to *magnetic* north, while the Qibla bearing is
 *     relative to *true* north. The difference (magnetic declination) is only ~2° in
 *     Belgium but exceeds 15° in parts of the world, which is more than enough to matter.
 *     [latitude]/[longitude] are used to correct for it.
 *  2. Raw readings jitter by several degrees, so they are smoothed with an angle-aware
 *     filter that takes the short way around the dial rather than spinning through 359°.
 *
 * The listener is registered only while the lifecycle is at least STARTED, so the
 * magnetometer is never left running in the background.
 */
@Composable
fun rememberCompassReading(
    latitude: Double,
    longitude: Double,
    smoothingFactor: Float = 0.15f
): State<CompassReading> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val readingState = remember {
        mutableStateOf(CompassReading(trueHeadingDegrees = null, accuracy = CompassAccuracy.MEDIUM))
    }

    DisposableEffect(lifecycleOwner, latitude, longitude) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotationSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)

        if (sensorManager == null || rotationSensor == null) {
            readingState.value = CompassReading(null, CompassAccuracy.UNAVAILABLE)
            return@DisposableEffect onDispose { }
        }

        // Declination converts magnetic north to true north for this position.
        val declination = GeomagneticField(
            latitude.toFloat(),
            longitude.toFloat(),
            0f,
            System.currentTimeMillis()
        ).declination

        val rotationMatrix = FloatArray(9)
        val displayAdjusted = FloatArray(9)
        val tiltAdjusted = FloatArray(9)
        val orientation = FloatArray(3)
        var smoothedHeading: Float? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Some devices report a 5-element rotation vector; getRotationMatrixFromVector
                // only accepts up to 4 and throws on the longer form.
                val vector = if (event.values.size > 4) {
                    event.values.copyOfRange(0, 4)
                } else {
                    event.values
                }
                SensorManager.getRotationMatrixFromVector(rotationMatrix, vector)

                // Sensor axes are fixed to the device's *natural* orientation, so a rotated
                // display has to be compensated for or the heading is off by the screen rotation.
                val (axisX, axisY) = when (currentDisplayRotation(context)) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, displayAdjusted)
                SensorManager.getOrientation(displayAdjusted, orientation)

                // getOrientation's azimuth describes where the phone's top edge points, which
                // only means "the direction you're facing" while the phone is flat. Once it is
                // tilted upright that axis points at the sky and the azimuth degenerates, so the
                // frame is remapped to use the screen normal (the way the back of the phone
                // faces) instead. Without this the compass reads wildly wrong when held normally.
                val pitchDegrees = Math.toDegrees(orientation[1].toDouble())
                if (kotlin.math.abs(pitchDegrees) > UPRIGHT_PITCH_THRESHOLD_DEGREES) {
                    val axisZ = if (pitchDegrees < 0) SensorManager.AXIS_MINUS_Z else SensorManager.AXIS_Z
                    SensorManager.remapCoordinateSystem(
                        displayAdjusted,
                        SensorManager.AXIS_X,
                        axisZ,
                        tiltAdjusted
                    )
                    SensorManager.getOrientation(tiltAdjusted, orientation)
                }

                val magneticHeading = Math.toDegrees(orientation[0].toDouble()).toFloat()
                val trueHeading = QiblaCalculator
                    .normalizeDegrees((magneticHeading + declination).toDouble())
                    .toFloat()

                // Angle-aware low-pass filter: step a fraction of the *shortest* rotation so
                // the needle never sweeps the long way when the heading crosses north.
                val previous = smoothedHeading
                val next = if (previous == null) {
                    trueHeading
                } else {
                    QiblaCalculator.normalizeDegrees(
                        (previous + QiblaCalculator.shortestRotation(previous, trueHeading) * smoothingFactor)
                            .toDouble()
                    ).toFloat()
                }
                smoothedHeading = next

                readingState.value = readingState.value.copy(trueHeadingDegrees = next)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                val mapped = when (accuracy) {
                    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> CompassAccuracy.HIGH
                    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> CompassAccuracy.MEDIUM
                    else -> CompassAccuracy.NEEDS_CALIBRATION
                }
                readingState.value = readingState.value.copy(accuracy = mapped)
            }
        }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> sensorManager.registerListener(
                    listener,
                    rotationSensor,
                    SensorManager.SENSOR_DELAY_GAME
                )
                Lifecycle.Event.ON_STOP -> sensorManager.unregisterListener(listener)
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensorManager.unregisterListener(listener)
        }
    }

    return readingState
}
