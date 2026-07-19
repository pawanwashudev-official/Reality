package com.neubofy.reality.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.data.db.CalendarEvent
import com.neubofy.reality.utils.BlockCache
import com.neubofy.reality.utils.NotificationHelper
import com.neubofy.reality.utils.SmartScheduleManager
import com.neubofy.reality.utils.TerminalLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.UUID

class ScheduleTransitionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TRANSITION = "com.neubofy.reality.ACTION_SCHEDULE_TRANSITION"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == ACTION_TRANSITION) {
            TerminalLogger.log("SMART ALARM: Transition triggered")
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = AppDatabase.getDatabase(context)
                    val now = System.currentTimeMillis()
                    
                    // 1. Rebuild the Box (O(1) block mapping)
                    BlockCache.rebuildBox(context)
                    
                    // 2. Notify AppBlockerService to refresh immediately
                    val refreshIntent = Intent("com.neubofy.reality.refresh.focus_mode").apply {
                        setPackage(context.packageName)
                    }
                    context.sendBroadcast(refreshIntent)

                    // 3. Handle Repeats for Expired In-App Schedules
                    val expiredEvents = db.calendarEventDao().getExpiredRepeatingInAppEvents(now)
                    val newEvents = mutableListOf<CalendarEvent>()
                    
                    for (event in expiredEvents) {
                        val nextOccurrence = calculateNextOccurrence(event)
                        if (nextOccurrence != null) {
                            newEvents.add(nextOccurrence)
                        }
                        // Delete the expired one
                        db.calendarEventDao().deleteByEventId(event.eventId)
                    }
                    
                    if (newEvents.isNotEmpty()) {
                        db.calendarEventDao().insertAll(newEvents)
                        TerminalLogger.log("SMART ALARM: Rolled over ${newEvents.size} repeating schedules")
                    }
                    
                    // Always cleanup non-repeating old events
                    db.calendarEventDao().deleteOldInAppEvents(now)

                    // 4. Send Notification if a schedule just started
                    val activeEvents = db.calendarEventDao().getCurrentEvents(now)
                    for (event in activeEvents) {
                        // If it started within the last minute, consider it "just started"
                        if (now - event.startTime < 60000) {
                            NotificationHelper.showScheduleStartedNotification(context, event)
                        }
                    }

                    // 5. Chain the next alarm
                    SmartScheduleManager.scheduleNextTransition(context)
                    
                } catch (e: Exception) {
                    TerminalLogger.log("SMART ALARM ERROR: ${e.message}")
                }
            }
        }
    }
    
    private fun calculateNextOccurrence(event: CalendarEvent): CalendarEvent? {
        val rule = event.repeatRule ?: return null
        val cal = Calendar.getInstance()
        cal.timeInMillis = event.startTime
        
        val duration = event.endTime - event.startTime
        
        if (rule == "DAILY") {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        } else if (rule.matches(Regex("^[1-7](,[1-7])*$"))) {
            // Specific days of week (1=Sun, 2=Mon... 7=Sat)
            val days = rule.split(",").mapNotNull { it.toIntOrNull() }
            if (days.isEmpty()) return null
            
            // Find next day in the list
            var addedDays = 0
            while (addedDays < 7) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                addedDays++
                if (days.contains(cal.get(Calendar.DAY_OF_WEEK))) {
                    break
                }
            }
        } else {
            return null
        }
        
        val newStart = cal.timeInMillis
        val newEnd = newStart + duration
        
        return event.copy(
            eventId = UUID.randomUUID().toString(),
            startTime = newStart,
            endTime = newEnd
        )
    }
}
