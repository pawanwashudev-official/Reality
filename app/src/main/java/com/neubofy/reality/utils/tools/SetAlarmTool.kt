package com.neubofy.reality.utils.tools

import android.content.Context
import com.neubofy.reality.utils.SavedPreferencesLoader
import com.neubofy.reality.utils.WakeupAlarmScheduler
import org.json.JSONObject

class SetAlarmTool : AgentTool {
    override val id = "action_set_alarm"
    override val name = "Set Alarm"
    override val shortDesc = "Set a wakeup alarm"
    override val category = ToolCategory.ACTION

    override fun getSchema(): JSONObject {
        return createSchema(
            "action_set_alarm",
            "Set a new wakeup alarm. Ask for time if not specified.",
            mapOf(
                "title" to "Optional: alarm title",
                "hour" to "Required: hour (0-23)",
                "minute" to "Optional: minute (0-59, default: 0)"
            ),
            required = listOf("hour")
        )
    }

    override suspend fun execute(context: Context, args: JSONObject): String {
        val title = args.optString("title", "Wake Up")
        val hourStr = args.optString("hour", "")

        if (hourStr.isEmpty()) {
            return "What time? Please specify the hour (0-23)."
        }

        val hour = hourStr.toIntOrNull() ?: return "Invalid hour. Please use 0-23."
        val minute = args.optString("minute", "0").toIntOrNull() ?: 0

        val prefs = SavedPreferencesLoader(context)
        val alarms = prefs.loadWakeupAlarms().toMutableList()

        val newAlarm = com.neubofy.reality.data.model.WakeupAlarm(
            id = System.currentTimeMillis().toString(),
            title = title,
            description = "",
            hour = hour,
            minute = minute,
            isEnabled = true,
            repeatDays = emptyList(), // One-time alarm
            ringtoneUri = null, // Default
            vibrationEnabled = true
        )

        alarms.add(newAlarm)
        prefs.saveWakeupAlarms(alarms)

        WakeupAlarmScheduler.scheduleNextAlarm(context)

        val timeStr = String.format("%02d:%02d", hour, minute)
        return "✅ Alarm set for $timeStr: \"$title\""
    }
}
