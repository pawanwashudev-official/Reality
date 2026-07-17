package com.neubofy.reality.utils

import android.content.Context
import android.content.SharedPreferences

class FeatureManager(private val context: Context) {
    private val prefs: SharedPreferences = com.neubofy.reality.utils.SecurePreferences.get(context, "reality_features")

    fun isAiEnabled(): Boolean = prefs.getBoolean("feature_ai", false)
    fun setAiEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_ai", enabled).apply()

    fun isRealityProEnabled(): Boolean = prefs.getBoolean("feature_reality_pro", false)
    fun setRealityProEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_reality_pro", enabled).apply()

    fun getTrialStartTime(): Long {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return 0L
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context)
        if (userId.isEmpty()) return 0L
        return prefs.getLong("trial_start_time_$userId", 0L)
    }

    fun getTrialEndTime(): Long {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return 0L
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context)
        if (userId.isEmpty()) return 0L

        var trialEndTime = prefs.getLong("trial_end_time_$userId", 0L)
        return trialEndTime
    }

    fun getRealityProStartTime(): Long {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return 0L
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context)
        if (userId.isEmpty()) return 0L
        return prefs.getLong("feature_reality_pro_start_time_$userId", 0L)
    }

    fun setRealityProStartTime(timeMs: Long) {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context)
        if (userId.isEmpty()) return
        prefs.edit().putLong("feature_reality_pro_start_time_$userId", timeMs).apply()
    }

    fun getRealityProEndTime(): Long {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return 0L
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context)
        if (userId.isEmpty()) return 0L
        return prefs.getLong("feature_reality_pro_verified_until_$userId", 0L)
    }

    fun isRealityProVerified(): Boolean {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return false
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context)
        if (userId.isEmpty()) return false

        // Check for 1-year timestamp
        val verifiedUntil = prefs.getLong("feature_reality_pro_verified_until_$userId", 0L)

        // Backwards compatibility for old boolean verified users (grace period)
        val legacyVerified = prefs.getBoolean("feature_reality_pro_verified_$userId", false)
        if (legacyVerified) {
            // Migrate them to 1 year from now
            setRealityProVerified(true)
            prefs.edit().remove("feature_reality_pro_verified_$userId").apply()
            return true
        }

        val secureNow = SecureTimeProvider.currentTimeMillis(context)
        return verifiedUntil > 0 && secureNow < verifiedUntil
    }

    fun setRealityProVerified(verified: Boolean, currentTimeMs: Long = SecureTimeProvider.currentTimeMillis(context), months: Int = 12) {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context)
        if (userId.isEmpty()) return
        if (verified) {
            val durationMs = (365L / 12) * months * 24 * 60 * 60 * 1000
            val verifiedUntil = currentTimeMs + durationMs
            prefs.edit().putLong("feature_reality_pro_verified_until_$userId", verifiedUntil).apply()
        } else {
            // Batch both removals into a single atomic edit to avoid race conditions
            prefs.edit()
                .remove("feature_reality_pro_verified_until_$userId")
                .remove("feature_reality_pro_verified_$userId")
                .apply()
        }
    }

    // getDeviceUniqueId() removed — was dead code from an older device-binding approach


    fun isTrialActive(): Boolean {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return false
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context)
        if (userId.isEmpty()) return false

        // 1. Check local secure prefs
        var trialEndTime = prefs.getLong("trial_end_time_$userId", 0L)


        return trialEndTime > 0L && SecureTimeProvider.currentTimeMillis(context) < trialEndTime
    }

    fun hasUsedTrial(): Boolean {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return false
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context)
        if (userId.isEmpty()) return false

        // Check local
        if (prefs.contains("trial_end_time_$userId")) return true

        return false
    }




    fun isTapasyaEnabled(): Boolean = prefs.getBoolean("feature_tapasya", false)
    fun setTapasyaEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_tapasya", enabled).apply()

    fun isReminderEnabled(): Boolean = prefs.getBoolean("feature_reminder", false)
    fun setReminderEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_reminder", enabled).apply()

    fun isHealthConnectEnabled(): Boolean = prefs.getBoolean("feature_health", false)
    fun setHealthConnectEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_health", enabled).apply()
}
