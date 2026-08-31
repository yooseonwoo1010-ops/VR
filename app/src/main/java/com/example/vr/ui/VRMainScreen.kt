package com.example.vr.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.vr.engine.VREnvironmentEngine
import com.example.vr.handtracking.HandTrackingManager
import com.example.vr.model.HandTrackingSource
import com.example.vr.model.VRDisplayMode
import com.example.vr.model.VRExperience
import com.example.vr.tracking.HeadTracker
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun VRMainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Instantiate HeadTracker, HandTrackingManager, and VREnvironmentEngine
    val headTracker = remember { HeadTracker(context) }
    val handManager = remember { HandTrackingManager(context) }
    val vrEngine = remember { VREnvironmentEngine(context) }

    // State Collection
    val headOrientation by headTracker.orientation.collectAsState()
    val rightHand by handManager.rightHand.collectAsState()
    val leftHand by handManager.leftHand.collectAsState()
    val trackingSource by handManager.trackingSource.collectAsState()
    val useFrontCamera by handManager.useFrontCamera.collectAsState()

    val displayMode by vrEngine.displayMode.collectAsState()
    val currentExperience by vrEngine.experience.collectAsState()
    val ipdMm by vrEngine.ipdMm.collectAsState()
    val fov by vrEngine.fov.collectAsState()
    val isMenuOpen by vrEngine.isMenuOpen.collectAsState()
    val questSettings by vrEngine.questSettings.collectAsState()
    val questDock by vrEngine.questDock.collectAsState()
    val menuCards by vrEngine.menuCards.collectAsState()
    val saberBlocks by vrEngine.saberBlocks.collectAsState()
    val physicsEntities by vrEngine.physicsEntities.collectAsState()
    val planets by vrEngine.planets.collectAsState()
    val targets by vrEngine.targets.collectAsState()
    val particles by vrEngine.particles.collectAsState()
    val score by vrEngine.score.collectAsState()
    val combo by vrEngine.combo.collectAsState()
    val lastActionText by vrEngine.lastActionText.collectAsState()

    // 10-Second Auto-Hide UI Inactivity Timer & Tap to Restore
    var isUiVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Auto-hide UI after 10 seconds of inactivity
    LaunchedEffect(lastInteractionTime, isUiVisible) {
        if (isUiVisible) {
            delay(10_000L)
            isUiVisible = false
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            handManager.startCameraTracking(lifecycleOwner)
        }
    }

    // Start sensors & camera
    DisposableEffect(Unit) {
        headTracker.start()
        if (hasCameraPermission) {
            handManager.startCameraTracking(lifecycleOwner)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        onDispose {
            headTracker.stop()
            handManager.stop()
            vrEngine.cleanup()
        }
    }

    // Bind camera when camera lens changes
    LaunchedEffect(useFrontCamera) {
        if (hasCameraPermission) {
            handManager.bindCameraAnalysis(lifecycleOwner)
        }
    }

    // 60 FPS VR Simulation Loop
    LaunchedEffect(Unit) {
        var lastTime = System.nanoTime()
        while (isActive) {
            withFrameNanos { now ->
                val dt = ((now - lastTime) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastTime = now
                vrEngine.updateWorld(dt, rightHand, leftHand, headOrientation)
            }
        }
    }

    val isPassthroughActive = questSettings.isPassthroughEnabled || currentExperience == VRExperience.PASSTHROUGH_MR

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures {
                    // Tap anywhere reveals the UI and resets the 10-second timer
                    isUiVisible = true
                    lastInteractionTime = System.currentTimeMillis()
                }
            }
            .testTag("vr_main_screen")
    ) {
        // 1. Real-World External Camera Passthrough Feed (Back/Rear Camera as background)
        if (isPassthroughActive && hasCameraPermission) {
            CameraPassthroughView(
                useFrontCamera = useFrontCamera,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 2. 3D Spatial Rendering Layer (Stereoscopic VR Headset or 2D Flat Mode)
        if (displayMode == VRDisplayMode.CARDBOARD_VR) {
            VRStereoscopicView(
                headOrientation = headOrientation,
                ipdMm = ipdMm,
                fov = fov,
                experience = currentExperience,
                isMenuOpen = isMenuOpen,
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
                modifier = Modifier.fillMaxSize()
            )
        } else {
            VRFlatView(
                headOrientation = headOrientation,
                fov = fov,
                experience = currentExperience,
                isMenuOpen = isMenuOpen,
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
                onDragLookAround = { dx, dy ->
                    headTracker.onDrag(dx, dy)
                    lastInteractionTime = System.currentTimeMillis()
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. Camera Live Hand Tracking Status Badge (when in Camera AI mode)
        if (hasCameraPermission && trackingSource == HandTrackingSource.CAMERA_AI && isUiVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xDD0F172A))
                    .border(1.dp, Color(0xFF00E5FF), RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (rightHand.isTracked || leftHand.isTracked) Color(0xFF00E5FF) else Color(0xFFFF9100))
                    )
                    Text(
                        text = if (rightHand.isTracked || leftHand.isTracked) "AI 핸드 감지됨" else "AI 핸드 탐색 중",
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // 4. Virtual Hand Controllers Overlay (in Flat mode or Simulator mode) with 10s auto-hide
        AnimatedVisibility(
            visible = isUiVisible && (trackingSource == HandTrackingSource.TOUCH_SIMULATOR || displayMode == VRDisplayMode.FLAT_TEST),
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            HandGestureSimulatorPanel(
                onUpdateHand = { isRight, x, y, g, pinch ->
                    lastInteractionTime = System.currentTimeMillis()
                    handManager.updateVirtualHand(isRight, x, y, g, pinch)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 5. Floating Quest Universal Menu & Quick Bar (Controlled by 10s auto-hide timer)
        AnimatedVisibility(
            visible = isUiVisible,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            QuestUniversalMenu(
                isMenuOpen = isMenuOpen,
                displayMode = displayMode,
                currentExperience = currentExperience,
                trackingSource = trackingSource,
                useFrontCamera = useFrontCamera,
                ipdMm = ipdMm,
                fov = fov,
                score = score,
                combo = combo,
                lastActionText = lastActionText,
                onToggleMenu = {
                    lastInteractionTime = System.currentTimeMillis()
                    vrEngine.toggleMenu()
                },
                onSetDisplayMode = { mode ->
                    lastInteractionTime = System.currentTimeMillis()
                    vrEngine.setDisplayMode(mode)
                },
                onSelectExperience = { exp ->
                    lastInteractionTime = System.currentTimeMillis()
                    vrEngine.switchExperience(exp)
                },
                onSetTrackingSource = { src ->
                    lastInteractionTime = System.currentTimeMillis()
                    handManager.setTrackingSource(src)
                },
                onToggleCameraLens = {
                    lastInteractionTime = System.currentTimeMillis()
                    handManager.toggleCameraLens()
                },
                onSetIpd = { ipd ->
                    lastInteractionTime = System.currentTimeMillis()
                    vrEngine.setIpdMm(ipd)
                },
                onSetFov = { newFov ->
                    lastInteractionTime = System.currentTimeMillis()
                    vrEngine.setFov(newFov)
                },
                onRecenter = {
                    lastInteractionTime = System.currentTimeMillis()
                    headTracker.recenter()
                    vrEngine.recenterSpatialAnchor(headOrientation)
                }
            )
        }

        // 6. Subtle hint badge when UI is hidden
        AnimatedVisibility(
            visible = !isUiVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .padding(bottom = 16.dp)
                    .background(Color(0x77000000), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0x4400E5FF), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "👆 화면을 터치하면 메뉴와 컨트롤러가 다시 나타납니다 (10초 자동 숨김)",
                    color = Color(0xCCFFFFFF),
                    fontSize = 11.sp
                )
            }
        }
    }
}
