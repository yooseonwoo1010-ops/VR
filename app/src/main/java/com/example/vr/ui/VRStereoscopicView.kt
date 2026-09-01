package com.example.vr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.vr.model.*
import com.example.vr.tracking.HeadOrientation

@Composable
fun VRStereoscopicView(
    headOrientation: HeadOrientation,
    ipdMm: Float,
    fov: Float,
    experience: VRExperience,
    vrBoxWindow: VRBoxWindowState,
    isMenuOpen: Boolean,
    questSettings: QuestQuickSettingsState,
    questDock: QuestDockState,
    menuCards: List<HolographicCard>,
    saberBlocks: List<SaberBlock>,
    physicsEntities: List<PhysicsEntity>,
    planets: List<PlanetEntity>,
    targets: List<TargetEntity>,
    particles: List<VRParticle>,
    rightHand: TrackedHand,
    leftHand: TrackedHand,
    score: Int,
    combo: Int,
    modifier: Modifier = Modifier
) {
    // Interpupillary distance in meters (e.g. 64mm -> 0.064m)
    val ipdMeters = (ipdMm / 1000f) * 0.5f
    val leftEyeCameraPos = Vector3(-ipdMeters, 0f, 0f)
    val rightEyeCameraPos = Vector3(ipdMeters, 0f, 0f)

    val isPassthrough = vrBoxWindow.isPassthroughActive || questSettings.isPassthroughEnabled || experience == VRExperience.PASSTHROUGH_MR

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isPassthrough) Color.Transparent else Color.Black)
            .testTag("vr_stereoscopic_view")
    ) {
        // Row with 2 equal half screens: Left Eye & Right Eye
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // LEFT EYE VIEWPORT
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Canvas(modifier = Modifier.fillMaxSize().testTag("left_eye_canvas")) {
                    VRRenderer.drawEyeView(
                        drawScope = this,
                        cameraPos = leftEyeCameraPos,
                        headOrientation = headOrientation,
                        fov = fov,
                        experience = experience,
                        vrBoxWindow = vrBoxWindow,
                        questSettings = questSettings,
                        questDock = questDock,
                        menuCards = menuCards,
                        saberBlocks = saberBlocks,
                        physicsEntities = physicsEntities,
                        planets = planets,
                        targets = targets,
                        particles = particles,
                        rightHand = rightHand,
                        leftHand = leftHand,
                        score = score,
                        combo = combo,
                        isStereo = true
                    )

                    // VR Optical Lens Vignette Shader Simulation (Soft subtle lens vignette)
                    val radius = size.minDimension * 0.75f
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = if (isPassthrough) {
                                listOf(Color.Transparent, Color.Transparent, Color(0x33000000), Color(0x88000000))
                            } else {
                                listOf(Color.Transparent, Color.Transparent, Color(0x99000000), Color(0xFF000000))
                            },
                            center = Offset(size.width * 0.5f, size.height * 0.5f),
                            radius = radius
                        )
                    )
                }
            }

            // RIGHT EYE VIEWPORT
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Canvas(modifier = Modifier.fillMaxSize().testTag("right_eye_canvas")) {
                    VRRenderer.drawEyeView(
                        drawScope = this,
                        cameraPos = rightEyeCameraPos,
                        headOrientation = headOrientation,
                        fov = fov,
                        experience = experience,
                        vrBoxWindow = vrBoxWindow,
                        questSettings = questSettings,
                        questDock = questDock,
                        menuCards = menuCards,
                        saberBlocks = saberBlocks,
                        physicsEntities = physicsEntities,
                        planets = planets,
                        targets = targets,
                        particles = particles,
                        rightHand = rightHand,
                        leftHand = leftHand,
                        score = score,
                        combo = combo,
                        isStereo = true
                    )

                    // VR Optical Lens Vignette Shader Simulation
                    val radius = size.minDimension * 0.75f
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = if (isPassthrough) {
                                listOf(Color.Transparent, Color.Transparent, Color(0x33000000), Color(0x88000000))
                            } else {
                                listOf(Color.Transparent, Color.Transparent, Color(0x99000000), Color(0xFF000000))
                            },
                            center = Offset(size.width * 0.5f, size.height * 0.5f),
                            radius = radius
                        )
                    )
                }
            }
        }

        // Center VR Headset Divider Marker Line & Lens Alignment Notch
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(2.dp)
                .fillMaxHeight()
                .background(Color(0xFF1E293B).copy(alpha = 0.8f))
        )
    }
}
