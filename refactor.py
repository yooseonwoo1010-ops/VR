import re
import os

with open("app/src/main/java/com/example/vr/ui/VRRenderer.kt", "r") as f:
    code = f.read()

# Replace pitch: Float, yaw: Float, roll: Float with viewMatrix: FloatArray
code = re.sub(r'pitch:\s*Float,\s*yaw:\s*Float,\s*roll:\s*Float', 'viewMatrix: FloatArray', code)
code = re.sub(r'pitch,\s*yaw,\s*roll', 'viewMatrix', code)

# In drawEyeView, add val viewMatrix = headOrientation.viewMatrix and remove pitch, yaw, roll
code = code.replace("val pitch = headOrientation.pitch\n        val yaw = headOrientation.yaw\n        val roll = headOrientation.roll", "val viewMatrix = headOrientation.viewMatrix")

with open("app/src/main/java/com/example/vr/ui/VRRenderer.kt", "w") as f:
    f.write(code)

with open("app/src/main/java/com/example/vr/engine/VRMath.kt", "r") as f:
    code2 = f.read()

code2 = re.sub(r'pitch:\s*Float,\s*yaw:\s*Float,\s*roll:\s*Float', 'viewMatrix: FloatArray', code2)
code2 = re.sub(r'pitch,\s*yaw,\s*roll', 'viewMatrix', code2)

with open("app/src/main/java/com/example/vr/engine/VRMath.kt", "w") as f:
    f.write(code2)

