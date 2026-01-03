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
            
            val effectiveOffset = event.customOffsetMins ?: globalOffset
            val triggerMins = event.startTimeMins - effectiveOffset
            
            // Only schedule if trigger time is in the future
            if (triggerMins > currentMins) {
                val triggerCal = java.util.Calendar.getInstance()
                triggerCal.set(java.util.Calendar.HOUR_OF_DAY, triggerMins / 60)
                triggerCal.set(java.util.Calendar.MINUTE, triggerMins % 60)
                triggerCal.set(java.util.Calendar.SECOND, 0)
                triggerCal.set(java.util.Calendar.MILLISECOND, 0)
                
                if (triggerCal.timeInMillis < nextTriggerMillis) {
                    nextTriggerMillis = triggerCal.timeInMillis
                    nextEvent = event
                }
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
            TerminalLogger.log("ALARM: No upcoming events to schedule today")
        }
    }
    
    /**
     * Schedules a snooze alarm. Uses separate request code.
     */
    fun scheduleSnooze(context: Context, id: String, title: String, url: String?, snoozeMins: Int) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("id", "snooze_$id")
                putExtra("title", title)
                putExtra("url", url)
                putExtra("mins", 0) // Snooze shows "Starting now"
                putExtra("isSnooze", true)
            }
            
            val triggerTime = System.currentTimeMillis() + (snoozeMins * 60 * 1000L)
            
            val pIntent = PendingIntent.getBroadcast(
                context,
                SNOOZE_REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val acInfo = AlarmManager.AlarmClockInfo(triggerTime, pIntent)
                alarmManager.setAlarmClock(acInfo, pIntent)
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pIntent)
            }
            
            TerminalLogger.log("SNOOZE: Scheduled '$title' in $snoozeMins minutes")
        } catch (e: Exception) {
            TerminalLogger.log("SNOOZE ERROR: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Cancels all scheduled alarms (main + snooze).
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
            
            // Cancel snooze alarm
            val snoozeIntent = PendingIntent.getBroadcast(
                context, SNOOZE_REQUEST_CODE, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(snoozeIntent)
            
            TerminalLogger.log("ALARM: All alarms canceled")
        } catch (e: Exception) {
            TerminalLogger.log("ALARM CANCEL ERROR: ${e.message}")
            e.printStackTrace()
        }
    }
}
