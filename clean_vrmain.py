import re

with open("app/src/main/java/com/example/vr/ui/VRMainScreen.kt", "r") as f:
    code = f.read()

# Fix MediaPipe Try Catch (Compose compiler doesn't allow remember inside try block)
code = re.sub(
    r'var handTracker: MediaPipeHandTracker\? = null\s*try \{\s*handTracker = remember \{\s*MediaPipeHandTracker\(context\) \{ result ->\s*val hands = HandResultMapper\.mapToTrackedHands\(result\)\s*if \(hands\.first\.isTracked\) rightHand = hands\.first\s*if \(hands\.second\.isTracked\) leftHand = hands\.second\s*\}\s*\}\s*\} catch \(e: Throwable\) \{\s*e\.printStackTrace\(\)\s*\}',
    '''val handTracker = remember {
        try {
            MediaPipeHandTracker(context) { result ->
                val hands = HandResultMapper.mapToTrackedHands(result)
                if (hands.first.isTracked) rightHand = hands.first
                if (hands.second.isTracked) leftHand = hands.second
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }''',
    code
)

with open("app/src/main/java/com/example/vr/ui/VRMainScreen.kt", "w") as f:
    f.write(code)
