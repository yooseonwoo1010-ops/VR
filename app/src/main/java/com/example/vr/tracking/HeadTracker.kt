package com.example.vr.tracking

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

data class HeadOrientation(
    val pitch: Float = 0f, // Looking Up (+) / Down (-) in radians
    val yaw: Float = 0f,   // Looking Left (-) / Right (+) in radians
    val roll: Float = 0f,  // Head Tilt in radians
    val pitchDeg: Float = 0f,
    val yawDeg: Float = 0f,
    val rollDeg: Float = 0f
)

/**
 * 1-Euro Filter for rock-solid IMU tracking without jitter or lag (Meta Quest standard).
 */
class OneEuroFilter(
    private val minCutoff: Float = 1.0f,
    private val beta: Float = 0.007f,
    private val dCutoff: Float = 1.0f
) {
    private var xPrev: Float? = null
    private var dxPrev = 0f
    private var tPrev: Long = 0L

    private fun alpha(rate: Float, cutoff: Float): Float {
        val tau = 1.0f / (2f * Math.PI.toFloat() * cutoff)
        val te = 1.0f / rate
        return 1.0f / (1.0f + tau / te)
    }

    fun filter(x: Float, timestampNs: Long): Float {
        if (xPrev == null) {
            xPrev = x
            tPrev = timestampNs
            return x
        }

        val dt = ((timestampNs - tPrev) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
        tPrev = timestampNs
        val rate = 1.0f / dt

        // Estimate derivative (speed)
        val dx = (x - xPrev!!) * rate
        val aD = alpha(rate, dCutoff)
        val dxHat = aD * dx + (1f - aD) * dxPrev
        dxPrev = dxHat

        // Adaptive cutoff frequency based on speed
        val cutoff = minCutoff + beta * abs(dxHat)
        val a = alpha(rate, cutoff)
        val xHat = a * x + (1f - a) * xPrev!!
        xPrev = xHat
        return xHat
    }

    fun reset() {
        xPrev = null
        dxPrev = 0f
    }
}

class HeadTracker(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION)

    private val _orientation = MutableStateFlow(HeadOrientation())
    val orientation: StateFlow<HeadOrientation> = _orientation.asStateFlow()

    private var isSensorEnabled = true
    private var isRegistered = false

    // Offset for recentering
    private var yawOffset = 0f
    private var pitchOffset = 0f
    private var rollOffset = 0f

    // Touch drag offsets for manual flat-mode control
    private var manualYaw = 0f
    private var manualPitch = 0f

    // 1-Euro Filters for Yaw, Pitch, Roll tuned for silky smooth, jitter-free spatial stability
    private val yawFilter = OneEuroFilter(minCutoff = 0.40f, beta = 0.002f, dCutoff = 1.0f)
    private val pitchFilter = OneEuroFilter(minCutoff = 0.40f, beta = 0.002f, dCutoff = 1.0f)
    private val rollFilter = OneEuroFilter(minCutoff = 0.40f, beta = 0.002f, dCutoff = 1.0f)

    private var currentPitch = 0f
    private var currentYaw = 0f
    private var currentRoll = 0f

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientationValues = FloatArray(3)

    private var isFirstReading = true

    fun start() {
        if (!isRegistered && rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_FASTEST)
            isRegistered = true
        }
    }

    fun stop() {
        if (isRegistered) {
            sensorManager.unregisterListener(this)
            isRegistered = false
        }
    }

    fun setSensorEnabled(enabled: Boolean) {
        isSensorEnabled = enabled
    }

    /**
     * Recalibrate center forward direction (Quest-like recenter button)
     */
    fun recenter() {
        yawOffset = currentYaw
        pitchOffset = currentPitch
        rollOffset = currentRoll
        manualYaw = 0f
        manualPitch = 0f
        yawFilter.reset()
        pitchFilter.reset()
        rollFilter.reset()
        updateOrientationState()
    }

    /**
     * Handle touch drag look-around for 2D flat mode or emulator
     */
    fun onDrag(deltaX: Float, deltaY: Float, sensitivity: Float = 0.003f) {
        manualYaw += deltaX * sensitivity
        manualPitch = (manualPitch - deltaY * sensitivity).coerceIn(-1.4f, 1.4f)
        updateOrientationState()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isSensorEnabled) return

        val timestamp = event.timestamp

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR || event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

            // Landscape mode remapping for VR headset (phone landscape in headset):
            SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_Y,
                SensorManager.AXIS_MINUS_X,
                remappedMatrix
            )

            SensorManager.getOrientation(remappedMatrix, orientationValues)

            // Sensor coordinate conversion:
            // - Turning head right -> rawYaw increases (+)
            // - Tilting head up -> rawPitch increases (+)
            // - Tilting head clockwise (right) -> rawRoll increases (+)
            val rawYaw = orientationValues[0]
            val rawPitch = -orientationValues[1]
            val rawRoll = orientationValues[2]

            if (isFirstReading) {
                currentYaw = rawYaw
                currentPitch = rawPitch
                currentRoll = rawRoll
                yawOffset = rawYaw
                pitchOffset = rawPitch
                rollOffset = rawRoll
                isFirstReading = false
            } else {
                // Shortest angular difference for yaw unwrapping
                var unwrapYaw = rawYaw
                var diffYaw = unwrapYaw - currentYaw
                while (diffYaw < -Math.PI) {
                    unwrapYaw += (2 * Math.PI).toFloat()
                    diffYaw = unwrapYaw - currentYaw
                }
                while (diffYaw > Math.PI) {
                    unwrapYaw -= (2 * Math.PI).toFloat()
                    diffYaw = unwrapYaw - currentYaw
                }

                currentYaw = yawFilter.filter(unwrapYaw, timestamp)
                currentPitch = pitchFilter.filter(rawPitch, timestamp)
                currentRoll = rollFilter.filter(rawRoll, timestamp)
            }

            updateOrientationState()
        } else if (event.sensor.type == Sensor.TYPE_ORIENTATION) {
            val rawYaw = Math.toRadians(event.values[0].toDouble()).toFloat()
            val rawPitch = -Math.toRadians(event.values[1].toDouble()).toFloat()
            val rawRoll = Math.toRadians(event.values[2].toDouble()).toFloat()

            if (isFirstReading) {
                currentYaw = rawYaw
                currentPitch = rawPitch
                currentRoll = rawRoll
                yawOffset = rawYaw
                pitchOffset = rawPitch
                rollOffset = rawRoll
                isFirstReading = false
            } else {
                var unwrapYaw = rawYaw
                var diffYaw = unwrapYaw - currentYaw
                while (diffYaw < -Math.PI) {
                    unwrapYaw += (2 * Math.PI).toFloat()
                    diffYaw = unwrapYaw - currentYaw
                }
                while (diffYaw > Math.PI) {
                    unwrapYaw -= (2 * Math.PI).toFloat()
                    diffYaw = unwrapYaw - currentYaw
                }

                currentYaw = yawFilter.filter(unwrapYaw, timestamp)
                currentPitch = pitchFilter.filter(rawPitch, timestamp)
                currentRoll = rollFilter.filter(rawRoll, timestamp)
            }

            updateOrientationState()
        }
    }

    private fun updateOrientationState() {
        val finalYaw = (currentYaw - yawOffset) + manualYaw
        val finalPitch = ((currentPitch - pitchOffset) + manualPitch).coerceIn(-1.5f, 1.5f)
        val finalRoll = (currentRoll - rollOffset)

        _orientation.value = HeadOrientation(
            pitch = finalPitch,
            yaw = finalYaw,
            roll = finalRoll,
            pitchDeg = Math.toDegrees(finalPitch.toDouble()).toFloat(),
            yawDeg = Math.toDegrees(finalYaw.toDouble()).toFloat(),
            rollDeg = Math.toDegrees(finalRoll.toDouble()).toFloat()
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}

