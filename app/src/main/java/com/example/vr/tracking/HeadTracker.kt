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

    // Smoothing filter variables (Low-pass filter with anti-jitter damping)
    private var currentPitch = 0f
    private var currentYaw = 0f
    private var currentRoll = 0f
    private val smoothingFactor = 0.18f // Gentle low-pass filter for rock-solid stability
    private val gyroSensitivity = 0.85f // Calibrated sensitivity to avoid over-sensitive twitching

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientationValues = FloatArray(3)

    private var isFirstReading = true

    fun start() {
        if (!isRegistered && rotationSensor != null) {
            sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME)
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
        updateOrientationState()
    }

    /**
     * Handle touch drag look-around for 2D flat mode or emulator
     */
    fun onDrag(deltaX: Float, deltaY: Float, sensitivity: Float = 0.003f) {
        manualYaw += deltaX * sensitivity
        manualPitch = (manualPitch + deltaY * sensitivity).coerceIn(-1.4f, 1.4f)
        updateOrientationState()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isSensorEnabled) return

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR || event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

            // In Landscape mode (phone sideways in VR headset), remap coordinate axes:
            SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_Y,
                SensorManager.AXIS_MINUS_X,
                remappedMatrix
            )

            SensorManager.getOrientation(remappedMatrix, orientationValues)

            // Invert rawYaw sign so turning head right turns view right (fixes inverted left-right)
            val rawYaw = -orientationValues[0]
            val rawPitch = orientationValues[1]
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
                // Shortest angular difference for yaw to prevent 360-degree snap jumps
                var diffYaw = rawYaw - currentYaw
                while (diffYaw < -Math.PI) diffYaw += (2 * Math.PI).toFloat()
                while (diffYaw > Math.PI) diffYaw -= (2 * Math.PI).toFloat()

                currentYaw += diffYaw * smoothingFactor
                currentPitch += (rawPitch - currentPitch) * smoothingFactor
                currentRoll += (rawRoll - currentRoll) * smoothingFactor
            }

            updateOrientationState()
        } else if (event.sensor.type == Sensor.TYPE_ORIENTATION) {
            val rawYaw = -Math.toRadians(event.values[0].toDouble()).toFloat()
            val rawPitch = Math.toRadians(event.values[1].toDouble()).toFloat()
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
                var diffYaw = rawYaw - currentYaw
                while (diffYaw < -Math.PI) diffYaw += (2 * Math.PI).toFloat()
                while (diffYaw > Math.PI) diffYaw -= (2 * Math.PI).toFloat()

                currentYaw += diffYaw * smoothingFactor
                currentPitch += (rawPitch - currentPitch) * smoothingFactor
                currentRoll += (rawRoll - currentRoll) * smoothingFactor
            }

            updateOrientationState()
        }
    }

    private fun updateOrientationState() {
        val finalYaw = (currentYaw - yawOffset) * gyroSensitivity + manualYaw
        val finalPitch = ((currentPitch - pitchOffset) * gyroSensitivity + manualPitch).coerceIn(-1.5f, 1.5f)
        val finalRoll = (currentRoll - rollOffset) * gyroSensitivity

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
