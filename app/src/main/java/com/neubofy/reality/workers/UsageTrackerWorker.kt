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
            return Result.success()
        } catch (e: Exception) {
            TerminalLogger.log("TRACKER ERROR: ${e.message}")
            return Result.retry()
        }
    }
}
