import re

with open("app/src/main/java/com/example/vr/engine/VREnvironmentEngine.kt", "r") as f:
    code = f.read()

replacement = """    fun recenterVRWindow(headOrientation: HeadOrientation) {
        val forwardDir = VRMath.getForwardVector(headOrientation.pitch * 0.5f, headOrientation.yaw, 0f)
        val newAnchor = forwardDir * 2.0f
        _vrBoxWindow.value = _vrBoxWindow.value.copy(anchorPos = newAnchor)
        
        val actualForward = VRMath.getForwardVector(headOrientation.pitch, headOrientation.yaw, headOrientation.roll)
        virtualWindowManager.placeVirtualWindowOnce(com.example.vr.model.Vector3(0f, 0f, 0f), actualForward)
        
        spawnBurstParticles(newAnchor, 0xFF60A5FA, 20)
    }"""

code = re.sub(
    r'fun recenterVRWindow\(headOrientation: HeadOrientation\) \{.*?\n    \}',
    replacement,
    code,
    flags=re.DOTALL
)

with open("app/src/main/java/com/example/vr/engine/VREnvironmentEngine.kt", "w") as f:
    f.write(code)
