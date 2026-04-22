with open("app/src/main/java/com/neubofy/reality/ui/activity/BlockActivity.kt", "r") as f:
    content = f.read()

content = content.replace("com.neubofy.reality.emergencyData.maxUses", "emergencyData.maxUses")

with open("app/src/main/java/com/neubofy/reality/ui/activity/BlockActivity.kt", "w") as f:
    f.write(content)
