package com.neubofy.reality.services

import android.app.NotificationManager
import android.content.Context
import com.neubofy.reality.utils.BlockCache
import com.neubofy.reality.utils.SavedPreferencesLoader
import com.neubofy.reality.utils.TerminalLogger
import com.neubofy.reality.utils.ZenModeManager

class SystemStateManager(private val context: Context) {
    private val savedPreferencesLoader = SavedPreferencesLoader(context)
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun setWasDndEnabledByApp(enabled: Boolean) {
        val prefs = context.getSharedPreferences("system_state_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("was_dnd_enabled_by_app", enabled).apply()
    }

    fun getWasDndEnabledByApp(): Boolean {
        val prefs = context.getSharedPreferences("system_state_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("was_dnd_enabled_by_app", false)
    }

    fun setWasSleepEnabledByApp(enabled: Boolean) {
        val prefs = context.getSharedPreferences("system_state_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("was_sleep_enabled_by_app", enabled).apply()
    }

    fun getWasSleepEnabledByApp(): Boolean {
        val prefs = context.getSharedPreferences("system_state_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean("was_sleep_enabled_by_app", false)
    }

    fun toggleDnd(enable: Boolean) {
        try {
            if (notificationManager.isNotificationPolicyAccessGranted) {
                if (enable) {
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    TerminalLogger.log("DND: Enabled (Mode Active)")
                } else {
                    notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                    TerminalLogger.log("DND: Disabled (Mode Ended)")
                }
            }
        } catch (e: Exception) {
            TerminalLogger.log("DND Sync Error: ${e.message}")
        }
    }

    fun syncDndState(isAnyModeActive: Boolean) {
        try {
            if (savedPreferencesLoader.isAutoDndEnabled()) {
                val currentFilter = notificationManager.currentInterruptionFilter
                val isDndOn = currentFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY || 
                              currentFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS || 
                              currentFilter == NotificationManager.INTERRUPTION_FILTER_NONE
                
                if (isAnyModeActive && !isDndOn) {
                    toggleDnd(true)
                    setWasDndEnabledByApp(true)
                } else if (!isAnyModeActive && getWasDndEnabledByApp()) {
                    toggleDnd(false)
                    setWasDndEnabledByApp(false)
                }
            }
        } catch (e: Exception) {
            TerminalLogger.log("DND Sync Error: ${e.message}")
        }
    }

    fun syncSleepModeState() {
        try {
            if (savedPreferencesLoader.isRealitySleepEnabled() && ZenModeManager.isSupported()) {
                val isBedtime = BlockCache.isBedtimeCurrentlyActive
                if (isBedtime) {
                    if (!getWasSleepEnabledByApp()) {
                        ZenModeManager.setZenState(context, true)
                        setWasSleepEnabledByApp(true)
                    }
                } else {
                    if (getWasSleepEnabledByApp()) {
                        ZenModeManager.setZenState(context, false)
                        setWasSleepEnabledByApp(false)
                    }
                }
            }
        } catch (e: Exception) {
            TerminalLogger.log("Sleep Mode Sync Error: ${e.message}")
        }
    }

    fun forceSleepModeEnforcement() {
        try {
            if (ZenModeManager.isSupported() && savedPreferencesLoader.isRealitySleepEnabled()) {
                val isBedtime = BlockCache.isBedtimeCurrentlyActive
                if (isBedtime) {
                    ZenModeManager.setZenState(context, true)
                    setWasSleepEnabledByApp(true)
                    TerminalLogger.log("BYPASS CHECK: Sleep mode forced ON (bedtime active)")
                }
            }
        } catch (e: Exception) {
            TerminalLogger.log("Bypass Check Error: ${e.message}")
        }
    }
}
