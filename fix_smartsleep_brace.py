import re

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "r") as f:
    content = f.read()

# I think there's a missing or extra brace. Let's find out.
# Let's count open and close braces.
open_braces = content.count('{')
close_braces = content.count('}')
print(f"Open: {open_braces}, Close: {close_braces}")

if close_braces > open_braces:
    # remove the last closing brace
    content = content[:content.rfind('}')] + content[content.rfind('}')+1:]

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "w") as f:
    f.write(content)
