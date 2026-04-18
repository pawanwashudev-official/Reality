package com.neubofy.reality.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.neubofy.reality.services.WakeupAlarmService
import com.neubofy.reality.utils.FiredEventsCache
import com.neubofy.reality.utils.TerminalLogger

class WakeupAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.getStringExtra("id") ?: return
        val title = intent.getStringExtra("title") ?: "Wake Up"
        val maxAttempts = intent.getIntExtra("maxAttempts", 5)
        val snoozeInterval = intent.getIntExtra("snoozeInterval", 3)
        val isSnooze = intent.getBooleanExtra("isSnooze", false)

        val fireCount = if (isSnooze) FiredEventsCache.incrementFireCount(context, "wakeup_$id") else {
            FiredEventsCache.markAsFired(context, "wakeup_$id")
            1
        }

        if (fireCount > maxAttempts) {
            TerminalLogger.log("WAKEUP ALARM: Max attempts reached ($maxAttempts). Auto-dismissing.")
            // Ideally we also dismiss logic here
            return
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "Reality:WakeupAlarmWakeLock"
        )
        wakeLock.acquire(10_000L)

        val serviceIntent = Intent(context, WakeupAlarmService::class.java).apply {
            putExtra("id", id)
            putExtra("title", title)
            putExtra("maxAttempts", maxAttempts)
            putExtra("snoozeInterval", snoozeInterval)
        }

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            TerminalLogger.log("ERROR starting WakeupAlarmService: ${e.message}")
        }
    }
}
