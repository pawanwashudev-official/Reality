package com.neubofy.reality.utils

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Agent Tools: Execution Layer
 * 
 * Uses ToolRegistry for definitions.
 * All times formatted in IST (Asia/Kolkata).
 */
object AgentTools {

    // Legacy compatibility: Full definitions (for old code paths)
    // New code should use ToolRegistry.buildToolsArray()
    val definitions: JSONArray
        get() = JSONArray().apply {
            ToolRegistry.ALL_TOOLS.forEach { tool ->
                ToolRegistry.getToolSchema(tool.id)?.let { put(it) }
            }
        }

    // --- Execution Logic ---
    suspend fun execute(context: Context, name: String, argsInfo: String): String {
        return try {
            val args = JSONObject(if (argsInfo.isBlank()) "{}" else argsInfo)
            
            // 1. Check if tool is enabled (Security/Privacy)
            val recognizedId = ToolRegistry.getToolIdForFunction(name)
            if (recognizedId != null && !ToolRegistry.isToolEnabled(context, recognizedId)) {
                 return "⚠️ Tool is disabled: $name. Please enable '${ToolRegistry.ALL_TOOLS.find { it.id == recognizedId }?.name ?: recognizedId}' in AI Settings."
            }

            // 2. Handle Meta Tool
            if (name == "get_tool_schema") {
                val toolId = args.optString("tool_id", "")
                if (toolId.isEmpty()) return "Error: tool_id is required"
                
                if (!ToolRegistry.isToolEnabled(context, toolId)) {
                    return "Tool '$toolId' is disabled. Enable it in AI Settings."
                }
                
                val schema = ToolRegistry.getToolSchema(toolId)
                return schema?.toString(2) ?: "Unknown tool: $toolId"
            }

            // 3. Find and execute requested tool
            val tool = ToolRegistry.getTool(name)
            if (tool != null) {
                return tool.execute(context, args)
            } else {
                return "Unknown tool: $name. Use get_tool_schema to discover available tools."
            }
        } catch (e: Exception) {
            "Tool Error: ${e.message}"
        }
    }
    
    /**
     * Downloads an image from URL and saves it to Pictures/Reality folder.
     * Uses MediaStore for proper gallery integration on Android 10+.
     */
    private fun saveImageToGallery(context: Context, imageUrl: String, timestamp: Long): String {
        val fileName = "reality_img_$timestamp.jpg"
        
        // Use app-specific files directory to avoid any permission requirements
        val directory = java.io.File(context.filesDir, "Images")
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val file = java.io.File(directory, fileName)

        val connection = java.net.URL(imageUrl).openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 30000
        connection.readTimeout = 30000
        connection.connect()

        if (connection.responseCode == 200) {
            java.io.FileOutputStream(file).use { outputStream ->
                connection.inputStream.use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            TerminalLogger.log("IMAGE: Saved to internal app storage - $fileName")
            return file.absolutePath
        } else {
            throw Exception("HTTP Error: ${connection.responseCode}")
        }
    }

    /**
     * Extracts a URL (preferably an image URL) from a string.
     * Supports Markdown format ![alt](url) and raw URLs.
     */
    private fun extractImageUrl(content: String): String {
        if (content.isEmpty()) return ""
        
        // 1. Try Markdown Image Pattern: ![description](url)
        val markdownRegex = Regex("""!\[.*?\]\((https?://\S+)\)""")
        val match = markdownRegex.find(content)
        if (match != null) return match.groupValues[1]
        
        // 2. Try Naked URL Pattern
        val urlRegex = Regex("""(https?://\S+)""")
        val urlMatch = urlRegex.find(content)
        if (urlMatch != null) {
             val url = urlMatch.groupValues[1]
             // Basic check if it looks like an image URL or if it's the only thing in the response
             if (url.contains(Regex("""\.(jpg|jpeg|png|webp|gif)""", RegexOption.IGNORE_CASE)) || content.trim() == url) {
                 return url
             }
        }
        
        return ""
    }
}
