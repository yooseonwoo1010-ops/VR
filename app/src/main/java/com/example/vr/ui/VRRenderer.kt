package com.example.vr.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.vr.engine.VRMath
import com.example.vr.model.*
import com.example.vr.tracking.HeadOrientation
import kotlin.math.*

object VRRenderer {

    /**
     * Draws the complete 3D VR Scene for a single camera / eye perspective.
     */
    fun drawEyeView(
        drawScope: DrawScope,
        cameraPos: Vector3,
        headOrientation: HeadOrientation,
        fov: Float,
        experience: VRExperience,
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
        isStereo: Boolean = true
    ) {
        val width = drawScope.size.width
        val height = drawScope.size.height
        val pitch = headOrientation.pitch
        val yaw = headOrientation.yaw
        val roll = headOrientation.roll

        // 1. Draw Starfield / Sky Space Background (always provide visual depth, and subtle grid in MR mode)
        if (!questSettings.isPassthroughEnabled && experience != VRExperience.PASSTHROUGH_MR) {
            drawSkyAndGrid(drawScope, cameraPos, pitch, yaw, roll, width, height, fov)
        } else {
            drawPassthroughGridOverlay(drawScope, cameraPos, pitch, yaw, roll, width, height, fov)
        }

        // 2. Draw 3D Solar System (Space Odyssey)
        if (experience == VRExperience.SPACE_ODYSSEY) {
            drawSolarSystem(drawScope, cameraPos, pitch, yaw, roll, width, height, fov, planets)
        }

        // 3. Draw Target Shooter Gallery
        if (experience == VRExperience.TARGET_SHOOTER) {
            drawTargetGallery(drawScope, cameraPos, pitch, yaw, roll, width, height, fov, targets)
        }

        // 4. Draw Rhythm Saber Blocks
        if (experience == VRExperience.RHYTHM_SABER) {
            drawRhythmSaber(drawScope, cameraPos, pitch, yaw, roll, width, height, fov, saberBlocks)
        }

        // 5. Draw Physics Sandbox Entities
        if (experience == VRExperience.PHYSICS_SANDBOX) {
            drawPhysicsEntities(drawScope, cameraPos, pitch, yaw, roll, width, height, fov, physicsEntities)
        }

        // 6. Draw 3D World-Locked Meta Quest Quick Settings Window & Universal Dock (from image)
        if (questSettings.isVisible) {
            drawQuestQuickSettingsWindow(drawScope, cameraPos, pitch, yaw, roll, width, height, fov, questSettings)
            drawQuestUniversalDock(drawScope, cameraPos, pitch, yaw, roll, width, height, fov, questDock, questSettings)
        }

        // 7. Draw 3D Burst Particles
        drawParticles(drawScope, cameraPos, pitch, yaw, roll, width, height, fov, particles)

        // 8. Draw 3D Quest Touch Controller & Laser Rays
        drawQuestTouchController(drawScope, cameraPos, pitch, yaw, roll, width, height, fov, rightHand, isRight = true, questSettings)
        drawQuestTouchController(drawScope, cameraPos, pitch, yaw, roll, width, height, fov, leftHand, isRight = false, questSettings)

        // 9. Draw Center Reticle / Crosshair for Gaze
        val centerProj = VRMath.project3DTo2D(
            pointWorld = cameraPos + VRMath.getForwardVector(pitch, yaw, roll) * 2.5f,
            cameraPos = cameraPos,
            pitch = pitch,
            yaw = yaw,
            roll = roll,
            screenWidth = width,
            screenHeight = height,
            fov = fov
        )
        if (centerProj.isVisible) {
            drawScope.drawCircle(
                color = Color.White.copy(alpha = 0.6f),
                radius = if (isStereo) 3f else 4.5f,
                center = Offset(centerProj.screenX, centerProj.screenY)
            )
            drawScope.drawCircle(
                color = Color(0xFF00E5FF).copy(alpha = 0.4f),
                radius = if (isStereo) 6f else 9f,
                center = Offset(centerProj.screenX, centerProj.screenY),
                style = Stroke(width = 1.2f)
            )
        }
    }

    /**
     * Renders the authentic Meta Quest 2 Quick Settings 3D Window (Anchored in 6-Axis Gyro World Space)
     */
    private fun drawQuestQuickSettingsWindow(
        drawScope: DrawScope,
        cameraPos: Vector3,
        pitch: Float,
        yaw: Float,
        roll: Float,
        width: Float,
        height: Float,
        fov: Float,
        settings: QuestQuickSettingsState
    ) {
        val anchor = settings.anchorPos
        val halfW = settings.width * 0.5f
        val halfH = settings.height * 0.5f

        val centerProj = VRMath.project3DTo2D(anchor, cameraPos, pitch, yaw, roll, width, height, fov)
        if (!centerProj.isVisible || centerProj.depth <= 0.2f) return

        // 4 Corners of 3D Panel in World Space
        val pTL = VRMath.project3DTo2D(anchor + Vector3(-halfW, halfH, 0f), cameraPos, pitch, yaw, roll, width, height, fov)
        val pTR = VRMath.project3DTo2D(anchor + Vector3(halfW, halfH, 0f), cameraPos, pitch, yaw, roll, width, height, fov)
        val pBR = VRMath.project3DTo2D(anchor + Vector3(halfW, -halfH, 0f), cameraPos, pitch, yaw, roll, width, height, fov)
        val pBL = VRMath.project3DTo2D(anchor + Vector3(-halfW, -halfH, 0f), cameraPos, pitch, yaw, roll, width, height, fov)

        val panelPath = Path().apply {
            moveTo(pTL.screenX, pTL.screenY)
            lineTo(pTR.screenX, pTR.screenY)
            lineTo(pBR.screenX, pBR.screenY)
            lineTo(pBL.screenX, pBL.screenY)
            close()
        }

        // 1. Dark Frosted Glass Window Panel Background (Quest Dark UI Theme)
        drawScope.drawPath(
            path = panelPath,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xF5111827),
                    Color(0xF80B1120),
                    Color(0xFC030712)
                )
            )
        )

        // Glass Highlight & Cyan Ambient Rim Light
        drawScope.drawPath(
            path = panelPath,
            color = Color(0x6600E5FF),
            style = Stroke(width = 2.0f)
        )

        val scale = (1.0f / centerProj.depth) * width * 0.28f
        val nativeCanvas = drawScope.drawContext.canvas.nativeCanvas

        // 2. Window Header Bar (Status Info: Time, Battery, Title, Avatar)
        val pHeader = VRMath.project3DTo2D(anchor + Vector3(0f, halfH * 0.82f, 0f), cameraPos, pitch, yaw, roll, width, height, fov)
        if (pHeader.isVisible) {
            // Header divider line
            val pDivL = VRMath.project3DTo2D(anchor + Vector3(-halfW * 0.92f, halfH * 0.68f, 0f), cameraPos, pitch, yaw, roll, width, height, fov)
            val pDivR = VRMath.project3DTo2D(anchor + Vector3(halfW * 0.92f, halfH * 0.68f, 0f), cameraPos, pitch, yaw, roll, width, height, fov)
            if (pDivL.isVisible && pDivR.isVisible) {
                drawScope.drawLine(
                    color = Color(0x33475569),
                    start = Offset(pDivL.screenX, pDivL.screenY),
                    end = Offset(pDivR.screenX, pDivR.screenY),
                    strokeWidth = 1.5f
                )
            }

            // Title & Battery Paint
            val headerPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = (scale * 0.12f).coerceIn(12f, 26f)
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.LEFT
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val pTitle = VRMath.project3DTo2D(anchor + Vector3(-halfW * 0.88f, halfH * 0.82f, 0f), cameraPos, pitch, yaw, roll, width, height, fov)
            if (pTitle.isVisible) {
                nativeCanvas.drawText("⚙️ Quick Settings (빠른 설정)", pTitle.screenX, pTitle.screenY, headerPaint)
            }

            val batteryIcon = if (settings.isCharging) "⚡🔋" else "🔋"
            val statusPaint = android.graphics.Paint().apply {
                color = if (settings.batteryPercent <= 20) {
                    android.graphics.Color.parseColor("#FF5252")
                } else if (settings.isCharging) {
                    android.graphics.Color.parseColor("#00E676")
                } else {
                    android.graphics.Color.parseColor("#38BDF8")
                }
                textSize = (scale * 0.11f).coerceIn(11f, 24f)
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.RIGHT
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            val pStatus = VRMath.project3DTo2D(anchor + Vector3(halfW * 0.88f, halfH * 0.82f, 0f), cameraPos, pitch, yaw, roll, width, height, fov)
            if (pStatus.isVisible) {
                nativeCanvas.drawText("$batteryIcon ${settings.batteryPercent}%  •  🕒 ${settings.timeString}", pStatus.screenX, pStatus.screenY, statusPaint)
            }
        }

        // 3. Top 3 Big Feature Cards: [Wi-Fi], [Guardian], [Quest Link] (Exact match with user image)
        val tileConfigs = listOf(
            Triple(-0.52f, "tile_wifi", Triple(if (settings.isWifiEnabled) 0xFF0284C7 else 0xFF334155, "📶 Wi-Fi", settings.wifiName)),
            Triple(0.00f, "tile_guardian", Triple(if (settings.isGuardianEnabled) 0xFF059669 else 0xFF334155, "🛡️ Guardian", "고정 경계 (Stationary)")),
            Triple(0.52f, "tile_link", Triple(if (settings.isQuestLinkActive) 0xFF7C3AED else 0xFF334155, "🔗 Quest Link", "Air Link / Cable"))
        )

        for ((relX, id, data) in tileConfigs) {
            val (colorLong, title, subtitle) = data
            val tileCenter = anchor + Vector3(relX, halfH * 0.32f, 0f)
            val tHalfW = 0.23f
            val tHalfH = 0.17f

            val tTL = VRMath.project3DTo2D(tileCenter + Vector3(-tHalfW, tHalfH, 0f), cameraPos, pitch, yaw, roll, width, height, fov)
            val tTR = VRMath.project3DTo2D(tileCenter + Vector3(tHalfW, tHalfH, 0f), cameraPos, pitch, yaw, roll, width, height, fov)
            val tBR = VRMath.project3DTo2D(tileCenter + Vector3(tHalfW, -tHalfH, 0f), cameraPos, pitch, yaw, roll, width, height, fov)
            val tBL = VRMath.project3DTo2D(tileCenter + Vector3(-tHalfW, -tHalfH, 0f), cameraPos, pitch, yaw, roll, width, height, fov)

            val tilePath = Path().apply {
                moveTo(tTL.screenX, tTL.screenY)
                lineTo(tTR.screenX, tTR.screenY)
                lineTo(tBR.screenX, tBR.screenY)
                lineTo(tBL.screenX, tBL.screenY)
                close()
            }

            val isHovered = settings.hoveredElementId == id
            val tileColor = Color(colorLong)

            drawScope.drawPath(
                path = tilePath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        tileColor.copy(alpha = if (isHovered) 0.95f else 0.75f),
                        Color(0xFF0F172A).copy(alpha = 0.9f)
                    )
                )
            )

            drawScope.drawPath(
                path = tilePath,
                color = if (isHovered) Color.White else tileColor.copy(alpha = 0.8f),
                style = Stroke(width = if (isHovered) 2.5f else 1.2f)
            )

            // Text on tile
            val tC = VRMath.project3DTo2D(tileCenter, cameraPos, pitch, yaw, roll, width, height, fov)
            if (tC.isVisible) {
                val tileTitlePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = (scale * 0.11f).coerceIn(11f, 22f)
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                val tileSubPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#CBD5E1")
                    textSize = (scale * 0.085f).coerceIn(9f, 16f)
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                nativeCanvas.drawText(title, tC.screenX, tC.screenY - scale * 0.03f, tileTitlePaint)
                nativeCanvas.drawText(subtitle, tC.screenX, tC.screenY + scale * 0.06f, tileSubPaint)
            }
        }

        // 4. Middle Row: Quick Action Round/Pill Buttons (Mic, Passthrough, Volume, Brightness, Night, Record)
        val actionButtons = listOf(
            Triple(-0.62f, "btn_mic", if (settings.isMicMuted) "🔇 마이크" else "🎙️ 마이크"),
            Triple(-0.38f, "btn_passthrough", if (settings.isPassthroughEnabled) "👓 MR ON" else "👓 MR OFF"),
            Triple(-0.14f, "btn_volume", "🔊 ${((settings.volumeLevel * 100).toInt())}%"),
            Triple(0.12f, "btn_brightness", "☀️ ${((settings.brightnessLevel * 100).toInt())}%"),
            Triple(0.38f, "btn_night", if (settings.isNightMode) "🌙 야간 ON" else "🌙 야간 OFF"),
            Triple(0.64f, "btn_record", if (settings.isRecording) "🔴 REC" else "⏺️ 화면 전송")
        )

        for ((relX, id, label) in actionButtons) {
            val btnPos = anchor + Vector3(relX, -halfH * 0.16f, 0f)
            val btnProj = VRMath.project3DTo2D(btnPos, cameraPos, pitch, yaw, roll, width, height, fov)
            if (btnProj.isVisible) {
                val isHovered = settings.hoveredElementId == id
                val isPill = (id == "btn_record")
                val btnRadius = scale * 0.22f

                val btnBgColor = when (id) {
                    "btn_record" -> if (settings.isRecording) Color(0xFFFF0055) else Color(0xFF0284C7)
                    "btn_passthrough" -> if (settings.isPassthroughEnabled) Color(0xFF00E5FF) else Color(0xFF1E293B)
                    "btn_mic" -> if (settings.isMicMuted) Color(0xFFFF5252) else Color(0xFF1E293B)
                    "btn_night" -> if (settings.isNightMode) Color(0xFFFFB300) else Color(0xFF1E293B)
                    else -> Color(0xFF1E293B)
                }

                if (isPill) {
                    val pillWidth = btnRadius * 2.8f
                    val pillHeight = btnRadius * 1.3f
                    drawScope.drawRoundRect(
                        color = btnBgColor,
                        topLeft = Offset(btnProj.screenX - pillWidth * 0.5f, btnProj.screenY - pillHeight * 0.5f),
                        size = Size(pillWidth, pillHeight),
                        cornerRadius = CornerRadius(pillHeight * 0.5f, pillHeight * 0.5f)
                    )
                    drawScope.drawRoundRect(
                        color = if (isHovered) Color.White else Color(0x6600E5FF),
                        topLeft = Offset(btnProj.screenX - pillWidth * 0.5f, btnProj.screenY - pillHeight * 0.5f),
                        size = Size(pillWidth, pillHeight),
                        cornerRadius = CornerRadius(pillHeight * 0.5f, pillHeight * 0.5f),
                        style = Stroke(width = if (isHovered) 2.5f else 1.2f)
                    )
                } else {
                    drawScope.drawCircle(
                        color = btnBgColor,
                        radius = btnRadius,
                        center = Offset(btnProj.screenX, btnProj.screenY)
                    )
                    drawScope.drawCircle(
                        color = if (isHovered) Color.White else Color(0x5500E5FF),
                        radius = btnRadius,
                        center = Offset(btnProj.screenX, btnProj.screenY),
                        style = Stroke(width = if (isHovered) 2.5f else 1.2f)
                    )
                }

                // Label below button
                val btnPaint = android.graphics.Paint().apply {
                    color = if (isHovered) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#E2E8F0")
                    textSize = (scale * 0.085f).coerceIn(9f, 15f)
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = if (isHovered) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                }
                nativeCanvas.drawText(label, btnProj.screenX, btnProj.screenY + (if (isPill) 4f else btnRadius + scale * 0.08f), btnPaint)
            }
        }

        // 5. Bottom Sub-Bar: [🧭 시점 정렬 (Recenter Spatial Anchor)]
        val pRecenter = VRMath.project3DTo2D(anchor + Vector3(0f, -halfH * 0.68f, 0f), cameraPos, pitch, yaw, roll, width, height, fov)
        if (pRecenter.isVisible) {
            val isHovered = settings.hoveredElementId == "btn_recenter"
            val rWidth = scale * 3.2f
            val rHeight = scale * 0.65f

            drawScope.drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF0F172A))
                ),
                topLeft = Offset(pRecenter.screenX - rWidth * 0.5f, pRecenter.screenY - rHeight * 0.5f),
                size = Size(rWidth, rHeight),
                cornerRadius = CornerRadius(rHeight * 0.5f, rHeight * 0.5f)
            )

            drawScope.drawRoundRect(
                color = if (isHovered) Color.White else Color(0xFF00E5FF),
                topLeft = Offset(pRecenter.screenX - rWidth * 0.5f, pRecenter.screenY - rHeight * 0.5f),
                size = Size(rWidth, rHeight),
                cornerRadius = CornerRadius(rHeight * 0.5f, rHeight * 0.5f),
                style = Stroke(width = if (isHovered) 2.5f else 1.2f)
            )

            val recenterPaint = android.graphics.Paint().apply {
                color = if (isHovered) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#00E5FF")
                textSize = (scale * 0.095f).coerceIn(10f, 18f)
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            nativeCanvas.drawText("🧭 시점 정렬 (Recenter Spatial Anchor)", pRecenter.screenX, pRecenter.screenY + scale * 0.035f, recenterPaint)
        }
    }

    /**
     * Renders the Meta Quest Universal Dock Bar floating in 3D space
     */
    private fun drawQuestUniversalDock(
        drawScope: DrawScope,
        cameraPos: Vector3,
        pitch: Float,
        yaw: Float,
        roll: Float,
        width: Float,
        height: Float,
        fov: Float,
        dock: QuestDockState,
        settings: QuestQuickSettingsState
    ) {
        val anchor = dock.anchorPos
        val halfW = dock.width * 0.5f
        val halfH = dock.height * 0.5f

        val centerProj = VRMath.project3DTo2D(anchor, cameraPos, pitch, yaw, roll, width, height, fov)
        if (!centerProj.isVisible || centerProj.depth <= 0.2f) return

        val pTL = VRMath.project3DTo2D(anchor + Vector3(-halfW, halfH, 0f), cameraPos, pitch, yaw, roll, width, height, fov)
        val pTR = VRMath.project3DTo2D(anchor + Vector3(halfW, halfH, 0f), cameraPos, pitch, yaw, roll, width, height, fov)
        val pBR = VRMath.project3DTo2D(anchor + Vector3(halfW, -halfH, 0f), cameraPos, pitch, yaw, roll, width, height, fov)
        val pBL = VRMath.project3DTo2D(anchor + Vector3(-halfW, -halfH, 0f), cameraPos, pitch, yaw, roll, width, height, fov)

        val dockPath = Path().apply {
            moveTo(pTL.screenX, pTL.screenY)
            lineTo(pTR.screenX, pTR.screenY)
            lineTo(pBR.screenX, pBR.screenY)
            lineTo(pBL.screenX, pBL.screenY)
            close()
        }

        // Dock Capsule Background
        drawScope.drawPath(
            path = dockPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xF50F172A), Color(0xFA050914))
            )
        )
        drawScope.drawPath(
            path = dockPath,
            color = Color(0x6600E5FF),
            style = Stroke(width = 1.8f)
        )

        val scale = (1.0f / centerProj.depth) * width * 0.28f
        val nativeCanvas = drawScope.drawContext.canvas.nativeCanvas

        // Draw App Icons on the Dock
        val totalApps = dock.apps.size
        val appSlotWidth = dock.width * 0.88f / totalApps
        val startX = -((totalApps - 1) * appSlotWidth * 0.5f)

        for (i in 0 until totalApps) {
            val app = dock.apps[i]
            val appX = startX + i * appSlotWidth
            val appPos = anchor + Vector3(appX, 0f, 0f)
            val pApp = VRMath.project3DTo2D(appPos, cameraPos, pitch, yaw, roll, width, height, fov)

            if (pApp.isVisible) {
                val isHovered = dock.hoveredAppId == app.id
                val radius = (0.085f / pApp.depth) * width * (if (isHovered) 0.38f else 0.30f)
                val appColor = Color(app.color)

                // App Icon Squircle
                val size = radius * 2f
                drawScope.drawRoundRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(appColor, appColor.copy(alpha = 0.7f))
                    ),
                    topLeft = Offset(pApp.screenX - radius, pApp.screenY - radius),
                    size = Size(size, size),
                    cornerRadius = CornerRadius(radius * 0.35f, radius * 0.35f)
                )

                // Outer Hover Glow
                if (isHovered) {
                    drawScope.drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(pApp.screenX - radius * 1.15f, pApp.screenY - radius * 1.15f),
                        size = Size(size * 1.15f, size * 1.15f),
                        cornerRadius = CornerRadius(radius * 0.4f, radius * 0.4f),
                        style = Stroke(width = 2.5f)
                    )
                }

                // App Emoji & Title
                val emojiPaint = android.graphics.Paint().apply {
                    textSize = (radius * 0.95f).coerceIn(12f, 30f)
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                nativeCanvas.drawText(app.iconEmoji, pApp.screenX, pApp.screenY + radius * 0.32f, emojiPaint)

                // App Name Label below squircle
                val namePaint = android.graphics.Paint().apply {
                    color = if (isHovered) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#94A3B8")
                    textSize = (scale * 0.075f).coerceIn(8f, 13f)
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = if (isHovered) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                }
                nativeCanvas.drawText(app.name, pApp.screenX, pApp.screenY + radius + scale * 0.07f, namePaint)
            }
        }
    }

    private fun drawPassthroughGridOverlay(
        drawScope: DrawScope,
        cameraPos: Vector3,
        pitch: Float,
        yaw: Float,
        roll: Float,
        width: Float,
        height: Float,
        fov: Float
    ) {
        // Subtle cybernetic MR floor grid overlay in passthrough mode
        val floorY = -1.0f
        val gridColor = Color(0x2200E5FF)

        for (x in -4..4) {
            val pStart = VRMath.project3DTo2D(Vector3(x * 1.0f, floorY, 0.8f), cameraPos, pitch, yaw, roll, width, height, fov)
            val pEnd = VRMath.project3DTo2D(Vector3(x * 1.0f, floorY, 6.0f), cameraPos, pitch, yaw, roll, width, height, fov)
            if (pStart.isVisible && pEnd.isVisible) {
                drawScope.drawLine(
                    color = gridColor,
                    start = Offset(pStart.screenX, pStart.screenY),
                    end = Offset(pEnd.screenX, pEnd.screenY),
                    strokeWidth = 1f
                )
            }
        }
    }

    /**
     * Renders the 3D Meta Quest Touch Controller and Laser Raycast
     */
    private fun drawQuestTouchController(
        drawScope: DrawScope,
        cameraPos: Vector3,
        pitch: Float,
        yaw: Float,
        roll: Float,
        width: Float,
        height: Float,
        fov: Float,
        hand: TrackedHand,
        isRight: Boolean,
        settings: QuestQuickSettingsState
    ) {
        if (!hand.isTracked) return

        val handColor = Color(hand.color)
        val pHand = VRMath.project3DTo2D(hand.position, cameraPos, pitch, yaw, roll, width, height, fov)

        if (pHand.isVisible) {
            val scale = (0.1f / pHand.depth) * width * 0.35f

            // 1. Controller Grip Body (Dark ergonomic capsule)
            drawScope.drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF334155), Color(0xFF0F172A))
                ),
                topLeft = Offset(pHand.screenX - scale * 0.4f, pHand.screenY),
                size = Size(scale * 0.8f, scale * 1.8f),
                cornerRadius = CornerRadius(scale * 0.35f, scale * 0.35f)
            )

            // 2. Quest Tracking Sensor Ring (Oval above grip)
            drawScope.drawOval(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFF475569), Color(0xFF64748B), Color(0xFF334155))
                ),
                topLeft = Offset(pHand.screenX - scale * 0.85f, pHand.screenY - scale * 0.7f),
                size = Size(scale * 1.7f, scale * 0.9f),
                style = Stroke(width = 3.5f)
            )

            // Glowing Tracking Sensor Dots on Ring
            drawScope.drawCircle(
                color = handColor,
                radius = 2.5f,
                center = Offset(pHand.screenX - scale * 0.6f, pHand.screenY - scale * 0.35f)
            )
            drawScope.drawCircle(
                color = handColor,
                radius = 2.5f,
                center = Offset(pHand.screenX + scale * 0.6f, pHand.screenY - scale * 0.35f)
            )

            // 3. Thumbstick & Action Buttons (A/B or X/Y)
            drawScope.drawCircle(
                color = Color(0xFF1E293B),
                radius = scale * 0.25f,
                center = Offset(pHand.screenX - scale * 0.15f, pHand.screenY + scale * 0.2f)
            )
            drawScope.drawCircle(
                color = Color.White,
                radius = scale * 0.1f,
                center = Offset(pHand.screenX + scale * 0.2f, pHand.screenY + scale * 0.15f)
            )

            // 4. White Glowing Laser Raycast Beam from Controller Tip to Target
            val ray = hand.laserRay
            if (ray != null) {
                val rayStart = hand.position + Vector3(0f, 0.05f, 0.1f)
                val rayTarget = ray.getPoint(2.2f)

                val pRayStart = VRMath.project3DTo2D(rayStart, cameraPos, pitch, yaw, roll, width, height, fov)
                val pRayEnd = VRMath.project3DTo2D(rayTarget, cameraPos, pitch, yaw, roll, width, height, fov)

                if (pRayStart.isVisible && pRayEnd.isVisible) {
                    // Glowing Laser Line (White Core with Cyan Glow)
                    drawScope.drawLine(
                        brush = Brush.linearGradient(
                            colors = listOf(Color.White, Color(0xFF00E5FF).copy(alpha = 0.8f), Color(0x0000E5FF)),
                            start = Offset(pRayStart.screenX, pRayStart.screenY),
                            end = Offset(pRayEnd.screenX, pRayEnd.screenY)
                        ),
                        start = Offset(pRayStart.screenX, pRayStart.screenY),
                        end = Offset(pRayEnd.screenX, pRayEnd.screenY),
                        strokeWidth = if (hand.isPinching) 4.5f else 2.5f
                    )

                    // Laser Reticle Dot on the 3D surface
                    drawScope.drawCircle(
                        color = Color.White,
                        radius = if (hand.isPinching) 7f else 4.5f,
                        center = Offset(pRayEnd.screenX, pRayEnd.screenY)
                    )
                    drawScope.drawCircle(
                        color = Color(0xFF00E5FF),
                        radius = if (hand.isPinching) 13f else 9f,
                        center = Offset(pRayEnd.screenX, pRayEnd.screenY),
                        style = Stroke(width = 1.8f)
                    )
                }
            }
        }
    }

    private fun drawSkyAndGrid(
        drawScope: DrawScope,
        cameraPos: Vector3,
        pitch: Float,
        yaw: Float,
        roll: Float,
        width: Float,
        height: Float,
        fov: Float
    ) {
        // Deep Space Cyberpunk background gradient
        drawScope.drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF030712), Color(0xFF0B132B), Color(0xFF02040A))
            )
        )

        // 3D Perspective Grid Floor (Y = -1.0f)
        val floorY = -1.0f
        val gridColor = Color(0x3300E5FF)
        val highlightColor = Color(0x6600E5FF)

        // Longitudinal lines (Z from 0.5 to 16)
        for (x in -8..8) {
            val pStart = VRMath.project3DTo2D(Vector3(x * 0.8f, floorY, 0.8f), cameraPos, pitch, yaw, roll, width, height, fov)
            val pEnd = VRMath.project3DTo2D(Vector3(x * 0.8f, floorY, 16.0f), cameraPos, pitch, yaw, roll, width, height, fov)

            if (pStart.isVisible && pEnd.isVisible) {
                drawScope.drawLine(
                    color = if (x == 0) highlightColor else gridColor,
                    start = Offset(pStart.screenX, pStart.screenY),
                    end = Offset(pEnd.screenX, pEnd.screenY),
                    strokeWidth = if (x == 0) 2.5f else 1.2f
                )
            }
        }

        // Latitudinal lines
        for (z in 1..16) {
            val pLeft = VRMath.project3DTo2D(Vector3(-6.4f, floorY, z.toFloat()), cameraPos, pitch, yaw, roll, width, height, fov)
            val pRight = VRMath.project3DTo2D(Vector3(6.4f, floorY, z.toFloat()), cameraPos, pitch, yaw, roll, width, height, fov)

            if (pLeft.isVisible && pRight.isVisible) {
                drawScope.drawLine(
                    color = gridColor,
                    start = Offset(pLeft.screenX, pLeft.screenY),
                    end = Offset(pRight.screenX, pRight.screenY),
                    strokeWidth = 1.2f
                )
            }
        }
    }

    private fun drawRhythmSaber(
        drawScope: DrawScope,
        cameraPos: Vector3,
        pitch: Float,
        yaw: Float,
        roll: Float,
        width: Float,
        height: Float,
        fov: Float,
        blocks: List<SaberBlock>
    ) {
        for (block in blocks) {
            val pCenter = VRMath.project3DTo2D(block.position, cameraPos, pitch, yaw, roll, width, height, fov)
            if (pCenter.isVisible) {
                val sizeOnScreen = (block.size / pCenter.depth) * width * 0.45f
                val blockColor = Color(block.color)

                if (block.isCut) {
                    drawScope.drawCircle(
                        color = blockColor.copy(alpha = (1f - block.cutTimer * 2f).coerceAtLeast(0f)),
                        radius = sizeOnScreen * 1.5f,
                        center = Offset(pCenter.screenX, pCenter.screenY)
                    )
                } else {
                    val half = sizeOnScreen * 0.5f
                    val rect = Rect(
                        pCenter.screenX - half,
                        pCenter.screenY - half,
                        pCenter.screenX + half,
                        pCenter.screenY + half
                    )

                    drawScope.drawRoundRect(
                        color = blockColor.copy(alpha = 0.85f),
                        topLeft = Offset(rect.left, rect.top),
                        size = Size(rect.width, rect.height),
                        cornerRadius = CornerRadius(6f, 6f)
                    )

                    drawScope.drawRoundRect(
                        color = Color.White.copy(alpha = 0.9f),
                        topLeft = Offset(rect.left + half * 0.4f, rect.top + half * 0.4f),
                        size = Size(half * 1.2f, half * 1.2f),
                        cornerRadius = CornerRadius(4f, 4f)
                    )

                    drawScope.drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(rect.left, rect.top),
                        size = Size(rect.width, rect.height),
                        cornerRadius = CornerRadius(6f, 6f),
                        style = Stroke(width = 2.5f)
                    )
                }
            }
        }
    }

    private fun drawPhysicsEntities(
        drawScope: DrawScope,
        cameraPos: Vector3,
        pitch: Float,
        yaw: Float,
        roll: Float,
        width: Float,
        height: Float,
        fov: Float,
        entities: List<PhysicsEntity>
    ) {
        for (entity in entities) {
            val p = VRMath.project3DTo2D(entity.position, cameraPos, pitch, yaw, roll, width, height, fov)
            if (p.isVisible) {
                val radius = (entity.size / p.depth) * width * 0.35f
                val color = Color(entity.color)

                when (entity.shape) {
                    PhysicsShape.SPHERE -> {
                        drawScope.drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White, color, Color(0xFF001020)),
                                center = Offset(p.screenX - radius * 0.3f, p.screenY - radius * 0.3f),
                                radius = radius
                            ),
                            radius = radius,
                            center = Offset(p.screenX, p.screenY)
                        )
                        if (entity.isGrabbed) {
                            drawScope.drawCircle(
                                color = Color.White,
                                radius = radius * 1.2f,
                                center = Offset(p.screenX, p.screenY),
                                style = Stroke(width = 2f)
                            )
                        }
                    }
                    PhysicsShape.CUBE, PhysicsShape.PYRAMID, PhysicsShape.CYLINDER -> {
                        val half = radius
                        drawScope.drawRoundRect(
                            color = color.copy(alpha = if (entity.isGrabbed) 1.0f else 0.85f),
                            topLeft = Offset(p.screenX - half, p.screenY - half),
                            size = Size(half * 2, half * 2),
                            cornerRadius = CornerRadius(8f, 8f)
                        )
                        drawScope.drawRoundRect(
                            color = if (entity.isGrabbed) Color.White else color,
                            topLeft = Offset(p.screenX - half, p.screenY - half),
                            size = Size(half * 2, half * 2),
                            cornerRadius = CornerRadius(8f, 8f),
                            style = Stroke(width = if (entity.isGrabbed) 3f else 1.5f)
                        )
                    }
                }
            }
        }
    }

    private fun drawSolarSystem(
        drawScope: DrawScope,
        cameraPos: Vector3,
        pitch: Float,
        yaw: Float,
        roll: Float,
        width: Float,
        height: Float,
        fov: Float,
        planets: List<PlanetEntity>
    ) {
        for (planet in planets) {
            val p = VRMath.project3DTo2D(
                Vector3(
                    x = sin(planet.orbitAngle) * planet.distance,
                    y = sin(planet.orbitAngle * 2f) * 0.2f,
                    z = cos(planet.orbitAngle) * planet.distance + 1.0f
                ),
                cameraPos, pitch, yaw, roll, width, height, fov
            )

            if (p.isVisible) {
                val radius = (planet.radius / p.depth) * width * 0.35f
                val color = Color(planet.color)

                drawScope.drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.White, color, Color.Black),
                        center = Offset(p.screenX - radius * 0.3f, p.screenY - radius * 0.3f),
                        radius = radius
                    ),
                    radius = radius.coerceAtLeast(3f),
                    center = Offset(p.screenX, p.screenY)
                )

                if (planet.hasRings) {
                    drawScope.drawOval(
                        color = Color(0xCCFFE082),
                        topLeft = Offset(p.screenX - radius * 2.2f, p.screenY - radius * 0.6f),
                        size = Size(radius * 4.4f, radius * 1.2f),
                        style = Stroke(width = 2.5f)
                    )
                }
            }
        }
    }

    private fun drawTargetGallery(
        drawScope: DrawScope,
        cameraPos: Vector3,
        pitch: Float,
        yaw: Float,
        roll: Float,
        width: Float,
        height: Float,
        fov: Float,
        targets: List<TargetEntity>
    ) {
        for (target in targets) {
            if (target.isHit) continue

            val p = VRMath.project3DTo2D(target.position, cameraPos, pitch, yaw, roll, width, height, fov)
            if (p.isVisible) {
                val radius = (target.radius / p.depth) * width * 0.35f
                val color = Color(target.color)

                // Outer Red Bullseye Ring
                drawScope.drawCircle(
                    color = color,
                    radius = radius,
                    center = Offset(p.screenX, p.screenY)
                )
                // White Middle Ring
                drawScope.drawCircle(
                    color = Color.White,
                    radius = radius * 0.7f,
                    center = Offset(p.screenX, p.screenY)
                )
                // Red Core Bullseye
                drawScope.drawCircle(
                    color = color,
                    radius = radius * 0.35f,
                    center = Offset(p.screenX, p.screenY)
                )
            }
        }
    }

    private fun drawParticles(
        drawScope: DrawScope,
        cameraPos: Vector3,
        pitch: Float,
        yaw: Float,
        roll: Float,
        width: Float,
        height: Float,
        fov: Float,
        particles: List<VRParticle>
    ) {
        for (p in particles) {
            val proj = VRMath.project3DTo2D(p.position, cameraPos, pitch, yaw, roll, width, height, fov)
            if (proj.isVisible) {
                val size = (p.size / proj.depth).coerceIn(2f, 12f)
                drawScope.drawCircle(
                    color = Color(p.color).copy(alpha = p.alpha),
                    radius = size,
                    center = Offset(proj.screenX, proj.screenY)
                )
            }
        }
    }
}
