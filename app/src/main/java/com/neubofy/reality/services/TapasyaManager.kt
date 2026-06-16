package com.neubofy.reality.services

import android.content.Context
import android.content.Intent
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.data.db.TapasyaSession
import com.neubofy.reality.utils.SavedPreferencesLoader
import com.neubofy.reality.utils.XPManager
import kotlinx.coroutines.*

/**
 * TapasyaManager — Zero-Background Tapasya Session Manager
 * 
 * Replaces TapasyaService (foreground service with 500ms timer loop).
 * All state is stored in SharedPrefs using wall-clock timestamps.
 * Timer calculation is on-demand: elapsed = now - runningStart + accumulated.
 * No background process, no notification, no timer loop.
 * 
 * UI-side: Activity calls getCurrentState() every 500ms while visible.
 */
object TapasyaManager {

    private const val PREFS_NAME = "tapasya_service_prefs"  // Same key as old service for migration
    
    // Keep same constants for backward compatibility with AmoledFocusActivity
    const val ACTION_START = "ACTION_START"
    const val ACTION_PAUSE = "ACTION_PAUSE"
    const val ACTION_RESUME = "ACTION_RESUME"
    const val ACTION_STOP = "ACTION_STOP"
    const val ACTION_RESET = "ACTION_RESET"
    const val ACTION_UPDATE_START_TIME = "ACTION_UPDATE_START_TIME"
    
    const val EXTRA_SESSION_NAME = "session_name"
    const val EXTRA_TARGET_TIME_MS = "target_time_ms"
    const val EXTRA_PAUSE_LIMIT_MS = "pause_limit_ms"
    const val EXTRA_NEW_START_TIME = "new_start_time"

    data class ClockState(
        val isSessionActive: Boolean = false,
        val isRunning: Boolean = false,
        val isPaused: Boolean = false,
        val elapsedTimeMs: Long = 0L,
        val totalPauseMs: Long = 0L,
        val targetTimeMs: Long = 0L,
        val pauseLimitMs: Long = 0L,
        val sessionName: String = "Tapasya",
        val progress: Float = 0f,
        val currentXP: Int = 0,
        val currentFragment: Int = 0
    )

    // ========================
    // SESSION CONTROL
    // ========================

    fun startSession(ctx: Context, name: String, targetMs: Long, pauseLimitMs: Long) {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean("is_active", false)) return  // Single session rule
        
        val now = System.currentTimeMillis()
        prefs.edit()
            .putBoolean("is_active", true)
            .putLong("session_start", now)
            .putLong("running_start", now)          // Wall-clock (not elapsedRealtime)
            .putLong("elapsed_running", 0L)
            .putLong("pause_start", 0L)
            .putLong("total_pause", 0L)
            .putString("session_name", name)
            .putLong("target_time", targetMs)
            .putLong("pause_limit", pauseLimitMs)
            .putBoolean("is_running", true)
            .putBoolean("is_paused", false)
            .putInt("last_completed_fragment", 0)
            .putLong("last_updated", now)
            .apply()
        
        // Enable focus mode
        startFocusMode(ctx)
    }

    fun pauseSession(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("is_running", false) || !prefs.getBoolean("is_active", false)) return
        
        val now = System.currentTimeMillis()
        val runningStart = prefs.getLong("running_start", now)
        val elapsed = prefs.getLong("elapsed_running", 0L) + (now - runningStart)
        
        prefs.edit()
            .putLong("elapsed_running", elapsed)
            .putLong("pause_start", now)
            .putBoolean("is_running", false)
            .putBoolean("is_paused", true)
            .putLong("last_updated", now)
            .apply()
    }

    fun resumeSession(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("is_paused", false) || !prefs.getBoolean("is_active", false)) return
        
        val now = System.currentTimeMillis()
        val pauseStart = prefs.getLong("pause_start", now)
        val totalPause = prefs.getLong("total_pause", 0L) + (now - pauseStart)
        val pauseLimit = prefs.getLong("pause_limit", 15 * 60 * 1000L)
        
        // Check if pause limit exceeded
        if (totalPause >= pauseLimit) {
            stopSession(ctx, wasAutoStopped = true)
            return
        }
        
        prefs.edit()
            .putLong("total_pause", totalPause)
            .putLong("running_start", now)
            .putBoolean("is_running", true)
            .putBoolean("is_paused", false)
            .putLong("last_updated", now)
            .apply()
    }

    fun stopSession(ctx: Context, wasAutoStopped: Boolean) {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("is_active", false)) return
        
        val now = System.currentTimeMillis()
        val isRunning = prefs.getBoolean("is_running", false)
        val isPaused = prefs.getBoolean("is_paused", false)
        var elapsed = prefs.getLong("elapsed_running", 0L)
        var totalPause = prefs.getLong("total_pause", 0L)
        val pauseLimit = prefs.getLong("pause_limit", 15 * 60 * 1000L)
        
        // Finalize elapsed time
        if (isRunning) {
            val runningStart = prefs.getLong("running_start", now)
            elapsed += (now - runningStart)
        } else if (isPaused) {
            val pauseStart = prefs.getLong("pause_start", now)
            totalPause += (now - pauseStart)
        }
        
        // Precise End Time Calculation for Auto-Stop
        // If auto-stopped, the 'actual' end time was the moment the pause limit was reached.
        val endTime = if (wasAutoStopped) {
            val pauseStartTime = prefs.getLong("pause_start", now)
            val totalPauseBeforeThis = prefs.getLong("total_pause", 0L)
            // EndTime = When this pause started + remaining pause budget at that time
            pauseStartTime + (pauseLimit - totalPauseBeforeThis)
        } else {
            now
        }
        
        val sessionStart = prefs.getLong("session_start", now)
        val sessionName = prefs.getString("session_name", "Tapasya") ?: "Tapasya"
        val targetTime = prefs.getLong("target_time", 60 * 60 * 1000L)
        val effectiveTime = TapasyaSession.calculateEffectiveTime(elapsed)
        
        // Save session to database
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(ctx)
                val session = TapasyaSession(
                    sessionId = TapasyaSession.generateId(sessionStart, endTime),
                    name = sessionName,
                    targetTimeMs = targetTime,
                    startTime = sessionStart,
                    endTime = endTime,
                    effectiveTimeMs = effectiveTime,
                    totalPauseMs = totalPause.coerceAtMost(pauseLimit),
                    pauseLimitMs = pauseLimit,
                    wasAutoStopped = wasAutoStopped
                )
                db.tapasyaSessionDao().insert(session)
                
                // Ring Buffer: 7-day retention
                val cutoff = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
                db.tapasyaSessionDao().deleteOldSessions(cutoff)
                
                // Award XP (one-time total calculation)
                val effectiveMinutes = (effectiveTime / 60000).toInt()
                val totalXP = XPManager.calculateTapasyaXP(effectiveMinutes)
                if (totalXP > 0) {
                    XPManager.addTapasyaXP(ctx, totalXP)
                }
                
                // Cloud Sync Disabled (Local Only)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Stop focus mode
        stopFocusMode(ctx)
        
        // Clear state
        prefs.edit().clear().apply()
        
        // Summary notification
        val title = "Session Complete: $sessionName"
        val msg = "Effective: ${formatTime(effectiveTime)} / Total: ${formatTime(elapsed)}"
        com.neubofy.reality.utils.NotificationHelper.showNotification(ctx, title, msg, 2002)
    }

    fun resetSession(ctx: Context) {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("is_active", false)) return
        
        stopFocusMode(ctx)
        prefs.edit().clear().apply()
    }

    fun updateStartTime(ctx: Context, newStartTime: Long) {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("is_active", false)) return
        
        val oldStart = prefs.getLong("session_start", 0L)
        val delta = oldStart - newStartTime // Positive = moved earlier
        val isRunning = prefs.getBoolean("is_running", false)
        val isPaused = prefs.getBoolean("is_paused", false)
        
        val editor = prefs.edit()
            .putLong("session_start", newStartTime)
            .putLong("last_updated", System.currentTimeMillis())
        
        if (isRunning) {
            // Shift running start window
            val runningStart = prefs.getLong("running_start", 0L)
            editor.putLong("running_start", runningStart - delta)
        } else if (isPaused) {
            // Apply delta to accumulated elapsed
            val elapsed = prefs.getLong("elapsed_running", 0L) + delta
            editor.putLong("elapsed_running", elapsed.coerceAtLeast(0L))
        }
        
        editor.apply()
    }

    // ========================
    // STATE QUERY
    // ========================

    /**
     * Calculate current state from SharedPrefs timestamps.
     * This is the key function — no service needed, just math.
     * Also handles auto-stop if pause limit exceeded.
     */
    fun getCurrentState(ctx: Context): ClockState {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        if (!prefs.getBoolean("is_active", false)) {
            return ClockState()  // No active session
        }
        
        val now = System.currentTimeMillis()
        val isRunning = prefs.getBoolean("is_running", false)
        val isPaused = prefs.getBoolean("is_paused", false)
        val sessionName = prefs.getString("session_name", "Tapasya") ?: "Tapasya"
        val targetTime = prefs.getLong("target_time", 60 * 60 * 1000L)
        val pauseLimit = prefs.getLong("pause_limit", 15 * 60 * 1000L)
        
        // Calculate elapsed running time
        val currentElapsed = if (isRunning) {
            val runningStart = prefs.getLong("running_start", now)
            prefs.getLong("elapsed_running", 0L) + (now - runningStart)
        } else {
            prefs.getLong("elapsed_running", 0L)
        }
        
        // Calculate total pause time
        val currentPause = if (isPaused) {
            val pauseStart = prefs.getLong("pause_start", now)
            prefs.getLong("total_pause", 0L) + (now - pauseStart)
        } else {
            prefs.getLong("total_pause", 0L)
        }
        
        // Auto-stop check: if paused and pause limit exceeded
        if (isPaused && currentPause >= pauseLimit) {
            stopSession(ctx, wasAutoStopped = true)
            return ClockState()  // Session ended
        }
        
        // Calculate XP and fragments
        val effectiveMinutes = (TapasyaSession.calculateEffectiveTime(currentElapsed) / 60000).toInt()
        val currentFragment = effectiveMinutes / 15
        val currentXP = XPManager.calculateTapasyaXP(currentFragment * 15)
        val progress = if (targetTime > 0) currentElapsed.toFloat() / targetTime else 0f
        
        return ClockState(
            isSessionActive = true,
            isRunning = isRunning,
            isPaused = isPaused,
            elapsedTimeMs = currentElapsed,
            totalPauseMs = currentPause,
            targetTimeMs = targetTime,
            pauseLimitMs = pauseLimit,
            sessionName = sessionName,
            progress = progress,
            currentXP = currentXP,
            currentFragment = currentFragment
        )
    }

    /**
     * Check if any session is currently active.
     * Lightweight check — only reads one boolean.
     */
    fun isSessionActive(ctx: Context): Boolean {
        return ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("is_active", false)
    }

    // ========================
    // FOCUS MODE
    // ========================

    private fun startFocusMode(ctx: Context) {
        val prefs = SavedPreferencesLoader(ctx)
        val data = prefs.getFocusModeData()
        data.isTurnedOn = true
        data.endTime = System.currentTimeMillis() + (6 * 60 * 60 * 1000L)
        data.isTapasyaTriggered = true
        prefs.saveFocusModeData(data)
        
        ctx.sendBroadcast(Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE).apply {
            setPackage(ctx.packageName)
        })
    }

    private fun stopFocusMode(ctx: Context) {
        val prefs = SavedPreferencesLoader(ctx)
        val data = prefs.getFocusModeData()
        data.isTurnedOn = false
        data.isTapasyaTriggered = false
        prefs.saveFocusModeData(data)
        
        ctx.sendBroadcast(Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE).apply {
            setPackage(ctx.packageName)
        })
    }

    // ========================
    // UTILITY
    // ========================

    fun formatTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = (ms / (1000 * 60 * 60))
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}
