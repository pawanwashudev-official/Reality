with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "r") as f:
    content = f.read()

# Change alarmIdToSave generation to use timestamp
content = content.replace("val alarmIdToSave = editAlarmId ?: java.util.UUID.randomUUID().toString()", "val alarmIdToSave = editAlarmId ?: System.currentTimeMillis().toString()")

# Add dismissal logic for deleting one-off alarms
deletion_logic = """
                    // Delete alarm if it is a non-repeating alarm
                    val loader = com.neubofy.reality.utils.SavedPreferencesLoader(this@SmartSleepActivity)
                    val alarms = loader.loadWakeupAlarms()
                    val idx = alarms.indexOfFirst { it.id == alarmId }
                    if (idx != -1 && alarms[idx].repeatDays.isEmpty()) {
                        alarms[idx] = alarms[idx].copy(isDeleted = true)
                        loader.saveWakeupAlarms(alarms)
                    }
                    com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleNextAlarm(this@SmartSleepActivity)
"""

content = content.replace("""                    // Stop Service
                    val stopIntent = android.content.Intent(this, com.neubofy.reality.services.WakeupAlarmService::class.java).apply {
                        this.action = "STOP"
                    }
                    startService(stopIntent)""", """                    // Stop Service
                    val stopIntent = android.content.Intent(this@SmartSleepActivity, com.neubofy.reality.services.WakeupAlarmService::class.java).apply {
                        this.action = "STOP"
                    }
                    startService(stopIntent)""" + deletion_logic)

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "w") as f:
    f.write(content)
