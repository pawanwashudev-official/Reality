package com.neubofy.reality.utils.tools

import android.content.Context
import com.neubofy.reality.utils.SavedPreferencesLoader
import com.neubofy.reality.utils.SecureTimeProvider
import com.neubofy.reality.utils.ToolRegistry
import org.json.JSONArray
import org.json.JSONObject

class AppBlockerTool : AgentTool {
    override val id = "app_blocker"
    override val name = "App Blocker"
    override val shortDesc = "Block status, schedules, app lists"
    override val category = ToolCategory.DATA

    override fun getSchema(): JSONObject {
        return createSchema(
            "app_blocker",
            "Get blocking status with detailed info.",
            mapOf(
                "package_name" to "Optional: Check specific app",
                "app_name" to "Optional: Search app by name",
                "include_schedule" to "Optional: 'true' to include block schedules",
                "list_blocked" to "Optional: 'true' to list all blocked apps"
            )
        )
    }

    override suspend fun execute(context: Context, args: JSONObject): String {
        val pkg = args.optString("package_name", "")
        val prefs = SavedPreferencesLoader(context)

        val isStrict = prefs.isStrictModeEnabled()
        val focusData = prefs.getFocusModeData()
        val isFocus = focusData.endTime > SecureTimeProvider.currentTimeMillis(context)

        val json = JSONObject().apply {
            put("strict_mode", isStrict)
            put("focus_mode", isFocus)
            if (isFocus) {
                put("focus_ends_at", ToolRegistry.formatIST(focusData.endTime))
            }
        }

        if (pkg.isNotEmpty()) {
            var isBlocked = false
            val reasons = mutableListOf<String>()

            if (isStrict) {
                val blockedApps = prefs.loadBlockedApps()
                if (blockedApps.contains(pkg)) {
                    isBlocked = true
                    reasons.add("Strict Mode blocklist")
                }
            }

            if (isFocus) {
                val config = prefs.getBlockedAppConfig(pkg)
                if (config.blockInFocus) {
                    val selected = if (focusData.selectedApps.isNotEmpty()) focusData.selectedApps else HashSet(prefs.getFocusModeSelectedApps())
                    if (selected.contains(pkg)) {
                        isBlocked = true
                        reasons.add("Focus Mode")
                    }
                }
            }

            json.put("app_checked", pkg)
            json.put("is_blocked", isBlocked)
            json.put("block_reasons", JSONArray(reasons))
        }

        return json.toString()
    }
}
