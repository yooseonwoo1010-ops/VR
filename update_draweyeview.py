import re

with open("app/src/main/java/com/example/vr/ui/VRRenderer.kt", "r") as f:
    code = f.read()

# Add parameters to drawEyeView
code = re.sub(
    r'vrBoxWindow: VRBoxWindowState,',
    'vrBoxWindow: VRBoxWindowState,\n        virtualWindow: VirtualWindow,\n        textMeasurer: TextMeasurer,',
    code
)

# Inside drawEyeView, call drawVirtualWindow
if "drawVirtualWindow(" not in code.split("fun drawEyeView")[1]:
    code = re.sub(
        r'if \(vrBoxWindow.isVisible\) \{\s*drawVRBoxWindow\(.*?\)\s*\}',
        'if (vrBoxWindow.isVisible) {\n            drawVRBoxWindow(drawScope, cameraPos, viewMatrix, width, height, fov, vrBoxWindow)\n        }\n        \n        drawVirtualWindow(drawScope, cameraPos, viewMatrix, width, height, fov, virtualWindow, textMeasurer)',
        code,
        flags=re.DOTALL
    )

with open("app/src/main/java/com/example/vr/ui/VRRenderer.kt", "w") as f:
    f.write(code)

