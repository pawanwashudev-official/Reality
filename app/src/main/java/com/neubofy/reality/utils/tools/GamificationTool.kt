package com.neubofy.reality.utils.tools

import android.content.Context
import com.neubofy.reality.utils.XPManager
import org.json.JSONObject
import java.time.LocalDate

class GamificationTool : AgentTool {
    override val id = "gamification"
    override val name = "XP & Stats"
    override val shortDesc = "XP, levels, streaks, and daily progress breakdown"
    override val category = ToolCategory.DATA

    override fun getSchema(): JSONObject {
        return createSchema(
            "gamification",
            "Get XP and progress stats. Can query single date or date range for trends. Use 'comparison' for progress insights.",
            mapOf(
                "date" to "YYYY-MM-DD (default: today)",
                "date_range" to "Optional: 'today', 'week', 'month', or 'custom'",
                "start_date" to "Required if date_range='custom': YYYY-MM-DD",
                "end_date" to "Required if date_range='custom': YYYY-MM-DD",
                "type" to "Optional: 'total', 'breakdown', 'trends'",
                "comparison" to "Optional: 'previous_period' to compare with prior period"
            )
        )
    }

    override suspend fun execute(context: Context, args: JSONObject): String {
        val dateStr = args.optString("date", "")
        val date = if (dateStr.isNotEmpty()) LocalDate.parse(dateStr) else LocalDate.now()
        val type = args.optString("type", "all")

        val stats = XPManager.getDailyStats(context, date.toString())
        val json = JSONObject().apply {
            put("date", date.toString())
            put("timezone", "IST")
            put("current_level", XPManager.getLevel(context))
            put("current_streak", XPManager.getStreak(context))
            put("total_xp_today", (stats?.totalDailyXP ?: 0) as Any)

            if (type == "all" || type == "breakdown") {
                put("xp_breakdown", JSONObject().apply {
                    put("distraction_xp", (stats?.distractionXP ?: 0) as Any)
                    put("reflection_xp", (stats?.reflectionXP ?: 0) as Any)
                    put("tasks_xp", (stats?.taskXP ?: 0) as Any)
                    put("tapasya_xp", (stats?.tapasyaXP ?: 0) as Any)
                    put("calendar_sessions_xp", (stats?.sessionXP ?: 0) as Any)
                    put("bonus_xp", (stats?.bonusXP ?: 0) as Any)
                    put("penalty_xp", (stats?.penaltyXP ?: 0) as Any)
                })
            }
        }
        return json.toString()
    }
}
