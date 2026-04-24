with open("app/src/main/java/com/neubofy/reality/utils/WakeupAlarmScheduler.kt", "r") as f:
    content = f.read()

# Let's see what WakeupAlarmScheduler prints out when scheduling:
new_schedule = """            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val acInfo = AlarmManager.AlarmClockInfo(nextAlarmTime, pendingIntent)
                alarmManager.setAlarmClock(acInfo, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextAlarmTime, pendingIntent)
            }
            val timeStr = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(nextAlarmTime))
            TerminalLogger.log("WAKEUP ALARM: Scheduled next alarm at $timeStr")"""

content = content.replace("""            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val acInfo = AlarmManager.AlarmClockInfo(nextAlarmTime, pendingIntent)
                alarmManager.setAlarmClock(acInfo, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextAlarmTime, pendingIntent)
            }
            TerminalLogger.log("WAKEUP ALARM: Scheduled next alarm")""", new_schedule)

with open("app/src/main/java/com/neubofy/reality/utils/WakeupAlarmScheduler.kt", "w") as f:
    f.write(content)
