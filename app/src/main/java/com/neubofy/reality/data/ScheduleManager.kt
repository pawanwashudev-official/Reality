package com.neubofy.reality.data

import android.content.Context
import com.neubofy.reality.Constants
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.utils.SavedPreferencesLoader
import java.util.Calendar

data class UnifiedEvent(
    val title: String,
    val startTimeMins: Int,
    val endTimeMins: Int,
    val isEnabled: Boolean,
    val source: EventSource,
    val originalId: String,
    val url: String? = null,
    val customOffsetMins: Int? = null,
    val retryIntervalMins: Int = 0,
    val lastDismissedDate: Long = 0L,
    // Snooze settings (snapshotted from reminder or defaults)
    val snoozeEnabled: Boolean = true,
    val snoozeIntervalMins: Int = 5,
    val autoSnoozeEnabled: Boolean = true,
    val autoSnoozeTimeoutSecs: Int = 30
)

enum class EventSource { MANUAL, CALENDAR, CUSTOM_REMINDER }

object ScheduleManager {
    suspend fun getUnifiedEventsForToday(context: Context): List<UnifiedEvent> {
        val list = mutableListOf<UnifiedEvent>()
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_WEEK) // Sunday=1
        
        // 1. Manual Schedules
        val manualSchedules = SavedPreferencesLoader(context).loadAutoFocusHoursList()
        for (sched in manualSchedules) {
            // Check repeat days
            if (sched.repeatDays.isEmpty() || sched.repeatDays.contains(currentDay)) {
                // Manual schedules are inherently 'enabled' if they are in the list
                // Unless there is an explicit strict mode or other toggle
                list.add(UnifiedEvent(
                    title = sched.title,
                    startTimeMins = sched.startTimeInMins,
                    endTimeMins = sched.endTimeInMins,
                    isEnabled = true, 
                    source = EventSource.MANUAL,
                    originalId = "${sched.title}_${sched.startTimeInMins}",
                    url = null // Manual schedules don't have URLs yet, maybe in future
                ))
            }
        }
        
        // 1.5 Custom Reminders
        // 1.5 Custom Reminders
        val customReminders = SavedPreferencesLoader(context).loadCustomReminders()
        val currentMins = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        
        for (reminder in customReminders) {
            if (reminder.isEnabled) {
                 // Day Check
                 if (reminder.repeatDays.isNotEmpty() && !reminder.repeatDays.contains(currentDay)) {
                     continue
                 }
                 
                 val startMins = reminder.hour * 60 + reminder.minute
                 
                 list.add(UnifiedEvent(
                     title = reminder.title,
                     startTimeMins = startMins,
                     endTimeMins = startMins + 5,
                     isEnabled = true,
                     source = EventSource.CUSTOM_REMINDER,
                     originalId = reminder.id,
                     // Use snapshotted settings (new fields) or fallback to legacy
                     url = reminder.redirectUrl ?: reminder.url,
                     customOffsetMins = reminder.offsetMins,
                     retryIntervalMins = reminder.retryIntervalMins,
                     lastDismissedDate = reminder.lastDismissedDate,
                     // Pass snooze settings for use by AlarmActivity
                     snoozeEnabled = reminder.snoozeEnabled,
                     snoozeIntervalMins = reminder.snoozeIntervalMins,
                     autoSnoozeEnabled = reminder.autoSnoozeEnabled,
                     autoSnoozeTimeoutSecs = reminder.autoSnoozeTimeoutSecs
                 ))
            }
        }
        
        // 2. Calendar Events
        val db = AppDatabase.getDatabase(context)
        val dao = db.calendarEventDao()
        
        // Fetch events for TODAY
        val startOfDay = getStartOfDayInMillis()
        val endOfDay = startOfDay + 24 * 60 * 60 * 1000
        
        val events = dao.getEventsInRange(startOfDay, endOfDay)
        
        for (event in events) {
            if (event.isEnabled) {
                 val startMins = getMinsFromMillis(event.startTime)
                 val endMins = getMinsFromMillis(event.endTime)
                 // Some calendar events might span days, but for start time check this is enough
                 list.add(UnifiedEvent(
                    title = event.title,
                    startTimeMins = startMins,
                    endTimeMins = endMins,
                    isEnabled = true,
                    source = EventSource.CALENDAR,
                    originalId = event.eventId,
                    url = extractUrlFromDescription(event.title) // Simple heuristic or null
                 ))
            }
        }
        
        return list.sortedBy { it.startTimeMins }
    }
    
    private fun getStartOfDayInMillis(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    
    private fun getMinsFromMillis(millis: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }
    
    private fun extractUrlFromDescription(text: String): String? {
        // Placeholder: If we had description, we'd parse.
        // For now, if title contains a URL, use it? Or maybe null.
        return null
    }
}
