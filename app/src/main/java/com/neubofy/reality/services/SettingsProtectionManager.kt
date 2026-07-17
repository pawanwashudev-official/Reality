package com.neubofy.reality.services

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import com.neubofy.reality.Constants
import com.neubofy.reality.R
import com.neubofy.reality.utils.SettingsBox
import com.neubofy.reality.utils.TerminalLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsProtectionManager(
    private val service: AppBlockerService,
    private val serviceScope: CoroutineScope,
    private val handler: Handler
) {
    var penaltyOverlay: View? = null
    private var penaltyTimer: CountDownTimer? = null

    /**
     * Extracts a quick hash of the page's top-level text content.
     * Used to detect actual page changes (different content) vs scroll/click (same content).
     * Only reads the first 8 text nodes for speed.
     */
    fun getQuickContentHash(): String {
        val rootNode = try { service.rootInActiveWindow } catch (e: Exception) { null } ?: return ""
        try {
            val sb = java.lang.StringBuilder()
            val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
            queue.add(rootNode)
            var count = 0
            while (queue.isNotEmpty() && count < 8) {
                val node = queue.removeFirst()
                node.text?.let { 
                    sb.append(it.toString().take(20))
                    count++
                }
                for (i in 0 until node.childCount.coerceAtMost(5)) {
                    try { node.getChild(i)?.let { queue.add(it) } } catch (_: Exception) {}
                }
            }
            return sb.toString().hashCode().toString()
        } catch (e: Exception) {
            return ""
        } finally {
            try { rootNode.recycle() } catch (_: Exception) {}
        }
    }

    /**
     * Schedule protection check with given delay. Supports two passes:
     * - Fast pass at 50ms for instant unique-class blocks
     * - Retry at 300ms for keyword-required pages where content wasn't loaded  
     */
    fun scheduleSettingsProtectionCheck(className: String, pkg: String, delay: Long = 50L) {
        // PASS 1
        handler.postDelayed({
            val rootNode = try { service.rootInActiveWindow } catch (e: Exception) { null }
            if (rootNode != null) {
                serviceScope.launch {
                    try {
                        handleStrictSettingsProtection(rootNode)
                    } catch (e: Exception) {
                        TerminalLogger.log("ERROR: ${e.message}")
                    } finally {
                        try { rootNode.recycle() } catch (e: Exception) {}
                    }
                }
            }
        }, delay)
        
        // PASS 2: Retry for keyword-required pages (only on initial trigger, not content-change retrigger)
        if (delay == 50L) {
            handler.postDelayed({
                if (className == service.lastWindowClassName && pkg == service.lastWindowPackage) {
                    val rootNode2 = try { service.rootInActiveWindow } catch (e: Exception) { null }
                    if (rootNode2 != null) {
                        serviceScope.launch {
                            try {
                                handleStrictSettingsProtection(rootNode2)
                            } catch (e: Exception) {
                                TerminalLogger.log("ERROR: ${e.message}")
                            } finally {
                                try { rootNode2.recycle() } catch (e: Exception) {}
                            }
                        }
                    }
                }
            }, 300)
        }
    }

    /**
     * SETTINGS BOX POWERED PROTECTION
     * 
     * Uses SettingsBox for O(1) page detection
     */
    private suspend fun handleStrictSettingsProtection(rootNode: AccessibilityNodeInfo?) {
        try {
            val strictData = service.blocker.strictModeData
            
            if (!strictData.isEnabled) {
                return
            }
            
            if (service.packageManager.isSafeMode) {
                return
            }
            
            val currentPackage = service.lastWindowPackage
            val currentClass = service.lastWindowClassName
            
            if (currentClass.isEmpty()) return
            
            val blockResult = SettingsBox.shouldBlockPage(
                packageName = currentPackage,
                className = currentClass,
                rootNode = rootNode
            )
            
            if (blockResult.shouldBlock) {
                TerminalLogger.log("SETTINGS_BOX: BLOCKING ${currentClass.substringAfterLast(".")} - ${blockResult.reason}")
                
                val penaltyDuration = calculatePenaltyDuration()
                
                // UI Operations MUST be on Main Thread
                withContext(Dispatchers.Main) {
                     showPenaltyOverlay(blockResult.reason, penaltyDuration)
                }
            }
            
        } catch (e: Exception) {
            TerminalLogger.log("STRICT ERROR: ${e.message}")
        }
    }

    private fun showPenaltyOverlay(reason: String, durationSecs: Int = 30) {
        if (penaltyOverlay != null) return
        try {
            // IMMEDIATELY kill Settings and go HOME
            try {
                val am = service.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                am.killBackgroundProcesses("com.android.settings")
                am.killBackgroundProcesses(service.lastWindowPackage) // Kill whatever settings package
            } catch (e: Exception) {}
            service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
            
            val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY 
                    else WindowManager.LayoutParams.TYPE_PHONE,
                // CRITICAL: Do NOT use FLAG_NOT_TOUCHABLE - it lets touches pass through!
                // We want the overlay to CONSUME all touches
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.OPAQUE  // Fully opaque
            )
            
            val inflater = LayoutInflater.from(service)
            penaltyOverlay = inflater.inflate(R.layout.overlay_penalty, null)
            
            // Consume ALL touch events - do not let them pass through
            penaltyOverlay?.setOnTouchListener { _, _ -> true }
            
            val tvTimer = penaltyOverlay?.findViewById<TextView>(R.id.tvPenaltyTimer)
            val tvReason = penaltyOverlay?.findViewById<TextView>(R.id.tvPenaltyReason)
            
            tvReason?.text = "Reason: $reason"
            
            penaltyTimer = object : CountDownTimer(durationSecs * 1000L, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    val secs = millisUntilFinished / 1000
                    tvTimer?.text = String.format("%02d:%02d", secs / 60, secs % 60)
                }
                
                override fun onFinish() {
                    removePenaltyOverlay()
                    service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                }
            }.start()
            
            windowManager.addView(penaltyOverlay, params)
            TerminalLogger.log("PENALTY: Showing ${durationSecs}s penalty for $reason")
        } catch (e: Exception) {
            TerminalLogger.log("PENALTY: Overlay error - ${e.message}")
        }
    }

    fun removePenaltyOverlay() {
        try {
            penaltyTimer?.cancel()
            penaltyTimer = null
            penaltyOverlay?.let {
                val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager.removeView(it)
            }
            penaltyOverlay = null
        } catch (e: Exception) { penaltyOverlay = null }
    }

    private fun calculatePenaltyDuration(): Int {
        val now = System.currentTimeMillis()
        val fiveMinutes = 5 * 60 * 1000L
        
        if (now - service.learnedSettingsPages.lastPenaltyTime < fiveMinutes) {
            service.learnedSettingsPages.consecutiveAttempts++
        } else {
            service.learnedSettingsPages.consecutiveAttempts = 1
        }
        service.learnedSettingsPages.lastPenaltyTime = now
        serviceScope.launch(Dispatchers.IO) {
            service.savedPreferencesLoader.saveLearnedSettingsPages(service.learnedSettingsPages)
        }
        
        // Escalating penalties: 30s → 60s → 120s → 180s → 300s (5 min max)
        return when (service.learnedSettingsPages.consecutiveAttempts) {
            1 -> 30      // 30 seconds
            2 -> 60      // 1 minute
            3 -> 120     // 2 minutes
            4 -> 180     // 3 minutes
            else -> 300  // 5 minutes max
        }
    }
}
