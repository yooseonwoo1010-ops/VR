sed -i 's/fun rayIntersectsQuad(/fun rayIntersectsQuad2(/g' app/src/main/java/com/example/vr/engine/VRMath.kt
sed -i 's/fun rayIntersectsSphere(/fun rayIntersectsSphere2(/g' app/src/main/java/com/example/vr/engine/VRMath.kt
cat << 'INNER_EOF' >> app/src/main/java/com/example/vr/engine/VRMath.kt

    fun rayIntersectsQuad(
        rayOrigin: Vector3,
        rayDir: Vector3,
        v0: Vector3,
        v1: Vector3,
        v2: Vector3,
        v3: Vector3
    ): Float? {
        val n = (v1 - v0).cross(v2 - v0).normalized()
        val denom = n.dot(rayDir)
        if (abs(denom) < 1e-6f) return null
        
        val t = (v0 - rayOrigin).dot(n) / denom
        if (t < 0) return null
        
        val p = rayOrigin + rayDir * t
        
        val edge0 = v1 - v0
        val edge1 = v2 - v1
        val edge2 = v3 - v2
        val edge3 = v0 - v3
        
        val c0 = p - v0
        val c1 = p - v1
        val c2 = p - v2
        val c3 = p - v3
        
        if (n.dot(edge0.cross(c0)) > 0 &&
            n.dot(edge1.cross(c1)) > 0 &&
            n.dot(edge2.cross(c2)) > 0 &&
            n.dot(edge3.cross(c3)) > 0) return t
            
        return null
    }

    fun rayIntersectsSphere(
        rayOrigin: Vector3,
        rayDir: Vector3,
        center: Vector3,
        radius: Float
    ): Float? {
        val l = center - rayOrigin
        val tca = l.dot(rayDir)
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
