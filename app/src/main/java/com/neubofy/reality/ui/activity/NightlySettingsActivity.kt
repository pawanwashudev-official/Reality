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
        // binding.btnBack handled by toolbar

        setupToggleListeners()


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

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        binding.switchStep1Enable.isChecked = prefs.getBoolean("step1_enable", true)
        binding.cbStep1Task.isChecked = prefs.getBoolean("step1_task", true)
        binding.cbStep1Calendar.isChecked = prefs.getBoolean("step1_calendar", true)
        binding.cbStep1Tapasya.isChecked = prefs.getBoolean("step1_tapasya", true)
        binding.cbStep1Apps.isChecked = prefs.getBoolean("step1_apps", true)
        binding.cbStep1Health.isChecked = prefs.getBoolean("step1_health", true)
        binding.switchStep2Enable.isChecked = prefs.getBoolean("step2_enable", true)
        binding.cbStep2AiQuestions.isChecked = prefs.getBoolean("step2_ai_questions", true)
        binding.switchStep3Enable.isChecked = prefs.getBoolean("step3_enable", true)
        binding.cbStep3Sheet.isChecked = prefs.getBoolean("step3_sheet", true)
        binding.switchStep4Enable.isChecked = prefs.getBoolean("step4_enable", true)
        binding.switchStep5Enable.isChecked = prefs.getBoolean("step5_enable", true)
        binding.cbStep5Alarm.isChecked = prefs.getBoolean("step5_alarm", true)
        binding.cbStep5Task.isChecked = prefs.getBoolean("step5_task", true)
        binding.cbStep5Event.isChecked = prefs.getBoolean("step5_event", true)
        binding.cbStep5Sleep.isChecked = prefs.getBoolean("step5_sleep", true)
        binding.cbStep5Limit.isChecked = prefs.getBoolean("step5_limit", true)
        binding.switchStep6Enable.isChecked = prefs.getBoolean("step6_enable", true)
    }
    private fun setupToggleListeners() {
        val editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
        binding.switchStep1Enable.setOnCheckedChangeListener { _, isChecked -> editor.putBoolean("step1_enable", isChecked).apply() }
        binding.cbStep1Task.setOnCheckedChangeListener { _, isChecked -> editor.putBoolean("step1_task", isChecked).apply() }
        binding.cbStep1Calendar.setOnCheckedChangeListener { _, isChecked -> editor.putBoolean("step1_calendar", isChecked).apply() }
        binding.cbStep1Tapasya.setOnCheckedChangeListener { _, isChecked -> editor.putBoolean("step1_tapasya", isChecked).apply() }
        binding.cbStep1Apps.setOnCheckedChangeListener { _, isChecked -> editor.putBoolean("step1_apps", isChecked).apply() }
        binding.cbStep1Health.setOnCheckedChangeListener { _, isChecked -> editor.putBoolean("step1_health", isChecked).apply() }
        binding.switchStep2Enable.setOnCheckedChangeListener { _, isChecked -> editor.putBoolean("step2_enable", isChecked).apply() }
        binding.cbStep2AiQuestions.setOnCheckedChangeListener { _, isChecked -> editor.putBoolean("step2_ai_questions", isChecked).apply() }
        binding.switchStep3Enable.setOnCheckedChangeListener { _, isChecked -> editor.putBoolean("step3_enable", isChecked).apply() }
        binding.cbStep3Sheet.setOnCheckedChangeListener { _, isChecked -> editor.putBoolean("step3_sheet", isChecked).apply() }
        binding.switchStep4Enable.setOnCheckedChangeListener { _, isChecked -> editor.putBoolean("step4_enable", isChecked).apply() }
        binding.switchStep5Enable.setOnCheckedChangeListener { _, isChecked -> editor.putBoolean("step5_enable", isChecked).apply() }
        binding.cbStep5Alarm.setOnCheckedChangeListener { _, isChecked -> editor.putBoolean("step5_alarm", isChecked).apply() }
        binding.cbStep5Task.setOnCheckedChangeListener { _, isChecked -> editor.putBoolean("step5_task", isChecked).apply() }
        binding.cbStep5Event.setOnCheckedChangeListener { _, isChecked -> editor.putBoolean("step5_event", isChecked).apply() }
        binding.cbStep5Sleep.setOnCheckedChangeListener { _, isChecked -> editor.putBoolean("step5_sleep", isChecked).apply() }
        binding.cbStep5Limit.setOnCheckedChangeListener { _, isChecked -> editor.putBoolean("step5_limit", isChecked).apply() }
        binding.switchStep6Enable.setOnCheckedChangeListener { _, isChecked -> editor.putBoolean("step6_enable", isChecked).apply() }
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
