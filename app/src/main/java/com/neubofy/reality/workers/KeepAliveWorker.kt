package com.neubofy.reality.workers

import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.neubofy.reality.services.AppBlockerService

class KeepAliveWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        try {
            // Log Heartbeat
            com.neubofy.reality.utils.TerminalLogger.log("SYSTEM: KeepAlive Heartbeat")

            // Send explicit broadcast to wake up the service logic
            val intent = Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE)
            intent.setPackage(applicationContext.packageName)
            applicationContext.sendBroadcast(intent)
            
            return Result.success()
        } catch (e: Exception) {
            return Result.failure()
        }
    }
}
