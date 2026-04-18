with open("app/src/main/java/com/neubofy/reality/utils/WakeupAlarmScheduler.kt", "r") as f:
    content = f.read()

# Let's double check if we have the permissions.
# In WakeupAlarmScheduler, let's just make sure we print something so we know it ran.
content = content.replace("alarmManager.setAlarmClock(acInfo, pendingIntent)", """alarmManager.setAlarmClock(acInfo, pendingIntent)
                TerminalLogger.log("WAKEUP ALARM: Scheduled next for $nextAlarmTime")""")
content = content.replace("alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarmTime, pendingIntent)", """alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarmTime, pendingIntent)
                TerminalLogger.log("WAKEUP ALARM: Scheduled next for $nextAlarmTime")""")

with open("app/src/main/java/com/neubofy/reality/utils/WakeupAlarmScheduler.kt", "w") as f:
    f.write(content)
