package com.neubofy.reality.data.repository

import android.content.Context
import com.neubofy.reality.data.NightlyProtocolExecutor
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.data.db.NightlySession
import com.neubofy.reality.data.db.NightlyStep
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Extended step data including result JSON for persistence
 */
data class StepData(
    val status: Int,
    val details: String?,
    val resultJson: String?,
    val linkUrl: String?
) {
    companion object {
        fun pending() = StepData(NightlyProtocolExecutor.StepProgress.STATUS_PENDING, null, null, null)
    }
}

object NightlyRepository {

    /**
     * Load full step state including resultJson for data restoration
     */
    suspend fun loadStepData(context: Context, date: LocalDate, step: Int): StepData {
        val db = AppDatabase.getDatabase(context)
        val dateStr = formatDate(date)
        val entity = db.nightlyDao().getStep(dateStr, step)
        
        return if (entity != null) {
            StepData(entity.status, entity.details, entity.resultJson, entity.linkUrl)
        } else {
            StepData.pending()
        }
    }
    
    /**
     * Legacy method for backward compatibility
     */
    suspend fun loadStepState(context: Context, date: LocalDate, step: Int): NightlyProtocolExecutor.StepProgress {
        val data = loadStepData(context, date, step)
        return NightlyProtocolExecutor.StepProgress(data.status, data.details)
    }

    /**
     * Save step state with full data including resultJson for persistence
     */
    suspend fun saveStepState(
        context: Context, 
        date: LocalDate, 
        step: Int, 
        status: Int, 
        details: String?,
        resultJson: String? = null,
        linkUrl: String? = null
    ) {
        val db = AppDatabase.getDatabase(context)
        val dateStr = formatDate(date)
        
        // Ensure session exists
        val session = db.nightlyDao().getSession(dateStr)
        if (session == null) {
            db.nightlyDao().insertOrUpdateSession(
                NightlySession(
                    date = dateStr,
                    startTime = System.currentTimeMillis()
                )
            )
        }
        
        val entity = NightlyStep(
            sessionDate = dateStr,
            stepId = step,
            status = status,
            details = details,
            resultJson = resultJson,
            linkUrl = linkUrl,
            updatedAt = System.currentTimeMillis()
        )
        db.nightlyDao().insertOrUpdateStep(entity)
    }
    
    /**
     * Quick helper to get just the resultJson for a step
     */
    suspend fun getStepResultJson(context: Context, date: LocalDate, step: Int): String? {
        val db = AppDatabase.getDatabase(context)
        val dateStr = formatDate(date)
        return db.nightlyDao().getStep(dateStr, step)?.resultJson
    }

    // Helper for other stats
    suspend fun getPlanDocId(context: Context, date: LocalDate): String? {
        val db = AppDatabase.getDatabase(context)
        val dateStr = formatDate(date)
        return db.nightlyDao().getSession(dateStr)?.planDocId
    }

    suspend fun savePlanDocId(context: Context, date: LocalDate, docId: String?) {
        val db = AppDatabase.getDatabase(context)
        val dateStr = formatDate(date)
        val session = db.nightlyDao().getSession(dateStr)
        if (session != null) {
            db.nightlyDao().insertOrUpdateSession(session.copy(planDocId = docId))
        } else {
             db.nightlyDao().insertOrUpdateSession(
                NightlySession(
                    date = dateStr,
                    startTime = System.currentTimeMillis(),
                    planDocId = docId
                )
            )
        }
    }
    
    suspend fun getDiaryDocId(context: Context, date: LocalDate): String? {
        val db = AppDatabase.getDatabase(context)
        val dateStr = formatDate(date)
        return db.nightlyDao().getSession(dateStr)?.diaryDocId
    }

    suspend fun saveDiaryDocId(context: Context, date: LocalDate, docId: String?) {
        val db = AppDatabase.getDatabase(context)
        val dateStr = formatDate(date)
        val session = db.nightlyDao().getSession(dateStr)
        if (session != null) {
            db.nightlyDao().insertOrUpdateSession(session.copy(diaryDocId = docId))
        } else {
             db.nightlyDao().insertOrUpdateSession(
                NightlySession(
                    date = dateStr,
                    startTime = System.currentTimeMillis(),
                    diaryDocId = docId
                )
            )
        }
    }

    suspend fun clearDiaryDocId(context: Context, date: LocalDate) {
        saveDiaryDocId(context, date, null)
    }

    suspend fun clearPlanDocId(context: Context, date: LocalDate) {
        savePlanDocId(context, date, null)
    }

    suspend fun clearSession(context: Context, date: LocalDate) {
        val db = AppDatabase.getDatabase(context)
        val dateStr = formatDate(date)
        
        // Delete from DB
        db.nightlyDao().deleteStepsForSession(dateStr)
        db.nightlyDao().deleteSession(dateStr)
        
        // Clear Legacy Prefs
        val prefs = context.getSharedPreferences("nightly_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .remove("diary_doc_id_$dateStr")
            .remove("plan_doc_id_$dateStr")
            .remove("plan_verified_$dateStr")
            .remove("state_$dateStr")
            .apply()
    }
    
    suspend fun getAllSessions(context: Context): List<NightlySession> {
        val db = AppDatabase.getDatabase(context)
        return db.nightlyDao().getAllSessions()
    }

    suspend fun cleanupOldData(context: Context, cutoffDate: LocalDate) {
        val db = AppDatabase.getDatabase(context)
        val dateStr = formatDate(cutoffDate)
        db.nightlyDao().deleteOldSteps(dateStr)
        db.nightlyDao().deleteOldSessions(dateStr)
    }
    
    private fun formatDate(date: LocalDate): String {
        return date.format(DateTimeFormatter.ISO_LOCAL_DATE)
    }

    suspend fun getSessionStatus(context: Context, date: LocalDate): Int {
        val db = AppDatabase.getDatabase(context)
        val dateStr = formatDate(date)
        return db.nightlyDao().getSession(dateStr)?.status ?: 0 // Default to IDLE (0)
    }

    suspend fun updateSessionStatus(context: Context, date: LocalDate, status: Int) {
        val db = AppDatabase.getDatabase(context)
        val dateStr = formatDate(date)
        val session = db.nightlyDao().getSession(dateStr)
        if (session != null) {
            db.nightlyDao().insertOrUpdateSession(session.copy(status = status))
        } else {
             db.nightlyDao().insertOrUpdateSession(
                NightlySession(
                    date = dateStr,
                    startTime = System.currentTimeMillis(),
                    status = status
                )
            )
        }
    }

    suspend fun saveReportPdfId(context: Context, date: LocalDate, docId: String) {
        val db = AppDatabase.getDatabase(context)
        val dateStr = formatDate(date)
        val session = db.nightlyDao().getSession(dateStr)
        if (session != null) {
            db.nightlyDao().insertOrUpdateSession(session.copy(reportPdfId = docId))
        }
    }
    
    suspend fun getReportPdfId(context: Context, date: LocalDate): String? {
        val db = AppDatabase.getDatabase(context)
        val dateStr = formatDate(date)
        return db.nightlyDao().getSession(dateStr)?.reportPdfId
    }



    suspend fun setReflectionXp(context: Context, date: LocalDate, xp: Int) {
        val db = AppDatabase.getDatabase(context)
        val dateStr = formatDate(date)
        val session = db.nightlyDao().getSession(dateStr)
        if (session != null) {
            db.nightlyDao().insertOrUpdateSession(session.copy(reflectionXp = xp))
        }
    }

    suspend fun getReflectionXp(context: Context, date: LocalDate): Int {
        val db = AppDatabase.getDatabase(context)
        val dateStr = formatDate(date)
        return db.nightlyDao().getSession(dateStr)?.reflectionXp ?: 0
    }

    suspend fun saveReportContent(context: Context, date: LocalDate, content: String) {
        val db = AppDatabase.getDatabase(context)
        val dateStr = formatDate(date)
        val session = db.nightlyDao().getSession(dateStr)
        if (session != null) {
            db.nightlyDao().insertOrUpdateSession(session.copy(reportContent = content))
        }
    }

    suspend fun getReportContent(context: Context, date: LocalDate): String? {
        val db = AppDatabase.getDatabase(context)
        val dateStr = formatDate(date)
        return db.nightlyDao().getSession(dateStr)?.reportContent
    }
}
