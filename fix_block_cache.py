with open("app/src/main/java/com/neubofy/reality/utils/BlockCache.kt", "r") as f:
    content = f.read()

# BlockCache logic:
# `if (emergencyData.currentSessionEndTime > now && emergencyData.usesRemaining >= 0)`
# Wait! If usesRemaining is 0 but the session is still active (endTime > now), we SHOULD allow the emergency access.
# The `usesRemaining` tracks how many new sessions you can start. Once you start the 3rd one, it drops to 0, but the session is currently active.
# By checking `usesRemaining > 0`, it blocks the 3rd use immediately!
# That explains "emergency cota limit is 3 but i think work only 2".

content = content.replace("emergencyData.currentSessionEndTime > now && emergencyData.usesRemaining > 0", "emergencyData.currentSessionEndTime > now")

with open("app/src/main/java/com/neubofy/reality/utils/BlockCache.kt", "w") as f:
    f.write(content)
