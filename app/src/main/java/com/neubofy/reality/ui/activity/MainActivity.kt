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
import java.util.Calendar

import kotlinx.coroutines.*
import com.neubofy.reality.utils.FocusStatusManager
import com.neubofy.reality.utils.FocusType
import com.neubofy.reality.utils.TimeTools
import com.neubofy.reality.utils.ThemeManager

class MainActivity : AppCompatActivity() {



    private lateinit var binding: ActivityMainBinding
    private lateinit var selectFocusModeUnblockedAppsLauncher: ActivityResultLauncher<Intent>
    private lateinit var addAutoFocusHoursActivity: ActivityResultLauncher<Intent>

    private val savedPreferencesLoader = SavedPreferencesLoader(this)
    private lateinit var options: ActivityOptionsCompat
    private var isDeviceAdminOn = false
    private var isAntiUninstallOn = false
    private var isGeneralSettingsOn = false
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

        // Startup permission check removed as per user request. 
        // Permissions are now requested on-demand.
        
        // Observe Terminal Logs
        scope.launch {
            com.neubofy.reality.utils.TerminalLogger.logs.collect { logText ->
                binding.tvTerminalLog.text = logText
            }
        }
        
        scheduleKeepAliveWorker()
    }

    override fun onResume() {
        super.onResume()
        checkAndRequestNextPermission()
        startStatusUpdater()
        updateEmergencyUI()
        updateUsageLimitUI()
        updateGreeting()
        updateThemeVisuals()
        

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

    override fun onPause() {
        super.onPause()
        statusUpdaterJob?.cancel()
    }

    private fun startStatusUpdater() {
        statusUpdaterJob?.cancel()
        statusUpdaterJob = scope.launch {
            while (isActive) {
                updateFocusStatus()
                delay(1000)
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
                 if (remaining > 0) {
                     val timeStr = com.neubofy.reality.utils.TimeTools.formatTime(remaining, showSeconds = true)
                     binding.tvFocusCardStatus.text = "Ends in $timeStr"
                 } else {
                     binding.tvFocusCardStatus.text = "Completing..."
                 }
                 
                 // Button Text
                 if (status.type == FocusType.MANUAL_FOCUS) {
                     binding.focusMode.text = "Stop Session"
                     binding.focusMode.setBackgroundColor(getColor(com.neubofy.reality.R.color.error_color))
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
        
        // Info Button - Show menu with User Manual and App Info
        binding.btnInfo.setOnClickListener { view ->
            val popup = android.widget.PopupMenu(this, view)
            popup.menu.add(0, 1, 0, "📖 User Manual")
            popup.menu.add(0, 2, 1, "ℹ️ App Status & Rules")
            popup.menu.add(0, 3, 2, "📱 About Reality")
            
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        // Open User Manual website
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://neubofyreality.vercel.app"))
                        startActivity(intent)
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

    private fun checkPermissions() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, com.neubofy.reality.receivers.AdminLockReceiver::class.java)
        isDeviceAdminOn = dpm.isAdminActive(adminComponent)

        val isAppBlockerOn = isAccessibilityServiceEnabled(AppBlockerService::class.java)
        isGeneralSettingsOn = isAccessibilityServiceEnabled(AppBlockerService::class.java)

        val antiUninstallPrefs = getSharedPreferences("anti_uninstall", Context.MODE_PRIVATE)
        isAntiUninstallOn = antiUninstallPrefs.getBoolean("is_anti_uninstall_on", false)

        // New UI doesn't have the old chips/warnings - removed updateUI call
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
                    // Verify hash (Simple check for now or hash logic)
                    // Assuming data.passwordHash stores the actual password for MVP or hash
                    if (entered == data.passwordHash) {
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
                     // Recovery: Switch to 12h Timer
                     data.modeType = Constants.StrictModeData.MODE_TIMER
                     data.timerEndTime = System.currentTimeMillis() + (12 * 60 * 60 * 1000L)
                     savedPreferencesLoader.saveStrictModeData(data)
                     
                     MaterialAlertDialogBuilder(this)
                         .setTitle("Recovery Mode")
                         .setMessage("Strict Mode switched to 12-Hour Timer. You can unlock after 12 hours.")
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

    private fun scheduleKeepAliveWorker() {
        val request = androidx.work.PeriodicWorkRequest.Builder(
            com.neubofy.reality.workers.KeepAliveWorker::class.java,
            15,
            java.util.concurrent.TimeUnit.MINUTES
        ).build()

        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "RealityKeepAlive",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            request
        )
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
}