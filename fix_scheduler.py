with open("app/src/main/java/com/neubofy/reality/utils/WakeupAlarmScheduler.kt", "r") as f:
    content = f.read()

content = content.replace("var maxAttempts = 5\n        var snoozeInterval = 3", "var maxAttempts = 5\n        var snoozeInterval = 3\n        var ringtoneUri: String? = null\n        var vibrationEnabled = true")

content = content.replace("snoozeInterval = alarm.snoozeIntervalMins", "snoozeInterval = alarm.snoozeIntervalMins\n                ringtoneUri = alarm.ringtoneUri\n                vibrationEnabled = alarm.vibrationEnabled")

content = content.replace('putExtra("snoozeInterval", snoozeInterval)', 'putExtra("snoozeInterval", snoozeInterval)\n                putExtra("ringtoneUri", ringtoneUri)\n                putExtra("vibrationEnabled", vibrationEnabled)')

content = content.replace("fun scheduleSnooze(context: Context, id: String, title: String, maxAttempts: Int, snoozeIntervalMins: Int)", "fun scheduleSnooze(context: Context, id: String, title: String, maxAttempts: Int, snoozeIntervalMins: Int, ringtoneUri: String?, vibrationEnabled: Boolean)")

content = content.replace('putExtra("snoozeInterval", snoozeIntervalMins)\n            putExtra("isSnooze", true)', 'putExtra("snoozeInterval", snoozeIntervalMins)\n            putExtra("isSnooze", true)\n            putExtra("ringtoneUri", ringtoneUri)\n            putExtra("vibrationEnabled", vibrationEnabled)')

with open("app/src/main/java/com/neubofy/reality/utils/WakeupAlarmScheduler.kt", "w") as f:
    f.write(content)
