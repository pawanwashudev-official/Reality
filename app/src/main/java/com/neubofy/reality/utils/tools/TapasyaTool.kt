package com.neubofy.reality.utils.tools

import android.content.Context
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.utils.ToolRegistry
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

class TapasyaTool : AgentTool {
    override val id = "tapasya"
    override val name = "Deep Focus"
    override val shortDesc = "Focus session history, session search, and deep work stats"
    override val category = ToolCategory.DATA

    override fun getSchema(): JSONObject {
        return createSchema(
            "tapasya",
            "Get Tapasya focus sessions with smart filtering. Can search, filter by status, and get stats.",
            mapOf(
                "date" to "YYYY-MM-DD (default: today)",
                "date_range" to "Optional: 'today', 'week', 'month', 'custom'",
                "start_date" to "For custom range: YYYY-MM-DD",
                "end_date" to "For custom range: YYYY-MM-DD",
                "filter" to "Optional: 'completed', 'missed', 'auto_stopped', 'all'",
                "search" to "Optional: Search session names",
                "min_duration_mins" to "Optional: Filter sessions >= this duration",
                "include_stats" to "Optional: 'true' to include summary stats",
                "limit" to "Max sessions to return (default: 10)"
            )
        )
    }

    override suspend fun execute(context: Context, args: JSONObject): String {
        val dateStr = args.optString("date", "")
        val date = if (dateStr.isNotEmpty()) LocalDate.parse(dateStr) else LocalDate.now()
        val limit = args.optInt("limit", 5)

        val db = AppDatabase.getDatabase(context)
        val startOfDay = date.atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        val endOfDay = date.plusDays(1).atStartOfDay(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()

        val sessions = db.tapasyaSessionDao().getSessionsForDay(startOfDay, endOfDay)

        if (sessions.isEmpty()) return "No Tapasya sessions found for $date."

        val arr = JSONArray()
        sessions.take(limit).forEach { s ->
            arr.put(JSONObject().apply {
                put("name", s.name)
                put("start_time", ToolRegistry.formatIST(s.startTime))
                put("end_time", ToolRegistry.formatIST(s.endTime))
                put("duration_mins", s.effectiveTimeMs / 60000)
                put("pause_mins", s.totalPauseMs / 60000)
                put("was_auto_stopped", s.wasAutoStopped)
            })
        }

        return JSONObject().apply {
            put("date", date.toString())
            put("timezone", "IST")
            put("session_count", sessions.size)
            put("sessions", arr)
        }.toString()
    }
}
