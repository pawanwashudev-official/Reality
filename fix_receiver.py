with open("app/src/main/java/com/neubofy/reality/receivers/WakeupAlarmReceiver.kt", "r") as f:
    content = f.read()

content = content.replace("val isSnooze = intent.getBooleanExtra(\"isSnooze\", false)", """val isSnooze = intent.getBooleanExtra("isSnooze", false)
        val ringtoneUri = intent.getStringExtra("ringtoneUri")
        val vibrationEnabled = intent.getBooleanExtra("vibrationEnabled", true)""")

content = content.replace("putExtra(\"snoozeInterval\", snoozeInterval)", """putExtra("snoozeInterval", snoozeInterval)
            putExtra("ringtoneUri", ringtoneUri)
            putExtra("vibrationEnabled", vibrationEnabled)""")

with open("app/src/main/java/com/neubofy/reality/receivers/WakeupAlarmReceiver.kt", "w") as f:
    f.write(content)
