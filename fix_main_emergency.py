with open("app/src/main/java/com/neubofy/reality/ui/activity/MainActivity.kt", "r") as f:
    content = f.read()

new_code = """        binding.btnEmergencyClick.setOnClickListener {
            scope.launch { showEmergencyDialog() }
        }

        binding.btnEmergencySettings.setOnClickListener {
            val loader = com.neubofy.reality.utils.SavedPreferencesLoader(this)
            val strictMode = loader.getStrictModeData()
            if (strictMode.isEnabled && strictMode.isEmergencyLocked) {
                Toast.makeText(this, "Emergency settings are locked by Strict Mode.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val emergencyData = loader.getEmergencyData()
            val numberPicker = android.widget.NumberPicker(this).apply {
                minValue = 1
                maxValue = 15
                value = emergencyData.maxUses
            }

            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Emergency Quota")
                .setMessage("Set maximum emergency breaks allowed per day:")
                .setView(numberPicker)
                .setPositiveButton("Save") { _, _ ->
                    val diff = numberPicker.value - emergencyData.maxUses
                    emergencyData.maxUses = numberPicker.value
                    emergencyData.usesRemaining = (emergencyData.usesRemaining + diff).coerceIn(0, emergencyData.maxUses)
                    loader.saveEmergencyData(emergencyData)
                    updateDashboardCards()
                    Toast.makeText(this, "Emergency quota updated.", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }"""

content = content.replace("""        binding.btnEmergencyClick.setOnClickListener {
            scope.launch { showEmergencyDialog() }
        }""", new_code)

with open("app/src/main/java/com/neubofy/reality/ui/activity/MainActivity.kt", "w") as f:
    f.write(content)
