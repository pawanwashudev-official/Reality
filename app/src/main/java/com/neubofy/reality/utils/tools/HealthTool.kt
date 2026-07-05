package com.neubofy.reality.utils.tools

import android.content.Context
import com.neubofy.reality.health.HealthManager
import com.neubofy.reality.utils.SecurePreferences
import org.json.JSONObject
import java.time.LocalDate

class HealthTool : AgentTool {
    override val id = "health"
    override val name = "Health"
    override val shortDesc = "Steps, calories, sleep with trends"
    override val category = ToolCategory.DATA
    override val defaultEnabled = false

    override fun getSchema(): JSONObject {
        return createSchema(
            "health",
            "Get health data with date range and metric selection.",
            mapOf(
                "date" to "YYYY-MM-DD (default: today)",
                "date_range" to "Optional: 'today', 'week', 'month'",
                "metrics" to "Optional: 'steps', 'calories', 'sleep', 'all' (default: all)",
                "include_goals" to "Optional: 'true' to include goal progress",
                "comparison" to "Optional: 'yesterday', 'last_week'"
            )
        )
    }

    override suspend fun execute(context: Context, args: JSONObject): String {
        val prefs = SecurePreferences.get(context, "ai_prefs")
        if (!prefs.getBoolean("health_access_enabled", false)) {
            return "Health access is disabled. Enable it in AI Settings."
        }

        val dateStr = args.optString("date", "")
        val date = if (dateStr.isNotEmpty()) LocalDate.parse(dateStr) else LocalDate.now()

        if (!HealthManager.isHealthConnectAvailable(context)) {
            return "Health Connect is not available on this device."
        }

        val manager = HealthManager(context)
        if (!manager.hasPermissions()) {
            return "Health permissions not granted. Please grant in Health Connect."
        }

        val steps = manager.getSteps(date)
        val cals = manager.getCalories(date)
        val sleep = manager.getSleep(date)

        return JSONObject().apply {
            put("date", date.toString())
            put("timezone", "IST")
            put("steps", steps)
            put("calories_kcal", String.format("%.1f", cals))
            put("sleep", sleep)
        }.toString()
    }
}
