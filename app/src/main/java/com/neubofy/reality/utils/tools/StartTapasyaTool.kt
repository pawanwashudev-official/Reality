package com.neubofy.reality.utils.tools

import android.content.Context
import android.content.Intent
import com.neubofy.reality.ui.activity.TapasyaActivity
import org.json.JSONObject

class StartTapasyaTool : AgentTool {
    override val id = "action_start_tapasya"
    override val name = "Start Tapasya"
    override val shortDesc = "Begin Tapasya focus session"
    override val category = ToolCategory.ACTION

    override fun getSchema(): JSONObject {
        return createSchema(
            "action_start_tapasya",
            "Start a Tapasya deep focus session.",
            mapOf(
                "name" to "Optional: session name",
                "duration_mins" to "Optional: target duration in minutes"
            )
        )
    }

    override suspend fun execute(context: Context, args: JSONObject): String {
        val name = args.optString("name", "Deep Focus")
        val durationMins = args.optInt("duration_mins", 0)

        val intent = Intent(context, TapasyaActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("auto_start", true)
            putExtra("session_name", name)
            if (durationMins > 0) {
                putExtra("target_duration_mins", durationMins)
            }
        }
        context.startActivity(intent)

        val durationText = if (durationMins > 0) " ($durationMins min target)" else ""
        return "✅ Starting Tapasya session: \"$name\"$durationText. Opening Tapasya screen..."
    }
}
