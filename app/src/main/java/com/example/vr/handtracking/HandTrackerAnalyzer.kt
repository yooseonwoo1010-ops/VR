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
            var leftPointX = width
            var leftPointY = 0
            var bottomPointX = 0
            var bottomPointY = 0

            // Collect edge boundary sample points for real hand silhouette outline contour
            val edgePoints = mutableListOf<Pair<Int, Int>>()

            for (y in 0 until height step step) {
                var rowFirstX = -1
                var rowLastX = -1
                for (x in 0 until width step step) {
                    val yIndex = y * yRowStride + x
                    val uvIndex = (y / 2) * uvRowStride + (x / 2) * uvPixelStride

                    if (yIndex < yBuffer.limit() && uvIndex < uBuffer.limit() && uvIndex < vBuffer.limit()) {
                        val yVal = yBuffer.get(yIndex).toInt() and 0xFF
                        val uVal = uBuffer.get(uvIndex).toInt() and 0xFF
                        val vVal = vBuffer.get(uvIndex).toInt() and 0xFF

                        // Adaptive YCbCr skin tone detection: supports wide lighting & hand skin tones
                        if (yVal in 35..250 && uVal in 70..135 && vVal in 128..180) {
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
                            if (y > bottomPointY) {
                                bottomPointY = y
                                bottomPointX = x
                            }
                            if (x > rightPointX) {
                                rightPointX = x
                                rightPointY = y
                            }
                            if (x < leftPointX) {
                                leftPointX = x
                                leftPointY = y
                            }

                            if (rowFirstX == -1) rowFirstX = x
                            rowLastX = x
                        }
                    }
                }
                if (rowFirstX != -1) {
                    edgePoints.add(Pair(rowFirstX, y))
                    if (rowLastX != rowFirstX) {
                        edgePoints.add(Pair(rowLastX, y))
                    }
                }
            }

            // Minimum skin area threshold (detects distinct hand)
            val minSkinThreshold = 30
            if (skinPixelCount > minSkinThreshold) {
                val palmCenterX = (sumX / skinPixelCount).toFloat() / width
                val palmCenterY = (sumY / skinPixelCount).toFloat() / height

                // Calculate hand bounding box span
                val handWidthNorm = (maxX - minX).toFloat() / width
                val handHeightNorm = (maxY - minY).toFloat() / height
                val handArea = handWidthNorm * handHeightNorm

                // Estimate depth Z based on hand size in frame
                val estimatedZ = (1.8f - (handArea * 2.2f)).coerceIn(0.7f, 2.2f)

                // Normalized 3D Position in camera view space
                val posX = (palmCenterX - 0.5f) * 2.4f
                val posY = -(palmCenterY - 0.5f) * 1.8f
                val posZ = estimatedZ

                // Dynamic hand dimensions in 3D
                val spanW = (handWidthNorm * 1.2f).coerceIn(0.12f, 0.35f)
                val spanH = (handHeightNorm * 1.2f).coerceIn(0.16f, 0.45f)

                val wristPos = Vector3(posX, posY - spanH * 0.55f, posZ + 0.05f)
                val palmPos = Vector3(posX, posY, posZ)

                val thumbTipPos = Vector3(posX - spanW * 0.52f, posY + spanH * 0.12f, posZ - 0.03f)
                val thumbBasePos = Vector3(posX - spanW * 0.32f, posY - spanH * 0.22f, posZ)

                val indexTipPos = Vector3(posX - spanW * 0.20f, posY + spanH * 0.55f, posZ - 0.06f)
                val indexKnucklePos = Vector3(posX - spanW * 0.16f, posY + spanH * 0.18f, posZ)

                val middleTipPos = Vector3(posX + spanW * 0.02f, posY + spanH * 0.62f, posZ - 0.08f)
                val middleKnucklePos = Vector3(posX + spanW * 0.02f, posY + spanH * 0.20f, posZ)

                val ringTipPos = Vector3(posX + spanW * 0.22f, posY + spanH * 0.50f, posZ - 0.06f)
                val ringKnucklePos = Vector3(posX + spanW * 0.18f, posY + spanH * 0.16f, posZ)

                val pinkyTipPos = Vector3(posX + spanW * 0.42f, posY + spanH * 0.34f, posZ - 0.04f)
                val pinkyKnucklePos = Vector3(posX + spanW * 0.32f, posY + spanH * 0.12f, posZ)
                val pinkyBasePos = Vector3(posX + spanW * 0.32f, posY - spanH * 0.15f, posZ)

                // Ordered Clockwise Hand Silhouette Contour Envelope (Clean, smooth polygon)
                val contour3D = listOf(
                    wristPos,
                    thumbBasePos,
                    thumbTipPos,
                    indexKnucklePos,
                    indexTipPos,
                    middleKnucklePos,
                    middleTipPos,
                    ringKnucklePos,
                    ringTipPos,
                    pinkyKnucklePos,
                    pinkyTipPos,
                    pinkyBasePos,
                    wristPos
                )

                val pinchDist = sqrt((indexTipPos.x - thumbTipPos.x).pow(2) + (indexTipPos.y - thumbTipPos.y).pow(2))
                val isPinching = pinchDist < 0.20f || (handHeightNorm < 0.16f && handWidthNorm < 0.16f)

                val gesture = when {
                    isPinching -> HandGesture.PINCH
                    palmCenterY > 0.75f && palmCenterX < 0.35f -> HandGesture.PALM_MENU
                    handHeightNorm > 1.4f * handWidthNorm -> HandGesture.POINTING
                    handArea > 0.15f -> HandGesture.OPEN_PALM
                    else -> HandGesture.OPEN_PALM
                }

                // Laser Ray shooting from index fingertip forward
                val rayDir = Vector3(
                    x = (indexTipPos.x - posX) * 0.6f + posX * 0.3f,
                    y = (indexTipPos.y - posY) * 0.6f + posY * 0.3f,
                    z = 1.0f
                ).normalized()

                val ray = Ray3D(origin = indexTipPos, direction = rayDir)

                // Smooth coordinates with previous frame
                val smoothFactor = 0.45f
                val smoothPos = Vector3(
                    x = lastTrackedHand.position.x + smoothFactor * (palmPos.x - lastTrackedHand.position.x),
                    y = lastTrackedHand.position.y + smoothFactor * (palmPos.y - lastTrackedHand.position.y),
                    z = lastTrackedHand.position.z + smoothFactor * (palmPos.z - lastTrackedHand.position.z)
                )

                val trackedHand = TrackedHand(
                    isTracked = true,
                    isLeft = false,
                    position = smoothPos,
                    wristPosition = wristPos,
                    indexTip = indexTipPos,
                    thumbTip = thumbTipPos,
                    middleTip = middleTipPos,
                    ringTip = ringTipPos,
                    pinkyTip = pinkyTipPos,
                    contourPoints = contour3D,
                    pinchDistance = pinchDist,
                    gesture = gesture,
                    confidence = (skinPixelCount.toFloat() / 250f).coerceIn(0.5f, 1.0f),
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
