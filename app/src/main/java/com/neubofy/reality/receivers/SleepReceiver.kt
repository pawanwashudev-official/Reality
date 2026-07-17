package com.neubofy.reality.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.SleepSegmentEvent
import com.neubofy.reality.utils.TerminalLogger
import java.time.Instant

class SleepReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (SleepSegmentEvent.hasEvents(intent)) {
            val events = SleepSegmentEvent.extractEvents(intent)
            for (event in events) {
                val durationHours = (event.endTimeMillis - event.startTimeMillis) / (1000.0 * 60 * 60)
                TerminalLogger.log("SleepReceiver: Detected sleep from ${Instant.ofEpochMilli(event.startTimeMillis)} to ${Instant.ofEpochMilli(event.endTimeMillis)} (${durationHours}h)")
                
                // Filter out obviously incorrect data (e.g. phone left on a desk for 18 hours)
                if (durationHours in 1.0..14.0) {
                    saveSleepSegment(context, event.startTimeMillis, event.endTimeMillis)
                } else {
                    TerminalLogger.log("SleepReceiver: Ignored unrealistic sleep duration of ${durationHours}h")
                }
            }
        }
    }

    private fun saveSleepSegment(context: Context, startMs: Long, endMs: Long) {
        val prefs = context.getSharedPreferences("reality_sleep_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("google_sleep_start", startMs)
            .putLong("google_sleep_end", endMs)
            .apply()
            
        sendSleepNotification(context, startMs, endMs)
    }

    private fun sendSleepNotification(context: Context, startMs: Long, endMs: Long) {
        val formatter = java.time.format.DateTimeFormatter.ofPattern("h:mm a").withZone(java.time.ZoneId.systemDefault())
        val startStr = formatter.format(Instant.ofEpochMilli(startMs))
        val endStr = formatter.format(Instant.ofEpochMilli(endMs))
        val message = "Reality detected sleep from $startStr to $endStr using Google AI. Tap to review and save."

        val channelId = "reality_sleep_channel"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, "Smart Sleep", android.app.NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }
        
        val intent = Intent(context, com.neubofy.reality.ui.activity.SmartSleepActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val pendingIntent = android.app.PendingIntent.getActivity(context, 0, intent, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        
        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.neubofy.reality.R.drawable.ic_launcher_foreground) // Replace with actual icon if available
            .setContentTitle("New Sleep Session Detected")
            .setContentText(message)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            
        notificationManager.notify(1002, builder.build())
    }
}
