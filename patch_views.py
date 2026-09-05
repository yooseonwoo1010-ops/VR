import re

def patch_view(filename):
    with open(filename, "r") as f:
        code = f.read()
    
    if "import androidx.compose.ui.text.rememberTextMeasurer" not in code:
        code = code.replace("import androidx.compose.ui.unit.dp", "import androidx.compose.ui.unit.dp\nimport androidx.compose.ui.text.rememberTextMeasurer\nimport com.example.vr.model.VirtualWindow")
    
    if "virtualWindow: VirtualWindow," not in code:
        code = re.sub(
            r'vrBoxWindow: VRBoxWindowState,',
            'vrBoxWindow: VRBoxWindowState,\n    virtualWindow: VirtualWindow,',
            code
        )
        
    if "val textMeasurer = rememberTextMeasurer()" not in code:
        code = re.sub(
            r'val ipdMeters = ',
            'val textMeasurer = rememberTextMeasurer()\n    val ipdMeters = ',
            code
        )
        code = re.sub(
            r'val isPassthrough = ',
            'val textMeasurer = rememberTextMeasurer()\n    val isPassthrough = ',
            code
        )
        
    code = re.sub(
        r'vrBoxWindow = vrBoxWindow,',
        'vrBoxWindow = vrBoxWindow,\n                        virtualWindow = virtualWindow,\n                        textMeasurer = textMeasurer,',
        code
    )
    
    with open(filename, "w") as f:
        f.write(code)

patch_view("app/src/main/java/com/example/vr/ui/VRStereoscopicView.kt")
patch_view("app/src/main/java/com/example/vr/ui/VRFlatView.kt")

