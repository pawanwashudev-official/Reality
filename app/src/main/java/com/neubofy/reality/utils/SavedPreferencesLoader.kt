package com.neubofy.reality.utils

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.neubofy.reality.Constants
import com.neubofy.reality.blockers.RealityBlocker

class SavedPreferencesLoader(private val context: Context) {

    private val gson = Gson()

    fun saveAutoFocusHoursList(list: List<Constants.AutoTimedActionItem>) {
        val sharedPreferences = context.getSharedPreferences("auto_focus_hours", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("auto_focus_list", gson.toJson(list)).apply()
    }

    fun loadAutoFocusHoursList(): MutableList<Constants.AutoTimedActionItem> {
        val sharedPreferences = context.getSharedPreferences("auto_focus_hours", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("auto_focus_list", null)
        if (json.isNullOrEmpty()) return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<Constants.AutoTimedActionItem>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) { mutableListOf() }
    }

    fun saveAppBlockerWarningInfo(warningData: Constants.WarningData) {
        val sharedPreferences = context.getSharedPreferences("warning_data", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("app_blocker", gson.toJson(warningData)).apply()
    }

    fun loadAppBlockerWarningInfo(): Constants.WarningData {
        val sharedPreferences = context.getSharedPreferences("warning_data", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("app_blocker", null)
        if (json.isNullOrEmpty()) return Constants.WarningData()
        return try {
            val type = object : TypeToken<Constants.WarningData>() {}.type
            gson.fromJson(json, type) ?: Constants.WarningData()
        } catch (e: Exception) { Constants.WarningData() }
    }
    
    fun loadBlockedApps(): Set<String> {
        // UNIFIED: Use FocusModeData.selectedApps as source of truth
        val focusData = getFocusModeData()
        // Defensive: R8/Gson might leave this null despite Kotlin non-null type
        val apps = focusData.selectedApps
        if (apps != null && apps.isNotEmpty()) {
            return apps.toSet()
        }
        // Fallback to legacy selected_apps list
        val legacyApps = getFocusModeSelectedApps()
        if (legacyApps.isNotEmpty()) {
            return legacyApps.toSet()
        }
        // Last resort: old blocked_apps key (for very old data)
        val sharedPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        return sharedPreferences.getStringSet("blocked_apps", emptySet()) ?: emptySet()
    }

    fun saveFocusModeData(data: RealityBlocker.FocusModeData) {
        val sharedPreferences = context.getSharedPreferences("focus_mode", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("focus_mode_v2", gson.toJson(data)).apply()
    }

    fun getFocusModeData(): RealityBlocker.FocusModeData {
        val sharedPreferences = context.getSharedPreferences("focus_mode", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("focus_mode_v2", null)
        if (json.isNullOrEmpty()) return RealityBlocker.FocusModeData()
        return try {
            val type = object : TypeToken<RealityBlocker.FocusModeData>() {}.type
            gson.fromJson(json, type) ?: RealityBlocker.FocusModeData()
        } catch (e: Exception) { RealityBlocker.FocusModeData() }
    }

    fun saveFocusModeSelectedApps(appList: List<String>) {
        val sharedPreferences = context.getSharedPreferences("focus_mode", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("selected_apps", gson.toJson(appList)).apply()
    }

    fun getFocusModeSelectedApps(): List<String> {
        val sharedPreferences = context.getSharedPreferences("focus_mode", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("selected_apps", null)
        if (json.isNullOrEmpty()) return listOf()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson(json, type) ?: listOf()
        } catch (e: Exception) { listOf() }
    }
    
    // Bedtime Mode
    fun saveBedtimeData(data: Constants.BedtimeData) {
        val sharedPreferences = context.getSharedPreferences("bedtime_mode", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("bedtime_data", gson.toJson(data)).apply()
    }
    
    fun getBedtimeData(): Constants.BedtimeData {
        val sharedPreferences = context.getSharedPreferences("bedtime_mode", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("bedtime_data", null)
        if (json.isNullOrEmpty()) return Constants.BedtimeData()
        return try {
            val type = object : TypeToken<Constants.BedtimeData>() {}.type
            gson.fromJson(json, type) ?: Constants.BedtimeData()
        } catch (e: Exception) { Constants.BedtimeData() }
    }
    
    // Emergency Mode
    fun saveEmergencyData(data: Constants.EmergencyModeData) {
        val sharedPreferences = context.getSharedPreferences("emergency_mode", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("emergency_data", gson.toJson(data)).apply()
    }
   
    fun getEmergencyData(): Constants.EmergencyModeData {
        val sharedPreferences = context.getSharedPreferences("emergency_mode", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("emergency_data", null)
        if (json.isNullOrEmpty()) return Constants.EmergencyModeData()
        return try {
            val type = object : TypeToken<Constants.EmergencyModeData>() {}.type
            gson.fromJson(json, type) ?: Constants.EmergencyModeData()
        } catch (e: Exception) { Constants.EmergencyModeData() }
    }
    
    // Usage Limit Data
    fun saveUsageLimitData(data: Constants.UsageLimitData) {
        val sharedPreferences = context.getSharedPreferences("usage_limit", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("usage_data", gson.toJson(data)).apply()
    }
    
    fun getUsageLimitData(): Constants.UsageLimitData {
        val sharedPreferences = context.getSharedPreferences("usage_limit", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("usage_data", null)
        if (json.isNullOrEmpty()) return Constants.UsageLimitData()
        return try {
            val type = object : TypeToken<Constants.UsageLimitData>() {}.type
            gson.fromJson(json, type) ?: Constants.UsageLimitData()
        } catch (e: Exception) { Constants.UsageLimitData() }
    }

    // Strict Mode Data
    fun saveStrictModeData(data: Constants.StrictModeData) {
        val sharedPreferences = context.getSharedPreferences("strict_mode", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("strict_data", gson.toJson(data)).apply()
    }
    
    fun getStrictModeData(): Constants.StrictModeData {
        val sharedPreferences = context.getSharedPreferences("strict_mode", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("strict_data", null)
        if (json.isNullOrEmpty()) return Constants.StrictModeData()
        return try {
            val type = object : TypeToken<Constants.StrictModeData>() {}.type
            gson.fromJson(json, type) ?: Constants.StrictModeData()
        } catch (e: Exception) { Constants.StrictModeData() }
    }

    fun isStrictModeEnabled(): Boolean {
        return getStrictModeData().isEnabled
    }
    
    // Learned Settings Pages (for Settings Page Learning feature)
    fun saveLearnedSettingsPages(data: Constants.LearnedSettingsPages) {
        val sharedPreferences = context.getSharedPreferences("strict_mode", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("learned_pages", gson.toJson(data)).apply()
    }
    
    fun getLearnedSettingsPages(): Constants.LearnedSettingsPages {
        val sharedPreferences = context.getSharedPreferences("strict_mode", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("learned_pages", null)
        if (json.isNullOrEmpty()) return Constants.LearnedSettingsPages()
        return try {
            val type = object : TypeToken<Constants.LearnedSettingsPages>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            Constants.LearnedSettingsPages()
        }
    }
    
    // Auto DND
    fun saveAutoDndEnabled(enabled: Boolean) {
        val sharedPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("auto_dnd_enabled", enabled).apply()
    }
    
    fun isAutoDndEnabled(): Boolean {
        val sharedPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("auto_dnd_enabled", false)
    }

    // Reality Sleep Mode (Android 15+ ZenDeviceEffects)
    fun saveRealitySleepEnabled(enabled: Boolean) {
        val sharedPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("reality_sleep_enabled", enabled).apply()
    }

    fun isRealitySleepEnabled(): Boolean {
        val sharedPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("reality_sleep_enabled", false)
    }

    // Smart Sleep Monitoring
    fun saveSmartSleepEnabled(enabled: Boolean) {
        val sharedPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("smart_sleep_enabled", enabled).apply()
    }

    fun isSmartSleepEnabled(): Boolean {
        val sharedPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("smart_sleep_enabled", false)
    }

    fun saveSleepSyncConfirmed(date: String, confirmed: Boolean) {
        val sharedPreferences = context.getSharedPreferences("health_sync_prefs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("confirmed_$date", confirmed).apply()
    }

    fun isSleepSyncConfirmed(date: String): Boolean {
        val sharedPreferences = context.getSharedPreferences("health_sync_prefs", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean("confirmed_$date", false)
    }

    // Custom Block Messages
    fun saveBlockMessages(messages: List<Constants.BlockMessage>) {
        val sharedPreferences = context.getSharedPreferences("block_messages", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("messages_list", gson.toJson(messages)).apply()
    }

    fun getBlockMessages(): MutableList<Constants.BlockMessage> {
        val sharedPreferences = context.getSharedPreferences("block_messages", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("messages_list", null)
        if (json.isNullOrEmpty()) {
            // Return default messages if empty
            return mutableListOf(
                Constants.BlockMessage(message = "Stay focused on your goals.", tags = listOf("ALL")),
                Constants.BlockMessage(message = "You can do this!", tags = listOf("ALL")),
                Constants.BlockMessage(message = "Is this really important?", tags = listOf("FOCUS")),
                Constants.BlockMessage(message = "Go to sleep, tomorrow is a new day.", tags = listOf("BEDTIME"))
            )
        }
        return try {
            val type = object : TypeToken<MutableList<Constants.BlockMessage>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) { mutableListOf() }
    }
    // Custom Reminders
    fun saveCustomReminders(list: List<com.neubofy.reality.data.CustomReminder>) {
        val sharedPreferences = context.getSharedPreferences("custom_reminders", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("reminders_list", gson.toJson(list)).apply()
    }

    fun loadCustomReminders(): MutableList<com.neubofy.reality.data.CustomReminder> {
        val sharedPreferences = context.getSharedPreferences("custom_reminders", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("reminders_list", null)
        if (json.isNullOrEmpty()) return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<com.neubofy.reality.data.CustomReminder>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            com.neubofy.reality.utils.TerminalLogger.log("DATA ERROR: Failed to load reminders - ${e.message}")
            mutableListOf()
        }
    }

    fun saveBoolean(key: String, value: Boolean) {
        val sharedPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean(key, value).apply()
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        val sharedPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(key, defaultValue)
    }

    // App Theme
    fun saveThemeMode(mode: Int) {
        // 0 = System, 1 = Light, 2 = Dark
        val sharedPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().putInt("theme_mode", mode).apply()
    }

    fun getThemeMode(): Int {
        val sharedPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        return sharedPreferences.getInt("theme_mode", 0) // Default to System (0)
    }
    
    // Per-App Mode Configuration
    fun saveBlockedAppConfigs(configs: List<Constants.BlockedAppConfig>) {
        val sharedPreferences = context.getSharedPreferences("blocked_app_configs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("configs_list", gson.toJson(configs)).apply()
    }
    
    fun loadBlockedAppConfigs(): MutableList<Constants.BlockedAppConfig> {
        val sharedPreferences = context.getSharedPreferences("blocked_app_configs", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("configs_list", null)
        if (json.isNullOrEmpty()) return mutableListOf()
        return try {
            val type = object : TypeToken<MutableList<Constants.BlockedAppConfig>>() {}.type
            gson.fromJson(json, type) ?: mutableListOf()
        } catch (e: Exception) { mutableListOf() }
    }
    
    /**
     * Get config for a specific package. Returns default (all modes enabled) if not found.
     */
    fun getBlockedAppConfig(packageName: String): Constants.BlockedAppConfig {
        val configs = loadBlockedAppConfigs()
        return configs.find { it.packageName == packageName }
            ?: Constants.BlockedAppConfig(packageName)  // Default: all modes enabled
    }
    
    /**
     * Update or add config for a specific package.
     */
    fun updateBlockedAppConfig(config: Constants.BlockedAppConfig) {
        val configs = loadBlockedAppConfigs()
        val index = configs.indexOfFirst { it.packageName == config.packageName }
        if (index >= 0) {
            configs[index] = config
        } else {
            configs.add(config)
        }
        saveBlockedAppConfigs(configs)
    }
}