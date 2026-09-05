import re

with open("app/src/main/java/com/example/vr/ui/VRMainScreen.kt", "r") as f:
    code = f.read()

# Add virtualWindow state collection
if "val virtualWindow by vrEngine.virtualWindowManager.window.collectAsState()" not in code:
    code = re.sub(
        r'val vrBoxWindow by vrEngine.vrBoxWindow.collectAsState\(\)',
        'val vrBoxWindow by vrEngine.vrBoxWindow.collectAsState()\n    val virtualWindow by vrEngine.virtualWindowManager.window.collectAsState()',
        code
    )

# Add to VRFlatView
code = re.sub(
    r'vrBoxWindow = vrBoxWindow,',
    'vrBoxWindow = vrBoxWindow,\n                virtualWindow = virtualWindow,',
    code
)

with open("app/src/main/java/com/example/vr/ui/VRMainScreen.kt", "w") as f:
    f.write(code)

