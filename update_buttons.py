import re

with open("app/src/main/java/com/example/vr/ui/VRRenderer.kt", "r") as f:
    code = f.read()

replacement = """                        // Buttons (Fake UI for now)
                        val btnPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#3B82F6") // Blue
                            isAntiAlias = true
                        }
                        // Recenter Button
                        drawScope.drawContext.canvas.nativeCanvas.drawRoundRect(
                            -180f, 90f, -10f, 140f, 12f, 12f, btnPaint
                        )
                        val btnText = textMeasurer.measure(
                            text = "RECENTER",
                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        )
                        drawText(
                            textLayoutResult = btnText,
                            topLeft = Offset(-95f - btnText.size.width / 2f, 105f)
                        )

                        // Close Button
                        val closePaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#EF4444") // Red
                            isAntiAlias = true
                        }
                        drawScope.drawContext.canvas.nativeCanvas.drawRoundRect(
                            10f, 90f, 180f, 140f, 12f, 12f, closePaint
                        )
                        val closeText = textMeasurer.measure(
                            text = "CLOSE",
                            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        )
                        drawText(
                            textLayoutResult = closeText,
                            topLeft = Offset(95f - closeText.size.width / 2f, 105f)
                        )"""

code = re.sub(
    r'// Buttons \(Fake UI for now\).*?topLeft = Offset\(-btnText.size.width / 2f, 115f\)\n                        \)',
    replacement,
    code,
    flags=re.DOTALL
)

with open("app/src/main/java/com/example/vr/ui/VRRenderer.kt", "w") as f:
    f.write(code)
