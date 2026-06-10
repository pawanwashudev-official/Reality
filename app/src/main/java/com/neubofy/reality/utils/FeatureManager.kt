package com.neubofy.reality.utils

import android.content.Context
import android.content.SharedPreferences

class FeatureManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("reality_features", Context.MODE_PRIVATE)

    fun isAiEnabled(): Boolean = prefs.getBoolean("feature_ai", true)
    fun setAiEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_ai", enabled).apply()

    fun isNightlyProtocolEnabled(): Boolean = prefs.getBoolean("feature_nightly", true)
    fun setNightlyProtocolEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_nightly", enabled).apply()

    fun isGamificationEnabled(): Boolean = prefs.getBoolean("feature_gamification", true)
    fun setGamificationEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_gamification", enabled).apply()

    fun isTapasyaEnabled(): Boolean = prefs.getBoolean("feature_tapasya", true)
    fun setTapasyaEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_tapasya", enabled).apply()

    fun isReminderEnabled(): Boolean = prefs.getBoolean("feature_reminder", true)
    fun setReminderEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_reminder", enabled).apply()

    fun isHealthConnectEnabled(): Boolean = prefs.getBoolean("feature_health", true)
    fun setHealthConnectEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_health", enabled).apply()
}
