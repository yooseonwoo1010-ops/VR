package com.example.vr.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.example.vr.model.*
import com.example.vr.tracking.HeadOrientation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.*
import kotlin.random.Random

class VREnvironmentEngine(private val context: Context) {

    // Real Phone Battery & Charging State
    private var realBatteryPercent: Int = 100
    private var isBatteryCharging: Boolean = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                if (level >= 0 && scale > 0) {
                    realBatteryPercent = ((level.toFloat() / scale.toFloat()) * 100).toInt().coerceIn(0, 100)
                }
                isBatteryCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
    }

    init {
        // Fetch initial real battery status immediately
        try {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(batteryReceiver, filter)
            batteryStatus?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                if (level >= 0 && scale > 0) {
                    realBatteryPercent = ((level.toFloat() / scale.toFloat()) * 100).toInt().coerceIn(0, 100)
                }
                isBatteryCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
            }
        } catch (e: Exception) {
            // Fallback default
            realBatteryPercent = 94
        }
    }

    // Current Active VR Experience (Default to HORIZON_HOME with floating Quest 2 3D window)
    private val _experience = MutableStateFlow(VRExperience.HORIZON_HOME)
    val experience: StateFlow<VRExperience> = _experience.asStateFlow()

    // VR Display Mode (Cardboard VR Split SBS vs Flat 2D Test Mode)
    private val _displayMode = MutableStateFlow(VRDisplayMode.CARDBOARD_VR)
    val displayMode: StateFlow<VRDisplayMode> = _displayMode.asStateFlow()

    // Optical IPD (Interpupillary Distance) in meters (e.g. 0.064m = 64mm standard)
    private val _ipdMm = MutableStateFlow(64f)
    val ipdMm: StateFlow<Float> = _ipdMm.asStateFlow()

    // Field of View
    private val _fov = MutableStateFlow(55f)
    val fov: StateFlow<Float> = _fov.asStateFlow()

    // Universal Menu Open/Closed state
    private val _isMenuOpen = MutableStateFlow(true)
    val isMenuOpen: StateFlow<Boolean> = _isMenuOpen.asStateFlow()

    // 3D Floating Grey Rounded-Corner Window State (VR BOX / MR System Dialog)
    private val _vrBoxWindow = MutableStateFlow(VRBoxWindowState())
    val vrBoxWindow: StateFlow<VRBoxWindowState> = _vrBoxWindow.asStateFlow()

    // 3D Spatial Quest Quick Settings & Universal Dock
    private val _questSettings = MutableStateFlow(QuestQuickSettingsState(isVisible = false))
    val questSettings: StateFlow<QuestQuickSettingsState> = _questSettings.asStateFlow()

    private val _questDock = MutableStateFlow(QuestDockState())
    val questDock: StateFlow<QuestDockState> = _questDock.asStateFlow()


    // Holographic Menu Cards (Quest Home Dashboard)
    private val _menuCards = MutableStateFlow(
        listOf(
            HolographicCard(
                id = "saber",
                title = "Rhythm Saber",
                subtitle = "비트 세이버 리듬 액션",
                experience = VRExperience.RHYTHM_SABER,
                position = Vector3(-0.9f, 0.2f, 2.2f),
                iconEmoji = "⚔️",
                primaryColor = 0xFFFF0055
            ),
            HolographicCard(
                id = "sandbox",
                title = "Physics Sandbox",
                subtitle = "무중력 3D 물리 공간",
                experience = VRExperience.PHYSICS_SANDBOX,
                position = Vector3(0f, 0.2f, 2.4f),
                iconEmoji = "🎲",
                primaryColor = 0xFF00E5FF
            ),
            HolographicCard(
                id = "space",
                title = "Space Odyssey",
                subtitle = "360° 태양계 우주 탐험",
                experience = VRExperience.SPACE_ODYSSEY,
                position = Vector3(0.9f, 0.2f, 2.2f),
                iconEmoji = "🪐",
                primaryColor = 0xFF7C4DFF
            ),
            HolographicCard(
                id = "shooter",
                title = "Target Range",
                subtitle = "레이저 사격 훈련장",
                experience = VRExperience.TARGET_SHOOTER,
                position = Vector3(-0.55f, -0.4f, 2.3f),
                iconEmoji = "🎯",
                primaryColor = 0xFFFF9100
            ),
            HolographicCard(
                id = "passthrough",
                title = "Passthrough MR",
                subtitle = "실시간 혼합현실 (카메라)",
                experience = VRExperience.PASSTHROUGH_MR,
                position = Vector3(0.55f, -0.4f, 2.3f),
                iconEmoji = "👓",
                primaryColor = 0xFF00E676
            )
        )
    )
    val menuCards: StateFlow<List<HolographicCard>> = _menuCards.asStateFlow()

    // Rhythm Saber State
    private val _saberBlocks = MutableStateFlow<List<SaberBlock>>(emptyList())
    val saberBlocks: StateFlow<List<SaberBlock>> = _saberBlocks.asStateFlow()
    private var blockSpawnTimer = 0f

    // Physics Sandbox State
    private val _physicsEntities = MutableStateFlow<List<PhysicsEntity>>(emptyList())
    val physicsEntities: StateFlow<List<PhysicsEntity>> = _physicsEntities.asStateFlow()

    // Space Odyssey Planets
    private val _planets = MutableStateFlow<List<PlanetEntity>>(emptyList())
    val planets: StateFlow<List<PlanetEntity>> = _planets.asStateFlow()

    // Target Shooter Targets
    private val _targets = MutableStateFlow<List<TargetEntity>>(emptyList())
    val targets: StateFlow<List<TargetEntity>> = _targets.asStateFlow()

    // Global Particles
    private val _particles = MutableStateFlow<List<VRParticle>>(emptyList())
    val particles: StateFlow<List<VRParticle>> = _particles.asStateFlow()

    // Scores & Stats
    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    private val _combo = MutableStateFlow(0)
    val combo: StateFlow<Int> = _combo.asStateFlow()

    private val _lastActionText = MutableStateFlow("손 제스처(핀치/가리키기)로 조작하세요")
    val lastActionText: StateFlow<String> = _lastActionText.asStateFlow()

    private var lastPinchProcessed = false

    private var grabbedPhysicsId: Long? = null

    init {
        initPhysicsSandbox()
        initSolarSystem()
        initTargetShooter()
    }

    fun setDisplayMode(mode: VRDisplayMode) {
        _displayMode.value = mode
        triggerHaptic()
    }

    fun setIpdMm(ipd: Float) {
        _ipdMm.value = ipd.coerceIn(52f, 76f)
    }

    fun setFov(fov: Float) {
        _fov.value = fov.coerceIn(40f, 100f)
    }

    fun toggleMenu() {
        _isMenuOpen.value = !_isMenuOpen.value
        triggerHaptic()
    }

    fun switchExperience(exp: VRExperience) {
        _experience.value = exp
        _score.value = 0
        _combo.value = 0
        _isMenuOpen.value = false
        triggerHaptic(50)

        when (exp) {
            VRExperience.RHYTHM_SABER -> {
                _saberBlocks.value = emptyList()
                _lastActionText.value = "다가오는 네온 큐브를 양손 세이버로 베어내세요!"
            }
            VRExperience.PHYSICS_SANDBOX -> {
                initPhysicsSandbox()
                _lastActionText.value = "물체를 핀치(🤏)해서 잡고 허공으로 던지세요!"
            }
            VRExperience.SPACE_ODYSSEY -> {
                initSolarSystem()
                _lastActionText.value = "행성을 레이저로 가리켜 정보를 확인하세요!"
            }
            VRExperience.TARGET_SHOOTER -> {
                initTargetShooter()
                _lastActionText.value = "표적을 조준하고 핀치(🤏)로 레이저를 발사하세요!"
            }
            VRExperience.PASSTHROUGH_MR -> {
                _lastActionText.value = "카메라 패스스루 MR(혼합현실) 모드 활성화됨"
            }
            VRExperience.HORIZON_HOME -> {
                _isMenuOpen.value = true
                _lastActionText.value = "Quest 홈 대시보드에서 앱을 선택하세요"
            }
        }
    }

    private fun initPhysicsSandbox() {
        val list = mutableListOf<PhysicsEntity>()
        val colors = listOf(0xFF00E5FF, 0xFFFF0055, 0xFFFFD600, 0xFF00E676, 0xFFE040FB)
        val shapes = listOf(PhysicsShape.CUBE, PhysicsShape.SPHERE, PhysicsShape.PYRAMID)

        var id = 1L
        for (x in -1..1) {
            for (y in 0..1) {
                list.add(
                    PhysicsEntity(
                        id = id++,
                        position = Vector3(x * 0.5f, y * 0.45f - 0.2f, 2.0f + (id % 3) * 0.2f),
                        shape = shapes[(id.toInt()) % shapes.size],
                        color = colors[(id.toInt()) % colors.size],
                        size = 0.28f + (id % 2) * 0.08f
                    )
                )
            }
        }
        _physicsEntities.value = list
    }

    private fun initSolarSystem() {
        _planets.value = listOf(
            PlanetEntity("Sun", "태양", "☀️", distance = 0f, radius = 0.5f, orbitSpeed = 0f, orbitAngle = 0f, color = 0xFFFFAB00, description = "태양계의 중심 항성"),
            PlanetEntity("Mercury", "수성", "🪐", distance = 1.2f, radius = 0.12f, orbitSpeed = 1.2f, orbitAngle = 0.3f, color = 0xFF9E9E9E, description = "태양에서 가장 가까운 행성"),
            PlanetEntity("Venus", "금성", "🟡", distance = 1.8f, radius = 0.18f, orbitSpeed = 0.9f, orbitAngle = 1.2f, color = 0xFFFFD54F, description = "두꺼운 대기의 샛별"),
            PlanetEntity("Earth", "지구", "🌍", distance = 2.5f, radius = 0.22f, orbitSpeed = 0.7f, orbitAngle = 2.4f, color = 0xFF29B6F6, description = "생명이 살아 숨쉬는 푸른 행성"),
            PlanetEntity("Mars", "화성", "🔴", distance = 3.2f, radius = 0.16f, orbitSpeed = 0.5f, orbitAngle = 3.8f, color = 0xFFFF5722, description = "붉은 모래와 화산의 붉은 행성"),
            PlanetEntity("Jupiter", "목성", "🪐", distance = 4.2f, radius = 0.38f, orbitSpeed = 0.35f, orbitAngle = 4.9f, color = 0xFFFFB74D, description = "태양계 최대의 가스 행성"),
            PlanetEntity("Saturn", "토성", "🪐", distance = 5.2f, radius = 0.32f, orbitSpeed = 0.25f, orbitAngle = 0.8f, color = 0xFFFFE082, hasRings = true, description = "아름다운 얼음 고리를 가진 행성")
        )
    }

    private fun initTargetShooter() {
        val targetsList = mutableListOf<TargetEntity>()
        var id = 1L
        for (i in 0 until 5) {
            val angle = (i * 0.8f) - 1.6f
            val dist = 3.2f + (i % 2) * 0.8f
            targetsList.add(
                TargetEntity(
                    id = id++,
                    position = Vector3(sin(angle) * dist, cos(angle * 1.5f) * 0.6f + 0.1f, cos(angle) * dist),
                    color = if (i % 2 == 0) 0xFFFF1744 else 0xFFFF9100,
                    points = (i + 1) * 50
                )
            )
        }
        _targets.value = targetsList
    }

    /**
     * Recalibrate and recenter 3D World-Locked Floating Window to current user gaze direction
     */
    fun recenterSpatialAnchor(headOrientation: HeadOrientation) {
        val forward = VRMath.getForwardVector(headOrientation.pitch * 0.4f, headOrientation.yaw, 0f)
        val newWindowPos = forward * 2.0f + Vector3(0f, 0.15f, 0f)
        val newDockPos = forward * 1.8f + Vector3(0f, -0.6f, 0f)

        val currentS = _questSettings.value
        currentS.anchorPos = newWindowPos
        _questSettings.value = currentS.copy(anchorPos = newWindowPos)

        val currentD = _questDock.value
        currentD.anchorPos = newDockPos
        _questDock.value = currentD.copy(anchorPos = newDockPos)
        _lastActionText.value = "🧭 6축 공간 고정 완료 (현재 시야 정면에 3D 창 배치됨)"
        triggerHaptic(50)
    }

    /**
     * Main Simulation Loop Tick (Delta Time in seconds)
     */
    fun updateWorld(deltaTime: Float, rightHand: TrackedHand, leftHand: TrackedHand, headOrientation: HeadOrientation) {
        // Update particles
        updateParticles(deltaTime)

        // Always update 3D Quest Quick Settings & Universal Dock Spatial Interactions
        // Update 3D Floating Grey Spatial Window (Gaze aiming & dwell selection)
        updateVRBoxWindow(deltaTime, headOrientation)

        // Handle active experience update

        when (_experience.value) {
            VRExperience.HORIZON_HOME -> updateHomeMenu(rightHand, leftHand)
            VRExperience.RHYTHM_SABER -> updateRhythmSaber(deltaTime, rightHand, leftHand)
            VRExperience.PHYSICS_SANDBOX -> updatePhysics(deltaTime, rightHand, leftHand)
            VRExperience.SPACE_ODYSSEY -> updateSpace(deltaTime, rightHand)
            VRExperience.TARGET_SHOOTER -> updateTargetShooter(deltaTime, rightHand, leftHand)
            VRExperience.PASSTHROUGH_MR -> { /* Managed via Quick Settings */ }
        }
    }

    /**
     * Updates Gaze Raycasting & Interactive selection on the 3D World-Anchored Spatial Window
     */
    private fun updateVRBoxWindow(
        dt: Float,
        headOrientation: HeadOrientation
    ) {
        val currentWin = _vrBoxWindow.value
        if (!currentWin.isVisible) return

        val forwardDir = VRMath.getForwardVector(headOrientation.pitch, headOrientation.yaw, headOrientation.roll)
        val gazeRay = Ray3D(origin = Vector3(0f, 0f, 0f), direction = forwardDir)

        val windowPos = currentWin.anchorPos
        val quadNormal = (Vector3(0f, 0f, 0f) - windowPos).normalized()
        val hitT = VRMath.rayIntersectsQuad(
            ray = gazeRay,
            quadCenter = windowPos,
            quadNormal = quadNormal,
            width = currentWin.width,
            height = currentWin.height
        )

        var newHoveredId: String? = null
        if (hitT != null) {
            val hitPoint = gazeRay.getPoint(hitT)
            // Compute quad local coordinates
            val rightVec = Vector3(-windowPos.z, 0f, windowPos.x).normalized()
            val upVec = quadNormal.cross(rightVec).normalized()

            val rel = hitPoint - windowPos
            val localX = rel.dot(rightVec)
            val localY = rel.dot(upVec)

            val halfW = currentWin.width * 0.5f
            val halfH = currentWin.height * 0.5f

            // Buttons located in lower section of the 3D window
            if (localY in (-halfH * 0.95f)..(-halfH * 0.35f)) {
                when {
                    localX in (-halfW * 0.92f)..(-halfW * 0.46f) -> newHoveredId = "btn_recenter"
                    localX in (-halfW * 0.46f)..0.0f -> newHoveredId = "btn_passthrough"
                    localX in 0.0f..(halfW * 0.46f) -> newHoveredId = "btn_ipd"
                    localX in (halfW * 0.46f)..(halfW * 0.92f) -> newHoveredId = "btn_proceed"
                }
            }
        }

        var newDwell = currentWin.gazeDwellProgress
        if (newHoveredId != null) {
            if (newHoveredId == currentWin.hoveredButtonId) {
                newDwell += dt * 0.9f // ~1.1 seconds dwell to click
                if (newDwell >= 1.0f) {
                    // Trigger action on dwell completion
                    executeButtonAction(newHoveredId, headOrientation)
                    newDwell = 0f
                }
            } else {
                newDwell = 0f
                triggerHaptic(20)
            }
        } else {
            newDwell = (newDwell - dt * 2.0f).coerceAtLeast(0f)
        }

        _vrBoxWindow.value = currentWin.copy(
            hoveredButtonId = newHoveredId,
            gazeDwellProgress = newDwell.coerceIn(0f, 1f),
            ipdMm = _ipdMm.value,
            isDisplayStereo = (_displayMode.value == VRDisplayMode.CARDBOARD_VR)
        )
    }

    /**
     * Executes the button click from Gaze Dwell or Direct Screen Tap
     */
    fun executeButtonAction(buttonId: String, headOrientation: HeadOrientation) {
        when (buttonId) {
            "btn_recenter" -> {
                recenterVRWindow(headOrientation)
                _lastActionText.value = "🧭 시점 정렬 완료"
                triggerHaptic(50)
            }
            "btn_passthrough" -> {
                togglePassthrough()
            }
            "btn_ipd" -> {
                val nextIpd = when (_ipdMm.value) {
                    58f -> 64f
                    64f -> 70f
                    else -> 58f
                }
                setIpdMm(nextIpd)
                _lastActionText.value = "👓 VR BOX 동공 거리 (IPD): ${nextIpd.toInt()}mm"
                triggerHaptic(35)
            }
            "btn_proceed" -> {
                _lastActionText.value = "✓ Spatial MR 세션 준비 완료!"
                triggerHaptic(80)
            }
        }
    }

    /**
     * Direct Screen Tap handler: checks if a specific button was tapped by touch coordinates
     */
    fun onScreenTouchPosition(x: Float, y: Float, screenWidth: Float, screenHeight: Float, headOrientation: HeadOrientation) {
        val isStereo = (_displayMode.value == VRDisplayMode.CARDBOARD_VR)
        val eyeWidth = if (isStereo) screenWidth * 0.5f else screenWidth
        val localX = if (isStereo && x >= eyeWidth) x - eyeWidth else x
        val normX = (localX / eyeWidth).coerceIn(0f, 1f)
        val normY = (y / screenHeight).coerceIn(0f, 1f)

        // Check if bottom action buttons area was tapped
        if (normY in 0.62f..0.92f) {
            val buttonId = when {
                normX in 0.04f..0.26f -> "btn_recenter"
                normX in 0.27f..0.49f -> "btn_passthrough"
                normX in 0.50f..0.72f -> "btn_ipd"
                normX in 0.73f..0.96f -> "btn_proceed"
                else -> null
            }
            if (buttonId != null) {
                executeButtonAction(buttonId, headOrientation)
                return
            }
        }

        // Tap outside buttons toggles passthrough or recenters
        onScreenTap(headOrientation)
    }

    /**
     * Screen Tap fallback handler
     */
    fun onScreenTap(headOrientation: HeadOrientation) {
        val hovered = _vrBoxWindow.value.hoveredButtonId
        if (hovered != null) {
            executeButtonAction(hovered, headOrientation)
        } else {
            recenterVRWindow(headOrientation)
            _lastActionText.value = "🧭 시점 정렬됨 (화면 터치)"
            triggerHaptic(30)
        }
    }

    /**
     * Smoothly recenters the 3D window in front of current gaze direction
     */
    fun recenterVRWindow(headOrientation: HeadOrientation) {
        val forwardDir = VRMath.getForwardVector(headOrientation.pitch * 0.5f, headOrientation.yaw, 0f)
        val newAnchor = forwardDir * 2.0f
        _vrBoxWindow.value = _vrBoxWindow.value.copy(anchorPos = newAnchor)
        spawnBurstParticles(newAnchor, 0xFF60A5FA, 20)
    }

    fun togglePassthrough() {
        val next = !_vrBoxWindow.value.isPassthroughActive
        _vrBoxWindow.value = _vrBoxWindow.value.copy(isPassthroughActive = next)
        _questSettings.value = _questSettings.value.copy(isPassthroughEnabled = next)
        _lastActionText.value = if (next) "📷 실시간 MR 비디오 모드 ON" else "🌌 VR 공간 모드 ON"
        triggerHaptic(40)
    }

    private fun updateQuickSettingsAndDock(
        dt: Float,
        rightHand: TrackedHand,
        leftHand: TrackedHand,
        headOrientation: HeadOrientation
    ) {
        val ray = rightHand.laserRay ?: leftHand.laserRay
        val isClicking = (rightHand.isPinching || leftHand.isPinching)
        val clickTriggered = isClicking && !lastPinchProcessed
        lastPinchProcessed = isClicking

        // Smoothly follow user gaze/head rotation so the 3D Quick Menu stays in view naturally (VR Billboard Follow)
        val targetForward = VRMath.getForwardVector(headOrientation.pitch * 0.5f, headOrientation.yaw, 0f)
        val targetWindowPos = targetForward * 2.0f + Vector3(0f, 0.15f, 0f)
        val targetDockPos = targetForward * 1.8f + Vector3(0f, -0.6f, 0f)

        // Smooth lerp (soft follow dampening)
        val followSpeed = (3.5f * dt).coerceIn(0f, 1f)
        val currentWin = _questSettings.value.anchorPos
        val smoothWinPos = Vector3(
            x = currentWin.x + followSpeed * (targetWindowPos.x - currentWin.x),
            y = currentWin.y + followSpeed * (targetWindowPos.y - currentWin.y),
            z = currentWin.z + followSpeed * (targetWindowPos.z - currentWin.z)
        )

        val currentDk = _questDock.value.anchorPos
        val smoothDockPos = Vector3(
            x = currentDk.x + followSpeed * (targetDockPos.x - currentDk.x),
            y = currentDk.y + followSpeed * (targetDockPos.y - currentDk.y),
            z = currentDk.z + followSpeed * (targetDockPos.z - currentDk.z)
        )

        // Update digital clock time string & real device battery data from Android system
        val timeNow = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        var currentSettings = _questSettings.value.copy(
            anchorPos = smoothWinPos,
            timeString = timeNow,
            batteryPercent = realBatteryPercent,
            isCharging = isBatteryCharging
        )
        var currentDock = _questDock.value.copy(
            anchorPos = smoothDockPos
        )

        if (ray != null) {
            // 1. Raycast against Quest Quick Settings Window
            val windowPos = currentSettings.anchorPos
            val hitT = VRMath.rayIntersectsQuad(
                ray = ray,
                quadCenter = windowPos,
                quadNormal = (Vector3(0f, 0f, 0f) - windowPos).normalized(),
                width = currentSettings.width,
                height = currentSettings.height
            )

            if (hitT != null) {
                val hitPoint = ray.getPoint(hitT)
                val localX = hitPoint.x - windowPos.x
                val localY = hitPoint.y - windowPos.y

                var hoveredId: String? = null

                // Top 3 Big Cards (Y: 0.12 to 0.45)
                if (localY in 0.12f..0.48f) {
                    if (localX in -0.75f..-0.25f) {
                        hoveredId = "tile_wifi"
                        if (clickTriggered) {
                            currentSettings = currentSettings.copy(isWifiEnabled = !currentSettings.isWifiEnabled)
                            _lastActionText.value = if (currentSettings.isWifiEnabled) "📶 Wi-Fi 연결됨 (5GHz)" else "📶 Wi-Fi 꺼짐"
                            triggerHaptic(40)
                            spawnBurstParticles(hitPoint, 0xFF00E5FF, 15)
                        }
                    } else if (localX in -0.25f..0.25f) {
                        hoveredId = "tile_guardian"
                        if (clickTriggered) {
                            currentSettings = currentSettings.copy(isGuardianEnabled = !currentSettings.isGuardianEnabled)
                            _lastActionText.value = if (currentSettings.isGuardianEnabled) "🛡️ 안전 경계(Guardian) 고정 모드" else "🛡️ 안전 경계 해제"
                            triggerHaptic(40)
                            spawnBurstParticles(hitPoint, 0xFF00E676, 15)
                        }
                    } else if (localX in 0.25f..0.75f) {
                        hoveredId = "tile_link"
                        if (clickTriggered) {
                            currentSettings = currentSettings.copy(isQuestLinkActive = !currentSettings.isQuestLinkActive)
                            _lastActionText.value = if (currentSettings.isQuestLinkActive) "🔗 Quest Air Link PC 연결 활성화" else "🔗 Quest Link 단독 모드"
                            triggerHaptic(40)
                            spawnBurstParticles(hitPoint, 0xFF7C4DFF, 15)
                        }
                    }
                }
                // Middle Row Quick Actions (Y: -0.18 to 0.10)
                else if (localY in -0.18f..0.10f) {
                    if (localX in -0.75f..-0.52f) {
                        hoveredId = "btn_mic"
                        if (clickTriggered) {
                            currentSettings = currentSettings.copy(isMicMuted = !currentSettings.isMicMuted)
                            _lastActionText.value = if (currentSettings.isMicMuted) "🎤 마이크 음소거됨" else "🎤 마이크 켜짐"
                            triggerHaptic(30)
                        }
                    } else if (localX in -0.52f..-0.30f) {
                        hoveredId = "btn_passthrough"
                        if (clickTriggered) {
                            val newPassthrough = !currentSettings.isPassthroughEnabled
                            currentSettings = currentSettings.copy(isPassthroughEnabled = newPassthrough)
                            if (newPassthrough) {
                                switchExperience(VRExperience.PASSTHROUGH_MR)
                            } else {
                                switchExperience(VRExperience.HORIZON_HOME)
                            }
                            _lastActionText.value = if (newPassthrough) "👁️ 패스스루 카메라 모드 활성화" else "🌌 VR 가상 환경 활성화"
                            triggerHaptic(40)
                        }
                    } else if (localX in -0.30f..-0.02f) {
                        hoveredId = "btn_volume"
                        if (clickTriggered) {
                            val newVol = if (currentSettings.volumeLevel >= 1.0f) 0.2f else currentSettings.volumeLevel + 0.2f
                            currentSettings = currentSettings.copy(volumeLevel = newVol)
                            _lastActionText.value = "🔊 볼륨: ${(newVol * 100).toInt()}%"
                            triggerHaptic(30)
                        }
                    } else if (localX in -0.02f..0.26f) {
                        hoveredId = "btn_brightness"
                        if (clickTriggered) {
                            val newBri = if (currentSettings.brightnessLevel >= 1.0f) 0.3f else currentSettings.brightnessLevel + 0.2f
                            currentSettings = currentSettings.copy(brightnessLevel = newBri)
                            _lastActionText.value = "☀️ 화면 밝기: ${(newBri * 100).toInt()}%"
                            triggerHaptic(30)
                        }
                    } else if (localX in 0.26f..0.48f) {
                        hoveredId = "btn_night"
                        if (clickTriggered) {
                            currentSettings = currentSettings.copy(isNightMode = !currentSettings.isNightMode)
                            _lastActionText.value = if (currentSettings.isNightMode) "🌙 야간 디스플레이 켜짐" else "🌙 야간 디스플레이 꺼짐"
                            triggerHaptic(30)
                        }
                    } else if (localX in 0.48f..0.75f) {
                        hoveredId = "btn_record"
                        if (clickTriggered) {
                            currentSettings = currentSettings.copy(isRecording = !currentSettings.isRecording)
                            _lastActionText.value = if (currentSettings.isRecording) "🔴 VR 화면 녹화 중..." else "📹 녹화 저장 완료"
                            triggerHaptic(60)
                            spawnBurstParticles(hitPoint, 0xFF00E5FF, 20)
                        }
                    }
                }
                // Bottom Bar Actions (Y: -0.48 to -0.22)
                else if (localY in -0.48f..-0.22f) {
                    if (localX in -0.25f..0.25f) {
                        hoveredId = "btn_recenter"
                        if (clickTriggered) {
                            recenterSpatialAnchor(headOrientation)
                            spawnBurstParticles(hitPoint, 0xFF00E5FF, 25)
                        }
                    }
                }

                currentSettings = currentSettings.copy(hoveredElementId = hoveredId)
            } else {
                currentSettings = currentSettings.copy(hoveredElementId = null)
            }

            // 2. Raycast against Universal Dock Bar
            val dockPos = currentDock.anchorPos
            val dockHitT = VRMath.rayIntersectsQuad(
                ray = ray,
                quadCenter = dockPos,
                quadNormal = (Vector3(0f, 0f, 0f) - dockPos).normalized(),
                width = currentDock.width,
                height = currentDock.height
            )

            if (dockHitT != null) {
                val hitPoint = ray.getPoint(dockHitT)
                val localX = hitPoint.x - dockPos.x
                // Map localX to app index (-0.65 to 0.65)
                val totalApps = currentDock.apps.size
                val appSlotWidth = currentDock.width * 0.8f / totalApps
                val startX = -((totalApps - 1) * appSlotWidth * 0.5f)
                val appIdx = ((localX - startX + appSlotWidth * 0.5f) / appSlotWidth).toInt().coerceIn(0, totalApps - 1)
                val selectedApp = currentDock.apps.getOrNull(appIdx)

                if (selectedApp != null) {
                    currentDock = currentDock.copy(hoveredAppId = selectedApp.id)
                    if (clickTriggered) {
                        if (selectedApp.experience != null) {
                            switchExperience(selectedApp.experience)
                        } else {
                            _lastActionText.value = "🚀 ${selectedApp.name} 실행됨"
                        }
                        triggerHaptic(50)
                        spawnBurstParticles(hitPoint, selectedApp.color, 25)
                    }
                }
            } else {
                currentDock = currentDock.copy(hoveredAppId = null)
            }
        } else {
            currentSettings = currentSettings.copy(hoveredElementId = null)
            currentDock = currentDock.copy(hoveredAppId = null)
        }

        _questSettings.value = currentSettings
        _questDock.value = currentDock
    }

    private fun updateHomeMenu(rightHand: TrackedHand, leftHand: TrackedHand) {
        val ray = rightHand.laserRay ?: leftHand.laserRay ?: return
        val isClicking = rightHand.isPinching || leftHand.isPinching

        val updatedCards = _menuCards.value.map { card ->
            val dist = VRMath.rayIntersectsQuad(
                ray = ray,
                quadCenter = card.position,
                quadNormal = Vector3(0f, 0f, -1f),
                width = card.width,
                height = card.height
            )

            val isHovered = dist != null
            if (isHovered && isClicking) {
                switchExperience(card.experience)
                spawnBurstParticles(card.position, card.primaryColor, 20)
                card.copy(isHovered = true, isSelected = true)
            } else {
                card.copy(isHovered = isHovered, isSelected = false)
            }
        }
        _menuCards.value = updatedCards
    }

    private fun updateRhythmSaber(dt: Float, rightHand: TrackedHand, leftHand: TrackedHand) {
        blockSpawnTimer += dt
        val currentBlocks = _saberBlocks.value.toMutableList()

        // Spawn new blocks every 1.2 seconds
        if (blockSpawnTimer > 1.1f) {
            blockSpawnTimer = 0f
            val isBlue = Random.nextBoolean()
            val laneX = if (isBlue) 0.35f else -0.35f
            val laneY = (Random.nextInt(3) - 1) * 0.3f
            currentBlocks.add(
                SaberBlock(
                    id = System.currentTimeMillis(),
                    position = Vector3(laneX, laneY, 10f),
                    color = if (isBlue) 0xFF00E5FF else 0xFFFF0055,
                    cutDirection = Random.nextInt(4),
                    speed = 4.5f
                )
            )
        }

        // Move blocks towards player (Z decreasing)
        val iterator = currentBlocks.iterator()
        while (iterator.hasNext()) {
            val block = iterator.next()
            if (block.isCut) {
                block.cutTimer += dt
                if (block.cutTimer > 0.4f) {
                    iterator.remove()
                }
            } else {
                block.position = block.position.copy(z = block.position.z - block.speed * dt)

                // Check collision with right hand (Cyan saber) and left hand (Pink saber)
                val distRight = rightHand.position.distanceTo(block.position)
                val distLeft = leftHand.position.distanceTo(block.position)

                val hitByRight = distRight < 0.45f
                val hitByLeft = distLeft < 0.45f

                if (hitByRight || hitByLeft) {
                    val cutColor = if (hitByRight) 0xFF00E5FF else 0xFFFF0055
                    spawnBurstParticles(block.position, cutColor, 25)
                    triggerHaptic(60)
                    _score.value += 150 + _combo.value * 10
                    _combo.value += 1
                    _lastActionText.value = "PERFECT CUT! Combo ${_combo.value}x"

                    val cutBlock = block.copy(isCut = true)
                    val idx = currentBlocks.indexOf(block)
                    if (idx != -1) currentBlocks[idx] = cutBlock
                } else if (block.position.z < -0.5f) {
                    // Missed block
                    _combo.value = 0
                    iterator.remove()
                }
            }
        }
        _saberBlocks.value = currentBlocks
    }

    private fun updatePhysics(dt: Float, rightHand: TrackedHand, leftHand: TrackedHand) {
        val list = _physicsEntities.value.toMutableList()
        val hand = if (rightHand.isTracked) rightHand else leftHand

        for (i in list.indices) {
            val entity = list[i]

            // Grab logic
            if (hand.isGrabbing && (grabbedPhysicsId == null || grabbedPhysicsId == entity.id)) {
                val distToHand = hand.position.distanceTo(entity.position)
                if (distToHand < 0.6f && !entity.isGrabbed) {
                    grabbedPhysicsId = entity.id
                    entity.isGrabbed = true
                    triggerHaptic(40)
                    _lastActionText.value = "물체를 잡았습니다! 손을 움직여 던지세요"
                }
            }

            if (entity.isGrabbed) {
                if (hand.isGrabbing) {
                    // Follow hand
                    val targetPos = hand.position + Vector3(0f, 0f, 0.4f)
                    val vel = (targetPos - entity.position) * (1f / max(dt, 0.016f))
                    entity.velocity = vel * 0.8f
                    entity.position = targetPos
                    entity.rotation = entity.rotation + Vector3(dt * 2f, dt * 3f, dt * 1f)
                } else {
                    // Release / Throw
                    entity.isGrabbed = false
                    grabbedPhysicsId = null
                    triggerHaptic(20)
                    _lastActionText.value = "물체를 던졌습니다!"
                }
            } else {
                // Apply Gravity & Velocity
                val gravity = Vector3(0f, -1.8f * dt, 0f)
                entity.velocity = (entity.velocity + gravity) * 0.98f // Air damping
                entity.position = entity.position + entity.velocity * dt

                // Floor bounce (Y = -0.8f)
                if (entity.position.y < -0.8f) {
                    entity.position = entity.position.copy(y = -0.8f)
                    entity.velocity = Vector3(entity.velocity.x * 0.7f, -entity.velocity.y * 0.6f, entity.velocity.z * 0.7f)
                }

                // Front & Side boundaries
                if (entity.position.z > 6f || entity.position.z < 0.6f) {
                    entity.velocity = Vector3(entity.velocity.x, entity.velocity.y, -entity.velocity.z * 0.7f)
                }
                if (abs(entity.position.x) > 3f) {
                    entity.velocity = Vector3(-entity.velocity.x * 0.7f, entity.velocity.y, entity.velocity.z)
                }
            }
        }
        _physicsEntities.value = list
    }

    private fun updateSpace(dt: Float, rightHand: TrackedHand) {
        val list = _planets.value.map { planet ->
            val newAngle = planet.orbitAngle + planet.orbitSpeed * dt * 0.5f
            planet.copy(orbitAngle = newAngle)
        }
        _planets.value = list

        // Raycast highlight
        val ray = rightHand.laserRay ?: return
        for (planet in list) {
            if (planet.distance > 0) {
                val pos = Vector3(
                    sin(planet.orbitAngle) * planet.distance,
                    cos(planet.orbitAngle * 0.5f) * 0.3f,
                    cos(planet.orbitAngle) * planet.distance + 3.0f
                )
                val hit = VRMath.rayIntersectsSphere(ray, pos, planet.radius * 1.5f)
                if (hit != null) {
                    _lastActionText.value = "${planet.emoji} ${planet.nameKo} (${planet.name}) - ${planet.description}"
                    if (rightHand.isPinching) {
                        spawnBurstParticles(pos, planet.color, 15)
                        triggerHaptic(25)
                    }
                    break
                }
            }
        }
    }

    private fun updateTargetShooter(dt: Float, rightHand: TrackedHand, leftHand: TrackedHand) {
        val targetsList = _targets.value.toMutableList()
        val hand = if (rightHand.isTracked) rightHand else leftHand
        val ray = hand.laserRay ?: return

        for (i in targetsList.indices) {
            val target = targetsList[i]
            if (target.isHit) {
                target.hitTimer += dt
                if (target.hitTimer > 0.5f) {
                    // Respawn target at new random angle
                    val angle = Random.nextFloat() * 4f - 2f
                    val dist = 3.5f + Random.nextFloat() * 2f
                    targetsList[i] = TargetEntity(
                        id = System.currentTimeMillis(),
                        position = Vector3(sin(angle) * dist, Random.nextFloat() * 1.2f - 0.4f, cos(angle) * dist),
                        points = 100
                    )
                }
            } else {
                // Bobbing animation
                val newY = target.position.y + sin(System.currentTimeMillis() * 0.003f + target.hoverOffset) * 0.002f
                target.position = target.position.copy(y = newY)

                val hitDist = VRMath.rayIntersectsSphere(ray, target.position, target.radius)
                if (hitDist != null && hand.isPinching) {
                    target.isHit = true
                    spawnBurstParticles(target.position, target.color, 35)
                    triggerHaptic(80)
                    _score.value += target.points
                    _combo.value += 1
                    _lastActionText.value = "HIT! +${target.points}점 (콤보 ${_combo.value}x)"
                }
            }
        }
        _targets.value = targetsList
    }

    private fun updatePassthrough(rightHand: TrackedHand, leftHand: TrackedHand) {
        // In Passthrough MR mode, floating holographic interactive companion spheres
    }

    private fun updateParticles(dt: Float) {
        val currentParticles = _particles.value.toMutableList()
        val iterator = currentParticles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.life -= dt
            if (p.life <= 0) {
                iterator.remove()
            } else {
                p.position = p.position + p.velocity * dt
                p.alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
            }
        }
        _particles.value = currentParticles
    }

    private fun spawnBurstParticles(origin: Vector3, color: Long, count: Int) {
        val newParticles = mutableListOf<VRParticle>()
        for (i in 0 until count) {
            val vel = Vector3(
                (Random.nextFloat() - 0.5f) * 3f,
                (Random.nextFloat() - 0.5f) * 3f,
                (Random.nextFloat() - 0.5f) * 3f
            )
            newParticles.add(
                VRParticle(
                    position = origin,
                    velocity = vel,
                    color = color,
                    alpha = 1.0f,
                    size = Random.nextFloat() * 6f + 3f,
                    life = 0.6f + Random.nextFloat() * 0.4f,
                    maxLife = 1.0f
                )
            )
        }
        _particles.value = _particles.value + newParticles
    }

    private fun triggerHaptic(durationMs: Long = 30) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(durationMs)
            }
        } catch (e: Exception) {
            // Ignore haptic failures
        }
    }

    fun cleanup() {
        try {
            context.unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
    }
}
