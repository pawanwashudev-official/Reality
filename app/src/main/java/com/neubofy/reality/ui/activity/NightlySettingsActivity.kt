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
import com.neubofy.reality.databinding.ActivityNightlySettingsBinding
import com.neubofy.reality.utils.ThemeManager
import kotlinx.coroutines.launch
import com.neubofy.reality.utils.SavedPreferencesLoader

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
        setupLockSettings()
        setupInfoButton()
        setupAutoAlarm()
    }

    private fun setupInfoButton() {
        binding.btnInfoNightly.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Nightly Customization")
                .setMessage("You can customize the allowed time window for your nightly review and enable features like Auto-Alarm.\n\nTo manage AI Prompts and Document Templates, use the 'Manage AI Prompts' button below.")
                .setPositiveButton("Got it", null)
                .show()
        }
    }
    
    private fun setupAutoAlarm() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        binding.switchAutoAlarm.isChecked = prefs.getBoolean("auto_set_alarm", false)
        
        binding.switchAutoAlarm.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Check permissions before enabling
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    val alarmManager = getSystemService(android.app.AlarmManager::class.java)
                    if (!alarmManager.canScheduleExactAlarms()) {
                        // Permission missing, revert toggle and explain
                        binding.switchAutoAlarm.isChecked = false
                        
                        MaterialAlertDialogBuilder(this)
                            .setTitle("Permission Required")
                            .setMessage("To automatically set alarms, Reality needs the 'Alarms & Reminders' permission.")
                            .setPositiveButton("Grant") { _, _ ->
                                val intent = Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                startActivity(intent)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                        return@setOnCheckedChangeListener
                    }
                }
            }
            // Permission OK or unchecked - save preference
            prefs.edit().putBoolean("auto_set_alarm", isChecked).apply()
        }
    }
    
    private fun setupLockSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val strictData = SavedPreferencesLoader(this).getStrictModeData()
        
        binding.switchLockStartTimeEdit.isChecked = prefs.getBoolean("lock_start_time_edit", false)
        
        if (strictData.isEnabled) {
            binding.switchLockStartTimeEdit.isEnabled = false
            binding.switchLockStartTimeEdit.alpha = 0.5f // Visual indication
        } else {
            binding.switchLockStartTimeEdit.isEnabled = true
            binding.switchLockStartTimeEdit.alpha = 1.0f
        }
        
        binding.switchLockStartTimeEdit.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("lock_start_time_edit", isChecked).apply()
        }
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
