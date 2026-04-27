package com.neubofy.reality.ui.activity

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.R
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


import com.neubofy.reality.utils.ThemeManager

class WakeupAlarmRingingActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)

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

        setContentView(R.layout.activity_wakeup_alarm_ringing)

        val tvTime = findViewById<TextView>(R.id.tvTime)
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        tvTime.text = sdf.format(Date())

        val btnLongPressDismiss = findViewById<MaterialButton>(R.id.btnLongPressDismiss)

        btnLongPressDismiss.setOnLongClickListener {
            val alarmId = intent.getStringExtra("id")

            // Forward to SmartSleepActivity to show math dismiss
            val smartSleepIntent = Intent(this, SmartSleepActivity::class.java).apply {
                putExtra("action", "wakeup_alarm")
                putExtra("id", alarmId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(smartSleepIntent)
            finish()
            true
        }
    }
}
