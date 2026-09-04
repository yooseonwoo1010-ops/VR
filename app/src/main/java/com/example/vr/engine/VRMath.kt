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
        viewMatrix: FloatArray
    ): ViewPoint {
        val rel = pointWorld - cameraPos
        val pointVec = floatArrayOf(rel.x, rel.y, rel.z, 1f)
        val resultVec = FloatArray(4)
        Matrix.multiplyMV(resultVec, 0, viewMatrix, 0, pointVec, 0)
        
        return ViewPoint(resultVec[0], resultVec[1], resultVec[2])
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
        
        val zDepth = vp.z
        
        val projX = (vp.x * f) / zDepth
        val projY = (vp.y * f * aspect) / zDepth
        
        val screenX = (projX + 1f) * 0.5f * screenWidth
        val screenY = (1f - projY) * 0.5f * screenHeight
        
        return ProjectedPoint(screenX, screenY, zDepth, isVisible = true)
    }

    fun project3DTo2D(
        pointWorld: Vector3,
        cameraPos: Vector3,
        viewMatrix: FloatArray,
        screenWidth: Float,
        screenHeight: Float,
        fov: Float = 75f,
        nearClip: Float = 0.05f,
        farClip: Float = 100f
    ): ProjectedPoint {
        val vp = worldToView(pointWorld, cameraPos, viewMatrix)
        if (vp.z <= nearClip || vp.z >= farClip) {
            return ProjectedPoint(0f, 0f, vp.z, isVisible = false)
        }
        return viewToScreen(vp, screenWidth, screenHeight, fov)
    }

    fun clipPolygon(
        vertices: List<Vector3>,
        cameraPos: Vector3,
        viewMatrix: FloatArray,
        nearClip: Float = 0.05f
    ): List<ViewPoint> {
        val viewPoints = vertices.map { worldToView(it, cameraPos, viewMatrix) }
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

    fun getForwardVector(viewMatrix: FloatArray): Vector3 {
        val cameraMatrix = FloatArray(16)
        Matrix.invertM(cameraMatrix, 0, viewMatrix, 0)
        
        val fwd = floatArrayOf(0f, 0f, 1f, 0f)
        val res = FloatArray(4)
        Matrix.multiplyMV(res, 0, cameraMatrix, 0, fwd, 0)
        
        return Vector3(res[0], res[1], res[2]).normalized()
    }

    fun getForwardVector(pitch: Float, yaw: Float, roll: Float): Vector3 {
        val matrix = FloatArray(16)
        Matrix.setIdentityM(matrix, 0)
        
        Matrix.rotateM(matrix, 0, Math.toDegrees(yaw.toDouble()).toFloat(), 0f, 1f, 0f)
        Matrix.rotateM(matrix, 0, Math.toDegrees(-pitch.toDouble()).toFloat(), 1f, 0f, 0f)
        Matrix.rotateM(matrix, 0, Math.toDegrees(roll.toDouble()).toFloat(), 0f, 0f, 1f)
        
        val fwd = floatArrayOf(0f, 0f, 1f, 0f)
        val res = FloatArray(4)
        Matrix.multiplyMV(res, 0, matrix, 0, fwd, 0)
        
        return Vector3(res[0], res[1], res[2]).normalized()
    }

    fun projectClippedPolygon(
        vertices: List<Vector3>,
        cameraPos: Vector3,
        viewMatrix: FloatArray,
        screenWidth: Float,
        screenHeight: Float,
        fov: Float = 75f
    ): List<ProjectedPoint> {
        val clipped = clipPolygon(vertices, cameraPos, viewMatrix)
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
