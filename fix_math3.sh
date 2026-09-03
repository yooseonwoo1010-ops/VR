sed -i '/data class ProjectedPoint/d' app/src/main/java/com/example/vr/engine/VRMath.kt
sed -i '/data class ViewPoint/d' app/src/main/java/com/example/vr/engine/VRMath.kt
sed -i '/import com.example.vr.model.Vector3/a import com.example.vr.model.ProjectedPoint\nimport com.example.vr.model.ViewPoint' app/src/main/java/com/example/vr/engine/VRMath.kt
