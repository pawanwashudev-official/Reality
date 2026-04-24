import re

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "r") as f:
    content = f.read()

# I found the issue. The inner class and DiffCallback were messed up or outside the class? No, the class declaration is missing closing brace.
# Let's just fix it automatically

content += "\n}"

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "w") as f:
    f.write(content)
