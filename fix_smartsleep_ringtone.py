with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "r") as f:
    content = f.read()

# Add Ringtone Launcher and handling to SmartSleepActivity
new_launcher = """
    private var selectedRingtoneUri: String? = null

    private val ringtonePickerLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri: android.net.Uri? = result.data?.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            selectedRingtoneUri = uri?.toString()
            Toast.makeText(this, "Ringtone Selected", Toast.LENGTH_SHORT).show()
        }
    }
"""

content = content.replace("    private val healthPermissionLauncher", new_launcher + "\n    private val healthPermissionLauncher")

# Fix button click and model
content = content.replace("var selectedMinute = existingAlarm?.minute ?: 0", """var selectedMinute = existingAlarm?.minute ?: 0
        selectedRingtoneUri = existingAlarm?.ringtoneUri""")

content = content.replace("""        btnCancel.setOnClickListener { dialog.dismiss() }""", """        val btnSelectRingtone = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.neubofy.reality.R.id.btnSelectRingtone)
        btnSelectRingtone.setOnClickListener {
            val intent = android.content.Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
            intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_ALARM)
            intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)

            if (selectedRingtoneUri != null) {
                intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, android.net.Uri.parse(selectedRingtoneUri))
            } else {
                intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM))
            }

            ringtonePickerLauncher.launch(intent)
        }

        btnCancel.setOnClickListener { dialog.dismiss() }""")

content = content.replace("""repeatDays = emptyList(),
                vibrationEnabled = swVibration.isChecked,
                snoozeIntervalMins = 3,""", """repeatDays = emptyList(),
                ringtoneUri = selectedRingtoneUri,
                vibrationEnabled = swVibration.isChecked,
                snoozeIntervalMins = 3,""")

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "w") as f:
    f.write(content)
