package com.neubofy.reality.google

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.api.client.auth.oauth2.AuthorizationCodeFlow
import com.google.api.client.auth.oauth2.BearerToken
import com.google.api.client.auth.oauth2.ClientParametersAuthentication
import com.google.api.client.auth.oauth2.Credential
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.docs.v1.DocsScopes
import com.google.api.services.drive.DriveScopes
import com.google.api.services.sheets.v4.SheetsScopes
import com.google.api.services.tasks.TasksScopes
import com.neubofy.reality.utils.TerminalLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Centralized Google Authentication Manager using BYOK (Bring Your Own Key) Desktop OAuth.
 */
object GoogleAuthManager {
    
    private const val PREF_NAME = "google_auth_prefs"
    private const val KEY_CLIENT_ID = "client_id"
    private const val KEY_CLIENT_SECRET = "client_secret"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_NAME = "user_display_name"
    private const val KEY_USER_PHOTO_URL = "user_photo_url"
    private const val KEY_IS_SIGNED_IN = "is_signed_in"
    
    val ALL_SCOPES = listOf(
        CalendarScopes.CALENDAR,
        TasksScopes.TASKS,
        DriveScopes.DRIVE_FILE,
        DocsScopes.DOCUMENTS,
        SheetsScopes.SPREADSHEETS,
        "email",
        "profile"
    )
    
    val BASIC_SCOPES = listOf("email", "profile")

    private fun getPrefs(context: Context) = 
        com.neubofy.reality.utils.SecurePreferences.get(context, PREF_NAME)

    private fun getConnectorPrefs(context: Context) =
        com.neubofy.reality.utils.SecurePreferences.get(context, "google_connector_prefs")

    fun saveCloudCredentials(context: Context, clientId: String, clientSecret: String) {
        getPrefs(context).edit().apply {
            putString(KEY_CLIENT_ID, clientId)
            putString(KEY_CLIENT_SECRET, clientSecret)
            apply()
        }
    }
    
    fun getCustomClientId(context: Context): String? {
        return getPrefs(context).getString(KEY_CLIENT_ID, null)
    }

    fun getCustomClientSecret(context: Context): String? {
        return getPrefs(context).getString(KEY_CLIENT_SECRET, null)
    }

    fun getClientId(context: Context): String? {
        val id = getCustomClientId(context)
        if (!id.isNullOrBlank()) return id
        val defaultId = com.neubofy.reality.BuildConfig.DEFAULT_CLIENT_ID
        return if (defaultId.isNotBlank()) defaultId else null
    }

    fun getClientSecret(context: Context): String? {
        val secret = getCustomClientSecret(context)
        if (!secret.isNullOrBlank()) return secret
        val defaultSecret = com.neubofy.reality.BuildConfig.DEFAULT_CLIENT_SECRET
        return if (defaultSecret.isNotBlank()) defaultSecret else null
    }
    fun hasCloudCredentials(context: Context): Boolean {
        val clientId = getClientId(context)
        val clientSecret = getClientSecret(context)
        return !clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()
    }
    



    fun getAuthUrl(context: Context, basicOnly: Boolean = false): String? {
        val clientId = getClientId(context) ?: return null
        val scopes = if (basicOnly) BASIC_SCOPES else ALL_SCOPES

        return GoogleAuthorizationCodeRequestUrl(
            clientId,
            "http://127.0.0.1:8080/Callback",
            scopes
        )
        .setAccessType("offline")
        .build()
    }
    

    suspend fun startLocalServerAndGetCode(): String? {
        return withContext(Dispatchers.IO) {
            var server: java.net.ServerSocket? = null
            try {
                server = java.net.ServerSocket(8080, 50, java.net.InetAddress.getByName("127.0.0.1"))
                // Set a timeout so we don't block forever if the user closes the browser
                server.soTimeout = 120000 // 2 minutes timeout

                val socket = server.accept()
                val reader = java.io.BufferedReader(java.io.InputStreamReader(socket.inputStream))
                var line = reader.readLine()
                var code: String? = null

                while (line != null && line.isNotEmpty()) {
                    if (line.startsWith("GET ")) {
                        // Example: GET /Callback?code=4/0AeaYSHD8...&scope=... HTTP/1.1
                        val parts = line.split(" ")
                        if (parts.size > 1) {
                            val pathAndQuery = parts[1]
                            val match = Regex("[?&]code=([^&]+)").find(pathAndQuery)
                            if (match != null) {
                                code = match.groupValues[1]
                                try {
                                    code = java.net.URLDecoder.decode(code, "UTF-8")
                                } catch (e: Exception) {}
                            }
                        }
                    }
                    line = reader.readLine()
                }

                val output = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n<!DOCTYPE html><html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\"><style>body{background-color:#05050A;color:#FFFFFF;font-family:monospace;display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;margin:0;text-align:center;}h1{color:#00E5FF;text-shadow:0 0 10px rgba(0,229,255,0.5);}p{color:#7B61FF;margin-top:10px;}#countdown{font-size:2rem;font-weight:bold;color:#00E5FF;margin-top:20px;}</style></head><body><h1>Reality Authorization</h1><p>Authentication captured successfully.</p><p>Please wait while we process the token...</p><div id=\"countdown\">10</div><script>var timeLeft = 10;var el = document.getElementById('countdown');var timerId = setInterval(function() {if (timeLeft <= 0) {clearInterval(timerId);el.innerHTML = 'Process Complete. You may close this window and return to Reality.';} else {el.innerHTML = timeLeft;timeLeft -= 1;}}, 1000);</script></body></html>"
                socket.outputStream.write(output.toByteArray())
                socket.outputStream.flush()
                socket.close()
                return@withContext code
            } catch (e: Exception) {
                TerminalLogger.log("GOOGLE AUTH: Server error or timeout - ${e.message}")
                e.printStackTrace()
                null
            } finally {
                server?.close()
            }
        }
    }

    suspend fun exchangeCodeForTokens(context: Context, code: String): Boolean {
        return withContext(Dispatchers.IO) {
            val clientId = getClientId(context)
            val clientSecret = getClientSecret(context)

            if (clientId.isNullOrBlank() || clientSecret.isNullOrBlank()) {
                return@withContext false
            }

            try {
                val tokenResponse = GoogleAuthorizationCodeTokenRequest(
                    getHttpTransport(),
                    getJsonFactory(),
                    clientId,
                    clientSecret,
                    code,
                    "http://127.0.0.1:8080/Callback"
                ).execute()


                val accessToken = tokenResponse.accessToken
                val refreshToken = tokenResponse.refreshToken

                if (accessToken != null) {
                    getPrefs(context).edit().apply {
                        putString(KEY_ACCESS_TOKEN, accessToken)
                        if (refreshToken != null) {
                            putString(KEY_REFRESH_TOKEN, refreshToken)
                        }
                        putBoolean(KEY_IS_SIGNED_IN, true)
                        apply()
                    }
                    
                    // Fetch user info
                    fetchAndSaveUserInfo(context, accessToken)

                    return@withContext true
                }
            } catch (e: Exception) {
                TerminalLogger.log("GOOGLE AUTH: Token exchange failed - ${e.message}")
                e.printStackTrace()
            }
            return@withContext false
        }
    }

    private suspend fun fetchAndSaveUserInfo(context: Context, accessToken: String) {
        try {
            val url = URL("https://www.googleapis.com/oauth2/v2/userinfo")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.requestMethod = "GET"

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)

                val email = json.optString("email", "")
                val name = json.optString("name", "")
                val picture = json.optString("picture", "")

                getPrefs(context).edit().apply {
                    putString(KEY_USER_EMAIL, email)
                    putString(KEY_USER_NAME, name)
                    putString(KEY_USER_PHOTO_URL, picture)
                    apply()
                }
                TerminalLogger.log("GOOGLE AUTH: Saved user info for $email")
            }
        } catch (e: Exception) {
            TerminalLogger.log("GOOGLE AUTH: Failed to fetch user info - ${e.message}")
        }
    }

    fun isSignedIn(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_SIGNED_IN, false) &&
               !getPrefs(context).getString(KEY_ACCESS_TOKEN, null).isNullOrBlank()
    }

    fun getUserEmail(context: Context): String? = getPrefs(context).getString(KEY_USER_EMAIL, null)
    fun getUserName(context: Context): String? = getPrefs(context).getString(KEY_USER_NAME, null)
    fun getUserPhotoUrl(context: Context): String? = getPrefs(context).getString(KEY_USER_PHOTO_URL, null)
    
    fun signOut(context: Context) {
        val clientId = getClientId(context)
        val clientSecret = getClientSecret(context)

        getPrefs(context).edit().clear().apply()
        // Keep credentials
        if (clientId != null && clientSecret != null) {
            saveCloudCredentials(context, clientId, clientSecret)
        }

        com.neubofy.reality.utils.SecurePreferences.get(context, "google_connector_prefs").edit().clear().apply()
        TerminalLogger.log("GOOGLE AUTH: Signed out and cleared connections")
    }
    
    fun getGoogleCredential(context: Context): Credential? {
        val accessToken = getPrefs(context).getString(KEY_ACCESS_TOKEN, null)
        val refreshToken = getPrefs(context).getString(KEY_REFRESH_TOKEN, null)
        val clientId = getClientId(context)
        val clientSecret = getClientSecret(context)
        
        if (accessToken.isNullOrBlank() || clientId.isNullOrBlank() || clientSecret.isNullOrBlank()) {
            return null
        }
        
        return Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
            .setTransport(getHttpTransport())
            .setJsonFactory(getJsonFactory())
            .setTokenServerUrl(com.google.api.client.http.GenericUrl("https://oauth2.googleapis.com/token"))
            .setClientAuthentication(ClientParametersAuthentication(clientId, clientSecret))
            .build()
            .setAccessToken(accessToken)
            .setRefreshToken(refreshToken)
    }

    // Dummy sign in returning boolean instead of GoogleIdTokenCredential
    suspend fun signIn(activity: Activity): Boolean {
        // This is now manual.
        return isSignedIn(activity)
    }
    
    fun getHttpTransport() = com.google.api.client.extensions.android.http.AndroidHttp.newCompatibleTransport()
    
    fun getJsonFactory() = com.google.api.client.json.gson.GsonFactory.getDefaultInstance()
    
    fun hasRequiredPermissions(context: Context): Boolean {
        return isSignedIn(context)
    }
}
