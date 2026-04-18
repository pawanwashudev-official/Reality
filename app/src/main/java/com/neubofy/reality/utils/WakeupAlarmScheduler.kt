package com.neubofy.reality.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.neubofy.reality.receivers.WakeupAlarmReceiver
import java.util.Calendar
import com.neubofy.reality.utils.TerminalLogger

object WakeupAlarmScheduler {

    fun scheduleNextAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefsLoader = SavedPreferencesLoader(context)
        val alarms = prefsLoader.loadWakeupAlarms().filter { it.isEnabled && !it.isDeleted }

        if (alarms.isEmpty()) {
            val intent = Intent(context, WakeupAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context, 1001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            return
        }

        var nextAlarmTime = Long.MAX_VALUE
        var nextAlarmId = ""
        var nextAlarmTitle = ""
        var maxAttempts = 5
        var snoozeInterval = 3
        var ringtoneUri: String? = null
        var vibrationEnabled = true

        val now = Calendar.getInstance()
        val currentMins = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val currentDay = now.get(Calendar.DAY_OF_WEEK)

        for (alarm in alarms) {
            val alarmMins = alarm.hour * 60 + alarm.minute
            val alarmCal = Calendar.getInstance()
            alarmCal.set(Calendar.HOUR_OF_DAY, alarm.hour)
            alarmCal.set(Calendar.MINUTE, alarm.minute)
            alarmCal.set(Calendar.SECOND, 0)
            alarmCal.set(Calendar.MILLISECOND, 0)

            if (alarm.repeatDays.isEmpty()) {
                if (alarmMins <= currentMins) {
                    alarmCal.add(Calendar.DAY_OF_YEAR, 1)
                }
            } else {
                if (alarm.repeatDays.contains(currentDay) && alarmMins > currentMins) {
                    // Today, later
                } else {
                    var daysToAdd = 1
                    while (!alarm.repeatDays.contains((currentDay + daysToAdd - 1) % 7 + 1)) {
                        daysToAdd++
                    }
                    alarmCal.add(Calendar.DAY_OF_YEAR, daysToAdd)
                }
            }

            if (alarmCal.timeInMillis < nextAlarmTime) {
                nextAlarmTime = alarmCal.timeInMillis
                nextAlarmId = alarm.id
                nextAlarmTitle = alarm.title
                maxAttempts = alarm.maxAttempts
                snoozeInterval = alarm.snoozeIntervalMins
                ringtoneUri = alarm.ringtoneUri
                vibrationEnabled = alarm.vibrationEnabled
            }
        }

        if (nextAlarmTime != Long.MAX_VALUE) {
            val intent = Intent(context, WakeupAlarmReceiver::class.java).apply {
                putExtra("id", nextAlarmId)
                putExtra("title", nextAlarmTitle)
                putExtra("maxAttempts", maxAttempts)
                putExtra("snoozeInterval", snoozeInterval)
                putExtra("ringtoneUri", ringtoneUri)
                putExtra("vibrationEnabled", vibrationEnabled)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 1001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val acInfo = AlarmManager.AlarmClockInfo(nextAlarmTime, pendingIntent)
                alarmManager.setAlarmClock(acInfo, pendingIntent)
                TerminalLogger.log("WAKEUP ALARM: Scheduled next exact alarm")
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAlarmTime, pendingIntent)
                TerminalLogger.log("WAKEUP ALARM: Scheduled next exact alarm")
            }
        }
    }

    fun scheduleSnooze(context: Context, id: String, title: String, maxAttempts: Int, snoozeIntervalMins: Int, ringtoneUri: String?, vibrationEnabled: Boolean) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WakeupAlarmReceiver::class.java).apply {
            putExtra("id", id)
            putExtra("title", title)
            putExtra("maxAttempts", maxAttempts)
            putExtra("snoozeInterval", snoozeIntervalMins)
            putExtra("isSnooze", true)
            putExtra("ringtoneUri", ringtoneUri)
            putExtra("vibrationEnabled", vibrationEnabled)
        }
        val triggerTime = System.currentTimeMillis() + (snoozeIntervalMins * 60 * 1000L)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 1002, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val acInfo = AlarmManager.AlarmClockInfo(triggerTime, pendingIntent)
            alarmManager.setAlarmClock(acInfo, pendingIntent)
                TerminalLogger.log("WAKEUP ALARM: Scheduled next exact alarm")
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
        }
    }

    fun cancelSnooze(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WakeupAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, 1002, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }
}
