package com.neubofy.reality.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import com.neubofy.reality.data.NightlyProtocolExecutor
import com.neubofy.reality.utils.TerminalLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * NightlyWorker - Safeguards the Nightly Protocol against App Death.
 * 
 * Runs the heavy phases (Creation, Analysis) in a Foreground Service (via WorkManager).
 * This ensures that even if the user swipes the app away, the diary creation/AI analysis completes.
 */
class NightlyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_MODE = "mode"
        const val MODE_CREATION = "creation"
        const val MODE_ANALYSIS = "analysis"
        const val MODE_PLANNING = "planning"
        const val MODE_SPECIFIC_STEP = "specific_step"
        const val KEY_STEP_ID = "step_id"
    }

    override suspend fun doWork(): Result {
        val mode = inputData.getString(KEY_MODE) ?: MODE_CREATION
        TerminalLogger.log("NIGHTLY_WORKER: Starting in $mode mode")

        if (!com.neubofy.reality.google.GoogleAuthManager.isFullWorkspaceConnected(applicationContext)) {
            TerminalLogger.log("NIGHTLY_WORKER: Skipped - Google Workspace connection required.")
            return Result.failure()
        }

        return withContext(Dispatchers.IO) {
            try {
                val silentListener = object : NightlyProtocolExecutor.NightlyProgressListener {
                    override fun onStepStarted(step: Int, stepName: String) {
                        TerminalLogger.log("WORKER: Step $step ($stepName) Started")
                    }

                    override fun onStepCompleted(step: Int, stepName: String, details: String?, linkUrl: String?) {
                        TerminalLogger.log("WORKER: Step $step Completed - $details")
                    }

                    override fun onStepSkipped(step: Int, stepName: String, reason: String) {
                        TerminalLogger.log("WORKER: Step $step Skipped - $reason")
                    }

                    override fun onError(step: Int, error: String) {
                        TerminalLogger.log("WORKER ERROR: Step $step - $error")
                    }

                    override fun onQuestionsReady(questions: List<String>) {
                        TerminalLogger.log("WORKER: Questions Generated (${questions.size})")
                    }

                    override fun onAnalysisFeedback(feedback: String) {
                        TerminalLogger.log("WORKER: Analysis Feedback - $feedback")
                    }

                    override fun onComplete(diaryDocId: String?, diaryUrl: String?) {
                        TerminalLogger.log("WORKER: Phase Complete! Doc: $diaryDocId")
                    }

                    override fun onStepLog(step: Int, logLine: String) {
                        TerminalLogger.log("WORKER: Step $step Log - $logLine")
                    }
                }

                // Retrieve optional date parameter or default to today
                val dateStr = inputData.getString("date")
                val date = if (dateStr != null) LocalDate.parse(dateStr) else LocalDate.now()

                val executor = NightlyProtocolExecutor(applicationContext, date, silentListener)

                when (mode) {
                    MODE_CREATION -> executor.startCreationPhase()
                    MODE_ANALYSIS -> executor.finishAnalysisPhase()
                    MODE_PLANNING -> executor.executePlanningPhase()
                    MODE_SPECIFIC_STEP -> {
                        val stepId = inputData.getInt(KEY_STEP_ID, -1)
                        if (stepId != -1) {
                            executor.executeSpecificStep(stepId)
                        } else {
                            throw IllegalArgumentException("Specific step mode requires a valid step_id")
                        }
                    }
                }

                Result.success()
            } catch (e: Exception) {
                TerminalLogger.log("WORKER FATAL: ${e.message}")
                com.neubofy.reality.utils.TerminalLogger.log("ERROR: ${e.message}")
                Result.failure()
            }
        }
    }
}
