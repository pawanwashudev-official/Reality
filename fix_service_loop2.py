with open("app/src/main/java/com/neubofy/reality/services/WakeupAlarmService.kt", "r") as f:
    content = f.read()

# Fix compilation error on vibrate(effect, 0)
# VibrationEffect doesn't have a signature vibrate(VibrationEffect, int).
# The repeat parameter is already passed in createWaveform(longArray, int repeat).
content = content.replace("vibrator?.vibrate(effect, 0)", "vibrator?.vibrate(effect)")

with open("app/src/main/java/com/neubofy/reality/services/WakeupAlarmService.kt", "w") as f:
    f.write(content)
