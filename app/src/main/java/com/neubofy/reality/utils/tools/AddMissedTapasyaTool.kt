package com.neubofy.reality.utils.tools

import android.content.Context
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.data.db.TapasyaSession
import com.neubofy.reality.utils.TerminalLogger
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

class AddMissedTapasyaTool : AgentTool {
    override val id = "action_add_missed_tapasya"
    override val name = "Add Missed Tapasya"
    override val shortDesc = "Record a missed Tapasya session (Reason required)"
    override val category = ToolCategory.ACTION

    override fun getSchema(): JSONObject {
        return createSchema(
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
    }

    override suspend fun execute(context: Context, args: JSONObject): String {
        val name = args.optString("name", "Deep Focus")
        val startTimeStr = args.optString("start_time", "")
        val endTimeStr = args.optString("end_time", "")
        val reason = args.optString("reason", "")
        val dateStr = args.optString("date", LocalDate.now().toString())
        val pauseMins = args.optInt("pause_mins", 0)

        if (reason.length < 5) {
            return "I need a valid reason why this session wasn't recorded live. Please explain."
        }
        if (startTimeStr.isEmpty() || endTimeStr.isEmpty()) {
            return "Please provide both start and end times (HH:mm)."
        }

        try {
            val date = LocalDate.parse(dateStr)
            val istZone = ZoneId.of("Asia/Kolkata")

            val startParts = startTimeStr.split(":")
            val endParts = endTimeStr.split(":")

            val startInstant = date.atTime(startParts[0].toInt(), startParts[1].toInt())
                .atZone(istZone).toInstant()
            val endInstant = date.atTime(endParts[0].toInt(), endParts[1].toInt())
                .atZone(istZone).toInstant()

            var startMs = startInstant.toEpochMilli()
            var endMs = endInstant.toEpochMilli()

            if (endMs <= startMs) {
                return "End time must be after start time."
            }

            val totalDurationMs = endMs - startMs
            val pauseMs = pauseMins * 60 * 1000L
            val effectiveMs = TapasyaSession.calculateEffectiveTime(totalDurationMs - pauseMs)

            if (effectiveMs <= 0) {
                return "Effective duration is too short (less than 15 mins) after removing pauses."
            }

            val session = TapasyaSession(
                sessionId = TapasyaSession.generateId(startMs, endMs),
                name = name,
                targetTimeMs = totalDurationMs,
                startTime = startMs,
                endTime = endMs,
                effectiveTimeMs = effectiveMs,
                totalPauseMs = pauseMs,
                pauseLimitMs = -1,
                wasAutoStopped = false,
                calendarEventId = null
            )

            val db = AppDatabase.getDatabase(context)
            db.tapasyaSessionDao().insert(session)

            TerminalLogger.log("Manual Tapasya Added: '$name' ($reason)")

            val durMins = totalDurationMs / 60000
            val effMins = effectiveMs / 60000
            return "✅ Added missed focus session: \"$name\" ($durMins mins total, $effMins mins effective score).\nReason logged: $reason"

        } catch (e: Exception) {
            return "❌ Error parsing time or saving: ${e.message}. Use HH:mm format."
        }
    }
}
