package com.example.vr.engine

import android.opengl.Matrix
import kotlin.math.*
import com.example.vr.model.Vector3
import com.example.vr.model.ProjectedPoint
import com.example.vr.model.Ray3D

data class ViewPoint(val x: Float, val y: Float, val z: Float)

object VRMath {

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
        
        val x1 = rel.x * cy - rel.z * sy
        val y1 = rel.y
        val z1 = rel.x * sy + rel.z * cy
        
        val x2 = x1
        val y2 = y1 * cp - z1 * sp
        val z2 = y1 * sp + z1 * cp
        
        val x3 = x2 * cr + y2 * sr
        val y3 = -x2 * sr + y2 * cr
        val z3 = z2
        
        return ViewPoint(x3, y3, z3)
    }

    fun viewToScreen(
        vp: ViewPoint,
        screenWidth: Float,
        screenHeight: Float,
        fov: Float
    ): ProjectedPoint {
        val aspect = screenWidth / screenHeight
        val fovRad = Math.toRadians(fov.toDouble()).toFloat()
        val f = 1.0f / tan(fovRad / 2.0f)
        
        val projX = (vp.x * f) / vp.z
        val projY = (vp.y * f * aspect) / vp.z
        
        val screenX = (projX + 1f) * 0.5f * screenWidth
        val screenY = (1f - projY) * 0.5f * screenHeight
        
        return ProjectedPoint(screenX, screenY, vp.z, isVisible = true)
    }

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

    fun clipPolygon(
        vertices: List<Vector3>,
        cameraPos: Vector3,
        pitch: Float,
        yaw: Float,
        roll: Float,
        nearClip: Float = 0.05f
    ): List<ViewPoint> {
        val viewPoints = vertices.map { worldToView(it, cameraPos, pitch, yaw, roll) }
        val output = mutableListOf<ViewPoint>()
        if (viewPoints.isEmpty()) return output
        
        var prev = viewPoints.last()
        var prevInside = prev.z >= nearClip
        
        for (curr in viewPoints) {
            val currInside = curr.z >= nearClip
            
            if (currInside != prevInside) {
                val t = (nearClip - prev.z) / (curr.z - prev.z)
                val intersectX = prev.x + t * (curr.x - prev.x)
                val intersectY = prev.y + t * (curr.y - prev.y)
                output.add(ViewPoint(intersectX, intersectY, nearClip))
            }
            if (currInside) {
                output.add(curr)
            }
            prev = curr
            prevInside = currInside
        }
        return output
    }

    fun getForwardVector(pitch: Float, yaw: Float, roll: Float): Vector3 {
        val cp = cos(pitch)
        val sp = sin(pitch)
        val cy = cos(yaw)
        val sy = sin(yaw)
        
        val x = sy * cp
        val y = sp
        val z = cy * cp
        return Vector3(x, y, z)
    }

    fun projectClippedPolygon(
        vertices: List<Vector3>,
        cameraPos: Vector3,
        pitch: Float,
        yaw: Float,
        roll: Float,
        screenWidth: Float,
        screenHeight: Float,
        fov: Float = 75f
    ): List<ProjectedPoint> {
        val clipped = clipPolygon(vertices, cameraPos, pitch, yaw, roll)
        return clipped.map { viewToScreen(it, screenWidth, screenHeight, fov) }
    }

    fun rayIntersectsQuad(
        ray: Ray3D,
        quadCenter: Vector3,
        quadNormal: Vector3,
        width: Float,
        height: Float
    ): Float? {
        val n = quadNormal.normalized()
        val denom = n.dot(ray.direction)
        if (abs(denom) < 1e-6f) return null
        
        val t = (quadCenter - ray.origin).dot(n) / denom
        if (t < 0) return null
        
        val p = ray.getPoint(t)
        
        val up = if (abs(n.y) > 0.9f) Vector3(0f, 0f, 1f) else Vector3(0f, 1f, 0f)
        val right = up.cross(n).normalized()
        val actualUp = n.cross(right).normalized()
        
        val d = p - quadCenter
        val x = abs(d.dot(right))
        val y = abs(d.dot(actualUp))
        
        if (x <= width / 2f && y <= height / 2f) return t
        return null
    }

    fun rayIntersectsSphere(
        ray: Ray3D,
        center: Vector3,
        radius: Float
    ): Float? {
        val l = center - ray.origin
        val tca = l.dot(ray.direction)
        if (tca < 0) return null
        val d2 = l.dot(l) - tca * tca
        val r2 = radius * radius
        if (d2 > r2) return null
        val thc = sqrt(r2 - d2)
        val t0 = tca - thc
        val t1 = tca + thc
        if (t0 < 0 && t1 < 0) return null
        return if (t0 < 0) t1 else t0
    }
}
