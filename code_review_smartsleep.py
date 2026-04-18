with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "r") as f:
    content = f.read()
    if "private fun showMathDismissDialog(alarmId: String?)" in content:
        print("--- showMathDismissDialog found ---")
        idx = content.find("private fun showMathDismissDialog")
        print(content[idx:idx+2500])
