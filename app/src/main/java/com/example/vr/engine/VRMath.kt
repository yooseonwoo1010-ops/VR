package com.example.vr.engine

import com.example.vr.model.ProjectedPoint
import com.example.vr.model.Ray3D
import com.example.vr.model.Vector3
import kotlin.math.*

data class ViewPoint(val x: Float, val y: Float, val z: Float) {
    fun toVector3() = Vector3(x, y, z)
}

object VRMath {

    /**
     * Converts a 3D world coordinate to camera view space (X: Right, Y: Up, Z: Forward)
     */
    fun worldToView(
        pointWorld: Vector3,
        cameraPos: Vector3,
        pitch: Float,
        yaw: Float,
        roll: Float
    ): ViewPoint {
        val rel = pointWorld - cameraPos

        val cy = cos(yaw)
        val sy = sin(yaw)
        val cp = cos(pitch)
        val sp = sin(pitch)
        val cr = cos(roll)
        val sr = sin(roll)

        // 1. Rotate by -yaw around Y axis (look left/right)
        val x1 = rel.x * cy - rel.z * sy
        val y1 = rel.y
        val z1 = rel.x * sy + rel.z * cy

        // 2. Rotate by -pitch around X axis (look up/down)
        val x2 = x1
        val y2 = y1 * cp + z1 * sp
        val z2 = -y1 * sp + z1 * cp

        // 3. Rotate by -roll around Z axis (tilt head)
        val x3 = x2 * cr + y2 * sr
        val y3 = -x2 * sr + y2 * cr
        val z3 = z2

        return ViewPoint(x3, y3, z3)
    }

    /**
     * Projects a point from view space to 2D screen coordinates.
     */
    fun viewToScreen(
        viewPoint: ViewPoint,
        screenWidth: Float,
        screenHeight: Float,
        fov: Float = 75f
    ): ProjectedPoint {
        if (viewPoint.z <= 0.05f) {
            return ProjectedPoint(0f, 0f, viewPoint.z, isVisible = false)
        }

        val fovRad = Math.toRadians(fov.toDouble()).toFloat()
        val aspect = screenWidth / max(screenHeight, 1f)
        val tanHalfFov = tan(fovRad * 0.5f)

        val projX = viewPoint.x / (viewPoint.z * tanHalfFov * aspect)
        val projY = viewPoint.y / (viewPoint.z * tanHalfFov)

        val screenX = (projX + 1f) * 0.5f * screenWidth
        val screenY = (1f - projY) * 0.5f * screenHeight

        return ProjectedPoint(screenX, screenY, viewPoint.z, isVisible = true)
    }

    /**
     * Projects a single 3D point in world space directly to 2D screen coordinates.
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
        val vp = worldToView(pointWorld, cameraPos, pitch, yaw, roll)
        if (vp.z <= nearClip || vp.z >= farClip) {
            return ProjectedPoint(0f, 0f, vp.z, isVisible = false)
        }
        return viewToScreen(vp, screenWidth, screenHeight, fov)
    }

    /**
     * Clips a 3D polygon in View Space against near plane z >= nearClip (Sutherland-Hodgman algorithm)
     * and projects all resulting vertices into 2D screen coordinates.
     * Prevents any distortion, warping, or sudden popping when turning head or looking sideways.
     */
    fun projectClippedPolygon(
        worldPolygon: List<Vector3>,
        cameraPos: Vector3,
        pitch: Float,
        yaw: Float,
        roll: Float,
        screenWidth: Float,
        screenHeight: Float,
        fov: Float = 75f,
        nearClip: Float = 0.12f
    ): List<ProjectedPoint> {
        if (worldPolygon.size < 3) return emptyList()

        // 1. Transform all vertices into view space
        val viewVerts = worldPolygon.map { worldToView(it, cameraPos, pitch, yaw, roll) }

        // 2. Clip polygon against near plane z >= nearClip
        val clipped = mutableListOf<ViewPoint>()
        for (i in viewVerts.indices) {
            val curr = viewVerts[i]
            val prev = viewVerts[(i + viewVerts.size - 1) % viewVerts.size]

            val currInside = curr.z >= nearClip
            val prevInside = prev.z >= nearClip

            if (currInside) {
                if (!prevInside) {
                    // Edge entered the frustum: calculate intersection with z = nearClip
                    val t = (nearClip - prev.z) / (curr.z - prev.z)
                    val ix = prev.x + t * (curr.x - prev.x)
                    val iy = prev.y + t * (curr.y - prev.y)
                    clipped.add(ViewPoint(ix, iy, nearClip))
                }
                clipped.add(curr)
            } else if (prevInside) {
                // Edge exited the frustum: calculate intersection with z = nearClip
                val t = (nearClip - prev.z) / (curr.z - prev.z)
                val ix = prev.x + t * (curr.x - prev.x)
                val iy = prev.y + t * (curr.y - prev.y)
                clipped.add(ViewPoint(ix, iy, nearClip))
            }
        }

        if (clipped.size < 3) return emptyList()

        // 3. Project clipped vertices onto 2D screen
        return clipped.map { viewToScreen(it, screenWidth, screenHeight, fov) }
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
        if (abs(denom) < 0.0001f) return null

        val p0l0 = quadCenter - ray.origin
        val t = p0l0.dot(quadNormal) / denom
        if (t < 0.05f) return null

        val hitPoint = ray.getPoint(t)
        val localOffset = hitPoint - quadCenter

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
