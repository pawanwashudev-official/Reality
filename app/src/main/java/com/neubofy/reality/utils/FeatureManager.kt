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

        val secureNow = SecureTimeProvider.currentTimeMillis(context)
        val isValid = verifiedUntil > 0 && secureNow < verifiedUntil
        if (!isValid && verifiedUntil > 0) {
            // Subscription expired. Wipe it out directly.
            setRealityProVerified(false)
            val proPrefs = com.neubofy.reality.utils.SecurePreferences.get(context, "reality_pro_prefs")
            proPrefs.edit().remove("pro_saved_verification_code_for_$userId").apply()
            prefs.edit().remove("feature_reality_pro_start_time_$userId").apply()
        }
        return isValid
    }

    fun setRealityProVerified(verified: Boolean, currentTimeMs: Long = SecureTimeProvider.currentTimeMillis(context), months: Int = 12) {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context)
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

        // 1. Check local secure prefs
        var trialEndTime = prefs.getLong("trial_end_time_$userId", 0L)


        return trialEndTime > 0L && SecureTimeProvider.currentTimeMillis(context) < trialEndTime
    }

    fun hasUsedTrial(): Boolean {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return false
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context)

        // Check local
        if (prefs.contains("trial_end_time_$userId")) return true

        return false
    }

    suspend fun activateTrial(currentTimeMs: Long = System.currentTimeMillis()): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val userEmail = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(context) ?: return@withContext false
        val userId = com.neubofy.reality.utils.IdentityManager.getUserId(context)
        val password = com.neubofy.reality.utils.IdentityManager.getBackupPassword(context)
        
        try {
            val workerUrl = com.neubofy.reality.BuildConfig.WORKER_URL.removeSuffix("/")
            val url = java.net.URL("$workerUrl/api/trial")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            
            val jsonBody = org.json.JSONObject()
            jsonBody.put("userId", userId)
            jsonBody.put("password", password)
            jsonBody.put("durationDays", 3)
            
            java.io.OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }
            
            if (conn.responseCode == java.net.HttpURLConnection.HTTP_OK) {
                val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                val responseJson = org.json.JSONObject(responseStr)
                val status = responseJson.optString("status")
                val trialPlan = responseJson.optString("trialPlan")
                
                if (trialPlan.isNotEmpty() && trialPlan != "null") {
                    val parts = trialPlan.split("-")
                    if (parts.size >= 2) {
                        val trialEndTime = parts[0].toLong()
                        val days = parts[1].toLong()
                        val trialStartTime = trialEndTime - (days * 24 * 60 * 60 * 1000)
                        
                        val editor = prefs.edit()
                        editor.putLong("trial_end_time_$userId", trialEndTime)
                        if (prefs.getLong("trial_start_time_$userId", 0L) == 0L) {
                            editor.putLong("trial_start_time_$userId", trialStartTime)
                        }
                        editor.apply()
                    }
                }
                return@withContext status == "SUCCESS" || status == "ALREADY_USED"
            }
        } catch (e: Exception) {
            com.neubofy.reality.utils.TerminalLogger.log("TRIAL API ERROR: ${e.message}")
        }
        return@withContext false
    }


    fun isTapasyaEnabled(): Boolean = prefs.getBoolean("feature_tapasya", false)
    fun setTapasyaEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_tapasya", enabled).apply()

    fun isReminderEnabled(): Boolean = prefs.getBoolean("feature_reminder", false)
    fun setReminderEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_reminder", enabled).apply()

    fun isHealthConnectEnabled(): Boolean = prefs.getBoolean("feature_health", false)
    fun setHealthConnectEnabled(enabled: Boolean) = prefs.edit().putBoolean("feature_health", enabled).apply()
}
