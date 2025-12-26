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
            
            // 2. Start Service directly if possible (Foreground Service start)
            // Note: AccessibilityService cannot be started directly via startService
            // It must be enabled by user. But we can start GeneralFeaturesService?
            // Actually, we rely on the Worker to poke the AccessibilityService.
        }
    }
}
