package com.neubofy.reality.utils.tools

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.neubofy.reality.receivers.ReminderReceiver
import org.json.JSONObject

class ScheduleNotificationTool : AgentTool {
    override val id = "action_schedule_notification"
    override val name = "Schedule Notification"
    override val shortDesc = "Schedule a lightweight push notification"
    override val category = ToolCategory.ACTION

    override fun getSchema(): JSONObject {
        return createSchema(
            "action_schedule_notification",
            "Schedule a lightweight push notification (informational only, no alarm sound). Use for gentle nudges or insights.",
            mapOf(
                "title" to "Required: Notification title",
                "message" to "Required: Notification body text",
                "minutes_from_now" to "Required: Delay in minutes (min 1, max 1440)"
            ),
            required = listOf("title", "message", "minutes_from_now")
        )
    }

    override suspend fun execute(context: Context, args: JSONObject): String {
        val title = args.optString("title", "Reality Alert")
        val message = args.optString("message", "")
        val delayMins = args.optInt("minutes_from_now", 0)

        if (message.isEmpty()) return "Notification message is required."
        if (delayMins < 1) return "Delay must be at least 1 minute."

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("id", "notif_" + System.currentTimeMillis())
            putExtra("title", title)
            putExtra("url", message)
            putExtra("type", "NOTIFICATION")
            putExtra("notification_message", message)
        }

        val triggerTime = System.currentTimeMillis() + (delayMins * 60 * 1000L)
        val reqCode = (System.currentTimeMillis() % 100000).toInt()

        val pIntent = PendingIntent.getBroadcast(
            context, reqCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pIntent)
        }

        return "✅ Notification scheduled in $delayMins mins: \"$title\""
    }
}
