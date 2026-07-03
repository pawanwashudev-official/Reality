package com.neubofy.reality.utils

import android.content.Context
import com.neubofy.reality.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Identity Manager
 *
 * Handles the generation and fetching of `userId` and `backupPassword`.
 * Uses a server-side deterministic HMAC-SHA256 calculation via our Cloudflare proxy
 * instead of legacy client-side MD5 flow, completely avoiding local vulnerabilities.
 */
object IdentityManager {

    private const val PREFS_NAME = "reality_identity_prefs"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_BACKUP_PASSWORD = "backup_password"

    private fun getPrefs(context: Context) = SecurePreferences.get(context, PREFS_NAME)

    fun getUserId(context: Context, email: String): String {
        val prefs = getPrefs(context)
        val cachedUserId = prefs.getString("${KEY_USER_ID}_$email", null)
        if (cachedUserId != null) return cachedUserId
        return runBlocking { fetchIdentitySync(context, email)?.userId ?: "" }
    }

    fun getBackupPassword(context: Context, email: String): String {
        val prefs = getPrefs(context)
        val cachedBackupPwd = prefs.getString("${KEY_BACKUP_PASSWORD}_$email", null)
        if (cachedBackupPwd != null) return cachedBackupPwd
        return runBlocking { fetchIdentitySync(context, email)?.backupPassword ?: "" }
    }

    fun clearIdentity(context: Context) {
        getPrefs(context).edit().clear().apply()
    }

    suspend fun refreshIdentity(context: Context, email: String) {
        withContext(Dispatchers.IO) { fetchIdentitySync(context, email) }
    }

    data class IdentityResponse(val userId: String, val backupPassword: String)

    private fun fetchIdentitySync(context: Context, email: String): IdentityResponse? {
        if (email.isBlank()) return null
        try {
            val workerUrl = BuildConfig.WORKER_URL.removeSuffix("/")
            val url = URL("$workerUrl/api/generate-identity")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; utf-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true

            val jsonInputString = JSONObject().apply { put("email", email) }.toString()
            OutputStreamWriter(conn.outputStream).use { os -> os.write(jsonInputString) }

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(responseBody)
                val userId = jsonResponse.optString("userId")
                val backupPassword = jsonResponse.optString("backupPassword")

                if (userId.isNotEmpty() && backupPassword.isNotEmpty()) {
                    getPrefs(context).edit()
                        .putString("${KEY_USER_ID}_$email", userId)
                        .putString("${KEY_BACKUP_PASSWORD}_$email", backupPassword)
                        .apply()
                    return IdentityResponse(userId, backupPassword)
                }
            } else {
                TerminalLogger.log("Identity fetch failed with code: $responseCode")
            }
        } catch (e: Exception) {
            TerminalLogger.log("Error fetching identity: ${e.message}")
            e.printStackTrace()
        }
        return null
    }
}
