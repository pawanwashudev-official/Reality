import re

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "r") as f:
    content = f.read()

# Add isMathDialogShowing
content = content.replace("    companion object {", "    private var isMathDialogShowing = false\n\n    companion object {")

# Update dialog showing state
content = content.replace("        val dialogView = layoutInflater.inflate(com.neubofy.reality.R.layout.dialog_math_alarm_dismiss, null)", "        isMathDialogShowing = true\n        val dialogView = layoutInflater.inflate(com.neubofy.reality.R.layout.dialog_math_alarm_dismiss, null)")

# Update dialog dismiss state
content = content.replace("        dialog.setOnDismissListener { isMathDialogShowing = false }", "        dialog.setOnDismissListener { isMathDialogShowing = false }")
if "dialog.setOnDismissListener { isMathDialogShowing = false }" not in content:
    content = content.replace("        dialog.show()", "        dialog.setOnDismissListener { isMathDialogShowing = false }\n        dialog.show()")

# Make sure we don't open the dialog again if the alarm stopped ringing
content = content.replace("        if (action == \"wakeup_alarm\" || activeAlarmId != null) {", "        if ((action == \"wakeup_alarm\" || activeAlarmId != null) && activeAlarmId != null) {")

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "w") as f:
    f.write(content)
