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
import android.provider.Settings
import com.neubofy.reality.data.ScheduleManager
import com.neubofy.reality.ui.overlay.ReminderOverlayManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.neubofy.reality.Constants
import com.neubofy.reality.blockers.RealityBlocker
import com.neubofy.reality.utils.getCurrentKeyboardPackageName
import com.neubofy.reality.utils.getDefaultLauncherPackageName

class AppBlockerService : BaseBlockingService() {

    companion object {
        const val INTENT_ACTION_REFRESH_FOCUS_MODE = "com.neubofy.reality.refresh.focus_mode"
        // REMOVED: INTENT_ACTION_BLOCK_APP_COOLDOWN - Proceed Anyway bypass removed
    }

    private var warningConfig = Constants.WarningData()
    private val blocker = RealityBlocker()
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private var lastPackage = ""
    
    // === THE BOX (Cached Blocking Strategy) ===
    private data class BoxStrategy(
        val isActive: Boolean = false,
        val isAllowListMode: Boolean = false, // If true, we BLOCK everything EXCEPT packages
        val packages: Set<String> = emptySet(), // The "Box" contents
        val limitBlockedPackages: Set<String> = emptySet() // Specific apps blocked by usage limits
    )
    private var boxStrategy = BoxStrategy()
    
    // Expanded Whitelist (Hardcoded Safety Net) - COPIED from RealityBlocker for fast path
    private val expandedWhitelist = setOf(
        "com.android.calculator2", "com.google.android.calculator",
        "com.android.dialer", "com.google.android.dialer",
        "com.android.contacts", "com.google.android.contacts",
        "com.android.deskclock", "com.google.android.deskclock",
        "com.android.systemui", 
        "com.google.android.packageinstaller", "com.android.packageinstaller"
    )
    
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
                // REMOVED: Cooldown handler - Proceed Anyway bypass removed
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
        if (!isBlockingActive) {
            // FIX v1.0.6: Even if blocking is OFF, we must check occasionally (every 30s)
            val now = System.currentTimeMillis()
            if (now - lastBackgroundUpdate > 30_000) {
                lastBackgroundUpdate = now
                performBackgroundUpdates()
            }
            // If still inactive after update, THEN return
            if (!isBlockingActive) return
        }
        scanEventsCount++
        
        if (event == null) return
        val eventType = event.eventType
        val packageName = event.packageName?.toString() ?: return
        if (packageName == getPackageName()) return

        // === STRICT MODE SETTINGS PROTECTION (High Priority) ===
        // Must be checked BEFORE any optimization or scrolling delays
        if (packageName == "com.android.settings") {
             handleStrictSettingsProtection()
        }

        // 1. Browser Special Handling (Watchdog Trigger)
        val isBrowser = com.neubofy.reality.utils.UrlDetector.isBrowser(packageName)
        
        // Check if any blocking mode is active (Focus/Schedule/Calendar/Bedtime)
        val isAnyBlockingModeActive = isWebsiteBlockActive()
        
        if (isBrowser) {
             // Browser is active - start/keep watchdog running if blocking is needed
             if (isAnyBlockingModeActive) {
                 currentBrowserPackage = packageName 
                 startBrowserCheckTimer()
                 resetWatchdogRampUp()
                 
                 // FIX: IMMEDIATE URL check on any browser event (catches URL/tab changes)
                 // Check on content changes, text changes, focus changes, window state changes
                 if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
                     eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED ||
                     eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
                     eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                     checkUrl(packageName)
                 }
             }
        } else {
             // Left the browser
             currentBrowserPackage = null
             // FIX: Watchdog stays alive (Hibernating) ONLY if session is active.
             // If session ends, the watchdog stops itself in the Runnable check.
             // We do NOT explicitly stop it here to prevent "Recent Apps" loophole.
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
        
        // REMOVED: Old Settings check block (Moved to top as handleStrictSettingsProtection)

        
        // Anti-bypass for apps
        if (packageName == lastBlockedPackage && System.currentTimeMillis() - lastBlockTime < 3000) {
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        lastPackage = packageName
        
        // === THE BOX CHECK - SINGLE SOURCE OF TRUTH ===
        // No fallback. No calculations. Just check the box.
        val (shouldBlock, reasons) = com.neubofy.reality.utils.BlockCache.shouldBlock(packageName)
        
        if (shouldBlock) {
            val reason = reasons.joinToString(", ")
            
            if (sessionBlockedPackages.contains(packageName)) {
                 handleBlock(packageName, reason)
                 return
            }
            if (packageName == lastBlockedPackage && (System.currentTimeMillis() - lastBlockTime) < 60000) {
                 handleBlock(packageName, reason)
                 return
            }
            if (packageName == currentWarnedPackage) return 

            // Show warning overlay before blocking
            if (android.provider.Settings.canDrawOverlays(this)) {
                currentWarnedPackage = packageName
                showWarningOverlay(5) 
                
                warningRunnable = Runnable {
                    handleBlock(packageName, reason)
                    removeWarningOverlay()
                    currentWarnedPackage = null
                }
                handler.postDelayed(warningRunnable!!, 5000)
            } else {
                handleBlock(packageName, reason)
            }

        } else {
            if (currentWarnedPackage == packageName) {
                cancelWarning()
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
        
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                // === REBUILD THE BOX - SINGLE SOURCE OF TRUTH ===
                com.neubofy.reality.utils.BlockCache.rebuildBox(applicationContext)
                
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    // Update blocking status based on BlockCache
                    isBlockingActive = com.neubofy.reality.utils.BlockCache.isAnyBlockingModeActive ||
                                       com.neubofy.reality.utils.BlockCache.getBlockedCount() > 0
                    
                    val isAnyModeActive = com.neubofy.reality.utils.BlockCache.isAnyBlockingModeActive
                    
                    // === NOTIFICATION MANAGEMENT ===
                    if (!isAnyModeActive) {
                        com.neubofy.reality.utils.NotificationTimerManager(this@AppBlockerService).stopTimer()
                        sessionBlockedPackages.clear()
                    }
                    
                    // === DND SYNC ===
                    if (savedPreferencesLoader.isAutoDndEnabled()) {
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        val currentFilter = notificationManager.getCurrentInterruptionFilter()
                        val isDndOn = currentFilter == android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
                        
                        if (isAnyModeActive && !isDndOn) {
                            toggleDnd(true)
                        } else if (!isAnyModeActive && isDndOn) {
                            toggleDnd(false)
                        }
                    }
                }
            } catch (e: Exception) {
                com.neubofy.reality.utils.TerminalLogger.log("BG UPDATE ERROR: ${e.message}")
            }
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
    
    // === STRICT MODE OVERLAY & LOGIC ===
    private var strictOverlay: android.widget.TextView? = null
    
    private fun handleStrictSettingsProtection() {
        try {
            val strictData = blocker.strictModeData
            if (!strictData.isEnabled || packageManager.isSafeMode) return
            
            val root = rootInActiveWindow ?: return
            val text = extractText(root).lowercase()
            
            var shouldBlock = false
            var blockReason = ""

            // 1. Time Protection
            if (strictData.isTimeCheatProtectionEnabled) {
                if (text.contains("date & time") || text.contains("automatic date") || text.contains("set time")) {
                    shouldBlock = true
                    blockReason = "Time Settings Locked"
                }
            }

            // 2. Anti-Uninstall
            if (!shouldBlock && strictData.isAntiUninstallEnabled) {
                val hasDeactivate = text.contains("deactivate") || text.contains("remove") || text.contains("uninstall")
                val hasContext = text.contains("device admin") || text.contains("reality") || text.contains("admin app")
                
                if ((hasDeactivate && hasContext) || text.contains("activate device admin help")) {
                    shouldBlock = true
                    blockReason = "Uninstall Protected"
                }
            }
            
            // 3. Accessibility Protection
            if (!shouldBlock && strictData.isAccessibilityProtectionEnabled) {
                val isOnAppBlockerPage = text.contains("app blocker") && 
                    (text.contains("use app blocker") || text.contains("shortcut") || text.contains("allow") || text.contains("off"))
                val isOnRealityServicePage = text.contains("reality") &&
                    text.contains("accessibility") && (text.contains("off") || text.contains("use "))
                
                if (isOnAppBlockerPage || isOnRealityServicePage) {
                    shouldBlock = true
                    blockReason = "Accessibility Locked"
                }
            }
            
            if (shouldBlock) {
                showStrictLockOverlay(blockReason)
                performGlobalAction(GLOBAL_ACTION_BACK)
                
                // Hide overlay after delay
                handler.postDelayed({ removeStrictOverlay() }, 1500)
            }
            
        } catch (e: Exception) {}
    }
    
    private fun showStrictLockOverlay(reason: String) {
        if (strictOverlay != null) return
        try {
            val windowManager = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
            val params = android.view.WindowManager.LayoutParams(
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.view.WindowManager.LayoutParams.MATCH_PARENT,
                android.os.Build.VERSION.SDK_INT.let { if (it >= 26) android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else android.view.WindowManager.LayoutParams.TYPE_PHONE },
                android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or 
                android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
            )
            
            strictOverlay = android.widget.TextView(this).apply {
                text = "$reason\n\nStrict Mode Active"
                textSize = 24f
                gravity = android.view.Gravity.CENTER
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(android.graphics.Color.parseColor("#E6000000")) // 90% Black
                elevation = 100f
            }
            windowManager.addView(strictOverlay, params)
        } catch (e: Exception) {}
    }
    
    private fun removeStrictOverlay() {
        try {
            strictOverlay?.let {
                val windowManager = getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager
                windowManager.removeView(it)
            }
            strictOverlay = null
        } catch (e: Exception) { strictOverlay = null }
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
        
        // Load BlockCache from disk (survives RAM cleanup)
        com.neubofy.reality.utils.BlockCache.loadFromDisk(this)
        
        refreshSettings()
        val filter = IntentFilter().apply {
            addAction(INTENT_ACTION_REFRESH_FOCUS_MODE)
            // REMOVED: addAction(INTENT_ACTION_BLOCK_APP_COOLDOWN) - Proceed Anyway bypass removed
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
            
            // Sync Emergency to BlockCache for instant access
            com.neubofy.reality.utils.BlockCache.emergencySessionEndTime = blocker.emergencyData.currentSessionEndTime
        } catch (e: Exception) {
             blocker.bedtimeData = com.neubofy.reality.Constants.BedtimeData()
             blocker.emergencyData = com.neubofy.reality.Constants.EmergencyModeData()
             blocker.strictModeData = com.neubofy.reality.Constants.StrictModeData()
        }
        
        // === REBUILD THE BOX IMMEDIATELY ===
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                com.neubofy.reality.utils.BlockCache.rebuildBox(applicationContext)
                
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    val wasActive = isBlockingActive
                    
                    isBlockingActive = com.neubofy.reality.utils.BlockCache.isAnyBlockingModeActive ||
                                       com.neubofy.reality.utils.BlockCache.getBlockedCount() > 0
                    
                    val isAnyModeActive = com.neubofy.reality.utils.BlockCache.isAnyBlockingModeActive
                    
                    // === NOTIFICATION MANAGEMENT ===
                    if (isAnyModeActive) {
                        // Focus/Schedule/Bedtime is running - show notification timer
                        // Timer is already managed elsewhere, but we ensure it's running
                    } else {
                        // No blocking mode active - stop notification timer
                        com.neubofy.reality.utils.NotificationTimerManager(this@AppBlockerService).stopTimer()
                        sessionBlockedPackages.clear()
                    }
                    
                    // === DND SYNC ===
                    if (savedPreferencesLoader.isAutoDndEnabled()) {
                        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        val currentFilter = notificationManager.getCurrentInterruptionFilter()
                        val isDndOn = currentFilter == android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
                        
                        if (isAnyModeActive && !isDndOn) {
                            // Mode started, turn DND on
                            toggleDnd(true)
                        } else if (!isAnyModeActive && isDndOn) {
                            // Mode ended, turn DND off
                            toggleDnd(false)
                        }
                    }
                    
                    com.neubofy.reality.utils.TerminalLogger.log("BOX SYNC: Active=$isBlockingActive, DND=${com.neubofy.reality.utils.BlockCache.isAnyBlockingModeActive}")
                }
            } catch (e: Exception) {
                com.neubofy.reality.utils.TerminalLogger.log("REFRESH ERROR: ${e.message}")
            }
        }
        
        // Load focus mode for website blocking and other legacy checks
        val data = savedPreferencesLoader.getFocusModeData()
        blocker.focusModeData = RealityBlocker.FocusModeData(
            isTurnedOn = data.isTurnedOn,
            endTime = data.endTime,
            selectedApps = savedPreferencesLoader.getFocusModeSelectedApps().toHashSet(),
            modeType = data.modeType,
            blockedWebsites = data.blockedWebsites
        )
                           
        com.neubofy.reality.utils.TerminalLogger.log("KERNEL: Config Loaded. BlockCache will be rebuilt.")
        
        // Force check the current window
        checkCurrentWindow()
        
        scheduleNextAlarm()
    }
    
    private fun checkCurrentWindow() {
        // Only run if blocking is active
        if (!isBlockingActive) return
        
        try {
            val root = rootInActiveWindow ?: return
            val pkg = root.packageName?.toString() ?: return
            
            // Avoid self-blocking system apps
            if (pkg == packageName || pkg == "com.android.systemui") return
            
            // === CHECK THE BOX - SINGLE SOURCE OF TRUTH ===
            val (shouldBlock, reasons) = com.neubofy.reality.utils.BlockCache.shouldBlock(pkg)
            if (shouldBlock) {
                val reason = reasons.joinToString(", ")
                com.neubofy.reality.utils.TerminalLogger.log("WATCHDOG: Caught $pkg")
                handleBlock(pkg, reason)
            }
        } catch (e: Exception) {
            // Ignore - accessibility node might be invalid
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
        // GLOBAL EMERGENCY BYPASS
        if (com.neubofy.reality.utils.BlockCache.emergencySessionEndTime > System.currentTimeMillis()) {
             return false
        }

        // Get blocked websites from saved preferences
        val blockedWebsites = savedPreferencesLoader.getFocusModeData().blockedWebsites
        
        // If no websites are blocked, don't run checks
        if (blockedWebsites.isEmpty()) {
            return false
        }
        
        // Check if any blocking mode is active using BlockCache
        return com.neubofy.reality.utils.BlockCache.isAnyBlockingModeActive
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
        
        com.neubofy.reality.utils.TerminalLogger.log("WATCHDOG: Started (Smart Hibernation)")
        browserCheckRunnable = object : Runnable {
            override fun run() {
                // STOP CONDITION: Only if the entire Blocking Session Ends
                if (!isWebsiteBlockActive()) {
                    com.neubofy.reality.utils.TerminalLogger.log("WATCHDOG: Stopped (Session Ended)")
                    stopBrowserCheckTimer()
                    return
                }
                
                // Check if browser is currently the active window
                val activeWindow = rootInActiveWindow
                val activePackage = activeWindow?.packageName?.toString()
                val isBrowserActive = activePackage != null && com.neubofy.reality.utils.UrlDetector.isBrowser(activePackage)
                
                if (isBrowserActive) {
                    // ACTIVE MODE: Browser is in foreground - do full URL check
                    currentBrowserPackage = activePackage
                    checkUrl(currentBrowserPackage!!)
                    
                    // Use adaptive polling when browser is active (15s -> 30s -> 60s -> 90s)
                    if (pollStepIndex < POLL_STEPS.size - 1) {
                        pollStepIndex++
                    }
                    currentPollInterval = POLL_STEPS[pollStepIndex]
                    
                } else {
                    // HIBERNATE MODE: Browser is NOT active - check less frequently
                    // Just check if browser appeared (from recents, etc.)
                    currentBrowserPackage = null
                    
                    // Use longer interval when hibernating (5 seconds - just to detect browser opening)
                    // This saves battery vs constantly checking URL when not in browser
                    currentPollInterval = 5000L  // 5 second hibernate check
                    
                    // If browser detected from recents, wake up immediately next cycle
                    // (handled above when isBrowserActive becomes true)
                }
                
                // Re-run with calculated interval
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

    // ==========================================
    // REMINDER SYSTEM (FULL SCREEN ALARM)
    // ==========================================
    
    private val reminderManager by lazy { com.neubofy.reality.ui.overlay.ReminderOverlayManager(this) }
    
    // Reliable Alarm Scheduling
    private fun scheduleNextAlarm() {
        try {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val intent = Intent(this, com.neubofy.reality.receivers.ReminderReceiver::class.java)
            
            // Cancel old
            val pIntentCheck = android.app.PendingIntent.getBroadcast(this, 1001, intent, android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE)
            if (pIntentCheck != null) {
                alarmManager.cancel(pIntentCheck)
            }

            val list = savedPreferencesLoader.loadCustomReminders()
            val now = java.util.Calendar.getInstance()
            val currentDay = now.get(java.util.Calendar.DAY_OF_WEEK)
            val currentMins = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
            
            var nextTriggerMillis = Long.MAX_VALUE
            var nextReminder: com.neubofy.reality.data.CustomReminder? = null
            
            // Global offset
            val globalOffset = getSharedPreferences("reality_prefs", Context.MODE_PRIVATE).getInt("reminder_offset_minutes", 1)

            for (r in list) {
                if (!r.isEnabled) continue
                if (r.repeatDays.isNotEmpty() && !r.repeatDays.contains(currentDay)) continue
                
                val startMins = r.hour * 60 + r.minute
                val effectiveOffset = r.customOffsetMins ?: globalOffset
                val triggerMins = startMins - effectiveOffset
                
                // If trigger time is in future today
                if (triggerMins > currentMins) {
                    val triggerCal = java.util.Calendar.getInstance()
                    triggerCal.set(java.util.Calendar.HOUR_OF_DAY, triggerMins / 60)
                    triggerCal.set(java.util.Calendar.MINUTE, triggerMins % 60)
                    triggerCal.set(java.util.Calendar.SECOND, 0)
                    triggerCal.set(java.util.Calendar.MILLISECOND, 0)
                    
                    if (triggerCal.timeInMillis < nextTriggerMillis) {
                        nextTriggerMillis = triggerCal.timeInMillis
                        nextReminder = r
                    }
                }
            }
            
            if (nextReminder != null && nextTriggerMillis != Long.MAX_VALUE) {
                val pIntent = android.app.PendingIntent.getBroadcast(this, 1001, intent.apply {
                    putExtra("id", nextReminder.id)
                    putExtra("title", nextReminder.title)
                    putExtra("url", nextReminder.url)
                    putExtra("mins", nextReminder.customOffsetMins ?: globalOffset)
                }, android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    // GOLD STANDARD: setAlarmClock guarantees wake-up even in Doze
                    val acInfo = android.app.AlarmManager.AlarmClockInfo(nextTriggerMillis, pIntent)
                    alarmManager.setAlarmClock(acInfo, pIntent)
                } else {
                    alarmManager.setExact(android.app.AlarmManager.RTC_WAKEUP, nextTriggerMillis, pIntent)
                }
                com.neubofy.reality.utils.TerminalLogger.log("ALARM: Scheduled (AlarmClock) for ${java.util.Date(nextTriggerMillis)}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun checkUpcomingSchedules() {
        // Delegate all alarm scheduling to unified AlarmScheduler
        com.neubofy.reality.utils.AlarmScheduler.scheduleNextAlarm(this)
    }

    private fun sendReminderNotification(title: String, mins: Int) {
        val channelId = "reality_reminders"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, "Reminders", android.app.NotificationManager.IMPORTANCE_HIGH)
            manager.createNotificationChannel(channel)
        }
        
        val intent = Intent(this, com.neubofy.reality.ui.activity.MainActivity::class.java)
        val pendingIntent = android.app.PendingIntent.getActivity(this, 101, intent, android.app.PendingIntent.FLAG_IMMUTABLE)
        
        val builder = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) 
            .setContentTitle("Upcoming Focus Session")
            .setContentText("$title starts in $mins minute.") // "1 minute"
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setDefaults(androidx.core.app.NotificationCompat.DEFAULT_ALL)
            
        manager.notify(title.hashCode(), builder.build())
    }
}
