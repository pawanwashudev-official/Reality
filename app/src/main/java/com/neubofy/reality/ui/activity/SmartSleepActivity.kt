package com.neubofy.reality.ui.activity

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.neubofy.reality.databinding.ActivitySmartSleepBinding
import com.neubofy.reality.utils.SleepInferenceHelper
import com.neubofy.reality.utils.ThemeManager
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.neubofy.reality.health.HealthPermissionManager

class SmartSleepActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySmartSleepBinding
    private val today = LocalDate.now()
    private val sessions = mutableListOf<SleepSessionUiModel>()
    private lateinit var adapter: SleepSessionAdapter

    data class SleepSessionUiModel(
        var start: Instant,
        var end: Instant,
        val originalStart: Instant? = null,
        val originalEnd: Instant? = null,
        var isSyncing: Boolean = false
    ) {
        val isNew: Boolean get() = originalStart == null
        val isChanged: Boolean get() = start != originalStart || end != originalEnd
    }

    companion object {
        var isUnlockedThisSession = false
    }

    private val qrScannerLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            isUnlockedThisSession = true
            checkHealthPermissionsFlow()
            checkAndShowMathDismissDialog()
        } else {
            Toast.makeText(this, "Scan QR to unlock", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private val healthPermissionLauncher = HealthPermissionManager.requestPermissionsLauncher(this) { granted ->
        if (granted.containsAll(HealthPermissionManager.REQUIRED_PERMISSIONS)) {
            binding.root.visibility = View.VISIBLE
            loadSessions()
        } else {
            showHealthPermissionRequiredDialog()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        binding = ActivitySmartSleepBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup UI basics (ready for data)
        setupToolbar()
        setupRecyclerView()
        setupUI()
        
        // QR Gatekeeper: Any entry to this page requires scan
        if (!isUnlockedThisSession) {
            binding.root.visibility = View.GONE // Hide until verified
            qrScannerLauncher.launch(android.content.Intent(this, QRScannerActivity::class.java))
        } else {
            checkHealthPermissionsFlow()
            checkAndShowMathDismissDialog()
        }
    }

    private fun checkHealthPermissionsFlow() {
        lifecycleScope.launch {
            if (HealthPermissionManager.hasAllPermissions(this@SmartSleepActivity)) {
                binding.root.visibility = View.VISIBLE
                loadSessions()
            } else {
                binding.root.visibility = View.GONE
                showHealthPermissionRequiredDialog()
            }
        }
    }

    private fun showHealthPermissionRequiredDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Permissions Required")
            .setMessage("Reality needs Health Connect access to analyze your sleep patterns for the Morning Reflection.\n\nIf you've already granted permissions but still see this, please check 'System Settings' to ensure all categories are allowed.")
            .setCancelable(false)
            .setPositiveButton("Grant Access") { _, _ ->
                healthPermissionLauncher.launch(HealthPermissionManager.REQUIRED_PERMISSIONS)
            }
            .setNeutralButton("System Settings") { _, _ ->
                HealthPermissionManager.launchHealthConnectSettings(this)
            }
            .setNegativeButton("Cancel") { _, _ ->
                finish()
            }
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        isUnlockedThisSession = false // Lock again on next launch
    }

    private fun setupToolbar() {
        val toolbar = binding.includeHeader.toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Morning Reflection"
        val formatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
        supportActionBar?.subtitle = today.format(formatter)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = SleepSessionAdapter(
            onEditStart = { model -> showTimePicker(model, true) },
            onEditEnd = { model -> showTimePicker(model, false) },
            onConfirm = { model -> confirmSession(model) }
        )
        binding.rvSleepSessions.adapter = adapter
    }

    private fun setupUI() {
        binding.btnFinish.setOnClickListener { finish() }
        binding.btnManualAdd.setOnClickListener { showAddSleepPicker() }
        binding.btnSetupAlarm.setOnClickListener { showAlarmSetupDialog() }
    }

    private fun loadSessions() {
        lifecycleScope.launch {
            val healthManager = com.neubofy.reality.health.HealthManager(this@SmartSleepActivity)
            val healthSessions = healthManager.getSleepSessions(today)
            
            sessions.clear()
            if (healthSessions.isEmpty()) {
                // Run inference if no confirmed data
                val inferred = SleepInferenceHelper.inferSleepSession(this@SmartSleepActivity, today, force = true)
                if (inferred != null) {
                    sessions.add(SleepSessionUiModel(inferred.first, inferred.second))
                }
            } else {
                healthSessions.forEach { (s, e) ->
                    sessions.add(SleepSessionUiModel(s, e, originalStart = s, originalEnd = e))
                }
            }
            updateUiState()
        }
        updateAlarmUI()
    }


    private fun updateAlarmUI() {
        val loader = com.neubofy.reality.utils.SavedPreferencesLoader(this)
        val alarms = loader.loadWakeupAlarms()
        val wakeupAlarm = alarms.find { it.id == "nightly_wakeup" }

        if (wakeupAlarm != null && !wakeupAlarm.isDeleted) {
            binding.llAlarmInfo.visibility = android.view.View.VISIBLE
            binding.tvAlarmTime.text = String.format("%02d:%02d", wakeupAlarm.hour, wakeupAlarm.minute)
            binding.tvAlarmName.text = wakeupAlarm.title
            binding.swAlarmEnabled.isChecked = wakeupAlarm.isEnabled

            binding.swAlarmEnabled.setOnCheckedChangeListener { _, isChecked ->
                val updatedList = loader.loadWakeupAlarms().map {
                    if (it.id == "nightly_wakeup") it.copy(isEnabled = isChecked) else it
                }.toMutableList()
                loader.saveWakeupAlarms(updatedList)
                com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleNextAlarm(this)
            }

            // Allow editing
            binding.llAlarmInfo.setOnClickListener { showAlarmSetupDialog() }

            // Allow deletion via long click
            binding.llAlarmInfo.setOnLongClickListener {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Delete Alarm")
                    .setMessage("Are you sure you want to delete this wake up alarm?")
                    .setPositiveButton("Delete") { _, _ ->
                        val updatedList = loader.loadWakeupAlarms().map {
                            if (it.id == "nightly_wakeup") it.copy(isDeleted = true) else it
                        }.toMutableList()
                        loader.saveWakeupAlarms(updatedList)
                        com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleNextAlarm(this)
                        updateAlarmUI()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
        } else {
            binding.llAlarmInfo.visibility = android.view.View.GONE
        }
    }

    private fun showAlarmSetupDialog() {
        val dialogView = layoutInflater.inflate(com.neubofy.reality.R.layout.dialog_wakeup_alarm_setup, null)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(dialogView)

        val tvWakeTime = dialogView.findViewById<android.widget.TextView>(com.neubofy.reality.R.id.tvWakeTime)
        val etAlarmName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.neubofy.reality.R.id.etAlarmName)
        val swVibration = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(com.neubofy.reality.R.id.swVibration)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.neubofy.reality.R.id.btnCancel)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.neubofy.reality.R.id.btnSave)

        val loader = com.neubofy.reality.utils.SavedPreferencesLoader(this)
        val existingAlarm = loader.loadWakeupAlarms().find { it.id == "nightly_wakeup" }

        var selectedHour = existingAlarm?.hour ?: 7
        var selectedMinute = existingAlarm?.minute ?: 0

        tvWakeTime.text = String.format("%02d:%02d", selectedHour, selectedMinute)
        etAlarmName.setText(existingAlarm?.title ?: "Wake Up")
        swVibration.isChecked = existingAlarm?.vibrationEnabled ?: true

        tvWakeTime.setOnClickListener {
            val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
                .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_24H)
                .setHour(selectedHour)
                .setMinute(selectedMinute)
                .setTitleText("Select Wake Up Time")
                .build()

            picker.addOnPositiveButtonClickListener {
                selectedHour = picker.hour
                selectedMinute = picker.minute
                tvWakeTime.text = String.format("%02d:%02d", selectedHour, selectedMinute)
            }
            picker.show(supportFragmentManager, "alarm_time_picker")
        }

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnSave.setOnClickListener {
            val title = etAlarmName.text.toString().ifEmpty { "Wake Up" }
            val alarms = loader.loadWakeupAlarms()
            alarms.removeAll { it.id == "nightly_wakeup" }
            alarms.add(com.neubofy.reality.data.model.WakeupAlarm(
                id = "nightly_wakeup",
                title = title,
                hour = selectedHour,
                minute = selectedMinute,
                isEnabled = true,
                repeatDays = emptyList(),
                vibrationEnabled = swVibration.isChecked,
                snoozeIntervalMins = 3,
                maxAttempts = 5,
                isDeleted = false
            ))
            loader.saveWakeupAlarms(alarms)
            com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleNextAlarm(this)
            updateAlarmUI()
            dialog.dismiss()
        }

        dialog.show()
    }


    private fun checkAndShowMathDismissDialog() {
        val action = intent.getStringExtra("action")
        if (action == "wakeup_alarm") {
            val alarmId = intent.getStringExtra("id")
            showMathDismissDialog(alarmId)
        }
    }

    private fun showMathDismissDialog(alarmId: String?) {
        val dialogView = layoutInflater.inflate(com.neubofy.reality.R.layout.dialog_math_alarm_dismiss, null)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setCancelable(false)
        dialog.setContentView(dialogView)

        val tvMathProblem = dialogView.findViewById<android.widget.TextView>(com.neubofy.reality.R.id.tvMathProblem)
        val etMathAnswer = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.neubofy.reality.R.id.etMathAnswer)
        val tvError = dialogView.findViewById<android.widget.TextView>(com.neubofy.reality.R.id.tvError)
        val btnSnooze = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.neubofy.reality.R.id.btnSnooze)
        val btnDismiss = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.neubofy.reality.R.id.btnDismiss)

        // Generate math problem
        var a = (11..99).random() / 10.0
        var b = (11..99).random() / 10.0

        // Ensure max 2 or 3 decimals total in product to not be too complex
        if ((0..1).random() == 1) {
            a = (11..99).random() / 100.0
        }

        val expectedAnswer = Math.round(a * b * 1000.0) / 1000.0
        tvMathProblem.text = "${a} × ${b} = ?"

        btnSnooze.setOnClickListener {
            // Auto snooze based on loader configuration
            val loader = com.neubofy.reality.utils.SavedPreferencesLoader(this)
            val alarm = loader.loadWakeupAlarms().find { it.id == alarmId }
            val interval = alarm?.snoozeIntervalMins ?: 3
            val maxAttempts = alarm?.maxAttempts ?: 5

            com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleSnooze(this, alarmId ?: "nightly_wakeup", alarm?.title ?: "Wake Up", maxAttempts, interval)

            // Stop Service
            val stopIntent = android.content.Intent(this, com.neubofy.reality.services.WakeupAlarmService::class.java).apply {
                this.action = "STOP"
            }
            startService(stopIntent)

            dialog.dismiss()
            finish()
        }

        btnDismiss.setOnClickListener {
            val userAnswerStr = etMathAnswer.text.toString()
            if (userAnswerStr.isEmpty()) {
                tvError.visibility = android.view.View.VISIBLE
                tvError.text = "Please enter an answer"
                return@setOnClickListener
            }

            try {
                val userAnswer = userAnswerStr.toDouble()
                if (Math.abs(userAnswer - expectedAnswer) < 0.001) {
                    // Correct!
                    tvError.visibility = android.view.View.GONE

                    // Stop Service
                    val stopIntent = android.content.Intent(this, com.neubofy.reality.services.WakeupAlarmService::class.java).apply {
                        this.action = "STOP"
                    }
                    startService(stopIntent)

                    // Calculate start time based on current end time and user confirmation
                    // (Assuming inference algorithm is robust or standard sleep tracking happens via sleep verification)
                    // Launch SleepInferenceHelper to auto log
                    lifecycleScope.launch {
                        com.neubofy.reality.utils.SleepInferenceHelper.autoConfirmSleep(this@SmartSleepActivity)
                        loadSessions() // Refresh UI
                    }

                    dialog.dismiss()
                } else {
                    tvError.visibility = android.view.View.VISIBLE
                    tvError.text = "Incorrect answer, try again."
                }
            } catch (e: Exception) {
                tvError.visibility = android.view.View.VISIBLE
                tvError.text = "Invalid number format."
            }
        }

        dialog.show()
    }

    private fun updateUiState() {
        binding.cardEmptyState.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
        adapter.submitList(sessions.toList())
    }

    private fun showTimePicker(model: SleepSessionUiModel, isStart: Boolean) {
        val instant = if (isStart) model.start else model.end
        val zdt = instant.atZone(ZoneId.systemDefault())
        
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(zdt.hour)
            .setMinute(zdt.minute)
            .setTitleText(if (isStart) "Slept at" else "Woke up at")
            .build()

        picker.addOnPositiveButtonClickListener {
            val h = picker.hour
            val m = picker.minute
            val baseDate = if (isStart && h > 18) today.minusDays(1) else today
            val newInstant = baseDate.atTime(h, m).atZone(ZoneId.systemDefault()).toInstant()
            
            if (isStart) model.start = newInstant else model.end = newInstant
            adapter.notifyItemChanged(sessions.indexOf(model))
        }
        picker.show(supportFragmentManager, "time_picker")
    }

    private fun showAddSleepPicker() {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setTitleText("New Sleep Start")
            .setHour(23).setMinute(0).build()
            
        picker.addOnPositiveButtonClickListener {
            showAddWakePicker(picker.hour, picker.minute)
        }
        picker.show(supportFragmentManager, "add_start")
    }

    private fun showAddWakePicker(sh: Int, sm: Int) {
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setTitleText("New Wake Time")
            .setHour(7).setMinute(0).build()
            
        picker.addOnPositiveButtonClickListener {
            processNewManualEntry(sh, sm, picker.hour, picker.minute)
        }
        picker.show(supportFragmentManager, "add_end")
    }

    private fun processNewManualEntry(sh: Int, sm: Int, eh: Int, em: Int) {
        val startDate = if (sh > 14) today.minusDays(1) else today
        var startI = startDate.atTime(sh, sm).atZone(ZoneId.systemDefault()).toInstant()
        var endI = startDate.atTime(eh, em).atZone(ZoneId.systemDefault()).toInstant()
        if (endI.isBefore(startI)) endI = endI.plus(Duration.ofDays(1))

        lifecycleScope.launch {
            val suggestion = SleepInferenceHelper.refineSleepWindow(this@SmartSleepActivity, startI, endI)
            val finalStart = suggestion?.first ?: startI
            val finalEnd = suggestion?.second ?: endI
            
            val newModel = SleepSessionUiModel(finalStart, finalEnd)
            sessions.add(newModel)
            updateUiState()
        }
        updateAlarmUI()
    }

    private fun confirmSession(model: SleepSessionUiModel) {
        lifecycleScope.launch {
            val healthManager = com.neubofy.reality.health.HealthManager(this@SmartSleepActivity)
            
            // 1. OVERLAP CHECK (Only for new entries or changed times)
            if (model.isChanged) {
                // If it's a NEW entry, detect ANY overlap
                // If it's an UPDATE, exclude the original session from search
                val overlaps = healthManager.findOverlappingSessions(model.start, model.end, model.originalStart)
                
                if (overlaps.isNotEmpty()) {
                    val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
                    val conf = overlaps.first()
                    MaterialAlertDialogBuilder(this@SmartSleepActivity)
                        .setTitle("⚠️ Overlap Detected")
                        .setMessage("This period overlaps with an existing session: ${formatter.format(conf.first)} - ${formatter.format(conf.second)}.\n\nPlease adjust the time.")
                        .setPositiveButton("OK", null)
                        .show()
                    return@launch
                }
            }

            model.isSyncing = true
            adapter.notifyItemChanged(sessions.indexOf(model))
            
            try {
                // UPDATING logic: Delete old, write new
                if (!model.isNew) {
                    healthManager.deleteSleepSessions(model.originalStart!!, model.originalEnd!!)
                }
                healthManager.writeSleepSession(model.start, model.end)
                Toast.makeText(this@SmartSleepActivity, "Sleep Synced!", Toast.LENGTH_SHORT).show()
                loadSessions() // Reload to refresh states
            } catch (e: Exception) {
                Toast.makeText(this@SmartSleepActivity, "Sync Failed: ${e.message}", Toast.LENGTH_LONG).show()
                model.isSyncing = false
                adapter.notifyItemChanged(sessions.indexOf(model))
            }
        }
    }

    // --- ADAPTER ---
    private inner class SleepSessionAdapter(
        private val onEditStart: (SleepSessionUiModel) -> Unit,
        private val onEditEnd: (SleepSessionUiModel) -> Unit,
        private val onConfirm: (SleepSessionUiModel) -> Unit
    ) : androidx.recyclerview.widget.ListAdapter<SleepSessionUiModel, SleepSessionAdapter.ViewHolder>(DiffCallback()) {

        inner class ViewHolder(val bindingItem: com.neubofy.reality.databinding.ItemSmartSleepCardBinding) : androidx.recyclerview.widget.RecyclerView.ViewHolder(bindingItem.root)

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val b = com.neubofy.reality.databinding.ItemSmartSleepCardBinding.inflate(layoutInflater, parent, false)
            return ViewHolder(b)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val model = getItem(position)
            val b = holder.bindingItem
            val fmt = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
            val z = ZoneId.systemDefault()

            b.tvCardTitle.text = if (model.isNew) "New Session" else "Recorded Sleep"
            b.tvCardSleepTime.text = model.start.atZone(z).format(fmt)
            b.tvCardWakeTime.text = model.end.atZone(z).format(fmt)
            
            val d = Duration.between(model.start, model.end)
            b.tvCardDuration.text = "Total Sleep: ${d.toHours()}h ${d.toMinutes() % 60}m"
            
            b.btnCardSleepTime.setOnClickListener { onEditStart(model) }
            b.btnCardWakeTime.setOnClickListener { onEditEnd(model) }
            
            b.btnCardConfirm.isEnabled = !model.isSyncing && (model.isNew || model.isChanged)
            b.btnCardConfirm.text = when {
                model.isSyncing -> "Syncing..."
                model.isNew -> "Add Record"
                !model.isChanged -> "Synced"
                else -> "Update"
            }
            b.btnCardConfirm.setOnClickListener { onConfirm(model) }
        }
    }

    private class DiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<SleepSessionUiModel>() {
        override fun areItemsTheSame(oldItem: SleepSessionUiModel, newItem: SleepSessionUiModel) = oldItem.originalStart == newItem.originalStart
        override fun areContentsTheSame(oldItem: SleepSessionUiModel, newItem: SleepSessionUiModel) = oldItem == newItem
    }
}
