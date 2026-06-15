package com.neubofy.reality.ui.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.ui.base.BaseActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.neubofy.reality.R
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.data.db.CalendarEvent
import com.neubofy.reality.databinding.ActivityScheduleListBinding
import com.neubofy.reality.utils.TimeTools
import java.text.SimpleDateFormat
import java.util.*
import com.neubofy.reality.Constants
import android.util.TypedValue
import com.google.android.material.card.MaterialCardView

class ScheduleListActivity : BaseActivity() {

    private lateinit var binding: ActivityScheduleListBinding
    private val displayItems = mutableListOf<ScheduleDisplayItem>()
    private val prefs by lazy { com.neubofy.reality.utils.SavedPreferencesLoader(this) }
    
    // Manual Schedule Logic
    private var manualList: MutableList<Constants.AutoTimedActionItem> = mutableListOf()
    private lateinit var dialogBinding: com.neubofy.reality.databinding.DialogAddTimedActionBinding
    
    // Sync Logic
    private val calendarPermissionLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) selectCalendars() else android.widget.Toast.makeText(this, "Permission denied", android.widget.Toast.LENGTH_SHORT).show()
    }

    // Timeline Constants
    private val HOUR_HEIGHT_DP = 60
    private var hourHeightPx = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hourHeightPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, HOUR_HEIGHT_DP.toFloat(), resources.displayMetrics).toInt()

        setupToolbar()
        setupSwipeRefresh()
        setupDateHeader()
        setupHourGrid()
        loadSchedules()
        
        if (intent.getBooleanExtra("OPEN_SETTINGS", false)) {
            binding.root.post { showSyncSettingsDialog() }
        }
    }
    
    private fun setupToolbar() {
        val toolbar = binding.includeHeader.toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Auto-Focus"
        toolbar.setNavigationOnClickListener { finish() }
        
        binding.fabAddSchedule.setOnClickListener { showAddScheduleDialog() }
    }
    
    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_schedule_list, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return if (item.itemId == R.id.action_settings) {
            showSyncSettingsDialog()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }
    
    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light
        )
        
        binding.swipeRefresh.setOnRefreshListener {
            // Check if manual sync is blocked by Strict Mode
            val strict = prefs.getStrictModeData()
            val isStrictActive = strict.isEnabled && !com.neubofy.reality.utils.StrictLockUtils.isMaintenanceWindow()
            
            if (isStrictActive && strict.isCalendarLocked) {
                android.widget.Toast.makeText(this, "🔒 Manual sync blocked by Strict Mode. Edit allowed 00:00-00:10 daily.", android.widget.Toast.LENGTH_LONG).show()
                binding.swipeRefresh.isRefreshing = false
                return@setOnRefreshListener
            }
            
            // Trigger manual calendar sync
            val isAutoSync = prefs.getBoolean("calendar_sync_auto_enabled", true)
            if (isAutoSync) {
                val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.neubofy.reality.workers.CalendarSyncWorker>().build()
                androidx.work.WorkManager.getInstance(applicationContext).enqueue(workRequest)
                android.widget.Toast.makeText(this, "📅 Syncing calendar...", android.widget.Toast.LENGTH_SHORT).show()
            }
            
            // Reload schedules after delay
            binding.swipeRefresh.postDelayed({
                loadSchedules()
                binding.swipeRefresh.isRefreshing = false
            }, 2000)
        }
    }

    private fun setupDateHeader() {
        val calendar = Calendar.getInstance()
        val dayFormat = SimpleDateFormat("EEEE", Locale.getDefault())
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
        val timezone = TimeZone.getDefault().getDisplayName(false, TimeZone.SHORT)

        binding.tvDayNumber.text = dayOfMonth.toString()
        binding.tvDayName.text = dayFormat.format(calendar.time)
        binding.tvTimezone.text = timezone
    }

    private fun setupHourGrid() {
        binding.hourGrid.removeAllViews()
        
        for (hour in 0..23) {
            val hourRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    hourHeightPx
                )
            }

            // Hour label
            val hourLabel = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 55f, resources.displayMetrics).toInt(),
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                text = when {
                    hour == 0 -> "12 AM"
                    hour < 12 -> "$hour AM"
                    hour == 12 -> "12 PM"
                    else -> "${hour - 12} PM"
                }
                setTextColor(ContextCompat.getColor(this@ScheduleListActivity, android.R.color.darker_gray))
                textSize = 12f
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
                setPadding(0, 0, 8, 0)
            }

            // Separator line
            val separator = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    1,
                    1f
                ).apply { gravity = android.view.Gravity.TOP }
                setBackgroundColor(ContextCompat.getColor(this@ScheduleListActivity, android.R.color.darker_gray))
                alpha = 0.3f
            }

            hourRow.addView(hourLabel)
            hourRow.addView(separator)
            binding.hourGrid.addView(hourRow)
        }
    }

    private fun loadSchedules() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            
            // 1. Load Custom Schedules
            val customSchedules = prefs.loadAutoFocusHoursList()
            
            // 2. Load Synced Events
            val allDbEvents = db.calendarEventDao().getAllEvents()

            // 3. Strict Mode Check
            val strict = prefs.getStrictModeData()
            val isStrictLocked = strict.isEnabled && !com.neubofy.reality.utils.StrictLockUtils.isMaintenanceWindow()
            
            withContext(Dispatchers.Main) {
                displayItems.clear()
                
                // Add Custom
                displayItems.addAll(customSchedules.map { ScheduleDisplayItem.Custom(it) })
                
                // Add Synced
                displayItems.addAll(allDbEvents.map { ScheduleDisplayItem.Synced(it) })

                // Render events on timeline
                renderEventsOnTimeline()
                
                val isEmpty = displayItems.isEmpty()
                binding.layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
                binding.scrollView.visibility = if (isEmpty) View.GONE else View.VISIBLE
            }
        }
    }

    private fun renderEventsOnTimeline() {
        binding.eventsContainer.removeAllViews()

        val nowMins = TimeTools.getCurrentTimeInMinutes()
        val nowMs = System.currentTimeMillis()

        for (item in displayItems) {
            val isCustom = item is ScheduleDisplayItem.Custom
            
            val (startMins, endMins, title, isActiveNow) = when (item) {
                is ScheduleDisplayItem.Custom -> {
                    val start = item.item.startTimeInMins
                    val end = item.item.endTimeInMins
                    val active = if (start <= end) nowMins in start..end else nowMins >= start || nowMins <= end
                    Quadruple(start, end, item.item.title, active)
                }
                is ScheduleDisplayItem.Synced -> {
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = item.event.startTime
                    val start = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                    cal.timeInMillis = item.event.endTime
                    val end = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                    val active = nowMs in item.event.startTime..item.event.endTime
                    Quadruple(start, end, item.event.title, active)
                }
            }

            // Calculate position and height
            val topPx = (startMins.toFloat() / 60f * hourHeightPx).toInt()
            val durationMins = if (endMins > startMins) endMins - startMins else (24 * 60 - startMins + endMins)
            val heightPx = (durationMins.toFloat() / 60f * hourHeightPx).toInt().coerceAtLeast(hourHeightPx / 2)

            // Color Logic: Custom = Purple, Synced = Blue, Active = Green
            val baseColor = when {
                isActiveNow -> R.color.green_500
                isCustom -> R.color.purple_500
                else -> R.color.blue_500
            }

            // Create event card
            val cardView = MaterialCardView(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    heightPx
                ).apply {
                    topMargin = topPx
                    marginEnd = 8
                }
                radius = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics)
                cardElevation = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics)
                setCardBackgroundColor(ContextCompat.getColor(context, baseColor))
                alpha = if (isActiveNow) 1.0f else 0.85f
                isClickable = true
                isFocusable = true
            }

            // Click listener for details popup
            cardView.setOnClickListener {
                showScheduleDetailsPopup(item)
            }

            // Event content
            val contentLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12, 8, 12, 8)
            }

            val titleView = TextView(this).apply {
                text = title
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                textSize = 14f
                maxLines = 2
            }

            val timeView = TextView(this).apply {
                val startStr = String.format("%02d:%02d", startMins / 60, startMins % 60)
                val endStr = String.format("%02d:%02d", endMins / 60, endMins % 60)
                text = "$startStr - $endStr"
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                textSize = 11f
                alpha = 0.8f
            }
            
            // Type indicator
            val typeView = TextView(this).apply {
                text = if (isCustom) "📅 Custom" else "📆 Synced"
                setTextColor(ContextCompat.getColor(context, android.R.color.white))
                textSize = 10f
                alpha = 0.7f
            }

            contentLayout.addView(titleView)
            contentLayout.addView(timeView)
            if (heightPx > hourHeightPx) contentLayout.addView(typeView) // Only show if enough space
            cardView.addView(contentLayout)
            binding.eventsContainer.addView(cardView)
        }

        // Scroll to current time
        val currentScrollY = (nowMins.toFloat() / 60f * hourHeightPx).toInt() - (hourHeightPx * 2)
        binding.scrollView.post {
            binding.scrollView.scrollTo(0, currentScrollY.coerceAtLeast(0))
        }
    }

    private fun showScheduleDetailsPopup(item: ScheduleDisplayItem) {
        when (item) {
            is ScheduleDisplayItem.Custom -> {
                val schedule = item.item
                val startStr = String.format("%02d:%02d", schedule.startTimeInMins / 60, schedule.startTimeInMins % 60)
                val endStr = String.format("%02d:%02d", schedule.endTimeInMins / 60, schedule.endTimeInMins % 60)
                val daysStr = if (schedule.repeatDays.size == 7) "Every Day" else "Selected Days"
                
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.GlassDialog)
                    .setTitle("📅 ${schedule.title}")
                    .setMessage("Time: $startStr - $endStr\nRepeat: $daysStr\nReminder: ${if (schedule.isReminderEnabled) "On" else "Off"}")
                    .setPositiveButton("Edit") { _, _ ->
                        showEditScheduleDialog(schedule)
                    }
                    .setNegativeButton("Delete") { _, _ ->
                        deleteManualItem(schedule)
                    }
                    .setNeutralButton("Close", null)
                    .show()
            }
            is ScheduleDisplayItem.Synced -> {
                val event = item.event
                val dateFormat = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault())
                
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.GlassDialog)
                    .setTitle("📆 ${event.title}")
                    .setMessage("Start: ${dateFormat.format(java.util.Date(event.startTime))}\nEnd: ${dateFormat.format(java.util.Date(event.endTime))}\nStatus: ${if (event.isEnabled) "Blocking Enabled" else "Disabled"}")
                    .setPositiveButton(if (event.isEnabled) "Disable" else "Enable") { _, _ ->
                        toggleSyncedEvent(event)
                    }
                    .setNegativeButton("Close", null)
                    .show()
            }
        }
    }

    private fun deleteManualItem(itemToDelete: Constants.AutoTimedActionItem) {
        val strict = prefs.getStrictModeData()
        val isStrictLocked = strict.isEnabled && !com.neubofy.reality.utils.StrictLockUtils.isMaintenanceWindow()
        
        if (isStrictLocked) {
            android.widget.Toast.makeText(this, "Locked by Strict Mode", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        android.app.AlertDialog.Builder(this)
            .setTitle("Delete Schedule")
            .setMessage("Are you sure you want to delete '${itemToDelete.title}'?")
            .setPositiveButton("Delete") { _, _ ->
                manualList = prefs.loadAutoFocusHoursList()
                val found = manualList.find { it.title == itemToDelete.title && it.startTimeInMins == itemToDelete.startTimeInMins }
                if (found != null) {
                    manualList.remove(found)
                    prefs.saveAutoFocusHoursList(manualList)
                    sendBroadcast(android.content.Intent(com.neubofy.reality.services.AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE))
                    // Reschedule reminders since schedule changed
                    com.neubofy.reality.utils.AlarmScheduler.scheduleNextAlarm(this)
                    loadSchedules()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleSyncedEvent(event: CalendarEvent) {
        val updatedEvent = event.copy(isEnabled = !event.isEnabled)
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            db.calendarEventDao().updateEvent(updatedEvent)
            sendBroadcast(android.content.Intent(com.neubofy.reality.services.AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE))
            withContext(Dispatchers.Main) { loadSchedules() }
        }
    }

    // ============ DIALOG LOGIC (Unchanged from original) ============
    
    @android.annotation.SuppressLint("SetTextI18n")
    private fun showAddScheduleDialog() {
        try {
            // Check Strict Mode first
            if (!com.neubofy.reality.utils.StrictLockUtils.isModificationAllowedFor(this, com.neubofy.reality.utils.StrictLockUtils.FeatureType.SCHEDULE)) {
                android.widget.Toast.makeText(this, "🔒 Locked by Strict Mode. Edit allowed 00:00-00:10 daily.", android.widget.Toast.LENGTH_LONG).show()
                return
            }
            
            dialogBinding = com.neubofy.reality.databinding.DialogAddTimedActionBinding.inflate(layoutInflater)
            dialogBinding.timedTitle.text = "Add Schedule"
            dialogBinding.btnSelectUnblockedApps.visibility = View.GONE

            
            var startTimeMins = 540 // 9:00 AM
            var endTimeMins = 1020 // 5:00 PM
            
            dialogBinding.fromTime.text = String.format("%02d:%02d", startTimeMins / 60, startTimeMins % 60)
            dialogBinding.endTime.text = String.format("%02d:%02d", endTimeMins / 60, endTimeMins % 60)
            dialogBinding.cbReminder.isChecked = true
            
            dialogBinding.fromTime.setOnClickListener {
                showMaterialTimePicker("Start Time", startTimeMins / 60, startTimeMins % 60) { h, m ->
                    startTimeMins = h * 60 + m
                    dialogBinding.fromTime.text = String.format("%02d:%02d", h, m)
                }
            }
            
            dialogBinding.endTime.setOnClickListener {
                showMaterialTimePicker("End Time", endTimeMins / 60, endTimeMins % 60) { h, m ->
                    endTimeMins = h * 60 + m
                    dialogBinding.endTime.text = String.format("%02d:%02d", h, m)
                }
            }

            com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.GlassDialog)
                .setView(dialogBinding.root)
                .setPositiveButton("Add") { dialog, _ ->
                    val title = dialogBinding.cheatHourTitle.text.toString()
                    if (title.isEmpty()) {
                        android.widget.Toast.makeText(this, "Enter a title", android.widget.Toast.LENGTH_SHORT).show()
                    } else {
                        val days = ArrayList<Int>()
                        val chipGroup = dialogBinding.root.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chip_days)
                        if (chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_sun).isChecked) days.add(1)
                        if (chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_mon).isChecked) days.add(2)
                        if (chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_tue).isChecked) days.add(3)
                        if (chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_wed).isChecked) days.add(4)
                        if (chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_thu).isChecked) days.add(5)
                        if (chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_fri).isChecked) days.add(6)
                        if (chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_sat).isChecked) days.add(7)
                        
                        if (days.isEmpty()) {
                             android.widget.Toast.makeText(this, "Select days", android.widget.Toast.LENGTH_SHORT).show()
                             return@setPositiveButton
                        }
                    
                        val isReminder = dialogBinding.cbReminder.isChecked
                        val newItem = Constants.AutoTimedActionItem(title, startTimeMins, endTimeMins, arrayListOf(), repeatDays = days, isReminderEnabled = isReminder)
                        
                        manualList = prefs.loadAutoFocusHoursList()
                        manualList.add(newItem)
                        prefs.saveAutoFocusHoursList(manualList)
                        
                        sendBroadcast(android.content.Intent(com.neubofy.reality.services.AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE))
                        // Reschedule reminders since new schedule added
                        com.neubofy.reality.utils.AlarmScheduler.scheduleNextAlarm(this)
                        loadSchedules()
                        dialog.dismiss()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            android.util.Log.e("ScheduleList", "Error opening dialog", e)
            android.widget.Toast.makeText(this, "Error opening dialog: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    @android.annotation.SuppressLint("SetTextI18n")
    private fun showEditScheduleDialog(existing: Constants.AutoTimedActionItem) {
        // Check Strict Mode
        if (!com.neubofy.reality.utils.StrictLockUtils.isModificationAllowedFor(this, com.neubofy.reality.utils.StrictLockUtils.FeatureType.SCHEDULE)) {
            android.widget.Toast.makeText(this, "Locked by Strict Mode. Edit allowed 00:00-00:10 daily.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        
        dialogBinding = com.neubofy.reality.databinding.DialogAddTimedActionBinding.inflate(layoutInflater)
        dialogBinding.timedTitle.text = "Edit Schedule"
        dialogBinding.btnSelectUnblockedApps.visibility = View.GONE

        
        // Pre-fill with existing values
        dialogBinding.cheatHourTitle.setText(existing.title)
        var startTimeMins = existing.startTimeInMins
        var endTimeMins = existing.endTimeInMins
        
        dialogBinding.fromTime.text = String.format("%02d:%02d", startTimeMins / 60, startTimeMins % 60)
        dialogBinding.endTime.text = String.format("%02d:%02d", endTimeMins / 60, endTimeMins % 60)
        dialogBinding.cbReminder.isChecked = existing.isReminderEnabled
        
        // Pre-select days
        val chipGroup = dialogBinding.root.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chip_days)
        if (existing.repeatDays.contains(1)) chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_sun).isChecked = true
        if (existing.repeatDays.contains(2)) chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_mon).isChecked = true
        if (existing.repeatDays.contains(3)) chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_tue).isChecked = true
        if (existing.repeatDays.contains(4)) chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_wed).isChecked = true
        if (existing.repeatDays.contains(5)) chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_thu).isChecked = true
        if (existing.repeatDays.contains(6)) chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_fri).isChecked = true
        if (existing.repeatDays.contains(7)) chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_sat).isChecked = true
        
        dialogBinding.fromTime.setOnClickListener {
            showMaterialTimePicker("Start Time", startTimeMins / 60, startTimeMins % 60) { h, m ->
                startTimeMins = h * 60 + m
                dialogBinding.fromTime.text = String.format("%02d:%02d", h, m)
            }
        }
        
        dialogBinding.endTime.setOnClickListener {
            showMaterialTimePicker("End Time", endTimeMins / 60, endTimeMins % 60) { h, m ->
                endTimeMins = h * 60 + m
                dialogBinding.endTime.text = String.format("%02d:%02d", h, m)
            }
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.GlassDialog)
            .setView(dialogBinding.root)
            .setPositiveButton("Save") { dialog, _ ->
                val title = dialogBinding.cheatHourTitle.text.toString()
                if (title.isEmpty()) {
                    android.widget.Toast.makeText(this, "Enter a title", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    val days = ArrayList<Int>()
                    if (chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_sun).isChecked) days.add(1)
                    if (chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_mon).isChecked) days.add(2)
                    if (chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_tue).isChecked) days.add(3)
                    if (chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_wed).isChecked) days.add(4)
                    if (chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_thu).isChecked) days.add(5)
                    if (chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_fri).isChecked) days.add(6)
                    if (chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_sat).isChecked) days.add(7)
                    
                    if (days.isEmpty()) {
                         android.widget.Toast.makeText(this, "Select days", android.widget.Toast.LENGTH_SHORT).show()
                         return@setPositiveButton
                    }
                
                    val isReminder = dialogBinding.cbReminder.isChecked
                    
                    // ALWAYS reset dismissal when editing - any edit should allow reminder to fire again
                    val newLastDismissed = 0L
                    
                    val updatedItem = Constants.AutoTimedActionItem(title, startTimeMins, endTimeMins, arrayListOf(), repeatDays = days, isReminderEnabled = isReminder, lastDismissedDate = newLastDismissed)
                    
                    // Remove old and add updated
                    manualList = prefs.loadAutoFocusHoursList()
                    val found = manualList.find { it.title == existing.title && it.startTimeInMins == existing.startTimeInMins }
                    if (found != null) manualList.remove(found)
                    manualList.add(updatedItem)
                    prefs.saveAutoFocusHoursList(manualList)
                    
                    sendBroadcast(android.content.Intent(com.neubofy.reality.services.AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE))
                    
                    // Clear from fired cache to allow edited schedule to fire again
                    val oldScheduleId = "sched_${existing.title}_${existing.startTimeInMins}_${existing.endTimeInMins}"
                    com.neubofy.reality.utils.FiredEventsCache.clearFired(this, oldScheduleId)
                    
                    // Reschedule reminders since schedule edited
                    com.neubofy.reality.utils.AlarmScheduler.scheduleNextAlarm(this)
                    loadSchedules()
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showMaterialTimePicker(title: String, initialHour: Int, initialMinute: Int, onTimeSet: (Int, Int) -> Unit) {
        val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
            .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_12H)
            .setHour(initialHour)
            .setMinute(initialMinute)
            .setTitleText(title)
            .build()
        picker.addOnPositiveButtonClickListener { onTimeSet(picker.hour, picker.minute) }
        picker.show(supportFragmentManager, "time_picker")
    }

    private fun showSyncSettingsDialog() {
        val prefsSync = getSharedPreferences("calendar_sync", android.content.Context.MODE_PRIVATE)
        val isAutoSync = prefs.getBoolean("calendar_sync_auto_enabled", true)
        
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_calendar_settings, null)
        val switchAutoSync = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchAutoSync)
        val btnSelect = dialogView.findViewById<View>(R.id.btnSelectCalendars)
        val btnDisconnect = dialogView.findViewById<View>(R.id.btnDisconnect)
        
        switchAutoSync.isChecked = isAutoSync
        
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .show()
            
        switchAutoSync.setOnCheckedChangeListener { _, isChecked ->
            prefs.saveBoolean("calendar_sync_auto_enabled", isChecked)
        }
        
        btnSelect.setOnClickListener {
            dialog.dismiss()
            calendarPermissionLauncher.launch(android.Manifest.permission.READ_CALENDAR)
        }
        
        btnDisconnect.setOnClickListener {
             // === STRICT MODE: Lock Calendar Disconnect ===
             val strict = prefs.getStrictModeData()
             val isStrictActive = strict.isEnabled && !com.neubofy.reality.utils.StrictLockUtils.isMaintenanceWindow()
             
             if (isStrictActive && strict.isCalendarLocked) {
                 android.widget.Toast.makeText(this, "🔒 Calendar is locked by Strict Mode. Edit allowed 00:00-00:10 daily.", android.widget.Toast.LENGTH_LONG).show()
                 return@setOnClickListener
             }
             
             android.app.AlertDialog.Builder(this)
                .setTitle("Disconnect Calendar?")
                .setMessage("This will stop syncing and remove all synced events.")
                .setPositiveButton("Disconnect") { _, _ ->
                     prefsSync.edit().clear().apply()
                     lifecycleScope.launch(Dispatchers.IO) {
                         AppDatabase.getDatabase(applicationContext).calendarEventDao().deleteOldEvents(Long.MAX_VALUE)
                         withContext(Dispatchers.Main) { 
                             loadSchedules()
                             android.widget.Toast.makeText(this@ScheduleListActivity, "Disconnected.", android.widget.Toast.LENGTH_SHORT).show() 
                         }
                     }
                     dialog.dismiss()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun selectCalendars() {
        lifecycleScope.launch(Dispatchers.IO) {
            val calendars = mutableListOf<Pair<String, String>>()
            val projection = arrayOf(
                android.provider.CalendarContract.Calendars._ID,
                android.provider.CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                android.provider.CalendarContract.Calendars.ACCOUNT_NAME
            )

            contentResolver.query(
                android.provider.CalendarContract.Calendars.CONTENT_URI,
                projection, null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val name = cursor.getString(1) ?: "Unknown"
                    val account = cursor.getString(2) ?: "Local"
                    val lower = name.lowercase()
                    if (lower.contains("holiday") || lower.contains("birth") || lower.contains("week")) continue
                    calendars.add(Pair(id, "$name ($account)"))
                }
            }

            withContext(Dispatchers.Main) {
                if (calendars.isEmpty()) {
                    android.widget.Toast.makeText(this@ScheduleListActivity, "No calendars found", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    showSelectionDialog(calendars)
                }
            }
        }
    }
    
    private fun showSelectionDialog(calendars: List<Pair<String, String>>) {
        val prefsSync = getSharedPreferences("calendar_sync", android.content.Context.MODE_PRIVATE)
        val selectedIds = prefsSync.getStringSet("selected_calendar_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        val items = calendars.map { it.second }.toTypedArray()
        val checked = calendars.map { selectedIds.contains(it.first) }.toBooleanArray()

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Select Calendars")
            .setMultiChoiceItems(items, checked) { _, which, isChecked ->
                if (isChecked) selectedIds.add(calendars[which].first) else selectedIds.remove(calendars[which].first)
            }
            .setPositiveButton("Save") { _, _ ->
                prefsSync.edit().putStringSet("selected_calendar_ids", selectedIds).apply()
                syncAndReload()
            }
            .show()
    }

    private fun syncAndReload() {
        // === STRICT MODE: Block manual refresh when calendar is locked ===
        val strict = prefs.getStrictModeData()
        val isStrictActive = strict.isEnabled && !com.neubofy.reality.utils.StrictLockUtils.isMaintenanceWindow()
        
        if (isStrictActive && strict.isCalendarLocked) {
            android.widget.Toast.makeText(this, "🔒 Manual sync blocked by Strict Mode.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        val isAutoSync = prefs.getBoolean("calendar_sync_auto_enabled", true)
        if (isAutoSync) {
            val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.neubofy.reality.workers.CalendarSyncWorker>().build()
            androidx.work.WorkManager.getInstance(applicationContext).enqueue(workRequest)
            binding.scrollView.postDelayed({ loadSchedules() }, 2000)
        } else {
            loadSchedules()
        }
    }

    override fun onResume() {
        super.onResume()
        loadSchedules()
    }

    // Helper data class
    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}

sealed class ScheduleDisplayItem {
    data class Custom(val item: com.neubofy.reality.Constants.AutoTimedActionItem) : ScheduleDisplayItem()
    data class Synced(val event: com.neubofy.reality.data.db.CalendarEvent) : ScheduleDisplayItem()
}
