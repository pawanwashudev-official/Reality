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
        val type = object : TypeToken<MutableList<Constants.AutoTimedActionItem>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveAppBlockerWarningInfo(warningData: Constants.WarningData) {
        val sharedPreferences = context.getSharedPreferences("warning_data", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("app_blocker", gson.toJson(warningData)).apply()
    }

    fun loadAppBlockerWarningInfo(): Constants.WarningData {
        val sharedPreferences = context.getSharedPreferences("warning_data", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("app_blocker", null)
        if (json.isNullOrEmpty()) return Constants.WarningData()
        val type = object : TypeToken<Constants.WarningData>() {}.type
        return gson.fromJson(json, type)
    }
    
    fun loadBlockedApps(): Set<String> {
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
        val type = object : TypeToken<RealityBlocker.FocusModeData>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveFocusModeSelectedApps(appList: List<String>) {
        val sharedPreferences = context.getSharedPreferences("focus_mode", Context.MODE_PRIVATE)
        sharedPreferences.edit().putString("selected_apps", gson.toJson(appList)).apply()
    }

    fun getFocusModeSelectedApps(): List<String> {
        val sharedPreferences = context.getSharedPreferences("focus_mode", Context.MODE_PRIVATE)
        val json = sharedPreferences.getString("selected_apps", null)
        if (json.isNullOrEmpty()) return listOf()
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(json, type)
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
        val type = object : TypeToken<Constants.BedtimeData>() {}.type
        return gson.fromJson(json, type)
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
        val type = object : TypeToken<Constants.EmergencyModeData>() {}.type
        return gson.fromJson(json, type)
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
        val type = object : TypeToken<Constants.UsageLimitData>() {}.type
        return gson.fromJson(json, type)
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
        val type = object : TypeToken<Constants.StrictModeData>() {}.type
        return gson.fromJson(json, type)
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
}