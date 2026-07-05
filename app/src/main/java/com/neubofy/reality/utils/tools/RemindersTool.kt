package com.neubofy.reality.utils.tools

import android.content.Context
import com.neubofy.reality.utils.SavedPreferencesLoader
import org.json.JSONArray
import org.json.JSONObject

class RemindersTool : AgentTool {
    override val id = "reminders"
    override val name = "Reminders"
    override val shortDesc = "Custom reminders with time filters"
    override val category = ToolCategory.DATA

    override fun getSchema(): JSONObject {
        return createSchema(
            "reminders",
            "Get custom reminders with filtering.",
            mapOf(
                "filter" to "Optional: 'active', 'all', 'today' (default: active)",
                "search" to "Optional: Search reminder titles",
                "time_range" to "Optional: 'morning', 'afternoon', 'evening'"
            )
        )
    }

    override suspend fun execute(context: Context, args: JSONObject): String {
        val prefs = SavedPreferencesLoader(context)
        val reminders = prefs.loadCustomReminders()

        if (reminders.isEmpty()) return "No active custom reminders."

        val arr = JSONArray()
        reminders.filter { it.isEnabled }.forEach { r ->
            arr.put(JSONObject().apply {
                put("title", r.title)
                put("time", String.format("%02d:%02d", r.hour, r.minute))
                put("repeat_days", JSONArray(r.repeatDays))
            })
        }

        return JSONObject().apply {
            put("timezone", "IST")
            put("reminder_count", arr.length())
            put("reminders", arr)
        }.toString()
    }
}
