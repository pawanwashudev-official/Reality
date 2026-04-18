# Fix 1: SmartSleepActivity - scheduleSnooze parameters
with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "r") as f:
    content = f.read()

content = content.replace(
    "com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleSnooze(this, alarmId ?: \"nightly_wakeup\", alarm?.title ?: \"Wake Up\", maxAttempts, interval)",
    "com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleSnooze(this, alarmId ?: \"nightly_wakeup\", alarm?.title ?: \"Wake Up\", maxAttempts, interval, alarm?.ringtoneUri, alarm?.vibrationEnabled ?: true)"
)

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "w") as f:
    f.write(content)

# Fix 2: WakeupAlarmScheduler - Unresolved reference: nextAlarmTime at line 120
with open("app/src/main/java/com/neubofy/reality/utils/WakeupAlarmScheduler.kt", "r") as f:
    content = f.read()

content = content.replace("TerminalLogger.log(\"WAKEUP ALARM: Scheduled next for $nextAlarmTime\")", "TerminalLogger.log(\"WAKEUP ALARM: Scheduled next exact alarm\")")

# make sure it has TerminalLogger import
if "import com.neubofy.reality.utils.TerminalLogger" not in content:
    content = content.replace("import java.util.Calendar", "import java.util.Calendar\nimport com.neubofy.reality.utils.TerminalLogger")

with open("app/src/main/java/com/neubofy/reality/utils/WakeupAlarmScheduler.kt", "w") as f:
    f.write(content)
