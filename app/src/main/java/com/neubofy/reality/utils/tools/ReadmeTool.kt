package com.neubofy.reality.utils.tools

import android.content.Context
import org.json.JSONObject

class ReadmeTool : AgentTool {
    override val id = "get_readme_content"
    override val name = "App README"
    override val shortDesc = "Get the content of the app's README.md file"
    override val category = ToolCategory.UTILITY

    override fun getSchema(): JSONObject {
        return createSchema(
            "get_readme_content",
            "Get the full content of the Reality app's README.md file from GitHub. Use this to find general information, features, and setup instructions about the app.",
            emptyMap()
        )
    }

    override suspend fun execute(context: Context, args: JSONObject): String {
        return try {
            val url = java.net.URL("https://raw.githubusercontent.com/pawanwashudev-official/Reality/main/README.md")
            val content = url.readText()
            JSONObject().apply {
                put("success", true)
                put("content", content)
            }.toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("success", false)
                put("error", e.message)
            }.toString()
        }
    }
}
