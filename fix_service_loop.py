with open("app/src/main/java/com/neubofy/reality/services/WakeupAlarmService.kt", "r") as f:
    content = f.read()

content = content.replace("vibrator?.vibrate(effect)", "vibrator?.vibrate(effect, 0)")
content = content.replace("vibrator?.vibrate(longArrayOf(0, 500, 500), 0)", "vibrator?.vibrate(longArrayOf(0, 500, 500), 0)")

with open("app/src/main/java/com/neubofy/reality/services/WakeupAlarmService.kt", "w") as f:
    f.write(content)
