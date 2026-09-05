package com.example.vr.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.rememberTextMeasurer
import com.example.vr.model.VirtualWindow
import androidx.compose.ui.unit.sp
import com.example.vr.model.*
import com.example.vr.tracking.HeadOrientation

@Composable
fun VRFlatView(
    headOrientation: HeadOrientation,
    fov: Float,
    experience: VRExperience,
    vrBoxWindow: VRBoxWindowState,
    virtualWindow: VirtualWindow,
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
    onDragLookAround: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val centerCameraPos = Vector3(0f, 0f, 0f)
        val isPassthrough = vrBoxWindow.isPassthroughActive || questSettings.isPassthroughEnabled || experience == VRExperience.PASSTHROUGH_MR

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isPassthrough) Color.Transparent else Color(0xFF030712))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDragLookAround(dragAmount.x, dragAmount.y)
                }
            }
            .testTag("vr_flat_test_view")
    ) {
        // Fullscreen 2D Canvas Viewport
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("flat_view_canvas")
        ) {
            VRRenderer.drawEyeView(
                drawScope = this,
                cameraPos = centerCameraPos,
                headOrientation = headOrientation,
                fov = fov,
                experience = experience,
                vrBoxWindow = vrBoxWindow,
                        virtualWindow = virtualWindow,
                        textMeasurer = textMeasurer,
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
                isStereo = false
            )
        }

        // Subtitle instructions
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .background(Color(0x88000000), shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = "📱 2D 테스트 모드 (화면 드래그 및 스마트폰 6축 센서로 360° 시점 제어)",
                color = Color(0xFF00E5FF),
                fontSize = 12.sp
            )
        }
    }
}
