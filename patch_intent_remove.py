import re

with open("app/src/main/java/com/neubofy/reality/data/nightly/NightlyPhasePlanning.kt", "r") as f:
    content = f.read()

pattern = r"""            val alarmIntent = android.content.Intent\(context, com.neubofy.reality.receivers.ReminderReceiver::class.java\).apply \{
.*?alarmManager.setExact\(android.app.AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent\)
            \}"""

content = re.sub(pattern, "", content, flags=re.DOTALL)

with open("app/src/main/java/com/neubofy/reality/data/nightly/NightlyPhasePlanning.kt", "w") as f:
    f.write(content)
