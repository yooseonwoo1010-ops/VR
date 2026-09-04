package com.example.vr.tracking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class MediaPipeHandTracker(
    val context: Context,
    val onHandTrackingResult: (HandLandmarkerResult) -> Unit
) {
    private var handLandmarker: HandLandmarker? = null

    init {
        setupHandLandmarker()
    }

    private fun setupHandLandmarker() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .build()

        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinHandDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setMinHandPresenceConfidence(0.5f)
            .setNumHands(2)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ ->
                onHandTrackingResult(result)
            }
            .setErrorListener { error ->
                error.printStackTrace()
            }
            .build()

        try {
            handLandmarker = HandLandmarker.createFromOptions(context, options)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun processImageProxy(imageProxy: ImageProxy, isFrontCamera: Boolean) {
        val handLandmarker = handLandmarker ?: run { imageProxy.close(); return }
        
        val bitmap = imageProxy.toBitmap()
        
        // Rotate and flip bitmap to match camera orientation and front camera mirroring
        val matrix = Matrix()
        matrix.postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
        if (isFrontCamera) {
            matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
        }
        
        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        
        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        val frameTime = SystemClock.uptimeMillis()
        
        try {
            handLandmarker.detectAsync(mpImage, frameTime)
        } catch (e: Throwable) {
            e.printStackTrace()
        } finally {
            imageProxy.close()
        }
    }

    fun close() {
        handLandmarker?.close()
        handLandmarker = null
    }
}
