package com.neubofy.reality.utils

import android.content.Context
import android.content.SharedPreferences

/**
 * Transient cache to track recently fired events.
 * 
 * Purpose:
 * 1. Prevent same-minute re-firing of reminders
 * 2. Allow edited events to fire at new time (by clearing from cache)
 * 3. Auto-clear at midnight (day boundary)
 * 
 * Logic:
 * - When reminder fires → markAsFired(eventId)
 * - When scheduling → hasFiredRecently(eventId) returns true if <2 min ago
 * - When event edited → clearFired(eventId)
 * - At midnight → clearAll() (implicit via day check)
 */
object FiredEventsCache {
    
    private const val PREF_NAME = "fired_events_cache"
    private const val KEY_LAST_CLEAR_DATE = "last_clear_date"
    private const val REFIRING_BUFFER_MS = 2 * 60 * 1000L // 2 minutes
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * Check if it's a new day and clear the cache if so.
     */
    private fun clearIfNewDay(context: Context) {
        val prefs = getPrefs(context)
        val lastClearDate = prefs.getLong(KEY_LAST_CLEAR_DATE, 0L)
        val todayStart = getTodayStartMillis()
        
        if (lastClearDate < todayStart) {
            // New day - clear all
            prefs.edit().clear().putLong(KEY_LAST_CLEAR_DATE, System.currentTimeMillis()).apply()
            TerminalLogger.log("FIRED_CACHE: Cleared for new day")
        }
    }
    
    private fun getTodayStartMillis(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
    
    /**
     * Mark an event as fired NOW.
     * Called when a reminder actually fires (shows notification/popup).
     */
    fun markAsFired(context: Context, eventId: String) {
        clearIfNewDay(context)
        val prefs = getPrefs(context)
        prefs.edit().putLong(eventId, System.currentTimeMillis()).apply()
        TerminalLogger.log("FIRED_CACHE: Marked '$eventId' as fired")
    }
    
    /**
     * Check if event has fired recently (within buffer period).
     * Returns true if event should be SKIPPED to prevent re-firing.
     */
    fun hasFiredRecently(context: Context, eventId: String): Boolean {
        clearIfNewDay(context)
        val prefs = getPrefs(context)
        val firedTime = prefs.getLong(eventId, 0L)
        
        if (firedTime == 0L) {
            return false // Never fired today
        }
        
        val timeSinceFired = System.currentTimeMillis() - firedTime
        return timeSinceFired < REFIRING_BUFFER_MS
    }
    
    /**
     * Get the time since event last fired (in ms).
     * Returns -1 if never fired today.
     */
    fun getTimeSinceFired(context: Context, eventId: String): Long {
        val prefs = getPrefs(context)
        val firedTime = prefs.getLong(eventId, 0L)
        
        if (firedTime == 0L) {
            return -1
        }
        
        return System.currentTimeMillis() - firedTime
    }
    
    /**
     * Clear fired status for a specific event.
     * Called when event is EDITED (time changed), allowing it to fire again.
     */
    fun clearFired(context: Context, eventId: String) {
        val prefs = getPrefs(context)
        prefs.edit().remove(eventId).apply()
        TerminalLogger.log("FIRED_CACHE: Cleared '$eventId' (edited)")
    }
    
    /**
     * Clear all fired events.
     * Called at midnight or on demand.
     */
    fun clearAll(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().clear().putLong(KEY_LAST_CLEAR_DATE, System.currentTimeMillis()).apply()
        TerminalLogger.log("FIRED_CACHE: Cleared all")
    }
}
