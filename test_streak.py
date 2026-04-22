import re

# In `updateDailyStats`:
# streak = getStreak(context),
# This gets the current streak from SharedPreferences `current_streak`.
# BUT wait! When is `PREF_STREAK` updated?
# It's updated in `recalculateGlobalStats()`:
# `prefs.edit().putInt(PREF_STREAK, currentStreak).apply()`
#
# Wait, `recalculateGlobalStats` calculates `currentStreak`:
# `val isFirstDaySuccessful = if (firstDay.totalPlannedMinutes > 0) { ... } else { firstDay.totalEffectiveMinutes > 0 }`
# If you just finished the Nightly Protocol (which adds Reflection XP), your `totalEffectiveMinutes` comes from `updateDailyStats`.
# If `totalEffectiveMinutes` is 0 (because maybe you didn't do Tapasya, but you did the Diary), the streak might break!
# The user did NOT mention what they did during the day, just that they finished the Nightly Protocol.
# Wait, if they do Nightly Protocol, that means they did reflection.
# Let's change the streak logic to include reflection:
# `isFirstDaySuccessful` should also be true if `totalDaily > 0` or something, OR if they completed the Nightly Protocol!
# A simpler fix: If they have `reflectionXP > 0`, they completed the Nightly Protocol! So streak should increment!

with open("app/src/main/java/com/neubofy/reality/utils/XPManager.kt", "r") as f:
    content = f.read()

# Let's check `isFirstDaySuccessful` logic inside `recalculateGlobalStats`
old_logic = """                val isFirstDaySuccessful = if (firstDay.totalPlannedMinutes > 0) {
                    firstDay.totalEffectiveMinutes >= (firstDay.totalPlannedMinutes * 0.75)
                } else {
                    firstDay.totalEffectiveMinutes > 0
                }"""

new_logic = """                val firstDayBreakdown = parseBreakdown(firstDay.breakdownJson, firstDay.date)
                val isFirstDaySuccessful = if (firstDay.totalPlannedMinutes > 0) {
                    firstDay.totalEffectiveMinutes >= (firstDay.totalPlannedMinutes * 0.75) || firstDayBreakdown.reflectionXP > 0
                } else {
                    firstDay.totalEffectiveMinutes > 0 || firstDayBreakdown.reflectionXP > 0
                }"""

content = content.replace(old_logic, new_logic)

# Same for `isSuccessful` inside the loop
old_loop_logic = """                        val isSuccessful = if (stat.totalPlannedMinutes > 0) {
                            stat.totalEffectiveMinutes >= (stat.totalPlannedMinutes * 0.75)
                        } else {
                            stat.totalEffectiveMinutes > 0
                        }"""

new_loop_logic = """                        val statBreakdown = parseBreakdown(stat.breakdownJson, stat.date)
                        val isSuccessful = if (stat.totalPlannedMinutes > 0) {
                            stat.totalEffectiveMinutes >= (stat.totalPlannedMinutes * 0.75) || statBreakdown.reflectionXP > 0
                        } else {
                            stat.totalEffectiveMinutes > 0 || statBreakdown.reflectionXP > 0
                        }"""

content = content.replace(old_loop_logic, new_loop_logic)

with open("app/src/main/java/com/neubofy/reality/utils/XPManager.kt", "w") as f:
    f.write(content)
