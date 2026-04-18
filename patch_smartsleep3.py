with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "r") as f:
    content = f.read()

new_functions = """
    private fun updateAlarmUI() {
        val loader = com.neubofy.reality.utils.SavedPreferencesLoader(this)
        val alarms = loader.loadWakeupAlarms()
        val wakeupAlarm = alarms.find { it.id == "nightly_wakeup" }

        if (wakeupAlarm != null && !wakeupAlarm.isDeleted) {
            binding.llAlarmInfo.visibility = android.view.View.VISIBLE
            binding.tvAlarmTime.text = String.format("%02d:%02d", wakeupAlarm.hour, wakeupAlarm.minute)
            binding.tvAlarmName.text = wakeupAlarm.title
            binding.swAlarmEnabled.isChecked = wakeupAlarm.isEnabled

            binding.swAlarmEnabled.setOnCheckedChangeListener { _, isChecked ->
                val updatedList = loader.loadWakeupAlarms().map {
                    if (it.id == "nightly_wakeup") it.copy(isEnabled = isChecked) else it
                }.toMutableList()
                loader.saveWakeupAlarms(updatedList)
                com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleNextAlarm(this)
            }

            // Allow editing
            binding.llAlarmInfo.setOnClickListener { showAlarmSetupDialog() }

            // Allow deletion via long click
            binding.llAlarmInfo.setOnLongClickListener {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Delete Alarm")
                    .setMessage("Are you sure you want to delete this wake up alarm?")
                    .setPositiveButton("Delete") { _, _ ->
                        val updatedList = loader.loadWakeupAlarms().map {
                            if (it.id == "nightly_wakeup") it.copy(isDeleted = true) else it
                        }.toMutableList()
                        loader.saveWakeupAlarms(updatedList)
                        com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleNextAlarm(this)
                        updateAlarmUI()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
        } else {
            binding.llAlarmInfo.visibility = android.view.View.GONE
        }
    }

    private fun showAlarmSetupDialog() {
        val dialogView = layoutInflater.inflate(com.neubofy.reality.R.layout.dialog_wakeup_alarm_setup, null)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(dialogView)

        val tvWakeTime = dialogView.findViewById<android.widget.TextView>(com.neubofy.reality.R.id.tvWakeTime)
        val etAlarmName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.neubofy.reality.R.id.etAlarmName)
        val swVibration = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(com.neubofy.reality.R.id.swVibration)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.neubofy.reality.R.id.btnCancel)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.neubofy.reality.R.id.btnSave)

        val loader = com.neubofy.reality.utils.SavedPreferencesLoader(this)
        val existingAlarm = loader.loadWakeupAlarms().find { it.id == "nightly_wakeup" }

        var selectedHour = existingAlarm?.hour ?: 7
        var selectedMinute = existingAlarm?.minute ?: 0

        tvWakeTime.text = String.format("%02d:%02d", selectedHour, selectedMinute)
        etAlarmName.setText(existingAlarm?.title ?: "Wake Up")
        swVibration.isChecked = existingAlarm?.vibrationEnabled ?: true

        tvWakeTime.setOnClickListener {
            val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
                .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
                .setHour(selectedHour)
                .setMinute(selectedMinute)
                .setTitleText("Select Wake Up Time")
                .build()

            picker.addOnPositiveButtonClickListener {
                selectedHour = picker.hour
                selectedMinute = picker.minute
                tvWakeTime.text = String.format("%02d:%02d", selectedHour, selectedMinute)
            }
            picker.show(supportFragmentManager, "alarm_time_picker")
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
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
                vibrationEnabled = swVibration.isChecked,
                snoozeIntervalMins = 3,
                maxAttempts = 5,
                isDeleted = false
            ))
            loader.saveWakeupAlarms(alarms)
            com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleNextAlarm(this)
            updateAlarmUI()
            dialog.dismiss()
        }

        dialog.show()
    }
"""

if "private fun updateAlarmUI()" not in content:
    content = content.replace("    private fun updateUiState() {",
                              new_functions + "\n    private fun updateUiState() {")
    content = content.replace("binding.btnManualAdd.setOnClickListener { showAddSleepPicker() }",
                              "binding.btnManualAdd.setOnClickListener { showAddSleepPicker() }\n        binding.btnSetupAlarm.setOnClickListener { showAlarmSetupDialog() }")
    content = content.replace("            updateUiState()\n        }",
                              "            updateUiState()\n        }\n        updateAlarmUI()")


with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "w") as f:
    f.write(content)
