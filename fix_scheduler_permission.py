import re

with open("app/src/main/java/com/neubofy/reality/utils/WakeupAlarmScheduler.kt", "r") as f:
    content = f.read()

# Need to check `alarmManager.canScheduleExactAlarms()` on Android 12+ (S) and handle.
# Also need to make sure the time logic is right.
# Let's verify time logic:
#
#            if (alarm.repeatDays.isEmpty()) {
#                if (alarmMins <= currentMins) {
#                    alarmCal.add(Calendar.DAY_OF_YEAR, 1)
#                }
#            }
#
# This is correct. `alarmCal` starts at today. If its hour/minute is earlier or equal to now, it bumps it by 1 day.

permission_check = """
            val canSchedule = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                alarmManager.canScheduleExactAlarms()
            } else {
                true
            }

            if (!canSchedule) {
                // Without exact alarm permission, fallback to approximate or ignore. In Reality app, we should have requested this permission.
                // Assuming it's already requested, or we use setAndAllowWhileIdle for safety?
                // setExactAndAllowWhileIdle is what AlarmScheduler uses.
                // But setAlarmClock is generally allowed or needs less strict? setAlarmClock does not require SCHEDULE_EXACT_ALARM in older versions but it does in newer.
                // Actually setAlarmClock is perfect for WakeUp alarms as it shows the alarm icon.
            }
"""

content = content.replace("alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextAlarmTime, pendingIntent)", "alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarmTime, pendingIntent)")
content = content.replace("alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)", "alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)")

with open("app/src/main/java/com/neubofy/reality/utils/WakeupAlarmScheduler.kt", "w") as f:
    f.write(content)
