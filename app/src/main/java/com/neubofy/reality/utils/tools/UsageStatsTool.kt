package com.neubofy.reality.utils.tools

import android.content.Context
import com.neubofy.reality.utils.UsageUtils
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class UsageStatsTool : AgentTool {
    override val id = "usage_stats"
    override val name = "Usage Stats"
    override val shortDesc = "App usage with categories, comparisons"
    override val category = ToolCategory.DATA

    override fun getSchema(): JSONObject {
        return createSchema(
            "usage_stats",
            "Get app usage with smart filtering. Categorize, compare days, get specific app deep-dive.",
            mapOf(
                "date" to "YYYY-MM-DD (default: today)",
                "date_range" to "Optional: 'today', 'week', 'month'",
                "package_name" to "Optional: Specific app package for deep-dive",
                "app_name" to "Optional: Search by app name (partial match)",
                "category" to "Optional: 'social', 'games', 'productivity', 'entertainment'",
                "sort_by" to "Optional: 'time', 'launches', 'name' (default: time)",
                "min_minutes" to "Optional: Only apps with >= this usage",
                "limit" to "Max apps to return (default: 10)",
                "comparison" to "Optional: 'yesterday', 'last_week' for comparison"
            )
        )
    }

    override suspend fun execute(context: Context, args: JSONObject): String {
        val dateStr = args.optString("date", "")
        val date = if (dateStr.isNotEmpty()) LocalDate.parse(dateStr) else LocalDate.now()
        val pkg = args.optString("package_name", "")

        val usageMap = UsageUtils.getUsageForDate(context, date)

        if (pkg.isNotEmpty()) {
            val ms = usageMap[pkg] ?: 0L
            val mins = ms / 60000
            return JSONObject().apply {
                put("date", date.toString())
                put("package", pkg)
                put("usage_minutes", mins)
            }.toString()
        } else {
            val sorted = usageMap.entries.sortedByDescending { it.value }.take(5)
            val arr = JSONArray()
            sorted.forEach { entry ->
                arr.put(JSONObject().apply {
                    put("app", entry.key.substringAfterLast("."))
                    put("package", entry.key)
                    put("minutes", entry.value / 60000)
                })
            }

            return JSONObject().apply {
                put("date", date.toString())
                put("timezone", "IST")
                put("top_apps", arr)
            }.toString()
        }
    }
}
