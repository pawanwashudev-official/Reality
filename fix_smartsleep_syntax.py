import re

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "r") as f:
    content = f.read()

# Fix syntax error in qrScannerLauncher
content = content.replace("""        if (result.resultCode == RESULT_OK) {
            isUnlockedThisSession = true
            checkHealthPermissionsFlow()
        }

        checkAndShowMathDismissDialog() else {""", """        if (result.resultCode == RESULT_OK) {
            isUnlockedThisSession = true
            checkHealthPermissionsFlow()
            checkAndShowMathDismissDialog()
        } else {""")

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "w") as f:
    f.write(content)
