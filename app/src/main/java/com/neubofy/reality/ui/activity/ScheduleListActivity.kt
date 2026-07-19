package com.neubofy.reality.ui.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.content.Context
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
    
    private lateinit var dialogBinding: com.neubofy.reality.databinding.DialogAddTimedActionBinding
    


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
            triggerManualSync()
        }
    }

    private fun triggerManualSync() {
        if (!com.neubofy.reality.google.GoogleAuthManager.isFullWorkspaceConnected(this)) {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Full Connection Required")
                .setMessage("Your account is connected with basic identity only. To sync your calendar, please go to the Profile page, sign out, and sign in again with Full Connection.")
                .setPositiveButton("Go to Profile") { _, _ ->
                    val intent = android.content.Intent(this, ProfileActivity::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
            binding.swipeRefresh.isRefreshing = false
            return
        }

        android.widget.Toast.makeText(this, "\uD83D\uDCC5 Syncing calendar...", android.widget.Toast.LENGTH_SHORT).show()

        // Always trigger sync regardless of auto-sync toggle (pull = explicit user intent)
        val tag = "manual_calendar_sync"
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.neubofy.reality.workers.CalendarSyncWorker>()
            .addTag(tag)
            .build()

        val workManager = androidx.work.WorkManager.getInstance(applicationContext)
        workManager.enqueue(workRequest)

        workManager.getWorkInfosByTagLiveData(tag).observe(this) { workInfos ->
            val info = workInfos?.firstOrNull() ?: return@observe
            if (info.state == androidx.work.WorkInfo.State.SUCCEEDED ||
                info.state == androidx.work.WorkInfo.State.FAILED ||
                info.state == androidx.work.WorkInfo.State.CANCELLED
            ) {
                loadSchedules()
                binding.swipeRefresh.isRefreshing = false
                                if (info.state == androidx.work.WorkInfo.State.FAILED) {
                        val errorMsg = info.outputData.getString("error") ?: "Sync failed."
                        if (errorMsg.contains("Not signed in") || errorMsg.contains("Unauthorized") || errorMsg.contains("401") || errorMsg.contains("Workspace connection") || errorMsg.contains("connection required")) {
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(this@ScheduleListActivity)
                            .setTitle("Authentication Error")
                            .setMessage("Your Google Workspace session is missing or expired. Please go to the Profile page to sign in again.")
                            .setPositiveButton("Go to Profile") { _, _ ->
                                val intent = android.content.Intent(this@ScheduleListActivity, ProfileActivity::class.java)
                                intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                                startActivity(intent)
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    } else {
                        android.widget.Toast.makeText(this@ScheduleListActivity, "Error: $errorMsg", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
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
            
            // Check if data is expired (from yesterday)
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val todayStart = cal.timeInMillis
            val todayEnd = todayStart + (24 * 60 * 60 * 1000)
            
            val lastSyncDate = getSharedPreferences("calendar_sync", Context.MODE_PRIVATE).getLong("last_sync_date_millis", 0L)
            if (lastSyncDate < todayStart) {
                // Data is expired! Auto-refresh the UI data from Google Calendar
                withContext(Dispatchers.Main) {
                    binding.swipeRefresh.isRefreshing = true
                    triggerManualSync()
                }
            }
            
            // 1. Load Custom Schedules
            
            
            // 2. Load Synced Events for TODAY ONLY (prevents yesterday's events bleeding over)
            val allDbEvents = db.calendarEventDao().getEventsInRange(todayStart, todayEnd)


            withContext(Dispatchers.Main) {
                displayItems.clear()
                
                // Add Custom
                
                
                // Add Synced
                displayItems.addAll(allDbEvents.map { ScheduleDisplayItem.Synced(it) })

                // Render events on timeline
                renderEventsOnTimeline()
                
                val isEmpty = displayItems.isEmpty()
                binding.layoutEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
                binding.timelineContainer.visibility = if (isEmpty) View.GONE else View.VISIBLE
            }
        }
    }

    private fun renderEventsOnTimeline() {
        binding.eventsContainer.removeAllViews()

        val nowMins = TimeTools.getCurrentTimeInMinutes()
        val nowMs = System.currentTimeMillis()

        for (item in displayItems) {
            val isCustom = (item as ScheduleDisplayItem.Synced).event.source == "IN_APP"
            
            val (startMins, endMins, title, isActiveNow) = when (item) {
                
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
            is ScheduleDisplayItem.Synced -> {
                val event = item.event
                val dateFormat = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault())
                
                val builder = com.google.android.material.dialog.MaterialAlertDialogBuilder(this, R.style.GlassDialog)
                    .setTitle("📆 ${event.title}")
                    .setMessage("Start: ${dateFormat.format(java.util.Date(event.startTime))}\nEnd: ${dateFormat.format(java.util.Date(event.endTime))}")
                
                if (com.neubofy.reality.utils.StrictLockUtils.isModificationAllowedFor(this, com.neubofy.reality.utils.StrictLockUtils.FeatureType.SCHEDULE)) {
                    builder.setPositiveButton("Delete") { _, _ ->
                        deleteSyncedEvent(event)
                    }
                    if (event.calendarId == "IN_APP" || event.source == "IN_APP") {
                        builder.setNeutralButton("Edit") { _, _ ->
                            showAddScheduleDialog(event)
                        }
                    }
                } else {
                    builder.setPositiveButton("Locked by Strict Mode", null)
                }
                
                builder.setNegativeButton("Close", null)
                builder.show()
            }
        }
    }

    private fun deleteSyncedEvent(event: CalendarEvent) {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            db.calendarEventDao().deleteByEventId(event.eventId)
            sendBroadcast(android.content.Intent(com.neubofy.reality.services.AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE))
            com.neubofy.reality.utils.SmartScheduleManager.scheduleNextTransition(this@ScheduleListActivity)
            withContext(Dispatchers.Main) { 
                loadSchedules() 
                android.widget.Toast.makeText(this@ScheduleListActivity, "Schedule Deleted", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ============ DIALOG LOGIC (Unchanged from original) ============
    
    @android.annotation.SuppressLint("SetTextI18n")
    private fun showAddScheduleDialog(eventToEdit: CalendarEvent? = null) {
        try {
            dialogBinding = com.neubofy.reality.databinding.DialogAddTimedActionBinding.inflate(layoutInflater)
            dialogBinding.timedTitle.text = if (eventToEdit != null) "Edit Schedule" else "Add Schedule"
            dialogBinding.btnSelectUnblockedApps.visibility = View.GONE

            if (eventToEdit != null) {
                dialogBinding.cheatHourTitle.setText(eventToEdit.title)
                
                // Parse days
                val chipGroup = dialogBinding.root.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chip_days)
                val days = eventToEdit.repeatRule?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
                if (days.contains(1)) chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_sun).isChecked = true
                if (days.contains(2)) chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_mon).isChecked = true
                if (days.contains(3)) chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_tue).isChecked = true
                if (days.contains(4)) chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_wed).isChecked = true
                if (days.contains(5)) chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_thu).isChecked = true
                if (days.contains(6)) chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_fri).isChecked = true
                if (days.contains(7)) chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_sat).isChecked = true
            }

            val calStart = java.util.Calendar.getInstance()
            if (eventToEdit != null) calStart.timeInMillis = eventToEdit.startTime
            var startTimeMins = calStart.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calStart.get(java.util.Calendar.MINUTE)
            if (eventToEdit == null) startTimeMins = 540 // 9:00 AM

            val calEnd = java.util.Calendar.getInstance()
            if (eventToEdit != null) calEnd.timeInMillis = eventToEdit.endTime
            var endTimeMins = calEnd.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calEnd.get(java.util.Calendar.MINUTE)
            if (eventToEdit == null) endTimeMins = 1020 // 5:00 PM
            
            dialogBinding.fromTime.text = String.format("%02d:%02d", startTimeMins / 60, startTimeMins % 60)
            dialogBinding.endTime.text = String.format("%02d:%02d", endTimeMins / 60, endTimeMins % 60)
            dialogBinding.cbReminder.visibility = View.GONE
            
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
                    
                        val isReminder = false // Deprecated
                        
                        val startMs = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.HOUR_OF_DAY, startTimeMins / 60)
                            set(java.util.Calendar.MINUTE, startTimeMins % 60)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        
                        val endMs = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.HOUR_OF_DAY, endTimeMins / 60)
                            set(java.util.Calendar.MINUTE, endTimeMins % 60)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        
                        val finalEndMs = if (endMs <= startMs) endMs + 86400000L else endMs

                        val newItem = com.neubofy.reality.data.db.CalendarEvent(
                            eventId = eventToEdit?.eventId ?: java.util.UUID.randomUUID().toString(),
                            title = title,
                            startTime = startMs,
                            endTime = finalEndMs,
                            calendarId = "IN_APP",
                            source = "IN_APP",
                            isEnabled = eventToEdit?.isEnabled ?: true,
                            repeatRule = days.joinToString(",")
                        )
                        
                        lifecycleScope.launch(Dispatchers.IO) {
                            val db = AppDatabase.getDatabase(applicationContext)
                            if (eventToEdit != null) {
                                db.calendarEventDao().updateEvent(newItem)
                                com.neubofy.reality.utils.NotificationHelper.showInfoNotification(applicationContext, "Schedule Updated", "Your productive session '${title}' has been updated.")
                            } else {
                                db.calendarEventDao().insertAll(listOf(newItem))
                                com.neubofy.reality.utils.NotificationHelper.showInfoNotification(applicationContext, "Schedule Created", "Your productive session '${title}' has been scheduled.")
                            }
                            sendBroadcast(android.content.Intent(com.neubofy.reality.services.AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE))
                            com.neubofy.reality.utils.SmartScheduleManager.scheduleNextTransition(this@ScheduleListActivity)
                            withContext(Dispatchers.Main) {
                                loadSchedules()
                                dialog.dismiss()
                            }
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            android.util.Log.e("ScheduleList", "Error opening dialog", e)
            android.widget.Toast.makeText(this, "Error opening dialog: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
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
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_calendar_settings, null)
        val switchAutoSync = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchAutoSync)
        val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .show()
            
        val isAutoSync = prefs.getBoolean("calendar_sync_auto_enabled", true)
        
        val realityPrefs = getSharedPreferences("reality_prefs", android.content.Context.MODE_PRIVATE)
        val currentFcmToken = realityPrefs.getString(com.neubofy.reality.services.RealityFCMService.PREF_FCM_TOKEN, null)
        val registeredFcmToken = realityPrefs.getString("registered_fcm_token", null)

        if (isAutoSync && currentFcmToken != null && currentFcmToken != registeredFcmToken) {
            // Token changed (key rotated), force the user to toggle it off and on to re-register
            com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Sync Key Rotated")
                .setMessage("Your device's Google Play Services sync key was recently rotated. Please toggle Auto-Sync OFF and then ON again to re-establish the connection.")
                .setPositiveButton("OK", null)
                .show()
                
            switchAutoSync.isChecked = false
            prefs.saveBoolean("calendar_sync_auto_enabled", false)
        } else {
            switchAutoSync.isChecked = isAutoSync
        }

        // Auto-sync toggle (enables 15-min heartbeat sync AND sets up real-time sync on toggle ON)
        switchAutoSync.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (!com.neubofy.reality.google.GoogleAuthManager.isFullWorkspaceConnected(this)) {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("Full Connection Required")
                        .setMessage("Auto-Sync requires a full connection to Google Calendar. Please go to the Profile page, sign out, and sign in again with Full Connection.")
                        .setPositiveButton("Go to Profile") { _, _ ->
                            val intent = android.content.Intent(this, ProfileActivity::class.java)
                            intent.flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
                            startActivity(intent)
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                    switchAutoSync.isChecked = false
                    return@setOnCheckedChangeListener
                }
                prefs.saveBoolean("calendar_sync_auto_enabled", true)
                val isSignedIn = com.neubofy.reality.google.GoogleAuthManager.isSignedIn(this)
                val userId = if (isSignedIn) com.neubofy.reality.utils.IdentityManager.getUserId(this) else null
                val connectionSecret = if (isSignedIn) com.neubofy.reality.utils.IdentityManager.getConnectionSecret(this) else null
                val fcmToken = getSharedPreferences("reality_prefs", android.content.Context.MODE_PRIVATE)
                    .getString(com.neubofy.reality.services.RealityFCMService.PREF_FCM_TOKEN, null)
                val workerUrl = com.neubofy.reality.BuildConfig.NOTIFICATION_WORKER_URL

                // Fetch Google access token for webhook registration
                val googleAuthPrefs = com.neubofy.reality.utils.SecurePreferences.get(this, "google_auth_prefs")
                val googleAccessToken = googleAuthPrefs.getString("access_token", null)

                if (userId.isNullOrEmpty() || connectionSecret.isNullOrEmpty() || googleAccessToken.isNullOrEmpty()) {
                    android.widget.Toast.makeText(this, "Sign in to your Google/Reality account first.", android.widget.Toast.LENGTH_SHORT).show()
                    switchAutoSync.isChecked = false
                    return@setOnCheckedChangeListener
                }

                if (fcmToken.isNullOrEmpty()) {
                    android.widget.Toast.makeText(this, "Waiting for device token... try again in a moment.", android.widget.Toast.LENGTH_SHORT).show()
                    switchAutoSync.isChecked = false
                    return@setOnCheckedChangeListener
                }
                if (workerUrl.isEmpty()) {
                    android.widget.Toast.makeText(this, "Notification service not configured.", android.widget.Toast.LENGTH_SHORT).show()
                    switchAutoSync.isChecked = false
                    return@setOnCheckedChangeListener
                }

                // 1. Register FCM token with notification worker
                com.neubofy.reality.services.RealityFCMService.registerTokenWithWorker(applicationContext, workerUrl, userId, connectionSecret, fcmToken)
                
                // 2. Register Webhook Watch channel with Google Calendar API
                com.neubofy.reality.services.RealityFCMService.registerCalendarWebhook(applicationContext, workerUrl, userId, googleAccessToken)

                android.widget.Toast.makeText(this, "\uD83D\uDD14 Real-time sync setup started...", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                prefs.saveBoolean("calendar_sync_auto_enabled", false)
            }
        }
    }



    private fun syncAndReload() {

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
        com.neubofy.reality.utils.PermissionHelper.checkAndPromptForCore(this, checkAccessibility = false)
        loadSchedules()
    }

    // Helper data class
    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}

sealed class ScheduleDisplayItem {
    
    data class Synced(val event: com.neubofy.reality.data.db.CalendarEvent) : ScheduleDisplayItem()
}
