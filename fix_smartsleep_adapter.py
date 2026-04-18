with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "r") as f:
    content = f.read()

adapter_code = """
    private fun setupAlarmRecycler() {
        val loader = com.neubofy.reality.utils.SavedPreferencesLoader(this)
        val alarms = loader.loadWakeupAlarms().filter { !it.isDeleted }
        val rvWakeupAlarms = findViewById<androidx.recyclerview.widget.RecyclerView>(com.neubofy.reality.R.id.rvWakeupAlarms)

        rvWakeupAlarms.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        rvWakeupAlarms.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val view = layoutInflater.inflate(com.neubofy.reality.R.layout.item_wakeup_alarm, parent, false)
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {}
            }

            override fun getItemCount(): Int = alarms.size

            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val alarm = alarms[position]
                val view = holder.itemView
                val tvAlarmTime = view.findViewById<android.widget.TextView>(com.neubofy.reality.R.id.tvAlarmTime)
                val tvAlarmName = view.findViewById<android.widget.TextView>(com.neubofy.reality.R.id.tvAlarmName)
                val swAlarmEnabled = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(com.neubofy.reality.R.id.swAlarmEnabled)
                val btnSettings = view.findViewById<android.widget.ImageButton>(com.neubofy.reality.R.id.btnSettings)

                tvAlarmTime.text = String.format("%02d:%02d", alarm.hour, alarm.minute)
                tvAlarmName.text = alarm.title
                swAlarmEnabled.isChecked = alarm.isEnabled

                swAlarmEnabled.setOnCheckedChangeListener { _, isChecked ->
                    val allAlarms = loader.loadWakeupAlarms()
                    val idx = allAlarms.indexOfFirst { it.id == alarm.id }
                    if (idx != -1) {
                        allAlarms[idx] = allAlarms[idx].copy(isEnabled = isChecked)
                        loader.saveWakeupAlarms(allAlarms)
                        com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleNextAlarm(this@SmartSleepActivity)
                    }
                }

                btnSettings.setOnClickListener { showAlarmSetupDialog(alarm.id) }

                view.setOnLongClickListener {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this@SmartSleepActivity)
                        .setTitle("Delete Alarm")
                        .setMessage("Move this alarm to the Recycle Bin?")
                        .setPositiveButton("Delete") { _, _ ->
                            val allAlarms = loader.loadWakeupAlarms()
                            val idx = allAlarms.indexOfFirst { it.id == alarm.id }
                            if (idx != -1) {
                                allAlarms[idx] = allAlarms[idx].copy(isDeleted = true)
                                loader.saveWakeupAlarms(allAlarms)
                                com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleNextAlarm(this@SmartSleepActivity)
                                updateAlarmUI()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }
            }
        }
    }

    private fun showRecycleBinDialog() {
        val loader = com.neubofy.reality.utils.SavedPreferencesLoader(this)
        val deletedAlarms = loader.loadWakeupAlarms().filter { it.isDeleted }

        if (deletedAlarms.isEmpty()) {
            Toast.makeText(this, "Recycle Bin is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val rv = androidx.recyclerview.widget.RecyclerView(this)
        rv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Recycle Bin")
            .setView(rv)
            .setPositiveButton("Close", null)
            .create()

        rv.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val view = layoutInflater.inflate(com.neubofy.reality.R.layout.item_recycled_alarm, parent, false)
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {}
            }

            override fun getItemCount(): Int = deletedAlarms.size

            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val alarm = deletedAlarms[position]
                val view = holder.itemView
                val tvAlarmTime = view.findViewById<android.widget.TextView>(com.neubofy.reality.R.id.tvAlarmTime)
                val tvAlarmName = view.findViewById<android.widget.TextView>(com.neubofy.reality.R.id.tvAlarmName)
                val btnRestore = view.findViewById<android.widget.ImageButton>(com.neubofy.reality.R.id.btnRestore)
                val btnDeletePermanent = view.findViewById<android.widget.ImageButton>(com.neubofy.reality.R.id.btnDeletePermanent)

                tvAlarmTime.text = String.format("%02d:%02d", alarm.hour, alarm.minute)
                tvAlarmName.text = alarm.title

                btnRestore.setOnClickListener {
                    val allAlarms = loader.loadWakeupAlarms()
                    val idx = allAlarms.indexOfFirst { it.id == alarm.id }
                    if (idx != -1) {
                        allAlarms[idx] = allAlarms[idx].copy(isDeleted = false)
                        loader.saveWakeupAlarms(allAlarms)
                        com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleNextAlarm(this@SmartSleepActivity)
                        updateAlarmUI()
                        dialog.dismiss()
                        showRecycleBinDialog() // refresh
                    }
                }

                btnDeletePermanent.setOnClickListener {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this@SmartSleepActivity)
                        .setTitle("Delete Permanently")
                        .setMessage("This action cannot be undone.")
                        .setPositiveButton("Delete") { _, _ ->
                            val allAlarms = loader.loadWakeupAlarms()
                            allAlarms.removeAll { it.id == alarm.id }
                            loader.saveWakeupAlarms(allAlarms)
                            dialog.dismiss()
                            showRecycleBinDialog() // refresh
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }

        dialog.show()
    }
"""

if "private fun setupAlarmRecycler()" not in content:
    content = content.replace("    private fun updateAlarmUI() {", adapter_code + "\n    private fun updateAlarmUI() {")

# Update UI and setupAlarmDialog methods
old_update_ui = """    private fun updateAlarmUI() {
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
    }"""

new_update_ui = """    private fun updateAlarmUI() {
        val btnAddAlarm = findViewById<android.widget.ImageButton>(com.neubofy.reality.R.id.btnAddAlarm)
        val btnRecycleBin = findViewById<android.widget.ImageButton>(com.neubofy.reality.R.id.btnRecycleBin)

        btnAddAlarm?.setOnClickListener { showAlarmSetupDialog(null) }
        btnRecycleBin?.setOnClickListener { showRecycleBinDialog() }

        setupAlarmRecycler()
    }"""

content = content.replace(old_update_ui, new_update_ui)

# Update dialog showing parameters
content = content.replace("private fun showAlarmSetupDialog() {", "private fun showAlarmSetupDialog(editAlarmId: String?) {")

content = content.replace("val existingAlarm = loader.loadWakeupAlarms().find { it.id == \"nightly_wakeup\" }", "val existingAlarm = if (editAlarmId != null) loader.loadWakeupAlarms().find { it.id == editAlarmId } else null")

content = content.replace("""        btnSave.setOnClickListener {
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
            ))""", """        btnSave.setOnClickListener {
            val title = etAlarmName.text.toString().ifEmpty { "Wake Up" }
            val snoozeStr = etSnoozeInterval.text.toString()
            val maxStr = etMaxAttempts.text.toString()

            val snoozeInterval = if (snoozeStr.isNotEmpty()) snoozeStr.toInt() else 3
            val maxAttempts = if (maxStr.isNotEmpty()) maxStr.toInt() else 5

            val alarms = loader.loadWakeupAlarms()
            val alarmIdToSave = editAlarmId ?: java.util.UUID.randomUUID().toString()
            alarms.removeAll { it.id == alarmIdToSave }
            alarms.add(com.neubofy.reality.data.model.WakeupAlarm(
                id = alarmIdToSave,
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
            ))""")

# Fix button setup in setupUI (which we removed in layout earlier, so we just remove the old references)
content = content.replace("        binding.btnSetupAlarm.setOnClickListener { showAlarmSetupDialog() }\n", "")
content = content.replace("        binding.btnSetupAlarm.setOnClickListener { showAlarmSetupDialog(null) }\n", "")

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "w") as f:
    f.write(content)
