package com.example.vr.engine

import com.example.vr.model.ProjectedPoint
import com.example.vr.model.Ray3D
import com.example.vr.model.Vector3
import kotlin.math.*

object VRMath {

    /**
     * Projects a 3D world-space coordinate into 2D viewport screen coordinates
     * based on camera position, camera orientation (pitch, yaw, roll), FOV, and screen dimensions.
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
        nearClip: Float = 0.1f,
        farClip: Float = 100f
    ): ProjectedPoint {
        // 1. Translate relative to camera position
        val rel = pointWorld - cameraPos

        // 2. Inverse Camera Rotation (World -> View space)
        // Inverse yaw (-yaw)
        val cy = cos(-yaw)
        val sy = sin(-yaw)
        val x1 = rel.x * cy + rel.z * sy
        val y1 = rel.y
        val z1 = -rel.x * sy + rel.z * cy

        // Inverse pitch (-pitch)
        val cp = cos(-pitch)
        val sp = sin(-pitch)
        val x2 = x1
        val y2 = y1 * cp - z1 * sp
        val z2 = y1 * sp + z1 * cp

        // Inverse roll (-roll)
        val cr = cos(-roll)
        val sr = sin(-roll)
        val x3 = x2 * cr - y2 * sr
        val y3 = x2 * sr + y2 * cr
        val z3 = z2

        // View space coordinates: x3 (Right), y3 (Up), z3 (Forward)
        if (z3 <= nearClip || z3 >= farClip) {
            return ProjectedPoint(0f, 0f, z3, isVisible = false)
        }

        // 3. Perspective Projection
        val fovRad = Math.toRadians(fov.toDouble()).toFloat()
        val aspect = screenWidth / max(screenHeight, 1f)
        val tanHalfFov = tan(fovRad / 2f)

        val projX = (x3 / (z3 * tanHalfFov * aspect))
        val projY = (y3 / (z3 * tanHalfFov))

        // Map normalized device coordinates [-1, 1] to screen pixels [0, width], [0, height]
        // Note: Y is flipped in screen coordinates (0 is top)
        val screenX = (projX + 1f) * 0.5f * screenWidth
        val screenY = (1f - projY) * 0.5f * screenHeight

        // Visible if point is in front of camera
        return ProjectedPoint(screenX, screenY, z3, isVisible = true)
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
            x = sy * cp,
            y = -sp,
            z = cy * cp
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
