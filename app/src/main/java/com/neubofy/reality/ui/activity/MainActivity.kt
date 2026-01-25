package com.neubofy.reality.ui.activity

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.neubofy.reality.Constants
import com.neubofy.reality.R
import com.neubofy.reality.databinding.ActivityMainBinding
import com.neubofy.reality.databinding.DialogPermissionInfoBinding
import com.neubofy.reality.databinding.DialogRemoveAntiUninstallBinding

import com.neubofy.reality.services.AppBlockerService
import com.neubofy.reality.ui.dialogs.StartFocusMode
import com.neubofy.reality.ui.fragments.anti_uninstall.ChooseModeFragment
import com.neubofy.reality.ui.fragments.installation.AccessibilityGuide
import com.neubofy.reality.ui.fragments.installation.PermissionsFragment
import com.neubofy.reality.ui.fragments.installation.WelcomeFragment
import com.neubofy.reality.utils.SavedPreferencesLoader
import kotlinx.coroutines.*
import com.neubofy.reality.utils.FocusStatusManager
import com.neubofy.reality.utils.FocusType
import com.neubofy.reality.utils.TimeTools
import com.neubofy.reality.utils.ThemeManager
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import android.view.LayoutInflater
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private val PERMISSIONS = setOf(
        androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.StepsRecord::class),
        androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.TotalCaloriesBurnedRecord::class),
        androidx.health.connect.client.permission.HealthPermission.getReadPermission(androidx.health.connect.client.records.SleepSessionRecord::class),
        androidx.health.connect.client.permission.HealthPermission.getWritePermission(androidx.health.connect.client.records.SleepSessionRecord::class)
    )

    private val requestPermissions = registerForActivityResult(androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.containsAll(PERMISSIONS)) {
            // Permissions successfully granted
            Log.d("HealthConnect", "All permissions granted")
        } else {
            // Not all permissions granted
            Log.d("HealthConnect", "Not all permissions granted")
        }
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var selectFocusModeUnblockedAppsLauncher: ActivityResultLauncher<Intent>
    private lateinit var addAutoFocusHoursActivity: ActivityResultLauncher<Intent>

    private val savedPreferencesLoader = SavedPreferencesLoader(this)
    private lateinit var options: ActivityOptionsCompat
    private var isDeviceAdminOn = false
    private var isAntiUninstallOn = false
    private var statusUpdaterJob: Job? = null
    private var currentFocusStatus: com.neubofy.reality.utils.FocusStatus? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        
        // Check for Intro (Moved from LauncherActivity)
        // Check for Intro & Onboarding
        val prefs = getSharedPreferences("app_preferences", MODE_PRIVATE)
        if (!prefs.getBoolean("intro_shown", false)) {
            val intent = Intent(this, SecurityIntroActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        
        if (!prefs.getBoolean("onboarding_v2_complete", false)) {
            val intent = Intent(this, OnboardingActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        options = ActivityOptionsCompat.makeCustomAnimation(this, R.anim.fade_in, R.anim.fade_out)
        setupActivityLaunchers()
        setupClickListeners()
        setupBottomNavigation()
        // logAppSignature() - masked as per user request

        // Startup permission check removed as per user request. 
        // Permissions are now requested on-demand.
        
        // Observe Terminal Logs
        scope.launch {
            com.neubofy.reality.utils.TerminalLogger.logs.collect { logText ->
                binding.tvTerminalLog.text = logText
            }
        }
    }

    override fun onResume() {
        super.onResume()
        
        checkAndShowSleepVerification()
        
        loadStatistics()
        checkAndRequestNextPermission()
        startStatusUpdater()
        updateGreeting()
        updateThemeVisuals()
        updateTerminalLogVisibility()
        ThemeManager.applyToAllCards(binding.root)

        

    }

    private fun loadStatistics() {
        updateEmergencyUI()
        updateBedtimeUI()
        updateUsageLimitUI()
        loadReflectionCard()
    }
    
    private fun updateGreeting() {
        val prefs = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        val name = prefs.getString("user_name", "")
        if (!name.isNullOrEmpty()) {
             binding.tvTitle.text = name
             binding.tvGreeting.text = "Hello,"
        }
    }

    private val scope = MainScope()

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun logAppSignature() {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = packageInfo.signingInfo ?: return
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            } else {
                packageInfo.signatures
            }
            
            if (signatures == null) return

            for (signature in signatures) {
                val md = java.security.MessageDigest.getInstance("SHA-1")
                md.update(signature.toByteArray())
                val digest = md.digest()
                val hexString = StringBuilder()
                for (b in digest) {
                    hexString.append(String.format("%02X:", b))
                }
                if (hexString.length > 0) {
                    hexString.setLength(hexString.length - 1)
                }
                val sha1 = hexString.toString()
                com.neubofy.reality.utils.TerminalLogger.log("APP SHA-1: $sha1")
                Log.d("RealityAuth", "SHA-1: $sha1")
            }
        } catch (e: Exception) {
            com.neubofy.reality.utils.TerminalLogger.log("Failed to get SHA-1: ${e.message}")
        }
    }

    override fun onPause() {
        super.onPause()
        statusUpdaterJob?.cancel()
    }

    private fun startStatusUpdater() {
        statusUpdaterJob?.cancel()
        statusUpdaterJob = scope.launch {
            while (isActive) {
                updateFocusStatus()
                
                // SMART POLLING: Wait until the start of the next minute
                val now = System.currentTimeMillis()
                val millisPassedInCurrentMinute = now % 60000
                val millisToNextMinute = 60000 - millisPassedInCurrentMinute
                
                // Add tiny buffer (50ms) to ensure we seek into the next minute
                delay(millisToNextMinute + 50)
            }
        }
    }

    private suspend fun updateFocusStatus() {
        val status = withContext(Dispatchers.IO) {
            FocusStatusManager(this@MainActivity).getCurrentStatus()
        }
        currentFocusStatus = status
        
        withContext(Dispatchers.Main) {
             if (status.isActive) {
                 val remaining = status.endTime - System.currentTimeMillis()
                 binding.tvFocusCardTitle.text = status.title
                 
                 // Check if Tapasya is running
                 val data = savedPreferencesLoader.getFocusModeData()
                 if (data.isTapasyaTriggered) {
                     binding.tvFocusCardStatus.text = "Active until stopped"
                 } else if (remaining > 60_000) {
                     // Minute-only precision
                     val mins = (remaining / 60000) + 1 // Ceiling
                     binding.tvFocusCardStatus.text = "Ends in ~ $mins min"
                 } else if (remaining > 0) {
                     binding.tvFocusCardStatus.text = "Ends in < 1 min"
                 } else {
                     binding.tvFocusCardStatus.text = "Completing..."
                 }
                 
                 // Button Text
                 if (status.type == FocusType.MANUAL_FOCUS) {
                     val data = savedPreferencesLoader.getFocusModeData()
                     if (data.isTapasyaTriggered) {
                         binding.focusMode.text = "Tapasya Running"
                         binding.focusMode.setBackgroundColor(getColor(com.neubofy.reality.R.color.purple_500)) // Use a distinct color
                     } else {
                         binding.focusMode.text = "Stop Session"
                         binding.focusMode.setBackgroundColor(getColor(com.neubofy.reality.R.color.error_color))
                     }
                 } else {
                     binding.focusMode.text = "Locked by Schedule"
                     binding.focusMode.setBackgroundColor(getColor(com.neubofy.reality.R.color.gray_dark))
                 }
             } else {
                 binding.tvFocusCardTitle.text = "Focus Session"
                 binding.tvFocusCardStatus.text = "Ready to Start"
                 binding.focusMode.text = "Start Focusing"
                 binding.focusMode.setBackgroundColor(getColor(com.neubofy.reality.R.color.teal_200))
             }
             
             // Sync widget with current status
             com.neubofy.reality.widget.FocusWidgetProvider.updateAllWidgets(this@MainActivity)
        }
    }



    private fun setupActivityLaunchers() {
        selectFocusModeUnblockedAppsLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK) {
                    val selectedApps = result.data?.getStringArrayListExtra("SELECTED_APPS")
                    if (selectedApps != null) {
                        savedPreferencesLoader.saveFocusModeSelectedApps(selectedApps)
                        sendRefreshRequest(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE)
                    }
                }
            }

        addAutoFocusHoursActivity =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                sendRefreshRequest(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE)
            }
    }

    private fun setupClickListeners() {
        // Emergency Mode
        binding.btnEmergencyClick.setOnClickListener {
            scope.launch { showEmergencyDialog() }
        }
        // Blocklist
        binding.blocklistCard.setOnClickListener {
             startActivity(Intent(this, UnifiedBlocklistActivity::class.java), options.toBundle())
        }
        
        // Bedtime Mode
        binding.cardBedtime.setOnClickListener {
             startActivity(Intent(this, BedtimeActivity::class.java), options.toBundle())
        }
        
        // Schedules
        binding.schedules.setOnClickListener {
            val intent = Intent(this, ScheduleListActivity::class.java)
            startActivity(intent, options.toBundle())
        }
        
        // Menu Button - Show menu with options
        binding.btnInfo.setOnClickListener { view ->
            val popup = android.widget.PopupMenu(this, view)
            popup.menu.add(0, 1, 0, "📖 User Manual")
            popup.menu.add(0, 5, 1, "❤️ Health Dashboard") // Added Health Dashboard
            popup.menu.add(0, 2, 2, "ℹ️ App Status & Rules")
            popup.menu.add(0, 3, 3, "📱 About Reality")
            
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        // Open User Manual website
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://neubofyreality.vercel.app"))
                        startActivity(intent)
                        true
                    }
                    5 -> {
                        startActivity(Intent(this, HealthDashboardActivity::class.java))
                        true
                    }
                    2 -> {
                        showRulesDialog()
                        true
                    }
                    3 -> {
                        startActivity(Intent(this, AboutActivity::class.java))
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }
        
        // Settings Button (Header)
        binding.btnActiveBlocks.setOnClickListener {
            startActivity(Intent(this, ActiveBlocksActivity::class.java))
        }
        
        binding.btnReminders.setOnClickListener {
            startActivity(Intent(this, ReminderActivity::class.java))
        }
        
        binding.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java), options.toBundle())
        }
        
        // Digital Life - Statistics with 7-Day Graphs
        binding.cardUsageLimit.setOnClickListener {
             startActivity(Intent(this, StatisticsActivity::class.java), options.toBundle())
        }
        
        // App Limits Card - Separate Page
        binding.cardAppLimits.setOnClickListener {
             val intent = Intent(this, AppLimitsActivity::class.java)
             startActivity(intent, options.toBundle())
        }
        
        // Group Limits Card - Separate Page
        binding.cardGroupLimits.setOnClickListener {
             val intent = Intent(this, GroupLimitsActivity::class.java)
             startActivity(intent, options.toBundle())
        }
        
        // Terminal Clear
        binding.btnTerminalClear.setOnClickListener {
            com.neubofy.reality.utils.TerminalLogger.clear()
        }

        // Focus Mode Button - FIXED
        binding.focusMode.setOnClickListener {
            val status = currentFocusStatus
            if (status != null && status.isActive) {
                // Stop Session
                if (status.type == FocusType.MANUAL_FOCUS) {
                    val data = savedPreferencesLoader.getFocusModeData()
                    
                    // Tapasya Guard
                    if (data.isTapasyaTriggered) {
                        Toast.makeText(this, "Active Tapasya session running. Stop it from the Tapasya Clock.", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    
                    data.isTurnedOn = false
                    savedPreferencesLoader.saveFocusModeData(data)
                    sendRefreshRequest(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE)
                    Toast.makeText(this, "Focus Session Stopped", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Cannot stop scheduled session", Toast.LENGTH_SHORT).show()
                }
            } else {
                // Start Session
                StartFocusMode(savedPreferencesLoader) {
                    // Refresh triggered by dialog
                }.show(supportFragmentManager, "StartFocusMode")
            }
        }
        
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.nav_home
        
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> true
                R.id.nav_tasks -> {
                    // Open Google Tasks App
                    val launchIntent = packageManager.getLaunchIntentForPackage("com.google.android.apps.tasks")
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                    } else {
                        // Redirect to Play Store or show toast
                        Toast.makeText(this, "Google Tasks not installed", Toast.LENGTH_SHORT).show()
                    }
                    false // Don't select
                }
                R.id.nav_calendar -> {
                     // Open Google Calendar App
                    val launchIntent = packageManager.getLaunchIntentForPackage("com.google.android.calendar")
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                    } else {
                        Toast.makeText(this, "Google Calendar not installed", Toast.LENGTH_SHORT).show()
                    }
                    false
                }
                R.id.nav_nightly -> {
                    val intent = Intent(this, NightlyActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    startActivity(intent)
                    false // Don't select, let NightlyActivity handle its own nav
                }
                R.id.nav_tapasya -> {
                    val intent = Intent(this, TapasyaActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    startActivity(intent)
                    false
                }
                else -> false
            }
        }
        
        // Setup AI Chat FAB
        setupAiChatFab()
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private fun setupAiChatFab() {
        var dX = 0f
        var dY = 0f
        var startX = 0f
        var startY = 0f
        val clickThreshold = 10
        
        binding.fabAiChat.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    dX = view.x - event.rawX
                    dY = view.y - event.rawY
                    startX = event.rawX
                    startY = event.rawY
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val newX = event.rawX + dX
                    val newY = event.rawY + dY
                    
                    // Bounds checking
                    val parent = view.parent as? android.view.View
                    if (parent != null) {
                        val maxX = parent.width - view.width.toFloat()
                        val maxY = parent.height - view.height.toFloat()
                        view.x = newX.coerceIn(0f, maxX)
                        view.y = newY.coerceIn(0f, maxY)
                    } else {
                        view.x = newX
                        view.y = newY
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP -> {
                    val endX = event.rawX
                    val endY = event.rawY
                    val diffX = kotlin.math.abs(endX - startX)
                    val diffY = kotlin.math.abs(endY - startY)
                    
                    if (diffX < clickThreshold && diffY < clickThreshold) {
                        // It's a click
                        view.performClick()
                    }
                    true
                }
                else -> false
            }
        }
        
        binding.fabAiChat.setOnClickListener {
            startActivity(Intent(this, AIChatActivity::class.java))
        }
    }
    
    private suspend fun showEmergencyDialog() {
        if (!isBlockingActive()) {
            Toast.makeText(this, "Emergency Mode is only available when a blocking session is active.", Toast.LENGTH_LONG).show()
            return
        }
    
        val emergencyData = savedPreferencesLoader.getEmergencyData()
        
        // Check if already in emergency session
        if (emergencyData.currentSessionEndTime > System.currentTimeMillis()) {
            val remainingMins = (emergencyData.currentSessionEndTime - System.currentTimeMillis()) / 60000
            Toast.makeText(this, "Emergency mode active for $remainingMins more minutes", Toast.LENGTH_SHORT).show()
            return
        }
        
        // Reset daily if needed
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = emergencyData.lastResetDate
        val lastResetDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        
        calendar.timeInMillis = System.currentTimeMillis()
        val currentDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        
        if (currentDay != lastResetDay) {
            emergencyData.usesRemaining = Constants.EMERGENCY_MAX_USES
            emergencyData.lastResetDate = System.currentTimeMillis()
            savedPreferencesLoader.saveEmergencyData(emergencyData)
        }
        
        // Check if uses remaining
        if (emergencyData.usesRemaining <= 0) {
            Toast.makeText(this, "No emergency breaks left today. Resets at midnight.", Toast.LENGTH_LONG).show()
            return
        }
        
        // Show dialog
        val dialogBinding = com.neubofy.reality.databinding.DialogEmergencyModeBinding.inflate(layoutInflater)
        dialogBinding.tvRemaining.text = emergencyData.usesRemaining.toString()
        
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }
        
        dialogBinding.btnActivate.setOnClickListener {
            // Activate emergency mode
            emergencyData.usesRemaining--
            emergencyData.currentSessionEndTime = System.currentTimeMillis() + Constants.EMERGENCY_DURATION_MS
            savedPreferencesLoader.saveEmergencyData(emergencyData)
            
            // Update blocker
            val intent = Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE)
            intent.setPackage(packageName)
            sendBroadcast(intent)
            
            Toast.makeText(this, "Emergency mode active for 5 minutes", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
            updateEmergencyUI()
        }
        
        dialog.show()
    }
    
    private fun showRulesDialog() {
        val strictData = savedPreferencesLoader.getStrictModeData()
        val emergencyData = savedPreferencesLoader.getEmergencyData()
        
        val sb = StringBuilder()
        
        sb.append("Current Status:\n\n")

        // Strict Mode
        sb.append("• Strict Mode: ")
        if (strictData.isEnabled) {
            sb.append("Active (${strictData.modeType})\n")
            if (strictData.timerEndTime > System.currentTimeMillis()) {
               val remaining = (strictData.timerEndTime - System.currentTimeMillis()) / 60000
               sb.append("  (Locked for ${remaining}m)\n")
            }
        } else {
            sb.append("Inactive\n")
        }
        sb.append("  (Anti-Uninstall: ${if (strictData.isAntiUninstallEnabled) "ON" else "OFF"})\n\n")
        
        // Emergency Access
        sb.append("• Emergency Access: ")
        sb.append("${emergencyData.usesRemaining} / ${Constants.EMERGENCY_MAX_USES} uses remaining today\n\n")
        
        sb.append("Quick Tips:\n")
        sb.append("• Maintenance Window: 00:00 - 00:10 daily (Settings unlocked)\n")
        sb.append("• Emergency Mode: Grants 5 minutes of access.\n")

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Reality Status")
            .setMessage(sb.toString())
            .setPositiveButton("OK", null)
            .show()
    }
    
    private fun updateEmergencyUI() {
        val emergencyData = savedPreferencesLoader.getEmergencyData()
        
        // Reset daily if needed
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = emergencyData.lastResetDate
        val lastResetDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        
        calendar.timeInMillis = System.currentTimeMillis()
        val currentDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        
        if (currentDay != lastResetDay) {
            emergencyData.usesRemaining = Constants.EMERGENCY_MAX_USES
            emergencyData.lastResetDate = System.currentTimeMillis()
            savedPreferencesLoader.saveEmergencyData(emergencyData)
        }
        
        binding.tvEmergencyRemaining.text = "${emergencyData.usesRemaining} breaks remaining today"
    }
    
    private fun updateBedtimeUI() {
        val bedtimeData = savedPreferencesLoader.getBedtimeData()
        if (bedtimeData.isEnabled) {
            val startHour = bedtimeData.startTimeInMins / 60
            val startMin = bedtimeData.startTimeInMins % 60
            val endHour = bedtimeData.endTimeInMins / 60
            val endMin = bedtimeData.endTimeInMins % 60
            binding.tvBedtimeStatus.text = String.format("Active %02d:%02d - %02d:%02d", startHour, startMin, endHour, endMin)
        } else {
            binding.tvBedtimeStatus.text = "Not configured"
        }
    }
    
    private fun updateUsageLimitUI() {
        scope.launch(Dispatchers.IO) {
            val db = com.neubofy.reality.data.db.AppDatabase.getDatabase(applicationContext)
            val groups = db.appGroupDao().getAllGroups()
            val text = if (groups.isEmpty()) "Not configured" else "${groups.size} active groups"
            
            withContext(Dispatchers.Main) {
                binding.tvUsageStatus.text = text
                if (groups.isNotEmpty()) {
                     binding.tvUsageStatus.setTextColor(getColor(R.color.teal_200))
                } else {
                     binding.tvUsageStatus.setTextColor(getColor(R.color.gray_light))
                }
            }
        }
    }
    
    private fun loadReflectionCard() {
        scope.launch(Dispatchers.IO) {
            try {
                val db = com.neubofy.reality.data.db.AppDatabase.getDatabase(applicationContext)
                
                // Get today's calendar events from device calendar (synced via CalendarRepository)
                val calendarRepo = com.neubofy.reality.data.repository.CalendarRepository(applicationContext)
                val todayEvents = calendarRepo.getEventsForToday()
                
                // Calculate total planned time from today's calendar events (in minutes)
                var totalPlannedMinutes = 0L
                for (event in todayEvents) {
                    val durationMs = event.endTime - event.startTime
                    totalPlannedMinutes += durationMs / 60000
                }
                
                // Get today's Tapasya sessions for effective study time
                val today = java.time.LocalDate.now()
                val startOfDay = today.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                val endOfDay = today.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                
                val todaySessions = db.tapasyaSessionDao().getSessionsForDay(startOfDay, endOfDay)
                var totalEffectiveMinutes = 0L
                for (session in todaySessions) {
                    totalEffectiveMinutes += session.effectiveTimeMs / 60000
                }
                
                // Calculate progress percentage
                val progressPercent = if (totalPlannedMinutes > 0) {
                    ((totalEffectiveMinutes * 100) / totalPlannedMinutes).toInt().coerceIn(0, 100)
                } else {
                    0
                }
                
                // Load XP/streak/level using XPManager (Use Projected for consistency)
                // XPManager.resetDailyXP(applicationContext) -- Removed, logic in getXPBreakdown
                val xpBreakdown = com.neubofy.reality.utils.XPManager.getProjectedDailyXP(applicationContext)
                val levelName = com.neubofy.reality.utils.XPManager.getLevelName(applicationContext, xpBreakdown.level)
                
                withContext(Dispatchers.Main) {
                    // Update XP/streak/level
                    val totalXP = com.neubofy.reality.utils.XPManager.getTotalXP(applicationContext)
                    binding.tvTotalXp.text = totalXP.toString()
                    binding.tvTodayXp.text = if (xpBreakdown.totalDailyXP > 0) "+${xpBreakdown.totalDailyXP}" else "+${xpBreakdown.tapasyaXP}"
                    binding.tvStreak.text = xpBreakdown.streak.toString()
                    binding.tvLevel.text = xpBreakdown.level.toString()
                    
                    // Update study time progress
                    binding.tvStudyProgress.text = "${totalEffectiveMinutes} / ${totalPlannedMinutes} min"
                    binding.progressStudyTime.progress = progressPercent
                    
                    // View Progress button click
                    binding.btnViewProgress.setOnClickListener {
                        startActivity(Intent(this@MainActivity, ReflectionDetailActivity::class.java))
                    }
                }
            } catch (e: Exception) {
                com.neubofy.reality.utils.TerminalLogger.log("Reflection card error: ${e.message}")
            }
        }
    }

    private fun checkPermissions() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, com.neubofy.reality.receivers.AdminLockReceiver::class.java)
        isDeviceAdminOn = dpm.isAdminActive(adminComponent)

        val antiUninstallPrefs = getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
        isAntiUninstallOn = antiUninstallPrefs.getBoolean("is_anti_uninstall_on", false)
    }

    private fun checkAndRequestNextPermission() {
        // 1. Accessibility Service (Highest Priority for Blocking)
        if (!isAccessibilityServiceEnabled(AppBlockerService::class.java)) {
            makeAccessibilityInfoDialog("Reality Blocker", AppBlockerService::class.java)
            return
        }
        
        // 2. Usage Access (Required for Limits/Statistics)
        if (!com.neubofy.reality.utils.UsageUtils.hasUsageStatsPermission(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Usage Access Required")
                .setMessage("To track time and enforce limits, Reality needs Usage Access.")
                .setPositiveButton("Grant") { _, _ ->
                     startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                .setCancelable(false)
                .show()
            return
        }
        
        // 3. Overlay Permission (Required for Blocking Screen)
        if (!Settings.canDrawOverlays(this)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Overlay Permission Required")
                .setMessage("To show the block screen, Reality needs 'Display over other apps' permission.")
                .setPositiveButton("Grant") { _, _ ->
                     startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
                }
                .setCancelable(false)
                .show()
            return
        }

        // 4. Battery Optimization (Critical for Stability)
        val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
             MaterialAlertDialogBuilder(this)
                .setTitle("Unrestricted Battery Access")
                .setMessage("To prevent the blocker from stopping randomly, Reality needs 'Unrestricted' battery access.\n\nTap 'Grant' -> Select 'All apps' -> Find 'Reality' -> Choose 'Don't optimize'.")
                .setPositiveButton("Grant") { _, _ ->
                     val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                     intent.data = android.net.Uri.parse("package:$packageName")
                     try {
                        startActivity(intent)
                     } catch(e: Exception) {
                        // Fallback to generic settings if direct request fails
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                     }
                }
                .setCancelable(false)
                .show()
            return
        }

        // 5. Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
             if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                 notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS, options)
             }
        }
    }

    private fun isAccessibilityServiceEnabled(service: Class<out AccessibilityService>): Boolean {
        val expectedComponentName = ComponentName(this, service)
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(expectedComponentName.flattenToString()) == true
    }

    private fun makeAccessibilityInfoDialog(title: String, service: Class<out AccessibilityService>) {
        val binding = DialogPermissionInfoBinding.inflate(layoutInflater)
        binding.title.text = "Enable $title"
        binding.desc.text = "To block apps effectively, Reality needs Accessibility permission.\n\n1. Tap 'Enable'.\n2. Find 'Reality' or 'Installed Apps'.\n3. Turn ON 'Reality Blocker'."
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(binding.root)
            .create()

        binding.btnAccept.setOnClickListener {
            openAccessibilityServiceScreen(service)
            dialog.dismiss()
        }
        binding.btnReject.text = "Later"
        binding.btnReject.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun makeDeviceAdminPermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Device Admin Required")
            .setMessage("Reality needs Device Admin permission for Anti-Uninstall to work properly.")
            .setPositiveButton("Enable") { _, _ ->
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this, com.neubofy.reality.receivers.AdminLockReceiver::class.java))
                intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Reality Anti-Uninstall Protection")
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createFocusModeShortcut() {
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(this)) {
            val shortcut = ShortcutInfoCompat.Builder(this, "focus_mode_shortcut")
                .setShortLabel("Focus Mode")
                .setIcon(IconCompat.createWithResource(this, R.drawable.baseline_start_24))
                .setIntent(Intent(this, ShortcutActivity::class.java).setAction(Intent.ACTION_VIEW))
                .build()
            ShortcutManagerCompat.requestPinShortcut(this, shortcut, null)
        }
    }

    private fun sendRefreshRequest(action: String) {
        val intent = Intent(action)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }

    private fun openAccessibilityServiceScreen(cls: Class<*>) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            val componentName = ComponentName(this, cls)
            intent.putExtra(":settings:fragment_args_key", componentName.flattenToString())
            startActivity(intent, options.toBundle())
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    @SuppressLint("ApplySharedPref")
    private fun makeRemoveAntiUninstallDialog() {
        val data = savedPreferencesLoader.getStrictModeData()
        
        if (!data.isEnabled) {
             Toast.makeText(this, "Strict Mode is not active", Toast.LENGTH_SHORT).show()
             return
        }
        
        if (data.modeType == Constants.StrictModeData.MODE_TIMER) {
            val remaining = data.timerEndTime - System.currentTimeMillis()
            if (remaining > 0) {
                 val hours = remaining / (1000 * 60 * 60)
                 val mins = (remaining % (1000 * 60 * 60)) / (1000 * 60)
                 MaterialAlertDialogBuilder(this)
                     .setTitle("Strict Mode Active")
                     .setMessage("Locked for $hours hours $mins minutes.")
                     .setPositiveButton("OK", null)
                     .show()
            } else {
                 // Expired - Turn Off
                 data.isEnabled = false
                 savedPreferencesLoader.saveStrictModeData(data)
                 sendRefreshRequest(AppBlockerService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL)
                 Snackbar.make(binding.root, "Strict Mode Expired & Removed", Snackbar.LENGTH_SHORT).show()
                 checkPermissions()
            }
        } else if (data.modeType == Constants.StrictModeData.MODE_PASSWORD) {
            val dialogBinding = DialogRemoveAntiUninstallBinding.inflate(layoutInflater)
            MaterialAlertDialogBuilder(this)
                .setTitle("Remove Strict Mode")
                .setView(dialogBinding.root)
                .setPositiveButton("Unlock") { _, _ ->
                    val entered = dialogBinding.password.text.toString()
                    // Hash the entered password and compare to stored hash
                    val enteredHash = hashPassword(entered)
                    if (enteredHash == data.passwordHash) {
                        data.isEnabled = false
                        savedPreferencesLoader.saveStrictModeData(data)
                        sendRefreshRequest(AppBlockerService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL)
                        Snackbar.make(binding.root, "Strict Mode Removed", Snackbar.LENGTH_SHORT).show()
                        checkPermissions()
                    } else {
                        Toast.makeText(this, "Incorrect password", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNeutralButton("Forgot Password") { _, _ ->
                     // Recovery: Switch to 24h Timer
                     data.modeType = Constants.StrictModeData.MODE_TIMER
                     data.timerEndTime = System.currentTimeMillis() + (24 * 60 * 60 * 1000L)
                     savedPreferencesLoader.saveStrictModeData(data)
                     
                     MaterialAlertDialogBuilder(this)
                         .setTitle("Recovery Mode")
                         .setMessage("Strict Mode switched to 24-Hour Timer. You can unlock after 24 hours.")
                         .setPositiveButton("OK", null)
                         .show()
                     sendRefreshRequest(AppBlockerService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL)
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private suspend fun isBlockingActive(): Boolean {
        // 1. Check Main Blocking Modes (Focus, Schedule, Calendar, Bedtime)
        // using FocusStatusManager which simplifies and includes Calendar (Fixing missing check)
        val focusStatus = com.neubofy.reality.utils.FocusStatusManager(this).getCurrentStatus()
        if (focusStatus.isActive) return true
        
        // 2. Check DB Limits / Groups (Time-based active periods)
        val db = com.neubofy.reality.data.db.AppDatabase.getDatabase(applicationContext)
        val groups = db.appGroupDao().getAllGroups()
        groups.forEach { 
             if (com.neubofy.reality.utils.TimeTools.isActivePeriod(it.activePeriodsJson)) return true
        }
        val limits = db.appLimitDao().getAllLimits()
        limits.forEach {
             if (com.neubofy.reality.utils.TimeTools.isActivePeriod(it.activePeriodsJson)) return true
        }
        
        return false
    }


    

    private fun updateThemeVisuals() {
        val cards = listOf(
            binding.cardEmergency,
            binding.cardFocusMode,
            binding.cardUsageLimit,
            binding.blocklistCard,
            binding.cardAppLimits,
            binding.cardGroupLimits,
            binding.schedules,
            binding.cardBedtime
        )
        cards.forEach { ThemeManager.applyCardAppearance(it) }
        
        // Also update background pattern alpha if needed? No, handled by XML usually.
        // But if AMOLED, pattern view should be hidden?
        val patternView = binding.root.getChildAt(1) // Assuming 2nd view is pattern
        if (ThemeManager.isAmoledMode(this) && isSystemNight()) {
            patternView.visibility = View.GONE
        } else {
            // Restore visibility based on Pattern preference?
            val pattern = ThemeManager.getBackgroundPattern(this)
            patternView.visibility = if (pattern == ThemeManager.BackgroundPattern.NONE) View.GONE else View.VISIBLE
            // Update drawable if needed
            if (pattern != ThemeManager.BackgroundPattern.NONE) {
                 patternView.setBackgroundResource(pattern.drawableResId)
            }
        }
    }
    
    private fun isSystemNight(): Boolean {
        return (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
    
    private fun hashPassword(password: String): String {
        return com.neubofy.reality.utils.SecurityUtils.hashPassword(password)
    }
    
    private fun updateTerminalLogVisibility() {
        val appPrefs = getSharedPreferences("reality_prefs", MODE_PRIVATE)
        val showTerminalLog = appPrefs.getBoolean("show_terminal_log", true) // Default ON
        
        // Find the terminal log card parent (it's inside ScrollView > LinearLayout)
        // The terminal card is at the bottom of the layout
        binding.tvTerminalLog.parent?.parent?.parent?.let { terminalCard ->
            if (terminalCard is android.view.View) {
                terminalCard.visibility = if (showTerminalLog) android.view.View.VISIBLE else android.view.View.GONE
            }
        }
    }

    private fun checkAndShowSleepVerification() {
        val loader = com.neubofy.reality.utils.SavedPreferencesLoader(this)
        if (!loader.isSmartSleepEnabled()) return

        lifecycleScope.launch {
            val healthManager = com.neubofy.reality.health.HealthManager(this@MainActivity)
            val today = LocalDate.now()
            
            if (healthManager.isSleepSyncedToday(today)) return@launch

            val session = com.neubofy.reality.utils.SleepInferenceHelper.inferSleepSession(this@MainActivity, today)
            if (session != null) {
                showSleepVerificationDialog(session.first, session.second)
            }
        }
    }

    private fun showSleepVerificationDialog(startTime: Instant, endTime: Instant) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_smart_sleep_confirm, null)
        val tvStart = dialogView.findViewById<TextView>(R.id.tv_sleep_start)
        val tvEnd = dialogView.findViewById<TextView>(R.id.tv_sleep_end)
        
        val timeFormatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
        tvStart.text = timeFormatter.format(startTime)
        tvEnd.text = timeFormatter.format(endTime)

        val dialog = MaterialAlertDialogBuilder(this, R.style.GlassDialog)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        dialogView.findViewById<android.view.View>(R.id.btn_correct).setOnClickListener {
            lifecycleScope.launch {
                val healthManager = com.neubofy.reality.health.HealthManager(this@MainActivity)
                // 1. Delete existing
                healthManager.deleteSleepSessions(startTime.minus(java.time.Duration.ofHours(2)), endTime.plus(java.time.Duration.ofHours(2)))
                // 2. Write new
                healthManager.writeSleepSession(startTime, endTime)
                
                Toast.makeText(this@MainActivity, "Sleep data synced", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialogView.findViewById<android.view.View>(R.id.btn_wrong).setOnClickListener {
            dialog.dismiss()
            showSleepEditDialog(startTime, endTime)
        }

        dialogView.findViewById<android.view.View>(R.id.btn_still_sleeping).setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showSleepEditDialog(oldStart: Instant, oldEnd: Instant) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_smart_sleep_edit, null)
        val pickerStart = dialogView.findViewById<android.widget.TimePicker>(R.id.time_picker_start)
        val pickerEnd = dialogView.findViewById<android.widget.TimePicker>(R.id.time_picker_end)

        pickerStart.setIs24HourView(true)
        pickerEnd.setIs24HourView(true)

        val startCal = java.util.Calendar.getInstance().apply { timeInMillis = oldStart.toEpochMilli() }
        val endCal = java.util.Calendar.getInstance().apply { timeInMillis = oldEnd.toEpochMilli() }

        pickerStart.hour = startCal.get(java.util.Calendar.HOUR_OF_DAY)
        pickerStart.minute = startCal.get(java.util.Calendar.MINUTE)
        pickerEnd.hour = endCal.get(java.util.Calendar.HOUR_OF_DAY)
        pickerEnd.minute = endCal.get(java.util.Calendar.MINUTE)

        MaterialAlertDialogBuilder(this, R.style.GlassDialog)
            .setView(dialogView)
            .setTitle("✏️ Adjust Sleep Time")
            .setPositiveButton("Save & Sync") { _, _ ->
                // Smart Inference logic (mirrors HealthDashboardActivity)
                val today = LocalDate.now()
                val startHour = pickerStart.hour
                val endHour = pickerEnd.hour
                
                val startDate = if (startHour > 14) today.minusDays(1) else today
                
                var newStart = startDate.atTime(startHour, pickerStart.minute).atZone(ZoneId.systemDefault()).toInstant()
                var newEnd = startDate.atTime(endHour, pickerEnd.minute).atZone(ZoneId.systemDefault()).toInstant()
                
                if (newEnd.isBefore(newStart)) {
                    newEnd = newEnd.plus(java.time.Duration.ofDays(1))
                }
                
                if (newStart.isAfter(Instant.now())) {
                     newStart = newStart.minus(java.time.Duration.ofDays(1))
                     newEnd = newEnd.minus(java.time.Duration.ofDays(1))
                }

                lifecycleScope.launch {
                    val healthManager = com.neubofy.reality.health.HealthManager(this@MainActivity)
                    // 1. Delete existing
                    healthManager.deleteSleepSessions(newStart.minus(java.time.Duration.ofHours(12)), newEnd.plus(java.time.Duration.ofHours(12)))
                    // 2. Write new
                    healthManager.writeSleepSession(newStart, newEnd)
                    
                    Toast.makeText(this@MainActivity, "Adjusted sleep data synced", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel") { _, _ ->
                showSleepVerificationDialog(oldStart, oldEnd)
            }
            .show()
    }
}
