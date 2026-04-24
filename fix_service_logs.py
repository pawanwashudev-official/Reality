with open("app/src/main/java/com/neubofy/reality/services/WakeupAlarmService.kt", "r") as f:
    content = f.read()

content = content.replace("alarmId = intent?.getStringExtra(\"id\")", """com.neubofy.reality.utils.TerminalLogger.log("WAKEUP ALARM: Service Started!")
        alarmId = intent?.getStringExtra("id")""")

with open("app/src/main/java/com/neubofy/reality/services/WakeupAlarmService.kt", "w") as f:
    f.write(content)
