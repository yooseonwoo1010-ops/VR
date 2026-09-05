import re

def clean_view(filename):
    with open(filename, "r") as f:
        code = f.read()
    
    # Remove all "val textMeasurer = rememberTextMeasurer()"
    code = code.replace("val textMeasurer = rememberTextMeasurer()\n", "")
    code = code.replace("val textMeasurer = rememberTextMeasurer()\n    val textMeasurer = rememberTextMeasurer()\n", "")
    
    # Add exactly one at the start of the Composable body
    code = code.replace("val ipdMeters = (ipdMm / 1000f) * 0.5f", "val textMeasurer = rememberTextMeasurer()\n    val ipdMeters = (ipdMm / 1000f) * 0.5f")
    code = code.replace("val centerCameraPos = Vector3(0f, 0f, 0f)", "val textMeasurer = rememberTextMeasurer()\n    val centerCameraPos = Vector3(0f, 0f, 0f)")
    
    with open(filename, "w") as f:
        f.write(code)

clean_view("app/src/main/java/com/example/vr/ui/VRStereoscopicView.kt")
clean_view("app/src/main/java/com/example/vr/ui/VRFlatView.kt")

