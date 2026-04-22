import os
import glob

# Replace all hardcoded references to Constants.EMERGENCY_MAX_USES with emergencyData.maxUses

for filepath in glob.glob("app/src/main/java/com/neubofy/reality/**/*.kt", recursive=True):
    with open(filepath, "r") as f:
        content = f.read()

    if "EMERGENCY_MAX_USES" in content:
        print(f"Fixing {filepath}")
        content = content.replace("Constants.EMERGENCY_MAX_USES", "emergencyData.maxUses")
        content = content.replace("com.neubofy.reality.Constants.EMERGENCY_MAX_USES", "emergencyData.maxUses")
        with open(filepath, "w") as f:
            f.write(content)
