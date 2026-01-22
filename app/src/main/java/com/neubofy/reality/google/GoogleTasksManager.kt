package com.neubofy.reality.google

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.model.Task
import com.google.api.services.tasks.model.TaskList
import com.neubofy.reality.utils.TerminalLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Google Tasks API wrapper.
 * 
 * Provides easy access to:
 * - List task lists
 * - Get/Create/Update/Delete tasks
 * - Sync with local storage
 */
object GoogleTasksManager {
    
    private const val APP_NAME = "Reality"
    
    private fun getTasksService(context: Context): Tasks? {
        val credential = GoogleAuthManager.getGoogleAccountCredential(context) ?: return null
        
        return Tasks.Builder(
            GoogleAuthManager.getHttpTransport(),
            GoogleAuthManager.getJsonFactory(),
            credential
        )
            .setApplicationName("com.neubofy.reality")
            .build()
    }
    
    /**
     * Get all task lists for the user.
     * Throws UserRecoverableAuthIOException if permission is needed.
     */
    suspend fun getTaskLists(context: Context): List<TaskList> {
        return withContext(Dispatchers.IO) {
            val service = getTasksService(context) 
                ?: throw IllegalStateException("Not signed in")
            val result = service.tasklists().list().execute()
            result.items ?: emptyList()
        }
    }

    /**
     * Find a task list by name, ignoring case and extra whitespace.
     */
    suspend fun findTaskListByName(context: Context, name: String): TaskList? {
        return withContext(Dispatchers.IO) {
            try {
                val lists = getTaskLists(context)
                val target = name.replace("\\s+".toRegex(), " ").trim().lowercase()
                lists.find { 
                    it.title?.replace("\\s+".toRegex(), " ")?.trim()?.lowercase() == target 
                }
            } catch (e: Exception) {
                TerminalLogger.log("TASKS API: Error finding list - ${e.message}")
                null
            }
        }
    }

    /**
     * Create a new task list.
     */
    suspend fun createTaskList(context: Context, title: String): TaskList? {
        return withContext(Dispatchers.IO) {
            try {
                val service = getTasksService(context) ?: return@withContext null
                val taskList = TaskList().setTitle(title)
                service.tasklists().insert(taskList).execute()
            } catch (e: Exception) {
                TerminalLogger.log("TASKS API: Error creating list - ${e.message}")
                null
            }
        }
    }
    
    /**
     * Get tasks from a specific task list.
     */
    suspend fun getTasks(context: Context, taskListId: String = "@default"): List<Task> {
        return withContext(Dispatchers.IO) {
            val service = getTasksService(context) 
                ?: throw IllegalStateException("Not signed in")
            val result = service.tasks().list(taskListId)
                .setShowCompleted(true)
                .setShowHidden(true)
                .execute()
            result.items ?: emptyList()
        }
    }
    
    /**
     * Get today's task statistics across all task lists.
     * Returns Pair(pending, completed) for tasks due today.
     */
    suspend fun getTodayTaskStats(context: Context): Pair<Int, Int> {
        return withContext(Dispatchers.IO) {
            val service = getTasksService(context)
                ?: throw IllegalStateException("Not signed in")
            
            var pending = 0
            var completed = 0
            
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                .format(java.util.Date())
            
            // Get all task lists
            val taskLists = service.tasklists().list().execute().items ?: emptyList()
            
            for (taskList in taskLists) {
                val tasks = service.tasks().list(taskList.id)
                    .setShowCompleted(true)
                    .setShowHidden(true)
                    .execute().items ?: continue
                
                for (task in tasks) {
                    // Check if due today
                    val dueDate = task.due
                    if (dueDate != null && dueDate.startsWith(today)) {
                        if (task.status == "completed") {
                            completed++
                        } else {
                            pending++
                        }
                    }
                }
            }
            
            Pair(pending, completed)
        }
    }
    
    /**
     * Data class for task statistics on a specific date.
     */
    data class TaskStats(
        val dueTasks: List<String>,
        val completedTasks: List<String>,
        val pendingCount: Int,
        val completedCount: Int
    )
    
    /**
     * Get task statistics for a specific date.
     * Fetches from Google Tasks API for the given date.
     * @param date The date string in "yyyy-MM-dd" format
     * @return TaskStats with lists of task titles and counts
     */
    suspend fun getTasksForDate(context: Context, date: String): TaskStats {
        return withContext(Dispatchers.IO) {
            val service = getTasksService(context)
                ?: return@withContext TaskStats(emptyList(), emptyList(), 0, 0)
            
            val dueTasks = mutableListOf<String>()
            val completedTasks = mutableListOf<String>()
            
            try {
                TerminalLogger.log("TASKS API: Fetching tasks for $date")
                
                // Get all task lists
                val taskLists = service.tasklists().list().execute().items ?: emptyList()
                
                for (taskList in taskLists) {
                    val tasks = service.tasks().list(taskList.id)
                        .setShowCompleted(true)
                        .setShowHidden(true)
                        .execute().items ?: continue
                    
                    for (task in tasks) {
                        val dueDate = task.due
                        val completedDate = task.completed // RFC 3339 Timestamp string
                        val title = task.title ?: "Untitled"

                        // logic:
                        // 1. If completed ON this date -> Completed Task
                        // 2. If due ON this date (and NOT completed on this date) -> Due/Pending Task
                        
                        val isCompletedOnDate = completedDate != null && completedDate.toString().startsWith(date)
                        val isDueOnDate = dueDate != null && dueDate.startsWith(date)
                        
                        if (isCompletedOnDate) {
                            if (!completedTasks.contains(title)) {
                                completedTasks.add(title)
                            }
                        } else if (isDueOnDate) {
                            // It was due today, but NOT completed today.
                            // (It might be completed later, or never, but for TODAY's record, it's pending)
                            dueTasks.add(title)
                        }
                    }
                }
                
                TerminalLogger.log("TASKS API: Found ${dueTasks.size} pending, ${completedTasks.size} completed for $date")
                
            } catch (e: Exception) {
                TerminalLogger.log("TASKS API: Error fetching tasks - ${e.message}")
            }
            
            TaskStats(
                dueTasks = dueTasks,
                completedTasks = completedTasks,
                pendingCount = dueTasks.size,
                completedCount = completedTasks.size
            )
        }
    }
    
    /**
     * Create a new task.
     */
    suspend fun createTask(context: Context, title: String, notes: String? = null, dueDate: String? = null, taskListId: String = "@default"): Task? {
        return withContext(Dispatchers.IO) {
            try {
                val service = getTasksService(context) ?: return@withContext null
                
                val task = Task().apply {
                    this.title = title
                    this.notes = notes
                    this.due = dueDate // Format: RFC 3339 timestamp (e.g., 2024-01-15T00:00:00.000Z)
                }
                
                service.tasks().insert(taskListId, task).execute()
            } catch (e: Exception) {
                TerminalLogger.log("TASKS API: Error creating task - ${e.message}")
                null
            }
        }
    }
    
    /**
     * Update an existing task.
     */
    suspend fun updateTask(context: Context, taskListId: String, taskId: String, task: Task): Task? {
        return withContext(Dispatchers.IO) {
            try {
                val service = getTasksService(context) ?: return@withContext null
                service.tasks().update(taskListId, taskId, task).execute()
            } catch (e: Exception) {
                TerminalLogger.log("TASKS API: Error updating task - ${e.message}")
                null
            }
        }
    }
    
    /**
     * Mark a task as complete.
     */
    suspend fun completeTask(context: Context, taskListId: String, taskId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val service = getTasksService(context) ?: return@withContext false
                val task = service.tasks().get(taskListId, taskId).execute()
                task.status = "completed"
                service.tasks().update(taskListId, taskId, task).execute()
                true
            } catch (e: Exception) {
                TerminalLogger.log("TASKS API: Error completing task - ${e.message}")
                false
            }
        }
    }
    
    /**
     * Delete a task.
     */
    suspend fun deleteTask(context: Context, taskListId: String, taskId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val service = getTasksService(context) ?: return@withContext false
                service.tasks().delete(taskListId, taskId).execute()
                true
            } catch (e: Exception) {
                TerminalLogger.log("TASKS API: Error deleting task - ${e.message}")
                false
            }
        }
    }
}
