import re

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "r") as f:
    content = f.read()

# Add view lookups
new_views = """        val swVibration = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(com.neubofy.reality.R.id.swVibration)
        val etSnoozeInterval = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.neubofy.reality.R.id.etSnoozeInterval)
        val etMaxAttempts = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.neubofy.reality.R.id.etMaxAttempts)"""

content = content.replace("val swVibration = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(com.neubofy.reality.R.id.swVibration)", new_views)

# Add existing values
new_existing = """        etAlarmName.setText(existingAlarm?.title ?: "Wake Up")
        swVibration.isChecked = existingAlarm?.vibrationEnabled ?: true
        etSnoozeInterval.setText((existingAlarm?.snoozeIntervalMins ?: 3).toString())
        etMaxAttempts.setText((existingAlarm?.maxAttempts ?: 5).toString())"""

content = content.replace("""        etAlarmName.setText(existingAlarm?.title ?: "Wake Up")
        swVibration.isChecked = existingAlarm?.vibrationEnabled ?: true""", new_existing)

# Modify Save logic
new_save = """        btnSave.setOnClickListener {
            val title = etAlarmName.text.toString().ifEmpty { "Wake Up" }
            val snoozeStr = etSnoozeInterval.text.toString()
            val maxStr = etMaxAttempts.text.toString()

            val snoozeInterval = if (snoozeStr.isNotEmpty()) snoozeStr.toInt() else 3
            val maxAttempts = if (maxStr.isNotEmpty()) maxStr.toInt() else 5

            val alarms = loader.loadWakeupAlarms()
            alarms.removeAll { it.id == "nightly_wakeup" }
            alarms.add(com.neubofy.reality.data.model.WakeupAlarm(
                id = "nightly_wakeup",
                title = title,
                hour = selectedHour,
                minute = selectedMinute,
                isEnabled = true,
                repeatDays = emptyList(),
                ringtoneUri = selectedRingtoneUri,
                vibrationEnabled = swVibration.isChecked,
                snoozeIntervalMins = snoozeInterval,
                maxAttempts = maxAttempts,
                isDeleted = false
            ))"""

content = content.replace("""        btnSave.setOnClickListener {
            val title = etAlarmName.text.toString().ifEmpty { "Wake Up" }
            val alarms = loader.loadWakeupAlarms()
            alarms.removeAll { it.id == "nightly_wakeup" }
            alarms.add(com.neubofy.reality.data.model.WakeupAlarm(
                id = "nightly_wakeup",
                title = title,
                hour = selectedHour,
                minute = selectedMinute,
                isEnabled = true,
                repeatDays = emptyList(),
                ringtoneUri = selectedRingtoneUri,
                vibrationEnabled = swVibration.isChecked,
                snoozeIntervalMins = 3,
                maxAttempts = 5,
                isDeleted = false
            ))""", new_save)

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "w") as f:
    f.write(content)
