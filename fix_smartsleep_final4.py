with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "r") as f:
    content = f.read()

# Remove the extra brace before checkAndShowMathDismissDialog
idx2 = content.find("private fun checkAndShowMathDismissDialog")
text_before = content[:idx2]
text_after = content[idx2:]

# remove last '}' in text_before
last_brace = text_before.rfind('}')
text_before = text_before[:last_brace] + text_before[last_brace+1:]

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "w") as f:
    f.write(text_before + text_after)
