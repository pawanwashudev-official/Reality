package com.neubofy.reality.services

import android.accessibilityservice.AccessibilityService
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import com.neubofy.reality.utils.SavedPreferencesLoader

open class BaseBlockingService : AccessibilityService() {
    val savedPreferencesLoader: SavedPreferencesLoader by lazy {
        SavedPreferencesLoader(this)
    }

    var lastBackPressTimeStamp: Long =
        SystemClock.uptimeMillis() // prevents repetitive global actions

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }


    fun isDelayOver(lastTimestamp: Long, delay: Int): Boolean {
        val currentTime = SystemClock.uptimeMillis().toFloat()
        return currentTime - lastTimestamp > delay
    }

    fun pressHome() {
            performGlobalAction(GLOBAL_ACTION_HOME)
            lastBackPressTimeStamp = SystemClock.uptimeMillis()
    }

    fun pressBack() {
            performGlobalAction(GLOBAL_ACTION_BACK)
            lastBackPressTimeStamp = SystemClock.uptimeMillis()
    }

    fun killApp(packageName: String) {
        try {
            val am = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            am.killBackgroundProcesses(packageName)
        } catch (e: Exception) {}
        pressHome()
    }
}
