package com.neubofy.reality.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.neubofy.reality.utils.TerminalLogger
import android.os.PowerManager
import com.neubofy.reality.services.AlarmService
import com.neubofy.reality.utils.AlarmScheduler

class ReminderReceiver : BroadcastReceiver() {

    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent?) {
        // 1. Handle Dismiss Action
        if (intent?.action == "ACTION_DISMISS") {
            handleDismiss(context, intent)
            return
        }

        // 2. Handle Reminder Alarm Check (Main Logic)
        val id = intent?.getStringExtra("id") ?: return
        val title = intent.getStringExtra("title") ?: "Reminder"
        val url = intent.getStringExtra("url")
        val mins = intent.getIntExtra("mins", 0)

        TerminalLogger.log("ALARM: Waking up for reminder: $title")

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

        // Start Alarm Service (Foreground - Guaranteed Sound)
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            putExtra("id", id)
            putExtra("title", title)
            putExtra("url", url)
            putExtra("mins", mins)
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
         TerminalLogger.log("ALARM: Notification Dismissed -> Auto-Snoozing...")
         // 1. Stop Sound
         val stopIntent = Intent(context, AlarmService::class.java).apply {
             action = "STOP"
         }
         context.startService(stopIntent)
         
         // 2. Schedule Snooze (Auto Snooze Logic)
         val autoSnoozeEnabled = intent.getBooleanExtra("autoSnoozeEnabled", true)
         if (autoSnoozeEnabled) {
             val title = intent.getStringExtra("title") ?: "Reminder"
             val id = intent.getStringExtra("id") ?: "0"
             val url = intent.getStringExtra("url")
             val snoozeIntervalMins = intent.getIntExtra("snoozeIntervalMins", 5)
             
             AlarmScheduler.scheduleSnooze(context, id, title, url, snoozeIntervalMins)
         }
    }
}
