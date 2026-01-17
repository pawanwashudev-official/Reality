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
    
    private const val PREF_CALENDAR_DISMISSALS = "calendar_dismissals"

    suspend fun getUnifiedEventsForToday(context: Context): List<UnifiedEvent> {
        val list = mutableListOf<UnifiedEvent>()
        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_WEEK) // Sunday=1
        
        // 1. Manual Schedules
        val prefs = SavedPreferencesLoader(context)
        val manualSchedules = prefs.loadAutoFocusHoursList()
        var schedulesChanged = false
        val todayStartMillis = getStartOfDayInMillis()

        val iterator = manualSchedules.iterator()
        while (iterator.hasNext()) {
            val sched = iterator.next()
            
            // CLEANUP: If this schedule was dismissed on a PREVIOUS day and it's a one-time thing (no repeat days)
            // or even if it repeats, a past dismissal shouldn't linger if it's considered "completed" for that day.
            // But per user request: "delete unnecessary previous days schedule".
            // We interpret this as: If it's a non-repeating schedule and it was dismissed/completed in the past -> DELETE IT.
            
            if (sched.repeatDays.isEmpty() && sched.lastDismissedDate > 0 && sched.lastDismissedDate < todayStartMillis) {
                iterator.remove()
                schedulesChanged = true
                continue
            }
            
            if (!sched.isReminderEnabled) continue
            
            val runsToday = sched.repeatDays.isEmpty() || sched.repeatDays.contains(currentDay)
            if (runsToday) {
                val scheduleId = "sched_${sched.title}_${sched.startTimeInMins}_${sched.endTimeInMins}"
                
                list.add(UnifiedEvent(
                    title = sched.title,
                    startTimeMins = sched.startTimeInMins,
                    endTimeMins = sched.endTimeInMins,
                    isEnabled = true,
                    source = EventSource.MANUAL,
                    originalId = scheduleId,
                    url = null,
                    lastDismissedDate = sched.lastDismissedDate
                ))
            }
        }
        
        if (schedulesChanged) {
             prefs.saveAutoFocusHoursList(manualSchedules)
        }
        
        // 1.5 Custom Reminders
        val customReminders = prefs.loadCustomReminders()
        
        for (reminder in customReminders) {
            if (reminder.isEnabled) {
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
                     url = reminder.redirectUrl ?: reminder.url,
                     customOffsetMins = reminder.offsetMins,
                     retryIntervalMins = reminder.retryIntervalMins,
                     lastDismissedDate = reminder.lastDismissedDate,
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
        
        val startOfDay = getStartOfDayInMillis()
        val endOfDay = startOfDay + 24 * 60 * 60 * 1000
        
        val events = dao.getEventsInRange(startOfDay, endOfDay)
        val dismissedCalendarEvents = getDismissedCalendarEvents(context)
        
        for (event in events) {
            if (event.isEnabled) {
                 val startMins = getMinsFromMillis(event.startTime)
                 val endMins = getMinsFromMillis(event.endTime)
                 
                 // Check if dismissed today
                 val lastDismissed = dismissedCalendarEvents[event.eventId] ?: 0L
                 
                 list.add(UnifiedEvent(
                    title = event.title,
                    startTimeMins = startMins,
                    endTimeMins = endMins,
                    isEnabled = true,
                    source = EventSource.CALENDAR,
                    originalId = event.eventId,
                    url = extractUrlFromDescription(event.title),
                    lastDismissedDate = lastDismissed
                 ))
            }
        }
        
        return list.sortedBy { it.startTimeMins }
    }
    
    /**
     * Mark an event as dismissed securely in the persistent store.
     */
    fun markAsDismissed(context: Context, eventId: String, source: EventSource) {
        val now = System.currentTimeMillis()
        val prefs = SavedPreferencesLoader(context)
        
        when (source) {
            EventSource.MANUAL -> {
                // We have to iterate to find the matching schedule.
                // ID format: sched_TITLE_START_END
                val list = prefs.loadAutoFocusHoursList()
                var changed = false
                
                for (item in list) {
                    val itemId = "sched_${item.title}_${item.startTimeInMins}_${item.endTimeInMins}"
                    if (itemId == eventId) {
                        item.lastDismissedDate = now
                        changed = true
                        
                        // If it's a non-repeating schedule, disable it after dismissal
                        // (Empty repeatDays means it runs every day, which IS repeating)
                        // Single-occurrence schedules would have no repeat days AND a specific date
                        // For now, we just mark it dismissed - it will be filtered by AlarmScheduler
                        
                        com.neubofy.reality.utils.TerminalLogger.log("SCHEDULE: Dismissed '${item.title}' until next repeat")
                        break
                    }
                }
                
                if (changed) {
                    prefs.saveAutoFocusHoursList(list)
                }
            }
            EventSource.CUSTOM_REMINDER -> {
                val list = prefs.loadCustomReminders()
                val item = list.find { it.id == eventId }
                if (item != null) {
                    // Update dismissal
                    val updatedItem = item.copy(lastDismissedDate = now)
                    val newList = list.toMutableList()
                    val index = list.indexOf(item)
                    newList[index] = updatedItem
                    
                    // IF it's a non-repeating reminder, should we disable it?
                    // "make /add function that if remainder is dismissed then it get deleted"
                    if (updatedItem.repeatDays.isEmpty()) {
                        // It's a one-off. Clean it up or disable it.
                        // User suggestion: Delete. 
                        // Let's remove it to keep list clean.
                        newList.removeAt(index)
                    }
                    
                    prefs.saveCustomReminders(newList)
                }
            }
            EventSource.CALENDAR -> {
                saveCalendarDismissal(context, eventId, now)
            }
        }
        
        // Recalculate next alarm immediately
        com.neubofy.reality.utils.AlarmScheduler.scheduleNextAlarm(context)
    }
    
    // === CALENDAR DISMISSAL PERSISTENCE & CLEANUP ===
    
    private fun getDismissedCalendarEvents(context: Context): Map<String, Long> {
        val prefs = context.getSharedPreferences(PREF_CALENDAR_DISMISSALS, Context.MODE_PRIVATE)
        val all = prefs.all
        val map = mutableMapOf<String, Long>()
        val cleanupThreshold = getStartOfDayInMillis() - 1000 // Keep for 24h
        
        var needsCleanup = false
        val editor = prefs.edit()
        
        for ((key, value) in all) {
            if (value is Long) {
                if (value > cleanupThreshold) {
                     map[key] = value
                } else {
                    // Optimized cleanup: Delete old entries to free storage
                    editor.remove(key)
                    needsCleanup = true
                }
            }
        }
        
        if (needsCleanup) {
            editor.apply()
        }
        
        return map
    }
    
    private fun saveCalendarDismissal(context: Context, eventId: String, timestamp: Long) {
        val prefs = context.getSharedPreferences(PREF_CALENDAR_DISMISSALS, Context.MODE_PRIVATE)
        prefs.edit().putLong(eventId, timestamp).apply()
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
        return null
    }
}
