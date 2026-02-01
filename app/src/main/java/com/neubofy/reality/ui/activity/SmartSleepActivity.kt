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

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySmartSleepBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupUI()
        loadSessions()
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
