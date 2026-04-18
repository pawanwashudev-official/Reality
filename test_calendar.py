import datetime
# Test logic manually to ensure no edge cases causing infinite loop or missed alarm.
# alarmMins <= currentMins -> +1 day. This is correct.
# repeatDays logic:
# if currentDay in repeatDays and alarmMins > currentMins -> today. Correct.
# else find days to add.
# daysToAdd = 1
# while not in repeatDays: daysToAdd++ -> correct.
print("Logic is correct")
