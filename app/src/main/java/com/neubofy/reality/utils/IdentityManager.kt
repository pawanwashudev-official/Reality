package com.neubofy.reality.utils

import android.content.Context
import com.neubofy.reality.BuildConfig
import com.neubofy.reality.google.GoogleAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object IdentityManager {

    private const val PREFS_NAME = "reality_identity_prefs"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_BACKUP_PASSWORD = "backup_password"

    /**
     * Fetches the cached userId. If it doesn't exist, synchronously calls the API to generate and cache it.
     */
    fun getUserId(context: Context): String {
        val prefs = SecurePreferences.get(context, PREFS_NAME)
        val cachedId = prefs.getString(KEY_USER_ID, null)
        if (cachedId != null) {
            return cachedId
        }

        return kotlinx.coroutines.runBlocking(Dispatchers.IO) { generateAndCacheIdentity(context); prefs.getString(KEY_USER_ID, "Unknown") ?: "Unknown" }
    }

    /**
     * Fetches the cached backupPassword. If it doesn't exist, synchronously calls the API to generate and cache it.
     */
    fun getBackupPassword(context: Context): String {
        val prefs = SecurePreferences.get(context, PREFS_NAME)
        val cachedPassword = prefs.getString(KEY_BACKUP_PASSWORD, null)
        if (cachedPassword != null) {
            return cachedPassword
        }

        return kotlinx.coroutines.runBlocking(Dispatchers.IO) { generateAndCacheIdentity(context); prefs.getString(KEY_BACKUP_PASSWORD, "Unknown") ?: "Unknown" }
    }

    suspend fun refreshIdentity(context: Context) {
        generateAndCacheIdentity(context)
    }

    private suspend fun generateAndCacheIdentity(context: Context) {
        withContext(Dispatchers.IO) {
            val email = GoogleAuthManager.getUserEmail(context)
            if (email.isNullOrBlank()) {
                return@withContext
            }

            try {
                val workerUrl = BuildConfig.WORKER_URL.removeSuffix("/")
                val url = URL("$workerUrl/api/generate-identity")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true

                val jsonInputString = JSONObject().apply {
                    put("email", email)
                }.toString()

                conn.outputStream.use { os ->
                    val input = jsonInputString.toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                    val responseJson = JSONObject(responseStr)

                    val userId = responseJson.optString("userId")
                    val backupPassword = responseJson.optString("backupPassword")

                    if (userId.isNotEmpty() && backupPassword.isNotEmpty()) {
                        SecurePreferences.get(context, PREFS_NAME).edit().apply {
                            putString(KEY_USER_ID, userId)
                            putString(KEY_BACKUP_PASSWORD, backupPassword)
                            apply()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun clearIdentity(context: Context) {
        SecurePreferences.get(context, PREFS_NAME).edit().clear().apply()
    }
}
