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

    companion object {
        const val CHANNEL_ID = "tapasya_channel"
        const val NOTIFICATION_ID = 2001
        
        // Actions
        const val ACTION_START = "ACTION_START"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_RESUME = "ACTION_RESUME"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_RESET = "ACTION_RESET"
        
        // Extras
        const val EXTRA_SESSION_NAME = "session_name"
        const val EXTRA_TARGET_TIME_MS = "target_time_ms"
        const val EXTRA_PAUSE_LIMIT_MS = "pause_limit_ms"
        
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
        val progress: Float = 0f  // 0 to 1+ for UI
    )

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
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
        }
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null

    private fun startSession(name: String, target: Long, pauseLimit: Long) {
        if (isSessionActive) return  // Single session rule
        
        sessionName = name
        targetTimeMs = target
        pauseLimitMs = pauseLimit
        
        sessionStartTime = System.currentTimeMillis()
        runningStartTime = sessionStartTime
        elapsedRunningTime = 0L
        totalPauseTime = 0L
        
        isSessionActive = true
        isRunning = true
        isPaused = false
        
        // Start Focus Mode
        startFocusMode()
        
        startForeground(NOTIFICATION_ID, buildNotification("Focusing: $sessionName", "00:00:00"))
        startTimerLoop()
        emitState()
    }

    private fun pauseSession() {
        if (!isRunning || !isSessionActive) return
        
        // Save elapsed running time
        elapsedRunningTime += System.currentTimeMillis() - runningStartTime
        pauseStartTime = System.currentTimeMillis()
        
        isRunning = false
        isPaused = true
        
        updateNotification("Paused: $sessionName", formatTime(elapsedRunningTime))
        emitState()
    }

    private fun resumeSession() {
        if (!isPaused || !isSessionActive) return
        
        // Add pause duration to total
        totalPauseTime += System.currentTimeMillis() - pauseStartTime
        
        // Check if exceeded pause limit
        if (totalPauseTime >= pauseLimitMs) {
            stopSession(wasAutoStopped = true)
            return
        }
        
        runningStartTime = System.currentTimeMillis()
        isRunning = true
        isPaused = false
        
        updateNotification("Focusing: $sessionName", formatTime(elapsedRunningTime))
        emitState()
    }

    private fun stopSession(wasAutoStopped: Boolean) {
        if (!isSessionActive) return
        
        // Finalize elapsed time
        if (isRunning) {
            elapsedRunningTime += System.currentTimeMillis() - runningStartTime
        } else if (isPaused) {
            totalPauseTime += System.currentTimeMillis() - pauseStartTime
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
        
        timerJob?.cancel()
        _clockState.value = ClockState()
        
        stopForeground(true)
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
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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

    private fun startTimerLoop() {
        timerJob?.cancel()
        timerJob = serviceScope.launch {
            while (isActive && isSessionActive) {
                val currentElapsed = if (isRunning) {
                    elapsedRunningTime + (System.currentTimeMillis() - runningStartTime)
                } else {
                    elapsedRunningTime
                }
                
                val currentPause = if (isPaused) {
                    totalPauseTime + (System.currentTimeMillis() - pauseStartTime)
                } else {
                    totalPauseTime
                }
                
                // Check pause limit auto-stop
                if (isPaused && currentPause >= pauseLimitMs) {
                    withContext(Dispatchers.Main) {
                        stopSession(wasAutoStopped = true)
                    }
                    return@launch
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
                _clockState.value = ClockState(
                    isSessionActive = true,
                    isRunning = isRunning,
                    isPaused = isPaused,
                    elapsedTimeMs = currentElapsed,
                    totalPauseMs = currentPause,
                    targetTimeMs = targetTimeMs,
                    pauseLimitMs = pauseLimitMs,
                    sessionName = sessionName,
                    progress = progress
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
        
        _clockState.value = ClockState(
            isSessionActive = isSessionActive,
            isRunning = isRunning,
            isPaused = isPaused,
            elapsedTimeMs = currentElapsed,
            totalPauseMs = totalPauseTime,
            targetTimeMs = targetTimeMs,
            pauseLimitMs = pauseLimitMs,
            sessionName = sessionName,
            progress = progress
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
