package com.neubofy.reality.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import android.content.Intent
import com.neubofy.reality.utils.TerminalLogger

/**
 * HeartbeatWorker: The separate "Heartbeat" system.
 * Runs every 15 minutes given by WorkManager constraints.
 * Triggers Calendar Sync (if enabled) and Refreshes the Blocker "Box".
 */
class HeartbeatWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        TerminalLogger.log("HEARTBEAT: Pulse Received.")

        // 1. Trigger Blocker Refresh (The Box Update)
        // We broadcast to AppBlockerService to perform its background updates
        val intent = Intent("com.neubofy.reality.refresh.focus_mode")
        intent.setPackage(applicationContext.packageName)
        applicationContext.sendBroadcast(intent)
        
        // 2. Trigger Calendar Sync (If Enabled)
        val prefs = applicationContext.getSharedPreferences("reality_prefs", Context.MODE_PRIVATE)
        val isAutoSyncEnabled = prefs.getBoolean("calendar_sync_auto_enabled", true)
        
        if (isAutoSyncEnabled) {
             TerminalLogger.log("HEARTBEAT: Triggering Auto-Sync...")
             // We can chain the worker or just run it via OneTimeRequest
             val syncRequest = androidx.work.OneTimeWorkRequestBuilder<CalendarSyncWorker>()
                 .build()
             WorkManager.getInstance(applicationContext).enqueue(syncRequest)
        } else {
             TerminalLogger.log("HEARTBEAT: Auto-Sync is Disabled. Skipping.")
        }

        return Result.success()
    }
    
    companion object {
        private const val WORK_NAME = "reality_heartbeat"
        
        fun startHeartbeat(context: Context) {
            val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(60, TimeUnit.MINUTES)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            TerminalLogger.log("HEARTBEAT: System Initialized (60m Interval)")
        }
    }
}
