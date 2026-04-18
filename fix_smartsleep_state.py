import re

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "r") as f:
    content = f.read()

# Handle intent properly if the activity is already running
new_intent = """
    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkAndShowMathDismissDialog()
    }
"""
content = content.replace("    override fun onDestroy() {", new_intent + "\n    override fun onDestroy() {")

# Update checkAndShowMathDismissDialog to also check activeAlarmId from service
new_check = """
    private fun checkAndShowMathDismissDialog() {
        if (isMathDialogShowing) return
        val action = intent?.getStringExtra("action")
        val activeAlarmId = com.neubofy.reality.services.WakeupAlarmService.activeAlarmId

        if (action == "wakeup_alarm" || activeAlarmId != null) {
            val alarmId = intent?.getStringExtra("id") ?: activeAlarmId
            showMathDismissDialog(alarmId)
        }
    }
"""

content = re.sub(r'    private fun checkAndShowMathDismissDialog\(\) \{.*?    \}', new_check, content, flags=re.DOTALL)

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "w") as f:
    f.write(content)
