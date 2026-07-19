package com.neubofy.reality.receivers

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.neubofy.reality.services.AppBlockerService
import com.neubofy.reality.ui.activity.MainActivity
import com.neubofy.reality.utils.TerminalLogger

class BootReceiver : BroadcastReceiver() {
    
    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            TerminalLogger.log("BOOT: Device restarted. Resurrecting Reality...")
            
            // Heartbeat Worker eliminated to save battery. Relying on Single-Intent alarm architecture.
            
            // 2. CRITICAL: Reschedule reminders (AlarmManager alarms are lost on reboot)
            try {
                com.neubofy.reality.utils.SmartScheduleManager.scheduleNextTransition(context)
                com.neubofy.reality.utils.BedtimeAlarmScheduler.scheduleNextBedtimeAlarm(context)
                TerminalLogger.log("BOOT: Alarms rescheduled successfully")
            } catch (e: Exception) {
                TerminalLogger.log("BOOT ERROR: Failed to reschedule alarms: ${e.message}")
            }
            
            // Note: AccessibilityService cannot be started directly via startService
            // It must be enabled by user. Event-driven triggers keep things running.
        }
    }
}
