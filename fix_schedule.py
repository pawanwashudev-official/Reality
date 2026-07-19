import re

file_path = r'c:\Users\Pawan Kumar\Neubofy\Reality\app\src\main\java\com\neubofy\reality\ui\activity\ScheduleListActivity.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Remove manualList references
content = re.sub(r'private var manualList: MutableList<Constants\.AutoTimedActionItem> = mutableListOf\(\)', '', content)

# 2. Replace ScheduleDisplayItem.Custom logic
content = re.sub(r'data class Custom\(val item: com\.neubofy\.reality\.Constants\.AutoTimedActionItem\) : ScheduleDisplayItem\(\)', '', content)
content = re.sub(r'val customSchedules = prefs\.loadAutoFocusHoursList\(\)', '', content)
content = re.sub(r'displayItems\.addAll\(customSchedules\.map \{ ScheduleDisplayItem\.Custom\(it\) \}\)', '', content)
content = content.replace('val isCustom = item is ScheduleDisplayItem.Custom', 'val isCustom = item.event.source == "IN_APP"')

# Fix renderEventsOnTimeline 'when' statement
content = re.sub(r'is ScheduleDisplayItem\.Custom -> \{[\s\S]*?\}', '', content)
content = content.replace('is ScheduleDisplayItem.Synced -> {', 'is ScheduleDisplayItem.Synced -> {')

# Delete deleteManualItem logic
content = re.sub(r'private fun deleteManualItem\(itemToDelete: Constants\.AutoTimedActionItem\) \{[\s\S]*?\}\n\n', '', content)

# 3. Fix Add / Edit Schedule Dialogs to save to Room DB
add_dialog_replacement = r'''
                        val isReminder = dialogBinding.cbReminder.isChecked
                        
                        val startMs = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.HOUR_OF_DAY, startTimeMins / 60)
                            set(java.util.Calendar.MINUTE, startTimeMins % 60)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        
                        val endMs = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.HOUR_OF_DAY, endTimeMins / 60)
                            set(java.util.Calendar.MINUTE, endTimeMins % 60)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        
                        val finalEndMs = if (endMs <= startMs) endMs + 86400000L else endMs

                        val newItem = com.neubofy.reality.data.db.CalendarEvent(
                            eventId = java.util.UUID.randomUUID().toString(),
                            title = title,
                            startTime = startMs,
                            endTime = finalEndMs,
                            source = "IN_APP",
                            isEnabled = true,
                            repeatRule = days.joinToString(",")
                        )
                        
                        lifecycleScope.launch(Dispatchers.IO) {
                            val db = AppDatabase.getDatabase(applicationContext)
                            db.calendarEventDao().insertEvent(newItem)
                            sendBroadcast(android.content.Intent(com.neubofy.reality.services.AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE))
                            com.neubofy.reality.utils.SmartScheduleManager.scheduleNextTransition(this@ScheduleListActivity)
                            withContext(Dispatchers.Main) {
                                loadSchedules()
                                dialog.dismiss()
                            }
                        }
'''

content = re.sub(r'val isReminder = dialogBinding\.cbReminder\.isChecked[\s\S]*?dialog\.dismiss\(\)', add_dialog_replacement.strip(), content)

# Remove showEditScheduleDialog completely for now to fix compile error (we can add back if needed)
content = re.sub(r'@android\.annotation\.SuppressLint\(\"SetTextI18n\"\)\s*private fun showEditScheduleDialog\(existing: Constants\.AutoTimedActionItem\) \{[\s\S]*?\}\n\n', '', content)

# Find all remaining AutoTimedActionItem and delete them or comment them
content = re.sub(r'existing: Constants\.AutoTimedActionItem', 'existing: Any', content)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print('Done modifying ScheduleListActivity.kt')
