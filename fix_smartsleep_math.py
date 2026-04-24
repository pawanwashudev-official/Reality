import re

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "r") as f:
    content = f.read()

# Fix syntax around intent access
content = content.replace("val action = intent?.getStringExtra(\"action\")", "val action = intent.getStringExtra(\"action\")")
content = content.replace("val alarmId = intent?.getStringExtra(\"id\") ?: activeAlarmId", "val alarmId = intent.getStringExtra(\"id\") ?: activeAlarmId")

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "w") as f:
    f.write(content)
