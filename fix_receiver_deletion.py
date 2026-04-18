with open("app/src/main/java/com/neubofy/reality/receivers/WakeupAlarmReceiver.kt", "r") as f:
    content = f.read()

deletion_logic = """
            val prefsLoader = com.neubofy.reality.utils.SavedPreferencesLoader(context)
            val alarms = prefsLoader.loadWakeupAlarms()
            val idx = alarms.indexOfFirst { it.id == id }
            if (idx != -1 && alarms[idx].repeatDays.isEmpty()) {
                alarms[idx] = alarms[idx].copy(isDeleted = true)
                prefsLoader.saveWakeupAlarms(alarms)
                com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleNextAlarm(context)
            }"""

content = content.replace("            // Ideally we also dismiss logic here", "            // Ideally we also dismiss logic here\n" + deletion_logic)

with open("app/src/main/java/com/neubofy/reality/receivers/WakeupAlarmReceiver.kt", "w") as f:
    f.write(content)
