package com.neubofy.reality.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.neubofy.reality.data.EventSource
import com.neubofy.reality.data.ScheduleManager
import com.neubofy.reality.data.UnifiedEvent
import com.neubofy.reality.receivers.ReminderReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Unified Alarm Scheduler - Single source of truth for ALL reminders.
 * Handles: Manual Schedules, Calendar Events, Custom Reminders, and Snooze.
 */
object AlarmScheduler {
    
    private const val ALARM_REQUEST_CODE = 1001
    private const val SNOOZE_REQUEST_CODE = 1002
    private const val SNOOZE_CODES_PREF = "active_snooze_codes"
    
    // Track active snooze request codes for proper cancellation
    private fun getActiveSnoozeCodesPrefs(context: Context) = 
        context.getSharedPreferences(SNOOZE_CODES_PREF, Context.MODE_PRIVATE)
    
    private fun addSnoozeCode(context: Context, code: Int) {
        val prefs = getActiveSnoozeCodesPrefs(context)
        val codes = prefs.getStringSet("codes", mutableSetOf()) ?: mutableSetOf()
        val newCodes = codes.toMutableSet()
        newCodes.add(code.toString())
        prefs.edit().putStringSet("codes", newCodes).apply()
    }
    
    private fun removeSnoozeCode(context: Context, code: Int) {
        val prefs = getActiveSnoozeCodesPrefs(context)
        val codes = prefs.getStringSet("codes", mutableSetOf()) ?: mutableSetOf()
        val newCodes = codes.toMutableSet()
        newCodes.remove(code.toString())
        prefs.edit().putStringSet("codes", newCodes).apply()
    }
    
    private fun getAllSnoozeCodes(context: Context): Set<Int> {
        val prefs = getActiveSnoozeCodesPrefs(context)
        val codes = prefs.getStringSet("codes", emptySet()) ?: emptySet()
        return codes.mapNotNull { it.toIntOrNull() }.toSet()
    }
    
    private fun clearAllSnoozeCodes(context: Context) {
        getActiveSnoozeCodesPrefs(context).edit().clear().apply()
    }
    
    /**
     * Schedules the next upcoming reminder from ALL sources.
     * Should be called:
     * - On app start
     * - After each reminder fires
     * - When schedules are modified
     * - When master switch is toggled ON
     */
    fun scheduleNextAlarm(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                scheduleNextAlarmInternal(context)
            } catch (e: Exception) {
                TerminalLogger.log("ALARM ERROR: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    private suspend fun scheduleNextAlarmInternal(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java)
        
        // Cancel existing alarm first
        val existingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        existingIntent?.let { alarmManager.cancel(it) }
        
        val prefs = context.getSharedPreferences("reality_prefs", Context.MODE_PRIVATE)
        
        // Check if reminders are enabled
        if (!prefs.getBoolean("reminders_global_enabled", true)) {
            TerminalLogger.log("ALARM: Reminders disabled, not scheduling")
            return
        }
        
        // Get filter settings
        val manualEnabled = prefs.getBoolean("reminder_source_manual", true)
        val calendarEnabled = prefs.getBoolean("reminder_source_calendar", true)
        val globalOffset = prefs.getInt("reminder_offset_minutes", 1)
        
        // Get ALL unified events
        val allEvents = ScheduleManager.getUnifiedEventsForToday(context)
        
        val now = java.util.Calendar.getInstance()
        val currentMins = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        
        var nextTriggerMillis = Long.MAX_VALUE
        var nextEvent: UnifiedEvent? = null
        
        for (event in allEvents) {
            // Apply source filters
            if (event.source == EventSource.MANUAL && !manualEnabled) continue
            if (event.source == EventSource.CALENDAR && !calendarEnabled) continue
            if (!event.isEnabled) continue
            
            // CHECK: Has this event fired recently? (prevents same-minute re-firing)
            if (FiredEventsCache.hasFiredRecently(context, event.originalId)) {
                TerminalLogger.log("ALARM: Skipping '${event.title}' (fired recently)")
                continue
            }
            
            val effectiveOffset = event.customOffsetMins ?: globalOffset
            val triggerMins = event.startTimeMins - effectiveOffset
            
            // Calculate when the reminder should trigger
            // If trigger time is in the past but event start is in future, trigger NOW
            val triggerCal = java.util.Calendar.getInstance()
            
            if (triggerMins >= currentMins) {
                // Normal case: trigger time is in the future or current minute
                triggerCal.set(java.util.Calendar.HOUR_OF_DAY, triggerMins / 60)
                triggerCal.set(java.util.Calendar.MINUTE, triggerMins % 60)
                triggerCal.set(java.util.Calendar.SECOND, 0)
                triggerCal.set(java.util.Calendar.MILLISECOND, 0)
            } else if (event.startTimeMins > currentMins) {
                // Edge case: trigger time passed but event hasn't started yet
                // Schedule to trigger in 10 seconds (catch up)
                triggerCal.add(java.util.Calendar.SECOND, 10)
            } else {
                // Both trigger and event are in the past, skip
                continue
            }
            
            if (triggerCal.timeInMillis < nextTriggerMillis) {
                nextTriggerMillis = triggerCal.timeInMillis
                nextEvent = event
            }
        }
        
        if (nextEvent != null && nextTriggerMillis != Long.MAX_VALUE) {
            val effectiveOffset = nextEvent.customOffsetMins ?: globalOffset
            
            intent.putExtra("id", nextEvent.originalId)
            intent.putExtra("title", nextEvent.title)
            intent.putExtra("url", nextEvent.url)
            intent.putExtra("mins", effectiveOffset)
            intent.putExtra("source", nextEvent.source.name)
            // Pass snooze settings (snapshotted from reminder)
            intent.putExtra("snoozeEnabled", nextEvent.snoozeEnabled)
            intent.putExtra("snoozeIntervalMins", nextEvent.snoozeIntervalMins)
            intent.putExtra("autoSnoozeEnabled", nextEvent.autoSnoozeEnabled)
            intent.putExtra("autoSnoozeTimeoutSecs", nextEvent.autoSnoozeTimeoutSecs)
            
            val pIntent = PendingIntent.getBroadcast(
                context,
                ALARM_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val acInfo = AlarmManager.AlarmClockInfo(nextTriggerMillis, pIntent)
                alarmManager.setAlarmClock(acInfo, pIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextTriggerMillis, pIntent)
            }
            
            val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(nextTriggerMillis))
            TerminalLogger.log("ALARM: Scheduled '${nextEvent.title}' [${nextEvent.source}] at $timeStr")
        } else {
            // Midnight refresh logic removed per user request.
            // "i had said that remove cleaning at midnight"
            TerminalLogger.log("ALARM: No upcoming events.")
        }
    }
    
    private fun getMidnightTonightMillis(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    
    /**
     * Schedules a snooze alarm. Uses separate request code.
     * Now tracks snooze codes for proper cancellation and passes source for dismissal.
     */
    fun scheduleSnooze(context: Context, id: String, title: String, url: String?, snoozeMins: Int, source: String = "MANUAL") {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            // Strip snooze_ prefix if present to get original ID
            val originalId = if (id.startsWith("snooze_")) id.removePrefix("snooze_") else id
            
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("id", "snooze_$originalId")
                putExtra("originalId", originalId)  // For dismissal lookup
                putExtra("title", title)
                putExtra("url", url)
                putExtra("mins", 0) // Snooze shows "Starting now"
                putExtra("isSnooze", true)
                putExtra("source", source)  // CRITICAL FIX: Pass source for dismissal
                putExtra("snoozeEnabled", true)
                putExtra("snoozeIntervalMins", snoozeMins)
                putExtra("autoSnoozeEnabled", true)
                putExtra("autoSnoozeTimeoutSecs", 30)
            }
            
            val triggerTime = System.currentTimeMillis() + (snoozeMins * 60 * 1000L)
            
            // Use unique request code based on reminder ID to avoid overwrites
            val snoozeRequestCode = 2000 + (originalId.hashCode() and 0x7FFFFFFF) % 1000
            
            // Track this snooze code for later cancellation
            addSnoozeCode(context, snoozeRequestCode)
            
            val pIntent = PendingIntent.getBroadcast(
                context,
                snoozeRequestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val acInfo = AlarmManager.AlarmClockInfo(triggerTime, pIntent)
                alarmManager.setAlarmClock(acInfo, pIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pIntent)
            }
            
            val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(triggerTime))
            TerminalLogger.log("SNOOZE: Scheduled '$title' at $timeStr (in $snoozeMins min)")
        } catch (e: Exception) {
            TerminalLogger.log("SNOOZE ERROR: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Cancels a specific snooze alarm by ID.
     */
    fun cancelSnooze(context: Context, id: String) {
        try {
            val originalId = if (id.startsWith("snooze_")) id.removePrefix("snooze_") else id
            val snoozeRequestCode = 2000 + (originalId.hashCode() and 0x7FFFFFFF) % 1000
            
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java)
            val pIntent = PendingIntent.getBroadcast(
                context, snoozeRequestCode, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            pIntent?.let { alarmManager.cancel(it) }
            
            // Remove from tracking
            removeSnoozeCode(context, snoozeRequestCode)
            TerminalLogger.log("SNOOZE: Canceled snooze for ID $originalId")
        } catch (e: Exception) {
            TerminalLogger.log("SNOOZE CANCEL ERROR: ${e.message}")
        }
    }
    
    /**
     * Cancels all scheduled alarms (main + ALL tracked snoozes).
     */
    fun cancelAllAlarms(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java)
            
            // Cancel main alarm
            val mainIntent = PendingIntent.getBroadcast(
                context, ALARM_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(mainIntent)
            
            // Cancel ALL tracked snooze alarms (CRITICAL FIX)
            val snoozeCodes = getAllSnoozeCodes(context)
            var canceledCount = 0
            for (code in snoozeCodes) {
                val snoozeIntent = PendingIntent.getBroadcast(
                    context, code, intent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                snoozeIntent?.let { 
                    alarmManager.cancel(it) 
                    canceledCount++
                }
            }
            
            // Clear all tracked codes
            clearAllSnoozeCodes(context)
            
            TerminalLogger.log("ALARM: Canceled main + $canceledCount snooze alarms")
        } catch (e: Exception) {
            TerminalLogger.log("ALARM CANCEL ERROR: ${e.message}")
            e.printStackTrace()
        }
    }
}

