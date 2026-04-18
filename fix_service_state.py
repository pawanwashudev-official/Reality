with open("app/src/main/java/com/neubofy/reality/services/WakeupAlarmService.kt", "r") as f:
    content = f.read()

content = content.replace("        private const val NOTIFICATION_ID = 9002", "        private const val NOTIFICATION_ID = 9002\n        var activeAlarmId: String? = null")

content = content.replace("        alarmId = intent?.getStringExtra(\"id\")", "        alarmId = intent?.getStringExtra(\"id\")\n        activeAlarmId = alarmId")

content = content.replace("        super.onDestroy()\n        stopAlarm()", "        super.onDestroy()\n        stopAlarm()\n        activeAlarmId = null")

content = content.replace("    private fun stopAlarm() {", "    private fun stopAlarm() {\n        activeAlarmId = null")

with open("app/src/main/java/com/neubofy/reality/services/WakeupAlarmService.kt", "w") as f:
    f.write(content)
