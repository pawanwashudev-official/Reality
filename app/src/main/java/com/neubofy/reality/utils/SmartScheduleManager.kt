package com.neubofy.reality.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.receivers.ScheduleTransitionReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SmartScheduleManager {
    
    fun scheduleNextTransition(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val now = System.currentTimeMillis()
                
                val nextTime = db.calendarEventDao().getNextEventTime(now)
                
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val intent = Intent(context, ScheduleTransitionReceiver::class.java).apply {
                    action = ScheduleTransitionReceiver.ACTION_TRANSITION
                }
                
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                
                if (nextTime != null) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            nextTime,
                            pendingIntent
                        )
                    } else {
                        alarmManager.setExact(
                            AlarmManager.RTC_WAKEUP,
                            nextTime,
                            pendingIntent
                        )
                    }
                    TerminalLogger.log("SMART ALARM: Scheduled for next transition at $nextTime")
                } else {
                    alarmManager.cancel(pendingIntent)
                    TerminalLogger.log("SMART ALARM: Canceled, no upcoming transitions")
                }
            } catch (e: Exception) {
                TerminalLogger.log("SMART ALARM ERROR: ${e.message}")
            }
        }
    }
}
