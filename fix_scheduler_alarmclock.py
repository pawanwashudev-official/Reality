import re

with open("app/src/main/java/com/neubofy/reality/utils/WakeupAlarmScheduler.kt", "r") as f:
    content = f.read()

# Remove the canScheduleExactAlarms check completely to match AlarmScheduler
pattern_to_remove = r"""            val canSchedule = if \(Build\.VERSION\.SDK_INT >= Build\.VERSION_CODES\.S\) \{
                alarmManager\.canScheduleExactAlarms\(\)
            \} else \{
                true
            \}

            if \(!canSchedule\) \{
                // Without exact alarm permission, fallback to approximate or ignore. In Reality app, we should have requested this permission.
                // Assuming it's already requested, or we use setAndAllowWhileIdle for safety\?
                // setExactAndAllowWhileIdle is what AlarmScheduler uses.
                // But setAlarmClock is generally allowed or needs less strict\? setAlarmClock does not require SCHEDULE_EXACT_ALARM in older versions but it does in newer.
                // Actually setAlarmClock is perfect for WakeUp alarms as it shows the alarm icon.
            \}
"""

content = re.sub(pattern_to_remove, "", content, flags=re.DOTALL)

# Revert setExactAndAllowWhileIdle back to setAlarmClock
content = content.replace("alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarmTime, pendingIntent)", """val acInfo = AlarmManager.AlarmClockInfo(nextAlarmTime, pendingIntent)
                alarmManager.setAlarmClock(acInfo, pendingIntent)""")

content = content.replace("alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)", """val acInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
                alarmManager.setAlarmClock(acInfo, pendingIntent)""")

with open("app/src/main/java/com/neubofy/reality/utils/WakeupAlarmScheduler.kt", "w") as f:
    f.write(content)
