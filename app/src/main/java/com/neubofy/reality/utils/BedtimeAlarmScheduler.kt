package com.neubofy.reality.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.neubofy.reality.Constants
import java.util.Calendar

object BedtimeAlarmScheduler {

    private const val ALARM_REQUEST_CODE = 2026

    fun scheduleNextBedtimeAlarm(context: Context) {
        val prefs = SavedPreferencesLoader(context)
        val bedtime = prefs.getBedtimeData()
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        val intent = Intent(context, com.neubofy.reality.receivers.BedtimeAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Always cancel existing bedtime alarm first
        alarmManager.cancel(pendingIntent)

        if (!bedtime.isEnabled) {
            TerminalLogger.log("BEDTIME ALARM: Disabled, canceled existing alarm.")
            return
        }

        val now = SecureTimeProvider.currentTimeMillis(context)
        val nextTransitionMillis = getNextTransitionMillis(bedtime, now)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTransitionMillis, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, nextTransitionMillis, pendingIntent)
        }

        val timeStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(nextTransitionMillis))
        TerminalLogger.log("BEDTIME ALARM: Scheduled next transition for $timeStr")
    }

    private fun getNextTransitionMillis(bedtime: Constants.BedtimeData, nowMillis: Long): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMillis
        val currentMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start = bedtime.startTimeInMins
        val end = bedtime.endTimeInMins

        val isCurrentlyActive = if (start < end) {
            currentMins in start until end
        } else if (start > end) {
            currentMins >= start || currentMins < end
        } else {
            false
        }

        val targetMins = if (isCurrentlyActive) end else start

        val targetCal = Calendar.getInstance()
        targetCal.timeInMillis = nowMillis
        targetCal.set(Calendar.HOUR_OF_DAY, targetMins / 60)
        targetCal.set(Calendar.MINUTE, targetMins % 60)
        targetCal.set(Calendar.SECOND, 0)
        targetCal.set(Calendar.MILLISECOND, 0)

        if (targetCal.timeInMillis <= nowMillis) {
            targetCal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return targetCal.timeInMillis
    }
}
