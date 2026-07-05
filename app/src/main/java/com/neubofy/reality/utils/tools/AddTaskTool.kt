package com.neubofy.reality.utils.tools

import android.content.Context
import com.neubofy.reality.google.GoogleTasksManager
import org.json.JSONObject

class AddTaskTool : AgentTool {
    override val id = "action_add_task"
    override val name = "Add Task"
    override val shortDesc = "Create new Google Task"
    override val category = ToolCategory.ACTION

    override fun getSchema(): JSONObject {
        return createSchema(
            "action_add_task",
            "Add a new task to Google Tasks. Ask user for title if not provided.",
            mapOf(
                "title" to "Required: task title",
                "notes" to "Optional: additional notes",
                "due_date" to "Optional: YYYY-MM-DD"
            ),
            required = listOf("title")
        )
    }

    override suspend fun execute(context: Context, args: JSONObject): String {
        val title = args.optString("title", "")
        if (title.isEmpty()) {
            return "I need the task title. What would you like the task to say?"
        }

        val notes = args.optString("notes", null)
        val dueDate = args.optString("due_date", null)

        val result = GoogleTasksManager.createTask(context, title, notes, dueDate)

        return if (result != null) {
            "✅ Task created: \"$title\"" + (if (dueDate != null) " (due: $dueDate)" else "")
        } else {
            "❌ Failed to create task. Please check Google Tasks connection."
        }
    }
}
