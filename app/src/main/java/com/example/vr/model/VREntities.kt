package com.example.vr.model

import androidx.compose.ui.graphics.Color
import kotlin.math.sin
import kotlin.random.Random

/**
 * Interactive 3D Holographic Menu Item in Quest Home
 */
data class HolographicCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val experience: VRExperience,
    val position: Vector3, // World position
    val width: Float = 0.8f,
    val height: Float = 0.5f,
    val iconEmoji: String = "🎮",
    val primaryColor: Long = 0xFF00E5FF,
    val isHovered: Boolean = false,
    val isSelected: Boolean = false
)

/**
 * Rhythm Beat Saber Block
 */
data class SaberBlock(
    val id: Long,
    var position: Vector3, // Moves from Z=12f down to Z=0f
    val color: Long, // 0xFFFF0055 (Red) or 0xFF00E5FF (Blue)
    val cutDirection: Int, // 0: Up, 1: Down, 2: Left, 3: Right, 4: Any/Dot
    val isCut: Boolean = false,
    var cutTimer: Float = 0f,
    val speed: Float = 4.0f,
    val size: Float = 0.35f
)

/**
 * Physics Sandbox 3D Object
 */
enum class PhysicsShape { CUBE, SPHERE, PYRAMID, CYLINDER }

data class PhysicsEntity(
    val id: Long,
    var position: Vector3,
    var velocity: Vector3 = Vector3(0f, 0f, 0f),
    var rotation: Vector3 = Vector3(0f, 0f, 0f),
    var angularVelocity: Vector3 = Vector3(0f, 0f, 0f),
    val shape: PhysicsShape = PhysicsShape.CUBE,
    val size: Float = 0.35f,
    val color: Long = 0xFF00E5FF,
    var isGrabbed: Boolean = false,
    var grabOffset: Vector3 = Vector3(0f, 0f, 0f)
)

/**
 * Space Odyssey Celestial Body
 */
data class PlanetEntity(
    val name: String,
    val nameKo: String,
    val emoji: String,
    val distance: Float,
    val radius: Float,
    val orbitSpeed: Float,
    var orbitAngle: Float,
    val color: Long,
    val hasRings: Boolean = false,
    val description: String = ""
)

/**
 * Target Gallery Target
 */
data class TargetEntity(
    val id: Long,
    var position: Vector3,
    val radius: Float = 0.4f,
    val points: Int = 100,
    val color: Long = 0xFFFF9100,
    var isHit: Boolean = false,
    var hitTimer: Float = 0f,
    var hoverOffset: Float = Random.nextFloat() * 6.28f
)

/**
 * 3D Particle for bursts & stars
 */
data class VRParticle(
    var position: Vector3,
    var velocity: Vector3,
    val color: Long,
    var alpha: Float = 1.0f,
    var size: Float = 4.0f,
    var life: Float = 1.0f,
    val maxLife: Float = 1.0f
)

/**
 * 3D Floating Grey Rounded-Corner Spatial Window (VR Box / MR Headset dialog as in user image)
 */
data class VRBoxWindowState(
    var anchorPos: Vector3 = Vector3(0f, 0f, 2.0f),
    var width: Float = 1.6f,
    var height: Float = 1.05f,
    var title: String = "VR BOX Spatial Mixed Reality",
    var subtitle: String = "Real-Time Video Passthrough & 6-DoF Head Tracking",
    var messageLines: List<String> = listOf(
        "• 실시간 스마트폰 외부 카메라 비디오를 배경으로 3D 공간 창이 표시됩니다.",
        "• 스마트폰을 VR BOX 헤드셋에 장착하고 머리를 움직여 둘러보세요.",
        "• 화면 중앙의 시선 조준점(Gaze Reticle)을 버튼에 맞추거나 화면을 터치하세요."
    ),
    var isPassthroughActive: Boolean = true,
    var ipdMm: Float = 64f,
    var isDisplayStereo: Boolean = true,
    var hoveredButtonId: String? = null,
    var gazeDwellProgress: Float = 0f,
    var isVisible: Boolean = true
)

data class VRBoxButton(
    val id: String,
    val label: String,
    val relX: Float,
    val relY: Float,
    val width: Float,
    val height: Float,
    val isPrimary: Boolean = false,
    val isAccent: Boolean = false
)

data class QuestQuickSettingsState(
    var anchorPos: Vector3 = Vector3(0f, 0.2f, 2.0f),
    var width: Float = 1.7f,
    var height: Float = 1.05f,
    var isWifiEnabled: Boolean = true,
    var wifiName: String = "Quest-Ultra-5G",
    var isGuardianEnabled: Boolean = true,
    var isQuestLinkActive: Boolean = false,
    var isMicMuted: Boolean = false,
    var isPassthroughEnabled: Boolean = false,
    var volumeLevel: Float = 0.8f,
    var brightnessLevel: Float = 0.9f,
    var isNightMode: Boolean = false,
    var isRecording: Boolean = false,
    var batteryPercent: Int = 94,
    var isCharging: Boolean = false,
    var timeString: String = "09:41",
    // Interactive hover states for laser raycast
    var hoveredElementId: String? = null,
    var isVisible: Boolean = true
)

data class QuestDockApp(
    val id: String,
    val name: String,
    val iconEmoji: String,
    val color: Long,
    val experience: VRExperience? = null,
    val isSystem: Boolean = false
)

data class QuestDockState(
    var anchorPos: Vector3 = Vector3(0f, -0.6f, 1.8f),
    var width: Float = 1.6f,
    var height: Float = 0.26f,
    var apps: List<QuestDockApp> = listOf(
        QuestDockApp("store", "Store", "🛒", 0xFF1976D2),
        QuestDockApp("saber", "Beat Saber", "⚔️", 0xFFFF0055, VRExperience.RHYTHM_SABER),
        QuestDockApp("physics", "Physics 3D", "🎲", 0xFF00E5FF, VRExperience.PHYSICS_SANDBOX),
        QuestDockApp("space", "Space 360", "🪐", 0xFF7C4DFF, VRExperience.SPACE_ODYSSEY),
        QuestDockApp("target", "Target Range", "🎯", 0xFFFF9100, VRExperience.TARGET_SHOOTER),
        QuestDockApp("passthrough", "MR Camera", "👓", 0xFF00E676, VRExperience.PASSTHROUGH_MR),
        QuestDockApp("browser", "Browser", "🌐", 0xFF00BCD4),
        QuestDockApp("library", "Files", "📁", 0xFFFF9800),
        QuestDockApp("settings", "Settings", "⚙️", 0xFF607D8B)
    ),
    var hoveredAppId: String? = null
)

