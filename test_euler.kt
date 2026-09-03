import android.opengl.Matrix
import kotlin.math.*

fun main() {
    val m = FloatArray(16)
    Matrix.setIdentityM(m, 0)
    Matrix.rotateM(m, 0, 45f, 0f, 1f, 0f) // yaw
    Matrix.rotateM(m, 0, 30f, 1f, 0f, 0f) // pitch
    Matrix.rotateM(m, 0, 10f, 0f, 0f, 1f) // roll
    
    // Extract Euler angles
    val pitch = asin(-m[6])
    val yaw = atan2(m[2], m[10])
    val roll = atan2(m[4], m[5])
    println("pitch: ${Math.toDegrees(pitch.toDouble())}")
    println("yaw: ${Math.toDegrees(yaw.toDouble())}")
    println("roll: ${Math.toDegrees(roll.toDouble())}")
}
