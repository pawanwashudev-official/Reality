package com.neubofy.reality.utils

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId


object SleepInferenceHelper {



    /**
     * Auto-confirm sleep when alarm is auto-dismissed (not manually interacted with).
     * Infers sleep and writes directly to Health Connect without user confirmation.
     */
    suspend fun autoConfirmSleep(context: Context) {
        val loader = SavedPreferencesLoader(context)
        if (!loader.isSmartSleepEnabled()) {
            TerminalLogger.log("AutoConfirmSleep: Smart Sleep disabled")
            return
        }
        
        val today = java.time.LocalDate.now()
        val healthManager = com.neubofy.reality.health.HealthManager(context)
        
        // Force inference (skip timing guard for auto-confirm since alarm already fired)
        val bedtimeData = loader.getBedtimeData()
        val planStartMins = bedtimeData.startTimeInMins
        val planEndMins = bedtimeData.endTimeInMins
        
        val planEndTime = today.atTime(planEndMins / 60, planEndMins % 60)
            .atZone(java.time.ZoneId.systemDefault()).toInstant()
        val planStartTime = if (planStartMins > planEndMins) {
            today.minusDays(1).atTime(planStartMins / 60, planStartMins % 60)
                .atZone(java.time.ZoneId.systemDefault()).toInstant()
        } else {
            today.atTime(planStartMins / 60, planStartMins % 60)
                .atZone(java.time.ZoneId.systemDefault()).toInstant()
        }
        
        // Use planned bedtime as auto-confirm (user didn't interact, assume plan was followed)
        TerminalLogger.log("AutoConfirmSleep: Auto-confirming planned sleep ${planStartTime} to ${planEndTime}")
        
        try {
            healthManager.deleteSleepSessions(
                planStartTime.minus(java.time.Duration.ofHours(2)),
                planEndTime.plus(java.time.Duration.ofHours(2))
            )
            healthManager.writeSleepSession(planStartTime, planEndTime)
            TerminalLogger.log("AutoConfirmSleep: Successfully synced to Health Connect")
        } catch (e: Exception) {
            TerminalLogger.log("AutoConfirmSleep: Error - ${e.message}")
        }
    }
}
