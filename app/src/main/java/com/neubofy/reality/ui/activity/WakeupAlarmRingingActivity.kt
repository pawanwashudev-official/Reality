package com.neubofy.reality.ui.activity

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.neubofy.reality.ui.base.BaseActivity
import com.neubofy.reality.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


import com.neubofy.reality.utils.ThemeManager

class WakeupAlarmRingingActivity : BaseActivity() {
    private var countdownTimer: CountDownTimer? = null
    private var expectedAnswer: Double = 0.0

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        setContentView(R.layout.activity_wakeup_alarm_ringing)

        val tvTime = findViewById<TextView>(R.id.tvTime)
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        tvTime.text = sdf.format(Date())

        val btnLongPressDismiss = findViewById<MaterialButton>(R.id.btnLongPressDismiss)
        val layoutMath = findViewById<LinearLayout>(R.id.layoutMath)
        val tvMathProblem = findViewById<TextView>(R.id.tvMathProblem)
        val etMathAnswer = findViewById<TextInputEditText>(R.id.etMathAnswer)
        val tvError = findViewById<TextView>(R.id.tvError)
        val btnSnooze = findViewById<MaterialButton>(R.id.btnSnooze)
        val btnDismiss = findViewById<MaterialButton>(R.id.btnDismiss)

        btnLongPressDismiss.setOnLongClickListener {
            val alarmId = intent.getStringExtra("id")

            // Pause ringing
            val pauseIntent = Intent(this, com.neubofy.reality.services.WakeupAlarmService::class.java).apply {
                this.action = "PAUSE_RINGING"
            }
            startService(pauseIntent)

            btnLongPressDismiss.visibility = View.GONE
            layoutMath.visibility = View.VISIBLE

            // Generate math problem
            var a = (11..99).random() / 10.0
            var b = (11..99).random() / 10.0

            if ((0..1).random() == 1) {
                a = (11..99).random() / 100.0
            }

            expectedAnswer = Math.round(a * b * 1000.0) / 1000.0
            tvMathProblem.text = "${a} × ${b} = ?"

            val loader = com.neubofy.reality.utils.SavedPreferencesLoader(this)
            val alarm = loader.loadWakeupAlarms().find { it.id == alarmId }
            val interval = alarm?.snoozeIntervalMins ?: 3
            val maxAttempts = alarm?.maxAttempts ?: 5

            // Countdown timer (90 seconds)
            countdownTimer = object : CountDownTimer(90000, 1000) {
                override fun onTick(millisUntilFinished: Long) {}
                override fun onFinish() {
                    // Snooze logic if not solved in time
                    com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleSnooze(this@WakeupAlarmRingingActivity, alarmId ?: "nightly_wakeup", alarm?.title ?: "Wake Up", maxAttempts, interval, alarm?.ringtoneUri, alarm?.vibrationEnabled ?: true)
                    val stopIntent = Intent(this@WakeupAlarmRingingActivity, com.neubofy.reality.services.WakeupAlarmService::class.java).apply {
                        this.action = "STOP"
                    }
                    startService(stopIntent)
                    finish()
                }
            }.start()

            btnSnooze.setOnClickListener {
                countdownTimer?.cancel()
                com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleSnooze(this@WakeupAlarmRingingActivity, alarmId ?: "nightly_wakeup", alarm?.title ?: "Wake Up", maxAttempts, interval, alarm?.ringtoneUri, alarm?.vibrationEnabled ?: true)
                val stopIntent = Intent(this@WakeupAlarmRingingActivity, com.neubofy.reality.services.WakeupAlarmService::class.java).apply {
                    this.action = "STOP"
                }
                startService(stopIntent)
                finish()
            }

            btnDismiss.setOnClickListener {
                val userAnswerStr = etMathAnswer.text.toString()
                if (userAnswerStr.isEmpty()) {
                    tvError.visibility = View.VISIBLE
                    tvError.text = "Please enter an answer"
                    return@setOnClickListener
                }

                try {
                    val userAnswer = userAnswerStr.toDouble()
                    if (Math.abs(userAnswer - expectedAnswer) < 0.001) {
                        countdownTimer?.cancel()
                        tvError.visibility = View.GONE
                        val stopIntent = Intent(this@WakeupAlarmRingingActivity, com.neubofy.reality.services.WakeupAlarmService::class.java).apply {
                            this.action = "STOP"
                        }
                        startService(stopIntent)

                        val alarms = loader.loadWakeupAlarms()
                        val idx = alarms.indexOfFirst { it.id == alarmId }
                        if (idx != -1 && alarms[idx].repeatDays.isEmpty()) {
                            alarms[idx] = alarms[idx].copy(isDeleted = true)
                            loader.saveWakeupAlarms(alarms)
                        }
                        com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleNextAlarm(this@WakeupAlarmRingingActivity)

                        lifecycleScope.launch {
                            com.neubofy.reality.utils.SleepInferenceHelper.autoConfirmSleep(this@WakeupAlarmRingingActivity)
                            finish()
                        }
                    } else {
                        tvError.visibility = View.VISIBLE
                        tvError.text = "Incorrect answer"
                    }
                } catch (e: NumberFormatException) {
                    tvError.visibility = View.VISIBLE
                    tvError.text = "Invalid format"
                }
            }
            true
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
    }
}
