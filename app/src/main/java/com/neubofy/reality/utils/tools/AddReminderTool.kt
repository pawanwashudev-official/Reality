package com.neubofy.reality.utils.tools

import android.content.Context
import com.neubofy.reality.utils.SavedPreferencesLoader
import com.neubofy.reality.utils.AlarmScheduler
import org.json.JSONObject

class AddReminderTool : AgentTool {
    override val id = "action_add_reminder"
    override val name = "Add Reminder"
    override val shortDesc = "Set custom reminder"
    override val category = ToolCategory.ACTION

    override fun getSchema(): JSONObject {
        return createSchema(
            "action_add_reminder",
            "Create a custom reminder. Ask for time if not specified.",
            mapOf(
                "title" to "Required: reminder message",
                "hour" to "Required: hour (0-23)",
                "minute" to "Optional: minute (0-59, default: 0)"
            ),
            required = listOf("title", "hour")
        )
    }

    override suspend fun execute(context: Context, args: JSONObject): String {
        val title = args.optString("title", "")
        val hourStr = args.optString("hour", "")

        if (title.isEmpty()) return "What should I remind you about?"
        if (hourStr.isEmpty()) return "At what time? Please specify the hour (0-23)."

        val hour = hourStr.toIntOrNull() ?: return "Invalid hour. Please use 0-23."
        val minute = args.optString("minute", "0").toIntOrNull() ?: 0

        val prefs = SavedPreferencesLoader(context)
        val reminders = prefs.loadCustomReminders().toMutableList()

        val newReminder = com.neubofy.reality.data.CustomReminder(
            id = System.currentTimeMillis().toString(),
            title = title,
            hour = hour,
            minute = minute,
            isEnabled = true,
            repeatDays = emptyList() // One-time reminder
        )

        reminders.add(newReminder)
        prefs.saveCustomReminders(reminders)

        com.neubofy.reality.utils.AlarmScheduler.scheduleNextAlarm(context)

        val timeStr = String.format("%02d:%02d", hour, minute)
        return "✅ Reminder set for $timeStr: \"$title\""
    }
}
