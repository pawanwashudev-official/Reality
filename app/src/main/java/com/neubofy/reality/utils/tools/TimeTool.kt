package com.neubofy.reality.utils.tools

import android.content.Context
import com.neubofy.reality.utils.ToolRegistry
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TimeTool : AgentTool {
    override val id = "utility_time"
    override val name = "Current Time"
    override val shortDesc = "Get current date/time in IST"
    override val category = ToolCategory.UTILITY

    override fun getSchema(): JSONObject {
        return createSchema(
            "utility_time",
            "Get current date and time in IST (India Standard Time). Use this when you need to know the current time for scheduling, reminders, or time-based responses.",
            mapOf(
                "include_date" to "Optional: 'true' to include full date (default: true)",
                "format" to "Optional: 'full', 'time_only', 'date_only' (default: full)"
            )
        )
    }

    override suspend fun execute(context: Context, args: JSONObject): String {
        val format = args.optString("format", "full")
        val istZone = ZoneId.of("Asia/Kolkata")
        val now = Instant.now().atZone(istZone)

        return when (format) {
            "time_only" -> now.format(DateTimeFormatter.ofPattern("HH:mm"))
            "date_only" -> now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            else -> now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        }
    }
}
