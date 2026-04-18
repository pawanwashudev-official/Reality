import re

# Fix MainActivity.kt
with open("app/src/main/java/com/neubofy/reality/ui/activity/MainActivity.kt", "r") as f:
    content = f.read()

content = content.replace("override fun onNewIntent(intent: Intent?)", "override fun onNewIntent(intent: Intent)")
content = content.replace("super.onNewIntent(intent)", "super.onNewIntent(intent)")
content = content.replace("setIntent(intent)", "setIntent(intent)")

with open("app/src/main/java/com/neubofy/reality/ui/activity/MainActivity.kt", "w") as f:
    f.write(content)


# Fix SmartSleepActivity.kt
with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "r") as f:
    content = f.read()

content = content.replace("override fun onNewIntent(intent: android.content.Intent?)", "override fun onNewIntent(intent: android.content.Intent)")

# Check if checkAndShowMathDismissDialog is a local function?
# "Modifier 'private' is not applicable to 'local function'" means it's nested inside another function!
idx = content.find("private fun checkAndShowMathDismissDialog()")
if idx != -1:
    print("Found checkAndShowMathDismissDialog. Checking braces.")
    # let's just properly place it outside. We'll find where it is currently.

    # Just extract it and move to class level.
    # It's probably inside another method.
