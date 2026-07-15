package com.neubofy.reality.utils.tools

import android.content.Context
import android.content.Intent
import com.neubofy.reality.blockers.RealityBlocker
import com.neubofy.reality.services.AppBlockerService
import com.neubofy.reality.utils.SavedPreferencesLoader
import com.neubofy.reality.utils.SecureTimeProvider
import org.json.JSONObject

class StartFocusTool : AgentTool {
    override val id = "action_start_focus"
    override val name = "Start Focus"
    override val shortDesc = "Begin Focus Mode session"
    override val category = ToolCategory.ACTION

    override fun getSchema(): JSONObject {
        return createSchema(
            "action_start_focus",
            "Start Focus Mode to block distracting apps.",
            mapOf("duration_mins" to "Optional: duration in minutes (default: 25)")
        )
    }

    override suspend fun execute(context: Context, args: JSONObject): String {
        val durationMins = args.optInt("duration_mins", 25)

        val prefs = SavedPreferencesLoader(context)
        val endTime = SecureTimeProvider.currentTimeMillis(context) + (durationMins * 60 * 1000L)

        val currentData = prefs.getFocusModeData()
        val newData = RealityBlocker.FocusModeData(
            endTime = endTime,
            isTurnedOn = true,
            selectedApps = currentData.selectedApps
        )
        prefs.saveFocusModeData(newData)

        val intent = Intent(context, AppBlockerService::class.java)
        context.startService(intent)

        return "✅ Focus Mode started for $durationMins minutes. Distracting apps are now blocked."
    }
}
