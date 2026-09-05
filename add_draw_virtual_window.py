import re

with open("app/src/main/java/com/example/vr/ui/VRRenderer.kt", "r") as f:
    code = f.read()

draw_func = """
    fun drawVirtualWindow(
        drawScope: DrawScope,
        cameraPos: Vector3,
        viewMatrix: FloatArray,
        width: Float,
        height: Float,
        fov: Float,
        win: VirtualWindow,
        textMeasurer: TextMeasurer
    ) {
        if (!win.isVisible) return

        val corners = win.getCorners()
        val projected = VRMath.projectClippedPolygon(corners, cameraPos, viewMatrix, width, height, fov)
        if (projected.size < 3) return

        val panelPath = Path().apply {
            moveTo(projected[0].screenX, projected[0].screenY)
            for (i in 1 until projected.size) {
                lineTo(projected[i].screenX, projected[i].screenY)
            }
            close()
        }

        // 1. Draw Window Background (Glassy Dark)
        drawScope.drawPath(
            path = panelPath,
            color = Color(0xD91E293B) // Dark slate with alpha
        )

        // 2. Draw Outer Border (Rounded corners cannot be done easily with arbitrary polygon, but stroke is fine)
        drawScope.drawPath(
            path = panelPath,
            color = Color(0xFF94A3B8),
            style = Stroke(width = 3f)
        )

        // 3. Clip Content Area
        drawScope.clipPath(panelPath) {
            val pCenter = VRMath.project3DTo2D(win.position, cameraPos, viewMatrix, width, height, fov)
            if (pCenter.isVisible && pCenter.depth > 0.1f) {
                val scale = (1.5f / pCenter.depth) * width * 0.0012f

                drawScope.translate(pCenter.screenX, pCenter.screenY) {
                    drawScope.scale(scale, scale) {
                        // Title
                        val titleResult = textMeasurer.measure(
                            text = win.title,
                            style = TextStyle(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        drawText(
                            textLayoutResult = titleResult,
                            topLeft = Offset(-titleResult.size.width / 2f, -150f)
                        )

                        // Divider
                        drawScope.drawLine(
                            color = Color(0xFF475569),
                            start = Offset(-200f, -110f),
                            end = Offset(200f, -110f),
                            strokeWidth = 2f
                        )

                        // Content (SoftWrap enabled)
                        val textResult = textMeasurer.measure(
                            text = win.textContent,
                            style = TextStyle(
                                fontSize = 16.sp,
                                color = Color(0xFFE2E8F0)
                            ),
                            softWrap = true,
                            maxLines = 10,
                            overflow = TextOverflow.Ellipsis,
                            constraints = androidx.compose.ui.unit.Constraints(maxWidth = 400)
                        )
                        drawText(
                            textLayoutResult = textResult,
                            topLeft = Offset(-200f, -80f)
                        )
                        
                        // Buttons (Fake UI for now)
                        val btnPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#3B82F6") // Blue
                            isAntiAlias = true
                        }
                        drawScope.drawContext.canvas.nativeCanvas.drawRoundRect(
                            -100f, 100f, 100f, 150f, 12f, 12f, btnPaint
                        )
                        val btnText = textMeasurer.measure(
                            text = "RECENTER",
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        )
                        drawText(
                            textLayoutResult = btnText,
                            topLeft = Offset(-btnText.size.width / 2f, 115f)
                        )
                    }
                }
            }
        }
    }
"""

if "fun drawVirtualWindow(" not in code:
    code = code.replace("object VRRenderer {", "object VRRenderer {\n" + draw_func)
    with open("app/src/main/java/com/example/vr/ui/VRRenderer.kt", "w") as f:
        f.write(code)

