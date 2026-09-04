import re

with open("app/src/main/java/com/example/vr/ui/VRMainScreen.kt", "r") as f:
    code = f.read()

# Replace the messy block with a clean handTracker instantiation
code = re.sub(
    r'val handTracker = remember \{\s*try \{\s*MediaPipeHandTracker\(context\) \{ result ->\s*val hands = HandResultMapper\.mapToTrackedHands\(result\)\s*if \(hands\.first\.isTracked\) rightHand = hands\.first\s*if \(hands\.second\.isTracked\) leftHand = hands\.second\s*\}\s*\} catch \(e: Throwable\) \{\s*e\.printStackTrace\(\)\s*null\s*\}\s*\}\s*MediaPipeHandTracker\(context\) \{ result ->\s*val hands = HandResultMapper\.mapToTrackedHands\(result\)\s*if \(hands\.first\.isTracked\) rightHand = hands\.first\s*if \(hands\.second\.isTracked\) leftHand = hands\.second\s*\}\s*\}',
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

