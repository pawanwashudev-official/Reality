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
        TerminalLogger.log("HEARTBEAT: Pulse Received - Updating ALL Boxes...")

        try {
            // === UNIFIED BOX UPDATE QUEUE ===
            // All boxes get updated in one heartbeat, no separate timers
            
            // 1. Update BlockCache (App/Website blocking decisions)
            TerminalLogger.log("HEARTBEAT: Rebuilding BlockCache...")
            com.neubofy.reality.utils.BlockCache.rebuildBox(applicationContext)
            
            // 2. Update SettingsBox (Strict mode page/toggle blocking)
            TerminalLogger.log("HEARTBEAT: Rebuilding SettingsBox...")
            com.neubofy.reality.utils.SettingsBox.rebuildBox(applicationContext)
            
            // 3. Notify AppBlockerService to refresh its local state
            val intent = android.content.Intent("com.neubofy.reality.refresh.focus_mode")
            intent.setPackage(applicationContext.packageName)
            applicationContext.sendBroadcast(intent)
            
            // 4. Trigger Calendar Sync (If Enabled)
            val prefs = applicationContext.getSharedPreferences("reality_prefs", Context.MODE_PRIVATE)
            val isAutoSyncEnabled = prefs.getBoolean("calendar_sync_auto_enabled", true)
            
            if (isAutoSyncEnabled) {
                TerminalLogger.log("HEARTBEAT: Triggering Calendar Sync...")
                val syncRequest = androidx.work.OneTimeWorkRequestBuilder<CalendarSyncWorker>()
                    .build()
                WorkManager.getInstance(applicationContext).enqueue(syncRequest)
            }
            
            TerminalLogger.log("HEARTBEAT: All boxes updated successfully!")
            
        } catch (e: Exception) {
            TerminalLogger.log("HEARTBEAT ERROR: ${e.message}")
            return Result.retry()
        }

        return Result.success()
    }
    
    companion object {
        private const val WORK_NAME = "reality_heartbeat"
        
        fun startHeartbeat(context: Context) {
            val request = PeriodicWorkRequestBuilder<HeartbeatWorker>(15, TimeUnit.MINUTES)
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            TerminalLogger.log("HEARTBEAT: System Initialized (15m Interval)")
        }
    }
}
