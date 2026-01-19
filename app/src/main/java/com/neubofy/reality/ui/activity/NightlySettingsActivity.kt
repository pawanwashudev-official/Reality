package com.neubofy.reality.ui.activity

import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.TasksScopes
import com.google.api.services.tasks.model.TaskList
import com.neubofy.reality.databinding.ActivityNightlySettingsBinding
import com.neubofy.reality.google.GoogleAuthManager
import com.neubofy.reality.utils.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern
import com.neubofy.reality.utils.SavedPreferencesLoader
import com.neubofy.reality.utils.UsageUtils
import com.neubofy.reality.utils.XPManager
import com.neubofy.reality.utils.GamificationLevel
import android.graphics.Color



class NightlySettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNightlySettingsBinding
    private val PREFS_NAME = "nightly_prefs"
    
    // Time window settings (in minutes from midnight)
    private var startTimeMinutes = 22 * 60 // 22:00 default
    private var endTimeMinutes = 23 * 60 + 59 // 23:59 default

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityNightlySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        setupListeners()
        loadSavedData()
        setupGamificationStats()
    }

    private fun setupGamificationStats() {
        // Setup "Edit Levels" Button (Dialog)
        binding.btnOpenLevelEditor.setOnClickListener {
            if (checkLocked()) return@setOnClickListener
            try {
                showLevelEditorDialog()
            } catch (e: Exception) {
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }

        // Initial setup
        setupRetentionSettings()
        setupLockSettings()

        refreshGamificationTable()
    }
    
    private fun setupLockSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        
        binding.switchLockStartTimeEdit.isChecked = prefs.getBoolean("lock_start_time_edit", false)
        
        binding.switchLockStartTimeEdit.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("lock_start_time_edit", isChecked).apply()
        }
    }

    private fun setupRetentionSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        
        // --- Raw Data Retention ---
        val rawOptions = listOf(
            "7 Days" to 7, 
            "30 Days" to 30, 
            "60 Days" to 60
        )
        val rawAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, rawOptions.map { it.first })
        binding.spinnerRawDataRetention.adapter = rawAdapter
        
        // Restore saved raw preference (default 30)
        val savedRaw = prefs.getInt("retention_raw_days", 30)
        val rawIndex = rawOptions.indexOfFirst { it.second == savedRaw }.coerceAtLeast(1) // Default to 30 index
        binding.spinnerRawDataRetention.setSelection(rawIndex)
        
        binding.spinnerRawDataRetention.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val days = rawOptions[position].second
                prefs.edit().putInt("retention_raw_days", days).apply()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        
        // --- Daily Stats Retention ---
        val statsOptions = listOf(
            "60 Days" to 60,
            "120 Days" to 120,
            "1 Year" to 365,
            "Forever" to -1
        )
        val statsAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, statsOptions.map { it.first })
        binding.spinnerDailyStatsRetention.adapter = statsAdapter
        
        // Restore saved stats preference (default Forever)
        val savedStats = prefs.getInt("retention_stats_days", -1)
        val statsIndex = statsOptions.indexOfFirst { it.second == savedStats }.coerceAtLeast(3) // Default to Forever index
        binding.spinnerDailyStatsRetention.setSelection(statsIndex)
        
        binding.spinnerDailyStatsRetention.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                val days = statsOptions[position].second
                prefs.edit().putInt("retention_stats_days", days).apply()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
    }
    
    private fun showLevelEditorDialog() {
        val dialogView = layoutInflater.inflate(com.neubofy.reality.R.layout.dialog_level_editor, null)
        
        // Find views in dialog layout
        val spinnerSelector = dialogView.findViewById<android.widget.Spinner>(com.neubofy.reality.R.id.spinner_level_selector)
        val etName = dialogView.findViewById<android.widget.EditText>(com.neubofy.reality.R.id.et_level_name_dialog)
        val etXp = dialogView.findViewById<android.widget.EditText>(com.neubofy.reality.R.id.et_level_xp_dialog)
        val etStreak = dialogView.findViewById<android.widget.EditText>(com.neubofy.reality.R.id.et_level_streak_dialog)
        
        // Setup Selector
        val levels = (1..100).map { "Level $it" }
        val adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, levels)
        spinnerSelector.adapter = adapter
        
        // Helper to load data
        fun loadLevel(levelId: Int) {
            val xpManager = XPManager
            val allLevels = xpManager.getAllLevels(this)
            val levelInfo = allLevels.find { it.level == levelId } ?: allLevels[0]
            etName.setText(levelInfo.name)
            etXp.setText(levelInfo.requiredXP.toString())
            etStreak.setText(levelInfo.requiredStreak.toString())
        }
        
        // Listener
        spinnerSelector.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
             override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                 val levelId = position + 1
                 loadLevel(levelId)
             }
             override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        
        // Build Dialog
        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("Update Preset") { _, _ ->
                 // Save Logic
                 val levelId = spinnerSelector.selectedItemPosition + 1
                 
                 val name = etName.text.toString().trim()
                 val xp = etXp.text.toString().toIntOrNull()
                 val streak = etStreak.text.toString().toIntOrNull()
                 
                 if (name.isNotEmpty() && xp != null && streak != null) {
                    XPManager.saveLevelOverride(this, levelId, name, xp, streak)
                    android.widget.Toast.makeText(this, "Level $levelId preset updated!", android.widget.Toast.LENGTH_SHORT).show()
                    refreshGamificationTable() // Refresh main table
                 } else {
                    android.widget.Toast.makeText(this, "Invalid input", android.widget.Toast.LENGTH_SHORT).show()
                 }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkLocked(): Boolean {
        val loader = SavedPreferencesLoader(this)
        val strictData = loader.getStrictModeData()
        if (strictData.isEnabled && strictData.isGamificationLocked) {
             android.widget.Toast.makeText(this, "Locked by Strict Mode", android.widget.Toast.LENGTH_SHORT).show()
             return true
        }
        return false
    }




    
    private fun refreshGamificationTable() {
        binding.llLevelTableContainer.removeAllViews()
        val xpManager = XPManager
        val allLevels = xpManager.getAllLevels(this)
        val currentLevel = xpManager.getXPBreakdown(this).level
        
        for (level in allLevels) { // Should be 100 items
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                setPadding(0, 10, 0, 10)
                if (level.level == currentLevel) {
                    setBackgroundColor(android.graphics.Color.parseColor("#20FFFFFF")) // Highlight
                }
            }
            
            // ID
            row.addView(android.widget.TextView(this).apply {
                text = level.level.toString()
                width = dpToPx(30)
                setTextColor(if(level.level==currentLevel) getColor(com.google.android.material.R.color.material_dynamic_primary70) else android.graphics.Color.GRAY)
                setTypeface(null, android.graphics.Typeface.BOLD)
            })
            
            // Name
            row.addView(android.widget.TextView(this).apply {
                text = level.name
                layoutParams = android.widget.LinearLayout.LayoutParams(0, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setTextColor(android.graphics.Color.WHITE)
            })
            
            // XP
            row.addView(android.widget.TextView(this).apply {
                text = "${level.requiredXP / 1000}k"
                width = dpToPx(60)
                gravity = android.view.Gravity.END
                setTextColor(android.graphics.Color.LTGRAY)
                textSize = 12f
            })
            
             // Streak
            row.addView(android.widget.TextView(this).apply {
                text = level.requiredStreak.toString()
                width = dpToPx(40)
                gravity = android.view.Gravity.END
                setTextColor(android.graphics.Color.LTGRAY)
                textSize = 12f
            })
            
            binding.llLevelTableContainer.addView(row)
        }
    }
    
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
    
    private fun updateGamificationDisplay() {
        // Legacy stub if needed, or redirect

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

        // Auto Setup Folders (Drive)
        binding.cardAutoSetup.setOnClickListener {
            autoSetupDriveFolders()
        }

        // Save Manual Folder Links
        binding.btnSaveFolders.setOnClickListener {
            saveManualFolderLinks()
        }

        // Auto Setup Task Lists
        binding.cardAutoTasks.setOnClickListener {
            autoSetupTaskLists()
        }

        // Find Task List IDs by Name
        binding.btnFindTaskLists.setOnClickListener {
            findTaskListsByName()
        }

        // Erase All Setup Data
        binding.btnEraseSetup.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Erase Setup Data?")
                .setMessage("This will remove all saved folder IDs and task list IDs. You will need to set up again.")
                .setPositiveButton("Erase") { _, _ ->
                    eraseAllSetupData()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Clear Nightly Memory - Day Selector Dialog
        binding.btnClearNightlyMemory.setOnClickListener {
            showDaySelectClearDialog()
        }

        // Verify All Connections
        binding.btnVerifySetup.setOnClickListener {
            verifyAllConnections()
        }
        
        // Time Window Pickers
        binding.btnStartTime.setOnClickListener {
            showTimePicker(true)
        }
        
        binding.btnEndTime.setOnClickListener {
            showTimePicker(false)
        }

        
        // Save Schedule Button
        binding.btnSaveSchedule.setOnClickListener {
            saveTimeWindowSettings()
            updateScheduleDisplay()
            Toast.makeText(this, "Schedule saved!", Toast.LENGTH_SHORT).show()
        }
        
        // Edit Schedule Button
        binding.btnEditSchedule.setOnClickListener {
            binding.cardSavedSchedule.visibility = View.GONE
            binding.cardScheduleForm.visibility = View.VISIBLE
            binding.btnEditSchedule.visibility = View.GONE
        }
        
        // Delete Schedule Button
        binding.btnDeleteSchedule.setOnClickListener {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .remove("nightly_start_time")
                .remove("nightly_end_time")
                .putBoolean("schedule_saved", false)
                .apply()
            startTimeMinutes = 22 * 60
            endTimeMinutes = 23 * 60 + 59
            binding.btnStartTime.text = "22:00"
            binding.btnEndTime.text = "23:59"
            updateScheduleDisplay()
            Toast.makeText(this, "Schedule deleted", Toast.LENGTH_SHORT).show()
        }
        
        // System Prompt - Load and display
        loadSystemPrompt()
        updatePromptDisplay()
        
        // Edit Prompt Button
        binding.btnEditPrompt.setOnClickListener {
            binding.cardSavedPrompt.visibility = View.GONE
            binding.cardEditPrompt.visibility = View.VISIBLE
            binding.btnEditPrompt.visibility = View.GONE
            binding.etSystemPrompt.setText(getCurrentPrompt())
        }
        
        // Cancel Edit Button
        binding.btnCancelPrompt.setOnClickListener {
            binding.cardSavedPrompt.visibility = View.VISIBLE
            binding.cardEditPrompt.visibility = View.GONE
            binding.btnEditPrompt.visibility = View.VISIBLE
        }
        
        // Save Prompt Button
        binding.btnSavePrompt.setOnClickListener {
            val prompt = binding.etSystemPrompt.text.toString()
            if (prompt.isNotBlank()) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putString("custom_ai_prompt", prompt)
                    .apply()
                updatePromptDisplay()
                binding.cardSavedPrompt.visibility = View.VISIBLE
                binding.cardEditPrompt.visibility = View.GONE
                binding.btnEditPrompt.visibility = View.VISIBLE
                Toast.makeText(this, "Prompt saved!", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Reset Prompt Button
        binding.btnResetPrompt.setOnClickListener {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .remove("custom_ai_prompt")
                .apply()
            binding.etSystemPrompt.setText(getDefaultPromptTemplate())
            Toast.makeText(this, "Prompt reset to default", Toast.LENGTH_SHORT).show()
        }
        
        // Placeholder Chip Click Listeners (insert at cursor)
        setupPlaceholderChips()
        
        // Screen Time Limit
        setupScreenTimeLimit()

        // Analyzer Prompt
        setupAnalyzerPrompt()
    }
    
    private fun setupScreenTimeLimit() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        
        fun updateDisplay() {
            val savedLimit = prefs.getInt("screen_time_limit_minutes", 0)
            if (savedLimit > 0) {
                binding.tvCurrentLimit.text = "$savedLimit min / day"
                binding.etScreenTimeLimit.setText(savedLimit.toString())
            } else {
                binding.tvCurrentLimit.text = "Not set"
                binding.etScreenTimeLimit.setText("")
            }
        }
        
        // Initial state
        updateDisplay()
        
        // Load affected apps & usage stats
        lifecycleScope.launch(Dispatchers.IO) {
            val prefsLoader = SavedPreferencesLoader(this@NightlySettingsActivity)
            val focusData = prefsLoader.getFocusModeData()
            val allSelected = if (focusData.selectedApps.isNotEmpty()) focusData.selectedApps else HashSet(prefsLoader.getFocusModeSelectedApps())
            
            // 1. Filter for Focus Mode apps
            val affectedPkgs = allSelected.filter { pkg ->
                prefsLoader.getBlockedAppConfig(pkg).blockInFocus
            }
            
            // 2. Get Usage Stats (if permitted)
            val hasUsagePerm = UsageUtils.hasUsageStatsPermission(this@NightlySettingsActivity)
            val totalUsedMillis = UsageUtils.getFocusedAppsUsage(this@NightlySettingsActivity)
            
            
            // 3. Get Labels
            val affectedLabels = affectedPkgs.mapNotNull { pkg ->
                try {
                    packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
                } catch (e: Exception) {
                    null 
                }
            }.sorted()
            
            withContext(Dispatchers.Main) {
                // Update Apps List
                binding.tvAffectedApps.text = if (affectedLabels.isNotEmpty()) {
                    "Applied to: ${affectedLabels.joinToString(", ")}"
                } else {
                    "Applied to: No apps marked for Focus Mode"
                }
                
                // Update Progress & Status
                val limitMins = prefs.getInt("screen_time_limit_minutes", 0)
                if (!hasUsagePerm) {
                    binding.tvLimitStatus.text = "Permission needed for usage stats"
                    binding.progressLimit.isIndeterminate = true
                } else if (limitMins > 0) {
                    val usedMins = (totalUsedMillis / 60000).toInt()
                    
                    binding.progressLimit.isIndeterminate = false
                    binding.progressLimit.max = limitMins
                    binding.progressLimit.progress = usedMins
                    
                    if (usedMins > limitMins) {
                         val over = usedMins - limitMins
                         val penalty = (over * 10).coerceAtMost(500)
                         binding.tvLimitStatus.text = "Used: ${usedMins}m (Over by ${over}m ⚠️) • Penalty: -${penalty} XP"
                         binding.progressLimit.setIndicatorColor(Color.parseColor("#B00020")) // Error Red
                    } else {
                         val left = limitMins - usedMins
                         val bonus = (left * 10).coerceAtMost(500)
                         binding.tvLimitStatus.text = "Used: ${usedMins}m (${left}m left) • Bonus: +${bonus} XP"
                         // Default color (Primary from theme)
                         binding.progressLimit.setIndicatorColor(getThemeColor(com.google.android.material.R.attr.colorPrimary))
                    }
                } else {
                    binding.tvLimitStatus.text = "Set a limit to see progress"
                    binding.progressLimit.progress = 0
                }
            }
        }
        
        // Edit button
        binding.btnEditLimit.setOnClickListener {
            val loader = com.neubofy.reality.utils.SavedPreferencesLoader(this)
            val strictData = loader.getStrictModeData()
            
            if (strictData.isEnabled && strictData.isNightlyLimitLocked) {
                 android.widget.Toast.makeText(this, "Locked by Strict Mode", android.widget.Toast.LENGTH_SHORT).show()
                 return@setOnClickListener
            }

            binding.layoutViewLimit.visibility = View.GONE
            binding.layoutEditLimit.visibility = View.VISIBLE
            binding.etScreenTimeLimit.requestFocus()
        }
        
        // Save button
        binding.btnSaveScreenLimit.setOnClickListener {
            val limitStr = binding.etScreenTimeLimit.text.toString()
            val limit = limitStr.toIntOrNull() ?: 0
            
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putInt("screen_time_limit_minutes", limit)
                .apply()
            
            updateDisplay()
            
            binding.layoutEditLimit.visibility = View.GONE
            binding.layoutViewLimit.visibility = View.VISIBLE
            
            Toast.makeText(this, "Screen time limit saved", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupPlaceholderChips() {
        val placeholderInfo = mapOf(
            binding.chipUserIntro to Pair("{user_intro}", "User's personal introduction from AI Settings"),
            binding.chipDate to Pair("{date}", "Date of diary (e.g., Monday, January 18)"),
            binding.chipCalendar to Pair("{calendar}", "Calendar events from device"),
            binding.chipTasksDue to Pair("{tasks_due}", "Tasks due today from Google Tasks"),
            binding.chipTasksCompleted to Pair("{tasks_completed}", "Completed tasks from Google Tasks"),
            binding.chipSessions to Pair("{sessions}", "Tapasya study sessions completed"),
            binding.chipStats to Pair("{stats}", "Summary statistics (planned time, effective time, efficiency)")
        )
        
        placeholderInfo.forEach { (chip, info) ->
            // Single tap: insert placeholder at cursor
            chip.setOnClickListener {
                val placeholder = info.first
                val editText = binding.etSystemPrompt
                val start = editText.selectionStart.coerceAtLeast(0)
                val end = editText.selectionEnd.coerceAtLeast(0)
                editText.text?.replace(start.coerceAtMost(end), start.coerceAtLeast(end), placeholder)
            }
            
            // Long press: show description
            chip.setOnLongClickListener {
                Toast.makeText(this, info.second, Toast.LENGTH_LONG).show()
                true
            }
        }
    }
    
    private fun getCurrentPrompt(): String {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getString("custom_ai_prompt", null) ?: getDefaultPromptTemplate()
    }
    
    private fun updatePromptDisplay() {
        val prompt = getCurrentPrompt()
        // Show first few lines as preview
        val preview = prompt.lines().take(5).joinToString("\n")
        binding.tvSavedPromptPreview.text = if (prompt.lines().size > 5) "$preview..." else preview
    }
    
    private fun loadSystemPrompt() {
        // Just for initialization - actual display is handled by updatePromptDisplay
    }
    
    private fun getDefaultPromptTemplate(): String {
        return """You are a supportive but honest personal productivity coach.

{user_intro}

Today is {date}.

📅 SCHEDULED CALENDAR EVENTS:
{calendar}

📋 TASKS DUE TODAY:
{tasks_due}

✅ TASKS COMPLETED TODAY:
{tasks_completed}

⏱️ STUDY/WORK SESSIONS (Tapasya):
{sessions}

📊 STATISTICS:
{stats}

Based on this comprehensive data, generate EXACTLY 5 personalized reflection questions.

Guidelines:
1. If there's a gap between planned and actual, ask about what happened (gently but directly)
2. If they completed many tasks, acknowledge and ask what helped them succeed
3. If tasks are pending, ask about priorities and blockers
4. Ask about their emotional/mental state during work
5. Help them plan improvements for tomorrow

Be warm and supportive, but also honest. Don't sugarcoat if they underperformed.
If no plan was set, ask about setting intentions.
If no work was done, be compassionate but encourage reflection on barriers.

Return ONLY the 5 questions, numbered 1-5, one per line. No other text."""
    }
    
    private fun showTimePicker(isStartTime: Boolean) {
        val currentMinutes = if (isStartTime) startTimeMinutes else endTimeMinutes
        val hour = currentMinutes / 60
        val minute = currentMinutes % 60
        
        com.google.android.material.timepicker.MaterialTimePicker.Builder()
            .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
            .setHour(hour)
            .setMinute(minute)
            .setTitleText(if (isStartTime) "Select Start Time" else "Select End Time")
            .build()
            .apply {
                addOnPositiveButtonClickListener {
                    val newMinutes = this.hour * 60 + this.minute
                    if (isStartTime) {
                        startTimeMinutes = newMinutes
                        binding.btnStartTime.text = formatTime(newMinutes)
                    } else {
                        endTimeMinutes = newMinutes
                        binding.btnEndTime.text = formatTime(newMinutes)
                    }
                    saveTimeWindowSettings()
                }
                show(supportFragmentManager, "time_picker")
            }
    }
    
    private fun formatTime(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return String.format("%02d:%02d", h, m)
    }
    
    // Day Selector Clear Dialog - Shows available days with checkboxes
    private fun setupTemplates() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultDiary = com.neubofy.reality.data.NightlyProtocolExecutor.DEFAULT_DIARY_TEMPLATE
        val defaultPlan = com.neubofy.reality.data.NightlyProtocolExecutor.DEFAULT_PLAN_TEMPLATE
        
        fun load() {
            val savedDiary = prefs.getString("template_diary", null)
            val savedPlan = prefs.getString("template_plan", null)
            
            // Update Saved View
            binding.tvDiaryTemplatePreview.text = savedDiary ?: "Default Template"
            binding.tvPlanTemplatePreview.text = savedPlan ?: "Default Template"
            
            // Update Editor Fields (pre-fill)
            binding.etDiaryTemplate.setText(savedDiary ?: defaultDiary)
            binding.etPlanTemplate.setText(savedPlan ?: defaultPlan)
        }
        
        // Initial Load
        load()
        
        // Edit Button (Show Editor)
        binding.btnEditTemplates.setOnClickListener {
            binding.cardSavedTemplates.visibility = View.GONE
            binding.cardEditTemplates.visibility = View.VISIBLE
            binding.btnEditTemplates.visibility = View.GONE
        }
        
        // Cancel Button (Hide Editor)
        binding.btnCancelTemplates.setOnClickListener {
            binding.cardSavedTemplates.visibility = View.VISIBLE
            binding.cardEditTemplates.visibility = View.GONE
            binding.btnEditTemplates.visibility = View.VISIBLE
            load() // Revert changes in editor
        }
        
        // Reset Button
        binding.btnResetTemplates.setOnClickListener {
            binding.etDiaryTemplate.setText(defaultDiary)
            binding.etPlanTemplate.setText(defaultPlan)
            Toast.makeText(this, "Templates Reset to Defaults", Toast.LENGTH_SHORT).show()
        }
        
        // Save Button
        binding.btnSaveTemplates.setOnClickListener {
            val newDiary = binding.etDiaryTemplate.text.toString()
            val newPlan = binding.etPlanTemplate.text.toString()
            
            val editor = prefs.edit()
            
            if (newDiary.isBlank() || newDiary == defaultDiary) {
                editor.remove("template_diary")
            } else {
                editor.putString("template_diary", newDiary)
            }
            
            if (newPlan.isBlank() || newPlan == defaultPlan) {
                editor.remove("template_plan")
            } else {
                editor.putString("template_plan", newPlan)
            }
            
            editor.apply()
            Toast.makeText(this, "Templates Saved", Toast.LENGTH_SHORT).show()
            
            load() // Refresh view
            binding.cardSavedTemplates.visibility = View.VISIBLE
            binding.cardEditTemplates.visibility = View.GONE
            binding.btnEditTemplates.visibility = View.VISIBLE
        }
    }
    
    // Day Selector Clear Dialog - Shows available days with checkboxes
    private fun showDaySelectClearDialog() {
        val prefs = getSharedPreferences("nightly_prefs", MODE_PRIVATE)
        
        // Find all saved dates from prefs keys (state_YYYY-MM-DD pattern)
        val savedDates = prefs.all.keys
            .filter { it.startsWith("state_") }
            .mapNotNull { key ->
                try {
                    val dateStr = key.removePrefix("state_")
                    java.time.LocalDate.parse(dateStr)
                } catch (e: Exception) { null }
            }
            .distinct()
            .sortedDescending()
        
        if (savedDates.isEmpty()) {
            Toast.makeText(this, "No saved data found", Toast.LENGTH_SHORT).show()
            return
        }
        
        val dateLabels = savedDates.map { date ->
            val formatter = java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d")
            date.format(formatter)
        }.toTypedArray()
        
        val checkedItems = BooleanArray(savedDates.size) { false }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Select Days to Clear")
            .setMultiChoiceItems(dateLabels, checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Delete Selected") { _, _ ->
                val selectedDates = savedDates.filterIndexed { index, _ -> checkedItems[index] }
                if (selectedDates.isEmpty()) {
                    Toast.makeText(this, "No days selected", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                // Clear data for selected dates
                val editor = prefs.edit()
                selectedDates.forEach { date ->
                    val dateStr = date.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                    // Remove all keys for this date
                    prefs.all.keys.filter { key ->
                        key.contains(dateStr)
                    }.forEach { key ->
                        editor.remove(key)
                    }
                }
                editor.apply()
                
                Toast.makeText(this, "${selectedDates.size} day(s) cleared", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Clear All") { _, _ ->
                com.neubofy.reality.data.NightlyProtocolExecutor.clearMemory(this)
                Toast.makeText(this, "All Nightly Memory Cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    

    
    private fun saveTimeWindowSettings() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putInt("nightly_start_time", startTimeMinutes)
            .putInt("nightly_end_time", endTimeMinutes)
            .putBoolean("schedule_saved", true)
            .apply()
        // Auto-start removed - process is now fully manual
    }
    
    private fun loadTimeWindowSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        startTimeMinutes = prefs.getInt("nightly_start_time", 22 * 60)
        endTimeMinutes = prefs.getInt("nightly_end_time", 23 * 60 + 59)
        
        binding.btnStartTime.text = formatTime(startTimeMinutes)
        binding.btnEndTime.text = formatTime(endTimeMinutes)
        
        updateScheduleDisplay()
    }
    
    private fun updateScheduleDisplay() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val isSaved = prefs.getBoolean("schedule_saved", false)
        
        if (isSaved) {
            // Show saved state
            binding.cardSavedSchedule.visibility = View.VISIBLE
            binding.cardScheduleForm.visibility = View.GONE
            binding.btnEditSchedule.visibility = View.VISIBLE
            binding.tvSavedSchedule.text = "${formatTime(startTimeMinutes)} → ${formatTime(endTimeMinutes)}"
            
            // Auto-Start UI removed - process is now fully manual
        } else {
            // Show form
            binding.cardSavedSchedule.visibility = View.GONE
            binding.cardScheduleForm.visibility = View.VISIBLE
            binding.btnEditSchedule.visibility = View.GONE
        }
    }

    private fun loadSavedData() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Load time window settings first
        loadTimeWindowSettings()

        // Load folder IDs
        val realityId = prefs.getString("reality_folder_id", null)
        val diaryId = prefs.getString("diary_folder_id", null)
        val reportId = prefs.getString("report_folder_id", null)
        
        val hasDriveFolders = !realityId.isNullOrEmpty() || !diaryId.isNullOrEmpty() || !reportId.isNullOrEmpty()

        if (hasDriveFolders) {
            // Hide setup forms, show saved IDs
            binding.cardAutoSetup.visibility = View.GONE
            binding.cardManualSetup.visibility = View.GONE
            binding.cardSavedFolders.visibility = View.VISIBLE
            
            val sb = StringBuilder()
            if (!realityId.isNullOrEmpty()) sb.append("Reality: $realityId\n")
            if (!diaryId.isNullOrEmpty()) sb.append("Daily Diary: $diaryId\n")
            if (!reportId.isNullOrEmpty()) sb.append("Report: $reportId")
            binding.tvSavedFolderIds.text = sb.toString().trim()
        } else {
            // Show setup forms, hide saved IDs
            binding.cardAutoSetup.visibility = View.VISIBLE
            binding.cardManualSetup.visibility = View.VISIBLE
            binding.cardSavedFolders.visibility = View.GONE
        }

        // Load task list IDs
        val taskList1Id = prefs.getString("task_list_1_id", null)
        val taskList1Name = prefs.getString("task_list_1_name", null)
        val taskList2Id = prefs.getString("task_list_2_id", null)
        val taskList2Name = prefs.getString("task_list_2_name", null)
        
        val hasTaskLists = !taskList1Id.isNullOrEmpty() || !taskList2Id.isNullOrEmpty()

        if (hasTaskLists) {
            // Hide setup forms, show saved IDs
            binding.cardAutoTasks.visibility = View.GONE
            binding.cardManualTasks.visibility = View.GONE
            binding.cardSavedTasks.visibility = View.VISIBLE
            
            val sb = StringBuilder()
            if (!taskList1Id.isNullOrEmpty()) sb.append("${taskList1Name ?: "List 1"}: $taskList1Id\n")
            if (!taskList2Id.isNullOrEmpty()) sb.append("${taskList2Name ?: "List 2"}: $taskList2Id")
            binding.tvSavedTaskIds.text = sb.toString().trim()
        } else {
            // Show setup forms, hide saved IDs
            binding.cardAutoTasks.visibility = View.VISIBLE
            binding.cardManualTasks.visibility = View.VISIBLE
            binding.cardSavedTasks.visibility = View.GONE
        }
    }

    private fun extractFolderIdFromUrl(url: String): String? {
        // Patterns:
        // https://drive.google.com/drive/folders/FOLDER_ID
        // https://drive.google.com/drive/u/0/folders/FOLDER_ID
        val pattern = Pattern.compile("folders/([a-zA-Z0-9_-]+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun saveManualFolderLinks() {
        val realityUrl = binding.etRealityFolder.text.toString().trim()
        val diaryUrl = binding.etDiaryFolder.text.toString().trim()
        val reportUrl = binding.etReportFolder.text.toString().trim()

        val realityId = if (realityUrl.isNotEmpty()) extractFolderIdFromUrl(realityUrl) ?: realityUrl else null
        val diaryId = if (diaryUrl.isNotEmpty()) extractFolderIdFromUrl(diaryUrl) ?: diaryUrl else null
        val reportId = if (reportUrl.isNotEmpty()) extractFolderIdFromUrl(reportUrl) ?: reportUrl else null

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
        if (realityId != null) prefs.putString("reality_folder_id", realityId)
        if (diaryId != null) prefs.putString("diary_folder_id", diaryId)
        if (reportId != null) prefs.putString("report_folder_id", reportId)
        prefs.apply()

        Toast.makeText(this, "Folder links saved!", Toast.LENGTH_SHORT).show()
        loadSavedData()
        
        // Clear inputs
        binding.etRealityFolder.text?.clear()
        binding.etDiaryFolder.text?.clear()
        binding.etReportFolder.text?.clear()
    }

    private fun autoSetupDriveFolders() {
        if (!GoogleAuthManager.isSignedIn(this)) {
            Toast.makeText(this, "Please sign in with Google first (Settings > Account)", Toast.LENGTH_LONG).show()
            return
        }
        
        val accountEmail = GoogleAuthManager.getUserEmail(this)

        lifecycleScope.launch {
            try {
                Toast.makeText(this@NightlySettingsActivity, "Creating folders...", Toast.LENGTH_SHORT).show()

                val (realityId, diaryId, reportId) = withContext(Dispatchers.IO) {
                    val credential = GoogleAuthManager.getGoogleAccountCredential(this@NightlySettingsActivity)
                    if (credential == null) {
                        throw Exception("Failed to get Google credential")
                    }

                    val driveService = Drive.Builder(
                        GoogleAuthManager.getHttpTransport(),
                        GoogleAuthManager.getJsonFactory(),
                        credential
                    ).setApplicationName("Reality").build()

                    // Create or find Reality folder
                    val realityFolderId = findOrCreateFolder(driveService, "Reality", null)

                    // Create Daily Diary folder inside Reality
                    val diaryFolderId = findOrCreateFolder(driveService, "Daily Diary", realityFolderId)

                    // Create Report of the Day folder inside Reality
                    val reportFolderId = findOrCreateFolder(driveService, "Report of the Day", realityFolderId)

                    Triple(realityFolderId, diaryFolderId, reportFolderId)
                }

                // Save to prefs
                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                prefs.putString("reality_folder_id", realityId)
                prefs.putString("diary_folder_id", diaryId)
                prefs.putString("report_folder_id", reportId)
                prefs.apply()

                Toast.makeText(this@NightlySettingsActivity, "Folders created successfully!", Toast.LENGTH_SHORT).show()
                loadSavedData()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@NightlySettingsActivity,
                    "Failed to create folders: ${e.message}\n\nPlease create manually and paste links.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun findOrCreateFolder(driveService: Drive, name: String, parentId: String?): String {
        // First, search ANYWHERE for an existing folder with this name (for Reality folder)
        // For subfolders, we specifically look within the parent
        val query = if (parentId == null) {
            // For root-level folders like "Reality", search anywhere first
            "mimeType='application/vnd.google-apps.folder' and name='$name' and trashed=false"
        } else {
            // For subfolders, search specifically within the parent
            "mimeType='application/vnd.google-apps.folder' and name='$name' and trashed=false and '$parentId' in parents"
        }

        val result = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        if (result.files.isNotEmpty()) {
            // Found existing folder, use it
            return result.files[0].id
        }

        // Create new folder (only if not found anywhere)
        val folderMetadata = DriveFile().apply {
            this.name = name
            this.mimeType = "application/vnd.google-apps.folder"
            if (parentId != null) {
                this.parents = listOf(parentId)
            }
        }

        val folder = driveService.files().create(folderMetadata)
            .setFields("id")
            .execute()

        return folder.id
    }

    private fun autoSetupTaskLists() {
        if (!GoogleAuthManager.isSignedIn(this)) {
            Toast.makeText(this, "Please sign in with Google first (Settings > Account)", Toast.LENGTH_LONG).show()
            return
        }
        
        val accountEmail = GoogleAuthManager.getUserEmail(this)

        lifecycleScope.launch {
            try {
                Toast.makeText(this@NightlySettingsActivity, "Setting up task lists...", Toast.LENGTH_SHORT).show()

                val (list1Id, list1Name, list2Id, list2Name) = withContext(Dispatchers.IO) {
                    val credential = GoogleAuthManager.getGoogleAccountCredential(this@NightlySettingsActivity)
                    if (credential == null) {
                        throw Exception("Failed to get Google credential")
                    }

                    val tasksService = Tasks.Builder(
                        GoogleAuthManager.getHttpTransport(),
                        GoogleAuthManager.getJsonFactory(),
                        credential
                    ).setApplicationName("Reality").build()

                    // Get all task lists
                    val taskLists = tasksService.tasklists().list().execute().items ?: emptyList()

                    // Try to find or create "Reality Daily" and "Reality Tomorrow"
                    val dailyList = taskLists.find { it.title == "Reality Daily" }
                        ?: createTaskList(tasksService, "Reality Daily")

                    val tomorrowList = taskLists.find { it.title == "Reality Tomorrow" }
                        ?: createTaskList(tasksService, "Reality Tomorrow")

                    listOf(dailyList.id, dailyList.title, tomorrowList.id, tomorrowList.title)
                }

                // Save to prefs
                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                prefs.putString("task_list_1_id", list1Id)
                prefs.putString("task_list_1_name", list1Name)
                prefs.putString("task_list_2_id", list2Id)
                prefs.putString("task_list_2_name", list2Name)
                prefs.apply()

                Toast.makeText(this@NightlySettingsActivity, "Task lists configured!", Toast.LENGTH_SHORT).show()
                loadSavedData()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@NightlySettingsActivity,
                    "Failed to setup task lists: ${e.message}\n\nPlease create manually and enter names below.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun createTaskList(tasksService: Tasks, name: String): TaskList {
        val newList = TaskList().setTitle(name)
        return tasksService.tasklists().insert(newList).execute()
    }

    private fun findTaskListsByName() {
        val name1 = binding.etTaskList1.text.toString().trim()
        val name2 = binding.etTaskList2.text.toString().trim()

        if (name1.isEmpty() && name2.isEmpty()) {
            Toast.makeText(this, "Please enter at least one task list name", Toast.LENGTH_SHORT).show()
            return
        }

        if (!GoogleAuthManager.isSignedIn(this)) {
            Toast.makeText(this, "Please sign in with Google first (Settings > Account)", Toast.LENGTH_LONG).show()
            return
        }
        
        val accountEmail = GoogleAuthManager.getUserEmail(this)

        lifecycleScope.launch {
            try {
                Toast.makeText(this@NightlySettingsActivity, "Finding task lists...", Toast.LENGTH_SHORT).show()

                val result = withContext(Dispatchers.IO) {
                    val credential = GoogleAuthManager.getGoogleAccountCredential(this@NightlySettingsActivity)
                    if (credential == null) {
                        throw Exception("Failed to get Google credential")
                    }

                    val tasksService = Tasks.Builder(
                        GoogleAuthManager.getHttpTransport(),
                        GoogleAuthManager.getJsonFactory(),
                        credential
                    ).setApplicationName("Reality").build()

                    val taskLists = tasksService.tasklists().list().execute().items ?: emptyList()

                    val list1 = if (name1.isNotEmpty()) taskLists.find { it.title.equals(name1, ignoreCase = true) } else null
                    val list2 = if (name2.isNotEmpty()) taskLists.find { it.title.equals(name2, ignoreCase = true) } else null

                    Pair(list1, list2)
                }

                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                var foundCount = 0

                result.first?.let {
                    prefs.putString("task_list_1_id", it.id)
                    prefs.putString("task_list_1_name", it.title)
                    foundCount++
                }

                result.second?.let {
                    prefs.putString("task_list_2_id", it.id)
                    prefs.putString("task_list_2_name", it.title)
                    foundCount++
                }

                prefs.apply()

                if (foundCount > 0) {
                    Toast.makeText(this@NightlySettingsActivity, "Found $foundCount task list(s)!", Toast.LENGTH_SHORT).show()
                    loadSavedData()
                    binding.etTaskList1.text?.clear()
                    binding.etTaskList2.text?.clear()
                } else {
                    Toast.makeText(this@NightlySettingsActivity, "No matching task lists found. Check names.", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@NightlySettingsActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun eraseAllSetupData() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
        prefs.clear()
        prefs.apply()

        Toast.makeText(this, "All setup data erased.", Toast.LENGTH_SHORT).show()
        loadSavedData()
    }

    private fun verifyAllConnections() {
        if (!GoogleAuthManager.isSignedIn(this)) {
            Toast.makeText(this, "Please sign in with Google first", Toast.LENGTH_LONG).show()
            return
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val realityId = prefs.getString("reality_folder_id", null)
        val diaryId = prefs.getString("diary_folder_id", null)
        val reportId = prefs.getString("report_folder_id", null)
        val taskList1Id = prefs.getString("task_list_1_id", null)
        val taskList2Id = prefs.getString("task_list_2_id", null)

        if (realityId.isNullOrEmpty() && diaryId.isNullOrEmpty() && taskList1Id.isNullOrEmpty()) {
            Toast.makeText(this, "No setup data to verify. Please set up first.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                Toast.makeText(this@NightlySettingsActivity, "Verifying connections...", Toast.LENGTH_SHORT).show()

                val results = withContext(Dispatchers.IO) {
                    val credential = GoogleAuthManager.getGoogleAccountCredential(this@NightlySettingsActivity)
                    if (credential == null) {
                        throw Exception("Failed to get Google credential")
                    }

                    val results = mutableListOf<String>()

                    // Verify Drive folders
                    if (!realityId.isNullOrEmpty() || !diaryId.isNullOrEmpty() || !reportId.isNullOrEmpty()) {
                        val driveService = Drive.Builder(
                            GoogleAuthManager.getHttpTransport(),
                            GoogleAuthManager.getJsonFactory(),
                            credential
                        ).setApplicationName("Reality").build()

                        realityId?.let {
                            try {
                                val file = driveService.files().get(it).setFields("id, name").execute()
                                results.add("✓ Reality folder: ${file.name}")
                            } catch (e: Exception) {
                                results.add("✗ Reality folder: Not accessible")
                            }
                        }

                        diaryId?.let {
                            try {
                                val file = driveService.files().get(it).setFields("id, name").execute()
                                results.add("✓ Diary folder: ${file.name}")
                            } catch (e: Exception) {
                                results.add("✗ Diary folder: Not accessible")
                            }
                        }

                        reportId?.let {
                            try {
                                val file = driveService.files().get(it).setFields("id, name").execute()
                                results.add("✓ Report folder: ${file.name}")
                            } catch (e: Exception) {
                                results.add("✗ Report folder: Not accessible")
                            }
                        }
                    }

                    // Verify Task Lists
                    if (!taskList1Id.isNullOrEmpty() || !taskList2Id.isNullOrEmpty()) {
                        val tasksService = Tasks.Builder(
                            GoogleAuthManager.getHttpTransport(),
                            GoogleAuthManager.getJsonFactory(),
                            credential
                        ).setApplicationName("Reality").build()

                        taskList1Id?.let {
                            try {
                                val list = tasksService.tasklists().get(it).execute()
                                results.add("✓ Task List 1: ${list.title}")
                            } catch (e: Exception) {
                                results.add("✗ Task List 1: Not accessible")
                            }
                        }

                        taskList2Id?.let {
                            try {
                                val list = tasksService.tasklists().get(it).execute()
                                results.add("✓ Task List 2: ${list.title}")
                            } catch (e: Exception) {
                                results.add("✗ Task List 2: Not accessible")
                            }
                        }
                    }

                    results
                }

                // Show results in dialog
                MaterialAlertDialogBuilder(this@NightlySettingsActivity)
                    .setTitle("Verification Results")
                    .setMessage(results.joinToString("\n"))
                    .setPositiveButton("OK", null)
                    .show()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@NightlySettingsActivity, "Verification failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupAnalyzerPrompt() {
        // Load and display
        updateAnalyzerPromptDisplay()
        
        // Edit Button
        binding.btnEditAnalyzerPrompt.setOnClickListener {
            binding.cardSavedAnalyzerPrompt.visibility = View.GONE
            binding.cardEditAnalyzerPrompt.visibility = View.VISIBLE
            binding.btnEditAnalyzerPrompt.visibility = View.GONE
            binding.etAnalyzerPrompt.setText(getCurrentAnalyzerPrompt())
        }
        
        // Cancel Button
        binding.btnCancelAnalyzerPrompt.setOnClickListener {
            binding.cardSavedAnalyzerPrompt.visibility = View.VISIBLE
            binding.cardEditAnalyzerPrompt.visibility = View.GONE
            binding.btnEditAnalyzerPrompt.visibility = View.VISIBLE
        }
        
        // Save Button
        binding.btnSaveAnalyzerPrompt.setOnClickListener {
            val prompt = binding.etAnalyzerPrompt.text.toString()
            if (prompt.isNotBlank()) {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putString("custom_analyzer_prompt", prompt)
                    .apply()
                updateAnalyzerPromptDisplay()
                binding.cardSavedAnalyzerPrompt.visibility = View.VISIBLE
                binding.cardEditAnalyzerPrompt.visibility = View.GONE
                binding.btnEditAnalyzerPrompt.visibility = View.VISIBLE
                Toast.makeText(this, "Analyzer prompt saved!", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Reset Button
        binding.btnResetAnalyzerPrompt.setOnClickListener {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .remove("custom_analyzer_prompt")
                .apply()
            binding.etAnalyzerPrompt.setText(getDefaultAnalyzerPromptTemplate())
            Toast.makeText(this, "Analyzer prompt reset to default", Toast.LENGTH_SHORT).show()
        }
        
        // Chips
        setupAnalyzerPlaceholderChips()
    }

    private fun setupAnalyzerPlaceholderChips() {
        val placeholderInfo = mapOf(
            binding.chipUserIntroAnalyzer to Pair("{user_intro}", "User's personal introduction"),
            binding.chipDiaryContent to Pair("{diary_content}", "The full text of the user's diary entry")
        )
        
        placeholderInfo.forEach { (chip, info) ->
            chip.setOnClickListener {
                val placeholder = info.first
                val editText = binding.etAnalyzerPrompt
                val start = editText.selectionStart.coerceAtLeast(0)
                val end = editText.selectionEnd.coerceAtLeast(0)
                editText.text?.replace(start.coerceAtMost(end), start.coerceAtLeast(end), placeholder)
            }
            chip.setOnLongClickListener {
                Toast.makeText(this, info.second, Toast.LENGTH_LONG).show()
                true
            }
        }
    }

    private fun getCurrentAnalyzerPrompt(): String {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getString("custom_analyzer_prompt", null) ?: getDefaultAnalyzerPromptTemplate()
    }

    private fun updateAnalyzerPromptDisplay() {
        val prompt = getCurrentAnalyzerPrompt()
        val preview = prompt.lines().take(5).joinToString("\n")
        binding.tvSavedAnalyzerPromptPreview.text = if (prompt.lines().size > 5) "$preview..." else preview
    }

    private fun getDefaultAnalyzerPromptTemplate(): String {
        return """You are a wise and strict mentor reviewing a student's nightly reflection.
Your goal is to ensure they are taking the process seriously and actually reflecting, not just going through the motions.

{user_intro}

Analyze the following Nightly Reflection Diary:

[[DIARY_START]]
{diary_content}
[[DIARY_END]]

EVALUATION CRITERIA:
1. DEPTH: Did they answer the questions with thought? (One word answers = Fail)
2. HONESTY: Does it seem genuine?
3. COMPLETENESS: Did they complete the reflection?

OUTPUT REQUIREMENTS:
You must output a single JSON object. Do not include markdown formatting like ```json.
{
  "xp": (integer 0-50, score for quality of reflection),
  "satisfied": (boolean, true if reflection is good enough to accept, false if lazy/incomplete),
  "feedback": (string, 1-2 sentence feedback. If satisfied, praise insight. If false, explain why and ask them to add more.)
}"""
    }

    private fun getThemeColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }
}
