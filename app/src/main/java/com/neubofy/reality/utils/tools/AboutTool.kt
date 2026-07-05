package com.neubofy.reality.utils.tools

import android.content.Context
import com.neubofy.reality.ui.activity.AboutActivity
import org.json.JSONObject

class AboutTool : AgentTool {
    override val id = "get_about_content"
    override val name = "App ABOUT"
    override val shortDesc = "Get the content of the app's ABOUT.md file"
    override val category = ToolCategory.UTILITY

    override fun getSchema(): JSONObject {
        return createSchema(
            "get_about_content",
            "Get the full content of the Reality app's ABOUT.md file from GitHub. Use this to find detailed information about the app's philosophy, developer, and mission.",
            emptyMap()
        )
    }

    override suspend fun execute(context: Context, args: JSONObject): String {
        return try {
            val url = java.net.URL(AboutActivity.ABOUT_MD_URL)
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
