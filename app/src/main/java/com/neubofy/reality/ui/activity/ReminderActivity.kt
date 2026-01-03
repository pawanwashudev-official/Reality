package com.neubofy.reality.ui.activity

import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.R
import com.neubofy.reality.databinding.ActivityReminderBinding
import com.neubofy.reality.utils.SavedPreferencesLoader
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReminderActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReminderBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var customAdapter: com.neubofy.reality.ui.adapter.CustomReminderAdapter
    private val savedPreferencesLoader by lazy { SavedPreferencesLoader(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReminderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefs = getSharedPreferences("reality_prefs", Context.MODE_PRIVATE)

        setupHeader()
        setupRecyclerView()
        setupListeners()
        loadSettings()
        
        if (intent.getBooleanExtra("OPEN_SETTINGS", false)) {
            binding.root.post { showSettingsDialog() }
        }
    }
    
    override fun onResume() {
        super.onResume()
        loadCustomReminders()
    }
    
    private fun setupHeader() {
        binding.btnBack.setOnClickListener { finish() }
        
        binding.btnRecycle.setOnClickListener {
            showRecycleBinDialog()
        }
        
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }
    }
    
    private fun setupRecyclerView() {
        binding.rvReminders.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        customAdapter = com.neubofy.reality.ui.adapter.CustomReminderAdapter(
            mutableListOf(),
            onEdit = { reminder -> 
                if (!reminder.id.startsWith("synced_")) {
                    showAddReminderDialog(reminder)
                } else {
                    android.widget.Toast.makeText(this, "Synced reminders are read-only", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = { reminder ->
                if (!reminder.id.startsWith("synced_")) {
                    showDeleteConfirmation(reminder)
                } else {
                    android.widget.Toast.makeText(this, "Synced reminders cannot be deleted here", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        )
        binding.rvReminders.adapter = customAdapter
    }
    
    private fun loadSettings() {
        binding.switchMaster.isChecked = prefs.getBoolean("reminders_global_enabled", true)
        updateStatusText()
    }
    
    private fun setupListeners() {
        // Master toggle
        binding.switchMaster.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("reminders_global_enabled", isChecked).apply()
            updateStatusText()
            if (isChecked) {
                com.neubofy.reality.utils.AlarmScheduler.scheduleNextAlarm(this)
            }
        }
        
        // FAB for adding new reminder
        binding.fabAdd.setOnClickListener {
            showAddReminderDialog(null)
        }
    }
    
    private fun updateStatusText() {
        if (binding.switchMaster.isChecked) {
            binding.tvStatus.text = "Active"
            binding.tvStatus.setTextColor(getColor(R.color.teal_200))
        } else {
            binding.tvStatus.text = "Disabled"
            binding.tvStatus.setTextColor(getColor(android.R.color.holo_red_light))
        }
    }
    
    private fun loadCustomReminders() {
        val customReminders = savedPreferencesLoader.loadCustomReminders()
        
        CoroutineScope(Dispatchers.Main).launch {
            val syncedEvents = withContext(Dispatchers.IO) {
                com.neubofy.reality.data.ScheduleManager.getUnifiedEventsForToday(this@ReminderActivity)
            }
            
            val allReminders = customReminders.toMutableList()
            
            for (event in syncedEvents) {
                if (event.source != com.neubofy.reality.data.EventSource.CUSTOM_REMINDER) {
                    val startMins = event.startTimeMins
                    val offset = event.customOffsetMins ?: prefs.getInt("reminder_offset_minutes", 1)
                    val triggerMins = startMins - offset
                    
                    val sourceLabel = when(event.source) {
                        com.neubofy.reality.data.EventSource.MANUAL -> "📅"
                        com.neubofy.reality.data.EventSource.CALENDAR -> "📆"
                        else -> "⏰"
                    }
                    
                    val virtualReminder = com.neubofy.reality.data.CustomReminder(
                        id = "synced_${event.originalId}",
                        title = "$sourceLabel ${event.title}",
                        hour = (triggerMins / 60).coerceIn(0, 23),
                        minute = (triggerMins % 60).coerceIn(0, 59),
                        isEnabled = event.isEnabled,
                        repeatDays = emptyList(),
                        retryIntervalMins = 0,
                        offsetMins = offset,
                        redirectUrl = event.url
                    )
                    allReminders.add(virtualReminder)
                }
            }
            
            val sorted = allReminders.sortedBy { it.hour * 60 + it.minute }
            customAdapter.updateList(sorted)
            
            // Show/hide empty state
            if (sorted.isEmpty()) {
                binding.emptyState.visibility = View.VISIBLE
                binding.rvReminders.visibility = View.GONE
            } else {
                binding.emptyState.visibility = View.GONE
                binding.rvReminders.visibility = View.VISIBLE
            }
        }
    }
    
    private fun showAddReminderDialog(existingReminder: com.neubofy.reality.data.CustomReminder? = null) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_custom_reminder, null)
        val etTitle = dialogView.findViewById<EditText>(R.id.et_title)
        val timePicker = dialogView.findViewById<android.widget.TimePicker>(R.id.timePicker)
        val etUrl = dialogView.findViewById<EditText>(R.id.et_url)
        val etRetry = dialogView.findViewById<EditText>(R.id.et_retry)
        val etOffset = dialogView.findViewById<EditText>(R.id.et_offset)
        val spinnerUrlSource = dialogView.findViewById<android.widget.Spinner>(R.id.spinner_url_source)
        val layoutCustomUrl = dialogView.findViewById<View>(R.id.layout_custom_url)
        
        val chips = mapOf(
            java.util.Calendar.SUNDAY to R.id.chip_sun,
            java.util.Calendar.MONDAY to R.id.chip_mon,
            java.util.Calendar.TUESDAY to R.id.chip_tue,
            java.util.Calendar.WEDNESDAY to R.id.chip_wed,
            java.util.Calendar.THURSDAY to R.id.chip_thu,
            java.util.Calendar.FRIDAY to R.id.chip_fri,
            java.util.Calendar.SATURDAY to R.id.chip_sat
        )
        
        timePicker.setIs24HourView(true)
        
        spinnerUrlSource.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                layoutCustomUrl.visibility = if (position == 2) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        if (existingReminder != null) {
            etTitle.setText(existingReminder.title)
            timePicker.hour = existingReminder.hour
            timePicker.minute = existingReminder.minute
            etUrl.setText(existingReminder.redirectUrl ?: "")
            etRetry.setText(existingReminder.retryIntervalMins.toString())
            if (existingReminder.customOffsetMins != null) {
                etOffset.setText(existingReminder.customOffsetMins.toString())
            }
            spinnerUrlSource.setSelection(existingReminder.urlSource)
            if (existingReminder.urlSource == 2) {
                layoutCustomUrl.visibility = View.VISIBLE
            }
            for (day in existingReminder.repeatDays) {
                chips[day]?.let { id ->
                    dialogView.findViewById<com.google.android.material.chip.Chip>(id).isChecked = true
                }
            }
        } else {
            // Auto-preset for new reminder
            val now = java.util.Calendar.getInstance()
            timePicker.hour = now.get(java.util.Calendar.HOUR_OF_DAY)
            timePicker.minute = now.get(java.util.Calendar.MINUTE)
            spinnerUrlSource.setSelection(1) // Default URL
            etOffset.hint = "Default: ${prefs.getInt("reminder_offset_minutes", 1)}"
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.GlassDialog)
            .setTitle(if (existingReminder != null) "✏️ Edit Reminder" else "➕ Add Reminder")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val title = etTitle.text.toString().trim().ifEmpty { "Reminder" }
                val hour = timePicker.hour
                val minute = timePicker.minute
                val urlSourcePos = spinnerUrlSource.selectedItemPosition
                val customUrlText = etUrl.text.toString().trim()
                
                val globalOffset = prefs.getInt("reminder_offset_minutes", 1)
                val globalUrl = prefs.getString("global_study_url", null)
                val globalSnoozeEnabled = prefs.getBoolean("snooze_enabled", true)
                val globalSnoozeInterval = prefs.getInt("snooze_interval_mins", 5)
                val globalAutoSnooze = prefs.getBoolean("snooze_auto_enabled", true)
                val globalAutoSnoozeTimeout = prefs.getInt("snooze_timeout_secs", 30)
                
                val retry = etRetry.text.toString().toIntOrNull() ?: 0
                val customOffset = etOffset.text.toString().toIntOrNull()
                val snapshotOffset = customOffset ?: globalOffset
                
                // Time validation
                val now = java.util.Calendar.getInstance()
                val currentTotalMins = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
                val reminderTotalMins = hour * 60 + minute
                val reminderWithOffset = reminderTotalMins - snapshotOffset
                
                val selectedDays = mutableListOf<Int>()
                fun checkChip(calendarDay: Int, chipId: Int) {
                    if (dialogView.findViewById<com.google.android.material.chip.Chip>(chipId).isChecked) {
                        selectedDays.add(calendarDay)
                    }
                }
                checkChip(java.util.Calendar.SUNDAY, R.id.chip_sun)
                checkChip(java.util.Calendar.MONDAY, R.id.chip_mon)
                checkChip(java.util.Calendar.TUESDAY, R.id.chip_tue)
                checkChip(java.util.Calendar.WEDNESDAY, R.id.chip_wed)
                checkChip(java.util.Calendar.THURSDAY, R.id.chip_thu)
                checkChip(java.util.Calendar.FRIDAY, R.id.chip_fri)
                checkChip(java.util.Calendar.SATURDAY, R.id.chip_sat)
                
                if (selectedDays.isEmpty() && reminderWithOffset <= currentTotalMins) {
                    android.widget.Toast.makeText(this, 
                        "Invalid time: Reminder would trigger in the past",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                    return@setPositiveButton
                }
                
                val snapshotUrl: String? = when (urlSourcePos) {
                    0 -> null
                    1 -> globalUrl
                    2 -> if (customUrlText.isNotEmpty()) customUrlText else null
                    else -> null
                }
                
                val reminder = com.neubofy.reality.data.CustomReminder(
                    id = existingReminder?.id ?: java.util.UUID.randomUUID().toString(),
                    title = title,
                    hour = hour,
                    minute = minute,
                    isEnabled = true,
                    repeatDays = selectedDays,
                    retryIntervalMins = retry,
                    lastDismissedDate = existingReminder?.lastDismissedDate ?: 0L,
                    offsetMins = snapshotOffset,
                    redirectUrl = snapshotUrl,
                    snoozeEnabled = globalSnoozeEnabled,
                    snoozeIntervalMins = globalSnoozeInterval,
                    autoSnoozeEnabled = globalAutoSnooze,
                    autoSnoozeTimeoutSecs = globalAutoSnoozeTimeout
                )
                
                val list = savedPreferencesLoader.loadCustomReminders()
                list.removeAll { it.id == reminder.id }
                list.add(reminder)
                savedPreferencesLoader.saveCustomReminders(list)
                loadCustomReminders()
                com.neubofy.reality.utils.AlarmScheduler.scheduleNextAlarm(this)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showSettingsDialog() {
        val currentOffset = prefs.getInt("reminder_offset_minutes", 1)
        val currentSnooze = prefs.getInt("snooze_interval_mins", 5)
        val snoozeEnabled = prefs.getBoolean("snooze_enabled", true)
        val autoSnooze = prefs.getBoolean("snooze_auto_enabled", true)
        val loopAudio = prefs.getBoolean("reminders_loop_audio", true)
        val vibrate = prefs.getBoolean("reminders_vibrate", true)
        val currentUrl = prefs.getString("global_study_url", "") ?: ""
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_reminder_settings, null)
        
        // Initialize views
        val sliderOffset = dialogView.findViewById<com.google.android.material.slider.Slider>(R.id.slider_offset)
        val tvOffset = dialogView.findViewById<android.widget.TextView>(R.id.tv_offset_value)
        val sliderSnooze = dialogView.findViewById<com.google.android.material.slider.Slider>(R.id.slider_snooze)
        val tvSnooze = dialogView.findViewById<android.widget.TextView>(R.id.tv_snooze_value)
        val switchSnooze = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_snooze)
        val switchAutoSnooze = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_auto_snooze)
        val switchLoop = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_loop)
        val switchVibrate = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_vibrate)
        val etUrl = dialogView.findViewById<EditText>(R.id.et_default_url)
        
        sliderOffset.value = currentOffset.toFloat()
        tvOffset.text = if (currentOffset == 0) "Exact" else "$currentOffset min"
        sliderSnooze.value = currentSnooze.toFloat()
        tvSnooze.text = "$currentSnooze min"
        switchSnooze.isChecked = snoozeEnabled
        switchAutoSnooze.isChecked = autoSnooze
        switchLoop.isChecked = loopAudio
        switchVibrate.isChecked = vibrate
        etUrl.setText(currentUrl)
        
        sliderOffset.addOnChangeListener { _, value, _ ->
            tvOffset.text = if (value.toInt() == 0) "Exact" else "${value.toInt()} min"
        }
        sliderSnooze.addOnChangeListener { _, value, _ ->
            tvSnooze.text = "${value.toInt()} min"
        }
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.GlassDialog)
            .setTitle("⚙️ Reminder Settings")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                prefs.edit()
                    .putInt("reminder_offset_minutes", sliderOffset.value.toInt())
                    .putInt("snooze_interval_mins", sliderSnooze.value.toInt())
                    .putBoolean("snooze_enabled", switchSnooze.isChecked)
                    .putBoolean("snooze_auto_enabled", switchAutoSnooze.isChecked)
                    .putBoolean("reminders_loop_audio", switchLoop.isChecked)
                    .putBoolean("reminders_vibrate", switchVibrate.isChecked)
                    .putString("global_study_url", etUrl.text.toString().trim().ifEmpty { null })
                    .apply()
                android.widget.Toast.makeText(this, "Settings saved", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    // ========== RECYCLE BIN ==========
    
    private fun showDeleteConfirmation(reminder: com.neubofy.reality.data.CustomReminder) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.GlassDialog)
            .setTitle("🗑️ Delete Reminder?")
            .setMessage("\"${reminder.title}\" will be moved to recycle bin for 24 hours.")
            .setPositiveButton("Delete") { _, _ ->
                moveToRecycleBin(reminder)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun moveToRecycleBin(reminder: com.neubofy.reality.data.CustomReminder) {
        // Remove from active list
        val list = savedPreferencesLoader.loadCustomReminders()
        list.removeAll { it.id == reminder.id }
        savedPreferencesLoader.saveCustomReminders(list)
        
        // Add to recycle bin with timestamp
        val recycleBin = loadRecycleBin().toMutableList()
        recycleBin.add(DeletedReminder(reminder, System.currentTimeMillis()))
        saveRecycleBin(recycleBin)
        
        loadCustomReminders()
        com.neubofy.reality.utils.AlarmScheduler.scheduleNextAlarm(this)
        
        android.widget.Toast.makeText(this, "Moved to recycle bin", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    private fun loadRecycleBin(): List<DeletedReminder> {
        val json = prefs.getString("reminder_recycle_bin", "[]") ?: "[]"
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<DeletedReminder>>() {}.type
            com.google.gson.Gson().fromJson<List<DeletedReminder>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    private fun saveRecycleBin(list: List<DeletedReminder>) {
        // Clean up items older than 24 hours
        val cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        val filtered = list.filter { it.deletedAt > cutoff }
        prefs.edit().putString("reminder_recycle_bin", com.google.gson.Gson().toJson(filtered)).apply()
    }
    
    private fun showRecycleBinDialog() {
        val recycleBin = loadRecycleBin().filter { 
            System.currentTimeMillis() - it.deletedAt < 24 * 60 * 60 * 1000 
        }
        
        if (recycleBin.isEmpty()) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.GlassDialog)
                .setTitle("🗑️ Recycle Bin")
                .setMessage("No deleted reminders in the last 24 hours.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        
        val items = recycleBin.map { deleted ->
            val hoursAgo = (System.currentTimeMillis() - deleted.deletedAt) / (60 * 60 * 1000)
            "${deleted.reminder.title} (${hoursAgo}h ago)"
        }.toTypedArray()
        
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.GlassDialog)
            .setTitle("🗑️ Recycle Bin")
            .setItems(items) { _, which ->
                restoreFromRecycleBin(recycleBin[which])
            }
            .setNegativeButton("Close", null)
            .setNeutralButton("Empty All") { _, _ ->
                prefs.edit().putString("reminder_recycle_bin", "[]").apply()
                android.widget.Toast.makeText(this, "Recycle bin emptied", android.widget.Toast.LENGTH_SHORT).show()
            }
            .show()
    }
    
    private fun restoreFromRecycleBin(deleted: DeletedReminder) {
        // Remove from recycle bin
        val recycleBin = loadRecycleBin().toMutableList()
        recycleBin.removeAll { it.reminder.id == deleted.reminder.id }
        saveRecycleBin(recycleBin)
        
        // Add back to active list
        val list = savedPreferencesLoader.loadCustomReminders()
        list.add(deleted.reminder)
        savedPreferencesLoader.saveCustomReminders(list)
        
        loadCustomReminders()
        com.neubofy.reality.utils.AlarmScheduler.scheduleNextAlarm(this)
        
        android.widget.Toast.makeText(this, "Restored: ${deleted.reminder.title}", android.widget.Toast.LENGTH_SHORT).show()
    }
    
    data class DeletedReminder(
        val reminder: com.neubofy.reality.data.CustomReminder,
        val deletedAt: Long
    )
}
