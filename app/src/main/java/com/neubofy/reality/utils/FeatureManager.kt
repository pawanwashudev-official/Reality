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
        return prefs.getLong("trial_start_time_$userId", 0L)
    }

    fun getTrialEndTime(): Long {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return 0L
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context)

        var trialEndTime = prefs.getLong("trial_end_time_$userId", 0L)
        return trialEndTime
    }

    fun getRealityProStartTime(): Long {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return 0L
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context)
        return prefs.getLong("feature_reality_pro_start_time_$userId", 0L)
    }

    fun setRealityProStartTime(timeMs: Long) {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context)
        prefs.edit().putLong("feature_reality_pro_start_time_$userId", timeMs).apply()
    }

    fun getRealityProEndTime(): Long {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return 0L
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context)
        return prefs.getLong("feature_reality_pro_verified_until_$userId", 0L)
    }

    fun isRealityProVerified(): Boolean {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return false
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context)

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

        val isValid = verifiedUntil > 0 && System.currentTimeMillis() < verifiedUntil
        if (!isValid && verifiedUntil > 0) {
            // Subscription expired. Wipe it out directly.
            setRealityProVerified(false)
            val proPrefs = com.neubofy.reality.utils.SecurePreferences.get(context, "reality_pro_prefs")
            proPrefs.edit().remove("pro_saved_verification_code_for_$userId").apply()
            prefs.edit().remove("feature_reality_pro_start_time_$userId").apply()
        }
        return isValid
    }

    fun setRealityProVerified(verified: Boolean, currentTimeMs: Long = System.currentTimeMillis(), months: Int = 12) {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context)
        if (verified) {
            val durationMs = (365L / 12) * months * 24 * 60 * 60 * 1000
            val verifiedUntil = currentTimeMs + durationMs
            prefs.edit().putLong("feature_reality_pro_verified_until_$userId", verifiedUntil).apply()
        } else {
            prefs.edit().remove("feature_reality_pro_verified_until_$userId").apply()
            prefs.edit().remove("feature_reality_pro_verified_$userId").apply()
        }
    }

    private fun getDeviceUniqueId(): String {
        return android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown"
    }


    fun isTrialActive(): Boolean {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return false
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context)

        // 1. Check local secure prefs
        var trialEndTime = prefs.getLong("trial_end_time_$userId", 0L)


        return trialEndTime > 0L && System.currentTimeMillis() < trialEndTime
    }

    fun hasUsedTrial(): Boolean {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return false
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context)

        // Check local
        if (prefs.contains("trial_end_time_$userId")) return true

        return false
    }

    fun activateTrial(currentTimeMs: Long = System.currentTimeMillis()) {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context)
        val trialDurationMs = 3L * 24 * 60 * 60 * 1000
        val currentTime = currentTimeMs
        val trialEndTime = currentTime + trialDurationMs

        // 1. Save local
        val editor = prefs.edit()
        editor.putLong("trial_end_time_$userId", trialEndTime)
        if (prefs.getLong("trial_start_time_$userId", 0L) == 0L) {
            editor.putLong("trial_start_time_$userId", currentTime)
        }
        editor.apply()

    }


    fun isTapasyaEnabled(): Boolean = prefs.getBoolean("feature_tapasya", false)
    fun setTapasyaEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_tapasya", enabled).apply()

    fun isReminderEnabled(): Boolean = prefs.getBoolean("feature_reminder", false)
    fun setReminderEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_reminder", enabled).apply()

    fun isHealthConnectEnabled(): Boolean = prefs.getBoolean("feature_health", false)
    fun setHealthConnectEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_health", enabled).apply()
}
