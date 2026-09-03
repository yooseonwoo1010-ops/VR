sed -i '/import com.example.vr.model.ViewPoint/d' app/src/main/java/com/example/vr/engine/VRMath.kt
sed -i '/object VRMath {/i data class ViewPoint(val x: Float, val y: Float, val z: Float)' app/src/main/java/com/example/vr/engine/VRMath.kt
