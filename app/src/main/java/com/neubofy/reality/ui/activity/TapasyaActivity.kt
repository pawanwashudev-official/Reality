package com.neubofy.reality.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.neubofy.reality.R
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.data.db.TapasyaSession
import com.neubofy.reality.databinding.ActivityTapasyaBinding
import com.neubofy.reality.services.TapasyaService
import com.neubofy.reality.ui.adapter.TapasyaSessionAdapter
import com.neubofy.reality.utils.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class TapasyaActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTapasyaBinding
    private lateinit var sessionAdapter: TapasyaSessionAdapter
    private lateinit var db: AppDatabase
    
    // Settings (stored in prefs)
    private var targetTimeMins = 60
    private var pauseLimitMins = 15
    
    // Day navigation (0 = today, -1 = yesterday, etc, max -6)
    private var selectedDayOffset = 0
    private val dateFormat = SimpleDateFormat("EEE, MMM d", Locale.getDefault())

    private val requestCalendarPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            binding.cardCalendarPerm.visibility = View.GONE
            binding.rvCalendarEvents.visibility = View.VISIBLE
            loadCalendarEvents()
        } else {
            binding.cardCalendarPerm.visibility = View.VISIBLE
            binding.rvCalendarEvents.visibility = View.GONE
            binding.tvPermMessage.text = "Permission needed to sync study blocks."
        }
    }

    private lateinit var calendarRepository: com.neubofy.reality.data.repository.CalendarRepository
    private lateinit var calendarAdapter: com.neubofy.reality.ui.adapter.CalendarEventAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityTapasyaBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getDatabase(this)
        calendarRepository = com.neubofy.reality.data.repository.CalendarRepository(this)
        
        setupInsets()
        loadSettings()
        setupListeners()
        setupSessionHistory()
        setupCalendar()
        observeClockState()
        
        // Cleanup old sessions on startup
        cleanupOldSessions()
        
        // Handle external open settings request
        handleIntent(intent)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("OPEN_SETTINGS", false) == true) {
            // Post to queue to ensure UI is ready
            binding.root.post { showSettingsDialog() }
        }
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { _, insets ->
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

        // Start Button with Double Tap support
        val gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                val state = TapasyaService.clockState.value
                if (!state.isSessionActive) {
                    // Smart Start Logic
                    val smartEvent = upcomingSmartEvent
                    if (smartEvent != null && smartEvent.status != com.neubofy.reality.data.repository.CalendarRepository.EventStatus.COMPLETED) {
                         // Start the highlighted/upcoming event!
                         startSessionFromEvent(smartEvent)
                    } else {
                         // Default start
                         startSessionWithDefaults()
                    }
                }
                return true
            }

            override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                val state = TapasyaService.clockState.value
                if (state.isPaused) {
                    sendServiceAction(TapasyaService.ACTION_RESUME)
                } else if (!state.isSessionActive) {
                    showStartSessionDialog()
                }
                return true
            }
        })

        binding.btnStart.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
        
        // Remove Long Click as Double Tap covers quick start, but keep for accessibility/alternate
        binding.btnStart.setOnLongClickListener {
            val state = TapasyaService.clockState.value
            if (!state.isSessionActive) {
                startSessionWithDefaults()
            }
            true
        }

        // Pause Button
        binding.btnPause.setOnClickListener {
            sendServiceAction(TapasyaService.ACTION_PAUSE)
        }

        // Stop Button
        binding.btnStop.setOnClickListener {
            sendServiceAction(TapasyaService.ACTION_STOP)
            binding.root.postDelayed({ loadSessionsForSelectedDay() }, 500)
        }
        
        // Reset Button
        binding.btnReset.setOnClickListener {
            sendServiceAction(TapasyaService.ACTION_RESET)
        }

        // Settings Button
        binding.btnSettings.setOnClickListener {
            showSettingsDialog()
        }
        
        // Day Navigation
        
        // Day Navigation
        binding.btnPrevDay.setOnClickListener {
            if (selectedDayOffset > -6) {
                selectedDayOffset--
                updateDateDisplay()
                loadSessionsForSelectedDay()
            }
        }
        
        binding.btnNextDay.setOnClickListener {
            if (selectedDayOffset < 0) {
                selectedDayOffset++
                updateDateDisplay()
                loadSessionsForSelectedDay()
            }
        }
        binding.btnNextDay.setOnClickListener {
            if (selectedDayOffset < 0) {
                selectedDayOffset++
                updateDateDisplay()
                loadSessionsForSelectedDay()
            }
        }

        // Edit Start Time Listener
        binding.btnEditTime.setOnClickListener {
            val state = TapasyaService.clockState.value
            if (state.isRunning) {
                showEditStartTimeDialog(state.elapsedTimeMs)
            }
        }
    }
    
    private fun showEditStartTimeDialog(currentElapsedMs: Long) {
        val now = System.currentTimeMillis()
        val derivedStartTime = now - currentElapsedMs
        val cal = Calendar.getInstance()
        cal.timeInMillis = derivedStartTime
        
        val picker = com.google.android.material.timepicker.MaterialTimePicker.Builder()
            .setTimeFormat(com.google.android.material.timepicker.TimeFormat.CLOCK_12H)
            .setHour(cal.get(Calendar.HOUR_OF_DAY))
            .setMinute(cal.get(Calendar.MINUTE))
            .setTitleText("Edit Start Time")
            .build()
            
        picker.addOnPositiveButtonClickListener {
            val h = picker.hour
            val m = picker.minute
            
            // Construct new start time (using Today's date + selected H:M)
            val newCal = Calendar.getInstance() // Now
            newCal.set(Calendar.HOUR_OF_DAY, h)
            newCal.set(Calendar.MINUTE, m)
            newCal.set(Calendar.SECOND, 0)
            newCal.set(Calendar.MILLISECOND, 0)
            
            val newStartTime = newCal.timeInMillis
            
            // Send to Service
            val intent = Intent(this, TapasyaService::class.java).apply {
                action = TapasyaService.ACTION_UPDATE_START_TIME
                putExtra(TapasyaService.EXTRA_NEW_START_TIME, newStartTime)
            }
            startService(intent)
        }
        
        picker.show(supportFragmentManager, "time_picker")
    }
    
    private fun setupSessionHistory() {
        sessionAdapter = TapasyaSessionAdapter(emptyList()) { session ->
            // Delete session
            MaterialAlertDialogBuilder(this)
                .setTitle("Delete Session")
                .setMessage("Delete this ${session.name} session?")
                .setPositiveButton("Delete") { _, _ ->
                    deleteSession(session)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        
        binding.rvSessions.layoutManager = LinearLayoutManager(this)
        binding.rvSessions.adapter = sessionAdapter
        
        updateDateDisplay()
        loadSessionsForSelectedDay()
    }
    
    private fun updateDateDisplay() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, selectedDayOffset)
        
        val dateText = when (selectedDayOffset) {
            0 -> "Today"
            -1 -> "Yesterday"
            else -> dateFormat.format(cal.time)
        }
        binding.tvSelectedDate.text = dateText
        
        // Enable/disable navigation buttons
        binding.btnPrevDay.alpha = if (selectedDayOffset > -6) 1f else 0.3f
        binding.btnNextDay.alpha = if (selectedDayOffset < 0) 1f else 0.3f
    }
    
    private fun loadSessionsForSelectedDay() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, selectedDayOffset)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis
        
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val dayEnd = cal.timeInMillis
        
        lifecycleScope.launch {
            val sessions = withContext(Dispatchers.IO) {
                db.tapasyaSessionDao().getSessionsForDay(dayStart, dayEnd)
            }
            sessionAdapter.updateSessions(sessions)
            binding.tvNoSessions.visibility = if (sessions.isEmpty()) View.VISIBLE else View.GONE
            binding.rvSessions.visibility = if (sessions.isEmpty()) View.GONE else View.VISIBLE
        }
    }
    
    private fun deleteSession(session: TapasyaSession) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                db.tapasyaSessionDao().delete(session)
            }
            loadSessionsForSelectedDay()
        }
    }
    
    private fun cleanupOldSessions() {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -7)
        val cutoff = cal.timeInMillis
        
        lifecycleScope.launch(Dispatchers.IO) {
            db.tapasyaSessionDao().deleteOldSessions(cutoff)
        }
    }

    private fun showStartSessionDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_start_session, null)
        val etName = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_session_name)
        val sliderTarget = dialogView.findViewById<com.google.android.material.slider.Slider>(R.id.slider_target_time)
        val sliderPause = dialogView.findViewById<com.google.android.material.slider.Slider>(R.id.slider_pause_limit)
        val tvTargetVal = dialogView.findViewById<android.widget.TextView>(R.id.tv_target_time_val)
        val tvPauseVal = dialogView.findViewById<android.widget.TextView>(R.id.tv_pause_limit_val)

        // Initialize with current defaults
        sliderTarget.value = targetTimeMins.toFloat().coerceIn(15f, 360f)
        sliderPause.value = pauseLimitMins.toFloat().coerceIn(1f, 30f)
        tvTargetVal.text = formatMinutes(targetTimeMins)
        tvPauseVal.text = formatMinutes(pauseLimitMins)

        // Listeners for local dialog updates (does not save to prefs)
        sliderTarget.addOnChangeListener { _, value, _ ->
            tvTargetVal.text = formatMinutes(value.toInt())
        }
        sliderPause.addOnChangeListener { _, value, _ ->
            tvPauseVal.text = formatMinutes(value.toInt())
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Start Session")
            .setView(dialogView)
            .setPositiveButton("Start") { _, _ ->
                val name = etName.text.toString().takeIf { it.isNotBlank() } ?: "Tapasya"
                val target = sliderTarget.value.toInt()
                val pause = sliderPause.value.toInt()
                startSession(name, target, pause)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startSessionWithDefaults() {
        startSession("Tapasya", targetTimeMins, pauseLimitMins)
    }

    private fun startSession(name: String, targetMins: Int, pauseMins: Int) {
        val intent = Intent(this, TapasyaService::class.java).apply {
            action = TapasyaService.ACTION_START
            putExtra(TapasyaService.EXTRA_SESSION_NAME, name)
            putExtra(TapasyaService.EXTRA_TARGET_TIME_MS, targetMins * 60 * 1000L)
            putExtra(TapasyaService.EXTRA_PAUSE_LIMIT_MS, pauseMins * 60 * 1000L)
        }
        startService(intent)
    }

    private fun startSessionFromEvent(event: com.neubofy.reality.data.repository.CalendarRepository.CalendarEvent) {
        val durationMins = ((event.endTime - event.startTime) / (1000 * 60)).toInt()
        val targetMs = durationMins * 60 * 1000L
        
        val intent = Intent(this, TapasyaService::class.java).apply {
            action = TapasyaService.ACTION_START
            putExtra(TapasyaService.EXTRA_SESSION_NAME, event.title)
            putExtra(TapasyaService.EXTRA_TARGET_TIME_MS, targetMs)
            putExtra(TapasyaService.EXTRA_PAUSE_LIMIT_MS, pauseLimitMins * 60 * 1000L) // Use default pause limit
        }
        startService(intent)
    }
    
    private fun setupCalendar() {
        calendarAdapter = com.neubofy.reality.ui.adapter.CalendarEventAdapter(emptyList()) { event ->
            // On Event Click
            MaterialAlertDialogBuilder(this)
                .setTitle("Start ${event.title}?")
                .setMessage("Start this session for ${(event.endTime - event.startTime) / (1000 * 60)} minutes?")
                .setPositiveButton("Start") { _, _ ->
                    startSessionFromEvent(event)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
        binding.rvCalendarEvents.layoutManager = LinearLayoutManager(this)
        binding.rvCalendarEvents.adapter = calendarAdapter
        
        binding.btnConnectCalendar.setOnClickListener {
            requestCalendarPermissionLauncher.launch(android.Manifest.permission.READ_CALENDAR)
        }
        
        binding.btnShowEvents.setOnClickListener {
             val useInternalSync = getSharedPreferences("tapasya_prefs", MODE_PRIVATE).getBoolean("sync_source_internal", false)
             
             if (useInternalSync) {
                 loadCalendarEvents()
                 binding.cardCalendarPerm.visibility = View.GONE
                 binding.rvCalendarEvents.visibility = View.VISIBLE
             } else {
                 if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this, 
                    android.Manifest.permission.READ_CALENDAR
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                     loadCalendarEvents()
                     binding.cardCalendarPerm.visibility = View.GONE
                     binding.rvCalendarEvents.visibility = View.VISIBLE
                } else {
                    requestCalendarPermissionLauncher.launch(android.Manifest.permission.READ_CALENDAR)
                }
             }
        }
        
        // Initial state
        checkCalendarPermission()
    }
    
    private fun checkCalendarPermission() {
        val useInternalSync = getSharedPreferences("tapasya_prefs", MODE_PRIVATE).getBoolean("sync_source_internal", false)
        
        if (useInternalSync) {
            // Internal sync doesn't need calendar permission
            binding.cardCalendarPerm.visibility = View.GONE
            binding.rvCalendarEvents.visibility = View.VISIBLE
            loadCalendarEvents() // Will route to internal
        } else {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    this, 
                    android.Manifest.permission.READ_CALENDAR
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                binding.cardCalendarPerm.visibility = View.GONE
                binding.rvCalendarEvents.visibility = View.VISIBLE
                loadCalendarEvents() 
            } else {
                binding.cardCalendarPerm.visibility = View.VISIBLE
                binding.rvCalendarEvents.visibility = View.GONE
            }
        }
    }
    
    private var upcomingSmartEvent: com.neubofy.reality.data.repository.CalendarRepository.CalendarEvent? = null

    private fun loadCalendarEvents() {
        val useInternalSync = getSharedPreferences("tapasya_prefs", MODE_PRIVATE).getBoolean("sync_source_internal", false)
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 1. Fetch Events (00:00 - 23:59)
                val events = if (useInternalSync) {
                     loadInternalEvents()
                } else {
                     calendarRepository.getEventsForToday()
                }
                
                // 2. Fetch Sessions for Today (00:00 - 23:59)
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val startOfDay = cal.timeInMillis
                
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                val endOfDay = cal.timeInMillis
                
                val sessions = db.tapasyaSessionDao().getSessionsForDay(startOfDay, endOfDay)
                
                // 3. Correlate
                val correlatedEvents = correlateEventsWithSessions(events, sessions)
                
                // 4. Identify Smart Event (First Upcoming or Running)
                val now = System.currentTimeMillis()
                upcomingSmartEvent = correlatedEvents.firstOrNull { 
                     it.status == com.neubofy.reality.data.repository.CalendarRepository.EventStatus.RUNNING || 
                     (it.status == com.neubofy.reality.data.repository.CalendarRepository.EventStatus.UPCOMING) 
                }

                withContext(Dispatchers.Main) {
                    calendarAdapter.updateEvents(correlatedEvents)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun correlateEventsWithSessions(
        events: List<com.neubofy.reality.data.repository.CalendarRepository.CalendarEvent>,
        sessions: List<TapasyaSession>
    ): List<com.neubofy.reality.data.repository.CalendarRepository.CalendarEvent> {
        val now = System.currentTimeMillis()
        var foundNext = false
        
        return events.map { event ->
            // Match Logic: Name equals (ignore case) AND Start time is within [EventStart - 30min, EventEnd]
            val matchedSessions = sessions.filter { session ->
                val nameMatch = session.name.equals(event.title, ignoreCase = true)
                val timeMatch = session.startTime >= (event.startTime - 30 * 60 * 1000L) && session.startTime <= event.endTime
                nameMatch && timeMatch
            }
            
            val totalEffectiveTime = matchedSessions.sumOf { it.effectiveTimeMs }
            val eventDuration = (event.endTime - event.startTime).coerceAtLeast(1)
            val progress = totalEffectiveTime.toFloat() / eventDuration
            
            // Determine Status
            if (progress >= 0.9f) { // 90% completion
                event.status = com.neubofy.reality.data.repository.CalendarRepository.EventStatus.COMPLETED
                event.progress = 1.0f
            } else {
                event.progress = progress
                
                if (now >= event.startTime && now < event.endTime) {
                    event.status = com.neubofy.reality.data.repository.CalendarRepository.EventStatus.RUNNING
                } else if (now < event.startTime && !foundNext) {
                    event.status = com.neubofy.reality.data.repository.CalendarRepository.EventStatus.UPCOMING
                    foundNext = true // Only mark the *first* upcoming
                } else {
                    event.status = com.neubofy.reality.data.repository.CalendarRepository.EventStatus.PENDING
                }
            }
            event
        }
    }

    // Fetch from ScheduleManager (App's Auto Focus / Internal DB)
    private suspend fun loadInternalEvents(): List<com.neubofy.reality.data.repository.CalendarRepository.CalendarEvent> {
        val unifiedEvents = com.neubofy.reality.data.ScheduleManager.getUnifiedEventsForToday(this)
        
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = cal.timeInMillis
        
        return unifiedEvents.map { uEvent ->
            com.neubofy.reality.data.repository.CalendarRepository.CalendarEvent(
                id = uEvent.originalId.hashCode().toLong(),
                title = uEvent.title,
                description = "Source: ${uEvent.source}",
                startTime = todayStart + (uEvent.startTimeMins * 60 * 1000L),
                endTime = todayStart + (uEvent.endTimeMins * 60 * 1000L),
                color = if (uEvent.source == com.neubofy.reality.data.EventSource.MANUAL) 
                            android.graphics.Color.parseColor("#4CAF50") // Green for Focus
                        else android.graphics.Color.parseColor("#2196F3"), // Blue for Others
                location = null,
                isInternal = true
            )
        }
    }
    
    private fun showSettingsDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val sheetBinding = com.neubofy.reality.databinding.BottomSheetTapasyaSettingsBinding.inflate(layoutInflater)
        dialog.setContentView(sheetBinding.root)
        
        // Target Slider (60 to 360, step 15)
        sheetBinding.sliderTargetTime.value = targetTimeMins.toFloat().coerceIn(60f, 360f)
        sheetBinding.tvRestDuration.text = formatMinutes(targetTimeMins)
        
        sheetBinding.sliderTargetTime.addOnChangeListener { _, value, _ ->
            targetTimeMins = value.toInt()
            sheetBinding.tvRestDuration.text = formatMinutes(targetTimeMins)
        }
        
        // Pause Slider (1 to 30, step 1)
        sheetBinding.sliderPauseLimit.value = pauseLimitMins.toFloat().coerceIn(1f, 30f)
        sheetBinding.tvPauseLimitVal.text = formatMinutes(pauseLimitMins)
        
        sheetBinding.sliderPauseLimit.addOnChangeListener { _, value, _ ->
            pauseLimitMins = value.toInt()
            sheetBinding.tvPauseLimitVal.text = formatMinutes(pauseLimitMins)
        }
        
        // Sync Source Switch
        val prefs = getSharedPreferences("tapasya_prefs", MODE_PRIVATE)
        var useInternalSync = prefs.getBoolean("sync_source_internal", false)
        
        sheetBinding.switchSyncSource.isChecked = useInternalSync
        sheetBinding.tvSyncSourceDesc.text = if (useInternalSync) "App Schedule (Auto Focus)" else "Device Calendar"
        
        sheetBinding.switchSyncSource.setOnCheckedChangeListener { _, isChecked ->
            useInternalSync = isChecked
            sheetBinding.tvSyncSourceDesc.text = if (useInternalSync) "App Schedule (Auto Focus)" else "Device Calendar"
        }
        
        sheetBinding.btnSaveSettings.setOnClickListener {
            saveSettings()
            
            // Save Sync Pref
            prefs.edit().putBoolean("sync_source_internal", useInternalSync).apply()
            
            // Refresh list if visible
            checkCalendarPermission()
            
            dialog.dismiss()
        }
        
        dialog.show()
    }

    private fun observeClockState() {
        lifecycleScope.launch {
            TapasyaService.clockState.collectLatest { state ->
                updateUI(state)
            }
        }
    }

    private fun updateUI(state: TapasyaService.ClockState) {
        binding.tvTimer.text = formatTime(state.elapsedTimeMs)
        
        // Update Wave View
        binding.waveView.setProgress(state.progress)
        
        // Resolve colors
        val colorPrimary = getThemeColor(android.R.attr.colorPrimary)
        val colorSecondary = getThemeColor(android.R.attr.colorAccent) 
        val colorError = getThemeColor(android.R.attr.colorError)

        when {
            state.isRunning -> {
                binding.tvStatus.text = "Focusing: ${state.sessionName}"
                binding.waveView.setWaterColor(colorPrimary)
                binding.waveView.setBorderColor(colorPrimary)
                
                binding.btnStart.visibility = View.GONE
                binding.btnPause.visibility = View.VISIBLE
                binding.btnStop.visibility = View.VISIBLE
                binding.btnReset.visibility = View.VISIBLE
                
                // Show Live Stats
                binding.cardLiveStats.visibility = View.VISIBLE
                binding.tvLiveXp.text = "⚡ ${state.currentXP} XP"
                binding.tvLiveFragment.text = "Fragment ${state.currentFragment}"
                
                // Edit Start Time (Check Lock)
                val lockEdit = getSharedPreferences("nightly_prefs", MODE_PRIVATE)
                    .getBoolean("lock_start_time_edit", false)
                binding.btnEditTime.visibility = if (lockEdit) View.GONE else View.VISIBLE
                
                // Hide Rest/Pause Timer
                binding.tvRestTimer.visibility = View.GONE
            }
            state.isPaused -> {
                binding.tvStatus.text = "Paused"
                val pauseColor = 0xFFFFC107.toInt() // Amber
                binding.waveView.setWaterColor(pauseColor)
                binding.waveView.setBorderColor(pauseColor)

                binding.btnStart.text = "Resume"
                binding.btnStart.setIconResource(R.drawable.baseline_play_arrow_24)
                binding.btnStart.visibility = View.VISIBLE
                binding.btnPause.visibility = View.GONE
                binding.btnStop.visibility = View.VISIBLE
                binding.btnReset.visibility = View.VISIBLE
                
                // Keep Live Stats visible (optional, but good context)
                binding.cardLiveStats.visibility = View.VISIBLE
                binding.tvLiveXp.text = "⚡ ${state.currentXP} XP"
                binding.tvLiveFragment.text = "Fragment ${state.currentFragment}"
                
                binding.btnEditTime.visibility = View.GONE
                
                val pauseRemaining = state.pauseLimitMs - state.totalPauseMs
                binding.tvRestTimer.text = "Pause left: ${formatTime(pauseRemaining)}"
                binding.tvRestTimer.visibility = View.VISIBLE
            }
            else -> {
                binding.tvStatus.text = "Ready to Focus"
                binding.waveView.setProgress(0f)
                binding.waveView.setWaterColor(colorPrimary)
                binding.waveView.setBorderColor(colorPrimary)

                binding.btnStart.text = "Start"
                binding.btnStart.setIconResource(R.drawable.baseline_play_arrow_24)
                binding.btnStart.visibility = View.VISIBLE
                binding.btnPause.visibility = View.GONE
                binding.btnStop.visibility = View.GONE
                binding.btnReset.visibility = View.GONE
                binding.btnEditTime.visibility = View.GONE
                
                binding.cardLiveStats.visibility = View.GONE
                binding.tvRestTimer.visibility = View.GONE
            }
        }

    }
    
    private fun getThemeColor(attr: Int): Int {
        val typedValue = android.util.TypedValue()
        theme.resolveAttribute(attr, typedValue, true)
        return typedValue.data
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(this, TapasyaService::class.java).apply {
            this.action = action
        }
        startService(intent)
    }

    private fun formatTime(ms: Long): String {
        val totalSecs = ms / 1000
        val seconds = totalSecs % 60
        val minutes = (totalSecs / 60) % 60
        val hours = totalSecs / 3600
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
    
    private fun formatMinutes(mins: Int): String {
        val h = mins / 60
        val m = mins % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("tapasya_prefs", MODE_PRIVATE)
        targetTimeMins = prefs.getInt("target_time_mins", 60)
        pauseLimitMins = prefs.getInt("pause_limit_mins", 15)
    }

    private fun saveSettings() {
        getSharedPreferences("tapasya_prefs", MODE_PRIVATE).edit().apply {
            putInt("target_time_mins", targetTimeMins)
            putInt("pause_limit_mins", pauseLimitMins)
            apply()
        }
    }
    
    override fun onResume() {
        super.onResume()
        loadSessionsForSelectedDay()
    }
}
