package com.neubofy.reality.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.neubofy.reality.R
import com.neubofy.reality.ui.activity.MainActivity
import com.neubofy.reality.utils.WakeupAlarmScheduler

class WakeupAlarmService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 9002
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var autoSnoozeTimer: CountDownTimer? = null
    private var alarmId: String? = null
    private var alarmTitle: String? = null
    private var maxAttempts = 5
    private var snoozeInterval = 3
    private var ringtoneUri: String? = null
    private var vibrationEnabled = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopAlarm()
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }

        com.neubofy.reality.utils.TerminalLogger.log("WAKEUP ALARM: Service Started!")
        alarmId = intent?.getStringExtra("id")
        alarmTitle = intent?.getStringExtra("title") ?: "Wake Up"
        maxAttempts = intent?.getIntExtra("maxAttempts", 5) ?: 5
        snoozeInterval = intent?.getIntExtra("snoozeInterval", 3) ?: 3
        ringtoneUri = intent?.getStringExtra("ringtoneUri")
        vibrationEnabled = intent?.getBooleanExtra("vibrationEnabled", true) ?: true

        startForeground(NOTIFICATION_ID, buildNotification())
        startAlarm()
        startAutoSnoozeTimer()

        return START_NOT_STICKY
    }

    private fun buildNotification(): Notification {
        val channelId = "reality_wakeup_alarm_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Wakeup Alarms", NotificationManager.IMPORTANCE_HIGH)
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channel.setSound(null, null)
            notificationManager.createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("action", "wakeup_alarm")
            putExtra("id", alarmId)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, alarmId.hashCode(), fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.baseline_access_time_24)
            .setContentTitle("Wake Up!")
            .setContentText(alarmTitle)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .build()
    }

    private fun startAlarm() {
        try {
            val alarmUri: Uri = if (ringtoneUri != null && ringtoneUri!!.isNotEmpty()) {
                Uri.parse(ringtoneUri)
            } else {
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            }
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }

            if (vibrationEnabled) {
                vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0)
                    vibrator?.vibrate(effect)
                } else {
                    vibrator?.vibrate(longArrayOf(0, 500, 500), 0)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startAutoSnoozeTimer() {
        autoSnoozeTimer?.cancel()
        // 1 minute
        autoSnoozeTimer = object : CountDownTimer(60_000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                autoSnooze()
            }
        }.start()
    }

    private fun autoSnooze() {
        alarmId?.let { id ->
            WakeupAlarmScheduler.scheduleSnooze(this, id, alarmTitle ?: "Wake Up", maxAttempts, snoozeInterval, ringtoneUri, vibrationEnabled)
        }
        stopAlarm()
        stopForeground(true)
        stopSelf()
    }

    private fun stopAlarm() {
        autoSnoozeTimer?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }
}
