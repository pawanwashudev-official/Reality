with open("app/src/main/java/com/neubofy/reality/Constants.kt", "r") as f:
    content = f.read()

# Change usesRemaining to rely on a max value tracked in the object, not a static constant.
# Because the user wants to be able to change this limit per user between 1 and 15.

new_data = """    data class EmergencyModeData(
        var maxUses: Int = 3,
        var usesRemaining: Int = 3,
        var currentSessionEndTime: Long = -1,
        var lastResetDate: Long = System.currentTimeMillis()
    )"""

content = content.replace("""    data class EmergencyModeData(
        var usesRemaining: Int = EMERGENCY_MAX_USES,
        var currentSessionEndTime: Long = -1,
        var lastResetDate: Long = System.currentTimeMillis()
    )""", new_data)

with open("app/src/main/java/com/neubofy/reality/Constants.kt", "w") as f:
    f.write(content)
