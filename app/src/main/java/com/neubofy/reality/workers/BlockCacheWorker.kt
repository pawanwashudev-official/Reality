package com.neubofy.reality.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.neubofy.reality.utils.BlockCache
import com.neubofy.reality.utils.TerminalLogger

/**
 * Updates BlockCache every 3 minutes.
 * This moves expensive calculations OFF the main accessibility event loop.
 */
class BlockCacheWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    override suspend fun doWork(): Result {
        return try {
            BlockCache.rebuildBox(applicationContext)
            Result.success()
        } catch (e: Exception) {
            TerminalLogger.log("WORKER ERROR: BlockCache update failed: ${e.message}")
            Result.retry()
        }
    }
}
