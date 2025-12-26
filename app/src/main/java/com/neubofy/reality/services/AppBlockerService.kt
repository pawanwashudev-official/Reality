package com.neubofy.reality.services

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.neubofy.reality.Constants
import com.neubofy.reality.blockers.RealityBlocker
import com.neubofy.reality.utils.getCurrentKeyboardPackageName
import com.neubofy.reality.utils.getDefaultLauncherPackageName

class AppBlockerService : BaseBlockingService() {

    companion object {
        const val INTENT_ACTION_REFRESH_FOCUS_MODE = "com.neubofy.reality.refresh.focus_mode"
        const val INTENT_ACTION_BLOCK_APP_COOLDOWN = "com.neubofy.reality.refresh.appblocker.cooldown"
    }

    private var warningConfig = Constants.WarningData()
    private val blocker = RealityBlocker()
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private var lastPackage = ""
    
    // Timers
    private var lastUrlCheckTime = 0L
    private var lastBackgroundUpdate = 0L
    private var scanEventsCount = 0
    
    // Battey Optimized 60s Watchdog
    private val BROWSER_CHECK_INTERVAL = 60_000L 
    private var browserCheckRunnable: Runnable? = null
    private var currentBrowserPackage: String? = null
    
    // Anti-bypass tracking
    private var lastBlockedPackage: String = ""
    private var lastBlockTime: Long = 0L
    
    // PERSISTENT blocked packages for current session (prevents 2-min bypass)
    private val sessionBlockedPackages = mutableSetOf<String>()

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                INTENT_ACTION_REFRESH_FOCUS_MODE -> refreshSettings()
                INTENT_ACTION_BLOCK_APP_COOLDOWN -> {
                    val packageName = intent.getStringExtra("result_id") ?: return
                    val duration = intent.getIntExtra("selected_time", 0)
                    if (duration > 0) {
                        val endTime = System.currentTimeMillis() + duration
                        blocker.putCooldown(packageName, endTime)
                        setUpForcedRefreshChecker(packageName, System.currentTimeMillis() + duration)
                    }
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    stopBrowserCheckTimer() // Save battery when screen off
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    lastBackgroundUpdate = 0 
                    // Resume checking if needed
                    if (isWebsiteBlockActive()) {
                         startBrowserCheckTimer()
                    }
                }
            }
        }
    }
    
    private var isScreenOn = true
    private var isBlockingActive = true

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isScreenOn) return
        if (!isBlockingActive) return
        scanEventsCount++
        
        if (event == null) return
        val eventType = event.eventType
        val packageName = event.packageName?.toString() ?: return
        if (packageName == getPackageName()) return

        // 1. Browser Special Handling (Watchdog Trigger)
        val isBrowser = com.neubofy.reality.utils.UrlDetector.isBrowser(packageName)
        
        if (isBrowser) {
             // Only run watchdog if we are actually in a browser AND blocking is needed
             if (isWebsiteBlockActive()) {
                 currentBrowserPackage = packageName 
                 startBrowserCheckTimer()
                 resetWatchdogRampUp()
             }
        } else {
             // We left the browser - STOP the watchdog to save battery
             if (browserCheckRunnable != null) {
                 com.neubofy.reality.utils.TerminalLogger.log("WATCHDOG: Paused (Non-browser app: $packageName)")
                 stopBrowserCheckTimer()
                 currentBrowserPackage = null
             }
        }
        
        // Strict Optimization: Ignore high-frequency events below this line
        if (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
             if (!sessionBlockedPackages.contains(packageName)) return
        }
        
        if (eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) return
        
        // Session Block Check (Fast path)
        if (sessionBlockedPackages.contains(packageName)) {
             handleBlock(packageName, "Session Block")
             return
        }
        
        // PERIODIC BACKGROUND UPDATE
        val now = System.currentTimeMillis()
        if (now - lastBackgroundUpdate > 30000) {
            lastBackgroundUpdate = now
            performBackgroundUpdates()
        }
        
        // SECURITY SHIELD: Anti-Uninstall & Anti-TimeCheat
        // Only runs when Settings is open (Efficient)
        if (packageName == "com.android.settings") {
            try {
               // Use Cached Data (Access O(1)) - removed IO call
               val strictData = blocker.strictModeData
               
               if (strictData.isEnabled && !packageManager.isSafeMode) {
                   val root = rootInActiveWindow
                   if (root != null) {
                        val text = extractText(root).lowercase()
                        
                        // 1. Time Protection (Prevents changing time to bypass locks)
                        // Trigger: "Date & time" or "Automatic date" visible on screen
                        if (strictData.isTimeCheatProtectionEnabled) {
                            val isTimeSettings = text.contains("date & time") || 
                                               text.contains("automatic date") || 
                                               text.contains("set time")
                            
                            if (isTimeSettings) {
                                 performGlobalAction(GLOBAL_ACTION_BACK)
                                 Toast.makeText(this, "Time Settings locked by Strict Mode", Toast.LENGTH_SHORT).show()
                                 return
                            }
                        }

                        // 2. Anti-Uninstall (Prevents removing Admin/App)
                        if (strictData.isAntiUninstallEnabled) {
                           val hasDeactivate = text.contains("deactivate") || text.contains("remove") || text.contains("uninstall")
                           val hasContext = text.contains("device admin") || text.contains("reality") || text.contains("admin app")
                           
                           if ((hasDeactivate && hasContext) || text.contains("activate device admin help")) {
                                performGlobalAction(GLOBAL_ACTION_HOME)
                                Toast.makeText(this, "Action blocked by Reality Strict Mode", Toast.LENGTH_SHORT).show()
                                return
                           }
                        }
                   }
               }
            } catch (e: Exception) {}
        }
        
        // Anti-bypass for apps
        if (packageName == lastBlockedPackage && System.currentTimeMillis() - lastBlockTime < 3000) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        lastPackage = packageName
        val result = blocker.doesAppNeedToBeBlocked(packageName)
        
        if (result.isRequestingUpdate) {
            savedPreferencesLoader.saveFocusModeData(blocker.focusModeData)
        }
        
        if (result.isBlocked) {
            if (sessionBlockedPackages.contains(packageName)) {
                 val baseReason = if (result.reason.isNotEmpty()) result.reason else "Session Block"
                 handleBlock(packageName, baseReason)
                 return
            }
            if (packageName == lastBlockedPackage && (System.currentTimeMillis() - lastBlockTime) < 60000) {
                 val baseReason = if (result.reason.isNotEmpty()) result.reason else "Recurrent Block"
                 handleBlock(packageName, baseReason)
                 return
            }
            if (packageName == currentWarnedPackage) return 

            val baseReason = if (result.reason.isNotEmpty()) result.reason else if (blocker.focusModeData.isTurnedOn) "Blocked by Focus Mode" else "Generic Block (Limit Reached)"
            val used = blocker.usageLimitData.appUsages[packageName] ?: 0L
            val usedMins = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(used)
            val detailMsg = if (baseReason.contains("limit reached", true) || baseReason.contains("Group", true)) {
                "$baseReason\n($usedMins min used)"
            } else {
                "$baseReason"
            }

            if (android.provider.Settings.canDrawOverlays(this)) {
                currentWarnedPackage = packageName
                showWarningOverlay(5) 
                
                warningRunnable = Runnable {
                    handleBlock(packageName, detailMsg)
                    removeWarningOverlay()
                    currentWarnedPackage = null
                }
                handler.postDelayed(warningRunnable!!, 5000)
            } else {
                handleBlock(packageName, detailMsg)
            }

        } else {
            if (currentWarnedPackage == packageName) {
                cancelWarning()
            }
            if (result.endTime != -1L) {
                 setUpForcedRefreshChecker(packageName, result.endTime)
            }
        }
    }
    
    private var currentWarnedPackage: String? = null
    private var warningOverlay: android.widget.TextView? = null
    private var warningRunnable: Runnable? = null

    private fun cancelWarning() {
        warningRunnable?.let { handler.removeCallbacks(it) }
        removeWarningOverlay()
        currentWarnedPackage = null
    }

    private fun showWarningOverlay(seconds: Int) {
        try {
            if (warningOverlay != null) return // Already showing

            val windowManager = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
            val params = android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                android.view.WindowManager.LayoutParams.WRAP_CONTENT,
                android.os.Build.VERSION.SDK_INT.let { if (it >= 26) android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else android.view.WindowManager.LayoutParams.TYPE_PHONE },
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT
            )
            params.gravity = android.view.Gravity.TOP or android.view.Gravity.CENTER_HORIZONTAL
            params.y = 100

            warningOverlay = android.widget.TextView(this).apply {
                text = "Limit Reached! Blocking in ${seconds}s..."
                textSize = 16f
                setTextColor(android.graphics.Color.WHITE)
                setPadding(32, 16, 32, 16)
                background = android.graphics.drawable.GradientDrawable().apply {
                    setColor(android.graphics.Color.parseColor("#CCFF4444")) // Semi-transparent Red
                    cornerRadius = 16f
                }
                elevation = 10f
            }

            windowManager.addView(warningOverlay, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeWarningOverlay() {
        try {
            warningOverlay?.let {
                val windowManager = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
                windowManager.removeView(it)
            }
            warningOverlay = null
        } catch (e: Exception) {
            warningOverlay = null // Reset anyway
        }
    }


    private fun performBackgroundUpdates() {
        if (!hasUsageStatsPermission()) return
        
        com.neubofy.reality.utils.TerminalLogger.log("KERNEL: Monitor Active. Processed $scanEventsCount signals.")
        scanEventsCount = 0

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Fetch Accurate Usage Stats
                com.neubofy.reality.utils.TerminalLogger.log("  [1/3] Syncing Usage Stats...")
                val usageMap = com.neubofy.reality.utils.UsageUtils.getUsageSinceMidnight(applicationContext)
                
                // 2. Fetch Active Calendar Events
                com.neubofy.reality.utils.TerminalLogger.log("  [2/3] Checking Calendar Events...")
                val db = com.neubofy.reality.data.db.AppDatabase.getDatabase(applicationContext)
                val currentTime = System.currentTimeMillis()
                val events = db.calendarEventDao().getCurrentEvents(currentTime)
                val eventPairs = events.map { Pair(it.startTime, it.endTime) }
                
                // 3. Update Blocker State on Main Thread
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    // Update Usage
                    blocker.usageLimitData.appUsages.clear()
                    blocker.usageLimitData.appUsages.putAll(usageMap)
                    
                    // Calculate total distracting time
                    var totalDistracting = 0L
                    for ((pkg, usageMs) in usageMap) {
                        val isDistracting = if (blocker.focusModeData.modeType == Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED) {
                            !blocker.focusModeData.selectedApps.contains(pkg)
                        } else {
                            blocker.focusModeData.selectedApps.contains(pkg)
                        }
                        if (isDistracting && !isSystemApp(pkg)) totalDistracting += usageMs
                    }
                    blocker.usageLimitData.usedTimeInMillis = totalDistracting
                    
                    // SAVE DATA so UI can read it
                    savedPreferencesLoader.saveUsageLimitData(blocker.usageLimitData)
                    
                    // Update Calendar Events
                    blocker.refreshCalendarEvents(eventPairs)
                
                
                    updateBlockingStatus()
                    com.neubofy.reality.utils.TerminalLogger.log("  [3/3] Background Logic Complete.")
                }
            } catch (e: Exception) {}
        }
    }
    
    private fun isSystemApp(packageName: String): Boolean {
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (packageName == getPackageName()) return true
            return isSystem
        } catch (e: Exception) { return false }
    }
    
    // Updated signature to control session persistence
    private fun handleBlock(packageName: String, reason: String? = null, addToSession: Boolean = true) {
        com.neubofy.reality.utils.TerminalLogger.log("ACTION: Blocking $packageName. Reason: ${reason ?: "N/A"}")
        // Kill Process for stronger block
        try {
             val am = getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
             am.killBackgroundProcesses(packageName)
        } catch(e: Exception) {}

        // Add to session blocked set - prevents 2-min bypass
        if (addToSession) {
            sessionBlockedPackages.add(packageName)
        }
        
        lastBlockedPackage = packageName
        lastBlockTime = System.currentTimeMillis()
        
        // Launch Block Activity
        try {
            val intent = Intent(this, com.neubofy.reality.ui.activity.BlockActivity::class.java).apply {
                 addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
                 putExtra("pkg", packageName)
                 putExtra("reason", reason)
            }
            startActivity(intent)
        } catch (e: Exception) {
            // Fallback if activity fails
            pressHome()
            Toast.makeText(this, reason, Toast.LENGTH_SHORT).show()
        }
        
        lastPackage = ""
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onServiceConnected() {
        super.onServiceConnected()
        
        // Initialize Dynamic Browser Detection
        com.neubofy.reality.utils.UrlDetector.init(this)
        
        refreshSettings()
        val filter = IntentFilter().apply {
            addAction(INTENT_ACTION_REFRESH_FOCUS_MODE)
            addAction(INTENT_ACTION_BLOCK_APP_COOLDOWN)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(refreshReceiver, filter)
        }
    }

    private fun refreshSettings() {
        // Universal Whitelist - These apps should NEVER be blocked
        val whitelist = hashSetOf<String>()
        whitelist.add(packageName) // Reality app
        whitelist.add("com.android.settings") // Settings app
        whitelist.add("com.android.systemui") // System UI
        whitelist.add("com.google.android.packageinstaller") // Package installer
        whitelist.add("com.android.packageinstaller") // Package installer (AOSP)
        whitelist.add("com.android.permissioncontroller") // Permission controller
        com.neubofy.reality.utils.getDefaultLauncherPackageName(packageManager)?.let { whitelist.add(it) }
        com.neubofy.reality.utils.getCurrentKeyboardPackageName(this)?.let { whitelist.add(it) }
        blocker.whitelistedPackages = whitelist
        
        warningConfig = savedPreferencesLoader.loadAppBlockerWarningInfo()
        blocker.refreshSchedules(savedPreferencesLoader.loadAutoFocusHoursList())
        blocker.usageLimitData = savedPreferencesLoader.getUsageLimitData()
        
        try {
            blocker.bedtimeData = savedPreferencesLoader.getBedtimeData()
            blocker.emergencyData = savedPreferencesLoader.getEmergencyData()
            blocker.strictModeData = savedPreferencesLoader.getStrictModeData()
        } catch (e: Exception) {
             blocker.bedtimeData = com.neubofy.reality.Constants.BedtimeData()
             blocker.emergencyData = com.neubofy.reality.Constants.EmergencyModeData()
             blocker.strictModeData = com.neubofy.reality.Constants.StrictModeData()
        }
        
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = com.neubofy.reality.data.db.AppDatabase.getDatabase(applicationContext)
                val events = db.calendarEventDao().getCurrentEvents(System.currentTimeMillis())
                val eventPairs = events.map { Pair(it.startTime, it.endTime) }
                blocker.refreshCalendarEvents(eventPairs)
                
                val groups = db.appGroupDao().getAllGroups()
                blocker.refreshAppGroups(groups)
                
                val limits = db.appLimitDao().getAllLimits()
                blocker.refreshAppLimits(limits)
                
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    updateBlockingStatus()
                }
            } catch (e: Exception) {}
        }
        
        val data = savedPreferencesLoader.getFocusModeData()
        if (!data.isTurnedOn) {
             com.neubofy.reality.utils.NotificationTimerManager(this).stopTimer()
             // Clear session blocked packages when Focus Mode ends
             sessionBlockedPackages.clear()
             // Shorts detection reset removed
        }
        val selected = savedPreferencesLoader.getFocusModeSelectedApps().toHashSet()

        if (data.modeType == Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED) {
            selected.add("com.android.systemui")
            selected.add("com.android.settings")
            selected.add(packageName)
            getDefaultLauncherPackageName(packageManager)?.let { selected.add(it) }
            getCurrentKeyboardPackageName(this)?.let { selected.add(it) }
        }
        
        blocker.focusModeData = RealityBlocker.FocusModeData(
            isTurnedOn = data.isTurnedOn,
            endTime = data.endTime,
            selectedApps = selected,
            modeType = data.modeType,
            blockedWebsites = data.blockedWebsites
        )
        
        updateBlockingStatus()
                           
        com.neubofy.reality.utils.TerminalLogger.log("KERNEL: Config Loaded. Blocklist: ${data.blockedWebsites.size}. Active: $isBlockingActive")
        
        // LOOPHOLE FIX v1.0.5: Force check the current window
        // This catches "Passive Watching" (e.g., Movie) where no touch events occur
        checkCurrentWindow()
    }
    
    private fun checkCurrentWindow() {
        // Only run if service is capable of blocking
        if (!isBlockingActive) return
        
        try {
            val root = rootInActiveWindow ?: return
            val pkg = root.packageName?.toString() ?: return
            
            // Avoid self-blocking system apps
            if (pkg == packageName || pkg == "com.android.systemui") return
            
            val result = blocker.doesAppNeedToBeBlocked(pkg)
            if (result.isBlocked) {
                com.neubofy.reality.utils.TerminalLogger.log("WATCHDOG: Periodic Check Caught $pkg")
                handleBlock(pkg, result.reason)
            }
        } catch (e: Exception) {
            // Ignore - accessibility node might be invalid
        }
    }

    private fun updateBlockingStatus() {
        val wasActive = isBlockingActive
        
        // 1. Service Active State (Monitoring)
        // Checks if we need to listen to accessibility events at all.
        // Needs to be true if ANY block condition is possible.
        isBlockingActive = blocker.focusModeData.isTurnedOn ||
                           blocker.hasActiveSchedules() ||
                           blocker.hasActiveCalendarEvents() ||
                           blocker.bedtimeData.isEnabled ||
                           (blocker.strictModeData.isEnabled && blocker.strictModeData.isAntiUninstallEnabled) ||
                           blocker.hasConfiguredLimits()
                           
        // 2. DND Logic (Modes Only)
        // DND should ONLY trigger for "Global Focus Modes", not for individual app limits.
        val shouldEnableDnd = blocker.focusModeData.isTurnedOn ||
                              blocker.hasActiveSchedules() ||
                              blocker.hasActiveCalendarEvents() || // Calendar events are treated like schedules
                              blocker.bedtimeData.isEnabled
                              
        // Only toggle DND if the "DND-worthy" state changed
        // We track DND state separately or just check current state? 
        // Better to just check if DND is enabled vs should be enabled.
        if (savedPreferencesLoader.isAutoDndEnabled()) {
             val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
             val currentInterruptionFilter = notificationManager.getCurrentInterruptionFilter()
             val isDndOn = currentInterruptionFilter == android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
             
             if (shouldEnableDnd && !isDndOn) {
                 toggleDnd(true)
             } else if (!shouldEnableDnd && isDndOn && !isBlockingActive) {
                  // Only turn OFF DND if we think we turned it on (risky to turn off user's manual DND)
                  // For now, let's stick to the previous simple logic but scoped to `shouldEnableDnd`
                  toggleDnd(false)
             }
        }
    }
    
    private fun toggleDnd(enable: Boolean) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (notificationManager.isNotificationPolicyAccessGranted) {
            if (enable) {
                notificationManager.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                com.neubofy.reality.utils.TerminalLogger.log("DND: Enabled (Mode Active)")
            } else {
                notificationManager.setInterruptionFilter(android.app.NotificationManager.INTERRUPTION_FILTER_ALL)
                com.neubofy.reality.utils.TerminalLogger.log("DND: Disabled (Mode Ended)")
            }
        }
    }

    private fun setUpForcedRefreshChecker(packageName: String, endMillis: Long) {
        updateRunnable?.let { handler.removeCallbacks(it) }
        updateRunnable = Runnable {
            if (rootInActiveWindow?.packageName == packageName) {
                handleBlock(packageName, "App time limit expired")
            }
        }
        handler.postAtTime(updateRunnable!!, endMillis)
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(refreshReceiver) } catch (e: Exception) {}
    }

    override fun onInterrupt() {}

    private fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, 
                android.os.Process.myUid(), packageName)
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) { false }
    }

    // ==========================================
    // BROWSER SMART BLOCKING Logic
    // ==========================================

    private fun isWebsiteBlockActive(): Boolean {
        // GLOBAL EMERGENCY BYPASS: If Emergency Mode is active, bypass ALL website blocks
        if (blocker.emergencyData.currentSessionEndTime > System.currentTimeMillis()) {
             return false
        }

        // Get blocked websites from saved preferences (works for all modes)
        val blockedWebsites = savedPreferencesLoader.getFocusModeData().blockedWebsites
        
        // Optimization: If no websites are blocked, don't run checks
        if (blockedWebsites.isEmpty()) {
            return false
        }
        
        // 1. Manual Focus Mode
        if (blocker.focusModeData.isTurnedOn) {
            return true
        }
        
        // 2. Bedtime Mode
        if (blocker.bedtimeData.isEnabled && isBedtime()) {
            return true
        }
        
        // 3. Calendar Events - check if any calendar-synced events are active
        val currentTime = System.currentTimeMillis()
        try {
            val db = com.neubofy.reality.data.db.AppDatabase.getDatabase(applicationContext)
            val hasCalendarEvent = kotlinx.coroutines.runBlocking {
                db.calendarEventDao().getCurrentEvents(currentTime).isNotEmpty()
            }
            if (hasCalendarEvent) {
                return true
            }
        } catch (e: Exception) {}
        
        // 4. Active Schedules (Robust Check)
        val schedules = savedPreferencesLoader.loadAutoFocusHoursList()
        val now = java.util.Calendar.getInstance()
        val currentMins = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
        val currentDay = now.get(java.util.Calendar.DAY_OF_WEEK)
        
        for (item in schedules) {
             // Check if schedule runs today (empty list means all days)
             val runsToday = item.repeatDays.isEmpty() || item.repeatDays.contains(currentDay)
             
             if (runsToday) {
                 val start = item.startTimeInMins
                 val end = item.endTimeInMins
                 val isActive = if (start <= end) {
                     currentMins in start until end
                 } else {
                     // Schedule spans midnight
                     currentMins >= start || currentMins < end
                 }
                 if (isActive) {
                     return true
                 }
             }
        }
        
        return false
    }

    private fun isBedtime(): Boolean {
        val cal = java.util.Calendar.getInstance()
        val currentMins = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val start = blocker.bedtimeData.startTimeInMins
        val end = blocker.bedtimeData.endTimeInMins
        
        return if (start < end) {
            currentMins in start until end
        } else {
            currentMins >= start || currentMins < end
        }
    }

    private fun extractText(node: AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val text = StringBuilder()
        if (node.text != null) text.append(node.text).append(" ")
        if (node.contentDescription != null) text.append(node.contentDescription).append(" ")
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            text.append(extractText(child))
            child?.recycle()
        }
        return text.toString()
    }
    private fun checkUrl(packageName: String) {
        lastUrlCheckTime = System.currentTimeMillis()
        try {
            val root = rootInActiveWindow
            if (root != null) {
                val url = com.neubofy.reality.utils.UrlDetector.getUrl(root, packageName)
                
                if (url != null && url.contains(".")) {
                    val cleanUrl = url.lowercase()
                    
                    // Get blocked websites from saved preferences (works for schedules too)
                    val blockedWebsites = savedPreferencesLoader.getFocusModeData().blockedWebsites
                    
                    // Check against blocklist
                    val blockedItem = blockedWebsites.find { it.isNotEmpty() && cleanUrl.contains(it) }
                    
                    if (blockedItem != null) {
                        com.neubofy.reality.utils.TerminalLogger.log("BLOCKED SITE: $cleanUrl") 
                        
                        // 1. Redirect browser to blank IMMEDIATELY
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("about:blank"))
                            intent.setPackage(packageName)
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                        } catch (e: Exception) {}
                        
                        // 2. Launch Block Activity Over Everything (Inescapable)
                        val blockIntent = Intent(this, com.neubofy.reality.ui.activity.BlockActivity::class.java).apply {
                             addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) 
                             putExtra("pkg", packageName)
                             putExtra("reason", "Website Blocked: $blockedItem")
                        }
                        startActivity(blockIntent)
                        
                        // 3. Accessibility Back (Just in case)
                        performGlobalAction(GLOBAL_ACTION_BACK)
                        
                        return
                    }
                }
            }
        } catch (e: Exception) {}
    }
    
    // THE WATCHDOG: Runs continuously while Session is Active
    // Adaptive Polling Variables
    private var currentPollInterval = 15_000L // Start aggressive
    private val POLL_STEPS = listOf(15_000L, 30_000L, 60_000L, 90_000L)
    private var pollStepIndex = 0

    private fun startBrowserCheckTimer() {
        if (browserCheckRunnable != null) return // Already running
        
        // Reset to aggressive start
        resetWatchdogRampUp()
        
        com.neubofy.reality.utils.TerminalLogger.log("WATCHDOG: Started (Adaptive)")
        browserCheckRunnable = object : Runnable {
            override fun run() {
                // STOP CONDITION: Only if the entire Blocking Session Ends
                if (!isWebsiteBlockActive()) {
                    com.neubofy.reality.utils.TerminalLogger.log("WATCHDOG: Stopped (Session Ended)")
                    stopBrowserCheckTimer()
                    return
                }
                
                // Active Scan: Check the current window
                if (currentBrowserPackage != null) {
                     checkUrl(currentBrowserPackage!!)
                } else {
                     // Try to find if a browser is active even if we missed the event
                     if (rootInActiveWindow?.packageName?.let { com.neubofy.reality.utils.UrlDetector.isBrowser(it.toString()) } == true) {
                         currentBrowserPackage = rootInActiveWindow.packageName.toString()
                         checkUrl(currentBrowserPackage!!)
                     }
                }
                
                // Adaptive Interval Logic
                // 15s -> 30s -> 60s -> 90s (Hold at 90s)
                if (pollStepIndex < POLL_STEPS.size - 1) {
                    pollStepIndex++
                }
                currentPollInterval = POLL_STEPS[pollStepIndex]
                
                // Re-run with new interval
                handler.postDelayed(this, currentPollInterval)
            }
        }
        handler.post(browserCheckRunnable!!) // Run immediately first
    }
    
    private fun resetWatchdogRampUp() {
        pollStepIndex = 0
        currentPollInterval = POLL_STEPS[0]
        // If runnable is running, we don't restart it, but the next loop will use the reset index/interval
        // However, to be instant, we can verify if we need to force a quick check? 
        // For now, next cycle catches it. To be purely instant on touch, we rely on the immediate post in start()
        // or the fact that this is called from onAccessibilityEvent.
    }
    
    private fun stopBrowserCheckTimer() {
        browserCheckRunnable?.let { 
            handler.removeCallbacks(it) 
        }
        browserCheckRunnable = null
    }
}
