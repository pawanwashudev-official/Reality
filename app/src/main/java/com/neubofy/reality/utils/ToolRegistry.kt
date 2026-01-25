package com.neubofy.reality.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Modular Tool Registry for AI Agent
 * 
 * Design Goals:
 * 1. Token Efficiency: AI gets tool names only, requests schema on-demand
 * 2. Modularity: Users can enable/disable tools in Settings
 * 3. IST First: All times formatted in Asia/Kolkata
 */
object ToolRegistry {

    private const val PREFS_NAME = "ai_prefs"
    private const val KEY_PREFIX = "tool_enabled_"
    
    // IST Formatter
    private val IST_ZONE = ZoneId.of("Asia/Kolkata")
    private val IST_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    
    fun formatIST(epochMs: Long): String {
        return Instant.ofEpochMilli(epochMs).atZone(IST_ZONE).format(IST_FORMATTER)
    }
    
    fun formatISTTime(epochMs: Long): String {
        return Instant.ofEpochMilli(epochMs).atZone(IST_ZONE).format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    // --- Tool Definitions ---
    data class ToolMeta(
        val id: String,
        val name: String,
        val shortDesc: String,  // One line for discovery prompt
        val defaultEnabled: Boolean = true
    )

    val ALL_TOOLS = listOf(
        // Data Tools
        ToolMeta("gamification", "Gamification", "XP, level, streak, daily breakdown"),
        ToolMeta("tapasya", "Tapasya", "Focus sessions history & stats"),
        ToolMeta("tasks", "Tasks", "Google Tasks (today's pending/completed)"),
        ToolMeta("study_sessions", "Study Sessions", "Calendar events for today (IST)"),
        ToolMeta("usage_stats", "Usage Stats", "App screen time data"),
        ToolMeta("reminders", "Reminders", "Custom reminder list"),
        ToolMeta("app_blocker", "App Blocker", "Block status, Focus/Strict mode state"),
        ToolMeta("nightly", "Nightly Protocol", "Historical AI plans & reports"),
        ToolMeta("health", "Health", "Steps, calories, sleep from Health Connect", defaultEnabled = false),
        // Action Tools
        ToolMeta("action_add_task", "Add Task", "Create new Google Task"),
        ToolMeta("action_complete_task", "Complete Task", "Mark a task as done"),
        ToolMeta("action_add_reminder", "Add Reminder", "Set custom reminder"),
        ToolMeta("action_start_focus", "Start Focus", "Begin Focus Mode session"),
        ToolMeta("action_start_tapasya", "Start Tapasya", "Begin Tapasya focus session"),
        ToolMeta("action_add_missed_tapasya", "Add Missed Tapasya", "Record a missed Tapasya session (Reason required)"),
        ToolMeta("action_schedule_notification", "Schedule Notification", "Schedule a lightweight push notification")
    )

    // --- Settings Helpers ---
    fun isToolEnabled(context: Context, toolId: String): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val tool = ALL_TOOLS.find { it.id == toolId } ?: return false
        return prefs.getBoolean("$KEY_PREFIX$toolId", tool.defaultEnabled)
    }

    fun setToolEnabled(context: Context, toolId: String, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("$KEY_PREFIX$toolId", enabled).apply()
    }

    fun getEnabledTools(context: Context): List<ToolMeta> {
        return ALL_TOOLS.filter { isToolEnabled(context, it.id) }
    }

    // --- Discovery Prompt (Minimal Tokens) ---
    /**
     * Returns a compact list of enabled tools for the system prompt.
     * Format: "tool_id: short description"
     * ~50 tokens for all 9 tools
     */
    fun getDiscoveryPrompt(context: Context): String {
        val enabled = getEnabledTools(context)
        if (enabled.isEmpty()) return "No data tools available."
        
        val sb = StringBuilder("Available tools (call get_tool_schema first):\n")
        enabled.forEach { t ->
            sb.append("- ${t.id}: ${t.shortDesc}\n")
        }
        return sb.toString()
    }

    // --- Full Schemas (On-Demand) ---
    /**
     * Returns the full OpenAI-format schema for a specific tool.
     * Only sent when AI explicitly requests it.
     */
    fun getToolSchema(toolId: String): JSONObject? {
        return when (toolId) {
            "gamification" -> createSchema(
                "get_xp_stats",
                "Get XP stats for a date. Returns level, streak, total XP, and breakdown (screen_time, reflection, tasks, tapasya, sessions, bonus, penalty).",
                mapOf(
                    "date" to "YYYY-MM-DD (default: today)",
                    "type" to "Optional: 'total' or 'breakdown'"
                )
            )
            "tapasya" -> createSchema(
                "get_tapasya_sessions",
                "Get Tapasya (focus) session history. Returns session list with duration, effective time, and XP earned.",
                mapOf(
                    "date" to "YYYY-MM-DD (default: today)",
                    "limit" to "Optional: max sessions to return (default: 5)"
                )
            )
            "tasks" -> createSchema(
                "get_tasks",
                "Get Google Tasks for a date. Returns pending/completed counts and task titles.",
                mapOf("date" to "YYYY-MM-DD (default: today)")
            )
            "study_sessions" -> createSchema(
                "get_calendar_events",
                "Get calendar events for a date. Returns event title, start/end time (IST), location.",
                mapOf("date" to "YYYY-MM-DD (default: today)")
            )
            "usage_stats" -> createSchema(
                "get_app_usage_stats",
                "Get app usage stats. Returns top 5 apps or specific app usage in minutes.",
                mapOf(
                    "date" to "YYYY-MM-DD (default: today)",
                    "package_name" to "Optional: specific app package"
                )
            )
            "reminders" -> createSchema(
                "get_reminders",
                "Get active custom reminders. Returns title and time (IST).",
                emptyMap()
            )
            "app_blocker" -> createSchema(
                "get_blocked_status",
                "Get blocking status. Returns Strict Mode state, Focus Mode state, and optionally checks if a specific app is blocked.",
                mapOf("package_name" to "Optional: check specific app")
            )
            "nightly" -> createSchema(
                "get_nightly_data",
                "Get historical Nightly Protocol data. Returns AI-generated plan or daily report.",
                mapOf(
                    "date" to "YYYY-MM-DD (default: today)",
                    "data_type" to "Required: 'plan' or 'report'"
                ),
                required = listOf("data_type")
            )
            "health" -> createSchema(
                "get_health_stats",
                "Get health data from Health Connect. Returns steps, calories, sleep duration.",
                mapOf("date" to "YYYY-MM-DD (default: today)")
            )
            // --- Action Tools ---
            "action_add_task" -> createSchema(
                "add_task",
                "Add a new task to Google Tasks. Ask user for title if not provided.",
                mapOf(
                    "title" to "Required: task title",
                    "notes" to "Optional: additional notes",
                    "due_date" to "Optional: YYYY-MM-DD"
                ),
                required = listOf("title")
            )
            "action_complete_task" -> createSchema(
                "complete_task",
                "Mark a task as completed. Ask user which task if not specified.",
                mapOf("task_title" to "Required: title of task to complete"),
                required = listOf("task_title")
            )
            "action_add_reminder" -> createSchema(
                "add_reminder",
                "Create a custom reminder. Ask for time if not specified.",
                mapOf(
                    "title" to "Required: reminder message",
                    "hour" to "Required: hour (0-23)",
                    "minute" to "Optional: minute (0-59, default: 0)"
                ),
                required = listOf("title", "hour")
            )
            "action_start_focus" -> createSchema(
                "start_focus",
                "Start Focus Mode to block distracting apps.",
                mapOf("duration_mins" to "Optional: duration in minutes (default: 25)")
            )
            "action_start_tapasya" -> createSchema(
                "start_tapasya",
                "Start a Tapasya deep focus session.",
                mapOf(
                    "name" to "Optional: session name",
                    "duration_mins" to "Optional: target duration in minutes"
                )
            )
            "action_add_missed_tapasya" -> createSchema(
                "add_missed_tapasya",
                "Record a Tapasya session that you completed but forgot to track. MUST ask for a valid reason first.",
                mapOf(
                    "name" to "Required: session name (e.g. 'Coding')",
                    "start_time" to "Required: HH:mm (IST)",
                    "end_time" to "Required: HH:mm (IST)",
                    "pause_mins" to "Optional: total minutes paused (default: 0)",
                    "reason" to "Required: Valid reason why it wasn't recorded live",
                    "date" to "Optional: YYYY-MM-DD (default: today)"
                ),
                required = listOf("name", "start_time", "end_time", "reason")
            )
            "action_schedule_notification" -> createSchema(
                "schedule_notification",
                "Schedule a lightweight push notification (informational only, no alarm sound). Use for gentle nudges or insights.",
                mapOf(
                    "title" to "Required: Notification title",
                    "message" to "Required: Notification body text",
                    "minutes_from_now" to "Required: Delay in minutes (min 1, max 1440)"
                ),
                required = listOf("title", "message", "minutes_from_now")
            )
            else -> null
        }
    }

    // --- Meta Tool: get_tool_schema ---
    val metaToolSchema = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", "get_tool_schema")
            put("description", "Get the full schema for a tool before using it. MUST call this first.")
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("tool_id", JSONObject().apply {
                        put("type", "string")
                        put("description", "Tool ID from the available list (e.g., 'gamification', 'tasks')")
                    })
                })
                put("required", JSONArray().put("tool_id"))
            })
        })
    }

    // --- Helper: Create Schema JSON ---
    private fun createSchema(
        name: String,
        description: String,
        params: Map<String, String>,
        required: List<String> = emptyList()
    ): JSONObject {
        return JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", name)
                put("description", description)
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        params.forEach { (key, desc) ->
                            put(key, JSONObject().apply {
                                put("type", "string")
                                put("description", desc)
                            })
                        }
                    })
                    if (required.isNotEmpty()) {
                        put("required", JSONArray(required))
                    }
                })
            })
        }
    }

    // --- Build Tool Array for AI Call ---
    /**
     * Builds the tools array for an AI API call.
     * For initial calls: Only includes meta tool (get_tool_schema)
     * After discovery: Includes requested tool schemas
     */
    fun buildToolsArray(context: Context, requestedToolIds: List<String> = emptyList()): JSONArray {
        val arr = JSONArray()
        
        // Always include meta tool for discovery
        arr.put(metaToolSchema)
        
        // Add requested tool schemas if any
        requestedToolIds.forEach { id ->
            if (isToolEnabled(context, id)) {
                getToolSchema(id)?.let { arr.put(it) }
            }
        }
        
        return arr
    }
    // --- Helper: Map Function Name to Tool ID ---
    fun getToolIdForFunction(functionName: String): String? {
        return when (functionName) {
            "get_xp_stats" -> "gamification"
            "get_tapasya_sessions" -> "tapasya"
            "get_tasks" -> "tasks"
            "get_calendar_events" -> "study_sessions"
            "get_app_usage_stats" -> "usage_stats"
            "get_reminders" -> "reminders"
            "get_blocked_status" -> "app_blocker"
            "get_nightly_data" -> "nightly"
            "get_health_stats" -> "health"
            "add_task" -> "action_add_task"
            "complete_task" -> "action_complete_task"
            "add_reminder" -> "action_add_reminder"
            "start_focus" -> "action_start_focus"
            "start_tapasya" -> "action_start_tapasya"
            "add_missed_tapasya" -> "action_add_missed_tapasya"
            "schedule_notification" -> "action_schedule_notification"
            else -> null
        }
    }
}
