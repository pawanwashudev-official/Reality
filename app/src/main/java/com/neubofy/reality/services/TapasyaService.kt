package com.neubofy.reality.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.neubofy.reality.R
import com.neubofy.reality.blockers.RealityBlocker
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.data.db.TapasyaSession
import com.neubofy.reality.ui.activity.TapasyaActivity
import com.neubofy.reality.utils.SavedPreferencesLoader
import com.neubofy.reality.utils.XPManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TapasyaService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var timerJob: Job? = null

    // Core State
    private var sessionStartTime = 0L       // When session started
    private var runningStartTime = 0L       // When current running segment started
    private var elapsedRunningTime = 0L     // Cumulative running time
    private var pauseStartTime = 0L         // When current pause started
    private var totalPauseTime = 0L         // Cumulative pause time
    
    // Config (set at session start)
    private var sessionName = "Tapasya"
    private var targetTimeMs = 60 * 60 * 1000L  // Default 60 min
    private var pauseLimitMs = 15 * 60 * 1000L  // Default 15 min
    
    // State flags
    private var isRunning = false
    private var isPaused = false
    private var isSessionActive = false
    
    // XP tracking
    private var lastCompletedFragment = 0
    private var warningPlayed = false // For pause warning

    private val PREFS_NAME = "tapasya_service_prefs"
    
    companion object {
        const val CHANNEL_ID = "tapasya_channel"
        const val NOTIFICATION_ID = 2001
        
        // Actions
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RESET = "ACTION_RESET"
        const val ACTION_UPDATE_START_TIME = "ACTION_UPDATE_START_TIME"
        
        // Extras
        const val EXTRA_SESSION_NAME = "session_name"
        const val EXTRA_TARGET_TIME_MS = "target_time_ms"
        const val EXTRA_PAUSE_LIMIT_MS = "pause_limit_ms"
        const val EXTRA_NEW_START_TIME = "new_start_time"
        
        // Shared State for UI binding
        private val _clockState = MutableStateFlow(ClockState())
        val clockState = _clockState.asStateFlow()
    }

    data class ClockState(
        val isSessionActive: Boolean = false,
        val isRunning: Boolean = false,
        val isPaused: Boolean = false,
        val elapsedTimeMs: Long = 0L,
        val totalPauseMs: Long = 0L,
        val targetTimeMs: Long = 0L,
        val pauseLimitMs: Long = 0L,
        val sessionName: String = "Tapasya",
        val progress: Float = 0f,  // 0 to 1+ for UI
        val currentXP: Int = 0,    // Live Tapasya XP
        val currentFragment: Int = 0  // Current fragment number
    )

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        restoreState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val name = intent.getStringExtra(EXTRA_SESSION_NAME) ?: "Tapasya"
                val target = intent.getLongExtra(EXTRA_TARGET_TIME_MS, 60 * 60 * 1000L)
                val pauseLimit = intent.getLongExtra(EXTRA_PAUSE_LIMIT_MS, 15 * 60 * 1000L)
                startSession(name, target, pauseLimit)
            }
            ACTION_PAUSE -> pauseSession()
            ACTION_RESUME -> resumeSession()
            ACTION_STOP -> stopSession(wasAutoStopped = false)
            ACTION_RESET -> resetSession()
            ACTION_UPDATE_START_TIME -> {
                val newStart = intent.getLongExtra(EXTRA_NEW_START_TIME, -1L)
                if (newStart != -1L) updateStartTime(newStart)
            }
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null

    private fun startSession(name: String, target: Long, pauseLimit: Long) {
        if (isSessionActive) return  // Single session rule
        
        sessionName = name
        targetTimeMs = target
        pauseLimitMs = pauseLimit
        
        sessionStartTime = System.currentTimeMillis() // Keep this for display/DB (Calendar time)
        runningStartTime = android.os.SystemClock.elapsedRealtime() // Use monotonic clock for duration
        elapsedRunningTime = 0L
        totalPauseTime = 0L
        
        isSessionActive = true
        isRunning = true
        isPaused = false
        lastCompletedFragment = 0
        warningPlayed = false
        
        // Start Focus Mode
        startFocusMode()
        
        saveState() // Persist
        startForeground(NOTIFICATION_ID, buildNotification("Focusing: $sessionName", "00:00:00"))
        startTimerLoop()
        emitState()
    }

    private fun pauseSession() {
        if (!isRunning || !isSessionActive) return
        
        // Save elapsed running time
        elapsedRunningTime += android.os.SystemClock.elapsedRealtime() - runningStartTime
        pauseStartTime = android.os.SystemClock.elapsedRealtime()
        
        isRunning = false
        isPaused = true
        
        saveState() // Persist
        updateNotification("Paused: $sessionName", formatTime(elapsedRunningTime))
        emitState()
    }

    private fun resumeSession() {
        if (!isPaused || !isSessionActive) return
        
        // Add pause duration to total
        totalPauseTime += android.os.SystemClock.elapsedRealtime() - pauseStartTime
        
        // Check if exceeded pause limit
        if (totalPauseTime >= pauseLimitMs) {
            stopSession(wasAutoStopped = true)
            return
        }
        
        runningStartTime = android.os.SystemClock.elapsedRealtime()
        isRunning = true
        isPaused = false
        warningPlayed = false // Reset warning
        
        saveState() // Persist
        updateNotification("Focusing: $sessionName", formatTime(elapsedRunningTime))
        emitState()
    }

    private fun stopSession(wasAutoStopped: Boolean) {
        if (!isSessionActive) return
        
        // Finalize elapsed time
        if (isRunning) {
            elapsedRunningTime += android.os.SystemClock.elapsedRealtime() - runningStartTime
        } else if (isPaused) {
            totalPauseTime += android.os.SystemClock.elapsedRealtime() - pauseStartTime
        }
        
        val endTime = System.currentTimeMillis()
        val effectiveTime = TapasyaSession.calculateEffectiveTime(elapsedRunningTime)
        
        // Save session to database
        saveSession(endTime, effectiveTime, wasAutoStopped)
        
        // Stop Focus Mode
        stopFocusMode()
        
        // Reset state
        isSessionActive = false
        isRunning = false
        isPaused = false
        elapsedRunningTime = 0L
        totalPauseTime = 0L
        
        clearState() // Clear Persistence
        
        timerJob?.cancel()
        _clockState.value = ClockState()
        
        stopForeground(true)
        
        // Final Summary Notification
        val finalTitle = "Session Complete: $sessionName"
        val finalMsg = "Effective: ${formatTime(effectiveTime)} / Total: ${formatTime(elapsedRunningTime)}"
        com.neubofy.reality.utils.NotificationHelper.showNotification(this, finalTitle, finalMsg, NOTIFICATION_ID + 1)
        
        stopSelf()
    }

    private fun resetSession() {
        if (!isSessionActive) return

        // Stop Focus Mode immediately
        stopFocusMode()
        
        // Reset all state
        isSessionActive = false
        isRunning = false
        isPaused = false
        elapsedRunningTime = 0L
        totalPauseTime = 0L
        
        clearState() // Clear Persistence
        
        // Cancel timer
        timerJob?.cancel()
        _clockState.value = ClockState()
        
        // Stop Service
        stopForeground(true)
        stopSelf()
    }

    private fun saveSession(endTime: Long, effectiveTimeMs: Long, wasAutoStopped: Boolean) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val db = AppDatabase.getDatabase(this@TapasyaService)
                val session = TapasyaSession(
                    sessionId = TapasyaSession.generateId(sessionStartTime, endTime),
                    name = sessionName,
                    targetTimeMs = targetTimeMs,
                    startTime = sessionStartTime,
                    endTime = endTime,
                    effectiveTimeMs = effectiveTimeMs,
                    totalPauseMs = totalPauseTime,
                    pauseLimitMs = pauseLimitMs,
                    wasAutoStopped = wasAutoStopped
                )
                db.tapasyaSessionDao().insert(session)
                
                // Ring Buffer: Fixed 7-day retention for Tapasya sessions
                val cutoff = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
                db.tapasyaSessionDao().deleteOldSessions(cutoff)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateStartTime(newStartTime: Long) {
        if (!isSessionActive) return
        
        // Calculate delta (Positive if moving start time back/earlier, Negative if forward/later)
        val delta = sessionStartTime - newStartTime
        
        sessionStartTime = newStartTime
        
        // Adjust elapsed calculation based on new start time
        if (isRunning) {
            // Shift the running start time window
            runningStartTime -= delta
        } else if (isPaused) {
            // Apply delta directly to accumulated elapsed time
            elapsedRunningTime += delta
        }
        
        // Ensure elapsed time is not negative
        if (elapsedRunningTime < 0) elapsedRunningTime = 0
        
        // Update notification immediately
        emitState()
        saveState() // Persist change
    }

    private fun startFocusMode() {
        val prefs = SavedPreferencesLoader(this)
        val data = prefs.getFocusModeData()
        data.isTurnedOn = true
        // User requested ~6 hours max duration to avoid infinite issues
        data.endTime = System.currentTimeMillis() + (6 * 60 * 60 * 1000L)
        data.isTapasyaTriggered = true  // Mark as Tapasya-controlled
        prefs.saveFocusModeData(data)
        
        // Notify AppBlockerService
        sendBroadcast(Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE).apply {
            setPackage(packageName)
        })
    }

    private fun stopFocusMode() {
        val prefs = SavedPreferencesLoader(this)
        val data = prefs.getFocusModeData()
        data.isTurnedOn = false
        data.isTapasyaTriggered = false  // Clear Tapasya flag
        prefs.saveFocusModeData(data)
        
        // Notify AppBlockerService
        sendBroadcast(Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE).apply {
            setPackage(packageName)
        })
    }

    private fun saveState() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit()
            .putBoolean("is_active", true)
            .putLong("session_start", sessionStartTime)
            .putLong("running_start", runningStartTime)
            .putLong("elapsed_running", elapsedRunningTime)
            .putLong("pause_start", pauseStartTime)
            .putLong("total_pause", totalPauseTime)
            .putString("session_name", sessionName)
            .putLong("target_time", targetTimeMs)
            .putLong("pause_limit", pauseLimitMs)
            .putBoolean("is_running", isRunning)
            .putBoolean("is_paused", isPaused)
            .putInt("last_completed_fragment", lastCompletedFragment)
            .apply()
    }
    
    private fun restoreState() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (!prefs.getBoolean("is_active", false)) return

        sessionStartTime = prefs.getLong("session_start", 0L)
        runningStartTime = prefs.getLong("running_start", 0L)
        elapsedRunningTime = prefs.getLong("elapsed_running", 0L)
        pauseStartTime = prefs.getLong("pause_start", 0L)
        totalPauseTime = prefs.getLong("total_pause", 0L)
        sessionName = prefs.getString("session_name", "Tapasya") ?: "Tapasya"
        targetTimeMs = prefs.getLong("target_time", 60 * 60 * 1000L)
        pauseLimitMs = prefs.getLong("pause_limit", 15 * 60 * 1000L)
        isRunning = prefs.getBoolean("is_running", false)
        isPaused = prefs.getBoolean("is_paused", false)
        lastCompletedFragment = prefs.getInt("last_completed_fragment", 0)
        
        isSessionActive = true
        warningPlayed = false // Reset warning on restore to be safe

        // Restart Loop if needed
        startTimerLoop()
        
        // Re-show notification
        val text = if (isRunning) formatTime(elapsedRunningTime) else "Paused"
        startForeground(NOTIFICATION_ID, buildNotification(if (isPaused) "Paused: $sessionName" else "Focusing: $sessionName", text))
        emitState()
    }
    
    private fun clearState() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().clear().apply()
    }

    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive && isSessionActive) {
                val now = android.os.SystemClock.elapsedRealtime()
                
                val currentElapsed = if (isRunning) {
                    elapsedRunningTime + (now - runningStartTime)
                } else {
                    elapsedRunningTime
                }
                
                val currentPause = if (isPaused) {
                    totalPauseTime + (now - pauseStartTime)
                } else {
                    totalPauseTime
                }
                
                // Check pause limit auto-stop & Warning
                if (isPaused) {
                    val remainingPause = pauseLimitMs - currentPause
                    
                    if (remainingPause <= 10000 && remainingPause > 0 && !warningPlayed) {
                        try {
                            val notification = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
                            val r = android.media.RingtoneManager.getRingtone(applicationContext, notification)
                            r.play()
                            warningPlayed = true
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    
                    if (currentPause >= pauseLimitMs) {
                        // Play stop sound? Maybe later.
                        withContext(Dispatchers.Main) {
                            stopSession(wasAutoStopped = true)
                        }
                        return@launch
                    }
                }
                
                // Calculate effective time and check for new fragment completion
                val effectiveMinutes = (com.neubofy.reality.data.db.TapasyaSession.calculateEffectiveTime(currentElapsed) / 60000).toInt()
                val currentFragment = effectiveMinutes / 15
                
                // If a new fragment completed, add XP
                if (currentFragment > lastCompletedFragment) {
                    val currentTotalXP = XPManager.calculateTapasyaXP(currentFragment * 15)
                    val lastTotalXP = XPManager.calculateTapasyaXP(lastCompletedFragment * 15)
                    val deltaXP = currentTotalXP - lastTotalXP
                    
                    if (deltaXP > 0) {
                        XPManager.addTapasyaXP(this@TapasyaService, deltaXP)
                    }
                    lastCompletedFragment = currentFragment
                    saveState() // Save XP state
                }
                
                // Update notification
                if (isRunning) {
                    updateNotification("Focusing: $sessionName", formatTime(currentElapsed))
                } else if (isPaused) {
                    val remaining = pauseLimitMs - currentPause
                    updateNotification("Paused: $sessionName", "Pause left: ${formatTime(remaining)}")
                }
                
                // Emit state for UI
                val progress = if (targetTimeMs > 0) currentElapsed.toFloat() / targetTimeMs else 0f
                val currentXP = XPManager.calculateTapasyaXP(currentFragment * 15)
                _clockState.value = ClockState(
                    isSessionActive = true,
                    isRunning = isRunning,
                    isPaused = isPaused,
                    elapsedTimeMs = currentElapsed,
                    totalPauseMs = currentPause,
                    targetTimeMs = targetTimeMs,
                    pauseLimitMs = pauseLimitMs,
                    sessionName = sessionName,
                    progress = progress,
                    currentXP = currentXP,
                    currentFragment = currentFragment
                )
                
                delay(500)
            }
        }
    }

    private fun emitState() {
        val currentElapsed = if (isRunning) {
            elapsedRunningTime + (System.currentTimeMillis() - runningStartTime)
        } else {
            elapsedRunningTime
        }
        val progress = if (targetTimeMs > 0) currentElapsed.toFloat() / targetTimeMs else 0f
        val effectiveMinutes = (com.neubofy.reality.data.db.TapasyaSession.calculateEffectiveTime(currentElapsed) / 60000).toInt()
        val currentFragment = effectiveMinutes / 15
        val currentXP = XPManager.calculateTapasyaXP(currentFragment * 15)
        
        _clockState.value = ClockState(
            isSessionActive = isSessionActive,
            isRunning = isRunning,
            isPaused = isPaused,
            elapsedTimeMs = currentElapsed,
            totalPauseMs = totalPauseTime,
            targetTimeMs = targetTimeMs,
            pauseLimitMs = pauseLimitMs,
            sessionName = sessionName,
            progress = progress,
            currentXP = currentXP,
            currentFragment = currentFragment
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Tapasya Clock",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(title: String, text: String): android.app.Notification {
        val intent = Intent(this, TapasyaActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.baseline_timer_24)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(title, text))
    }

    private fun formatTime(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / (1000 * 60)) % 60
        val hours = (ms / (1000 * 60 * 60))
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
        serviceScope.cancel()
    }
}
