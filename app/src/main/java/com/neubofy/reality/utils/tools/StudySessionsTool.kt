package com.neubofy.reality.utils.tools

import android.content.Context
import com.neubofy.reality.data.repository.CalendarRepository
import com.neubofy.reality.utils.ToolRegistry
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

class StudySessionsTool : AgentTool {
    override val id = "study_sessions"
    override val name = "Academic Calendar"
    override val shortDesc = "Study sessions and calendar events with search"
    override val category = ToolCategory.DATA

    override fun getSchema(): JSONObject {
        return createSchema(
            "study_sessions",
            "Get calendar events with filtering. Search by title, filter by time of day.",
            mapOf(
                "date" to "YYYY-MM-DD (default: today)",
                "date_range" to "Optional: 'today', 'week', 'month'",
                "start_date" to "For custom range",
                "end_date" to "For custom range",
                "search" to "Optional: Search event titles",
                "time_filter" to "Optional: 'morning', 'afternoon', 'evening'",
                "include_past" to "Optional: 'true' to include past events today"
            )
        )
    }

    override suspend fun execute(context: Context, args: JSONObject): String {
        val dateStr = args.optString("date", "")
        val date = if (dateStr.isNotEmpty()) LocalDate.parse(dateStr) else LocalDate.now()

        val repo = CalendarRepository(context)
        val start = date.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()

        val events = repo.getEventsInRange(start, end)
        if (events.isEmpty()) return "No events found for $date."

        val jsonArr = JSONArray()
        events.forEach { e ->
            jsonArr.put(JSONObject().apply {
                put("title", e.title)
                put("start", ToolRegistry.formatIST(e.startTime))
                put("end", ToolRegistry.formatIST(e.endTime))
                put("description", e.description ?: "")
                put("location", e.location ?: "")
            })
        }

        return JSONObject().apply {
            put("date", date.toString())
            put("timezone", "IST")
            put("event_count", events.size)
            put("events", jsonArr)
        }.toString()
    }
}
