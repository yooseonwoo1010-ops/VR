import re

with open("app/src/main/java/com/example/vr/ui/VRMainScreen.kt", "r") as f:
    code = f.read()

# Add imports
imports = """
import com.example.vr.tracking.MediaPipeHandTracker
import com.example.vr.tracking.HandResultMapper
import com.example.vr.model.TrackedHand
"""
code = code.replace("import com.example.vr.tracking.HeadTracker", imports + "\nimport com.example.vr.tracking.HeadTracker")

# Change dummy hands to states
hands_state = """
    var rightHand by remember { mutableStateOf(TrackedHand()) }
    var leftHand by remember { mutableStateOf(TrackedHand(isLeft = true)) }

    val handTracker = remember {
        MediaPipeHandTracker(context) { result ->
            val hands = HandResultMapper.mapToTrackedHands(result)
            if (hands.first.isTracked) rightHand = hands.first
            if (hands.second.isTracked) leftHand = hands.second
        }
    }
"""
code = code.replace("val dummyRightHand = remember { com.example.vr.model.TrackedHand() }", hands_state)
code = code.replace("val dummyLeftHand = remember { com.example.vr.model.TrackedHand(isLeft = true) }", "")

# Change dummyRightHand to rightHand in updateWorld
code = code.replace("vrEngine.updateWorld(dt, dummyRightHand, dummyLeftHand, headOrientation)", "vrEngine.updateWorld(dt, rightHand, leftHand, headOrientation)")

# Change dummyRightHand in VRStereoscopicView and VRFlatView
code = code.replace("rightHand = dummyRightHand", "rightHand = rightHand")
code = code.replace("leftHand = dummyLeftHand", "leftHand = leftHand")

# Clean up hand tracker
dispose = """
            headTracker.stop()
            handTracker.close()
            vrEngine.cleanup()
"""
code = code.replace("headTracker.stop()\n            vrEngine.cleanup()", dispose)

# Update CameraPassthroughView call
camera_call = """
            CameraPassthroughView(
                useFrontCamera = false,
                onImageProxy = { imageProxy ->
                    handTracker.processImageProxy(imageProxy, false)
                },
                modifier = Modifier.fillMaxSize()
            )
"""
code = code.replace("""
            CameraPassthroughView(
                useFrontCamera = false,
                modifier = Modifier.fillMaxSize()
            )
""", camera_call)

with open("app/src/main/java/com/example/vr/ui/VRMainScreen.kt", "w") as f:
    f.write(code)

