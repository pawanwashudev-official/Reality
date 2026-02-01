package com.neubofy.reality.ui.activity

import com.neubofy.reality.ui.base.BaseActivity


import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.R
import kotlin.math.abs
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class AlarmActivity : BaseActivity() {


    private var isHandled = false
    private var autoSnoozeTimer: CountDownTimer? = null
    private var startX = 0f
    private lateinit var ivHandle: ImageView
    private var containerWidth = 0
    
    // Snooze settings
    private var snoozeEnabled = true
    private var snoozeIntervalMins = 5
    private var autoSnoozeEnabled = true
    private var autoSnoozeTimeoutSecs = 30
    private var source = "MANUAL"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Wake Screen & Show on Lock Screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        
        setContentView(R.layout.layout_reminder_alarm)
        
        // Load snooze settings from intent (snapshotted from reminder)
        snoozeEnabled = intent.getBooleanExtra("snoozeEnabled", true)
        snoozeIntervalMins = intent.getIntExtra("snoozeIntervalMins", 5)
        autoSnoozeEnabled = intent.getBooleanExtra("autoSnoozeEnabled", true)
        autoSnoozeTimeoutSecs = intent.getIntExtra("autoSnoozeTimeoutSecs", 30)
        
        // Get intent data
        val title = intent.getStringExtra("title") ?: "Reminder"
        val mins = intent.getIntExtra("mins", 0)
        val url = intent.getStringExtra("url")
        val id = intent.getStringExtra("id")
        source = intent.getStringExtra("source") ?: "MANUAL"

        // Setup UI
        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val tvTime = findViewById<TextView>(R.id.tvTime)
        val tvAutoSnooze = findViewById<TextView>(R.id.tvAutoSnooze)
        val tvSnoozeLabel = findViewById<TextView>(R.id.tvSnoozeLabel)
        ivHandle = findViewById(R.id.ivHandle)

        tvTitle.text = title
        tvTime.text = if (mins == 0) "Starting now!" else "Starts in $mins minute${if(mins!=1)"s" else ""}"
        
        // Hide snooze option if disabled
        if (!snoozeEnabled) {
            tvSnoozeLabel.visibility = View.INVISIBLE
        }

        // Setup handle touch listener
        setupTouchListener(url, id)

        // Start auto-snooze countdown if enabled
        if (snoozeEnabled && autoSnoozeEnabled) {
            tvAutoSnooze.visibility = View.VISIBLE
            startAutoSnoozeTimer(tvAutoSnooze, id, title, url, mins)
        }
    }
    
    private fun setupTouchListener(url: String?, id: String?) {
        ivHandle.post {
            containerWidth = ivHandle.parent?.let { (it as View).width } ?: 0
        }
        
        ivHandle.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - startX
                    val maxDrag = containerWidth / 2f - view.width / 2f
                    val newX = deltaX.coerceIn(-maxDrag, maxDrag)
                    view.translationX = newX
                    
                    // Visual feedback
                    val alpha = abs(newX) / maxDrag
                    if (newX < 0) {
                        // Moving left (Snooze)
                        findViewById<View>(R.id.viewSnoozeZone).alpha = 0.3f + alpha * 0.5f
                        findViewById<View>(R.id.viewDismissZone).alpha = 0.3f
                    } else {
                        // Moving right (Dismiss)
                        findViewById<View>(R.id.viewDismissZone).alpha = 0.3f + alpha * 0.5f
                        findViewById<View>(R.id.viewSnoozeZone).alpha = 0.3f
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val deltaX = event.rawX - startX
                    val threshold = containerWidth * 0.3f
                    
                    when {
                        deltaX < -threshold && snoozeEnabled -> {
                            // Snoozed
                            handleSnooze(id, intent.getStringExtra("title") ?: "Reminder", url, intent.getIntExtra("mins", 0))
                        }
                        deltaX > threshold -> {
                            // Dismissed
                            handleDismiss(url, id)
                        }
                        else -> {
                            // Reset to center
                            view.animate().translationX(0f).setDuration(150).start()
                            findViewById<View>(R.id.viewSnoozeZone).alpha = 0.3f
                            findViewById<View>(R.id.viewDismissZone).alpha = 0.3f
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun startAutoSnoozeTimer(tvAutoSnooze: TextView, id: String?, title: String, url: String?, mins: Int) {
        autoSnoozeTimer = object : CountDownTimer(autoSnoozeTimeoutSecs * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secs = millisUntilFinished / 1000
                tvAutoSnooze.text = "Auto-snooze in ${secs}s"
            }
            override fun onFinish() {
                if (!isHandled) {
                    handleSnooze(id, title, url, mins)
                }
            }
        }.start()
    }

    private fun handleSnooze(id: String?, title: String, url: String?, mins: Int) {
        if (isHandled) return
        isHandled = true
        
        autoSnoozeTimer?.cancel()
        stopAlarmService()
        
        // For nightly wakeup alarm, auto-confirm sleep if snoozed (user didn't interact)
        if (id == "nightly_wakeup" && url == "reality://sleep_verify") {
            MainScope().launch {
                com.neubofy.reality.utils.SleepInferenceHelper.autoConfirmSleep(applicationContext)
            }
        }
        
        // Schedule snooze alarm
        scheduleSnoozeAlarm(id, title, url, mins)
        
        finish()
    }

    private fun handleDismiss(url: String?, id: String?) {
        if (isHandled) return
        isHandled = true
        
        autoSnoozeTimer?.cancel()
        stopAlarmService()
        handleAction(url)
        
        // Mark as dismissed using ScheduleManager (handles all sources correctly)
        if (id != null) {
            try {
                // Get original ID (strip snooze_ prefix if present)
                val originalId = if (id.startsWith("snooze_")) id.removePrefix("snooze_") else id
                val eventSource = com.neubofy.reality.data.EventSource.valueOf(source)
                com.neubofy.reality.data.ScheduleManager.markAsDismissed(applicationContext, originalId, eventSource)
                com.neubofy.reality.utils.TerminalLogger.log("ALARM: Dismissed $originalId ($source)")
            } catch (e: Exception) { 
                com.neubofy.reality.utils.TerminalLogger.log("ALARM ERROR: ${e.message}")
                e.printStackTrace() 
            }
        }
        
        finish()
    }

    private fun stopAlarmService() {
        try {
            val stopIntent = Intent(this, com.neubofy.reality.services.AlarmService::class.java)
            stopIntent.action = "STOP"
            startService(stopIntent)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun scheduleSnoozeAlarm(id: String?, title: String, url: String?, mins: Int) {
        com.neubofy.reality.utils.AlarmScheduler.scheduleSnooze(
            this,
            id ?: "unknown",
            title,
            url,
            snoozeIntervalMins,
            source  // Pass source for proper dismissal handling later
        )
    }
    
    private fun handleAction(customUrl: String?) {
        val prefs = getSharedPreferences("reality_prefs", Context.MODE_PRIVATE)
        val globalUrl = prefs.getString("global_study_url", null)
        
        var target: String? = when {
            customUrl == "__USE_DEFAULT__" -> globalUrl
            !customUrl.isNullOrEmpty() -> customUrl
            else -> null
        }
        
        if (target.isNullOrEmpty()) return
        
        // Handle special Reality deep links
        if (target == "reality://sleep_verify" || target == "reality://smart_sleep") {
            // Launch MainActivity with sleep verification intent
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("action", "smart_sleep")
            }
            startActivity(intent)
            return
        }
        
        // Check if it's a package name (no slashes, has dots, starts with letter)
        if (target.matches(Regex("^[a-z][a-z0-9_]*(\\.[a-z0-9_]+)+$"))) {
            // It's a package name - try to launch app
            val launchIntent = packageManager.getLaunchIntentForPackage(target)
            if (launchIntent != null) {
                launchIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(launchIntent)
            } else {
                // App not installed - open Play Store
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$target")))
                } catch (e: Exception) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$target")))
                }
            }
        } else {
            // It's a URL
            if (!target.startsWith("http://") && !target.startsWith("https://")) {
                target = "https://$target"
            }
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(target))
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        autoSnoozeTimer?.cancel()
        // Only stop alarm if not already handled
        if (!isHandled) {
            stopAlarmService()
        }
    }
}
