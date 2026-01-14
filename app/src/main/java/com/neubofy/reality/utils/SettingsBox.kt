package com.neubofy.reality.utils

import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import com.neubofy.reality.Constants

/**
 * SETTINGS BOX - THE SINGLE SOURCE OF TRUTH FOR SETTINGS PAGE BLOCKING
 * 
 * Architecture inspired by BlockCache:
 * - Pre-computed box of blocked settings pages
 * - O(1) lookup using "package|className" key
 * - Optional keyword verification for ambiguous pages (SubSettings, etc)
 * - Hardcoded fallbacks for common pages
 * 
 * Detection Flow:
 * 1. Get package+className from AccessibilityEvent (instant)
 * 2. O(1) lookup in settingsBox map
 * 3. If ambiguous class → quick keyword scan (top 10 nodes only)
 * 4. Block or allow
 */
object SettingsBox {
    
    private const val TAG = "SettingsBox"
    private const val PREFS_NAME = "settings_box_prefs"
    private const val KEY_LEARNED_PAGES = "learned_pages_json"
    
    // === THE BOX ===
    // Key: "packageName|className" (e.g., "com.android.settings|DateTimeSettings")
    // Value: BlockedPageConfig with blocking reason and optional keywords
    @Volatile
    private var settingsBox: Map<String, BlockedPageConfig> = emptyMap()
    
    // Wildcard entries for content-based matching (when className varies)
    @Volatile
    private var wildcardPages: List<BlockedPageConfig> = emptyList()
    
    // === STRICT MODE FLAGS ===
    @Volatile var isAccessibilityProtectionEnabled = false
    // NOTE: isAppInfoProtectionEnabled REMOVED - feature disabled
    @Volatile var isTimeCheatProtectionEnabled = false
    @Volatile var isAntiUninstallEnabled = false
    
    // === DATA CLASSES ===
    data class BlockedPageConfig(
        val pageType: Constants.PageType,
        val packageName: String,
        val className: String,  // Can be "*" for wildcard matching
        val requiresContentCheck: Boolean = false,
        val contentKeywords: List<String> = emptyList(),
        val blockReason: String = "Protected by Strict Mode"
    ) {
        fun getKey(): String = "$packageName|$className"
    }
    
    data class BlockResult(
        val shouldBlock: Boolean,
        val reason: String = "",
        val pageType: Constants.PageType? = null
    )
    
    // === AMBIGUOUS CLASS NAMES ===
    // These classes are used for multiple pages, require content verification
    private val AMBIGUOUS_CLASSES = setOf(
        "SubSettings",
        "Settings\$",
        "SettingsActivity",
        "SettingsHomepage",
        "Fragment",
        "DashboardFragment",
        "SettingsPreferenceFragment"
    )
    
    // === SETTINGS PACKAGE NAMES ===
    private val SETTINGS_PACKAGES = setOf(
        "com.android.settings",
        "com.samsung.android.settings",
        "com.miui.securitycenter",
        "com.coloros.safecenter",
        "com.oplus.safecenter",
        "com.vivo.permissionmanager",
        "com.oneplus.security",
        "com.oppo.safe",
        "com.realme.security"
    )
    
    // === HARDCODED FALLBACKS REMOVED ===
    // Per user POV: Learning is ALWAYS required. No fallbacks.
    // The getHardcodedPages() function has been removed.
    
    /**
     * REBUILD THE BOX - Call when strict mode settings change or pages are learned
     * 
     * NOTE: Only learned pages are used. No hardcoded fallbacks.
     * User MUST learn pages/buttons for protection to work.
     */
    fun rebuildBox(context: Context) {
        try {
            val loader = SavedPreferencesLoader(context)
            val strictData = loader.getStrictModeData()
            val learnedPages = loader.getLearnedSettingsPages()
            
            // Update protection flags
            isAccessibilityProtectionEnabled = strictData.isEnabled && strictData.isAccessibilityProtectionEnabled
            isTimeCheatProtectionEnabled = strictData.isEnabled && strictData.isTimeCheatProtectionEnabled
            isAntiUninstallEnabled = strictData.isEnabled && strictData.isAntiUninstallEnabled
            
            val newBox = mutableMapOf<String, BlockedPageConfig>()
            val newWildcards = mutableListOf<BlockedPageConfig>()
            
            // ONLY ADD LEARNED PAGES - No hardcoded fallbacks!
            addLearnedPagesToBox(learnedPages, newBox, newWildcards)
            
            // NOTE: Hardcoded fallbacks removed per user POV
            // Protection only works if user has learned the pages
            
            // Atomic swap
            settingsBox = newBox
            wildcardPages = newWildcards
            
            TerminalLogger.log("SETTINGS_BOX: Rebuilt. ${newBox.size} learned pages, ${newWildcards.size} keyword-based")
            
        } catch (e: Exception) {
            TerminalLogger.log("SETTINGS_BOX: Rebuild error - ${e.message}")
        }
    }
    
    private fun addLearnedPagesToBox(
        learnedPages: Constants.LearnedSettingsPages,
        box: MutableMap<String, BlockedPageConfig>,
        wildcards: MutableList<BlockedPageConfig>
    ) {
        // Use detected OEM settings package as default if available
        val defaultPackage = learnedPages.detectedSettingsPackage.ifEmpty { "com.android.settings" }
        
        // === ACCESSIBILITY PAGE ===
        if (learnedPages.accessibilityPageClass.isNotEmpty() && isAccessibilityProtectionEnabled) {
            val className = learnedPages.accessibilityPageClass.substringAfterLast(".")
            val packageName = learnedPages.accessibilityPagePackage.ifEmpty { defaultPackage }
            
            // Use user-defined keywords if available, otherwise no content check
            val hasKeywords = learnedPages.accessibilityKeywords.isNotEmpty()
            val config = BlockedPageConfig(
                pageType = Constants.PageType.ACCESSIBILITY,
                packageName = packageName,
                className = className,
                requiresContentCheck = hasKeywords,  // Only check if user added keywords
                contentKeywords = learnedPages.accessibilityKeywords,
                blockReason = "Accessibility Protection"
            )
            box[config.getKey()] = config
        }
        
        // === TIME SETTINGS PAGE ===
        if (learnedPages.timeSettingsPageClass.isNotEmpty() && isTimeCheatProtectionEnabled) {
            val className = learnedPages.timeSettingsPageClass.substringAfterLast(".")
            val packageName = learnedPages.timeSettingsPagePackage.ifEmpty { defaultPackage }
            
            val hasKeywords = learnedPages.timeSettingsKeywords.isNotEmpty()
            val config = BlockedPageConfig(
                pageType = Constants.PageType.TIME_SETTINGS,
                packageName = packageName,
                className = className,
                requiresContentCheck = hasKeywords,
                contentKeywords = learnedPages.timeSettingsKeywords,
                blockReason = "Time Settings Protection"
            )
            box[config.getKey()] = config
        }
        
        // === DEVICE ADMIN PAGE ===
        if (learnedPages.deviceAdminPageClass.isNotEmpty() && isAntiUninstallEnabled) {
            val className = learnedPages.deviceAdminPageClass.substringAfterLast(".")
            val packageName = learnedPages.deviceAdminPagePackage.ifEmpty { defaultPackage }

            val hasKeywords = learnedPages.deviceAdminKeywords.isNotEmpty()
            val config = BlockedPageConfig(
                pageType = Constants.PageType.DEVICE_ADMIN,
                packageName = packageName,
                className = className,
                requiresContentCheck = hasKeywords,
                contentKeywords = learnedPages.deviceAdminKeywords,
                blockReason = "Device Admin Protection"
            )
            box[config.getKey()] = config
        }
        
        // === CUSTOM BLOCKED PAGES ===
        for (customPage in learnedPages.customBlockedPages) {
            // Format: ENABLED|NAME|PKG|CLASS|KEYWORDS (comma separated)
            // Legacy: PKG|CLASS
            var isEnabled = true
            var pkg = ""
            var cls = ""
            var keywords = listOf<String>()
            
            val parts = customPage.split("|")
            if (parts.size >= 4 && (parts[0] == "0" || parts[0] == "1")) {
                isEnabled = parts[0] == "1"
                pkg = parts[2]
                cls = parts[3]
                // Keywords are in 5th field if present
                if (parts.size >= 5 && parts[4].isNotEmpty()) {
                    keywords = parts[4].split(",").map { it.trim() }
                }
            } else if (parts.size >= 2) {
                // Legacy format
                pkg = parts[0]
                cls = parts[1]
            }
            
            if (isEnabled && pkg.isNotEmpty() && cls.isNotEmpty()) {
                val config = BlockedPageConfig(
                    pageType = Constants.PageType.APP_INFO, // Using as placeholder for Custom
                    packageName = pkg,
                    className = cls.substringAfterLast("."),
                    requiresContentCheck = keywords.isNotEmpty(),
                    contentKeywords = keywords,
                    blockReason = "Custom Protected Page"
                )
                box[config.getKey()] = config
            }
        }
    }
    
    private fun isProtectionEnabledForType(pageType: Constants.PageType): Boolean {
        return when (pageType) {
            Constants.PageType.ACCESSIBILITY -> isAccessibilityProtectionEnabled
            Constants.PageType.APP_INFO -> false // App Info protection DISABLED
            Constants.PageType.TIME_SETTINGS -> isTimeCheatProtectionEnabled
            Constants.PageType.DEVICE_ADMIN -> isAntiUninstallEnabled
            Constants.PageType.DEVELOPER_OPTIONS -> false // Disabled with App Info
        }
    }
    
    /**
     * SHOULD BLOCK PAGE - Lightning fast O(1) lookup
     * 
     * @param packageName The package of the current window
     * @param className The class of the current window
     * @param rootNode Optional root node for content verification
     * @return BlockResult with blocking decision and reason
     */
    /**
     * SHOULD BLOCK PAGE - Lightning fast O(1) lookup
     * Optimized with lazy text collection and 70% keyword accuracy check
     */
    fun shouldBlockPage(
        packageName: String?,
        className: String?,
        rootNode: AccessibilityNodeInfo? = null
    ): BlockResult {
        if (packageName == null || className == null) {
            return BlockResult(false)
        }
        
        // Quick exit: Not a settings package
        if (!SETTINGS_PACKAGES.any { packageName.contains(it, ignoreCase = true) }) {
            return BlockResult(false)
        }
        
        // Extract simple class name
        val simpleClassName = className.substringAfterLast(".")
        
        // Lazy Text Cache - Scans only ONCE if needed
        var cachedScreenText: String? = null
        
        fun getPageContent(): String {
            if (cachedScreenText == null) {
                if (rootNode == null) return ""
                val sb = StringBuilder()
                // OPTIMIZATION: Scan top 100 nodes (was 15) for better coverage as requested
                collectTextFast(rootNode, sb, depth = 0, maxDepth = 15, nodeCount = intArrayOf(0), maxNodes = 100)
                cachedScreenText = sb.toString().lowercase()
            }
            return cachedScreenText!!
        }
        
        // === STEP 1: O(1) Exact Match ===
        val key = "$packageName|$simpleClassName"
        val exactMatch = settingsBox[key]
        
        if (exactMatch != null) {
            if (!exactMatch.requiresContentCheck) {
                // INSTANT BLOCK - no content check needed (Fastest Path)
                TerminalLogger.log("SETTINGS_BOX: EXACT MATCH BLOCK - $simpleClassName")
                return BlockResult(true, exactMatch.blockReason, exactMatch.pageType)
            } else {
                // Needs content verification (Smart Match)
                val content = getPageContent()
                if (content.isNotEmpty() && checkKeywords(content, exactMatch.contentKeywords)) {
                    TerminalLogger.log("SETTINGS_BOX: CONTENT MATCH BLOCK - $simpleClassName")
                    return BlockResult(true, exactMatch.blockReason, exactMatch.pageType)
                }
            }
        }
        
        // === STEP 2: Check if ambiguous class needs wildcard matching ===
        if (isAmbiguousClass(simpleClassName) && rootNode != null) {
            val content = getPageContent() // Reuse cached content
            if (content.isNotEmpty()) {
                for (wildcard in wildcardPages) {
                    if (!isProtectionEnabledForType(wildcard.pageType)) continue
                    
                    if (checkKeywords(content, wildcard.contentKeywords)) {
                        TerminalLogger.log("SETTINGS_BOX: WILDCARD BLOCK - ${wildcard.pageType}")
                        return BlockResult(true, wildcard.blockReason, wildcard.pageType)
                    }
                }
            }
        }
        
        return BlockResult(false)
    }
    
    /**
     * Smart Keyword Matching
     * - Case insensitive
     * - Requires 70% of provided keywords to be present
     */
    private fun checkKeywords(screenText: String, keywords: List<String>): Boolean {
        if (keywords.isEmpty()) return true
        
        val totalKeywords = keywords.size.toFloat()
        var matchesFound = 0f
        
        for (keyword in keywords) {
            // Case-insensitive check (screenText is already lowercase)
            if (screenText.contains(keyword.lowercase())) {
                matchesFound++
            }
        }
        
        // Check 70% threshold
        val accuracy = matchesFound / totalKeywords
        return accuracy >= 0.7f
    }
    
    /**
     * Fast text collection - limited depth and node count for speed
     */
    private fun collectTextFast(
        node: AccessibilityNodeInfo?,
        builder: StringBuilder,
        depth: Int,
        maxDepth: Int,
        nodeCount: IntArray,
        maxNodes: Int
    ) {
        if (node == null || depth > maxDepth || nodeCount[0] >= maxNodes) return
        
        node.text?.let { builder.append(it).append(" ") }
        node.contentDescription?.let { builder.append(it).append(" ") }
        
        nodeCount[0]++
        
        for (i in 0 until node.childCount.coerceAtMost(5)) {
            try {
                val child = node.getChild(i) ?: continue
                collectTextFast(child, builder, depth + 1, maxDepth, nodeCount, maxNodes)
                try { child.recycle() } catch (_: Exception) {}
            } catch (_: Exception) {}
        }
    }
    
    private fun isAmbiguousClass(className: String): Boolean {
        return AMBIGUOUS_CLASSES.any { className.contains(it, ignoreCase = true) }
    }
    
    /**
     * Check if any protection is currently active
     */
    fun isAnyProtectionActive(): Boolean {
        return isAccessibilityProtectionEnabled || 
               // App Info protection DISABLED
               isTimeCheatProtectionEnabled || 
               isAntiUninstallEnabled
    }
    
    /**
     * Add a custom page to block (for Custom Pages feature)
     */
    fun addCustomPage(context: Context, packageName: String, className: String) {
        val loader = SavedPreferencesLoader(context)
        val learnedPages = loader.getLearnedSettingsPages()
        val key = "$packageName|$className"
        
        if (!learnedPages.customBlockedPages.contains(key)) {
            learnedPages.customBlockedPages.add(key)
            loader.saveLearnedSettingsPages(learnedPages)
            rebuildBox(context)
        }
    }
    
    /**
     * Remove a custom blocked page
     */
    fun removeCustomPage(context: Context, packageName: String, className: String) {
        val loader = SavedPreferencesLoader(context)
        val learnedPages = loader.getLearnedSettingsPages()
        val key = "$packageName|$className"
        
        if (learnedPages.customBlockedPages.contains(key)) {
            learnedPages.customBlockedPages.remove(key)
            loader.saveLearnedSettingsPages(learnedPages)
            rebuildBox(context)
        }
    }
}
