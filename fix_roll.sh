sed -i 's/val x3 = x2 \* cr - y2 \* sr/val x3 = x2 \* cr + y2 \* sr/g' app/src/main/java/com/example/vr/engine/VRMath.kt
sed -i 's/val y3 = x2 \* sr + y2 \* cr/val y3 = -x2 \* sr + y2 \* cr/g' app/src/main/java/com/example/vr/engine/VRMath.kt
