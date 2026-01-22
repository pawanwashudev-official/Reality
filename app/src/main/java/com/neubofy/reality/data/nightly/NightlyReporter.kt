package com.neubofy.reality.data.nightly

import android.content.Context
import com.neubofy.reality.data.db.NightlySession
import com.neubofy.reality.data.repository.NightlyRepository
import com.neubofy.reality.utils.NightlyAIHelper
import com.neubofy.reality.data.model.DaySummary
import com.neubofy.reality.ui.activity.AISettingsActivity
import java.time.LocalDate

class NightlyReporter(private val context: Context) {

    suspend fun generateReport(
        date: LocalDate, 
        daySummary: DaySummary,
        reflectionContent: String,
        planContent: String
    ): String {
        // Fetch AI Settings
        val userIntro = AISettingsActivity.getUserIntroduction(context) ?: ""
        val nightlyModel = AISettingsActivity.getNightlyModel(context)
        
        if (nightlyModel.isNullOrEmpty()) {
            return "Report Unavailable (No AI Model Configured)"
        }

        // Fetch XP Stats from DB (Step 11 requirement)
        val xpStats = com.neubofy.reality.utils.XPManager.getDailyStats(context, date.toString())

        // AI Generation
        val report = try {
            NightlyAIHelper.generateReportSummary(
                context, 
                nightlyModel, 
                userIntro, 
                daySummary,
                xpStats,
                reflectionContent, 
                planContent
            )
        } catch (e: Exception) {
            "Report generation error: ${e.message}"
        }
        
        // Save to DB
        NightlyRepository.saveReportContent(context, date, report)
        
        return report
    }
}
