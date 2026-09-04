import android.opengl.Matrix

fun main() {
    val m = FloatArray(16)
    Matrix.setIdentityM(m, 0)
    Matrix.rotateM(m, 0, 90f, 0f, 1f, 0f)
    val v = floatArrayOf(1f, 0f, 0f, 1f)
    val r = FloatArray(4)
    Matrix.multiplyMV(r, 0, m, 0, v, 0)
    println("${r[0]}, ${r[1]}, ${r[2]}")
}
