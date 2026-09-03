cat << 'INNER_EOF' >> app/src/main/java/com/example/vr/engine/VRMath.kt

    fun rayIntersectsQuad(
        ray: com.example.vr.model.Ray3D,
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
        
        // Approximate check: is p within width/height of center on the quad plane?
        // Since we don't have the explicit right/up vectors, we can just use distance for a simple VR UI check
        // Or we can construct right/up. Let's assume up is roughly Y axis, unless normal is Y.
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
        ray: com.example.vr.model.Ray3D,
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
INNER_EOF
