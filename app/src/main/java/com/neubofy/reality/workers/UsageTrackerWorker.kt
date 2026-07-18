package com.neubofy.reality.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neubofy.reality.utils.BlockCache
import com.neubofy.reality.utils.TerminalLogger

class UsageTrackerWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        try {
            TerminalLogger.log("TRACKER: Checking app usage limits...")
            BlockCache.rebuildBox(applicationContext)
            
            // Notify AppBlockerService to reload/refresh settings and block immediately
            val intent = android.content.Intent(com.neubofy.reality.services.AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE).apply {
                setPackage(applicationContext.packageName)
            }
            applicationContext.sendBroadcast(intent)
            
            return Result.success()
        } catch (e: Exception) {
            TerminalLogger.log("TRACKER ERROR: ${e.message}")
            return Result.retry()
        }
    }
}
