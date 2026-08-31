package com.example.vr.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vr.model.HandTrackingSource
import com.example.vr.model.VRDisplayMode
import com.example.vr.model.VRExperience

@Composable
fun QuestUniversalMenu(
    isMenuOpen: Boolean,
    displayMode: VRDisplayMode,
    currentExperience: VRExperience,
    trackingSource: HandTrackingSource,
    useFrontCamera: Boolean,
    ipdMm: Float,
    fov: Float,
    score: Int,
    combo: Int,
    lastActionText: String,
    onToggleMenu: () -> Unit,
    onSetDisplayMode: (VRDisplayMode) -> Unit,
    onSelectExperience: (VRExperience) -> Unit,
    onSetTrackingSource: (HandTrackingSource) -> Unit,
    onToggleCameraLens: () -> Unit,
    onSetIpd: (Float) -> Unit,
    onSetFov: (Float) -> Unit,
    onRecenter: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSettingsDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag("quest_universal_menu_container")
    ) {
        // Top HUD Bar (Experience Name, Score, Combo, Gesture Helper)
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xCC0B132B))
                .border(1.dp, Color(0x4400E5FF), RoundedCornerShape(24.dp))
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Mode Badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (displayMode == VRDisplayMode.CARDBOARD_VR) Color(0xFF7C4DFF) else Color(0xFF00E5FF)
            ) {
                Text(
                    text = if (displayMode == VRDisplayMode.CARDBOARD_VR) "🥽 VR 기기 모드" else "📱 2D 테스트 모드",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            // Current App Title
            Text(
                text = when (currentExperience) {
                    VRExperience.HORIZON_HOME -> "Horizon Home"
                    VRExperience.RHYTHM_SABER -> "Beat Saber VR"
                    VRExperience.PHYSICS_SANDBOX -> "Physics 3D Lab"
                    VRExperience.SPACE_ODYSSEY -> "Space Odyssey 360"
                    VRExperience.TARGET_SHOOTER -> "Target Range"
                    VRExperience.PASSTHROUGH_MR -> "Passthrough MR"
                },
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )

            if (score > 0) {
                Text(
                    text = "SCORE: $score",
                    color = Color(0xFFFFD600),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            if (combo > 1) {
                Text(
                    text = "${combo}X COMBO",
                    color = Color(0xFFFF0055),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }

            // Status message
            Text(
                text = "• $lastActionText",
                color = Color(0xFF94A3B8),
                fontSize = 11.sp,
                maxLines = 1
            )
        }

        // Bottom Oculus Quest 2 Floating Quick Bar / Dock
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = Color(0xE60A0F1D),
                modifier = Modifier
                    .border(1.5.dp, Color(0x6600E5FF), RoundedCornerShape(32.dp))
                    .testTag("quest_quick_dock")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Mode Switch Button (VR SBS <-> 2D Flat)
                    DockIconButton(
                        icon = if (displayMode == VRDisplayMode.CARDBOARD_VR) Icons.Default.ViewInAr else Icons.Default.Smartphone,
                        label = if (displayMode == VRDisplayMode.CARDBOARD_VR) "VR 헤드셋" else "2D 테스트",
                        isActive = true,
                        activeColor = if (displayMode == VRDisplayMode.CARDBOARD_VR) Color(0xFF7C4DFF) else Color(0xFF00E5FF),
                        testTag = "toggle_vr_mode_button",
                        onClick = {
                            if (displayMode == VRDisplayMode.CARDBOARD_VR) {
                                onSetDisplayMode(VRDisplayMode.FLAT_TEST)
                            } else {
                                onSetDisplayMode(VRDisplayMode.CARDBOARD_VR)
                            }
                        }
                    )

                    // Home / App Library Button
                    DockIconButton(
                        icon = Icons.Default.Home,
                        label = "홈 대시보드",
                        isActive = currentExperience == VRExperience.HORIZON_HOME,
                        activeColor = Color(0xFF00E5FF),
                        testTag = "home_menu_button",
                        onClick = { onSelectExperience(VRExperience.HORIZON_HOME) }
                    )

                    // Hand Tracking Mode Switch (Camera AI <-> Virtual Controller)
                    DockIconButton(
                        icon = if (trackingSource == HandTrackingSource.CAMERA_AI) Icons.Default.PanTool else Icons.Default.SportsEsports,
                        label = if (trackingSource == HandTrackingSource.CAMERA_AI) "카메라 핸드" else "가상 컨트롤러",
                        isActive = true,
                        activeColor = if (trackingSource == HandTrackingSource.CAMERA_AI) Color(0xFF00E676) else Color(0xFFFF9100),
                        testTag = "toggle_hand_tracking_button",
                        onClick = {
                            if (trackingSource == HandTrackingSource.CAMERA_AI) {
                                onSetTrackingSource(HandTrackingSource.TOUCH_SIMULATOR)
                            } else {
                                onSetTrackingSource(HandTrackingSource.CAMERA_AI)
                            }
                        }
                    )

                    // Recenter / Zero Drift Button
                    DockIconButton(
                        icon = Icons.Default.Explore,
                        label = "시점 정렬",
                        isActive = false,
                        testTag = "recenter_button",
                        onClick = onRecenter
                    )

                    // Camera Front/Back Switch (for Hand Tracking or Passthrough)
                    DockIconButton(
                        icon = Icons.Default.FlipCameraAndroid,
                        label = if (useFrontCamera) "전면 카메라" else "후면 카메라",
                        isActive = false,
                        testTag = "camera_switch_button",
                        onClick = onToggleCameraLens
                    )

                    // VR Settings Modal
                    DockIconButton(
                        icon = Icons.Default.Settings,
                        label = "VR 설정 (IPD)",
                        isActive = showSettingsDialog,
                        testTag = "vr_settings_button",
                        onClick = { showSettingsDialog = true }
                    )
                }
            }
        }

        // Settings Dialog (IPD, FOV, Sensitivity)
        if (showSettingsDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF00E5FF))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Quest 2 VR 광학 설정", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // IPD Lens Spacing Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("렌즈 간격 (IPD):", color = Color.White, fontSize = 13.sp)
                                Text("${ipdMm.toInt()} mm (Quest 2 1~3단계)", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Slider(
                                value = ipdMm,
                                onValueChange = onSetIpd,
                                valueRange = 54f..74f,
                                modifier = Modifier.testTag("ipd_slider")
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("58mm (1단)", color = Color.Gray, fontSize = 10.sp)
                                Text("63mm (2단)", color = Color.Gray, fontSize = 10.sp)
                                Text("68mm (3단)", color = Color.Gray, fontSize = 10.sp)
                            }
                        }

                        // FOV Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("시야각 (FOV):", color = Color.White, fontSize = 13.sp)
                                Text("${fov.toInt()}°", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Slider(
                                value = fov,
                                onValueChange = onSetFov,
                                valueRange = 65f..95f,
                                modifier = Modifier.testTag("fov_slider")
                            )
                        }

                        // Hand tracking gesture guide
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1E293B),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("✋ 핸드 트래킹 제스처 가이드", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("🤏 핀치(검지+엄지 맞닿기): 3D 오브젝트 클릭 & 잡기", color = Color.LightGray, fontSize = 11.sp)
                                Text("✋ 손바닥 펼치기: 레이저 포인터 조준광선 발사", color = Color.LightGray, fontSize = 11.sp)
                                Text("✊ 주먹 쥐기: 물체 잡고 던지기 / 이동", color = Color.LightGray, fontSize = 11.sp)
                                Text("🖐️ 손목 보기: 퀘스트 빠른 메뉴 소환", color = Color.LightGray, fontSize = 11.sp)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showSettingsDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        modifier = Modifier.testTag("settings_done_button")
                    ) {
                        Text("완료", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = Color(0xFF0F172A),
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

@Composable
private fun DockIconButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color = Color(0xFF00E5FF),
    testTag: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(testTag)
    ) {
        Surface(
            shape = CircleShape,
            color = if (isActive) activeColor.copy(alpha = 0.25f) else Color(0x33334155),
            border = if (isActive) androidx.compose.foundation.BorderStroke(1.5.dp, activeColor) else null,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) activeColor else Color.LightGray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = if (isActive) activeColor else Color(0xFF94A3B8),
            fontSize = 9.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
        )
    }
}
