package com.neubofy.reality.ui.activity

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.neubofy.reality.R
import com.neubofy.reality.data.ScheduleManager
import com.neubofy.reality.data.EventSource
import com.neubofy.reality.services.AlarmService
import com.neubofy.reality.ui.base.BaseActivity
import com.neubofy.reality.utils.TerminalLogger
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WakeupAlarmActivity : BaseActivity() {

    private var isHandled = false
    private var id: String? = null
    private var source: String = "NIGHTLY"
    private var autoSnoozeTimer: android.os.CountDownTimer? = null
    private var autoSnoozeTimeoutSecs = 30
    private var snoozeIntervalMins = 3

    private val qrScannerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            handleDismiss()
        } else {
            Toast.makeText(this, "Scan QR to stop alarm", Toast.LENGTH_SHORT).show()
        }
    }

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

        setContentView(R.layout.layout_wakeup_alarm)

        id = intent.getStringExtra("id")
        source = intent.getStringExtra("source") ?: "NIGHTLY"

        val tvTime = findViewById<TextView>(R.id.tvTime)
        val tvTitle = findViewById<TextView>(R.id.tvTitle)
        val btnScanQR = findViewById<Button>(R.id.btnScanQR)

        val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        tvTime.text = timeFormat.format(Date())
        tvTitle.text = intent.getStringExtra("title") ?: "Wake Up!"

        btnScanQR.setOnClickListener {
            qrScannerLauncher.launch(Intent(this, QRScannerActivity::class.java))
        }

        // Get smart sleep settings for snooze and timeout
        val prefs = getSharedPreferences("nightly_prefs", Context.MODE_PRIVATE)
        autoSnoozeTimeoutSecs = prefs.getInt("smart_sleep_timeout_secs", 30)
        snoozeIntervalMins = prefs.getInt("smart_sleep_snooze_mins", 3)

        startAutoSnoozeTimer()
    }

    private fun startAutoSnoozeTimer() {
        autoSnoozeTimer = object : android.os.CountDownTimer(autoSnoozeTimeoutSecs * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                if (!isHandled) {
                    handleSnooze()
                }
            }
        }.start()
    }

    private fun handleSnooze() {
        if (isHandled) return
        isHandled = true

        autoSnoozeTimer?.cancel()
        stopAlarmService()

        com.neubofy.reality.utils.AlarmScheduler.scheduleSnooze(
            this,
            id ?: "nightly_wakeup",
            intent.getStringExtra("title") ?: "Wake Up!",
            intent.getStringExtra("url"),
            snoozeIntervalMins,
            source
        )

        finish()
    }

    private fun handleDismiss() {
        if (isHandled) return
        isHandled = true

        autoSnoozeTimer?.cancel()
        stopAlarmService()

        // Smart Sleep verification should be launched immediately
        SmartSleepActivity.isUnlockedThisSession = true
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action", "smart_sleep")
        }
        startActivity(intent)

        // Mark as dismissed
        if (id != null) {
            try {
                val originalId = if (id!!.startsWith("snooze_")) id!!.removePrefix("snooze_") else id!!
                val eventSource = EventSource.valueOf(source)
                ScheduleManager.markAsDismissed(applicationContext, originalId, eventSource)
                TerminalLogger.log("WAKEUP ALARM: Dismissed $originalId ($source)")
            } catch (e: Exception) {
                TerminalLogger.log("WAKEUP ALARM ERROR: ${e.message}")
                e.printStackTrace()
            }
        }

        finish()
    }

    private fun stopAlarmService() {
        try {
            val stopIntent = Intent(this, AlarmService::class.java)
            stopIntent.action = "STOP"
            startService(stopIntent)
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onDestroy() {
        super.onDestroy()
        autoSnoozeTimer?.cancel()
        if (!isHandled) {
            stopAlarmService()
        }
    }

    // Prevent back button
    override fun onBackPressed() {
        Toast.makeText(this, "Scan QR to stop alarm", Toast.LENGTH_SHORT).show()
    }
}
