package com.example.vr.engine

import com.example.vr.model.ProjectedPoint
import com.example.vr.model.Ray3D
import com.example.vr.model.Vector3
import kotlin.math.*

object VRMath {

    /**
     * Projects a 3D world-space coordinate into 2D viewport screen coordinates
     * based on camera position, camera orientation (pitch, yaw, roll), FOV, and screen dimensions.
     * 
     * Natural Spatial Computing Laws:
     * - Head turns Right (yaw > 0) -> World moves Left on screen.
     * - Head tilts Up (pitch > 0) -> World moves Down on screen.
     * - Head tilts Clockwise (roll > 0) -> World rotates Counter-Clockwise on screen.
     */
    fun project3DTo2D(
        pointWorld: Vector3,
        cameraPos: Vector3,
        pitch: Float,
        yaw: Float,
        roll: Float,
        screenWidth: Float,
        screenHeight: Float,
        fov: Float = 75f,
        nearClip: Float = 0.05f,
        farClip: Float = 100f
    ): ProjectedPoint {
        // 1. Translate point relative to camera position in World Space
        val rel = pointWorld - cameraPos

        // 2. Camera Orientation Trigonometry
        val cy = cos(yaw)
        val sy = sin(yaw)
        val cp = cos(pitch)
        val sp = sin(pitch)
        val cr = cos(roll)
        val sr = sin(roll)

        // 3. Orthonormal 3x3 Camera View Matrix
        // Row 0: Camera Right vector in World
        val m0 = cr * cy - sr * sp * sy
        val m1 = sr * cp
        val m2 = -cr * sy - sr * sp * cy

        // Row 1: Camera Up vector in World
        val m3 = -sr * cy - cr * sp * sy
        val m4 = cr * cp
        val m5 = sr * sy - cr * sp * cy

        // Row 2: Camera Forward vector in World (Optical Depth axis)
        val m6 = cp * sy
        val m7 = sp
        val m8 = cp * cy

        // 4. Transform World vector into Camera View Space (X = Right, Y = Up, Z = Forward)
        val viewX = rel.x * m0 + rel.y * m1 + rel.z * m2
        val viewY = rel.x * m3 + rel.y * m4 + rel.z * m5
        val viewZ = rel.x * m6 + rel.y * m7 + rel.z * m8

        // Behind camera or clipped
        if (viewZ <= nearClip || viewZ >= farClip) {
            return ProjectedPoint(0f, 0f, viewZ, isVisible = false)
        }

        // 5. Symmetric Perspective Camera Projection
        val fovRad = Math.toRadians(fov.toDouble()).toFloat()
        val aspect = screenWidth / max(screenHeight, 1f)
        val tanHalfFov = tan(fovRad * 0.5f)

        val projX = viewX / (viewZ * tanHalfFov * aspect)
        val projY = viewY / (viewZ * tanHalfFov)

        // Map normalized device coordinates [-1, 1] to screen pixels [0, width], [0, height]
        // Note: In screen coordinates, Y=0 is Top, so positive viewY (Up) maps towards Y=0
        val screenX = (projX + 1f) * 0.5f * screenWidth
        val screenY = (1f - projY) * 0.5f * screenHeight

        return ProjectedPoint(screenX, screenY, viewZ, isVisible = true)
    }

    /**
     * Helper to compute camera forward vector from Euler angles (pitch, yaw, roll in radians)
     */
    fun getForwardVector(pitch: Float, yaw: Float, roll: Float): Vector3 {
        val cy = cos(yaw)
        val sy = sin(yaw)
        val cp = cos(pitch)
        val sp = sin(pitch)
        return Vector3(
            x = cp * sy,
            y = sp,
            z = cp * cy
        ).normalized()
    }

    /**
     * Ray-Sphere intersection test for laser pointing
     */
    fun rayIntersectsSphere(ray: Ray3D, sphereCenter: Vector3, sphereRadius: Float): Float? {
        val oc = ray.origin - sphereCenter
        val a = ray.direction.dot(ray.direction)
        val b = 2f * oc.dot(ray.direction)
        val c = oc.dot(oc) - sphereRadius * sphereRadius
        val discriminant = b * b - 4f * a * c

        if (discriminant < 0) return null

        val t = (-b - sqrt(discriminant)) / (2f * a)
        return if (t > 0.05f) t else null
    }

    /**
     * Ray-Plane / Quad intersection test for holographic UI panels
     */
    fun rayIntersectsQuad(
        ray: Ray3D,
        quadCenter: Vector3,
        quadNormal: Vector3,
        width: Float,
        height: Float
    ): Float? {
        val denom = quadNormal.dot(ray.direction)
        if (abs(denom) < 0.0001f) return null // Ray parallel to plane

        val p0l0 = quadCenter - ray.origin
        val t = p0l0.dot(quadNormal) / denom
        if (t < 0.05f) return null

        val hitPoint = ray.getPoint(t)
        val localOffset = hitPoint - quadCenter

        // Check bounding box
        if (abs(localOffset.x) <= width * 0.5f && abs(localOffset.y) <= height * 0.5f) {
            return t
        }
        return null
    }

    /**
     * Ray-AABB intersection test for 3D boxes
     */
    fun rayIntersectsBox(ray: Ray3D, boxCenter: Vector3, size: Float): Float? {
        val minX = boxCenter.x - size * 0.5f
        val maxX = boxCenter.x + size * 0.5f
        val minY = boxCenter.y - size * 0.5f
        val maxY = boxCenter.y + size * 0.5f
        val minZ = boxCenter.z - size * 0.5f
        val maxZ = boxCenter.z + size * 0.5f

        val invDirX = if (abs(ray.direction.x) > 0.0001f) 1f / ray.direction.x else 10000f
        val invDirY = if (abs(ray.direction.y) > 0.0001f) 1f / ray.direction.y else 10000f
        val invDirZ = if (abs(ray.direction.z) > 0.0001f) 1f / ray.direction.z else 10000f

        val t1 = (minX - ray.origin.x) * invDirX
        val t2 = (maxX - ray.origin.x) * invDirX
        val t3 = (minY - ray.origin.y) * invDirY
        val t4 = (maxY - ray.origin.y) * invDirY
        val t5 = (minZ - ray.origin.z) * invDirZ
        val t6 = (maxZ - ray.origin.z) * invDirZ

        val tmin = max(max(min(t1, t2), min(t3, t4)), min(t5, t6))
        val tmax = min(min(max(t1, t2), max(t3, t4)), max(t5, t6))

        if (tmax < 0 || tmin > tmax) return null
        return if (tmin > 0.05f) tmin else if (tmax > 0.05f) tmax else null
    }
}
