package com.neubofy.reality.ui.activity

import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.ui.base.BaseActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.neubofy.reality.databinding.ActivityNightlySettingsBinding
import com.neubofy.reality.utils.ThemeManager
import kotlinx.coroutines.launch
import com.neubofy.reality.utils.SavedPreferencesLoader
import com.neubofy.reality.R
import com.neubofy.reality.data.repository.NightlyRepository
import com.neubofy.reality.data.NightlyProtocolExecutor

class NightlySettingsActivity : BaseActivity() {

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

        setupListeners()
        setupToolbar()
        loadSavedData()
    }

    private fun setupToolbar() {
        val toolbar = binding.includeHeader.toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Nightly Settings"
        toolbar.setNavigationOnClickListener { finish() }
    }
    
    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_nightly_settings, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return if (item.itemId == R.id.action_info) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Nightly Customization")
                .setMessage("You can customize the allowed time window for your nightly review and enable features like Auto-Alarm.\n\nTo manage AI Prompts and Document Templates, use the 'Manage AI Prompts' button below.")
                .setPositiveButton("Got it", null)
                .show()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    private fun setupListeners() {
        // Clear Nightly Memory - Day Selector Dialog
        binding.btnClearNightlyMemory.setOnClickListener {
            showDaySelectClearDialog()
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
        
        // Manage Prompts Button (Navigation to new Dashboard)
        binding.btnManagePrompts.setOnClickListener {
            startActivity(Intent(this, NightlyPromptsActivity::class.java).apply {
                putExtra("mode", "prompts")
            })
        }

        binding.btnManageTemplates.setOnClickListener {
            startActivity(Intent(this, NightlyPromptsActivity::class.java).apply {
                putExtra("mode", "templates")
            })
        }

        // Step toggles change listeners
        binding.switchStepAnalytics.setOnCheckedChangeListener { _, isChecked ->
            NightlyRepository.setStepEnabled(this, NightlyProtocolExecutor.STEP_FETCH_ANALYTICS, isChecked)
        }
        binding.switchStepDiary.setOnCheckedChangeListener { _, isChecked ->
            NightlyRepository.setStepEnabled(this, NightlyProtocolExecutor.STEP_CREATE_DIARY, isChecked)
        }
        binding.switchStepSaveAnalytics.setOnCheckedChangeListener { _, isChecked ->
            NightlyRepository.setStepEnabled(this, NightlyProtocolExecutor.STEP_SAVE_ANALYTICS, isChecked)
        }
        binding.switchStepCreatePlan.setOnCheckedChangeListener { _, isChecked ->
            NightlyRepository.setStepEnabled(this, NightlyProtocolExecutor.STEP_CREATE_PLAN, isChecked)
        }
        binding.switchStepApplyPlan.setOnCheckedChangeListener { _, isChecked ->
            NightlyRepository.setStepEnabled(this, NightlyProtocolExecutor.STEP_APPLY_PLAN, isChecked)
        }
        binding.switchStepReport.setOnCheckedChangeListener { _, isChecked ->
            NightlyRepository.setStepEnabled(this, NightlyProtocolExecutor.STEP_GENERATE_REPORT, isChecked)
        }

        // Sub-permissions change listeners
        binding.switchPermTasks.setOnCheckedChangeListener { _, isChecked ->
            NightlyRepository.setSubFeatureEnabled(this, "collect_tasks", isChecked)
        }
        binding.switchPermCalendar.setOnCheckedChangeListener { _, isChecked ->
            NightlyRepository.setSubFeatureEnabled(this, "collect_calendar", isChecked)
        }
        binding.switchPermTapasya.setOnCheckedChangeListener { _, isChecked ->
            NightlyRepository.setSubFeatureEnabled(this, "collect_tapasya", isChecked)
        }
        binding.switchPermDistraction.setOnCheckedChangeListener { _, isChecked ->
            NightlyRepository.setSubFeatureEnabled(this, "collect_distraction", isChecked)
        }
        binding.switchPermHealth.setOnCheckedChangeListener { _, isChecked ->
            NightlyRepository.setSubFeatureEnabled(this, "collect_health", isChecked)
            NightlyRepository.setSubFeatureEnabled(this, "collect_sleep", isChecked)
        }
        binding.switchPermAiQuestions.setOnCheckedChangeListener { _, isChecked ->
            NightlyRepository.setSubFeatureEnabled(this, "include_ai_questions", isChecked)
        }
        binding.switchPermSaveSheet.setOnCheckedChangeListener { _, isChecked ->
            NightlyRepository.setSubFeatureEnabled(this, "save_to_reality_sheet", isChecked)
        }
        binding.switchPermApplyAlarm.setOnCheckedChangeListener { _, isChecked ->
            NightlyRepository.setSubFeatureEnabled(this, "apply_alarm", isChecked)
        }
        binding.switchPermApplyTasks.setOnCheckedChangeListener { _, isChecked ->
            NightlyRepository.setSubFeatureEnabled(this, "apply_tasks", isChecked)
        }
        binding.switchPermApplyCalendar.setOnCheckedChangeListener { _, isChecked ->
            NightlyRepository.setSubFeatureEnabled(this, "apply_calendar_events", isChecked)
        }
        binding.switchPermApplySleep.setOnCheckedChangeListener { _, isChecked ->
            NightlyRepository.setSubFeatureEnabled(this, "apply_sleep_time", isChecked)
        }
        binding.switchPermApplyDistraction.setOnCheckedChangeListener { _, isChecked ->
            NightlyRepository.setSubFeatureEnabled(this, "apply_distraction_limit", isChecked)
        }
    }
    
    private fun showDaySelectClearDialog() {
        lifecycleScope.launch {
            val sessions = com.neubofy.reality.data.repository.NightlyRepository.getAllSessions(this@NightlySettingsActivity)
            
            if (sessions.isEmpty()) {
                Toast.makeText(this@NightlySettingsActivity, "No saved data found", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val savedDates = sessions.map { 
                try {
                    java.time.LocalDate.parse(it.date) 
                } catch (e: Exception) { 
                    java.time.LocalDate.now()
                }
            }
            
            val dateLabels = savedDates.map { date ->
                val formatter = java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d")
                date.format(formatter)
            }.toTypedArray()
            
            val checkedItems = BooleanArray(savedDates.size) { false }
            
            MaterialAlertDialogBuilder(this@NightlySettingsActivity)
                .setTitle("Select Days to Clear (DB)")
                .setMultiChoiceItems(dateLabels, checkedItems) { _, which, isChecked ->
                    checkedItems[which] = isChecked
                }
                .setPositiveButton("Delete Selected") { _, _ ->
                    val selectedIndices = checkedItems.indices.filter { checkedItems[it] }
                    if (selectedIndices.isEmpty()) {
                        Toast.makeText(this@NightlySettingsActivity, "No days selected", Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    
                    lifecycleScope.launch {
                        for (index in selectedIndices) {
                            val date = savedDates[index]
                            com.neubofy.reality.data.repository.NightlyRepository.clearSession(this@NightlySettingsActivity, date)
                        }
                        Toast.makeText(this@NightlySettingsActivity, "${selectedIndices.size} day(s) cleared", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNeutralButton("Clear All") { _, _ ->
                    lifecycleScope.launch {
                         savedDates.forEach { date ->
                             com.neubofy.reality.data.repository.NightlyRepository.clearSession(this@NightlySettingsActivity, date)
                         }
                        Toast.makeText(this@NightlySettingsActivity, "All Nightly Memory Cleared", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }
    
    private fun saveTimeWindowSettings() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
            .putInt("nightly_start_time", startTimeMinutes)
            .putInt("nightly_end_time", endTimeMinutes)
            .putBoolean("schedule_saved", true)
            .apply()
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
            binding.cardSavedSchedule.visibility = View.VISIBLE
            binding.cardScheduleForm.visibility = View.GONE
            binding.btnEditSchedule.visibility = View.VISIBLE
            binding.tvSavedSchedule.text = "${formatTime(startTimeMinutes)} → ${formatTime(endTimeMinutes)}"
        } else {
            binding.cardSavedSchedule.visibility = View.GONE
            binding.cardScheduleForm.visibility = View.VISIBLE
            binding.btnEditSchedule.visibility = View.GONE
        }
    }

    private fun loadSavedData() {
        loadTimeWindowSettings()
        
        // Load step toggles
        binding.switchStepAnalytics.isChecked = NightlyRepository.isStepEnabled(this, NightlyProtocolExecutor.STEP_FETCH_ANALYTICS)
        binding.switchStepDiary.isChecked = NightlyRepository.isStepEnabled(this, NightlyProtocolExecutor.STEP_CREATE_DIARY)
        binding.switchStepSaveAnalytics.isChecked = NightlyRepository.isStepEnabled(this, NightlyProtocolExecutor.STEP_SAVE_ANALYTICS)
        binding.switchStepCreatePlan.isChecked = NightlyRepository.isStepEnabled(this, NightlyProtocolExecutor.STEP_CREATE_PLAN)
        binding.switchStepApplyPlan.isChecked = NightlyRepository.isStepEnabled(this, NightlyProtocolExecutor.STEP_APPLY_PLAN)
        binding.switchStepReport.isChecked = NightlyRepository.isStepEnabled(this, NightlyProtocolExecutor.STEP_GENERATE_REPORT)

        // Load sub-permissions
        binding.switchPermTasks.isChecked = NightlyRepository.isSubFeatureEnabled(this, "collect_tasks")
        binding.switchPermCalendar.isChecked = NightlyRepository.isSubFeatureEnabled(this, "collect_calendar")
        binding.switchPermTapasya.isChecked = NightlyRepository.isSubFeatureEnabled(this, "collect_tapasya")
        binding.switchPermDistraction.isChecked = NightlyRepository.isSubFeatureEnabled(this, "collect_distraction")
        binding.switchPermHealth.isChecked = NightlyRepository.isSubFeatureEnabled(this, "collect_health")
        binding.switchPermAiQuestions.isChecked = NightlyRepository.isSubFeatureEnabled(this, "include_ai_questions")
        binding.switchPermSaveSheet.isChecked = NightlyRepository.isSubFeatureEnabled(this, "save_to_reality_sheet")
        binding.switchPermApplyAlarm.isChecked = NightlyRepository.isSubFeatureEnabled(this, "apply_alarm")
        binding.switchPermApplyTasks.isChecked = NightlyRepository.isSubFeatureEnabled(this, "apply_tasks")
        binding.switchPermApplyCalendar.isChecked = NightlyRepository.isSubFeatureEnabled(this, "apply_calendar_events")
        binding.switchPermApplySleep.isChecked = NightlyRepository.isSubFeatureEnabled(this, "apply_sleep_time")
        binding.switchPermApplyDistraction.isChecked = NightlyRepository.isSubFeatureEnabled(this, "apply_distraction_limit")
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
}
