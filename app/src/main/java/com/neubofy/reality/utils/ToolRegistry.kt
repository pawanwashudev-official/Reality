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
        ToolMeta("gamification", "XP & Stats", "XP, levels, streaks, and daily progress breakdown"),
        ToolMeta("tapasya", "Deep Focus", "Focus session history, session search, and deep work stats"),
        ToolMeta("tasks", "Google Tasks", "Active and recently completed tasks from Google"),
        ToolMeta("study_sessions", "Academic Calendar", "Study sessions and calendar events with search"),
        ToolMeta("usage_stats", "Usage Stats", "App usage with categories, comparisons"),
        ToolMeta("reminders", "Reminders", "Custom reminders with time filters"),
        ToolMeta("app_blocker", "App Blocker", "Block status, schedules, app lists"),
        ToolMeta("nightly", "Nightly Protocol", "AI plans, reports, history access"),
        ToolMeta("health", "Health", "Steps, calories, sleep with trends", defaultEnabled = false),
        // Utility Tools
        ToolMeta("utility_time", "Current Time", "Get current date/time in IST"),
        ToolMeta("web_search", "Tavily Web Search", "Real-time internet search for facts and news"),
        // Power Tool
        ToolMeta("universal_query", "Smart Data Query", "Advanced cross-source querying with filters"),
        // Action Tools
        ToolMeta("action_add_task", "Add Task", "Create new Google Task"),
        ToolMeta("action_complete_task", "Complete Task", "Mark a task as done"),
        ToolMeta("action_add_reminder", "Add Reminder", "Set custom reminder"),
        ToolMeta("action_start_focus", "Start Focus", "Begin Focus Mode session"),
        ToolMeta("action_start_tapasya", "Start Tapasya", "Begin Tapasya focus session"),
        ToolMeta("action_add_missed_tapasya", "Add Missed Tapasya", "Record a missed Tapasya session (Reason required)"),
        ToolMeta("action_schedule_notification", "Schedule Notification", "Schedule a lightweight push notification"),
        // Creative Tools
        ToolMeta("action_generate_image", "Generate Image", "Create AI-generated images from text prompts (FREE)")
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
        
        val sb = StringBuilder("AVAILABLE TOOLS (IDs):\n")
        enabled.forEach { t ->
            sb.append("- ${t.id}: ${t.shortDesc}\n")
        }
        sb.append("\nPROCEDURE:")
        sb.append("\n1. Initially, you ONLY have the `get_tool_schema(tool_id)` tool.")
        sb.append("\n2. To use ANY tool above, MUST call `get_tool_schema` first.")
        sb.append("\n3. Once you get the schema, it becomes available in the NEXT turn.")
        sb.append("\n4. CRITICAL: `web_search` is EXPENSIVE. Consolidate ALL information needs into ONE query per request.")
        
        return sb.toString()
    }

    /**
     * Returns the full OpenAI-format schema for a specific tool.
     * Only sent when AI explicitly requests it.
     * ENHANCED: Smart querying with filters, date ranges, and targeted data access.
     */
    fun getToolSchema(toolId: String): JSONObject? {
        return when (toolId) {
            // ==================== ENHANCED DATA TOOLS ====================
            
            "gamification" -> createSchema(
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
            
            "tapasya" -> createSchema(
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
            
            "tasks" -> createSchema(
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
            
            "study_sessions" -> createSchema(
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
            
            "usage_stats" -> createSchema(
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
            
            "reminders" -> createSchema(
                "reminders",
                "Get custom reminders with filtering.",
                mapOf(
                    "filter" to "Optional: 'active', 'all', 'today' (default: active)",
                    "search" to "Optional: Search reminder titles",
                    "time_range" to "Optional: 'morning', 'afternoon', 'evening'"
                )
            )
            
            "app_blocker" -> createSchema(
                "app_blocker",
                "Get blocking status with detailed info.",
                mapOf(
                    "package_name" to "Optional: Check specific app",
                    "app_name" to "Optional: Search app by name",
                    "include_schedule" to "Optional: 'true' to include block schedules",
                    "list_blocked" to "Optional: 'true' to list all blocked apps"
                )
            )
            
            "nightly" -> createSchema(
                "nightly",
                "Get Nightly Protocol AI plans and reports with history access.",
                mapOf(
                    "date" to "YYYY-MM-DD (default: today)",
                    "data_type" to "Required: 'plan', 'report', or 'both'",
                    "include_history" to "Optional: number of past days to include",
                    "summary_only" to "Optional: 'true' for brief summary"
                ),
                required = listOf("data_type")
            )
            
            "health" -> createSchema(
                "health",
                "Get health data with date range and metric selection.",
                mapOf(
                    "date" to "YYYY-MM-DD (default: today)",
                    "date_range" to "Optional: 'today', 'week', 'month'",
                    "metrics" to "Optional: 'steps', 'calories', 'sleep', 'all' (default: all)",
                    "include_goals" to "Optional: 'true' to include goal progress",
                    "comparison" to "Optional: 'yesterday', 'last_week'"
                )
            )
            
            // NEW: Universal Query Tool
            "universal_query" -> createSchema(
                "universal_query",
                "Powerful universal data query tool. Fetch targeted data from any source with natural language or structured queries. Use this when you need specific, filtered data from multiple sources.",
                mapOf(
                    "sources" to "Required: Comma-separated list: 'tasks,tapasya,calendar,usage,xp,reminders,health'",
                    "query" to "Optional: Natural language query (AI will parse)",
                    "date_range" to "Optional: 'today', 'yesterday', 'week', 'month', 'custom'",
                    "start_date" to "For custom range: YYYY-MM-DD",
                    "end_date" to "For custom range: YYYY-MM-DD",
                    "filters" to "Optional: JSON object with source-specific filters",
                    "format" to "Optional: 'summary', 'detailed', 'stats' (default: summary)",
                    "limit" to "Max items per source (default: 5)"
                ),
                required = listOf("sources")
            )
            
            // --- Utility Tools ---
            "utility_time" -> createSchema(
                "utility_time",
                "Get current date and time in IST (India Standard Time). Use this when you need to know the current time for scheduling, reminders, or time-based responses.",
                mapOf(
                    "include_date" to "Optional: 'true' to include full date (default: true)",
                    "format" to "Optional: 'full', 'time_only', 'date_only' (default: full)"
                )
            )

            "web_search" -> createSchema(
                "web_search",
                "Search the internet for real-time information using Tavily. EXPENSIVE: Use only as a last resort. Consolidate research into ONE single turn. Never repeat searches.",
                mapOf(
                    "query" to "Required: One comprehensive search query",
                    "max_results" to "Optional: Number of results (1-5, default: 3)"
                ),
                required = listOf("query")
            )
            
            // --- Action Tools ---
            "action_add_task" -> createSchema(
                "action_add_task",
                "Add a new task to Google Tasks. Ask user for title if not provided.",
                mapOf(
                    "title" to "Required: task title",
                    "notes" to "Optional: additional notes",
                    "due_date" to "Optional: YYYY-MM-DD"
                ),
                required = listOf("title")
            )
            "action_complete_task" -> createSchema(
                "action_complete_task",
                "Mark a task as completed. Ask user which task if not specified.",
                mapOf("task_title" to "Required: title of task to complete"),
                required = listOf("task_title")
            )
            "action_add_reminder" -> createSchema(
                "action_add_reminder",
                "Create a custom reminder. Ask for time if not specified.",
                mapOf(
                    "title" to "Required: reminder message",
                    "hour" to "Required: hour (0-23)",
                    "minute" to "Optional: minute (0-59, default: 0)"
                ),
                required = listOf("title", "hour")
            )
            "action_start_focus" -> createSchema(
                "action_start_focus",
                "Start Focus Mode to block distracting apps.",
                mapOf("duration_mins" to "Optional: duration in minutes (default: 25)")
            )
            "action_start_tapasya" -> createSchema(
                "action_start_tapasya",
                "Start a Tapasya deep focus session.",
                mapOf(
                    "name" to "Optional: session name",
                    "duration_mins" to "Optional: target duration in minutes"
                )
            )
            "action_add_missed_tapasya" -> createSchema(
                "action_add_missed_tapasya",
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
                "action_schedule_notification",
                "Schedule a lightweight push notification (informational only, no alarm sound). Use for gentle nudges or insights.",
                mapOf(
                    "title" to "Required: Notification title",
                    "message" to "Required: Notification body text",
                    "minutes_from_now" to "Required: Delay in minutes (min 1, max 1440)"
                ),
                required = listOf("title", "message", "minutes_from_now")
            )
            "action_generate_image" -> createSchema(
                "action_generate_image",
                "Generate an AI image from a text prompt. Uses free Pollinations.ai. Image is displayed in chat and saved to Pictures/Reality folder. Be creative and detailed with prompts for best results.",
                mapOf(
                    "prompt" to "Required: Detailed description of the image to generate (be specific about style, colors, mood)",
                    "style" to "Optional: Art style (e.g., 'realistic', 'anime', 'watercolor', 'minimalist', 'cyberpunk')",
                    "save_to_gallery" to "Optional: 'true' to save to phone gallery (default: true)"
                ),
                required = listOf("prompt")
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
            "gamification", "get_xp_stats" -> "gamification"
            "tapasya", "get_tapasya_sessions" -> "tapasya"
            "tasks", "get_tasks" -> "tasks"
            "study_sessions", "get_calendar_events" -> "study_sessions"
            "usage_stats", "get_app_usage_stats" -> "usage_stats"
            "reminders", "get_reminders" -> "reminders"
            "app_blocker", "get_blocked_status" -> "app_blocker"
            "nightly", "get_nightly_data" -> "nightly"
            "health", "get_health_stats" -> "health"
            "universal_query", "query_data" -> "universal_query"
            "utility_time", "get_current_time" -> "utility_time"
            "web_search", "perform_web_search" -> "web_search"
            "action_add_task", "add_task" -> "action_add_task"
            "action_complete_task", "complete_task" -> "action_complete_task"
            "action_add_reminder", "add_reminder" -> "action_add_reminder"
            "action_start_focus", "start_focus" -> "action_start_focus"
            "action_start_tapasya", "start_tapasya" -> "action_start_tapasya"
            "action_add_missed_tapasya", "add_missed_tapasya" -> "action_add_missed_tapasya"
            "action_schedule_notification", "schedule_notification" -> "action_schedule_notification"
            "action_generate_image", "generate_image" -> "action_generate_image"
            else -> if (ALL_TOOLS.any { it.id == functionName }) functionName else null
        }
    }
}
