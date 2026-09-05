import re

with open("app/src/main/java/com/example/vr/ui/VRRenderer.kt", "r") as f:
    code = f.read()

# Make sure TextMeasurer is imported
if "import androidx.compose.ui.text.TextMeasurer" not in code:
    code = code.replace(
        "import androidx.compose.ui.graphics.Color",
        "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.text.TextMeasurer\nimport androidx.compose.ui.text.drawText\nimport androidx.compose.ui.text.TextStyle\nimport androidx.compose.ui.text.font.FontWeight\nimport androidx.compose.ui.unit.sp\nimport androidx.compose.ui.text.style.TextOverflow\nimport com.example.vr.model.VirtualWindow\nimport androidx.compose.ui.graphics.drawscope.clipPath"
    )

with open("app/src/main/java/com/example/vr/ui/VRRenderer.kt", "w") as f:
    f.write(code)
