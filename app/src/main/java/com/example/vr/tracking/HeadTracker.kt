package com.example.vr.tracking

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.Matrix
import android.view.Surface
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

data class HeadOrientation(
    val pitch: Float = 0f,
    val yaw: Float = 0f,
    val roll: Float = 0f,
    val pitchDeg: Float = 0f,
    val yawDeg: Float = 0f,
    val rollDeg: Float = 0f,
    val viewMatrix: FloatArray = FloatArray(16) { if (it % 5 == 0) 1f else 0f }
)

class HeadTracker(private val context: Context) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        ?: sensorManager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    
    private val _orientation = MutableStateFlow(HeadOrientation())
    val orientation: StateFlow<HeadOrientation> = _orientation.asStateFlow()

    private var isSensorEnabled = true
    private var isRegistered = false

    private var yawOffset = 0f
    private var manualYaw = 0f
    private var manualPitch = 0f

    private var currentPitch = 0f
    private var currentYaw = 0f
    private var currentRoll = 0f

    private val rotationMatrix = FloatArray(16)
    private val remappedMatrix = FloatArray(16)
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

    fun recenter() {
        yawOffset = currentYaw
        manualYaw = 0f
        manualPitch = 0f
        updateOrientationState()
    }

    fun onDrag(deltaX: Float, deltaY: Float, sensitivity: Float = 0.003f) {
        manualYaw += deltaX * sensitivity
        manualPitch = (manualPitch - deltaY * sensitivity).coerceIn(-1.4f, 1.4f)
        updateOrientationState()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isSensorEnabled) return

        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR || event.sensor.type == Sensor.TYPE_GAME_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val rotation = windowManager.defaultDisplay.rotation

            var axisX = SensorManager.AXIS_X
            var axisY = SensorManager.AXIS_Y

            when (rotation) {
                Surface.ROTATION_90 -> {
                    axisX = SensorManager.AXIS_Y
                    axisY = SensorManager.AXIS_MINUS_X
                }
                Surface.ROTATION_270 -> {
                    axisX = SensorManager.AXIS_MINUS_Y
                    axisY = SensorManager.AXIS_X
                }
                Surface.ROTATION_180 -> {
                    axisX = SensorManager.AXIS_MINUS_X
                    axisY = SensorManager.AXIS_MINUS_Y
                }
                else -> {
                    axisX = SensorManager.AXIS_X
                    axisY = SensorManager.AXIS_Y
                }
            }

            SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)
            SensorManager.getOrientation(remappedMatrix, orientationValues)

            val rawYaw = orientationValues[0]
            val rawPitch = -orientationValues[1]
            val rawRoll = orientationValues[2]

            if (isFirstReading) {
                currentYaw = rawYaw
                currentPitch = rawPitch
                currentRoll = rawRoll
                yawOffset = rawYaw
                isFirstReading = false
            } else {
                var diffYaw = rawYaw - currentYaw
                while (diffYaw < -Math.PI) diffYaw += (2 * Math.PI).toFloat()
                while (diffYaw > Math.PI) diffYaw -= (2 * Math.PI).toFloat()
                
                currentYaw += diffYaw
                currentPitch = rawPitch
                currentRoll = rawRoll
            }
            updateOrientationState()
        }
    }

    private fun updateOrientationState() {
        val finalYaw = (currentYaw - yawOffset) + manualYaw
        val finalPitch = currentPitch + manualPitch
        val finalRoll = currentRoll
        
        val viewMatrix = FloatArray(16)
        Matrix.setIdentityM(viewMatrix, 0)
        
        Matrix.rotateM(viewMatrix, 0, Math.toDegrees(-finalRoll.toDouble()).toFloat(), 0f, 0f, 1f)
        Matrix.rotateM(viewMatrix, 0, Math.toDegrees(finalPitch.toDouble()).toFloat(), 1f, 0f, 0f)
        Matrix.rotateM(viewMatrix, 0, Math.toDegrees(-finalYaw.toDouble()).toFloat(), 0f, 1f, 0f)

        _orientation.value = HeadOrientation(
            pitch = finalPitch,
            yaw = finalYaw,
            roll = finalRoll,
            pitchDeg = Math.toDegrees(finalPitch.toDouble()).toFloat(),
            yawDeg = Math.toDegrees(finalYaw.toDouble()).toFloat(),
            rollDeg = Math.toDegrees(finalRoll.toDouble()).toFloat(),
            viewMatrix = viewMatrix
        )
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
