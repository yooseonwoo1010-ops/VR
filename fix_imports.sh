sed -i '/data class Vector3/d' app/src/main/java/com/example/vr/engine/VRMath.kt
sed -i '/operator fun/d' app/src/main/java/com/example/vr/engine/VRMath.kt
sed -i '/import kotlin.math.\*/a import com.example.vr.model.Vector3' app/src/main/java/com/example/vr/engine/VRMath.kt
