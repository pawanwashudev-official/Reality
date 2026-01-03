package com.neubofy.reality.utils

import android.content.Context
import com.neubofy.reality.data.db.AppDatabase
import java.util.Calendar

data class FocusStatus(
    val isActive: Boolean,
    val type: FocusType,
    val endTime: Long,
    val title: String
)

enum class FocusType {
    NONE, MANUAL_FOCUS, SCHEDULE, CALENDAR, BEDTIME
}

class FocusStatusManager(private val context: Context) {
    private val prefs = SavedPreferencesLoader(context)
    private val db = AppDatabase.getDatabase(context)

    suspend fun getCurrentStatus(): FocusStatus {
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val currentMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val currentDay = cal.get(Calendar.DAY_OF_WEEK) // Sun=1, Mon=2

        // 1. Bedtime (Priority: High)
        val bedtime = prefs.getBedtimeData()
        if (bedtime.isEnabled) {
            val start = bedtime.startTimeInMins
            val end = bedtime.endTimeInMins
            var isBedtimeActive = false
            var bedEndTimeMs = 0L
            
            if (start < end) {
                if (currentMins in start until end) {
                    isBedtimeActive = true
                    bedEndTimeMs = getTimeToday(end)
                }
            } else {
                // Spans midnight e.g. 22:00 to 07:00
                if (currentMins >= start) {
                    isBedtimeActive = true
                    bedEndTimeMs = getTimeTomorrow(end)
                } else if (currentMins < end) {
                    isBedtimeActive = true
                    bedEndTimeMs = getTimeToday(end)
                }
            }
            
            if (isBedtimeActive) {
                return FocusStatus(true, FocusType.BEDTIME, bedEndTimeMs, "Bedtime Mode")
            }
        }

        // 2. Manual Focus (User overridden priority)
        val focus = prefs.getFocusModeData()
        if (focus.isTurnedOn) {
            if (focus.endTime > now) {
                return FocusStatus(true, FocusType.MANUAL_FOCUS, focus.endTime, "Focus Session")
            } else {
                 // Clean up expired focus (Optional, but good for consistency)
                 focus.isTurnedOn = false
                 prefs.saveFocusModeData(focus)
            }
        }
        
        // 3. Calendar Events
        val events = db.calendarEventDao().getCurrentEvents(now)
        if (events.isNotEmpty()) {
            val event = events.first()
            return FocusStatus(true, FocusType.CALENDAR, event.endTime, event.title)
        }

        // 4. Manual Schedules
        val schedules = prefs.loadAutoFocusHoursList()
        schedules.forEach { item ->
            // Check Repeat Days
            if (item.repeatDays.contains(currentDay)) {
                val start = item.startTimeInMins
                val end = item.endTimeInMins
                var isScheduleActive = false
                var schedEndTimeMs = 0L
                
                if (start < end) {
                    if (currentMins in start until end) {
                        isScheduleActive = true
                        schedEndTimeMs = getTimeToday(end)
                    }
                } else {
                    if (currentMins >= start) {
                        isScheduleActive = true
                        schedEndTimeMs = getTimeTomorrow(end)
                    } else if (currentMins < end) {
                        isScheduleActive = true
                        schedEndTimeMs = getTimeToday(end)
                    }
                }
                
                if (isScheduleActive) {
                    return FocusStatus(true, FocusType.SCHEDULE, schedEndTimeMs, item.title)
                }
            }
        }

        return FocusStatus(false, FocusType.NONE, 0, "")
    }
    
    private fun getTimeToday(minutes: Int): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, minutes / 60)
        cal.set(Calendar.MINUTE, minutes % 60)
        cal.set(Calendar.SECOND, 0)
        return cal.timeInMillis
    }
    
    private fun getTimeTomorrow(minutes: Int): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, minutes / 60)
        cal.set(Calendar.MINUTE, minutes % 60)
        cal.set(Calendar.SECOND, 0)
        return cal.timeInMillis
    }
}
