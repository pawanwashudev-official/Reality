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

        // Load checkbox states
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        binding.cbStep1.isChecked = prefs.getBoolean("step1_enabled", true)
        binding.cbStep1Tasks.isChecked = prefs.getBoolean("step1_tasks", true)
        binding.cbStep1Calendar.isChecked = prefs.getBoolean("step1_calendar", true)
        binding.cbStep1Tapasya.isChecked = prefs.getBoolean("step1_tapasya", true)
        binding.cbStep1Distraction.isChecked = prefs.getBoolean("step1_distraction", true)
        binding.cbStep1Health.isChecked = prefs.getBoolean("step1_health", true)

        binding.cbStep2.isChecked = prefs.getBoolean("step2_enabled", true)
        binding.cbStep2AiQuestions.isChecked = prefs.getBoolean("step2_ai_questions", true)

        binding.cbStep3.isChecked = prefs.getBoolean("step3_enabled", true)
        binding.cbStep3Sheets.isChecked = prefs.getBoolean("step3_sheets", true)

        binding.cbStep4.isChecked = prefs.getBoolean("step4_enabled", true)

        binding.cbStep5.isChecked = prefs.getBoolean("step5_enabled", true)
        binding.cbStep5Alarm.isChecked = prefs.getBoolean("step5_alarm", true)
        binding.cbStep5Tasks.isChecked = prefs.getBoolean("step5_tasks", true)
        binding.cbStep5Calendar.isChecked = prefs.getBoolean("step5_calendar", true)
        binding.cbStep5Sleep.isChecked = prefs.getBoolean("step5_sleep", true)
        binding.cbStep5Distraction.isChecked = prefs.getBoolean("step5_distraction", true)

        binding.cbStep6.isChecked = prefs.getBoolean("step6_enabled", true)

        // Parent checkbox listeners to toggle children
        binding.cbStep1.setOnCheckedChangeListener { _, isChecked ->
            binding.cbStep1Tasks.isEnabled = isChecked
            binding.cbStep1Calendar.isEnabled = isChecked
            binding.cbStep1Tapasya.isEnabled = isChecked
            binding.cbStep1Distraction.isEnabled = isChecked
            binding.cbStep1Health.isEnabled = isChecked
            saveCheckboxState()
        }
        binding.cbStep2.setOnCheckedChangeListener { _, isChecked ->
            binding.cbStep2AiQuestions.isEnabled = isChecked
            saveCheckboxState()
        }
        binding.cbStep3.setOnCheckedChangeListener { _, isChecked ->
            binding.cbStep3Sheets.isEnabled = isChecked
            saveCheckboxState()
        }
        binding.cbStep4.setOnCheckedChangeListener { _, _ -> saveCheckboxState() }
        binding.cbStep5.setOnCheckedChangeListener { _, isChecked ->
            binding.cbStep5Alarm.isEnabled = isChecked
            binding.cbStep5Tasks.isEnabled = isChecked
            binding.cbStep5Calendar.isEnabled = isChecked
            binding.cbStep5Sleep.isEnabled = isChecked
            binding.cbStep5Distraction.isEnabled = isChecked
            saveCheckboxState()
        }
        binding.cbStep6.setOnCheckedChangeListener { _, _ -> saveCheckboxState() }

        // Child checkbox listeners
        val childCheckboxes = listOf(
            binding.cbStep1Tasks, binding.cbStep1Calendar, binding.cbStep1Tapasya, binding.cbStep1Distraction, binding.cbStep1Health,
            binding.cbStep2AiQuestions, binding.cbStep3Sheets,
            binding.cbStep5Alarm, binding.cbStep5Tasks, binding.cbStep5Calendar, binding.cbStep5Sleep, binding.cbStep5Distraction
        )
        childCheckboxes.forEach { cb ->
            cb.setOnCheckedChangeListener { _, _ -> saveCheckboxState() }
        }

        // Trigger initial state
        binding.cbStep1Tasks.isEnabled = binding.cbStep1.isChecked
        binding.cbStep1Calendar.isEnabled = binding.cbStep1.isChecked
        binding.cbStep1Tapasya.isEnabled = binding.cbStep1.isChecked
        binding.cbStep1Distraction.isEnabled = binding.cbStep1.isChecked
        binding.cbStep1Health.isEnabled = binding.cbStep1.isChecked
        binding.cbStep2AiQuestions.isEnabled = binding.cbStep2.isChecked
        binding.cbStep3Sheets.isEnabled = binding.cbStep3.isChecked
        binding.cbStep5Alarm.isEnabled = binding.cbStep5.isChecked
        binding.cbStep5Tasks.isEnabled = binding.cbStep5.isChecked
        binding.cbStep5Calendar.isEnabled = binding.cbStep5.isChecked
        binding.cbStep5Sleep.isEnabled = binding.cbStep5.isChecked
        binding.cbStep5Distraction.isEnabled = binding.cbStep5.isChecked


        // binding.btnBack handled by toolbar


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
            startActivity(Intent(this, NightlyPromptsActivity::class.java))
        }
    }
    
    
    
    // Day Selector Clear Dialog - Shows available days with checkboxes
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
                    java.time.LocalDate.now() // Fallback
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
    

    private fun saveCheckboxState() {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().apply {
            putBoolean("step1_enabled", binding.cbStep1.isChecked)
            putBoolean("step1_tasks", binding.cbStep1Tasks.isChecked)
            putBoolean("step1_calendar", binding.cbStep1Calendar.isChecked)
            putBoolean("step1_tapasya", binding.cbStep1Tapasya.isChecked)
            putBoolean("step1_distraction", binding.cbStep1Distraction.isChecked)
            putBoolean("step1_health", binding.cbStep1Health.isChecked)

            putBoolean("step2_enabled", binding.cbStep2.isChecked)
            putBoolean("step2_ai_questions", binding.cbStep2AiQuestions.isChecked)

            putBoolean("step3_enabled", binding.cbStep3.isChecked)
            putBoolean("step3_sheets", binding.cbStep3Sheets.isChecked)

            putBoolean("step4_enabled", binding.cbStep4.isChecked)

            putBoolean("step5_enabled", binding.cbStep5.isChecked)
            putBoolean("step5_alarm", binding.cbStep5Alarm.isChecked)
            putBoolean("step5_tasks", binding.cbStep5Tasks.isChecked)
            putBoolean("step5_calendar", binding.cbStep5Calendar.isChecked)
            putBoolean("step5_sleep", binding.cbStep5Sleep.isChecked)
            putBoolean("step5_distraction", binding.cbStep5Distraction.isChecked)

            putBoolean("step6_enabled", binding.cbStep6.isChecked)
            apply()
        }
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
        // Load time window settings first
        loadTimeWindowSettings()
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
