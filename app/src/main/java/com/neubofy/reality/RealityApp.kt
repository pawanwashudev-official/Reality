package com.neubofy.reality

import android.app.Application
import com.google.android.material.color.DynamicColors
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import android.os.Bundle
import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks

class RealityApp: Application() {
  override fun onCreate() {
    // Apply Theme
    val prefs = com.neubofy.reality.utils.SavedPreferencesLoader(this)
    val themeMode = prefs.getThemeMode()
    val mode = when (themeMode) {
        1 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
        2 -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
        else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }
    androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(mode)

    DynamicColors.applyToActivitiesIfAvailable(this)
    Thread.setDefaultUncaughtExceptionHandler(CrashLogger(this))
    
    // Schedule BlockCacheWorker to run every 3 minutes
    scheduleBlockCacheWorker()
    
    // Schedule HeartbeatWorker (15 min system pulse)
    com.neubofy.reality.workers.HeartbeatWorker.startHeartbeat(this)
    
    super.onCreate()
    
    registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: android.app.Activity) {
            // Optimize for High Refresh Rate (120Hz/144Hz)
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    activity.display?.let { display ->
                        val modes = display.supportedModes
                        // Sort by refresh rate descending
                        val maxMode = modes.maxByOrNull { it.refreshRate }
                        if (maxMode != null && maxMode.refreshRate > 60f) {
                            val params = activity.window.attributes
                            params.preferredDisplayModeId = maxMode.modeId
                            activity.window.attributes = params
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore errors on unsupported devices
            }
        }
        override fun onActivityResumed(activity: android.app.Activity) {}
        override fun onActivityPaused(activity: android.app.Activity) {}
        override fun onActivityStopped(activity: android.app.Activity) {}
        override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: android.app.Activity) {}
    })
  }
  
  private fun scheduleBlockCacheWorker() {
    try {
      val cacheWorkRequest = PeriodicWorkRequestBuilder<com.neubofy.reality.workers.BlockCacheWorker>(
        3, TimeUnit.MINUTES
      ).build()
      
      WorkManager.getInstance(this).enqueueUniquePeriodicWork(
        "BlockCacheUpdate",
        ExistingPeriodicWorkPolicy.KEEP,
        cacheWorkRequest
      )
      
      com.neubofy.reality.utils.TerminalLogger.log("INIT: BlockCacheWorker scheduled")
    } catch (e: Exception) {
      com.neubofy.reality.utils.TerminalLogger.log("INIT ERROR: ${e.message}")
    }
  }
}
