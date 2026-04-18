import re

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "r") as f:
    content = f.read()

# Fix second syntax error in checkAndShowMathDismissDialog call
content = content.replace("""        if (!isUnlockedThisSession) {
            binding.root.visibility = View.GONE // Hide until verified
            qrScannerLauncher.launch(android.content.Intent(this, QRScannerActivity::class.java))
        } else {
            checkHealthPermissionsFlow()
        }

        checkAndShowMathDismissDialog()""", """        if (!isUnlockedThisSession) {
            binding.root.visibility = View.GONE // Hide until verified
            qrScannerLauncher.launch(android.content.Intent(this, QRScannerActivity::class.java))
        } else {
            checkHealthPermissionsFlow()
            checkAndShowMathDismissDialog()
        }""")

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "w") as f:
    f.write(content)
