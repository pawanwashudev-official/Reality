package com.neubofy.reality.utils.tools

import android.content.Context
import com.neubofy.reality.google.GoogleTasksManager
import org.json.JSONObject

class CompleteTaskTool : AgentTool {
    override val id = "action_complete_task"
    override val name = "Complete Task"
    override val shortDesc = "Mark a task as done"
    override val category = ToolCategory.ACTION

    override fun getSchema(): JSONObject {
        return createSchema(
            "action_complete_task",
            "Mark a task as completed. Ask user which task if not specified.",
            mapOf("task_title" to "Required: title of task to complete"),
            required = listOf("task_title")
        )
    }

    override suspend fun execute(context: Context, args: JSONObject): String {
        val taskTitle = args.optString("task_title", "")
        if (taskTitle.isEmpty()) {
            return "Which task would you like to mark as complete?"
        }

        var foundAndCompleted = false
        var foundTitle = ""

        try {
            val taskLists = GoogleTasksManager.getTaskLists(context)
            for (list in taskLists) {
                val tasks = GoogleTasksManager.getTasks(context, list.id)
                val match = tasks.find {
                    it.title?.trim()?.equals(taskTitle.trim(), ignoreCase = true) == true && it.status != "completed"
                }

                if (match != null) {
                    foundTitle = match.title ?: taskTitle
                    val success = GoogleTasksManager.completeTask(context, list.id, match.id)
                    if (success) {
                        foundAndCompleted = true
                        break
                    }
                }
            }
        } catch (e: Exception) {
            return "❌ Error accessing Google Tasks: ${e.localizedMessage}"
        }

        return if (foundAndCompleted) {
            "✅ Task marked as complete: \"$foundTitle\""
        } else {
            "❌ Couldn't find a pending task named \"$taskTitle\"."
        }
    }
}
