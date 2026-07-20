package com.neubofy.reality.ui.activity

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.ui.base.BaseActivity
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
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.SleepSegmentRequest
import android.app.PendingIntent
import com.neubofy.reality.receivers.SleepReceiver

class SmartSleepActivity : BaseActivity() {

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

    private var isMathDialogShowing = false

    private var selectedRingtoneUri: String? = null

    private val ringtonePickerLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri: android.net.Uri? = result.data?.getParcelableExtra(android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            selectedRingtoneUri = uri?.toString()
            Toast.makeText(this, "Ringtone Selected", Toast.LENGTH_SHORT).show()
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

    private val activityRecognitionLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        val swGoogleSleep = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(com.neubofy.reality.R.id.swGoogleSleep)
        if (granted) {
            enableGoogleSleep()
            swGoogleSleep?.isChecked = true
        } else {
            Toast.makeText(this, "Permission required for Google Sleep Detection.", Toast.LENGTH_LONG).show()
            swGoogleSleep?.isChecked = false
            val prefs = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("google_sleep_enabled", false).apply()
        }
    }

    private fun enableGoogleSleep() {
        val prefs = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("google_sleep_enabled", true).apply()
        
        val intent = android.content.Intent(this, SleepReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        
        try {
            ActivityRecognition.getClient(this).requestSleepSegmentUpdates(
                pendingIntent, 
                SleepSegmentRequest.getDefaultSleepSegmentRequest()
            ).addOnSuccessListener {
                com.neubofy.reality.utils.TerminalLogger.log("Google Sleep API successfully registered.")
                Toast.makeText(this, "Google Sleep Detection Enabled", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener { e ->
                com.neubofy.reality.utils.TerminalLogger.log("Failed to register Google Sleep API: ${e.message}")
                Toast.makeText(this, "Failed to enable: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: SecurityException) {
            com.neubofy.reality.utils.TerminalLogger.log("SecurityException registering Sleep API: ${e.message}")
        }
    }
    
    private fun disableGoogleSleep() {
        val prefs = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("google_sleep_enabled", false).apply()
        
        val intent = android.content.Intent(this, SleepReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        
        try {
            ActivityRecognition.getClient(this).removeSleepSegmentUpdates(pendingIntent)
            com.neubofy.reality.utils.TerminalLogger.log("Google Sleep API unregistered.")
            Toast.makeText(this, "Google Sleep Detection Disabled", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            com.neubofy.reality.utils.TerminalLogger.log("Error unregistering Sleep API: ${e.message}")
            Toast.makeText(this, "Failed to disable: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        if (!com.neubofy.reality.utils.RealityProManager.checkVerification(this)) return
        
        enableEdgeToEdge()
        binding = ActivitySmartSleepBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup UI basics (ready for data)
        setupToolbar()
        setupRecyclerView()
        setupUI()
        
        // Check Alarm Permission
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                android.widget.Toast.makeText(this, "Exact Alarm Permission Required", android.widget.Toast.LENGTH_LONG).show()
                startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
        }

        checkHealthPermissionsFlow()
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
    

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun setupToolbar() {
        val toolbar = binding.includeHeader.toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Sleep & Alarm"
        val formatter = DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())
        supportActionBar?.subtitle = today.format(formatter)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = SleepSessionAdapter(
            onEditStart = { model -> showTimePicker(model, true) },
            onEditEnd = { model -> showTimePicker(model, false) },
            onConfirm = { model -> confirmSession(model) },
            onReject = { model -> rejectSession(model) }
        )
        binding.rvSleepSessions.adapter = adapter
        binding.btnViewHealth.setOnClickListener {
            startActivity(android.content.Intent(this, com.neubofy.reality.ui.activity.HealthDashboardActivity::class.java))
        }
    }

    private fun setupUI() {
        binding.btnFinish.setOnClickListener { finish() }
        binding.btnManualAdd.setOnClickListener { showAddSleepPicker() }
        
        val swGoogleSleep = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(com.neubofy.reality.R.id.swGoogleSleep)
        val prefs = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        swGoogleSleep.isChecked = prefs.getBoolean("google_sleep_enabled", false)
        
        swGoogleSleep.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
                        swGoogleSleep.isChecked = false // Revert until granted
                        activityRecognitionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    } else {
                        enableGoogleSleep()
                    }
                } else {
                    enableGoogleSleep()
                }
            } else {
                disableGoogleSleep()
            }
        }
    }

    private fun loadSessions() {
        lifecycleScope.launch {
            val healthManager = com.neubofy.reality.health.HealthManager(this@SmartSleepActivity)
            val healthSessions = healthManager.getSleepSessions(today)
            
            sessions.clear()
            if (healthSessions.isEmpty()) {
                val sleepPrefs = getSharedPreferences("reality_sleep_prefs", Context.MODE_PRIVATE)
                val googleStart = sleepPrefs.getLong("google_sleep_start", 0L)
                val googleEnd = sleepPrefs.getLong("google_sleep_end", 0L)
                
                if (googleStart > 0 && googleEnd > 0) {
                    // Use Google's highly accurate AI sleep detection
                    sessions.add(SleepSessionUiModel(Instant.ofEpochMilli(googleStart), Instant.ofEpochMilli(googleEnd)))
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



    private fun setupAlarmRecycler() {
        val loader = com.neubofy.reality.utils.SavedPreferencesLoader(this)
        val alarms = loader.loadWakeupAlarms().filter { !it.isDeleted }
        val rvWakeupAlarms = findViewById<androidx.recyclerview.widget.RecyclerView>(com.neubofy.reality.R.id.rvWakeupAlarms)

        rvWakeupAlarms.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        rvWakeupAlarms.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val view = layoutInflater.inflate(com.neubofy.reality.R.layout.item_wakeup_alarm, parent, false)
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {}
            }

            override fun getItemCount(): Int = alarms.size

            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val alarm = alarms[position]
                val view = holder.itemView
                val tvAlarmTime = view.findViewById<android.widget.TextView>(com.neubofy.reality.R.id.tvAlarmTime)
                val tvAlarmName = view.findViewById<android.widget.TextView>(com.neubofy.reality.R.id.tvAlarmName)
                val swAlarmEnabled = view.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(com.neubofy.reality.R.id.swAlarmEnabled)
                val btnSettings = view.findViewById<android.widget.ImageButton>(com.neubofy.reality.R.id.btnSettings)

                tvAlarmTime.text = String.format("%02d:%02d", alarm.hour, alarm.minute)
                tvAlarmName.text = alarm.title
                swAlarmEnabled.isChecked = alarm.isEnabled

                swAlarmEnabled.setOnCheckedChangeListener { _, isChecked ->
                    val allAlarms = loader.loadWakeupAlarms()
                    val idx = allAlarms.indexOfFirst { it.id == alarm.id }
                    if (idx != -1) {
                        allAlarms[idx] = allAlarms[idx].copy(isEnabled = isChecked)
                        loader.saveWakeupAlarms(allAlarms)
                        com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleNextAlarm(this@SmartSleepActivity)
                    }
                }

                btnSettings.setOnClickListener { showAlarmSetupDialog(alarm.id) }

                view.setOnLongClickListener {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this@SmartSleepActivity)
                        .setTitle("Delete Alarm")
                        .setMessage("Move this alarm to the Recycle Bin?")
                        .setPositiveButton("Delete") { _, _ ->
                            val allAlarms = loader.loadWakeupAlarms()
                            val idx = allAlarms.indexOfFirst { it.id == alarm.id }
                            if (idx != -1) {
                                allAlarms[idx] = allAlarms[idx].copy(isDeleted = true)
                                loader.saveWakeupAlarms(allAlarms)
                                com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleNextAlarm(this@SmartSleepActivity)
                                updateAlarmUI()
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    true
                }
            }
        }
    }

    private fun showRecycleBinDialog() {
        val loader = com.neubofy.reality.utils.SavedPreferencesLoader(this)
        val deletedAlarms = loader.loadWakeupAlarms().filter { it.isDeleted }

        if (deletedAlarms.isEmpty()) {
            Toast.makeText(this, "Recycle Bin is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val rv = androidx.recyclerview.widget.RecyclerView(this)
        rv.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Recycle Bin")
            .setView(rv)
            .setPositiveButton("Close", null)
            .create()

        rv.adapter = object : androidx.recyclerview.widget.RecyclerView.Adapter<androidx.recyclerview.widget.RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): androidx.recyclerview.widget.RecyclerView.ViewHolder {
                val view = layoutInflater.inflate(com.neubofy.reality.R.layout.item_recycled_alarm, parent, false)
                return object : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {}
            }

            override fun getItemCount(): Int = deletedAlarms.size

            override fun onBindViewHolder(holder: androidx.recyclerview.widget.RecyclerView.ViewHolder, position: Int) {
                val alarm = deletedAlarms[position]
                val view = holder.itemView
                val tvAlarmTime = view.findViewById<android.widget.TextView>(com.neubofy.reality.R.id.tvAlarmTime)
                val tvAlarmName = view.findViewById<android.widget.TextView>(com.neubofy.reality.R.id.tvAlarmName)
                val btnRestore = view.findViewById<android.widget.ImageButton>(com.neubofy.reality.R.id.btnRestore)
                val btnDeletePermanent = view.findViewById<android.widget.ImageButton>(com.neubofy.reality.R.id.btnDeletePermanent)

                tvAlarmTime.text = String.format("%02d:%02d", alarm.hour, alarm.minute)
                tvAlarmName.text = alarm.title

                btnRestore.setOnClickListener {
                    val allAlarms = loader.loadWakeupAlarms()
                    val idx = allAlarms.indexOfFirst { it.id == alarm.id }
                    if (idx != -1) {
                        allAlarms[idx] = allAlarms[idx].copy(isDeleted = false)
                        loader.saveWakeupAlarms(allAlarms)
                        com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleNextAlarm(this@SmartSleepActivity)
                        updateAlarmUI()
                        dialog.dismiss()
                        showRecycleBinDialog() // refresh
                    }
                }

                btnDeletePermanent.setOnClickListener {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this@SmartSleepActivity)
                        .setTitle("Delete Permanently")
                        .setMessage("This action cannot be undone.")
                        .setPositiveButton("Delete") { _, _ ->
                            val allAlarms = loader.loadWakeupAlarms()
                            allAlarms.removeAll { it.id == alarm.id }
                            loader.saveWakeupAlarms(allAlarms)
                            dialog.dismiss()
                            showRecycleBinDialog() // refresh
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
        }

        dialog.setOnDismissListener { isMathDialogShowing = false }
        dialog.show()
    }


    private fun showGlobalDefaultsDialog() {
        val dialogView = layoutInflater.inflate(com.neubofy.reality.R.layout.dialog_wakeup_alarm_defaults, null)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(dialogView)

        val swVibration = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(com.neubofy.reality.R.id.swVibration)
        val etSnoozeInterval = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.neubofy.reality.R.id.etSnoozeInterval)
        val etMaxAttempts = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.neubofy.reality.R.id.etMaxAttempts)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.neubofy.reality.R.id.btnCancel)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.neubofy.reality.R.id.btnSave)
        val btnSelectRingtone = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.neubofy.reality.R.id.btnSelectRingtone)

        val loader = com.neubofy.reality.utils.SavedPreferencesLoader(this)
        val defaults = loader.getWakeupAlarmDefaults()

        selectedRingtoneUri = defaults.ringtoneUri
        swVibration.isChecked = defaults.vibrationEnabled
        etSnoozeInterval.setText(defaults.snoozeIntervalMins.toString())
        etMaxAttempts.setText(defaults.maxAttempts.toString())

        btnSelectRingtone.setOnClickListener {
            val intent = android.content.Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
            intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_ALARM)
            intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)

            if (selectedRingtoneUri != null) {
                intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, android.net.Uri.parse(selectedRingtoneUri))
            } else {
                intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM))
            }

            ringtonePickerLauncher.launch(intent)
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            val snoozeStr = etSnoozeInterval.text.toString()
            val maxStr = etMaxAttempts.text.toString()
            val snoozeInterval = if (snoozeStr.isNotEmpty()) snoozeStr.toInt() else 3
            val maxAttempts = if (maxStr.isNotEmpty()) maxStr.toInt() else 5

            val newDefaults = com.neubofy.reality.Constants.WakeupAlarmDefaults(
                ringtoneUri = selectedRingtoneUri,
                vibrationEnabled = swVibration.isChecked,
                snoozeIntervalMins = snoozeInterval,
                maxAttempts = maxAttempts
            )
            loader.saveWakeupAlarmDefaults(newDefaults)
            Toast.makeText(this, "Default Settings Saved", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun updateAlarmUI() {
        val btnAddAlarm = findViewById<android.widget.ImageButton>(com.neubofy.reality.R.id.btnAddAlarm)
        val btnRecycleBin = findViewById<android.widget.ImageButton>(com.neubofy.reality.R.id.btnRecycleBin)

        btnAddAlarm?.setOnClickListener { showAlarmSetupDialog(null) }
        btnRecycleBin?.setOnClickListener { showRecycleBinDialog() }
        findViewById<android.widget.ImageButton>(com.neubofy.reality.R.id.btnAlarmDefaults)?.setOnClickListener { showGlobalDefaultsDialog() }

        setupAlarmRecycler()
    }

    private fun showAlarmSetupDialog(editAlarmId: String?) {
        val dialogView = layoutInflater.inflate(com.neubofy.reality.R.layout.dialog_wakeup_alarm_setup, null)
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        dialog.setContentView(dialogView)

        val npHour = dialogView.findViewById<android.widget.NumberPicker>(com.neubofy.reality.R.id.npHour)
        val npMinute = dialogView.findViewById<android.widget.NumberPicker>(com.neubofy.reality.R.id.npMinute)
        val etAlarmName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.neubofy.reality.R.id.etAlarmName)
        val etAlarmDescription = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.neubofy.reality.R.id.etAlarmDescription)

        val tbMon = dialogView.findViewById<android.widget.ToggleButton>(com.neubofy.reality.R.id.tbMon)
        val tbTue = dialogView.findViewById<android.widget.ToggleButton>(com.neubofy.reality.R.id.tbTue)
        val tbWed = dialogView.findViewById<android.widget.ToggleButton>(com.neubofy.reality.R.id.tbWed)
        val tbThu = dialogView.findViewById<android.widget.ToggleButton>(com.neubofy.reality.R.id.tbThu)
        val tbFri = dialogView.findViewById<android.widget.ToggleButton>(com.neubofy.reality.R.id.tbFri)
        val tbSat = dialogView.findViewById<android.widget.ToggleButton>(com.neubofy.reality.R.id.tbSat)
        val tbSun = dialogView.findViewById<android.widget.ToggleButton>(com.neubofy.reality.R.id.tbSun)

        val swVibration = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(com.neubofy.reality.R.id.swVibration)
        val etSnoozeInterval = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.neubofy.reality.R.id.etSnoozeInterval)
        val etMaxAttempts = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(com.neubofy.reality.R.id.etMaxAttempts)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.neubofy.reality.R.id.btnCancel)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.neubofy.reality.R.id.btnSave)
        val btnSelectRingtone = dialogView.findViewById<com.google.android.material.button.MaterialButton>(com.neubofy.reality.R.id.btnSelectRingtone)

        val loader = com.neubofy.reality.utils.SavedPreferencesLoader(this)
        val existingAlarm = if (editAlarmId != null) loader.loadWakeupAlarms().find { it.id == editAlarmId } else null
        val defaults = loader.getWakeupAlarmDefaults()

        npHour.minValue = 0
        npHour.maxValue = 23
        npMinute.minValue = 0
        npMinute.maxValue = 59

        var selectedHour = existingAlarm?.hour ?: 7
        var selectedMinute = existingAlarm?.minute ?: 0
        selectedRingtoneUri = existingAlarm?.ringtoneUri ?: defaults.ringtoneUri

        npHour.value = selectedHour
        npMinute.value = selectedMinute

        etAlarmName.setText(existingAlarm?.title ?: "Wake Up")
        etAlarmDescription.setText(existingAlarm?.description ?: "")

        swVibration.isChecked = existingAlarm?.vibrationEnabled ?: defaults.vibrationEnabled
        etSnoozeInterval.setText((existingAlarm?.snoozeIntervalMins ?: defaults.snoozeIntervalMins).toString())
        etMaxAttempts.setText((existingAlarm?.maxAttempts ?: defaults.maxAttempts).toString())

        val repeatDays = existingAlarm?.repeatDays ?: emptyList()
        tbMon.isChecked = repeatDays.contains(java.util.Calendar.MONDAY)
        tbTue.isChecked = repeatDays.contains(java.util.Calendar.TUESDAY)
        tbWed.isChecked = repeatDays.contains(java.util.Calendar.WEDNESDAY)
        tbThu.isChecked = repeatDays.contains(java.util.Calendar.THURSDAY)
        tbFri.isChecked = repeatDays.contains(java.util.Calendar.FRIDAY)
        tbSat.isChecked = repeatDays.contains(java.util.Calendar.SATURDAY)
        tbSun.isChecked = repeatDays.contains(java.util.Calendar.SUNDAY)

        btnSelectRingtone.setOnClickListener {
            val intent = android.content.Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER)
            intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_ALARM)
            intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)

            if (selectedRingtoneUri != null) {
                intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, android.net.Uri.parse(selectedRingtoneUri))
            } else {
                intent.putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM))
            }

            ringtonePickerLauncher.launch(intent)
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnSave.setOnClickListener {
            selectedHour = npHour.value
            selectedMinute = npMinute.value

            val title = etAlarmName.text.toString().ifEmpty { "Wake Up" }
            val description = etAlarmDescription.text.toString()
            val snoozeStr = etSnoozeInterval.text.toString()
            val maxStr = etMaxAttempts.text.toString()

            val snoozeInterval = if (snoozeStr.isNotEmpty()) snoozeStr.toInt() else 3
            val maxAttempts = if (maxStr.isNotEmpty()) maxStr.toInt() else 5

            val selectedDays = mutableListOf<Int>()
            if (tbMon.isChecked) selectedDays.add(java.util.Calendar.MONDAY)
            if (tbTue.isChecked) selectedDays.add(java.util.Calendar.TUESDAY)
            if (tbWed.isChecked) selectedDays.add(java.util.Calendar.WEDNESDAY)
            if (tbThu.isChecked) selectedDays.add(java.util.Calendar.THURSDAY)
            if (tbFri.isChecked) selectedDays.add(java.util.Calendar.FRIDAY)
            if (tbSat.isChecked) selectedDays.add(java.util.Calendar.SATURDAY)
            if (tbSun.isChecked) selectedDays.add(java.util.Calendar.SUNDAY)

            val alarms = loader.loadWakeupAlarms()
            val alarmIdToSave = editAlarmId ?: System.currentTimeMillis().toString()
            alarms.removeAll { it.id == alarmIdToSave }
            alarms.add(com.neubofy.reality.data.model.WakeupAlarm(
                id = alarmIdToSave,
                title = title,
                description = description,
                hour = selectedHour,
                minute = selectedMinute,
                isEnabled = true,
                repeatDays = selectedDays,
                ringtoneUri = selectedRingtoneUri,
                vibrationEnabled = swVibration.isChecked,
                snoozeIntervalMins = snoozeInterval,
                maxAttempts = maxAttempts,
                isDeleted = false
            ))
            loader.saveWakeupAlarms(alarms)



            com.neubofy.reality.utils.WakeupAlarmScheduler.scheduleNextAlarm(this)
            updateAlarmUI()
            dialog.dismiss()
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
            val newModel = SleepSessionUiModel(startI, endI)
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
                
                // Clear Google Sleep data so it doesn't prompt again
                if (model.isNew) {
                    val sleepPrefs = getSharedPreferences("reality_sleep_prefs", Context.MODE_PRIVATE)
                    sleepPrefs.edit().remove("google_sleep_start").remove("google_sleep_end").apply()
                }
                
                loadSessions() // Reload to refresh states
            } catch (e: Exception) {
                Toast.makeText(this@SmartSleepActivity, "Sync Failed: ${e.message}", Toast.LENGTH_LONG).show()
                model.isSyncing = false
                adapter.notifyItemChanged(sessions.indexOf(model))
            }
        }
    }

    private fun rejectSession(model: SleepSessionUiModel) {
        if (model.isNew) {
            val sleepPrefs = getSharedPreferences("reality_sleep_prefs", Context.MODE_PRIVATE)
            sleepPrefs.edit().remove("google_sleep_start").remove("google_sleep_end").apply()
            sessions.remove(model)
            updateUiState()
        }
    }

    // --- ADAPTER ---
    private inner class SleepSessionAdapter(
        private val onEditStart: (SleepSessionUiModel) -> Unit,
        private val onEditEnd: (SleepSessionUiModel) -> Unit,
        private val onConfirm: (SleepSessionUiModel) -> Unit,
        private val onReject: (SleepSessionUiModel) -> Unit
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
            
            // Allow rejection for new un-synced sessions
            if (model.isNew) {
                b.btnDelete.visibility = android.view.View.VISIBLE
                b.btnDelete.setOnClickListener { onReject(model) }
            } else {
                b.btnDelete.visibility = android.view.View.GONE
                b.btnDelete.setOnClickListener(null)
            }
        }
    }

    private class DiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<SleepSessionUiModel>() {
        override fun areItemsTheSame(oldItem: SleepSessionUiModel, newItem: SleepSessionUiModel) = oldItem.originalStart == newItem.originalStart
        override fun areContentsTheSame(oldItem: SleepSessionUiModel, newItem: SleepSessionUiModel) = oldItem == newItem
    }



}