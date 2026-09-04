import re

with open("app/src/main/java/com/example/vr/ui/VRMainScreen.kt", "r") as f:
    code = f.read()

new_code = re.sub(
    r'// Start sensors\s*DisposableEffect\(Unit\) \{\s*headTracker\.start\(\)\s*if \(\!hasCameraPermission\) \{\s*cameraPermissionLauncher\.launch\(Manifest\.permission\.CAMERA\)\s*\}\s*onDispose \{',
    'LaunchedEffect(Unit) { if (!hasCameraPermission) { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) } }\n    // Start sensors\n    DisposableEffect(Unit) {\n        headTracker.start()\n        onDispose {',
    code
)

with open("app/src/main/java/com/example/vr/ui/VRMainScreen.kt", "w") as f:
    f.write(new_code)

