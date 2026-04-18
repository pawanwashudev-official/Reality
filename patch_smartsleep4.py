with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "r") as f:
    content = f.read()

new_functions = """
    private fun checkAndShowMathDismissDialog() {
        val action = intent.getStringExtra("action")
        if (action == "wakeup_alarm") {
            val alarmId = intent.getStringExtra("id")
            showMathDismissDialog(alarmId)
        }
    }

    private fun showMathDismissDialog(alarmId: String?) {
        val dialogView = layoutInflater.inflate(com.neubofy.reality.R.layout.dialog_math_alarm_dismiss, null)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setCancelable(false)
        dialog.setContentView(dialogView)

        val tvMathProblem = dialogView.findViewById<android.widget.TextView>(com.neubofy.reality.R.id.tvMathProblem)
        val etMathAnswer = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.neubofy.reality.R.id.etMathAnswer)
        val tvError = dialogView.findViewById<android.widget.TextView>(com.neubofy.reality.R.id.tvError)
        val btnSnooze = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.neubofy.reality.R.id.btnSnooze)
        val btnDismiss = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.neubofy.reality.R.id.btnDismiss)

        // Generate math problem
        var a = (11..99).random() / 10.0
        var b = (11..99).random() / 10.0

        // Ensure max 2 or 3 decimals total in product to not be too complex
        if ((0..1).random() == 1) {
            a = (11..99).random() / 100.0
        }

        val expectedAnswer = Math.round(a * b * 1000.0) / 1000.0
        tvMathProblem.text = "${a} × ${b} = ?"

        btnSnooze.setOnClickListener {
            // Auto snooze based on loader configuration
            val loader = com.neubofy.reality.utils.SavedPreferencesLoader(this)
            val alarm = loader.loadWakeupAlarms().find { it.id == alarmId }
            val interval = alarm?.snoozeIntervalMins ?: 3
            val maxAttempts = alarm?.maxAttempts ?: 5

            com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleSnooze(this, alarmId ?: "nightly_wakeup", alarm?.title ?: "Wake Up", maxAttempts, interval)

            // Stop Service
            val stopIntent = android.content.Intent(this, com.neubofy.reality.services.WakeupAlarmService::class.java).apply {
                this.action = "STOP"
            }
            startService(stopIntent)

            dialog.dismiss()
            finish()
        }

        btnDismiss.setOnClickListener {
            val userAnswerStr = etMathAnswer.text.toString()
            if (userAnswerStr.isEmpty()) {
                tvError.visibility = android.view.View.VISIBLE
                tvError.text = "Please enter an answer"
                return@setOnClickListener
            }

            try {
                val userAnswer = userAnswerStr.toDouble()
                if (Math.abs(userAnswer - expectedAnswer) < 0.001) {
                    // Correct!
                    tvError.visibility = android.view.View.GONE

                    // Stop Service
                    val stopIntent = android.content.Intent(this, com.neubofy.reality.services.WakeupAlarmService::class.java).apply {
                        this.action = "STOP"
                    }
                    startService(stopIntent)

                    // Calculate start time based on current end time and user confirmation
                    // (Assuming inference algorithm is robust or standard sleep tracking happens via sleep verification)
                    // Launch SleepInferenceHelper to auto log
                    lifecycleScope.launch {
                        com.neubofy.reality.utils.SleepInferenceHelper.autoConfirmSleep(this@SmartSleepActivity)
                        loadSessions() // Refresh UI
                    }

                    dialog.dismiss()
                } else {
                    tvError.visibility = android.view.View.VISIBLE
                    tvError.text = "Incorrect answer, try again."
                }
            } catch (e: Exception) {
                tvError.visibility = android.view.View.VISIBLE
                tvError.text = "Invalid number format."
            }
        }

        dialog.show()
    }
"""

content = content.replace("            checkHealthPermissionsFlow()\n        }", "            checkHealthPermissionsFlow()\n        }\n\n        checkAndShowMathDismissDialog()")

content = content.replace("    private fun updateUiState() {",
                          new_functions + "\n    private fun updateUiState() {")

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "w") as f:
    f.write(content)
