with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "r") as f:
    content = f.read()

# Fix onNewIntent signature
content = content.replace("override fun onNewIntent(intent: android.content.Intent?)", "override fun onNewIntent(intent: android.content.Intent)")

# Move checkAndShowMathDismissDialog out of whatever it is in.
# Looking at the code:
#             dialog.dismiss()
#         }
#
#         dialog.setOnDismissListener { isMathDialogShowing = false }
#         dialog.show()
#
#
#
#
#     private fun checkAndShowMathDismissDialog() {
# It looks like checkAndShowMathDismissDialog is INSIDE showAlarmSetupDialog!

bad_part = """        dialog.setOnDismissListener { isMathDialogShowing = false }
        dialog.show()




    private fun checkAndShowMathDismissDialog() {
        if (isMathDialogShowing) return
        val action = intent.getStringExtra("action")
        val activeAlarmId = com.neubofy.reality.services.WakeupAlarmService.activeAlarmId

        if ((action == "wakeup_alarm" || activeAlarmId != null) && activeAlarmId != null) {
            val alarmId = intent.getStringExtra("id") ?: activeAlarmId
            showMathDismissDialog(alarmId)
        }
    }

    }"""

good_part = """        dialog.setOnDismissListener { isMathDialogShowing = false }
        dialog.show()
    }

    private fun checkAndShowMathDismissDialog() {
        if (isMathDialogShowing) return
        val action = intent.getStringExtra("action")
        val activeAlarmId = com.neubofy.reality.services.WakeupAlarmService.activeAlarmId

        if ((action == "wakeup_alarm" || activeAlarmId != null) && activeAlarmId != null) {
            val alarmId = intent.getStringExtra("id") ?: activeAlarmId
            showMathDismissDialog(alarmId)
        }
    }"""

content = content.replace(bad_part, good_part)

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "w") as f:
    f.write(content)
