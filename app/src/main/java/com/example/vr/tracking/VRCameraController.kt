package com.example.vr.tracking

import com.example.vr.model.Vector3

/**
 * VRCameraController
 * 역할: HeadTracker 값을 받아 카메라 회전을 관리하고,
 * 카메라의 월드 트랜스폼(위치, 회전)을 제공한다.
 */
class VRCameraController {
    var position: Vector3 = Vector3(0f, 0f, 0f)
        private set
        
    var orientation: HeadOrientation = HeadOrientation()
        private set

    fun updateCamera(newOrientation: HeadOrientation, newPosition: Vector3 = Vector3(0f, 0f, 0f)) {
        this.orientation = newOrientation
        this.position = newPosition
    }

    val forward: Vector3
        get() {
            // Calculate forward vector based on pitch and yaw
            // Pitch is around X axis, Yaw is around Y axis
            val pitch = orientation.pitch
            val yaw = orientation.yaw
            return Vector3(
                x = (Math.sin(yaw.toDouble()) * Math.cos(pitch.toDouble())).toFloat(),
                y = (-Math.sin(pitch.toDouble())).toFloat(),
                z = (Math.cos(yaw.toDouble()) * Math.cos(pitch.toDouble())).toFloat()
            )
        }
}
