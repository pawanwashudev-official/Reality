package com.neubofy.reality.utils.tools

import android.content.Context
import com.neubofy.reality.google.GoogleTasksManager
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class TasksTool : AgentTool {
    override val id = "tasks"
    override val name = "Google Tasks"
    override val shortDesc = "Active and recently completed tasks from Google"
    override val category = ToolCategory.DATA

    override fun getSchema(): JSONObject {
        return createSchema(
            "tasks",
            "Get Google Tasks with smart filtering. Search, filter by status, get specific lists.",
            mapOf(
                "date" to "YYYY-MM-DD for due date filter (default: all)",
                "date_range" to "Optional: 'today', 'week', 'overdue', 'upcoming', 'all'",
                "filter" to "Optional: 'pending', 'completed', 'all' (default: all)",
                "search" to "Optional: Search task titles (case-insensitive)",
                "list_name" to "Optional: Filter by task list name",
                "include_notes" to "Optional: 'true' to include task notes",
                "limit" to "Max tasks to return (default: 20)"
            )
        )
    }

    override suspend fun execute(context: Context, args: JSONObject): String {
        val dateStr = args.optString("date", "")
        val date = if (dateStr.isNotEmpty()) LocalDate.parse(dateStr) else LocalDate.now()

        val stats = GoogleTasksManager.getTasksForDate(context, date.toString())
        return JSONObject().apply {
            put("date", date.toString())
            put("timezone", "IST")
            put("pending_count", stats.pendingCount)
            put("completed_count", stats.completedCount)
            put("due_tasks", JSONArray(stats.dueTasks))
            put("completed_tasks", JSONArray(stats.completedTasks))
        }.toString()
    }
}
