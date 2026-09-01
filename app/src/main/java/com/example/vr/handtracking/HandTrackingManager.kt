package com.example.vr.handtracking

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.vr.model.HandGesture
import com.example.vr.model.HandTrackingSource
import com.example.vr.model.Ray3D
import com.example.vr.model.TrackedHand
import com.example.vr.model.Vector3
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executors

class HandTrackingManager(private val context: Context) {

    private val _rightHand = MutableStateFlow(
        createDefaultHand(isRight = true)
    )
    val rightHand: StateFlow<TrackedHand> = _rightHand.asStateFlow()

    private val _leftHand = MutableStateFlow(
        createDefaultHand(isRight = false)
    )
    val leftHand: StateFlow<TrackedHand> = _leftHand.asStateFlow()

    companion object {
        fun createDefaultHand(isRight: Boolean): TrackedHand {
            val posX = if (isRight) 0.35f else -0.35f
            val posY = -0.22f
            val posZ = 1.2f

            val wristPos = Vector3(posX, posY - 0.16f, posZ + 0.06f)
            val thumbTipPos = Vector3(posX + (if (isRight) -0.06f else 0.06f), posY + 0.05f, posZ - 0.03f)
            val indexTipPos = Vector3(posX, posY + 0.12f, posZ - 0.06f)
            val middleTipPos = Vector3(posX + (if (isRight) 0.03f else -0.03f), posY + 0.14f, posZ - 0.07f)
            val ringTipPos = Vector3(posX + (if (isRight) 0.06f else -0.06f), posY + 0.11f, posZ - 0.05f)
            val pinkyTipPos = Vector3(posX + (if (isRight) 0.085f else -0.085f), posY + 0.06f, posZ - 0.03f)

            val contour = listOf(
                wristPos,
                Vector3(posX + (if (isRight) -0.07f else 0.07f), posY - 0.04f, posZ),
                thumbTipPos,
                Vector3(posX + (if (isRight) -0.02f else 0.02f), posY + 0.06f, posZ),
                indexTipPos,
                Vector3(posX + (if (isRight) 0.015f else -0.015f), posY + 0.09f, posZ),
                middleTipPos,
                Vector3(posX + (if (isRight) 0.045f else -0.045f), posY + 0.08f, posZ),
                ringTipPos,
                Vector3(posX + (if (isRight) 0.075f else -0.075f), posY + 0.05f, posZ),
                pinkyTipPos,
                Vector3(posX + (if (isRight) 0.075f else -0.075f), posY - 0.07f, posZ),
                wristPos
            )

            return TrackedHand(
                isTracked = true,
                isLeft = !isRight,
                position = Vector3(posX, posY, posZ),
                wristPosition = wristPos,
                indexTip = indexTipPos,
                thumbTip = thumbTipPos,
                middleTip = middleTipPos,
                ringTip = ringTipPos,
                pinkyTip = pinkyTipPos,
                contourPoints = contour,
                laserRay = Ray3D(Vector3(posX, posY, posZ), Vector3(posX * 0.4f, posY * 0.4f, 1f).normalized()),
                gesture = HandGesture.OPEN_PALM
            )
        }
    }

    private val _trackingSource = MutableStateFlow(HandTrackingSource.CAMERA_AI)
    val trackingSource: StateFlow<HandTrackingSource> = _trackingSource.asStateFlow()

    private val _useFrontCamera = MutableStateFlow(false)
    val useFrontCamera: StateFlow<Boolean> = _useFrontCamera.asStateFlow()

    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    fun setTrackingSource(source: HandTrackingSource) {
        _trackingSource.value = source
    }

    fun toggleCameraLens() {
        _useFrontCamera.value = !_useFrontCamera.value
    }

    /**
     * Updates hand position and gesture via on-screen virtual hand touch controls (Simulator mode)
     */
    fun updateVirtualHand(
        isRight: Boolean,
        normX: Float, // -1f to 1f
        normY: Float, // -1f to 1f
        gesture: HandGesture,
        isPinching: Boolean
    ) {
        val posX = normX * 0.8f + (if (isRight) 0.25f else -0.25f)
        val posY = normY * 0.6f - 0.1f
        val posZ = if (isPinching) 1.0f else 1.2f

        val handPos = Vector3(posX, posY, posZ)
        val rayDir = Vector3(posX * 0.5f, posY * 0.5f, 1.0f).normalized()

        val indexTipPos = Vector3(posX, posY + 0.12f, posZ - 0.08f)
        val thumbTipPos = Vector3(posX + (if (isRight) -0.06f else 0.06f), posY + 0.06f, posZ - 0.04f)
        val middleTipPos = Vector3(posX + (if (isRight) 0.03f else -0.03f), posY + 0.14f, posZ - 0.09f)
        val ringTipPos = Vector3(posX + (if (isRight) 0.06f else -0.06f), posY + 0.11f, posZ - 0.07f)
        val pinkyTipPos = Vector3(posX + (if (isRight) 0.09f else -0.09f), posY + 0.06f, posZ - 0.04f)
        val wristPos = Vector3(posX, posY - 0.18f, posZ + 0.08f)

        // Generate synthetic hand outline contour polygon
        val contour = listOf(
            wristPos,
            Vector3(posX + (if (isRight) -0.08f else 0.08f), posY - 0.05f, posZ),
            thumbTipPos,
            Vector3(posX + (if (isRight) -0.02f else 0.02f), posY + 0.06f, posZ),
            indexTipPos,
            Vector3(posX + (if (isRight) 0.015f else -0.015f), posY + 0.09f, posZ),
            middleTipPos,
            Vector3(posX + (if (isRight) 0.045f else -0.045f), posY + 0.08f, posZ),
            ringTipPos,
            Vector3(posX + (if (isRight) 0.075f else -0.075f), posY + 0.05f, posZ),
            pinkyTipPos,
            Vector3(posX + (if (isRight) 0.08f else -0.08f), posY - 0.08f, posZ),
            wristPos
        )

        val updated = TrackedHand(
            isTracked = true,
            isLeft = !isRight,
            position = handPos,
            wristPosition = wristPos,
            indexTip = indexTipPos,
            thumbTip = thumbTipPos,
            middleTip = middleTipPos,
            ringTip = ringTipPos,
            pinkyTip = pinkyTipPos,
            contourPoints = contour,
            gesture = gesture,
            confidence = 1.0f,
            laserRay = Ray3D(handPos, rayDir),
            isPinching = isPinching,
            isGrabbing = gesture == HandGesture.FIST || isPinching,
            color = if (isRight) 0xFF00E5FF else 0xFFFF0077
        )

        if (isRight) {
            _rightHand.value = updated
        } else {
            _leftHand.value = updated
        }
    }

    /**
     * Start CameraX analyzer with lifecycle
     */
    fun startCameraTracking(lifecycleOwner: LifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraAnalysis(lifecycleOwner)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun bindCameraAnalysis(lifecycleOwner: LifecycleOwner) {
        val provider = cameraProvider ?: return
        try {
            provider.unbindAll()

            val cameraSelector = if (_useFrontCamera.value) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                .build()

            imageAnalysis.setAnalyzer(cameraExecutor, HandTrackerAnalyzer { hand ->
                if (_trackingSource.value == HandTrackingSource.CAMERA_AI) {
                    _rightHand.value = hand
                }
            })

            provider.bindToLifecycle(lifecycleOwner, cameraSelector, imageAnalysis)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
}
