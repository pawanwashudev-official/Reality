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
            
            // 1. Reschedule KeepAlive Worker immediately
            val request = androidx.work.PeriodicWorkRequest.Builder(
                com.neubofy.reality.workers.KeepAliveWorker::class.java,
                15,
                java.util.concurrent.TimeUnit.MINUTES
            ).build()

            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "RealityKeepAlive",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            
            // 2. Start Heartbeat (15 min pulse)
            com.neubofy.reality.workers.HeartbeatWorker.startHeartbeat(context)
            
            // 2. CRITICAL: Reschedule reminders (AlarmManager alarms are lost on reboot)
            try {
                com.neubofy.reality.utils.AlarmScheduler.scheduleNextAlarm(context)
                TerminalLogger.log("BOOT: Alarms rescheduled successfully")
            } catch (e: Exception) {
                TerminalLogger.log("BOOT ERROR: Failed to reschedule alarms: ${e.message}")
            }
            
            // Note: AccessibilityService cannot be started directly via startService
            // It must be enabled by user. But KeepAliveWorker will poke it.
        }
    }
}
