package com.neubofy.reality.services

import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.neubofy.reality.Constants
import com.neubofy.reality.R
import com.neubofy.reality.ui.activity.StrictModeActivity
import com.neubofy.reality.utils.KeywordSuggestions
import com.neubofy.reality.utils.SavedPreferencesLoader
import com.neubofy.reality.utils.SettingsBox
import com.neubofy.reality.utils.TerminalLogger

class SettingsLearningManager(
    private val service: AppBlockerService
) {
    private val savedPreferencesLoader = SavedPreferencesLoader(service)
    
    var isLearningMode = false
    var isCustomPageLearning = false
    var currentLearningPageType: Constants.PageType? = null
    var learnOverlay: View? = null
    var currentCustomPageName: String = ""
    
    val selectedLearningKeywords = mutableListOf<String>()
    private var learnOverlayParams: WindowManager.LayoutParams? = null

    fun stopLearning() {
        isLearningMode = false
        isCustomPageLearning = false
        currentLearningPageType = null
        removeLearnOverlay()
    }

    fun showLearnConfirmOverlay() {
        if (learnOverlay != null) return
        selectedLearningKeywords.clear() // Reset keywords for new learning session
        
        try {
            val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY 
                    else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                x = 50
                y = 200
            }
            learnOverlayParams = params
            
            val inflater = LayoutInflater.from(service)
            learnOverlay = inflater.inflate(R.layout.overlay_learn_confirm, null)
            
            learnOverlay?.findViewById<TextView>(R.id.tvCurrentPage)?.text = 
                "Current: ${service.lastWindowClassName.substringAfterLast(".")}"
            
            // SCAN BUTTON - Extract keywords from actual page content
            learnOverlay?.findViewById<Button>(R.id.btnScan)?.setOnClickListener {
                try {
                    val rootNode = service.rootInActiveWindow
                    if (rootNode != null) {
                        val keywords = KeywordSuggestions.extractFromAccessibilityTree(rootNode)
                        rootNode.recycle()
                        
                        if (keywords.isNotEmpty()) {
                            // Show keywords section
                            learnOverlay?.findViewById<View>(R.id.keywordsSection)?.visibility = View.VISIBLE
                            
                            // Add keyword chips to row 1 and row 2
                            val chipsContainerRow1 = learnOverlay?.findViewById<LinearLayout>(R.id.chipsContainerRow1)
                            val chipsContainerRow2 = learnOverlay?.findViewById<LinearLayout>(R.id.chipsContainerRow2)
                            chipsContainerRow1?.removeAllViews()
                            chipsContainerRow2?.removeAllViews()
                            
                            val density = service.resources.displayMetrics.density
                            keywords.take(12).forEachIndexed { index, keyword ->
                                val chip = TextView(service).apply {
                                    text = keyword
                                    background = createChipDrawable(keyword in selectedLearningKeywords)
                                    setTextColor(0xFFFFFFFF.toInt())
                                    textSize = 12f
                                    setPadding((14 * density).toInt(), (8 * density).toInt(), (14 * density).toInt(), (8 * density).toInt())
                                    setOnClickListener {
                                        if (keyword !in selectedLearningKeywords) {
                                            selectedLearningKeywords.add(keyword)
                                            background = createChipDrawable(true)
                                            updateSelectedKeywordsDisplay()
                                        } else {
                                            selectedLearningKeywords.remove(keyword)
                                            background = createChipDrawable(false)
                                            updateSelectedKeywordsDisplay()
                                        }
                                    }
                                }
                                val chipParams = LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply { 
                                    setMargins((4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt(), (4 * density).toInt())
                                }
                                if (index % 2 == 0) {
                                    chipsContainerRow1?.addView(chip, chipParams)
                                } else {
                                    chipsContainerRow2?.addView(chip, chipParams)
                                }
                            }
                            
                            Toast.makeText(service, "Found ${keywords.size} keywords! Tap to add.", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(service, "No keywords found on this page", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(service, "Cannot scan - navigate to page first", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(service, "Scan error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            
            learnOverlay?.findViewById<Button>(R.id.btnConfirm)?.setOnClickListener {
                saveLearnedPage()
                removeLearnOverlay()
                val keywordCount = selectedLearningKeywords.size
                val msg = if (keywordCount > 0) "✓ Page + $keywordCount keywords saved!" else "✓ Page recorded!"
                Toast.makeText(service, msg, Toast.LENGTH_SHORT).show()
                isLearningMode = false
                isCustomPageLearning = false
                currentLearningPageType = null
                selectedLearningKeywords.clear()
                
                // Navigate back to StrictModeActivity
                val intent = Intent(service, StrictModeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                service.startActivity(intent)
            }
            
            learnOverlay?.findViewById<Button>(R.id.btnCancel)?.setOnClickListener {
                removeLearnOverlay()
                service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME)
                isLearningMode = false
                currentLearningPageType = null
                selectedLearningKeywords.clear()
            }
            
            // === MAKE DRAGGABLE ===
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            
            learnOverlay?.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        try {
                            windowManager.updateViewLayout(learnOverlay, params)
                        } catch (e: Exception) {}
                        true
                    }
                    else -> false
                }
            }
            
            windowManager.addView(learnOverlay, params)
        } catch (e: Exception) {
            TerminalLogger.log("LEARN: Overlay error - ${e.message}")
        }
    }

    private fun updateSelectedKeywordsDisplay() {
        val tvSelected = learnOverlay?.findViewById<TextView>(R.id.tvSelectedKeywords)
        if (selectedLearningKeywords.isNotEmpty()) {
            tvSelected?.visibility = View.VISIBLE
            tvSelected?.text = "Selected: ${selectedLearningKeywords.joinToString(", ")}"
        } else {
            tvSelected?.visibility = View.GONE
        }
    }

    fun updateLearnOverlayText() {
        try {
            learnOverlay?.findViewById<TextView>(R.id.tvCurrentPage)?.text = 
                "Current: ${service.lastWindowClassName.substringAfterLast(".")}"
        } catch (e: Exception) {}
    }

    fun removeLearnOverlay() {
        try {
            learnOverlay?.let {
                val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                windowManager.removeView(it)
            }
            learnOverlay = null
        } catch (e: Exception) { learnOverlay = null }
    }

    private fun createChipDrawable(selected: Boolean): android.graphics.drawable.GradientDrawable {
        val density = service.resources.displayMetrics.density
        return android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 16f * density
            if (selected) {
                setColor(0xFF00CC66.toInt()) // Sleek green when selected
                setStroke((1.5f * density).toInt(), 0xFF00FF88.toInt())
            } else {
                setColor(0x1F000000.toInt()) // Dark translucent when unselected
                setStroke((1f * density).toInt(), 0x44FFFFFF.toInt()) // Subtle white border
            }
        }
    }

    private fun saveLearnedPage() {
        val className = service.lastWindowClassName
        val lastWindowPackage = service.lastWindowPackage
        if (className.isEmpty()) return
        
        // CRITICAL: Reload fresh data from preferences to avoid overwriting deletions!
        service.learnedSettingsPages = savedPreferencesLoader.getLearnedSettingsPages()
        
        // If custom page learning, add to custom list
        if (isCustomPageLearning && currentLearningPageType == null) {
            val name = currentCustomPageName.ifEmpty { "Custom Page" }
            val pageKey = "1|$name|$lastWindowPackage|$className"
            service.learnedSettingsPages.customBlockedPages.add(pageKey)
            savedPreferencesLoader.saveLearnedSettingsPages(service.learnedSettingsPages)
            TerminalLogger.log("LEARN CUSTOM: Saved page = $pageKey")
            return
        }
        
        val pageType = currentLearningPageType ?: return
        
        when (pageType) {
            Constants.PageType.ACCESSIBILITY -> {
                service.learnedSettingsPages.accessibilityPageClass = className
                service.learnedSettingsPages.accessibilityPagePackage = lastWindowPackage
                // Add selected keywords if any
                if (selectedLearningKeywords.isNotEmpty()) {
                    service.learnedSettingsPages.accessibilityKeywords.addAll(selectedLearningKeywords)
                }
            }
            Constants.PageType.DEVICE_ADMIN -> {
                service.learnedSettingsPages.deviceAdminPageClass = className
                service.learnedSettingsPages.deviceAdminPagePackage = lastWindowPackage
                if (selectedLearningKeywords.isNotEmpty()) {
                    service.learnedSettingsPages.deviceAdminKeywords.addAll(selectedLearningKeywords)
                }
            }
            Constants.PageType.APP_INFO -> {
                service.learnedSettingsPages.appInfoPageClass = className
                service.learnedSettingsPages.appInfoPagePackage = lastWindowPackage
                if (selectedLearningKeywords.isNotEmpty()) {
                    service.learnedSettingsPages.appInfoKeywords.addAll(selectedLearningKeywords)
                }
            }
            Constants.PageType.TIME_SETTINGS -> {
                service.learnedSettingsPages.timeSettingsPageClass = className
                service.learnedSettingsPages.timeSettingsPagePackage = lastWindowPackage
                if (selectedLearningKeywords.isNotEmpty()) {
                    service.learnedSettingsPages.timeSettingsKeywords.addAll(selectedLearningKeywords)
                }
            }
            Constants.PageType.DEVELOPER_OPTIONS -> {
                service.learnedSettingsPages.developerOptionsPageClass = className
                service.learnedSettingsPages.developerOptionsPagePackage = lastWindowPackage
                if (selectedLearningKeywords.isNotEmpty()) {
                    service.learnedSettingsPages.developerOptionsKeywords.addAll(selectedLearningKeywords)
                }
            }
        }
        
        savedPreferencesLoader.saveLearnedSettingsPages(service.learnedSettingsPages)
        SettingsBox.rebuildBox(service)
        val keywordInfo = if (selectedLearningKeywords.isNotEmpty()) " + ${selectedLearningKeywords.size} keywords" else ""
        TerminalLogger.log("LEARN: Saved $pageType = $lastWindowPackage|$className$keywordInfo")
    }
}
