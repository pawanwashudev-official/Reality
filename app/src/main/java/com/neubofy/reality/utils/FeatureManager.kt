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
        val userId = com.neubofy.reality.utils.MD5Utils.getUserIdFromEmail(userEmail)
        return prefs.getLong("trial_start_time_$userId", 0L)
    }

    fun getTrialEndTime(): Long {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return 0L
        val userId = com.neubofy.reality.utils.MD5Utils.getUserIdFromEmail(userEmail)

        var trialEndTime = prefs.getLong("trial_end_time_$userId", 0L)
        if (trialEndTime == 0L) {
            try {
                val extFile = getExternalTrialFile()
                if (extFile != null && extFile.exists()) {
                    val content = extFile.readText()
                    val lines = content.split("\n")
                    for (line in lines) {
                        val parts = line.split("=")
                        if (parts.size == 2 && (parts[0] == userId || parts[0] == getDeviceUniqueId())) {
                            trialEndTime = parts[1].toLong()
                            prefs.edit().putLong("trial_end_time_$userId", trialEndTime).apply()
                            break
                        }
                    }
                }
            } catch (e: Exception) {}
        }
        return trialEndTime
    }

    fun getRealityProStartTime(): Long {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return 0L
        val userId = com.neubofy.reality.utils.MD5Utils.getUserIdFromEmail(userEmail)
        return prefs.getLong("feature_reality_pro_start_time_$userId", 0L)
    }

    fun setRealityProStartTime(timeMs: Long) {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return
        val userId = com.neubofy.reality.utils.MD5Utils.getUserIdFromEmail(userEmail)
        val currentStart = prefs.getLong("feature_reality_pro_start_time_$userId", 0L)
        if (currentStart == 0L) {
            prefs.edit().putLong("feature_reality_pro_start_time_$userId", timeMs).apply()
        }
    }

    fun getRealityProEndTime(): Long {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return 0L
        val userId = com.neubofy.reality.utils.MD5Utils.getUserIdFromEmail(userEmail)
        return prefs.getLong("feature_reality_pro_verified_until_$userId", 0L)
    }

    fun isRealityProVerified(): Boolean {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return false
        val userId = com.neubofy.reality.utils.MD5Utils.getUserIdFromEmail(userEmail)

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

        return System.currentTimeMillis() < verifiedUntil
    }

    fun setRealityProVerified(verified: Boolean) {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return
        val userId = com.neubofy.reality.utils.MD5Utils.getUserIdFromEmail(userEmail)
        if (verified) {
            val oneYearMs = 365L * 24 * 60 * 60 * 1000
            val verifiedUntil = System.currentTimeMillis() + oneYearMs
            prefs.edit().putLong("feature_reality_pro_verified_until_$userId", verifiedUntil).apply()
        } else {
            prefs.edit().remove("feature_reality_pro_verified_until_$userId").apply()
        }
    }

    private fun getDeviceUniqueId(): String {
        return android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown"
    }

    private fun getExternalTrialFile(): java.io.File? {
        try {
            // Store a hidden file in the public Documents or Downloads directory
            // This survives app uninstall and data clearing.
            val dir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
            if (!dir.exists()) dir.mkdirs()
            val file = java.io.File(dir, ".reality_engine_sys_config")
            return file
        } catch (e: Exception) {
            return null
        }
    }

    fun isTrialActive(): Boolean {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return false
        val userId = com.neubofy.reality.utils.MD5Utils.getUserIdFromEmail(userEmail)

        // 1. Check local secure prefs
        var trialEndTime = prefs.getLong("trial_end_time_$userId", 0L)

        // 2. Fallback to external file if data was cleared
        if (trialEndTime == 0L) {
            try {
                val extFile = getExternalTrialFile()
                if (extFile != null && extFile.exists()) {
                    val content = extFile.readText()
                    // Format: userId:endTime,deviceId:endTime
                    val lines = content.split("\n")
                    for (line in lines) {
                        val parts = line.split("=")
                        if (parts.size == 2 && (parts[0] == userId || parts[0] == getDeviceUniqueId())) {
                            trialEndTime = parts[1].toLong()
                            // Restore to local prefs
                            prefs.edit().putLong("trial_end_time_$userId", trialEndTime).apply()
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore IO errors
            }
        }

        return trialEndTime > 0L && System.currentTimeMillis() < trialEndTime
    }

    fun hasUsedTrial(): Boolean {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return false
        val userId = com.neubofy.reality.utils.MD5Utils.getUserIdFromEmail(userEmail)

        // Check local
        if (prefs.contains("trial_end_time_$userId")) return true

        // Check external to prevent bypass via data clear
        try {
            val extFile = getExternalTrialFile()
            if (extFile != null && extFile.exists()) {
                val content = extFile.readText()
                if (content.contains(userId) || content.contains(getDeviceUniqueId())) {
                    return true
                }
            }
        } catch (e: Exception) {}

        return false
    }

    fun activateTrial() {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return
        val userId = com.neubofy.reality.utils.MD5Utils.getUserIdFromEmail(userEmail)
        val trialDurationMs = 3L * 24 * 60 * 60 * 1000
        val currentTime = System.currentTimeMillis()
        val trialEndTime = currentTime + trialDurationMs

        // 1. Save local
        val editor = prefs.edit()
        editor.putLong("trial_end_time_$userId", trialEndTime)
        if (prefs.getLong("trial_start_time_$userId", 0L) == 0L) {
            editor.putLong("trial_start_time_$userId", currentTime)
        }
        editor.apply()

        // 2. Save external to survive data clear
        try {
            val extFile = getExternalTrialFile()
            if (extFile != null) {
                val deviceId = getDeviceUniqueId()
                extFile.appendText("$userId=$trialEndTime\n")
                extFile.appendText("$deviceId=$trialEndTime\n")
            }
        } catch (e: Exception) {
            // Ignore
        }
    }


    fun isTapasyaEnabled(): Boolean = prefs.getBoolean("feature_tapasya", false)
    fun setTapasyaEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_tapasya", enabled).apply()

    fun isReminderEnabled(): Boolean = prefs.getBoolean("feature_reminder", false)
    fun setReminderEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_reminder", enabled).apply()

    fun isHealthConnectEnabled(): Boolean = prefs.getBoolean("feature_health", false)
    fun setHealthConnectEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_health", enabled).apply()
}
