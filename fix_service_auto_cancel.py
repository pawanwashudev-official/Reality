with open("app/src/main/java/com/neubofy/reality/services/WakeupAlarmService.kt", "r") as f:
    content = f.read()

# Change ongoing to true, autoCancel to false, category alarm
content = content.replace(".setOngoing(true)", ".setOngoing(true)\n            .setAutoCancel(false)")

# Change countdown timer to 45 seconds
content = content.replace("autoSnoozeTimer = object : CountDownTimer(60_000L, 1000) {", "autoSnoozeTimer = object : CountDownTimer(45_000L, 1000) {")

with open("app/src/main/java/com/neubofy/reality/services/WakeupAlarmService.kt", "w") as f:
    f.write(content)
