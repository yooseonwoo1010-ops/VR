import re

with open("app/src/main/java/com/example/vr/engine/VREnvironmentEngine.kt", "r") as f:
    code = f.read()

# Add virtualWindowManager
if "val virtualWindowManager = VirtualWindowManager()" not in code:
    code = code.replace(
        "class VREnvironmentEngine(private val context: Context) {",
        "class VREnvironmentEngine(private val context: Context) {\n    val virtualWindowManager = VirtualWindowManager()\n"
    )

with open("app/src/main/java/com/example/vr/engine/VREnvironmentEngine.kt", "w") as f:
    f.write(code)
