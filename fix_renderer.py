import re

with open("app/src/main/java/com/example/vr/ui/VRRenderer.kt", "r") as f:
    code = f.read()

# Fix named parameters
code = re.sub(r'pitch\s*=\s*pitch,\s*yaw\s*=\s*yaw,\s*roll\s*=\s*roll', 'viewMatrix = viewMatrix', code)

# Fix any multiline calls like:
# pitch,
# yaw,
# roll,
code = re.sub(r'pitch,\s*yaw,\s*roll', 'viewMatrix', code)

with open("app/src/main/java/com/example/vr/ui/VRRenderer.kt", "w") as f:
    f.write(code)

