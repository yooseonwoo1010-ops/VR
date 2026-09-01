package com.example.vr.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
        vrBoxWindow: VRBoxWindowState,
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

        val isPassthrough = vrBoxWindow.isPassthroughActive || questSettings.isPassthroughEnabled || experience == VRExperience.PASSTHROUGH_MR

        // 1. Draw Starfield / Sky Space Background (when camera passthrough is off, or subtle grid)
        if (!isPassthrough) {
            drawSkyAndGrid(drawScope, cameraPos, pitch, yaw, roll, width, height, fov)
        } else {
            drawPassthroughGridOverlay(drawScope, cameraPos, pitch, yaw, roll, width, height, fov)
        }

        // 2. Draw 3D World-Anchored Grey Rounded-Corner Spatial Window (Fixed in 3D virtual world)
        if (vrBoxWindow.isVisible) {
            drawVRBoxWindow(drawScope, cameraPos, pitch, yaw, roll, width, height, fov, vrBoxWindow)
        }

        // 3. Draw 3D Hand Tracking with Grey Outline Border & Joints
        if (rightHand.isTracked) {
            drawTrackedGreyHand(drawScope, cameraPos, pitch, yaw, roll, width, height, fov, rightHand, isRight = true)
        }
        if (leftHand.isTracked) {
            drawTrackedGreyHand(drawScope, cameraPos, pitch, yaw, roll, width, height, fov, leftHand, isRight = false)
        }

        // 4. Draw 3D Burst Particles
        drawParticles(drawScope, cameraPos, pitch, yaw, roll, width, height, fov, particles)

        // 5. Draw Center Reticle / Crosshair for Head Gaze Aiming (with dwell ring)
        val forwardDir = VRMath.getForwardVector(pitch, yaw, roll)
        val centerProj = VRMath.project3DTo2D(
            pointWorld = cameraPos + forwardDir * 2.0f,
            cameraPos = cameraPos,
            pitch = pitch,
            yaw = yaw,
            roll = roll,
            screenWidth = width,
            screenHeight = height,
            fov = fov
        )
        if (centerProj.isVisible) {
            val isHovering = vrBoxWindow.hoveredButtonId != null
            val dwell = vrBoxWindow.gazeDwellProgress

            // Outer Gaze Aiming Ring
            drawScope.drawCircle(
                color = if (isHovering) Color(0xFF38BDF8).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.5f),
                radius = if (isHovering) 10f else 5f,
                center = Offset(centerProj.screenX, centerProj.screenY),
                style = Stroke(width = if (isHovering) 2.0f else 1.2f)
            )

            // Inner Center Dot
            drawScope.drawCircle(
                color = if (isHovering) Color.White else Color(0xCCFFFFFF),
                radius = 3.0f,
                center = Offset(centerProj.screenX, centerProj.screenY)
            )

            // Gaze Dwell Circular Progress Arc (when aiming at a button)
            if (isHovering && dwell > 0.01f) {
                drawScope.drawArc(
                    color = Color(0xFF00E5FF),
                    startAngle = -90f,
                    sweepAngle = dwell * 360f,
                    useCenter = false,
                    topLeft = Offset(centerProj.screenX - 16f, centerProj.screenY - 16f),
                    size = Size(32f, 32f),
                    style = Stroke(width = 3.0f, cap = StrokeCap.Round)
                )
            }
        }
    }


    /**
     * Renders the 3D World-Anchored Grey Rounded-Corner Spatial Window.
     * Fixed in virtual space: stays in place when head turns, scaled accurately by distance depth.
     */
    private fun drawVRBoxWindow(
        drawScope: DrawScope,
        cameraPos: Vector3,
        pitch: Float,
        yaw: Float,
        roll: Float,
        width: Float,
        height: Float,
        fov: Float,
        win: VRBoxWindowState
    ) {
        val anchor = win.anchorPos
        val centerProj = VRMath.project3DTo2D(anchor, cameraPos, pitch, yaw, roll, width, height, fov)
        if (!centerProj.isVisible || centerProj.depth <= 0.2f) return

        // Scale according to 3D distance depth
        val depthScale = (2.0f / centerProj.depth).coerceIn(0.25f, 2.5f)
        val cardW = (width * 0.52f * depthScale).coerceIn(160f, 900f)
        val cardH = (cardW * 0.62f).coerceIn(100f, 580f)

        val centerX = centerProj.screenX
        val centerY = centerProj.screenY

        val cardLeft = centerX - cardW * 0.5f
        val cardTop = centerY - cardH * 0.5f
        val cardRight = cardLeft + cardW
        val cardBottom = cardTop + cardH
        val cornerRad = (18f * depthScale).coerceIn(8f, 32f)

        // Compute billboard tilt angle from 3D orientation
        val pRight = VRMath.project3DTo2D(anchor + Vector3(1f, 0f, 0f), cameraPos, pitch, yaw, roll, width, height, fov)
        val rollAngleDeg = if (pRight.isVisible) {
            Math.toDegrees(atan2((pRight.screenY - centerProj.screenY).toDouble(), (pRight.screenX - centerProj.screenX).toDouble())).toFloat()
        } else {
            0f
        }

        drawScope.rotate(degrees = rollAngleDeg, pivot = Offset(centerX, centerY)) {
            // 1. Solid Slate Grey Rounded-Corner Floating Window Background (Anchored in 3D Space)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xF6282E39), // Slate Charcoal
                        Color(0xFA1C2128),
                        Color(0xFD13161C)
                    ),
                    startY = cardTop,
                    endY = cardBottom
                ),
                topLeft = Offset(cardLeft, cardTop),
                size = Size(cardW, cardH),
                cornerRadius = CornerRadius(cornerRad, cornerRad)
            )

            // Outer Clean Silver / Slate Border Stroke
            drawRoundRect(
                color = Color(0xFF94A3B8), // Sleek silver / light slate border
                topLeft = Offset(cardLeft, cardTop),
                size = Size(cardW, cardH),
                cornerRadius = CornerRadius(cornerRad, cornerRad),
                style = Stroke(width = (2.0f * depthScale).coerceIn(1.2f, 3.5f))
            )

            val nativeCanvas = drawContext.canvas.nativeCanvas

            // 2. Window Header Bar (Title & Live Status)
            val headerY = cardTop + cardH * 0.11f
            val titlePaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = (cardH * 0.065f).coerceIn(10f, 22f)
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.LEFT
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            }
            nativeCanvas.drawText("VR BOX SPATIAL MR", cardLeft + cardW * 0.04f, headerY, titlePaint)

            val statusPaint = android.graphics.Paint().apply {
                color = if (win.isPassthroughActive) android.graphics.Color.parseColor("#10B981") else android.graphics.Color.parseColor("#38BDF8")
                textSize = (cardH * 0.052f).coerceIn(8.5f, 16f)
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.RIGHT
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            }
            val statusText = if (win.isPassthroughActive) "● LIVE CAMERA MR" else "🌌 3D SPACE GRID"
            nativeCanvas.drawText(statusText, cardRight - cardW * 0.04f, headerY, statusPaint)

            // Header Divider Line
            val divY = cardTop + cardH * 0.16f
            drawLine(
                color = Color(0xFF334155),
                start = Offset(cardLeft + cardW * 0.035f, divY),
                end = Offset(cardRight - cardW * 0.035f, divY),
                strokeWidth = (1.2f * depthScale).coerceIn(0.8f, 2.0f)
            )

            // 3. Recessed Body Content Panel (Dark inset card with instruction text)
            val bodyLeft = cardLeft + cardW * 0.035f
            val bodyTop = cardTop + cardH * 0.20f
            val bodyW = cardW * 0.93f
            val bodyH = cardH * 0.46f

            drawRoundRect(
                color = Color(0xF20F1217),
                topLeft = Offset(bodyLeft, bodyTop),
                size = Size(bodyW, bodyH),
                cornerRadius = CornerRadius((14f * depthScale).coerceIn(6f, 20f), (14f * depthScale).coerceIn(6f, 20f))
            )
            drawRoundRect(
                color = Color(0xFF334155),
                topLeft = Offset(bodyLeft, bodyTop),
                size = Size(bodyW, bodyH),
                cornerRadius = CornerRadius((14f * depthScale).coerceIn(6f, 20f), (14f * depthScale).coerceIn(6f, 20f)),
                style = Stroke(width = (1.0f * depthScale).coerceIn(0.8f, 2.0f))
            )

            val bodyPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#E2E8F0")
                textSize = (cardH * 0.052f).coerceIn(8.5f, 16f)
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.LEFT
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            }
            val subPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.parseColor("#94A3B8")
                textSize = (cardH * 0.044f).coerceIn(7.5f, 14f)
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.LEFT
                typeface = android.graphics.Typeface.SANS_SERIF
            }

            nativeCanvas.drawText("• 가상 세계 속에 3D로 고정된 공간 창입니다.", bodyLeft + cardW * 0.03f, bodyTop + bodyH * 0.28f, bodyPaint)
            nativeCanvas.drawText("• 머리를 돌려 시야를 이동해도 창이 해당 위치에 유지됩니다.", bodyLeft + cardW * 0.03f, bodyTop + bodyH * 0.56f, subPaint)
            nativeCanvas.drawText("• 손을 카메라에 비추면 회색 테두리 핸드트래킹이 활성화됩니다.", bodyLeft + cardW * 0.03f, bodyTop + bodyH * 0.82f, subPaint)

            // 4. Interactive Bottom Action Buttons
            val buttons = listOf(
                Triple("btn_recenter", "🧭 시점정렬", Color(0xFF1E242E)),
                Triple("btn_passthrough", if (win.isPassthroughActive) "📷 MR: ON" else "📷 MR: OFF", if (win.isPassthroughActive) Color(0xFF065F46) else Color(0xFF1E242E)),
                Triple("btn_ipd", "👓 ${win.ipdMm.toInt()}mm", Color(0xFF1E242E)),
                Triple("btn_proceed", "✓ 시작하기", Color(0xFF1E3A5F))
            )

            val btnTop = cardTop + cardH * 0.70f
            val btnH = cardH * 0.22f
            val btnSpacing = cardW * 0.02f
            val btnW = (cardW * 0.93f - btnSpacing * 3f) / 4f
            val btnStartX = cardLeft + cardW * 0.035f

            for (i in buttons.indices) {
                val (id, label, defaultBg) = buttons[i]
                val isHovered = win.hoveredButtonId == id
                val isPrimary = (id == "btn_proceed")
                val bX = btnStartX + i * (btnW + btnSpacing)

                val btnBgColor = if (isHovered) Color(0xFF0284C7) else defaultBg
                val btnStrokeColor = when {
                    isHovered -> Color.White
                    isPrimary -> Color(0xFF38BDF8)
                    else -> Color(0xFF475569)
                }

                drawRoundRect(
                    color = btnBgColor,
                    topLeft = Offset(bX, btnTop),
                    size = Size(btnW, btnH),
                    cornerRadius = CornerRadius((12f * depthScale).coerceIn(5f, 18f), (12f * depthScale).coerceIn(5f, 18f))
                )
                drawRoundRect(
                    color = btnStrokeColor,
                    topLeft = Offset(bX, btnTop),
                    size = Size(btnW, btnH),
                    cornerRadius = CornerRadius((12f * depthScale).coerceIn(5f, 18f), (12f * depthScale).coerceIn(5f, 18f)),
                    style = Stroke(width = if (isHovered) (2.0f * depthScale).coerceIn(1.5f, 3.5f) else (1.0f * depthScale).coerceIn(0.8f, 2.0f))
                )

                val btnTextPaint = android.graphics.Paint().apply {
                    color = if (isHovered) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#F8FAFC")
                    textSize = (cardH * 0.048f).coerceIn(7.5f, 15f)
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = if (isHovered || isPrimary) {
                        android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                    } else {
                        android.graphics.Typeface.SANS_SERIF
                    }
                }
                nativeCanvas.drawText(label, bX + btnW * 0.5f, btnTop + btnH * 0.62f, btnTextPaint)
            }
        }
    }

    /**
     * Renders 3D Tracked Hand with a distinct Grey Border / Outline (손에 회색 테두리)
     */
    private fun drawTrackedGreyHand(
        drawScope: DrawScope,
        cameraPos: Vector3,
        pitch: Float,
        yaw: Float,
        roll: Float,
        width: Float,
        height: Float,
        fov: Float,
        hand: TrackedHand,
        isRight: Boolean
    ) {
        if (!hand.isTracked) return

        val pHand = VRMath.project3DTo2D(hand.position, cameraPos, pitch, yaw, roll, width, height, fov)
        if (!pHand.isVisible || pHand.depth <= 0.1f) return

        val scale = (0.1f / pHand.depth) * width * 0.35f
        val greyOutline = Color(0xFF94A3B8) // Slate silver grey
        val greyDark = Color(0xFF475569)
        val greyLight = Color(0xFFCBD5E1)
        val fillTranslucent = Color(0x33334155)

        // 1. Hand Contour Silhouette with Grey Border Outline (손에 회색 테두리)
        if (hand.contourPoints.size >= 4) {
            val contourPath = Path()
            var firstPoint = true
            for (pt in hand.contourPoints) {
                val proj = VRMath.project3DTo2D(pt, cameraPos, pitch, yaw, roll, width, height, fov)
                if (proj.isVisible) {
                    if (firstPoint) {
                        contourPath.moveTo(proj.screenX, proj.screenY)
                        firstPoint = false
                    } else {
                        contourPath.lineTo(proj.screenX, proj.screenY)
                    }
                }
            }
            contourPath.close()

            // Translucent grey hand body mesh fill
            drawScope.drawPath(
                path = contourPath,
                color = fillTranslucent
            )

            // Outer subtle halo outline
            drawScope.drawPath(
                path = contourPath,
                color = greyDark.copy(alpha = 0.5f),
                style = Stroke(width = 4.0f)
            )

            // Sharp Grey Border Contour Stroke (회색 테두리)
            drawScope.drawPath(
                path = contourPath,
                color = greyOutline,
                style = Stroke(width = 2.5f)
            )
        }

        // 2. Skeletal Finger Bones & Joints
        val pWrist = VRMath.project3DTo2D(hand.wristPosition, cameraPos, pitch, yaw, roll, width, height, fov)
        val pThumb = VRMath.project3DTo2D(hand.thumbTip, cameraPos, pitch, yaw, roll, width, height, fov)
        val pIndex = VRMath.project3DTo2D(hand.indexTip, cameraPos, pitch, yaw, roll, width, height, fov)
        val pMiddle = VRMath.project3DTo2D(hand.middleTip, cameraPos, pitch, yaw, roll, width, height, fov)
        val pRing = VRMath.project3DTo2D(hand.ringTip, cameraPos, pitch, yaw, roll, width, height, fov)
        val pPinky = VRMath.project3DTo2D(hand.pinkyTip, cameraPos, pitch, yaw, roll, width, height, fov)

        val palmCenter = pHand
        val fingerTips = listOf(pThumb, pIndex, pMiddle, pRing, pPinky)

        for (tip in fingerTips) {
            if (tip.isVisible && palmCenter.isVisible) {
                // Bone segment line
                drawScope.drawLine(
                    color = greyDark.copy(alpha = 0.7f),
                    start = Offset(palmCenter.screenX, palmCenter.screenY),
                    end = Offset(tip.screenX, tip.screenY),
                    strokeWidth = 1.8f
                )
                // Fingertip Node (Crisp white with grey border ring)
                drawScope.drawCircle(
                    color = Color.White,
                    radius = (scale * 0.08f).coerceIn(2.5f, 6.0f),
                    center = Offset(tip.screenX, tip.screenY)
                )
                drawScope.drawCircle(
                    color = greyOutline,
                    radius = (scale * 0.14f).coerceIn(4.0f, 9.0f),
                    center = Offset(tip.screenX, tip.screenY),
                    style = Stroke(width = 1.5f)
                )
            }
        }

        if (pWrist.isVisible && palmCenter.isVisible) {
            drawScope.drawLine(
                color = greyOutline.copy(alpha = 0.8f),
                start = Offset(pWrist.screenX, pWrist.screenY),
                end = Offset(palmCenter.screenX, palmCenter.screenY),
                strokeWidth = 2.5f
            )
            drawScope.drawCircle(
                color = greyLight,
                radius = (scale * 0.15f).coerceIn(4.0f, 10.0f),
                center = Offset(pWrist.screenX, pWrist.screenY),
                style = Stroke(width = 2.0f)
            )
        }

        // Palm Center Sensor Node
        drawScope.drawCircle(
            color = if (hand.isPinching) Color.White else greyLight,
            radius = (scale * 0.18f).coerceIn(5.0f, 14.0f),
            center = Offset(pHand.screenX, pHand.screenY)
        )
        drawScope.drawCircle(
            color = greyOutline,
            radius = (scale * 0.28f).coerceIn(8.0f, 20.0f),
            center = Offset(pHand.screenX, pHand.screenY),
            style = Stroke(width = 1.8f)
        )

        // 3. Pinching Feedback Ring
        if (hand.isPinching && pIndex.isVisible && pThumb.isVisible) {
            val pinchX = (pIndex.screenX + pThumb.screenX) * 0.5f
            val pinchY = (pIndex.screenY + pThumb.screenY) * 0.5f
            drawScope.drawCircle(
                color = Color.White,
                radius = 7f,
                center = Offset(pinchX, pinchY)
            )
            drawScope.drawCircle(
                color = Color(0xFF38BDF8),
                radius = 15f,
                center = Offset(pinchX, pinchY),
                style = Stroke(width = 2.5f)
            )
        }

        // 4. Index Finger Laser Pointer Beam
        val ray = hand.laserRay
        if (ray != null && pIndex.isVisible) {
            val rayTarget = ray.getPoint(2.0f)
            val pRayEnd = VRMath.project3DTo2D(rayTarget, cameraPos, pitch, yaw, roll, width, height, fov)
            if (pRayEnd.isVisible) {
                drawScope.drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.8f), greyOutline.copy(alpha = 0.5f), Color.Transparent),
                        start = Offset(pIndex.screenX, pIndex.screenY),
                        end = Offset(pRayEnd.screenX, pRayEnd.screenY)
                    ),
                    start = Offset(pIndex.screenX, pIndex.screenY),
                    end = Offset(pRayEnd.screenX, pRayEnd.screenY),
                    strokeWidth = if (hand.isPinching) 3.0f else 1.5f
                )
            }
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

        // Glass Highlight & Soft Accent Rim Light
        drawScope.drawPath(
            path = panelPath,
            color = Color(0x5538BDF8),
            style = Stroke(width = 1.5f)
        )

        val scale = (1.0f / centerProj.depth) * width * 0.28f
        val nativeCanvas = drawScope.drawContext.canvas.nativeCanvas

        // 2. Window Header Bar (Status Info: Time, Battery, Title)
        val pHeader = VRMath.project3DTo2D(anchor + Vector3(0f, halfH * 0.80f, 0f), cameraPos, pitch, yaw, roll, width, height, fov)
        if (pHeader.isVisible) {
            // Header divider line
            val pDivL = VRMath.project3DTo2D(anchor + Vector3(-halfW * 0.90f, halfH * 0.65f, 0f), cameraPos, pitch, yaw, roll, width, height, fov)
            val pDivR = VRMath.project3DTo2D(anchor + Vector3(halfW * 0.90f, halfH * 0.65f, 0f), cameraPos, pitch, yaw, roll, width, height, fov)
            if (pDivL.isVisible && pDivR.isVisible) {
                drawScope.drawLine(
                    color = Color(0x33475569),
                    start = Offset(pDivL.screenX, pDivL.screenY),
                    end = Offset(pDivR.screenX, pDivR.screenY),
                    strokeWidth = 1.0f
                )
            }

            // Title & Battery Paint
            val headerPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.WHITE
                textSize = (scale * 0.095f).coerceIn(11f, 20f)
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.LEFT
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            }
            val pTitle = VRMath.project3DTo2D(anchor + Vector3(-halfW * 0.85f, halfH * 0.80f, 0f), cameraPos, pitch, yaw, roll, width, height, fov)
            if (pTitle.isVisible) {
                nativeCanvas.drawText("Quick Settings", pTitle.screenX, pTitle.screenY, headerPaint)
            }

            val batteryIcon = if (settings.isCharging) "⚡" else "🔋"
            val statusPaint = android.graphics.Paint().apply {
                color = if (settings.batteryPercent <= 20) {
                    android.graphics.Color.parseColor("#FF5252")
                } else if (settings.isCharging) {
                    android.graphics.Color.parseColor("#00E676")
                } else {
                    android.graphics.Color.parseColor("#38BDF8")
                }
                textSize = (scale * 0.085f).coerceIn(10f, 18f)
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.RIGHT
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            }
            val pStatus = VRMath.project3DTo2D(anchor + Vector3(halfW * 0.85f, halfH * 0.80f, 0f), cameraPos, pitch, yaw, roll, width, height, fov)
            if (pStatus.isVisible) {
                nativeCanvas.drawText("$batteryIcon ${settings.batteryPercent}%  •  ${settings.timeString}", pStatus.screenX, pStatus.screenY, statusPaint)
            }
        }

        // 3. Top 3 Feature Cards: [Wi-Fi], [Guardian], [Quest Link]
        val tileConfigs = listOf(
            Triple(-0.54f, "tile_wifi", Triple(if (settings.isWifiEnabled) 0xFF0284C7 else 0xFF1E293B, "Wi-Fi", settings.wifiName)),
            Triple(0.00f, "tile_guardian", Triple(if (settings.isGuardianEnabled) 0xFF059669 else 0xFF1E293B, "Guardian", "Stationary Room")),
            Triple(0.54f, "tile_link", Triple(if (settings.isQuestLinkActive) 0xFF7C3AED else 0xFF1E293B, "Quest Link", "Air Link Active"))
        )

        for ((relX, id, data) in tileConfigs) {
            val (colorLong, title, subtitle) = data
            val tileCenter = anchor + Vector3(relX, halfH * 0.32f, 0f)
            val tHalfW = 0.22f
            val tHalfH = 0.14f

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
                        tileColor.copy(alpha = if (isHovered) 0.90f else 0.65f),
                        Color(0xFF0F172A).copy(alpha = 0.85f)
                    )
                )
            )

            drawScope.drawPath(
                path = tilePath,
                color = if (isHovered) Color.White else tileColor.copy(alpha = 0.7f),
                style = Stroke(width = if (isHovered) 2.0f else 1.0f)
            )

            // Text on tile
            val tC = VRMath.project3DTo2D(tileCenter, cameraPos, pitch, yaw, roll, width, height, fov)
            if (tC.isVisible) {
                val tileTitlePaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = (scale * 0.085f).coerceIn(10f, 17f)
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                }
                val tileSubPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.parseColor("#94A3B8")
                    textSize = (scale * 0.065f).coerceIn(8f, 12f)
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                nativeCanvas.drawText(title, tC.screenX, tC.screenY - scale * 0.015f, tileTitlePaint)
                nativeCanvas.drawText(subtitle, tC.screenX, tC.screenY + scale * 0.05f, tileSubPaint)
            }
        }

        // 4. Middle Row: Quick Action Round/Pill Buttons (Mic, Passthrough, Volume, Brightness, Night, Record)
        val actionButtons = listOf(
            Triple(-0.62f, "btn_mic", if (settings.isMicMuted) "MIC OFF" else "MIC ON"),
            Triple(-0.38f, "btn_passthrough", if (settings.isPassthroughEnabled) "PASSTHROUGH" else "VR WORLD"),
            Triple(-0.14f, "btn_volume", "VOL ${((settings.volumeLevel * 100).toInt())}%"),
            Triple(0.12f, "btn_brightness", "BRIGHT ${((settings.brightnessLevel * 100).toInt())}%"),
            Triple(0.38f, "btn_night", if (settings.isNightMode) "NIGHT ON" else "NIGHT OFF"),
            Triple(0.64f, "btn_record", if (settings.isRecording) "RECORDING" else "CAST SCREEN")
        )

        for ((relX, id, label) in actionButtons) {
            val btnPos = anchor + Vector3(relX, -halfH * 0.18f, 0f)
            val btnProj = VRMath.project3DTo2D(btnPos, cameraPos, pitch, yaw, roll, width, height, fov)
            if (btnProj.isVisible) {
                val isHovered = settings.hoveredElementId == id
                val isPill = (id == "btn_record" || id == "btn_passthrough")
                val btnRadius = scale * 0.16f

                val btnBgColor = when (id) {
                    "btn_record" -> if (settings.isRecording) Color(0xFFFF0055) else Color(0xFF0284C7)
                    "btn_passthrough" -> if (settings.isPassthroughEnabled) Color(0xFF00E5FF) else Color(0xFF1E293B)
                    "btn_mic" -> if (settings.isMicMuted) Color(0xFFFF5252) else Color(0xFF1E293B)
                    "btn_night" -> if (settings.isNightMode) Color(0xFFFFB300) else Color(0xFF1E293B)
                    else -> Color(0xFF1E293B)
                }

                if (isPill) {
                    val pillWidth = btnRadius * 2.6f
                    val pillHeight = btnRadius * 1.2f
                    drawScope.drawRoundRect(
                        color = btnBgColor,
                        topLeft = Offset(btnProj.screenX - pillWidth * 0.5f, btnProj.screenY - pillHeight * 0.5f),
                        size = Size(pillWidth, pillHeight),
                        cornerRadius = CornerRadius(pillHeight * 0.5f, pillHeight * 0.5f)
                    )
                    drawScope.drawRoundRect(
                        color = if (isHovered) Color.White else Color(0x5500E5FF),
                        topLeft = Offset(btnProj.screenX - pillWidth * 0.5f, btnProj.screenY - pillHeight * 0.5f),
                        size = Size(pillWidth, pillHeight),
                        cornerRadius = CornerRadius(pillHeight * 0.5f, pillHeight * 0.5f),
                        style = Stroke(width = if (isHovered) 2.0f else 1.0f)
                    )
                } else {
                    drawScope.drawCircle(
                        color = btnBgColor,
                        radius = btnRadius,
                        center = Offset(btnProj.screenX, btnProj.screenY)
                    )
                    drawScope.drawCircle(
                        color = if (isHovered) Color.White else Color(0x4400E5FF),
                        radius = btnRadius,
                        center = Offset(btnProj.screenX, btnProj.screenY),
                        style = Stroke(width = if (isHovered) 2.0f else 1.0f)
                    )
                }

                // Label below button
                val btnPaint = android.graphics.Paint().apply {
                    color = if (isHovered) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#E2E8F0")
                    textSize = (scale * 0.065f).coerceIn(8f, 13f)
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = if (isHovered) android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD) else android.graphics.Typeface.SANS_SERIF
                }
                nativeCanvas.drawText(label, btnProj.screenX, btnProj.screenY + (if (isPill) 4f else btnRadius + scale * 0.06f), btnPaint)
            }
        }

        // 5. Bottom Sub-Bar: [🧭 시점 정렬 (Recenter Spatial Anchor)]
        val pRecenter = VRMath.project3DTo2D(anchor + Vector3(0f, -halfH * 0.65f, 0f), cameraPos, pitch, yaw, roll, width, height, fov)
        if (pRecenter.isVisible) {
            val isHovered = settings.hoveredElementId == "btn_recenter"
            val rWidth = scale * 2.6f
            val rHeight = scale * 0.50f

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
                style = Stroke(width = if (isHovered) 2.0f else 1.0f)
            )

            val recenterPaint = android.graphics.Paint().apply {
                color = if (isHovered) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#00E5FF")
                textSize = (scale * 0.075f).coerceIn(9f, 15f)
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.CENTER
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
            }
            nativeCanvas.drawText("RECENTER VIEW (시점 정렬)", pRecenter.screenX, pRecenter.screenY + scale * 0.025f, recenterPaint)
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

                // App Icon Glyph (Crisp vector geometric glyph)
                when (app.id) {
                    "saber" -> {
                        // Dual crossed sabers
                        drawScope.drawLine(
                            color = Color.White,
                            start = Offset(pApp.screenX - radius * 0.45f, pApp.screenY + radius * 0.45f),
                            end = Offset(pApp.screenX + radius * 0.45f, pApp.screenY - radius * 0.45f),
                            strokeWidth = 2.5f
                        )
                        drawScope.drawLine(
                            color = Color(0xFF00E5FF),
                            start = Offset(pApp.screenX - radius * 0.45f, pApp.screenY - radius * 0.45f),
                            end = Offset(pApp.screenX + radius * 0.45f, pApp.screenY + radius * 0.45f),
                            strokeWidth = 2.5f
                        )
                    }
                    "physics" -> {
                        // 3D Cube outline glyph
                        drawScope.drawRect(
                            color = Color.White,
                            topLeft = Offset(pApp.screenX - radius * 0.35f, pApp.screenY - radius * 0.35f),
                            size = Size(radius * 0.7f, radius * 0.7f),
                            style = Stroke(width = 2.0f)
                        )
                    }
                    "space" -> {
                        // Saturn ring planet glyph
                        drawScope.drawCircle(
                            color = Color.White,
                            radius = radius * 0.35f,
                            center = Offset(pApp.screenX, pApp.screenY)
                        )
                        drawScope.drawOval(
                            color = Color(0xFF00E5FF),
                            topLeft = Offset(pApp.screenX - radius * 0.65f, pApp.screenY - radius * 0.22f),
                            size = Size(radius * 1.3f, radius * 0.44f),
                            style = Stroke(width = 1.8f)
                        )
                    }
                    "target" -> {
                        // Bullseye concentric target glyph
                        drawScope.drawCircle(
                            color = Color.White,
                            radius = radius * 0.45f,
                            center = Offset(pApp.screenX, pApp.screenY),
                            style = Stroke(width = 2.0f)
                        )
                        drawScope.drawCircle(
                            color = Color.White,
                            radius = radius * 0.18f,
                            center = Offset(pApp.screenX, pApp.screenY)
                        )
                    }
                    "passthrough" -> {
                        // Camera / VR headset visor glyph
                        drawScope.drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(pApp.screenX - radius * 0.5f, pApp.screenY - radius * 0.28f),
                            size = Size(radius * 1.0f, radius * 0.56f),
                            cornerRadius = CornerRadius(radius * 0.2f, radius * 0.2f),
                            style = Stroke(width = 2.0f)
                        )
                        drawScope.drawCircle(
                            color = Color(0xFF00E676),
                            radius = radius * 0.14f,
                            center = Offset(pApp.screenX, pApp.screenY)
                        )
                    }
                    else -> {
                        // Clean initials text fallback
                        val initialPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = (radius * 0.75f).coerceIn(11f, 22f)
                            isAntiAlias = true
                            textAlign = android.graphics.Paint.Align.CENTER
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                        }
                        nativeCanvas.drawText(app.name.take(2).uppercase(), pApp.screenX, pApp.screenY + radius * 0.28f, initialPaint)
                    }
                }

                // App Name Label below squircle
                val namePaint = android.graphics.Paint().apply {
                    color = if (isHovered) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#94A3B8")
                    textSize = (scale * 0.075f).coerceIn(8f, 13f)
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = if (isHovered) android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD) else android.graphics.Typeface.SANS_SERIF
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
     * Renders authentic Quest Hand Tracking (Glowing Skeleton Joints & Outline Contour) or Quest Touch Controller
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

        val pHand = VRMath.project3DTo2D(hand.position, cameraPos, pitch, yaw, roll, width, height, fov)
        if (!pHand.isVisible) return

        val scale = (0.1f / pHand.depth) * width * 0.35f
        val nativeCanvas = drawScope.drawContext.canvas.nativeCanvas

        // 1. RENDER GENUINE QUEST HAND TRACKING OUTLINE & GHOST SKELETON
        val cyanGlow = Color(0xFF00E5FF)
        val magentaGlow = Color(0xFFFF0077)
        val themeColor = if (isRight) cyanGlow else magentaGlow

        // A. Draw Hand Silhouette / Contour Outline (Meta Quest Ghost Hand style)
        if (hand.contourPoints.size >= 4) {
            val contourPath = Path()
            var firstPoint = true
            for (pt in hand.contourPoints) {
                val proj = VRMath.project3DTo2D(pt, cameraPos, pitch, yaw, roll, width, height, fov)
                if (proj.isVisible) {
                    if (firstPoint) {
                        contourPath.moveTo(proj.screenX, proj.screenY)
                        firstPoint = false
                    } else {
                        contourPath.lineTo(proj.screenX, proj.screenY)
                    }
                }
            }
            contourPath.close()
            // Semi-transparent hand mesh fill
            drawScope.drawPath(
                path = contourPath,
                color = themeColor.copy(alpha = 0.10f)
            )
            // Glowing neon hand boundary contour stroke
            drawScope.drawPath(
                path = contourPath,
                color = themeColor.copy(alpha = 0.60f),
                style = Stroke(width = 1.5f)
            )
        }

        // B. Draw Hand Bones & Skeletal Joints
        val pWrist = VRMath.project3DTo2D(hand.wristPosition, cameraPos, pitch, yaw, roll, width, height, fov)
        val pIndex = VRMath.project3DTo2D(hand.indexTip, cameraPos, pitch, yaw, roll, width, height, fov)
        val pThumb = VRMath.project3DTo2D(hand.thumbTip, cameraPos, pitch, yaw, roll, width, height, fov)
        val pMiddle = VRMath.project3DTo2D(hand.middleTip, cameraPos, pitch, yaw, roll, width, height, fov)
        val pRing = VRMath.project3DTo2D(hand.ringTip, cameraPos, pitch, yaw, roll, width, height, fov)
        val pPinky = VRMath.project3DTo2D(hand.pinkyTip, cameraPos, pitch, yaw, roll, width, height, fov)

        val palmCenter = pHand

        // Bone lines connecting wrist to palm and fingertips
        val joints = listOf(pThumb, pIndex, pMiddle, pRing, pPinky)
        for (joint in joints) {
            if (joint.isVisible && palmCenter.isVisible) {
                // Palm to fingertip bone
                drawScope.drawLine(
                    color = themeColor.copy(alpha = 0.6f),
                    start = Offset(palmCenter.screenX, palmCenter.screenY),
                    end = Offset(joint.screenX, joint.screenY),
                    strokeWidth = 2.0f
                )
                // Fingertip glowing joint sphere
                drawScope.drawCircle(
                    color = Color.White,
                    radius = (scale * 0.08f).coerceIn(2.5f, 6.0f),
                    center = Offset(joint.screenX, joint.screenY)
                )
                drawScope.drawCircle(
                    color = themeColor,
                    radius = (scale * 0.14f).coerceIn(4.0f, 10.0f),
                    center = Offset(joint.screenX, joint.screenY),
                    style = Stroke(width = 1.5f)
                )
            }
        }

        if (pWrist.isVisible && palmCenter.isVisible) {
            // Wrist to palm base bone
            drawScope.drawLine(
                color = themeColor.copy(alpha = 0.7f),
                start = Offset(pWrist.screenX, pWrist.screenY),
                end = Offset(palmCenter.screenX, palmCenter.screenY),
                strokeWidth = 2.8f
            )
            // Wrist joint ring
            drawScope.drawCircle(
                color = themeColor,
                radius = (scale * 0.16f).coerceIn(5.0f, 12.0f),
                center = Offset(pWrist.screenX, pWrist.screenY),
                style = Stroke(width = 2.0f)
            )
        }

        // Palm Core Sensor Glow
        drawScope.drawCircle(
            color = if (hand.isPinching) Color.White else themeColor,
            radius = (scale * 0.22f).coerceIn(6.0f, 16.0f),
            center = Offset(pHand.screenX, pHand.screenY)
        )
        drawScope.drawCircle(
            color = themeColor.copy(alpha = 0.5f),
            radius = (scale * 0.35f).coerceIn(10.0f, 24.0f),
            center = Offset(pHand.screenX, pHand.screenY),
            style = Stroke(width = 2.0f)
        )

        // Pinch Indicator Ring when index + thumb touch
        if (hand.isPinching && pIndex.isVisible && pThumb.isVisible) {
            val pinchMidX = (pIndex.screenX + pThumb.screenX) * 0.5f
            val pinchMidY = (pIndex.screenY + pThumb.screenY) * 0.5f
            drawScope.drawCircle(
                color = Color.White,
                radius = 8f,
                center = Offset(pinchMidX, pinchMidY)
            )
            drawScope.drawCircle(
                color = Color(0xFF00E676),
                radius = 16f,
                center = Offset(pinchMidX, pinchMidY),
                style = Stroke(width = 2.5f)
            )
        }

        // 2. White Glowing Laser Raycast Beam from Index Tip to Target
        val ray = hand.laserRay
        if (ray != null) {
            val rayStart = hand.indexTip
            val rayTarget = ray.getPoint(2.2f)

            val pRayStart = VRMath.project3DTo2D(rayStart, cameraPos, pitch, yaw, roll, width, height, fov)
            val pRayEnd = VRMath.project3DTo2D(rayTarget, cameraPos, pitch, yaw, roll, width, height, fov)

            if (pRayStart.isVisible && pRayEnd.isVisible) {
                // Glowing Laser Line (Soft gradient)
                drawScope.drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.85f), themeColor.copy(alpha = 0.65f), Color(0x00000000)),
                        start = Offset(pRayStart.screenX, pRayStart.screenY),
                        end = Offset(pRayEnd.screenX, pRayEnd.screenY)
                    ),
                    start = Offset(pRayStart.screenX, pRayStart.screenY),
                    end = Offset(pRayEnd.screenX, pRayEnd.screenY),
                    strokeWidth = if (hand.isPinching) 3.5f else 1.8f
                )

                // Laser Reticle Dot on the 3D surface
                drawScope.drawCircle(
                    color = Color.White,
                    radius = if (hand.isPinching) 5f else 3f,
                    center = Offset(pRayEnd.screenX, pRayEnd.screenY)
                )
                drawScope.drawCircle(
                    color = themeColor,
                    radius = if (hand.isPinching) 10f else 6f,
                    center = Offset(pRayEnd.screenX, pRayEnd.screenY),
                    style = Stroke(width = 1.5f)
                )
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
