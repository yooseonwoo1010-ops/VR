import re

with open("app/src/main/java/com/example/vr/ui/VRRenderer.kt", "r") as f:
    code = f.read()

imports = """
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import com.example.vr.model.VirtualWindow
"""
code = code.replace("import kotlin.math.*", "import kotlin.math.*\n" + imports)

with open("app/src/main/java/com/example/vr/ui/VRRenderer.kt", "w") as f:
    f.write(code)

