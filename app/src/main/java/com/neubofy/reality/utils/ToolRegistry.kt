package com.neubofy.reality.utils

import android.content.Context
import com.neubofy.reality.utils.tools.*
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
        val shortDesc: String,
        val defaultEnabled: Boolean = true,
        val category: ToolCategory = ToolCategory.DATA
    )

    private val toolsList: List<AgentTool> = listOf(
        // Data Tools
        GamificationTool(),
        TapasyaTool(),
        TasksTool(),
        StudySessionsTool(),
        UsageStatsTool(),

        AppBlockerTool(),
        NightlyTool(),
        HealthTool(),

        // Utility Tools
        TimeTool(),
        ReadmeTool(),
        AboutTool(),
        UniversalQueryTool(),

        // Action Tools
        AddTaskTool(),
        CompleteTaskTool(),

        SetAlarmTool(),
        StartFocusTool(),
        StartTapasyaTool(),
        AddMissedTapasyaTool()
    )

    val ALL_TOOLS: List<ToolMeta> = toolsList.map {
        ToolMeta(it.id, it.name, it.shortDesc, it.defaultEnabled, it.category)
    }

    fun getTool(toolId: String): AgentTool? {
        val recognizedId = getToolIdForFunction(toolId) ?: toolId
        return toolsList.find { it.id == recognizedId }
    }

    // --- Settings Helpers ---
    fun isToolEnabled(context: Context, toolId: String): Boolean {
        val prefs = com.neubofy.reality.utils.SecurePreferences.get(context, PREFS_NAME)
        val tool = getTool(toolId) ?: return false
        return prefs.getBoolean("$KEY_PREFIX${tool.id}", tool.defaultEnabled)
    }

    fun setToolEnabled(context: Context, toolId: String, enabled: Boolean) {
        val prefs = com.neubofy.reality.utils.SecurePreferences.get(context, PREFS_NAME)
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
        if (enabled.isEmpty()) return "No tools available."

        val sb = StringBuilder("AVAILABLE TOOLS BY CATEGORY:\n")
        
        val dataTools = enabled.filter { it.category == ToolCategory.DATA }
        if (dataTools.isNotEmpty()) {
            sb.append("\n[DATA TOOLS] - Use these to fetch user stats, history, and context:\n")
            dataTools.forEach { t -> sb.append("- ${t.id}: ${t.shortDesc}\n") }
        }

        val actionTools = enabled.filter { it.category == ToolCategory.ACTION }
        if (actionTools.isNotEmpty()) {
            sb.append("\n[ACTION TOOLS] - Use these to perform actions or modify user state:\n")
            actionTools.forEach { t -> sb.append("- ${t.id}: ${t.shortDesc}\n") }
        }

        val utilityTools = enabled.filter { it.category == ToolCategory.UTILITY }
        if (utilityTools.isNotEmpty()) {
            sb.append("\n[UTILITY TOOLS] - Use these for general app information and utilities:\n")
            utilityTools.forEach { t -> sb.append("- ${t.id}: ${t.shortDesc}\n") }
        }

        sb.append("\nPROCEDURE:")
        sb.append("\n1. Initially, you ONLY have the `get_tool_schema(tool_id)` tool.")
        sb.append("\n2. Review the categories above. If a tool sounds like it can solve the user's request, call `get_tool_schema` with its ID to learn how to use it.")
        sb.append("\n3. Once you get the schema, it becomes available in the NEXT turn.")
        
        return sb.toString()
    }

    fun getToolSchema(toolId: String): JSONObject? {
        return getTool(toolId)?.getSchema()
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
            "get_readme_content", "get_readme" -> "get_readme_content"
            "get_about_content", "get_about" -> "get_about_content"
            "action_add_task", "add_task" -> "action_add_task"
            "action_complete_task", "complete_task" -> "action_complete_task"
            "action_add_reminder", "add_reminder" -> "action_add_reminder"
            "action_set_alarm", "set_alarm" -> "action_set_alarm"
            "action_start_focus", "start_focus" -> "action_start_focus"
            "action_start_tapasya", "start_tapasya" -> "action_start_tapasya"
            "action_add_missed_tapasya", "add_missed_tapasya" -> "action_add_missed_tapasya"
            "action_schedule_notification", "schedule_notification" -> "action_schedule_notification"
            else -> if (ALL_TOOLS.any { it.id == functionName }) functionName else null
        }
    }
}
