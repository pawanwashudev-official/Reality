package com.neubofy.reality.ui.activity

import com.neubofy.reality.ui.base.BaseActivity


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
import com.neubofy.reality.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.neubofy.reality.workers.NightlyWorker
import com.neubofy.reality.data.repository.NightlyRepository
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class NightlyActivity : BaseActivity(), NightlyProtocolExecutor.NightlyProgressListener {


    private lateinit var binding: ActivityNightlyBinding
    
    // Date selection state
    private var baseDate: LocalDate = LocalDate.now()
    private var selectedDate: LocalDate = LocalDate.now()
    private var dateOffset: Int = 0
    private val maxOffset = 2 // Can go back up to 2 days from today
    
    // Execution state
    private var isExecuting = false
    private var diaryUrl: String? = null
    
    // RecyclerView Adapter for 13 Steps
    private lateinit var stepAdapter: com.neubofy.reality.ui.adapter.NightlyStepAdapter
    private val stepItems = mutableListOf<com.neubofy.reality.ui.adapter.StepItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        binding = ActivityNightlyBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        calculateBaseDate()
        
        // Clean up old data on launch (protect currently selected date)
        lifecycleScope.launch {
            NightlyProtocolExecutor.cleanupOldEntries(this@NightlyActivity, selectedDate)
        }
        
        setupInsets()
        setupListeners()
        setupStepsRecyclerView()
        setupObservers()
        updateDateDisplay()
    }
    
    private fun setupObservers() {
        // Observe Steps (Real-time updates from Background Worker)
        lifecycleScope.launch {
            NightlyRepository.observeSteps(this@NightlyActivity, selectedDate).collectLatest { steps ->
                steps.forEach { step ->
                    val hasData = !step.resultJson.isNullOrEmpty()
                    val realStatus = if (hasData) {
                        com.neubofy.reality.data.nightly.StepProgress.STATUS_COMPLETED
                    } else if (step.status == com.neubofy.reality.data.nightly.StepProgress.STATUS_RUNNING) {
                        // If it's running but we are just observing, keep it as running
                        step.status
                    } else {
                        step.status
                    }
                    
                    val statusText = when (realStatus) {
                        com.neubofy.reality.data.nightly.StepProgress.STATUS_COMPLETED -> step.details ?: "Completed"
                        com.neubofy.reality.data.nightly.StepProgress.STATUS_ERROR -> step.details ?: "Error"
                        com.neubofy.reality.data.nightly.StepProgress.STATUS_SKIPPED -> "Skipped"
                        com.neubofy.reality.data.nightly.StepProgress.STATUS_RUNNING -> step.details ?: "Running..."
                        else -> "Not run yet"
                    }
                    
                    stepAdapter.updateStep(step.stepId, realStatus, statusText, step.linkUrl)
                }
                
                // Update next step states
                updateDependentSteps(steps.associateBy { it.stepId })
            }
        }

        // Observe Session Status
        lifecycleScope.launch {
            NightlyRepository.observeSessionStatus(this@NightlyActivity, selectedDate).collectLatest { status ->
                updateStartButtonState(status)
            }
        }
    }

    private fun updateDependentSteps(stepStates: Map<Int, com.neubofy.reality.data.db.NightlyStep>) {
        // Helper function: Step is "OK" if it has non-empty resultJson (consistent with observer)
        fun isStepOk(stepId: Int): Boolean {
            val step = stepStates[stepId] ?: return false
            return !step.resultJson.isNullOrEmpty()
        }
        
        // Step 6 (Analyze Reflection) requires Step 5 (Create Diary) to have data
        stepAdapter.setStepEnabled(NightlyProtocolExecutor.STEP_ANALYZE_REFLECTION, isStepOk(NightlyProtocolExecutor.STEP_CREATE_DIARY))
        
        // Step 9 (Generate Plan) requires Step 8 (Create Plan Doc) to have data
        stepAdapter.setStepEnabled(NightlyProtocolExecutor.STEP_GENERATE_PLAN, isStepOk(NightlyProtocolExecutor.STEP_CREATE_PLAN_DOC))
        
        // Step 10 (Process Plan) requires Step 9 (Generate Plan) to have data
        stepAdapter.setStepEnabled(NightlyProtocolExecutor.STEP_PROCESS_PLAN, isStepOk(NightlyProtocolExecutor.STEP_GENERATE_PLAN))
        
        // Step 14 (Normalize Tasks) requires Step 10 (Process Plan) to have data
        stepAdapter.setStepEnabled(NightlyProtocolExecutor.STEP_NORMALIZE_TASKS, isStepOk(NightlyProtocolExecutor.STEP_PROCESS_PLAN))
        
        // Step 12 (Generate PDF) requires Step 11 (Generate Report) - check status completed
        val step11 = stepStates[NightlyProtocolExecutor.STEP_GENERATE_REPORT]
        val step11Ok = step11 != null && (step11.status == com.neubofy.reality.data.nightly.StepProgress.STATUS_COMPLETED || !step11.resultJson.isNullOrEmpty())
        stepAdapter.setStepEnabled(NightlyProtocolExecutor.STEP_GENERATE_PDF, step11Ok)
        
        // Log for debugging
        com.neubofy.reality.utils.TerminalLogger.log("Step Dependencies: Diary=${isStepOk(5)}, PlanDoc=${isStepOk(8)}, GenPlan=${isStepOk(9)}, ProcessPlan=${isStepOk(10)}, Report=$step11Ok")
    }

    private fun updateStartButtonState(protocolState: Int) {
        val prefs = getSharedPreferences("nightly_prefs", MODE_PRIVATE)
        val startTimeMinutes = prefs.getInt("nightly_start_time", 22 * 60)
        val endTimeMinutes = prefs.getInt("nightly_end_time", 23 * 60 + 59)
        val withinTimeWindow = isWithinTimeWindow(startTimeMinutes, endTimeMinutes)
        
        if (!withinTimeWindow) {
            binding.btnStartNightly.isEnabled = false
            binding.btnStartNightly.text = "Outside Window"
            return
        }

        binding.btnStartNightly.isEnabled = true
        when (protocolState) {
            NightlyProtocolExecutor.STATE_IDLE -> binding.btnStartNightly.text = "Start Nightly Protocol"
            NightlyProtocolExecutor.STATE_CREATING -> {
                binding.btnStartNightly.text = "Creating Diary..."
                binding.btnStartNightly.isEnabled = false
            }
            NightlyProtocolExecutor.STATE_PENDING_REFLECTION -> binding.btnStartNightly.text = "Analyze Day"
            NightlyProtocolExecutor.STATE_ANALYZING -> {
                binding.btnStartNightly.text = "Analyzing..."
                binding.btnStartNightly.isEnabled = false
            }
            NightlyProtocolExecutor.STATE_PLANNING_READY -> binding.btnStartNightly.text = "Create Plan"
            NightlyProtocolExecutor.STATE_COMPLETE -> {
                binding.btnStartNightly.text = "Review Complete"
                binding.btnStartNightly.isEnabled = false
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        updateSetupStatus()
        loadPersistentState()
    }
    
    private fun calculateBaseDate() {
        // Always include TODAY in baseDate so it's always selectable.
        // We use maxOffset to go back in time.
        baseDate = LocalDate.now()
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
    
    private fun setupStepsRecyclerView() {
        // Initialize 13 Step Items
        stepItems.clear()
        stepItems.addAll(listOf(
            com.neubofy.reality.ui.adapter.StepItem(NightlyProtocolExecutor.STEP_FETCH_TASKS, "1. Fetch Tasks", R.drawable.baseline_sync_24),
            com.neubofy.reality.ui.adapter.StepItem(NightlyProtocolExecutor.STEP_FETCH_SESSIONS, "2. Fetch Sessions", R.drawable.baseline_sync_24),
            com.neubofy.reality.ui.adapter.StepItem(NightlyProtocolExecutor.STEP_CALC_SCREEN_TIME, "3. Screen Time", R.drawable.baseline_access_time_24),
            com.neubofy.reality.ui.adapter.StepItem(NightlyProtocolExecutor.STEP_GENERATE_QUESTIONS, "4. AI Questions", R.drawable.baseline_auto_awesome_24),
            
            // Step 5
            com.neubofy.reality.ui.adapter.StepItem(NightlyProtocolExecutor.STEP_CREATE_DIARY, "5. Create Diary", R.drawable.baseline_edit_24),
                
            com.neubofy.reality.ui.adapter.StepItem(NightlyProtocolExecutor.STEP_ANALYZE_REFLECTION, "6. Analyze Reflection", R.drawable.baseline_auto_awesome_24, isEnabled = false), // Initially disabled
            com.neubofy.reality.ui.adapter.StepItem(NightlyProtocolExecutor.STEP_FINALIZE_XP, "7. Finalize XP", R.drawable.baseline_bolt_24),
            
            // Step 8 
            com.neubofy.reality.ui.adapter.StepItem(NightlyProtocolExecutor.STEP_CREATE_PLAN_DOC, "8. Create Plan", R.drawable.baseline_description_24),
                
            com.neubofy.reality.ui.adapter.StepItem(NightlyProtocolExecutor.STEP_GENERATE_PLAN, "9. AI Plan", R.drawable.baseline_auto_awesome_24, isEnabled = false), // Initially disabled
            com.neubofy.reality.ui.adapter.StepItem(NightlyProtocolExecutor.STEP_PROCESS_PLAN, "10. Process Plan", R.drawable.baseline_calendar_month_24),
            com.neubofy.reality.ui.adapter.StepItem(NightlyProtocolExecutor.STEP_GENERATE_REPORT, "11. Generate Report", R.drawable.baseline_description_24),
            com.neubofy.reality.ui.adapter.StepItem(NightlyProtocolExecutor.STEP_GENERATE_PDF, "12. Generate PDF", R.drawable.baseline_picture_as_pdf_24),
            com.neubofy.reality.ui.adapter.StepItem(NightlyProtocolExecutor.STEP_SET_ALARM, "13. Set Alarm", R.drawable.baseline_alarm_add_24),
            com.neubofy.reality.ui.adapter.StepItem(NightlyProtocolExecutor.STEP_NORMALIZE_TASKS, "14. AI Task Cleanup", R.drawable.baseline_auto_fix_high_24),
            com.neubofy.reality.ui.adapter.StepItem(NightlyProtocolExecutor.STEP_UPDATE_DISTRACTION, "15. Update Distraction", R.drawable.baseline_do_not_disturb_on_24)
        ))
        
        stepAdapter = com.neubofy.reality.ui.adapter.NightlyStepAdapter(
            steps = stepItems,
            onStartClick = { step -> executeStep(step.stepId) },
            onDoubleTap = { step -> showStepDetails(step) },
            onLongPress = { step -> showStepContextMenu(step.stepId) },
                onCheckboxChanged = { _, _ -> } // Removed functionality as per user request
        )
        
        binding.recyclerSteps.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        binding.recyclerSteps.adapter = stepAdapter
    }
    
    private fun executeStep(stepId: Int) {
        if (isExecuting) return
        
        // STRICT MODE: Check if within active time window before allowing any step to run
        val prefs = getSharedPreferences("nightly_prefs", MODE_PRIVATE)
        val startTimeMinutes = prefs.getInt("nightly_start_time", 22 * 60)
        val endTimeMinutes = prefs.getInt("nightly_end_time", 23 * 60 + 59)
        
        if (!isWithinTimeWindow(startTimeMinutes, endTimeMinutes)) {
            Toast.makeText(this, "Steps can only run within the active time window (${formatTimeDisplay(startTimeMinutes)} - ${formatTimeDisplay(endTimeMinutes)})", Toast.LENGTH_LONG).show()
            return
        }
        
        isExecuting = true
        
        lifecycleScope.launch {
            try {
                val executor = NightlyProtocolExecutor(this@NightlyActivity, selectedDate, this@NightlyActivity)
                
                // Call specific step function based on stepId
                appendLog("Starting Step $stepId...")
                stepAdapter.updateStep(stepId, com.neubofy.reality.data.nightly.StepProgress.STATUS_RUNNING, "Running...")
                
                executor.executeSpecificStep(stepId)
                
            } catch (e: Exception) {
                appendLog("Error: ${e.message}")
                stepAdapter.updateStep(stepId, com.neubofy.reality.data.nightly.StepProgress.STATUS_ERROR, e.message ?: "Failed")
            } finally {
                isExecuting = false
            }
        }
    }
    
    private fun showStepDetails(step: com.neubofy.reality.ui.adapter.StepItem) {
        showDetailedStepPopup(step.stepId)
    }
    // Legacy showStepDetails implementation removed

        

    
    private fun getStatusName(status: Int): String = when(status) {
        com.neubofy.reality.data.nightly.StepProgress.STATUS_PENDING -> "Pending"
        com.neubofy.reality.data.nightly.StepProgress.STATUS_RUNNING -> "Running"
        com.neubofy.reality.data.nightly.StepProgress.STATUS_COMPLETED -> "Completed"
        com.neubofy.reality.data.nightly.StepProgress.STATUS_ERROR -> "Error"
        com.neubofy.reality.data.nightly.StepProgress.STATUS_SKIPPED -> "Skipped"
        else -> "Unknown"
    }
    
    private fun clearAndRetryStep(stepId: Int) {
        lifecycleScope.launch {
            // Clear this step's state
            com.neubofy.reality.data.repository.NightlyRepository.saveStepState(this@NightlyActivity, selectedDate, stepId, 
                com.neubofy.reality.data.nightly.StepProgress.STATUS_PENDING, null)
            
            // Update UI
            stepAdapter.updateStep(stepId, com.neubofy.reality.data.nightly.StepProgress.STATUS_PENDING, "Pending...")
            loadPersistentState()
            
            Toast.makeText(this@NightlyActivity, "Step cleared. Tap Start to retry.", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }
        
        binding.btnReport.setOnClickListener {
            val intent = Intent(this, NightlyReportActivity::class.java)
            intent.putExtra("date", selectedDate)
            startActivity(intent)
        }
        
        // NEW: Plan Icon Navigation
        binding.btnPlan.setOnClickListener {
            val intent = Intent(this, NightlyPlanActivity::class.java)
            intent.putExtra("date", selectedDate)
            startActivity(intent)
        }

        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, NightlySettingsActivity::class.java))
        }
        
        binding.btnReport.setOnClickListener {
             startActivity(Intent(this, NightlyReportActivity::class.java).apply {
                 putExtra("date", selectedDate.toString())
             })
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
        
        // Step Card Listeners - Removed (Now handled by RecyclerView Adapter)


        // Start Button Logic (Dynamic)
        binding.btnStartNightly.setOnClickListener {
            if (isExecuting) return@setOnClickListener
            
            val prefs = getSharedPreferences("nightly_prefs", MODE_PRIVATE)
            val currentState = prefs.getInt("protocol_state", NightlyProtocolExecutor.STATE_IDLE)
            
            if (currentState == NightlyProtocolExecutor.STATE_PENDING_REFLECTION) {
                // Analysis Phase
                analyzeDay()
            } else if (currentState == NightlyProtocolExecutor.STATE_PLANNING_READY) {
                // Planning Phase
                 processPlan()
            } else {
                // Creation Phase
                 if (checkApiIntegrity()) {
                    startNightlyProtocol()
                 }
            }
        }
        
        // btnOpenDiary removed - now in Step 5 card via adapter
        
        // Verification Actions - Moved to Step 8 card (handled in double-tap details)
        
        // Clear Log Console
        binding.btnClearLog.setOnClickListener {
            binding.tvExecutionLog.text = "Ready to start..."
        }
        
        // Inline Open Diary/Report buttons - now handled in adapter via linkUrl
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
    
    private fun showStepContextMenu(step: Int) {
        val options = arrayOf("View Details", "Retry Step (Reset)")
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Step Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showDetailedStepPopup(step)
                    1 -> confirmDeleteAndRerun(step, step)
                }
            }
            .show()
    }
    
    // mapUiStepToExecutorStep removed - stepId is now consistent


    // Enhanced Step Popup - Shows all saved data from memory
    private fun showDetailedStepPopup(step: Int) {
        val executorStep = step // Direct mapping now
        
        lifecycleScope.launch {
            // Show loading
            val loadingDialog = androidx.appcompat.app.AlertDialog.Builder(this@NightlyActivity)
                .setTitle("Fetching Details...")
                .setMessage("Please wait...")
                .setCancelable(false)
                .show()
                
            try {
                // Fetch State
                val stepState = com.neubofy.reality.data.repository.NightlyRepository.loadStepState(this@NightlyActivity, selectedDate, executorStep)
                
                // Fetch Rich Debug Data (Input/Output)
                val executor = NightlyProtocolExecutor(this@NightlyActivity, selectedDate, this@NightlyActivity)
                val debugData = executor.getStepDebugData(executorStep)
                
                loadingDialog.dismiss()
                
                val prefs = getSharedPreferences("nightly_prefs", MODE_PRIVATE)
                
                val item = stepItems.find { it.stepId == step }
                val title = item?.title ?: "Step $step"
                
                // Build content
                val content = StringBuilder()
                
                content.append("Status: ${getStatusText(stepState.status)}\n")
                // content.append("Last Msg: ${stepState.details ?: "None"}\n\n") // Redundant if debugData is good
                
                content.append(debugData)
    
                // Build Dialog
                val builder = androidx.appcompat.app.AlertDialog.Builder(this@NightlyActivity)
                    .setTitle(title)
                    .setMessage(content.toString())
                    .setPositiveButton("Close", null)
                
                // Add "Copy" option
                builder.setNeutralButton("Copy") { _, _ ->
                     val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                     val clip = android.content.ClipData.newPlainText("Step Details", content.toString())
                     clipboard.setPrimaryClip(clip)
                     Toast.makeText(this@NightlyActivity, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                }
                
                // Add "Delete & Re-run" option as a separate choice (maybe overflow or negative?)
                // User asked for Input/Output primarily. Re-run is accessible via context menu too.
                // Let's keep Delete as negative button but rename it to be clear
                builder.setNegativeButton("🗑️ Reset Step") { _, _ ->
                    confirmDeleteAndRerun(step, executorStep)
                }
                
                // Extra actions for specific steps
                if (step == NightlyProtocolExecutor.STEP_CREATE_DIARY) {
                    val diaryId = prefs.getString(NightlyProtocolExecutor.getDiaryDocIdKey(selectedDate), null)
                    if (diaryId != null) {
                        builder.setPositiveButton("📖 Open Diary") { _, _ ->
                            try {
                                val url = "https://docs.google.com/document/d/$diaryId/edit"
                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            } catch (e: Exception) {
                                Toast.makeText(this@NightlyActivity, "Could not open diary", Toast.LENGTH_SHORT).show()
                            }
                        }
                        // Add Close as neutral since Positive is taken? Or just add another button?
                        // builder.setNeutralButton("Close", null) // limit 3 buttons usually
                    }
                }
                
                builder.show()
                
            } catch (e: Exception) {
                loadingDialog.dismiss()
                Toast.makeText(this@NightlyActivity, "Error loading details: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun getStatusText(status: Int): String {
        return when (status) {
            com.neubofy.reality.data.nightly.StepProgress.STATUS_PENDING -> "⏸️ Pending"
            com.neubofy.reality.data.nightly.StepProgress.STATUS_RUNNING -> "⏳ Running..."
            com.neubofy.reality.data.nightly.StepProgress.STATUS_COMPLETED -> "✅ Completed"
            com.neubofy.reality.data.nightly.StepProgress.STATUS_SKIPPED -> "⏭️ Skipped"
            com.neubofy.reality.data.nightly.StepProgress.STATUS_ERROR -> "❌ Error"
            else -> "Unknown"
        }
    }
    
    private fun confirmDeleteAndRerun(step: Int, executorStep: Int) {
        val stepName = when (step) {
            NightlyProtocolExecutor.STEP_FETCH_TASKS -> "Fetch Tasks"
            NightlyProtocolExecutor.STEP_FETCH_SESSIONS -> "Fetch Sessions"
            NightlyProtocolExecutor.STEP_CALC_SCREEN_TIME -> "Screen Time"
            NightlyProtocolExecutor.STEP_GENERATE_QUESTIONS -> "Generate Questions"
            NightlyProtocolExecutor.STEP_CREATE_DIARY -> "Create Diary"
            NightlyProtocolExecutor.STEP_ANALYZE_REFLECTION -> "Analyze Reflection"
            NightlyProtocolExecutor.STEP_FINALIZE_XP -> "Finalize XP"
            NightlyProtocolExecutor.STEP_CREATE_PLAN_DOC -> "Create Plan Doc"
            NightlyProtocolExecutor.STEP_GENERATE_PLAN -> "Generate Plan Suggestions"
            NightlyProtocolExecutor.STEP_PROCESS_PLAN -> "Process Plan"
            NightlyProtocolExecutor.STEP_GENERATE_REPORT -> "Generate Report"
            NightlyProtocolExecutor.STEP_GENERATE_PDF -> "Generate PDF"
            NightlyProtocolExecutor.STEP_SET_ALARM -> "Set Alarm"
            NightlyProtocolExecutor.STEP_NORMALIZE_TASKS -> "AI Task Cleanup"
            else -> "Step $step"
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete & Re-run $stepName?")
            .setMessage("This will delete saved data for this step and re-execute it.")
            .setPositiveButton("Delete & Re-run") { _, _ ->
                lifecycleScope.launch {
                    // Clear this step's state
                    com.neubofy.reality.data.repository.NightlyRepository.saveStepState(
                        this@NightlyActivity, selectedDate, executorStep,
                        com.neubofy.reality.data.nightly.StepProgress.STATUS_PENDING, null
                    )
                    
                    // Clear persistent Doc IDs if applicable
                    if (executorStep == NightlyProtocolExecutor.STEP_CREATE_DIARY) {
                        com.neubofy.reality.data.repository.NightlyRepository.clearDiaryDocId(this@NightlyActivity, selectedDate)
                        // Also clear preferences for legacy support if needed
                        getSharedPreferences("nightly_prefs", MODE_PRIVATE).edit()
                            .remove(NightlyProtocolExecutor.getDiaryDocIdKey(selectedDate))
                            .apply()
                    }
                    if (executorStep == NightlyProtocolExecutor.STEP_CREATE_PLAN_DOC) {
                         com.neubofy.reality.data.repository.NightlyRepository.clearPlanDocId(this@NightlyActivity, selectedDate)
                    }

                    // Reset adapter step
                    stepAdapter.updateStep(step, com.neubofy.reality.data.nightly.StepProgress.STATUS_PENDING, "Pending...")
                    
                    handleRerunStep(step)
                }
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

        lifecycleScope.launch {
            val executor = NightlyProtocolExecutor(this@NightlyActivity, selectedDate, this@NightlyActivity)
            
            when (step) {
                // Phase 1 & 2: Creation Phase (Dependencies: 1->2->3->4->5)
                NightlyProtocolExecutor.STEP_FETCH_TASKS,
                NightlyProtocolExecutor.STEP_FETCH_SESSIONS,
                NightlyProtocolExecutor.STEP_CALC_SCREEN_TIME,
                NightlyProtocolExecutor.STEP_GENERATE_QUESTIONS -> {
                     Toast.makeText(this@NightlyActivity, "Restarting Data Collection...", Toast.LENGTH_SHORT).show()
                     executor.executeSpecificStep(step) // Just run this step, don't force whole chain unless needed?
                     // Actually, safer to just run specific step if user asked for specific step.
                     // But dependencies might be missing? executeSpecificStep handles silent collection.
                }
                
                NightlyProtocolExecutor.STEP_CREATE_DIARY -> {
                    NightlyProtocolExecutor.clearMemory(this@NightlyActivity)
                    Toast.makeText(this@NightlyActivity, "Re-creating Diary...", Toast.LENGTH_SHORT).show()
                    executor.executeSpecificStep(step)
                }
                
                // Phase 2: Analysis/XP
                NightlyProtocolExecutor.STEP_ANALYZE_REFLECTION -> {
                    Toast.makeText(this@NightlyActivity, "Re-analyzing...", Toast.LENGTH_SHORT).show()
                    executor.executeSpecificStep(step)
                }
                NightlyProtocolExecutor.STEP_FINALIZE_XP -> {
                    Toast.makeText(this@NightlyActivity, "Finalizing XP...", Toast.LENGTH_SHORT).show()
                    executor.executeSpecificStep(step)
                }
                NightlyProtocolExecutor.STEP_NORMALIZE_TASKS -> {
                    Toast.makeText(this@NightlyActivity, "Cleaning Tasks...", Toast.LENGTH_SHORT).show()
                    executor.executeSpecificStep(step)
                }
                
                // Phase 3: Planning
                NightlyProtocolExecutor.STEP_CREATE_PLAN_DOC,
                NightlyProtocolExecutor.STEP_GENERATE_PLAN,
                NightlyProtocolExecutor.STEP_PROCESS_PLAN -> {
                    Toast.makeText(this@NightlyActivity, "Re-running Plan Step...", Toast.LENGTH_SHORT).show()
                    executor.executeSpecificStep(step)
                }
                
                // Phase 4: Reporting
                NightlyProtocolExecutor.STEP_GENERATE_REPORT,
                NightlyProtocolExecutor.STEP_GENERATE_PDF -> {
                    val label = if (step == NightlyProtocolExecutor.STEP_GENERATE_REPORT) "Report" else "PDF"
                    Toast.makeText(this@NightlyActivity, "Re-generating $label...", Toast.LENGTH_SHORT).show()
                    executor.executeSpecificStep(step)
                }
            }
        }
    }
    
    private fun checkApiIntegrity(): Boolean {
        if (!com.neubofy.reality.google.GoogleAuthManager.hasRequiredPermissions(this)) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Connecion Required")
                .setMessage("To customize your reality, this app needs access to Google Tasks, Calendar, and Drive.\n\nPlease check your settings or sign in again.")
                .setPositiveButton("Fix Connection") { _, _ ->
                    startActivity(Intent(this, NightlySettingsActivity::class.java))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return false
        }
        return true
    }

    private fun startNightlyProtocol() {
        val inputData = Data.Builder()
            .putString(NightlyWorker.KEY_MODE, NightlyWorker.MODE_CREATION)
            .build()
            
        val workRequest = OneTimeWorkRequestBuilder<NightlyWorker>()
            .setInputData(inputData)
            .addTag("nightly")
            .build()
            
        WorkManager.getInstance(this).enqueueUniqueWork(
            "nightly_creation",
            androidx.work.ExistingWorkPolicy.REPLACE,
            workRequest
        )
        TerminalLogger.log("Nightly: Scheduled background Creation Phase")
    }
    
    private fun analyzeDay() {
        val inputData = Data.Builder()
            .putString(NightlyWorker.KEY_MODE, NightlyWorker.MODE_ANALYSIS)
            .build()
            
        val workRequest = OneTimeWorkRequestBuilder<NightlyWorker>()
            .setInputData(inputData)
            .addTag("nightly")
            .build()
            
        WorkManager.getInstance(this).enqueueUniqueWork(
            "nightly_analysis",
            androidx.work.ExistingWorkPolicy.REPLACE,
            workRequest
        )
        TerminalLogger.log("Nightly: Scheduled background Analysis Phase")
    }
    
    private fun processPlan() {
        val inputData = Data.Builder()
            .putString(NightlyWorker.KEY_MODE, NightlyWorker.MODE_PLANNING)
            .build()
            
        val workRequest = OneTimeWorkRequestBuilder<NightlyWorker>()
            .setInputData(inputData)
            .addTag("nightly")
            .build()
            
        WorkManager.getInstance(this).enqueueUniqueWork(
            "nightly_planning",
            androidx.work.ExistingWorkPolicy.REPLACE,
            workRequest
        )
        TerminalLogger.log("Nightly: Scheduled background Planning Phase")
    }
    
    private fun resetStepIcons() {
        // Reset all steps in adapter to pending
        stepItems.forEach { item ->
            stepAdapter.updateStep(item.stepId, com.neubofy.reality.data.nightly.StepProgress.STATUS_PENDING, "Pending...")
        }
    }
    
    private fun resetStepIcon(icon: android.widget.ImageView, detail: android.widget.TextView) {
        // Legacy - kept for compatibility but not actively used
        icon.setImageResource(com.neubofy.reality.R.drawable.baseline_radio_button_unchecked_24)
        icon.setColorFilter(getColor(com.google.android.material.R.color.material_on_surface_disabled))
        detail.text = "Pending..."
    }


    private fun loadPersistentState() {
        lifecycleScope.launch {
            // Iterate through all 12 steps and update adapter
            val allSteps = listOf(
                NightlyProtocolExecutor.STEP_FETCH_TASKS,
                NightlyProtocolExecutor.STEP_FETCH_SESSIONS,
                NightlyProtocolExecutor.STEP_CALC_SCREEN_TIME,
                NightlyProtocolExecutor.STEP_GENERATE_QUESTIONS,
                NightlyProtocolExecutor.STEP_CREATE_DIARY,
                NightlyProtocolExecutor.STEP_ANALYZE_REFLECTION,
                NightlyProtocolExecutor.STEP_FINALIZE_XP,
                NightlyProtocolExecutor.STEP_CREATE_PLAN_DOC,
                NightlyProtocolExecutor.STEP_GENERATE_PLAN,
                NightlyProtocolExecutor.STEP_PROCESS_PLAN,
                NightlyProtocolExecutor.STEP_GENERATE_REPORT,
                NightlyProtocolExecutor.STEP_GENERATE_PDF,
                NightlyProtocolExecutor.STEP_SET_ALARM,
                NightlyProtocolExecutor.STEP_NORMALIZE_TASKS,
                NightlyProtocolExecutor.STEP_UPDATE_DISTRACTION
            )
            
            allSteps.forEach { step ->
                // Use loadStepData to check actual resultJson existence (Strict Data-Driven)
                val stepData = com.neubofy.reality.data.repository.NightlyRepository.loadStepData(this@NightlyActivity, selectedDate, step)
                
                // Determine real status: ONLY truly completed if resultJson exists
                // This ensures UI doesn't show "Completed" if data was wiped or failed to save.
                val hasData = !stepData.resultJson.isNullOrEmpty()
                val realStatus = if (hasData) {
                    com.neubofy.reality.data.nightly.StepProgress.STATUS_COMPLETED
                } else if (stepData.status == com.neubofy.reality.data.nightly.StepProgress.STATUS_RUNNING) {
                    com.neubofy.reality.data.nightly.StepProgress.STATUS_PENDING
                } else {
                    stepData.status
                }
                
                val statusText = when (realStatus) {
                    com.neubofy.reality.data.nightly.StepProgress.STATUS_COMPLETED -> stepData.details ?: "Completed"
                    com.neubofy.reality.data.nightly.StepProgress.STATUS_ERROR -> stepData.details ?: "Error"
                    com.neubofy.reality.data.nightly.StepProgress.STATUS_SKIPPED -> "Skipped"
                    else -> "Not run yet"
                }
                
                stepAdapter.updateStep(step, realStatus, statusText, stepData.linkUrl)
            }
            
            // Restore dependent step enable states (Strictly based on data availability of previous step)
            val stepStates = allSteps.associateWith { step ->
                com.neubofy.reality.data.repository.NightlyRepository.loadStepData(this@NightlyActivity, selectedDate, step)
            }

            // Step 6 enables if Step 5 has data
            val step5Ok = stepStates[NightlyProtocolExecutor.STEP_CREATE_DIARY]?.resultJson != null
            stepAdapter.setStepEnabled(NightlyProtocolExecutor.STEP_ANALYZE_REFLECTION, step5Ok)
            
            // Step 9 enables if Step 8 has data
            val step8Ok = stepStates[NightlyProtocolExecutor.STEP_CREATE_PLAN_DOC]?.resultJson != null
            stepAdapter.setStepEnabled(NightlyProtocolExecutor.STEP_GENERATE_PLAN, step8Ok)
            
            // Step 10 enables if Step 9 has data
            val step9Ok = stepStates[NightlyProtocolExecutor.STEP_GENERATE_PLAN]?.resultJson != null
            stepAdapter.setStepEnabled(NightlyProtocolExecutor.STEP_PROCESS_PLAN, step9Ok)
            
            // Step 12 enables if Step 11 has data (Check NightlyRepository for report content)
            val step11Ok = stepStates[NightlyProtocolExecutor.STEP_GENERATE_REPORT]?.status == com.neubofy.reality.data.nightly.StepProgress.STATUS_COMPLETED
            stepAdapter.setStepEnabled(NightlyProtocolExecutor.STEP_GENERATE_PDF, step11Ok)

            // Step 14 enables if Step 10 has data
            val step10Ok_Load = stepStates[NightlyProtocolExecutor.STEP_PROCESS_PLAN]?.resultJson != null
            stepAdapter.setStepEnabled(NightlyProtocolExecutor.STEP_NORMALIZE_TASKS, step10Ok_Load)
        }
    }
    
    private fun updateSetupStatus() {
        lifecycleScope.launch {
            val aiPrefs = getSharedPreferences("ai_prefs", MODE_PRIVATE)
            val nightlyModel = aiPrefs.getString("nightly_model", null)
            
            val prefs = getSharedPreferences("nightly_prefs", MODE_PRIVATE)
            val realityFolderId = prefs.getString("reality_folder_id", null)
            val diaryFolderId = prefs.getString("diary_folder_id", null)
            
            // State Check - DATE-SPECIFIC (Suspend calls)
            val protocolState = NightlyProtocolExecutor.getStateForDate(this@NightlyActivity, selectedDate)
            val lastDocId = NightlyProtocolExecutor.getDiaryDocIdForDate(this@NightlyActivity, selectedDate)
            
            val startTimeMinutes = prefs.getInt("nightly_start_time", 22 * 60)
            val endTimeMinutes = prefs.getInt("nightly_end_time", 23 * 60 + 59)
            
            val aiReady = !nightlyModel.isNullOrEmpty()
            val driveReady = !realityFolderId.isNullOrEmpty() && !diaryFolderId.isNullOrEmpty() 
            val withinTimeWindow = isWithinTimeWindow(startTimeMinutes, endTimeMinutes)
            
            val sb = StringBuilder()
            
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
            
            TerminalLogger.log("Nightly: Window Check - Start: $startTimeMinutes, End: $endTimeMinutes, Now: ${LocalTime.now().hour * 60 + LocalTime.now().minute}, Valid: $withinTimeWindow")
            
            binding.tvSetupStatus.text = sb.toString()
            
            // Button State Logic - ENFORCE WINDOW STRICTLY
            val canStart = aiReady && driveReady && withinTimeWindow
            val isPendingReflection = protocolState == NightlyProtocolExecutor.STATE_PENDING_REFLECTION
            
            // STRICT MODE: If outside window, NO PROCESS allowed, even resume.
            // Exception: If verification (Step 8) is pending? No, user said "no process".
            
            if (withinTimeWindow) {
                binding.btnStartNightly.isEnabled = canStart || isPendingReflection
                binding.tvSetupStatus.alpha = 1.0f
            } else {
                binding.btnStartNightly.isEnabled = false
                binding.btnStartNightly.text = "Outside Window"
                binding.tvSetupStatus.alpha = 0.5f // Dim status to indicate inactive
            }
            
            when (protocolState) {
                NightlyProtocolExecutor.STATE_IDLE -> {
                    binding.btnStartNightly.text = "Start Nightly Protocol"
                }
                NightlyProtocolExecutor.STATE_CREATING -> {
                    binding.btnStartNightly.text = "Resume Creation"
                }
                NightlyProtocolExecutor.STATE_PENDING_REFLECTION -> {
                    binding.btnStartNightly.text = "Analyze Day"
                    // Update Step 5 diary card with link
                    if (lastDocId != null) {
                        diaryUrl = "https://docs.google.com/document/d/$lastDocId/edit"
                        stepAdapter.updateStep(NightlyProtocolExecutor.STEP_CREATE_DIARY, 
                            com.neubofy.reality.data.nightly.StepProgress.STATUS_COMPLETED, 
                            "Diary ready", diaryUrl)
                    }
                }
                NightlyProtocolExecutor.STATE_ANALYZING -> {
                    binding.btnStartNightly.text = "Resume Analysis"
                }
                NightlyProtocolExecutor.STATE_PLANNING_READY -> {
                    binding.btnStartNightly.text = "Create Plan"
                }
                NightlyProtocolExecutor.STATE_COMPLETE -> {
                     binding.btnStartNightly.text = "Review Complete"
                     binding.btnStartNightly.isEnabled = false // Disable if done for today
                }
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
                // Update step 5 with link
                stepAdapter.updateStep(NightlyProtocolExecutor.STEP_CREATE_DIARY, 
                    com.neubofy.reality.data.nightly.StepProgress.STATUS_COMPLETED, "Diary ready", diaryUrl)
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
    
    // --- UI Polish: Visual Locking & Animations ---
    
    // Obsolete animations and card getters removed


    // NightlyProgressListener Implementation
    
    // NightlyProgressListener Implementation
    
    override fun onStepStarted(step: Int, stepName: String) {
        runOnUiThread {
            TerminalLogger.log("Nightly: Step $step started - $stepName")
            stepAdapter.updateStep(step, com.neubofy.reality.data.nightly.StepProgress.STATUS_RUNNING, "Running...")
            appendLog("Step $step: $stepName...")
        }
    }
    
    override fun onStepCompleted(step: Int, stepName: String, details: String?, linkUrl: String?) {
        runOnUiThread {
            TerminalLogger.log("Nightly: Step $step completed - $stepName ${details?.let { "($it)" } ?: ""}")
            stepAdapter.updateStep(step, com.neubofy.reality.data.nightly.StepProgress.STATUS_COMPLETED, details ?: "Completed", linkUrl)
            appendLog("Step $step OK: ${details ?: "Done"}")
            
            // Force full refresh of all step states (fixes enable/disable not updating)
            // This ensures dependent steps are properly enabled after data is saved to DB
            loadPersistentState()
        }
    }
    
    override fun onStepSkipped(step: Int, stepName: String, reason: String) {
        runOnUiThread {
            TerminalLogger.log("Nightly: Step $step skipped - $reason")
            stepAdapter.updateStep(step, com.neubofy.reality.data.nightly.StepProgress.STATUS_SKIPPED, reason)
            appendLog("Step $step Skipped: $reason")
        }
    }
    
    override fun onError(step: Int, error: String) {
        runOnUiThread {
            TerminalLogger.log("Nightly: Error at step $step - $error")
            if (step > 0) {
                stepAdapter.updateStep(step, com.neubofy.reality.data.nightly.StepProgress.STATUS_ERROR, error)
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
            
            // Update step adapter instead of showing separate card
            stepAdapter.updateStep(NightlyProtocolExecutor.STEP_GENERATE_QUESTIONS, 
                com.neubofy.reality.data.nightly.StepProgress.STATUS_COMPLETED,
                "${questions.size} questions generated")
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
                    diaryUrl?.let { url ->
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
                .setCancelable(false)
                .show()
                
            binding.btnStartNightly.text = "Analyze Again"
            binding.btnStartNightly.isEnabled = true
        }
    }
    
    // Legacy getStepIcon and getStepText removed - now using RecyclerView adapter
}
