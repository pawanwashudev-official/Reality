package com.neubofy.reality.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.neubofy.reality.R
import com.neubofy.reality.ui.activity.AIChatActivity

/**
 * NotificationHelper
 * 
 * Handles "Smart Push Notifications" that are lightweight and informational.
 * Distinct from the heavy AlarmService used for time-critical reminders.
 */
object NotificationHelper {

    const val CHANNEL_SMART_ALERTS = "reality_smart_alerts"
    private const val CHANNEL_NAME = "Smart Alerts (AI)"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Channel for AI Insights & Standard Reminders (Silent or Standard sound)
            val channel = NotificationChannel(
                CHANNEL_SMART_ALERTS,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Proactive insights and non-urgent reminders from Reality AI"
                enableLights(true)
                enableVibration(true)
            }
            
            manager.createNotificationChannel(channel)
        }
    }

    /**
     * Post a standard notification.
     * @param id Unique ID for this notification (default: hash of title)
     */
    fun showNotification(context: Context, title: String, message: String, id: Int = title.hashCode()) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Ensure channel exists
        createChannels(context)

        // Tap action -> Open Chat
        val intent = Intent(context, AIChatActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_SMART_ALERTS)
            .setSmallIcon(R.drawable.baseline_chat_24) // Fallback to chat icon or app icon
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        manager.notify(id, notification)
        TerminalLogger.log("NOTIF: Posted '$title'")
    }
}
