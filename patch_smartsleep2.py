import re

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "r") as f:
    content = f.read()

# Replace CustomReminder related code in SmartSleepActivity to use WakeupAlarm

content = content.replace("val reminders = loader.loadCustomReminders()", "val reminders = loader.loadWakeupAlarms()")
content = content.replace("loader.saveCustomReminders(updatedList)", "loader.saveWakeupAlarms(updatedList as MutableList)")
content = content.replace("loader.saveCustomReminders(reminders)", "loader.saveWakeupAlarms(reminders)")
content = content.replace("com.neubofy.reality.utils.AlarmScheduler.scheduleNextAlarm(this)", "com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleNextAlarm(this)")
content = content.replace("com.neubofy.reality.data.CustomReminder(", "com.neubofy.reality.data.model.WakeupAlarm(")
content = content.replace("offsetMins = 0, retryIntervalMins = 3, snoozeEnabled = true, snoozeIntervalMins = 3, autoSnoozeEnabled = true, redirectUrl = \"reality://wakeup_alarm\", urlSource = 2", "snoozeIntervalMins = 3, maxAttempts = 5")


with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "w") as f:
    f.write(content)
