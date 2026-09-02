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
    val vrBoxWindow by vrEngine.vrBoxWindow.collectAsState()
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

    var isUiVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

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

    val isPassthroughActive = vrBoxWindow.isPassthroughActive || questSettings.isPassthroughEnabled || currentExperience == VRExperience.PASSTHROUGH_MR

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val screenW = size.width.toFloat()
                    val screenH = size.height.toFloat()
                    vrEngine.onScreenTouchPosition(offset.x, offset.y, screenW, screenH, headOrientation)
                    lastInteractionTime = System.currentTimeMillis()
                }
            }
            .testTag("vr_main_screen")
    ) {
        // 1. Real-Time Live External Camera Video Passthrough Feed (MR background)
        if (isPassthroughActive && hasCameraPermission) {
            CameraPassthroughView(
                handManager = handManager,
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
                vrBoxWindow = vrBoxWindow,
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
                vrBoxWindow = vrBoxWindow,
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

        // 3. Clean Minimalist Top HUD Bar (Quick VR Mode Toggle & Recenter)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xCC1E293B))
                .border(1.dp, Color(0xFF475569), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // VR Stereoscopic / 2D Flat Mode Toggle
                Surface(
                    onClick = {
                        val nextMode = if (displayMode == VRDisplayMode.CARDBOARD_VR) VRDisplayMode.FLAT_TEST else VRDisplayMode.CARDBOARD_VR
                        vrEngine.setDisplayMode(nextMode)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (displayMode == VRDisplayMode.CARDBOARD_VR) Color(0xFF0284C7) else Color(0xFF334155),
                    modifier = Modifier.testTag("btn_toggle_vr_mode")
                ) {
                    Text(
                        text = if (displayMode == VRDisplayMode.CARDBOARD_VR) "🥽 VR BOX (좌우분할)" else "📱 2D 단일화면",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                // Recenter Anchor Button
                Surface(
                    onClick = {
                        headTracker.recenter()
                        vrEngine.recenterSpatialAnchor(headOrientation)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF334155),
                    modifier = Modifier.testTag("btn_hud_recenter")
                ) {
                    Text(
                        text = "🧭 시점 정렬",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }

                // Passthrough Camera Toggle
                Surface(
                    onClick = {
                        vrEngine.togglePassthrough()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = if (isPassthroughActive) Color(0xFF059669) else Color(0xFF334155),
                    modifier = Modifier.testTag("btn_hud_passthrough")
                ) {
                    Text(
                        text = if (isPassthroughActive) "📷 MR 비디오: ON" else "🌌 VR 공간모드",
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }
    }
}
