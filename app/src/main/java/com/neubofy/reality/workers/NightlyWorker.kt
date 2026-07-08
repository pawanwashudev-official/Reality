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

class NightlyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_MODE = "mode"
        const val MODE_CREATION = "creation"
        const val MODE_ANALYSIS = "analysis"
        const val MODE_PLANNING = "planning"
    }

    override suspend fun doWork(): Result {
        val mode = inputData.getString(KEY_MODE) ?: MODE_CREATION
        TerminalLogger.log("NIGHTLY_WORKER: Starting in $mode mode")

        return withContext(Dispatchers.IO) {
            try {
                val silentListener = object : NightlyProtocolExecutor.NightlyProgressListener {
                    private fun broadcast(msg: String) {
                        val intent = android.content.Intent("com.neubofy.reality.NIGHTLY_LOG")
                        intent.putExtra("message", msg)
                        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
                    }
                    override fun onStepStarted(step: Int, stepName: String) { broadcast("WORKER: Step $step ($stepName) Started") }
                    override fun onStepCompleted(step: Int, stepName: String, details: String?, linkUrl: String?) { broadcast("WORKER: Step $step Completed - $details") }
                    override fun onStepSkipped(step: Int, stepName: String, reason: String) { broadcast("WORKER: Step $step Skipped") }
                    override fun onError(step: Int, error: String) { broadcast("WORKER ERROR: Step $step - $error") }
                    override fun onQuestionsReady(questions: List<String>) {}
                    override fun onAnalysisFeedback(feedback: String) {}
                    override fun onComplete(diaryDocId: String?, diaryUrl: String?) { broadcast("WORKER: Phase Complete!") }
                }

                val executor = NightlyProtocolExecutor(applicationContext, LocalDate.now(), silentListener)

                when (mode) {
                    MODE_CREATION -> executor.startCreationPhase()
                    MODE_ANALYSIS -> executor.finishAnalysisPhase()
                    MODE_PLANNING -> executor.executePlanningPhase()
                }

                Result.success()
            } catch (e: Exception) {
                TerminalLogger.log("WORKER FATAL: ${e.message}")
                e.printStackTrace()
                Result.failure()
            }
        }
    }
}