package com.example.vr.tracking

import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.example.vr.model.TrackedHand
import com.example.vr.model.Vector3
import com.example.vr.model.HandGesture
import com.example.vr.model.Ray3D
import kotlin.math.*

object HandResultMapper {

    fun mapToTrackedHands(result: HandLandmarkerResult): Pair<TrackedHand, TrackedHand> {
        var rightHand = TrackedHand(isTracked = false, isLeft = false)
        var leftHand = TrackedHand(isTracked = false, isLeft = true)

        val handednessList = result.handedness()
        val landmarksList = result.landmarks()

        if (handednessList.isEmpty() || landmarksList.isEmpty()) {
            return Pair(rightHand, leftHand)
        }

        for (i in handednessList.indices) {
            val handedness = handednessList[i][0]
            val landmarks = landmarksList[i]
            
            // MediaPipe returns Left/Right. Sometimes mirrored for front camera.
            val isLeft = handedness.categoryName() == "Left"
            
            // 0: WRIST, 8: INDEX_FINGER_TIP, 4: THUMB_TIP, 12: MIDDLE_FINGER_TIP
            // 16: RING_FINGER_TIP, 20: PINKY_TIP
            
            // Map normalized coordinates [0, 1] to our VR 3D camera space.
            // X: -1 (left) to 1 (right)
            // Y: -1 (bottom) to 1 (top)
            // Z: 0.5 to 2.5 (depth estimation)
            
            fun mapPoint(lm: com.google.mediapipe.tasks.components.containers.NormalizedLandmark): Vector3 {
                val x = (lm.x() - 0.5f) * 2f
                val y = -(lm.y() - 0.5f) * 2f // Invert Y so up is positive
                val z = 1.0f + lm.z() * 5f // Rough depth scaling
                return Vector3(x, y, z)
            }

            val wrist = mapPoint(landmarks[0])
            val indexTip = mapPoint(landmarks[8])
            val thumbTip = mapPoint(landmarks[4])
            val middleTip = mapPoint(landmarks[12])
            val ringTip = mapPoint(landmarks[16])
            val pinkyTip = mapPoint(landmarks[20])
            
            // Calculate pinch
            val pinchDist = (indexTip - thumbTip).length()
            val isPinching = pinchDist < 0.15f
            
            // Determine gesture
            val gesture = if (isPinching) HandGesture.PINCH else HandGesture.OPEN_PALM
            
            // Calculate Laser Ray (pointing from index tip forward)
            // Ray direction: wrist to index tip
            val rayDir = (indexTip - wrist).normalized()
            val ray = Ray3D(indexTip, rayDir)

            val hand = TrackedHand(
                isTracked = true,
                isLeft = isLeft,
                position = wrist,
                wristPosition = wrist,
                indexTip = indexTip,
                thumbTip = thumbTip,
                middleTip = middleTip,
                ringTip = ringTip,
                pinkyTip = pinkyTip,
                pinchDistance = pinchDist,
                gesture = gesture,
                isPinching = isPinching,
                laserRay = ray
            )

            if (isLeft) {
                leftHand = hand
            } else {
                rightHand = hand
            }
        }

        return Pair(rightHand, leftHand)
    }
}
