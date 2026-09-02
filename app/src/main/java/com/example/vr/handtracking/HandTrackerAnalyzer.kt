package com.example.vr.handtracking

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.vr.model.HandGesture
import com.example.vr.model.Ray3D
import com.example.vr.model.TrackedHand
import com.example.vr.model.Vector3
import java.nio.ByteBuffer
import kotlin.math.*

class HandTrackerAnalyzer(
    private val onHandTracked: (TrackedHand) -> Unit
) : ImageAnalysis.Analyzer {

    private var frameCount = 0
    private var lastTrackedHand = HandTrackingManager.createDefaultHand(isRight = true)
    private var isCurrentlyTracked = true
    private var lostFramesCount = 0

    override fun analyze(image: ImageProxy) {
        frameCount++
        // Process every frame for high responsiveness
        try {
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]

            val yBuffer: ByteBuffer = yPlane.buffer
            val uBuffer: ByteBuffer = uPlane.buffer
            val vBuffer: ByteBuffer = vPlane.buffer

            val imgWidth = image.width
            val imgHeight = image.height
            val rotationDegrees = image.imageInfo.rotationDegrees

            val yRowStride = yPlane.rowStride
            val uvRowStride = uPlane.rowStride
            val uvPixelStride = uPlane.pixelStride

            // Fast subsampling step
            val step = 6
            var sumX = 0L
            var sumY = 0L
            var skinPixelCount = 0

            var minX = imgWidth
            var maxX = 0
            var minY = imgHeight
            var maxY = 0

            // Multi-tier skin/contrast detection across lighting conditions
            for (y in 0 until imgHeight step step) {
                for (x in 0 until imgWidth step step) {
                    val yIndex = y * yRowStride + x
                    val uvIndex = (y / 2) * uvRowStride + (x / 2) * uvPixelStride

                    if (yIndex < yBuffer.limit() && uvIndex < uBuffer.limit() && uvIndex < vBuffer.limit()) {
                        val yVal = yBuffer.get(yIndex).toInt() and 0xFF
                        val uVal = uBuffer.get(uvIndex).toInt() and 0xFF
                        val vVal = vBuffer.get(uvIndex).toInt() and 0xFF

                        // Robust YCbCr Skin Tone segmentation (inclusive of diverse tones & room lighting)
                        val isSkin = (yVal in 30..250 && uVal in 75..135 && vVal in 125..185) ||
                                (yVal in 45..230 && abs(uVal - 105) < 30 && abs(vVal - 150) < 35)

                        if (isSkin) {
                            sumX += x
                            sumY += y
                            skinPixelCount++

                            if (x < minX) minX = x
                            if (x > maxX) maxX = x
                            if (y < minY) minY = y
                            if (y > maxY) maxY = y
                        }
                    }
                }
            }

            val minSkinThreshold = 25
            if (skinPixelCount > minSkinThreshold) {
                lostFramesCount = 0
                isCurrentlyTracked = true

                var rawCenterX = (sumX / skinPixelCount).toFloat() / imgWidth
                var rawCenterY = (sumY / skinPixelCount).toFloat() / imgHeight

                var rawMinX = minX.toFloat() / imgWidth
                var rawMaxX = maxX.toFloat() / imgWidth
                var rawMinY = minY.toFloat() / imgHeight
                var rawMaxY = maxY.toFloat() / imgHeight

                // Account for CameraX sensor rotation degrees (landscape 0, 90, 180, 270)
                val (normX, normY, spanW, spanH) = when (rotationDegrees) {
                    90 -> {
                        val nx = rawCenterY
                        val ny = 1f - rawCenterX
                        val sw = rawMaxY - rawMinY
                        val sh = rawMaxX - rawMinX
                        listOf(nx, ny, sw, sh)
                    }
                    270 -> {
                        val nx = 1f - rawCenterY
                        val ny = rawCenterX
                        val sw = rawMaxY - rawMinY
                        val sh = rawMaxX - rawMinX
                        listOf(nx, ny, sw, sh)
                    }
                    180 -> {
                        val nx = 1f - rawCenterX
                        val ny = 1f - rawCenterY
                        val sw = rawMaxX - rawMinX
                        val sh = rawMaxY - rawMinY
                        listOf(nx, ny, sw, sh)
                    }
                    else -> {
                        val nx = rawCenterX
                        val ny = rawCenterY
                        val sw = rawMaxX - rawMinX
                        val sh = rawMaxY - rawMinY
                        listOf(nx, ny, sw, sh)
                    }
                }

                val handArea = spanW * spanH
                val estimatedZ = (1.5f - (handArea * 1.8f)).coerceIn(0.7f, 2.0f)

                // 3D coordinates in camera view space
                val targetPosX = (normX - 0.5f) * 2.2f
                val targetPosY = -(normY - 0.5f) * 1.6f
                val targetPosZ = estimatedZ

                // Dynamic hand dimensions
                val handW = (spanW * 1.1f).coerceIn(0.12f, 0.32f)
                val handH = (spanH * 1.1f).coerceIn(0.15f, 0.40f)

                // Smooth position with exponential smoothing
                val lerpRate = 0.55f
                val posX = lastTrackedHand.position.x + lerpRate * (targetPosX - lastTrackedHand.position.x)
                val posY = lastTrackedHand.position.y + lerpRate * (targetPosY - lastTrackedHand.position.y)
                val posZ = lastTrackedHand.position.z + lerpRate * (targetPosZ - lastTrackedHand.position.z)

                val palmPos = Vector3(posX, posY, posZ)
                val wristPos = Vector3(posX, posY - handH * 0.50f, posZ + 0.05f)

                // 5 Skeletal Finger Joints & Tips
                val thumbTipPos = Vector3(posX - handW * 0.45f, posY + handH * 0.10f, posZ - 0.03f)
                val indexTipPos = Vector3(posX - handW * 0.15f, posY + handH * 0.52f, posZ - 0.06f)
                val middleTipPos = Vector3(posX + handW * 0.04f, posY + handH * 0.58f, posZ - 0.07f)
                val ringTipPos = Vector3(posX + handW * 0.22f, posY + handH * 0.46f, posZ - 0.05f)
                val pinkyTipPos = Vector3(posX + handW * 0.38f, posY + handH * 0.30f, posZ - 0.03f)

                // Hand Outer Contour Polygon
                val contour3D = listOf(
                    wristPos,
                    Vector3(posX - handW * 0.30f, posY - handH * 0.20f, posZ),
                    thumbTipPos,
                    Vector3(posX - handW * 0.20f, posY + handH * 0.20f, posZ),
                    indexTipPos,
                    Vector3(posX - handW * 0.05f, posY + handH * 0.30f, posZ),
                    middleTipPos,
                    Vector3(posX + handW * 0.12f, posY + handH * 0.28f, posZ),
                    ringTipPos,
                    Vector3(posX + handW * 0.30f, posY + handH * 0.20f, posZ),
                    pinkyTipPos,
                    Vector3(posX + handW * 0.32f, posY - handH * 0.15f, posZ),
                    wristPos
                )

                val pinchDist = sqrt((indexTipPos.x - thumbTipPos.x).pow(2) + (indexTipPos.y - thumbTipPos.y).pow(2))
                val isPinching = pinchDist < 0.18f || (spanH < 0.14f && spanW < 0.14f)

                val gesture = when {
                    isPinching -> HandGesture.PINCH
                    spanH > 1.35f * spanW -> HandGesture.POINTING
                    else -> HandGesture.OPEN_PALM
                }

                // Laser Ray shooting from index fingertip forward (Quest 2 style)
                val rayDir = Vector3(
                    x = (indexTipPos.x - posX) * 0.5f + posX * 0.4f,
                    y = (indexTipPos.y - posY) * 0.5f + posY * 0.4f,
                    z = 1.0f
                ).normalized()

                val ray = Ray3D(origin = indexTipPos, direction = rayDir)

                val tracked = TrackedHand(
                    isTracked = true,
                    isLeft = false,
                    position = palmPos,
                    wristPosition = wristPos,
                    indexTip = indexTipPos,
                    thumbTip = thumbTipPos,
                    middleTip = middleTipPos,
                    ringTip = ringTipPos,
                    pinkyTip = pinkyTipPos,
                    contourPoints = contour3D,
                    pinchDistance = pinchDist,
                    gesture = gesture,
                    confidence = (skinPixelCount.toFloat() / 200f).coerceIn(0.6f, 1.0f),
                    laserRay = ray,
                    isPinching = isPinching,
                    isGrabbing = isPinching,
                    color = 0xFF00E5FF
                )

                lastTrackedHand = tracked
                onHandTracked(tracked)
            } else {
                lostFramesCount++
                if (lostFramesCount > 10) {
                    isCurrentlyTracked = false
                }
                // Keep smooth state with fallback
                val fallbackHand = lastTrackedHand.copy(
                    isTracked = isCurrentlyTracked,
                    confidence = if (isCurrentlyTracked) 0.5f else 0f,
                    isPinching = false
                )
                onHandTracked(fallbackHand)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            image.close()
        }
    }
}

