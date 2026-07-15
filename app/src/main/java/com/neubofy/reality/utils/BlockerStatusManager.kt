package com.neubofy.reality.utils

import android.content.Context
import com.neubofy.reality.data.db.AppDatabase
import java.util.Calendar

data class BlockerStatus(
    val isActive: Boolean,
    val type: BlockerType,
    val endTime: Long,
    val title: String
)

enum class BlockerType {
    NONE, MANUAL_BLOCKER, SCHEDULE, CALENDAR, BEDTIME
}

class BlockerStatusManager(private val context: Context) {
    private val prefs = SavedPreferencesLoader(context)
    private val db = AppDatabase.getDatabase(context)

    suspend fun getCurrentStatus(): BlockerStatus {
        val now = SecureTimeProvider.currentTimeMillis(context)
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
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
            } else if (start > end) {
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
                return BlockerStatus(true, BlockerType.BEDTIME, bedEndTimeMs, "Bedtime Mode")
            }
        }

        // 2. Manual Blocker (User overridden priority)
        val blocker = prefs.getFocusModeData()
        if (blocker.isTurnedOn) {
            if (blocker.endTime > now) {
                return BlockerStatus(true, BlockerType.MANUAL_BLOCKER, blocker.endTime, "Blocker Session")
            } else {
                 // Clean up expired blocker
                 blocker.isTurnedOn = false
                 prefs.saveFocusModeData(blocker)
            }
        }
        
        // 3. Calendar Events
        val events = db.calendarEventDao().getCurrentEvents(now)
        if (events.isNotEmpty()) {
            val event = events.first()
            return BlockerStatus(true, BlockerType.CALENDAR, event.endTime, event.title)
        }

        // 4. Manual Schedules
        val schedules = prefs.loadAutoFocusHoursList()
        schedules.forEach { item ->
            // Check Repeat Days - empty means every day (consistent with BlockCache)
            val runsToday = item.repeatDays.isEmpty() || item.repeatDays.contains(currentDay)
            if (runsToday) {
                val start = item.startTimeInMins
                val end = item.endTimeInMins
                var isScheduleActive = false
                var schedEndTimeMs = 0L
                
                if (start < end) {
                    if (currentMins in start until end) {
                        isScheduleActive = true
                        schedEndTimeMs = getTimeToday(end)
                    }
                } else if (start > end) {
                    if (currentMins >= start) {
                        isScheduleActive = true
                        schedEndTimeMs = getTimeTomorrow(end)
                    } else if (currentMins < end) {
                        isScheduleActive = true
                        schedEndTimeMs = getTimeToday(end)
                    }
                }
                
                if (isScheduleActive) {
                    return BlockerStatus(true, BlockerType.SCHEDULE, schedEndTimeMs, item.title)
                }
            }
        }

        return BlockerStatus(false, BlockerType.NONE, 0, "")
    }
    
    private fun getTimeToday(minutes: Int): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = SecureTimeProvider.currentTimeMillis(context)
        cal.set(Calendar.HOUR_OF_DAY, minutes / 60)
        cal.set(Calendar.MINUTE, minutes % 60)
        cal.set(Calendar.SECOND, 0)
        return cal.timeInMillis
    }
    
    private fun getTimeTomorrow(minutes: Int): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = SecureTimeProvider.currentTimeMillis(context)
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, minutes / 60)
        cal.set(Calendar.MINUTE, minutes % 60)
        cal.set(Calendar.SECOND, 0)
        return cal.timeInMillis
    }
}
