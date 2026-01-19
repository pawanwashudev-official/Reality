package com.neubofy.reality.ui.activity

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.neubofy.reality.data.NightlyProtocolExecutor
import com.neubofy.reality.databinding.ActivityNightlyBinding
import com.neubofy.reality.utils.TerminalLogger
import com.neubofy.reality.utils.ThemeManager
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class NightlyActivity : AppCompatActivity(), NightlyProtocolExecutor.NightlyProgressListener {

    private lateinit var binding: ActivityNightlyBinding
    
    // Date selection state
    private var baseDate: LocalDate = LocalDate.now()
    private var selectedDate: LocalDate = LocalDate.now()
    private var dateOffset: Int = 0
    private val maxOffset = 2 // Can go back up to 2 days from today
    
    // Execution state
    private var isExecuting = false
    private var diaryUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityNightlyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        calculateBaseDate()
        setupInsets()
        setupListeners()
        updateSetupStatus()
        updateDateDisplay()
    }
    
    override fun onResume() {
        super.onResume()
        updateSetupStatus()
        loadPersistentState()
    }
    
    private fun calculateBaseDate() {
        // Simple logic: if after midnight and before 4am, use previous day
        // This covers most nightly review scenarios
        val now = LocalTime.now()
        baseDate = if (now.hour < 4) LocalDate.now().minusDays(1) else LocalDate.now()
        selectedDate = baseDate
        dateOffset = 0
    }
    
    private fun updateDateDisplay() {
        val formatter = DateTimeFormatter.ofPattern("EEEE, MMM d")
        val isToday = selectedDate == LocalDate.now()
        val isYesterday = selectedDate == LocalDate.now().minusDays(1)
        
        val prefix = when {
            isToday -> "Today, "
            isYesterday -> "Yesterday, "
            else -> ""
        }
        
        binding.tvSelectedDate.text = "$prefix${selectedDate.format(formatter)}"
        
        // Update arrow visibility - can only go back (no forward dates)
        binding.btnPrevDate.alpha = if (dateOffset > -maxOffset) 1.0f else 0.3f
        binding.btnNextDate.alpha = if (dateOffset < 0) 1.0f else 0.3f // Can only go forward up to today
        
        loadPersistentState()
    }
    
    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.header.setPadding(
                binding.header.paddingLeft,
                systemBars.top + 16,
                binding.header.paddingRight,
                binding.header.paddingBottom
            )
            insets
        }
    }
    
    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, NightlySettingsActivity::class.java))
        }
        
        binding.btnPrevDate.setOnClickListener {
            if (dateOffset > -maxOffset) {
                dateOffset--
                selectedDate = baseDate.plusDays(dateOffset.toLong())
                updateDateDisplay()
                updateSetupStatus() // State might differ for different days (future feature)
            }
        }
        
        binding.btnNextDate.setOnClickListener {
            if (dateOffset < 0) {
                dateOffset++
                selectedDate = baseDate.plusDays(dateOffset.toLong())
                updateDateDisplay()
                updateSetupStatus()
            }
        }
        
        // Step Details Click Listeners - Show saved data from memory
        binding.cardStepData.setOnClickListener { showDetailedStepPopup(1) }
        binding.cardStepQuestions.setOnClickListener { showDetailedStepPopup(2) }
        binding.cardStepDiary.setOnClickListener { showDetailedStepPopup(3) }
        binding.cardStepAnalysis.setOnClickListener { showDetailedStepPopup(4) }
        binding.cardStepCreatePlan.setOnClickListener { showDetailedStepPopup(5) }
        binding.cardStepProcessPlan.setOnClickListener { showDetailedStepPopup(6) }

        // Start Button Logic (Dynamic)
        binding.btnStartNightly.setOnClickListener {
            if (isExecuting) return@setOnClickListener
            
            val prefs = getSharedPreferences("nightly_prefs", MODE_PRIVATE)
            val currentState = prefs.getInt("protocol_state", NightlyProtocolExecutor.STATE_IDLE)
            
            if (currentState == NightlyProtocolExecutor.STATE_PENDING_REFLECTION) {
                // Analysis Phase
                analyzeDay()
            } else {
                // Creation Phase
                startNightlyProtocol()
            }
        }
        
        binding.btnOpenDiary.setOnClickListener {
            diaryUrl?.let { url ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open Google Docs", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // Verification Actions
        binding.btnOpenPlanDoc.setOnClickListener {
            val prefs = getSharedPreferences("nightly_prefs", MODE_PRIVATE)
            val planId = prefs.getString(NightlyProtocolExecutor.getPlanDocIdKey(selectedDate), null)
            planId?.let { id ->
                try {
                    val url = "https://docs.google.com/document/d/$id/edit"
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open Plan", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        binding.btnVerifyPlan.setOnClickListener {
            // User confirms they have edited the plan
            val prefs = getSharedPreferences("nightly_prefs", MODE_PRIVATE)
            prefs.edit().putBoolean(NightlyProtocolExecutor.getPlanVerifiedKey(selectedDate), true).apply()
            
            // UI Update
            binding.layoutVerificationActions.visibility = View.GONE
            Toast.makeText(this, "Plan Verified! Proceeding...", Toast.LENGTH_SHORT).show()
            
            // Allow processing to start
            loadPersistentState() 
            // We might want to trigger processing immediately or just let user click start?
            // "Plan is Ready" implies we are good to go.
        }
        

        
        // Clear Log Console
        binding.btnClearLog.setOnClickListener {
            binding.tvExecutionLog.text = "Ready to start..."
        }
    }

    private fun showStepDetail(step: Int, title: String, description: String, isDestructive: Boolean = false) {
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(description)
            .setPositiveButton("Close") { d, _ -> d.dismiss() }

        // Add Re-run Action
        builder.setNeutralButton("Re-run Step") { _, _ ->
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Re-run $title?")
                .setMessage(if (isDestructive) "WARNING: This will DELETE existing data (e.g., current diary) and create new." else "This will re-execute this step.")
                .setPositiveButton("Yes, Re-run") { _, _ ->
                    handleRerunStep(step)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        builder.show()
    }
    
    // Enhanced Step Popup - Shows all saved data from memory
    private fun showDetailedStepPopup(step: Int) {
        val executorStep = when (step) {
            1 -> NightlyProtocolExecutor.STEP_COLLECT_DATA
            2 -> NightlyProtocolExecutor.STEP_GENERATE_QUESTIONS
            3 -> NightlyProtocolExecutor.STEP_CREATE_DIARY
            4 -> NightlyProtocolExecutor.STEP_ANALYZE_REFLECTION
            5 -> NightlyProtocolExecutor.STEP_CREATE_PLAN_DOC
            6 -> NightlyProtocolExecutor.STEP_PROCESS_PLAN
            else -> return
        }
        
        val stepState = NightlyProtocolExecutor.loadStepState(this, selectedDate, executorStep)
        val prefs = getSharedPreferences("nightly_prefs", MODE_PRIVATE)
        
        val title = when (step) {
            1 -> "📊 Day Data Collection"
            2 -> "🤖 AI Questions"
            3 -> "📖 Diary Document"
            4 -> "✨ AI Analysis & XP"
            5 -> "📝 Plan Document"
            6 -> "📅 Process & Calendar"
            else -> "Step $step"
        }
        
        // Build content from saved memory
        val content = StringBuilder()
        
        content.append("Status: ${getStatusText(stepState.status)}\n\n")
        content.append("📋 Saved Details:\n${stepState.details ?: "No data yet"}\n\n")
        
        when (step) {
            1 -> content.append("This step collects:\n• Calendar events\n• Tasks (due & completed)\n• Focus sessions")
            2 -> content.append("AI generates 5 personalized reflection questions based on your day summary.")
            3 -> {
                val diaryId = prefs.getString(NightlyProtocolExecutor.getDiaryDocIdKey(selectedDate), null)
                if (diaryId != null) {
                    content.append("📄 Doc ID: $diaryId\n")
                    content.append("🔗 Click 'Open Diary' to view in Google Docs")
                }
            }
            4 -> content.append("AI reads your reflection and grades quality (0-50 XP bonus).")
            5 -> {
                val planId = prefs.getString(NightlyProtocolExecutor.getPlanDocIdKey(selectedDate), null)
                if (planId != null) {
                    content.append("📄 Doc ID: $planId\n")
                    content.append("🔗 Click 'Open Plan' to edit in Google Docs")
                }
            }
            6 -> content.append("AI reads your verified plan and creates tasks/events for tomorrow.")
        }
        
        val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(content.toString())
            .setPositiveButton("Close", null)
        
        // Add "Delete & Re-run" option
        builder.setNegativeButton("🗑️ Delete & Re-run") { _, _ ->
            confirmDeleteAndRerun(step, executorStep)
        }
        
        // Add "Open Doc" buttons
        if (step == 3) {
            val diaryId = prefs.getString(NightlyProtocolExecutor.getDiaryDocIdKey(selectedDate), null)
            if (diaryId != null) {
                builder.setNeutralButton("📖 Open Diary") { _, _ ->
                    try {
                        val url = "https://docs.google.com/document/d/$diaryId/edit"
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not open diary", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } else if (step == 5) {
            val planId = prefs.getString(NightlyProtocolExecutor.getPlanDocIdKey(selectedDate), null)
            if (planId != null) {
                builder.setNeutralButton("📝 Open Plan") { _, _ ->
                    try {
                        val url = "https://docs.google.com/document/d/$planId/edit"
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not open plan", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        
        builder.show()
    }
    
    private fun getStatusText(status: Int): String {
        return when (status) {
            NightlyProtocolExecutor.StepProgress.STATUS_PENDING -> "⏸️ Pending"
            NightlyProtocolExecutor.StepProgress.STATUS_RUNNING -> "⏳ Running..."
            NightlyProtocolExecutor.StepProgress.STATUS_COMPLETED -> "✅ Completed"
            NightlyProtocolExecutor.StepProgress.STATUS_SKIPPED -> "⏭️ Skipped"
            NightlyProtocolExecutor.StepProgress.STATUS_ERROR -> "❌ Error"
            else -> "Unknown"
        }
    }
    
    private fun confirmDeleteAndRerun(step: Int, executorStep: Int) {
        val stepName = when (step) {
            1 -> "Data Collection"
            2 -> "Question Generation"
            3 -> "Diary Creation"
            4 -> "Analysis & XP"
            5 -> "Plan Document"
            6 -> "Process Plan"
            else -> "Step $step"
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete & Re-run $stepName?")
            .setMessage("This will delete saved data for this step and re-execute it.")
            .setPositiveButton("Delete & Re-run") { _, _ ->
                // Clear this step's state
                NightlyProtocolExecutor.saveStepState(
                    this, selectedDate, executorStep,
                    NightlyProtocolExecutor.StepProgress.STATUS_PENDING, null
                )
                
                // Reset icon for this step
                val icon = getStepIcon(step)
                val text = getStepText(step)
                icon.setImageResource(com.neubofy.reality.R.drawable.baseline_radio_button_unchecked_24)
                icon.setColorFilter(getColor(com.google.android.material.R.color.material_on_surface_disabled))
                text.text = "Pending..."
                
                handleRerunStep(step)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleRerunStep(step: Int) {
        appendLog("User requested re-run of Step $step")
        if (isExecuting) {
            Toast.makeText(this, "Wait for current process to finish", Toast.LENGTH_SHORT).show()
            return
        }

        when (step) {
            1 -> { // Data
                 startNightlyProtocol() // Simplest way is to restart creation phase
                 Toast.makeText(this, "Restarting Data Collection...", Toast.LENGTH_SHORT).show()
            }
            2 -> { // Questions
                 startNightlyProtocol() // Questions depend on data, so restart creation
                 Toast.makeText(this, "Regenerating Questions...", Toast.LENGTH_SHORT).show()
            }
            3 -> { // Diary
                NightlyProtocolExecutor.clearMemory(this) // Simulates "Fresh Start"
                startNightlyProtocol()
                Toast.makeText(this, "Re-creating Diary...", Toast.LENGTH_SHORT).show()
            }
            4 -> { // Analysis
                analyzeDay()
                Toast.makeText(this, "Restarting Analysis...", Toast.LENGTH_SHORT).show()
            }
            5 -> { // Plan Doc
                // Need a way to run just plan doc? For now, re-run analysis/creation or just set state
                // Since this runs after Analysis, we should likely check state
                startNightlyProtocol() // Fallback
                Toast.makeText(this, "Re-creating Plan Doc...", Toast.LENGTH_SHORT).show()
            }
            6 -> { // Process Plan
                // Re-process
                analyzeDay() // Runs the sequential steps
                Toast.makeText(this, "Re-processing Plan...", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun startNightlyProtocol() {
        isExecuting = true
        TerminalLogger.log("Nightly: Starting protocol for $selectedDate")
        
        // Show progress card (now container)
        // binding.layoutStepsContainer.visibility = View.VISIBLE // Always visible

        binding.btnStartNightly.isEnabled = false
        binding.btnStartNightly.text = "Creating Diary..."
        
        resetStepIcons()
        
        lifecycleScope.launch {
            val executor = NightlyProtocolExecutor(this@NightlyActivity, selectedDate, this@NightlyActivity)
            executor.startCreationPhase()
        }
    }
    
    private fun analyzeDay() {
        isExecuting = true
        TerminalLogger.log("Nightly: Analyzing day for $selectedDate")
        
        // binding.layoutStepsContainer.visibility = View.VISIBLE

        binding.btnStartNightly.isEnabled = false
        binding.btnStartNightly.text = "Analyzing..."
        
        lifecycleScope.launch {
             val executor = NightlyProtocolExecutor(this@NightlyActivity, selectedDate, this@NightlyActivity)
             executor.finishAnalysisPhase()
        }
    }
    
    private fun resetStepIcons() {
        // Reset to default only if not loading from state? 
        // Actually, startNightlyProtocol calls this. If strictly starting fresh, we should reset.
        // But if persistent state exists, we might want to respect it?
        // Let's keep it resetting visually for "Start" action, but loadPersistentState works on Resume.
        resetStepIcon(binding.iconStepData, binding.tvStepDataDetail)
        resetStepIcon(binding.iconStepQuestions, binding.tvStepQuestionsDetail)
        resetStepIcon(binding.iconStepDiary, binding.tvStepDiaryDetail)
        
        resetStepIcon(binding.iconStepAnalysis, binding.tvStepAnalysisDetail)
        resetStepIcon(binding.iconStepCreatePlan, binding.tvStepCreatePlanDetail)
        resetStepIcon(binding.iconStepProcessPlan, binding.tvStepProcessPlanDetail)
    }
    
    private fun resetStepIcon(icon: android.widget.ImageView, detail: android.widget.TextView) {
        icon.setImageResource(com.neubofy.reality.R.drawable.baseline_radio_button_unchecked_24)
        icon.setColorFilter(getColor(com.google.android.material.R.color.material_on_surface_disabled))
        detail.text = "Pending..."
    }


    private fun loadPersistentState() {
        // Iterate through all known steps
        val steps = listOf(
            NightlyProtocolExecutor.STEP_COLLECT_DATA,
            NightlyProtocolExecutor.STEP_GENERATE_QUESTIONS,
            NightlyProtocolExecutor.STEP_CREATE_DIARY,
            NightlyProtocolExecutor.STEP_ANALYZE_REFLECTION,
            NightlyProtocolExecutor.STEP_CREATE_PLAN_DOC,
            NightlyProtocolExecutor.STEP_PROCESS_PLAN
        )
        
        steps.forEach { step ->
            val stepState = NightlyProtocolExecutor.loadStepState(this, selectedDate, step)
            val mappedStep = when(step) {
                NightlyProtocolExecutor.STEP_COLLECT_DATA -> 1
                NightlyProtocolExecutor.STEP_GENERATE_QUESTIONS -> 2
                NightlyProtocolExecutor.STEP_CREATE_DIARY -> 3
                NightlyProtocolExecutor.STEP_ANALYZE_REFLECTION -> 4
                NightlyProtocolExecutor.STEP_CREATE_PLAN_DOC -> 5
                NightlyProtocolExecutor.STEP_PROCESS_PLAN -> 6
                else -> -1
            }
            
            if (mappedStep != -1) {
                val icon = getStepIcon(mappedStep)
                val text = getStepText(mappedStep)
                
                when (stepState.status) {
                    NightlyProtocolExecutor.StepProgress.STATUS_COMPLETED -> {
                        icon.setImageResource(com.neubofy.reality.R.drawable.baseline_check_circle_24)
                        icon.setColorFilter(getColor(com.neubofy.reality.R.color.green_500))
                        text.text = if (!stepState.details.isNullOrEmpty()) stepState.details else "Completed"
                    }
                    NightlyProtocolExecutor.StepProgress.STATUS_RUNNING -> {
                         icon.setImageResource(com.neubofy.reality.R.drawable.baseline_sync_24)
                         // Rotate animation?
                         icon.setColorFilter(getColor(com.neubofy.reality.R.color.blue_500))
                         text.text = "Running..."
                    }
                    NightlyProtocolExecutor.StepProgress.STATUS_ERROR -> {
                         icon.setImageResource(com.neubofy.reality.R.drawable.baseline_error_24)
                         icon.setColorFilter(getColor(com.neubofy.reality.R.color.status_error))
                         text.text = "Error"
                    }
                    NightlyProtocolExecutor.StepProgress.STATUS_SKIPPED -> {
                         icon.setImageResource(com.neubofy.reality.R.drawable.baseline_arrow_forward_24)
                         icon.setColorFilter(getColor(com.google.android.material.R.color.material_on_surface_disabled))
                         text.text = "Skipped"
                    }
                    else -> {
                        // Pending
                         icon.setImageResource(com.neubofy.reality.R.drawable.baseline_radio_button_unchecked_24)
                         icon.setColorFilter(getColor(com.google.android.material.R.color.material_on_surface_disabled))
                         text.text = "Pending..."
                    }
                }
            }
        }
        
        // Verification State Check
        val prefs = getSharedPreferences("nightly_prefs", MODE_PRIVATE)
        val step5State = NightlyProtocolExecutor.loadStepState(this, selectedDate, NightlyProtocolExecutor.STEP_CREATE_PLAN_DOC)
        val planVerified = prefs.getBoolean(NightlyProtocolExecutor.getPlanVerifiedKey(selectedDate), false)
        
        if (step5State.status == NightlyProtocolExecutor.StepProgress.STATUS_COMPLETED && !planVerified) {
            binding.layoutVerificationActions.visibility = View.VISIBLE
        } else {
            binding.layoutVerificationActions.visibility = View.GONE
        }
    }
    
    private fun updateSetupStatus() {
        val aiPrefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
        val nightlyModel = aiPrefs.getString("nightly_model", null)
        
        val prefs = getSharedPreferences("nightly_prefs", MODE_PRIVATE)
        val realityFolderId = prefs.getString("reality_folder_id", null)
        val diaryFolderId = prefs.getString("diary_folder_id", null)
        val reportFolderId = prefs.getString("report_folder_id", null)
        
        // State Check - DATE-SPECIFIC
        val protocolState = NightlyProtocolExecutor.getStateForDate(this, selectedDate)
        val lastDocId = NightlyProtocolExecutor.getDiaryDocIdForDate(this, selectedDate)
        
        // ... (Existing readiness checks) ...
        val startTimeMinutes = prefs.getInt("nightly_start_time", 22 * 60)
        val endTimeMinutes = prefs.getInt("nightly_end_time", 23 * 60 + 59)
        
        val aiReady = !nightlyModel.isNullOrEmpty()
        val driveReady = !realityFolderId.isNullOrEmpty() && !diaryFolderId.isNullOrEmpty() 
        val withinTimeWindow = isWithinTimeWindow(startTimeMinutes, endTimeMinutes)
        
        val sb = StringBuilder()
        // ... (Status Text Building - simplified for brevity, keep existing logic logic if possible or rewrite) ...
        // Re-implementing concise status text to fit block
        
        if (aiReady && driveReady) {
             sb.append("✓ System Ready\n")
        } else {
             sb.append("✗ Setup Incomplete\n")
        }
        
        if (withinTimeWindow) {
             sb.append("✓ Within time window\n")
        } else {
             sb.append("⏰ Recommended time: ${formatTimeDisplay(startTimeMinutes)}\n")
        }
        
        binding.tvSetupStatus.text = sb.toString()
        
        // Button State Logic - ENFORCE WINDOW
        val canStart = aiReady && driveReady && withinTimeWindow
        val isPendingReflection = protocolState == NightlyProtocolExecutor.STATE_PENDING_REFLECTION
        
        // Allow start only if within window OR if resuming pending reflection (user already started)
        binding.btnStartNightly.isEnabled = canStart || isPendingReflection
        
        when (protocolState) {
            NightlyProtocolExecutor.STATE_IDLE -> {
                binding.btnStartNightly.text = "Start Nightly Protocol"
            }
            NightlyProtocolExecutor.STATE_CREATING -> {
                binding.btnStartNightly.text = "Resume Creation"
            }
            NightlyProtocolExecutor.STATE_PENDING_REFLECTION -> {
                binding.btnStartNightly.text = "Analyze Day"
                binding.btnOpenDiary.visibility = if (lastDocId != null) View.VISIBLE else View.GONE
                diaryUrl = lastDocId?.let { "https://docs.google.com/document/d/$it/edit" }
            }
            NightlyProtocolExecutor.STATE_ANALYZING -> {
                binding.btnStartNightly.text = "Resume Analysis"
            }
            NightlyProtocolExecutor.STATE_COMPLETE -> {
                 binding.btnStartNightly.text = "Review Complete"
                 binding.btnStartNightly.isEnabled = false // Disable if done for today
            }
        }
    }

    // ... (rest of methods) ...

    private fun appendLog(message: String) {
        val timestamp = java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss").format(LocalTime.now())
        val newLog = "[$timestamp] $message"
        val currentText = binding.tvExecutionLog.text.toString()
        binding.tvExecutionLog.text = if (currentText == "Ready to start...") newLog else "$currentText\n$newLog"
        
        // Auto-scroll to bottom
        binding.tvExecutionLog.post {
            val scroll = binding.tvExecutionLog.parent as? android.widget.ScrollView
            scroll?.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onComplete(diaryDocId: String?, diaryUrl: String?) {
        runOnUiThread {
            TerminalLogger.log("Nightly: Protocol step complete! Diary ID: $diaryDocId")
            
            isExecuting = false
            
            val prefs = getSharedPreferences("nightly_prefs", MODE_PRIVATE)
            val state = prefs.getInt("protocol_state", NightlyProtocolExecutor.STATE_IDLE)
            
            if (state == NightlyProtocolExecutor.STATE_PENDING_REFLECTION) {
                // Creation Phase Done
                binding.btnStartNightly.text = "Analyze Day"
                binding.btnStartNightly.isEnabled = true
                appendLog("Creation Complete. Diary Ready.")
                Toast.makeText(this, "Diary Ready! Please write your reflection.", Toast.LENGTH_LONG).show()
            } else if (state == NightlyProtocolExecutor.STATE_COMPLETE) {
                // Analysis Phase Done
                binding.btnStartNightly.text = "Review Complete"
                binding.btnStartNightly.isEnabled = false
                appendLog("Analysis Complete. Nightly Review Done for today.")
                Toast.makeText(this, "Nightly Review Completed!", Toast.LENGTH_LONG).show()
            }
            
            if (diaryUrl != null) {
                this.diaryUrl = diaryUrl
                binding.btnOpenDiary.visibility = View.VISIBLE
            }
        }
    }
    
    private fun isWithinTimeWindow(startMinutes: Int, endMinutes: Int): Boolean {
        val now = LocalTime.now()
        val nowMinutes = now.hour * 60 + now.minute
        
        return if (endMinutes < startMinutes) {
            // Crosses midnight (e.g., 22:00 to 01:00)
            nowMinutes >= startMinutes || nowMinutes <= endMinutes
        } else {
            // Same day (e.g., 22:00 to 23:59)
            nowMinutes in startMinutes..endMinutes
        }
    }
    
    private fun formatTimeDisplay(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return String.format("%02d:%02d", h, m)
    }
    
    // NightlyProgressListener Implementation
    
    override fun onStepStarted(step: Int, stepName: String) {
        runOnUiThread {
            TerminalLogger.log("Nightly: Step $step started - $stepName")
            getStepIcon(step).setImageResource(com.neubofy.reality.R.drawable.baseline_sync_24)
            getStepIcon(step).setColorFilter(getColor(com.neubofy.reality.R.color.blue_500))
            appendLog("Step $step: $stepName...")
        }
    }
    
    override fun onStepCompleted(step: Int, stepName: String, details: String?) {
        runOnUiThread {
            TerminalLogger.log("Nightly: Step $step completed - $stepName ${details?.let { "($it)" } ?: ""}")
            getStepIcon(step).setImageResource(com.neubofy.reality.R.drawable.baseline_check_circle_24)
            getStepIcon(step).setColorFilter(getColor(com.neubofy.reality.R.color.green_500))
            getStepText(step).text = details ?: "Completed"
            appendLog("Step $step OK: ${details ?: "Done"}")
            
            // Auto-check Verification Trigger
             val prefs = getSharedPreferences("nightly_prefs", MODE_PRIVATE)
            if (step == 5) { // Plan Doc Created
                val planVerified = prefs.getBoolean(NightlyProtocolExecutor.getPlanVerifiedKey(selectedDate), false)
                if (!planVerified) {
                    binding.layoutVerificationActions.visibility = View.VISIBLE
                    appendLog("Waiting for Plan Verification...")
                }
            }
        }
    }
    
    override fun onStepSkipped(step: Int, stepName: String, reason: String) {
        runOnUiThread {
            TerminalLogger.log("Nightly: Step $step skipped - $reason")
            getStepIcon(step).setImageResource(com.neubofy.reality.R.drawable.baseline_arrow_forward_24)
            getStepIcon(step).setColorFilter(getColor(com.google.android.material.R.color.material_on_surface_disabled))
            getStepText(step).text = reason
            appendLog("Step $step Skipped: $reason")
        }
    }
    
    override fun onError(step: Int, error: String) {
        runOnUiThread {
            TerminalLogger.log("Nightly: Error at step $step - $error")
            if (step > 0) {
                getStepIcon(step).setImageResource(com.neubofy.reality.R.drawable.baseline_error_24)
                getStepIcon(step).setColorFilter(getColor(com.neubofy.reality.R.color.status_error))
                getStepText(step).text = error
            }
            appendLog("ERROR (Step $step): $error")
            Toast.makeText(this, "Error: $error", Toast.LENGTH_LONG).show()
            
            isExecuting = false
            binding.btnStartNightly.isEnabled = true
            binding.btnStartNightly.text = "Retry"
        }
    }
    
    override fun onQuestionsReady(questions: List<String>) {
        runOnUiThread {
            TerminalLogger.log("Nightly: ${questions.size} questions ready")
            appendLog("Generated ${questions.size} questions.")
            
            binding.cardQuestions.visibility = View.VISIBLE
            binding.tvQuestionsList.text = questions.mapIndexed { index, q -> 
                "${index + 1}. $q"
            }.joinToString("\n\n")
        }
    }
    
    override fun onAnalysisFeedback(feedback: String) {
        runOnUiThread {
            TerminalLogger.log("Nightly: Analysis Feedback - $feedback")
            appendLog("Analysis Feedback: Needs more detail.")
            isExecuting = false
            
            // Show feedback dialog
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Reflection Feedback")
                .setMessage(feedback)
                .setPositiveButton("I'll Edit It") { dialog, _ ->
                    dialog.dismiss()
                    // Open the diary for them to edit
                    binding.btnOpenDiary.performClick()
                }
                .setCancelable(false)
                .show()
                
            binding.btnStartNightly.text = "Analyze Again"
            binding.btnStartNightly.isEnabled = true
        }
    }
    

    
    private fun getStepIcon(step: Int) = when (step) {
        1 -> binding.iconStepData
        2 -> binding.iconStepQuestions
        3 -> binding.iconStepDiary
        4 -> binding.iconStepAnalysis
        5 -> binding.iconStepCreatePlan
        6 -> binding.iconStepProcessPlan
        else -> binding.iconStepData
    }
    
    private fun getStepText(step: Int) = when (step) {
        1 -> binding.tvStepDataDetail
        2 -> binding.tvStepQuestionsDetail
        3 -> binding.tvStepDiaryDetail
        4 -> binding.tvStepAnalysisDetail
        5 -> binding.tvStepCreatePlanDetail
        6 -> binding.tvStepProcessPlanDetail
        else -> binding.tvStepDataDetail
    }
}
