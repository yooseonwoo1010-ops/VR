package com.example.vr.model

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 3D Vector representation for VR math and spatial computing
 */
data class Vector3(
    val x: Float = 0f,
    val y: Float = 0f,
    val z: Float = 0f
) {
    operator fun plus(v: Vector3) = Vector3(x + v.x, y + v.y, z + v.z)
    operator fun minus(v: Vector3) = Vector3(x - v.x, y - v.y, z - v.z)
    operator fun times(scalar: Float) = Vector3(x * scalar, y * scalar, z * scalar)
    operator fun div(scalar: Float) = if (scalar != 0f) Vector3(x / scalar, y / scalar, z / scalar) else this

    fun length(): Float = sqrt(x * x + y * y + z * z)
    
    fun normalized(): Vector3 {
        val len = length()
        return if (len > 0.0001f) this / len else Vector3(0f, 0f, 1f)
    }

    fun dot(v: Vector3): Float = x * v.x + y * v.y + z * v.z

    fun cross(v: Vector3): Vector3 = Vector3(
        y * v.z - z * v.y,
        z * v.x - x * v.z,
        x * v.y - y * v.x
    )

    fun distanceTo(v: Vector3): Float = (this - v).length()

    /**
     * Rotate vector around X, Y, Z axes (Euler angles in radians)
     */
    fun rotate(pitch: Float, yaw: Float, roll: Float): Vector3 {
        // Yaw (around Y)
        val cy = cos(yaw)
        val sy = sin(yaw)
        val x1 = x * cy + z * sy
        val y1 = y
        val z1 = -x * sy + z * cy

        // Pitch (around X)
        val cp = cos(pitch)
        val sp = sin(pitch)
        val x2 = x1
        val y2 = y1 * cp - z1 * sp
        val z2 = y1 * sp + z1 * cp

        // Roll (around Z)
        val cr = cos(roll)
        val sr = sin(roll)
        val x3 = x2 * cr - y2 * sr
        val y3 = x2 * sr + y2 * cr
        val z3 = z2

        return Vector3(x3, y3, z3)
    }
}

/**
 * VR Display Mode
 */
enum class VRDisplayMode {
    CARDBOARD_VR, // Side-by-side stereoscopic 3D for phone VR slot-in headsets
    FLAT_TEST     // 2D full-screen simulator for quick testing without headset
}

/**
 * Hand tracking gesture types
 */
enum class HandGesture {
    NONE,
    OPEN_PALM,      // Open palm: activates laser pointer raycast
    PINCH,          // Index + Thumb pinch: trigger click / grab / interact
    POINTING,       // Pointing index finger: precise laser pointer
    FIST,           // Closed fist: dragging / holding grabbed objects
    PEACE_SIGN,     // V sign: quick teleport / jump
    PALM_MENU       // Palm facing user: summons Quest Quick Menu
}

/**
 * Tracking source for hand gestures
 */
enum class HandTrackingSource {
    CAMERA_AI,      // Real-time camera CV hand tracking
    TOUCH_SIMULATOR // On-screen virtual hand controllers & touch
}

/**
 * Active VR Experience / App in Quest Home
 */
enum class VRExperience {
    HORIZON_HOME,    // Oculus Quest style Cyberpunk Loft with floating holographic UI
    RHYTHM_SABER,    // Beat rhythm game - cut neon blocks with hand laser sabers
    PHYSICS_SANDBOX, // Grab, throw, stack floating glowing 3D polyhedrons with gravity
    SPACE_ODYSSEY,   // 360 panoramic solar system & celestial planetarium
    TARGET_SHOOTER,  // Futuristic target shooting gallery with laser ray
    PASSTHROUGH_MR   // Mixed Reality mode blending camera feed with 3D holograms
}

/**
 * Hand skeleton tracking state for 3D rendering and raycasting
 */
data class TrackedHand(
    val isTracked: Boolean = false,
    val isLeft: Boolean = false,
    val position: Vector3 = Vector3(0f, 0f, 1.5f), // Normalized 3D position relative to camera
    val wristPosition: Vector3 = Vector3(0f, 0f, 1.5f),
    val indexTip: Vector3 = Vector3(0f, 0.1f, 1.4f),
    val thumbTip: Vector3 = Vector3(-0.05f, 0.05f, 1.4f),
    val middleTip: Vector3 = Vector3(0.04f, 0.12f, 1.4f),
    val ringTip: Vector3 = Vector3(0.08f, 0.09f, 1.4f),
    val pinkyTip: Vector3 = Vector3(0.12f, 0.05f, 1.4f),
    val contourPoints: List<Vector3> = emptyList(), // Real-time 3D hand silhouette outline points
    val pinchDistance: Float = 1.0f,
    val gesture: HandGesture = HandGesture.NONE,
    val confidence: Float = 0f,
    val laserRay: Ray3D? = null,
    val isPinching: Boolean = false,
    val isGrabbing: Boolean = false,
    val color: Long = 0xFF00E5FF // Cyan glow
)

/**
 * 3D Ray for gaze / hand pointing
 */
data class Ray3D(
    val origin: Vector3,
    val direction: Vector3
) {
    fun getPoint(distance: Float): Vector3 = origin + direction * distance
}

/**
 * 2D Projected Point with depth & visibility
 */
data class ProjectedPoint(
    val screenX: Float,
    val screenY: Float,
    val depth: Float,
    val isVisible: Boolean
)
