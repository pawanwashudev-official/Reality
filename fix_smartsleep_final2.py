import re

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "r") as f:
    content = f.read()

# Let's count properly
open_braces = content.count('{')
close_braces = content.count('}')

if open_braces > close_braces:
    content += "}" * (open_braces - close_braces)
elif close_braces > open_braces:
    for _ in range(close_braces - open_braces):
        content = content[:content.rfind('}')] + content[content.rfind('}')+1:]

with open("app/src/main/java/com/neubofy/reality/ui/activity/SmartSleepActivity.kt", "w") as f:
    f.write(content)
