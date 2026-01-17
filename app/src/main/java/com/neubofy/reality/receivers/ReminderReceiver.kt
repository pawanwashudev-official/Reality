package com.neubofy.reality.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.neubofy.reality.utils.TerminalLogger
import android.os.PowerManager
import com.neubofy.reality.services.AlarmService
import com.neubofy.reality.utils.AlarmScheduler
import com.neubofy.reality.utils.FiredEventsCache

class ReminderReceiver : BroadcastReceiver() {

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent?) {
        // 1. Handle Dismiss Action
        if (intent?.action == "ACTION_DISMISS") {
            handleDismiss(context, intent)
            return
        }

        // 2. Handle Midnight Refresh (Silent - just reschedule)
        if (intent?.getBooleanExtra("isMidnightRefresh", false) == true) {
            TerminalLogger.log("ALARM: Midnight refresh triggered - recalculating...")
            AlarmScheduler.scheduleNextAlarm(context)
            return
        }

        // 3. Handle Reminder Alarm Check (Main Logic)
        val id = intent?.getStringExtra("id") ?: return
        val title = intent.getStringExtra("title") ?: "Reminder"
        val url = intent.getStringExtra("url")
        val mins = intent.getIntExtra("mins", 0)

        TerminalLogger.log("ALARM: Waking up for reminder: $title")
        
        // Mark as fired to prevent same-minute re-triggering
        FiredEventsCache.markAsFired(context, id)

        // Acquire WakeLock (Force Screen Wake)
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "Reality:ReminderWakeLock"
        )
        wakeLock.acquire(10_000L) // 10s to ensure service starts

        // Extract snooze settings from intent (snapshotted from reminder)
        val snoozeEnabled = intent.getBooleanExtra("snoozeEnabled", true)
        val snoozeIntervalMins = intent.getIntExtra("snoozeIntervalMins", 5)
        val autoSnoozeEnabled = intent.getBooleanExtra("autoSnoozeEnabled", true)
        val autoSnoozeTimeoutSecs = intent.getIntExtra("autoSnoozeTimeoutSecs", 30)
        val source = intent.getStringExtra("source") ?: "MANUAL"

        // Start Alarm Service (Foreground - Guaranteed Sound)
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("id", id)
            putExtra("title", title)
            putExtra("url", url)
            putExtra("mins", mins)
            putExtra("source", source)  // Pass source for proper dismissal handling
            putExtra("snoozeEnabled", snoozeEnabled)
            putExtra("snoozeIntervalMins", snoozeIntervalMins)
            putExtra("autoSnoozeEnabled", autoSnoozeEnabled)
            putExtra("autoSnoozeTimeoutSecs", autoSnoozeTimeoutSecs)
        }
        
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            TerminalLogger.log("ERROR starting AlarmService: ${e.message}")
            e.printStackTrace()
        }
        
        // Reschedule for next reminder after this one fires
        try {
            AlarmScheduler.scheduleNextAlarm(context)
        } catch(e: Exception) {
            TerminalLogger.log("ERROR rescheduling: ${e.message}")
            e.printStackTrace()
        }
    }
    
    private fun handleDismiss(context: Context, intent: Intent) {
         // Stop Sound
         val stopIntent = Intent(context, AlarmService::class.java).apply {
             action = "STOP"
         }
         context.startService(stopIntent)
         
         val isUserExplicitDismiss = intent.getBooleanExtra("explicit_dismiss", false)
         val id = intent.getStringExtra("id") ?: return
         val sourceName = intent.getStringExtra("source") ?: "MANUAL"
         val isSnooze = intent.getBooleanExtra("isSnooze", false) || id.startsWith("snooze_")
         
         // Get original ID (strip snooze_ prefix if present)
         val originalId = intent.getStringExtra("originalId") 
             ?: if (id.startsWith("snooze_")) id.removePrefix("snooze_") else id
         
         if (isUserExplicitDismiss) {
             TerminalLogger.log("ALARM: Explicit Dismiss -> Marking as done for today")
             
             // If this was a snooze, cancel its alarm
             if (isSnooze) {
                 AlarmScheduler.cancelSnooze(context, originalId)
             }
             
             // Mark as dismissed in DB/Prefs using ORIGINAL ID
             try {
                 val source = com.neubofy.reality.data.EventSource.valueOf(sourceName)
                 com.neubofy.reality.data.ScheduleManager.markAsDismissed(context, originalId, source)
             } catch (e: Exception) {
                 TerminalLogger.log("ALARM ERROR: Could not mark dismissed - ${e.message}")
             }
         } else {
             // Swipe or Auto-timeout -> Auto Snooze (Safety Net)
             TerminalLogger.log("ALARM: Notification Dismissed (Swipe) -> Auto-Snoozing...")
             
             val autoSnoozeEnabled = intent.getBooleanExtra("autoSnoozeEnabled", true)
             if (autoSnoozeEnabled) {
                 val title = intent.getStringExtra("title") ?: "Reminder"
                 val url = intent.getStringExtra("url")
                 val snoozeIntervalMins = intent.getIntExtra("snoozeIntervalMins", 5)
                 
                 // Pass source for proper dismissal handling later
                 AlarmScheduler.scheduleSnooze(context, originalId, title, url, snoozeIntervalMins, sourceName)
             }
         }
    }
}

