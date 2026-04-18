with open("app/src/main/java/com/neubofy/reality/utils/WakeupAlarmScheduler.kt", "r") as f:
    content = f.read()

content = content.replace("""            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val acInfo = AlarmManager.AlarmClockInfo(nextAlarmTime, pendingIntent)
                alarmManager.setAlarmClock(acInfo, pendingIntent)
                TerminalLogger.log("WAKEUP ALARM: Scheduled next exact alarm")
            } else {
                val acInfo = AlarmManager.AlarmClockInfo(nextAlarmTime, pendingIntent)
                alarmManager.setAlarmClock(acInfo, pendingIntent)
                TerminalLogger.log("WAKEUP ALARM: Scheduled next exact alarm")
            }""", """            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val acInfo = AlarmManager.AlarmClockInfo(nextAlarmTime, pendingIntent)
                alarmManager.setAlarmClock(acInfo, pendingIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextAlarmTime, pendingIntent)
            }
            TerminalLogger.log("WAKEUP ALARM: Scheduled next alarm")""")

content = content.replace("""        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val acInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
            alarmManager.setAlarmClock(acInfo, pendingIntent)
                TerminalLogger.log("WAKEUP ALARM: Scheduled next exact alarm")
        } else {
            val acInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
                alarmManager.setAlarmClock(acInfo, pendingIntent)
        }""", """        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val acInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
            alarmManager.setAlarmClock(acInfo, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
        TerminalLogger.log("WAKEUP ALARM: Scheduled snooze alarm")""")

with open("app/src/main/java/com/neubofy/reality/utils/WakeupAlarmScheduler.kt", "w") as f:
    f.write(content)
