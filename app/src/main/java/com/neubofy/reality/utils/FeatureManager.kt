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
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context, userEmail)
        return prefs.getLong("trial_start_time_$userId", 0L)
    }

    fun getTrialEndTime(): Long {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return 0L
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context, userEmail)
        return prefs.getLong("trial_end_time_$userId", 0L)
    }

    fun getRealityProStartTime(): Long {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return 0L
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context, userEmail)
        return prefs.getLong("feature_reality_pro_start_time_$userId", 0L)
    }

    fun setRealityProStartTime(timeMs: Long) {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context, userEmail)
        val currentStart = prefs.getLong("feature_reality_pro_start_time_$userId", 0L)
        if (currentStart == 0L) {
            prefs.edit().putLong("feature_reality_pro_start_time_$userId", timeMs).apply()
        }
    }

    fun getRealityProEndTime(): Long {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return 0L
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context, userEmail)
        return prefs.getLong("feature_reality_pro_verified_until_$userId", 0L)
    }

    fun isRealityProVerified(): Boolean {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return false
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context, userEmail)

        val verifiedUntil = prefs.getLong("feature_reality_pro_verified_until_$userId", 0L)
        val legacyVerified = prefs.getBoolean("feature_reality_pro_verified_$userId", false)
        if (legacyVerified) {
            setRealityProVerified(true)
            prefs.edit().remove("feature_reality_pro_verified_$userId").apply()
            return true
        }

        val isValid = verifiedUntil > 0 && System.currentTimeMillis() < verifiedUntil
        if (!isValid && verifiedUntil > 0) {
            setRealityProVerified(false)
            val proPrefs = com.neubofy.reality.utils.SecurePreferences.get(context, "reality_pro_prefs")
            proPrefs.edit().remove("pro_saved_verification_code_for_$userId").apply()
            prefs.edit().remove("feature_reality_pro_start_time_$userId").apply()
        }
        return isValid
    }

    fun setRealityProVerified(verified: Boolean, currentTimeMs: Long = System.currentTimeMillis(), months: Int = 12) {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context, userEmail)
        if (verified) {
            val oneYearMs = (365L / 12) * months * 24 * 60 * 60 * 1000
            val verifiedUntil = currentTimeMs + oneYearMs
            prefs.edit().putLong("feature_reality_pro_verified_until_$userId", verifiedUntil).apply()
        } else {
            prefs.edit().remove("feature_reality_pro_verified_until_$userId").apply()
            prefs.edit().remove("feature_reality_pro_verified_$userId").apply()
        }
    }

    fun isTrialActive(): Boolean {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return false
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context, userEmail)
        val trialEndTime = prefs.getLong("trial_end_time_$userId", 0L)
        return trialEndTime > 0L && System.currentTimeMillis() < trialEndTime
    }

    fun hasUsedTrial(): Boolean {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return false
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context, userEmail)
        return prefs.contains("trial_end_time_$userId")
    }

    fun activateTrial(currentTimeMs: Long = System.currentTimeMillis()) {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context, userEmail)
        val trialDurationMs = 3L * 24 * 60 * 60 * 1000
        val trialEndTime = currentTimeMs + trialDurationMs
        val editor = prefs.edit()
        editor.putLong("trial_end_time_$userId", trialEndTime)
        if (prefs.getLong("trial_start_time_$userId", 0L) == 0L) {
            editor.putLong("trial_start_time_$userId", currentTimeMs)
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
