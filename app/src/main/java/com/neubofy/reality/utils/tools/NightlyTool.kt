package com.neubofy.reality.utils.tools

import android.content.Context
import com.neubofy.reality.data.NightlyProtocolExecutor
import org.json.JSONObject
import org.json.JSONTokener
import java.time.LocalDate

class NightlyTool : AgentTool {
    override val id = "nightly"
    override val name = "Nightly Protocol"
    override val shortDesc = "AI plans, reports, history access"
    override val category = ToolCategory.DATA

    override fun getSchema(): JSONObject {
        return createSchema(
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
    }

    override suspend fun execute(context: Context, args: JSONObject): String {
        val dateStr = args.optString("date", "")
        val date = if (dateStr.isNotEmpty()) LocalDate.parse(dateStr) else LocalDate.now()
        val dataType = args.optString("data_type")

        val stepId = when(dataType) {
            "plan" -> NightlyProtocolExecutor.STEP_GENERATE_PLAN
            "report" -> NightlyProtocolExecutor.STEP_GENERATE_REPORT
            else -> return "Error: data_type must be 'plan' or 'report'"
        }

        var data = NightlyProtocolExecutor.mockLoadStepDataForAgent(context, date, stepId)

        // Fallback: If asking for today's plan, check yesterday (plan is made night before)
        if (data == null && dataType == "plan" && date == LocalDate.now()) {
            data = NightlyProtocolExecutor.mockLoadStepDataForAgent(context, date.minusDays(1), stepId)
        }

        if (data.isNullOrEmpty()) return "No $dataType data found for $date."

        try {
            if (data.trim().startsWith("{")) return JSONObject(JSONTokener(data)).toString()
            return data
        } catch (e: Exception) { return data }
    }
}
