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
    private var lastTrackedHand = TrackedHand()

    override fun analyze(image: ImageProxy) {
        frameCount++
        // Downsample analysis: process every 2nd frame for 30+ FPS smooth rendering
        if (frameCount % 2 != 0) {
            image.close()
            return
        }

        try {
            val yPlane = image.planes[0]
            val uPlane = image.planes[1]
            val vPlane = image.planes[2]

            val yBuffer: ByteBuffer = yPlane.buffer
            val uBuffer: ByteBuffer = uPlane.buffer
            val vBuffer: ByteBuffer = vPlane.buffer

            val width = image.width
            val height = image.height
            val yRowStride = yPlane.rowStride
            val uvRowStride = uPlane.rowStride
            val uvPixelStride = uPlane.pixelStride

            // Sample grid (step of 8 for fast real-time computer vision)
            val step = 8
            var sumX = 0L
            var sumY = 0L
            var skinPixelCount = 0

            var minX = width
            var maxX = 0
            var minY = height
            var maxY = 0

            var topPointX = 0
            var topPointY = height
            var rightPointX = 0
            var rightPointY = 0

            for (y in 0 until height step step) {
                for (x in 0 until width step step) {
                    val yIndex = y * yRowStride + x
                    val uvIndex = (y / 2) * uvRowStride + (x / 2) * uvPixelStride

                    if (yIndex < yBuffer.limit() && uvIndex < uBuffer.limit() && uvIndex < vBuffer.limit()) {
                        val yVal = yBuffer.get(yIndex).toInt() and 0xFF
                        val uVal = uBuffer.get(uvIndex).toInt() and 0xFF
                        val vVal = vBuffer.get(uvIndex).toInt() and 0xFF

                        // YCbCr skin tone detection: Cb in [77, 127], Cr in [133, 173]
                        if (yVal > 40 && uVal in 75..130 && vVal in 130..175) {
                            sumX += x
                            sumY += y
                            skinPixelCount++

                            if (x < minX) minX = x
                            if (x > maxX) maxX = x
                            if (y < minY) minY = y
                            if (y > maxY) maxY = y

                            if (y < topPointY) {
                                topPointY = y
                                topPointX = x
                            }
                            if (x > rightPointX) {
                                rightPointX = x
                                rightPointY = y
                            }
                        }
                    }
                }
            }

            // Minimum skin area threshold (detects distinct hand)
            val minSkinThreshold = 40
            if (skinPixelCount > minSkinThreshold) {
                val palmCenterX = (sumX / skinPixelCount).toFloat() / width
                val palmCenterY = (sumY / skinPixelCount).toFloat() / height

                // Calculate hand bounding box span
                val handWidthNorm = (maxX - minX).toFloat() / width
                val handHeightNorm = (maxY - minY).toFloat() / height
                val handArea = handWidthNorm * handHeightNorm

                // Estimate depth Z based on hand size in frame
                // Closer hand = larger area = smaller Z
                val estimatedZ = (1.8f - (handArea * 2.5f)).coerceIn(0.6f, 2.5f)

                // Normalized 3D Position in camera view space
                // Map X: [0, 1] -> [-1.2, 1.2], Y: [0, 1] -> [0.8, -0.8]
                val posX = (palmCenterX - 0.5f) * 2.4f
                val posY = -(palmCenterY - 0.5f) * 1.8f
                val posZ = estimatedZ

                // Fingertip points
                val indexX = (topPointX.toFloat() / width - 0.5f) * 2.4f
                val indexY = -(topPointY.toFloat() / height - 0.5f) * 1.8f
                val thumbX = (rightPointX.toFloat() / width - 0.5f) * 2.4f
                val thumbY = -(rightPointY.toFloat() / height - 0.5f) * 1.8f

                val pinchDist = sqrt((indexX - thumbX).pow(2) + (indexY - thumbY).pow(2))
                val isPinching = pinchDist < 0.22f || (handHeightNorm < 0.18f && handWidthNorm < 0.18f)

                val gesture = when {
                    isPinching -> HandGesture.PINCH
                    palmCenterY > 0.75f && palmCenterX < 0.35f -> HandGesture.PALM_MENU
                    handHeightNorm > 1.4f * handWidthNorm -> HandGesture.POINTING
                    handArea > 0.15f -> HandGesture.OPEN_PALM
                    else -> HandGesture.OPEN_PALM
                }

                // Laser Ray shooting from palm forward
                val rayDir = Vector3(
                    x = posX * 0.4f,
                    y = posY * 0.4f,
                    z = 1.0f
                ).normalized()

                val handPos = Vector3(posX, posY, posZ)
                val ray = Ray3D(origin = handPos, direction = rayDir)

                // Smooth coordinates with previous frame
                val smoothFactor = 0.45f
                val smoothPos = Vector3(
                    x = lastTrackedHand.position.x + smoothFactor * (handPos.x - lastTrackedHand.position.x),
                    y = lastTrackedHand.position.y + smoothFactor * (handPos.y - lastTrackedHand.position.y),
                    z = lastTrackedHand.position.z + smoothFactor * (handPos.z - lastTrackedHand.position.z)
                )

                val trackedHand = TrackedHand(
                    isTracked = true,
                    isLeft = false,
                    position = smoothPos,
                    wristPosition = Vector3(smoothPos.x, smoothPos.y - 0.2f, smoothPos.z + 0.1f),
                    indexTip = Vector3(indexX, indexY, posZ - 0.1f),
                    thumbTip = Vector3(thumbX, thumbY, posZ - 0.05f),
                    pinchDistance = pinchDist,
                    gesture = gesture,
                    confidence = (skinPixelCount.toFloat() / 300f).coerceIn(0.5f, 1.0f),
                    laserRay = ray,
                    isPinching = isPinching,
                    isGrabbing = gesture == HandGesture.FIST || isPinching
                )

                lastTrackedHand = trackedHand
                onHandTracked(trackedHand)
            } else {
                // Decay tracking
                val untracked = lastTrackedHand.copy(
                    isTracked = false,
                    confidence = 0f,
                    gesture = HandGesture.NONE,
                    isPinching = false
                )
                lastTrackedHand = untracked
                onHandTracked(untracked)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            image.close()
        }
    }
}
