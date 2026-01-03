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
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.neubofy.reality.R
import com.neubofy.reality.ui.activity.AlarmActivity

class AlarmService : Service() {

    companion object {
        private const val NOTIFICATION_ID = 9001  // Fixed ID for proper stop
        var instance: AlarmService? = null
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Check for STOP action
        if (intent?.action == "STOP") {
            stopAlarm()
            stopForeground(true)
            stopSelf()
            return START_NOT_STICKY
        }
        
        val title = intent?.getStringExtra("title") ?: "Focus Reminder"
        val id = intent?.getStringExtra("id") ?: ""
        val url = intent?.getStringExtra("url")
        val mins = intent?.getIntExtra("mins", 0) ?: 0
        
        // Extract snooze settings (snapshotted from reminder)
        val snoozeEnabled = intent?.getBooleanExtra("snoozeEnabled", true) ?: true
        val snoozeIntervalMins = intent?.getIntExtra("snoozeIntervalMins", 5) ?: 5
        val autoSnoozeEnabled = intent?.getBooleanExtra("autoSnoozeEnabled", true) ?: true
        val autoSnoozeTimeoutSecs = intent?.getIntExtra("autoSnoozeTimeoutSecs", 30) ?: 30

        startForeground(NOTIFICATION_ID, buildNotification(id, title, mins, url, snoozeEnabled, snoozeIntervalMins, autoSnoozeEnabled, autoSnoozeTimeoutSecs))
        startAlarm()
        
        return START_NOT_STICKY
    }

    private fun buildNotification(id: String, title: String, mins: Int, url: String?, snoozeEnabled: Boolean, snoozeIntervalMins: Int, autoSnoozeEnabled: Boolean, autoSnoozeTimeoutSecs: Int): Notification {
        val channelId = "reality_alarm_channel_v2"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Focus Alarms",
                NotificationManager.IMPORTANCE_HIGH
            )
            channel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            channel.setSound(null, null) // We handle sound manually in Service
            notificationManager.createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(this, AlarmActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("id", id)
            putExtra("title", title)
            putExtra("url", url)
            putExtra("mins", mins)
            // Pass snooze settings
            putExtra("snoozeEnabled", snoozeEnabled)
            putExtra("snoozeIntervalMins", snoozeIntervalMins)
            putExtra("autoSnoozeEnabled", autoSnoozeEnabled)
            putExtra("autoSnoozeTimeoutSecs", autoSnoozeTimeoutSecs)
        }
        
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            id.hashCode(),
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.baseline_access_time_24)
            .setContentTitle("Time to Focus")
            .setContentText(title)
            .setPriority(NotificationCompat.PRIORITY_MAX) // Max priority for Alarm
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(false) // Changed to false so it can be dismissed to trigger deleteIntent
            .setDeleteIntent(getDeleteIntent(id, title, mins, url, snoozeEnabled, snoozeIntervalMins, autoSnoozeEnabled, autoSnoozeTimeoutSecs))
            .build()
    }

    private fun getDeleteIntent(id: String, title: String, mins: Int, url: String?, snoozeEnabled: Boolean, snoozeIntervalMins: Int, autoSnoozeEnabled: Boolean, autoSnoozeTimeoutSecs: Int): PendingIntent {
        val intent = Intent(this, com.neubofy.reality.receivers.ReminderReceiver::class.java).apply {
            action = "ACTION_DISMISS"
            putExtra("id", id)
            putExtra("title", title)
            putExtra("url", url)
            putExtra("mins", mins)
            putExtra("snoozeEnabled", snoozeEnabled)
            putExtra("snoozeIntervalMins", snoozeIntervalMins)
            putExtra("autoSnoozeEnabled", autoSnoozeEnabled)
            putExtra("autoSnoozeTimeoutSecs", autoSnoozeTimeoutSecs)
        }
        return PendingIntent.getBroadcast(
            this,
            id.hashCode() + 1, // distinct ID
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun startAlarm() {
        try {
            val prefs = getSharedPreferences("reality_prefs", Context.MODE_PRIVATE)
            val shouldLoop = prefs.getBoolean("reminders_loop_audio", true)
            val shouldVibrate = prefs.getBoolean("reminders_vibrate", true)

            // Audio
            val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                        .build()
                )
                isLooping = shouldLoop
                prepare()
                start()
            }

            // Vibration
            if (shouldVibrate) {
                vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                val repeatMode = if (shouldLoop) 0 else -1
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), repeatMode)
                    vibrator?.vibrate(effect)
                } else {
                    vibrator?.vibrate(longArrayOf(0, 500, 200, 500), repeatMode)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAlarm()
    }

    private fun stopAlarm() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            vibrator?.cancel()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
