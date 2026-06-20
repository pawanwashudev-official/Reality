package com.neubofy.reality.utils

import android.content.Context
import android.content.SharedPreferences

class FeatureManager(private val context: Context) {
    private val prefs: SharedPreferences = com.neubofy.reality.utils.SecurePreferences.get(context, "reality_features")

    fun isAiEnabled(): Boolean = prefs.getBoolean("feature_ai", false)
    fun setAiEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_ai", enabled).apply()

    fun isRealityProEnabled(): Boolean = prefs.getBoolean("feature_reality_pro", false)
    fun setRealityProEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_reality_pro", enabled).apply()

    fun isRealityProVerified(): Boolean {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return false
        val userId = com.neubofy.reality.utils.MD5Utils.getUserIdFromEmail(userEmail)
        return prefs.getBoolean("feature_reality_pro_verified_$userId", false)
    }

    fun setRealityProVerified(verified: Boolean) {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return
        val userId = com.neubofy.reality.utils.MD5Utils.getUserIdFromEmail(userEmail)
        prefs.edit().putBoolean("feature_reality_pro_verified_$userId", verified).apply()
    }

    fun isTapasyaEnabled(): Boolean = prefs.getBoolean("feature_tapasya", false)
    fun setTapasyaEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_tapasya", enabled).apply()

    fun isReminderEnabled(): Boolean = prefs.getBoolean("feature_reminder", false)
    fun setReminderEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_reminder", enabled).apply()

    fun isHealthConnectEnabled(): Boolean = prefs.getBoolean("feature_health", false)
    fun setHealthConnectEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_health", enabled).apply()
}
