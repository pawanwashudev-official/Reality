package com.neubofy.reality.utils

import android.content.Context
import android.util.Log
import com.azhon.appupdate.manager.DownloadManager
import com.neubofy.reality.BuildConfig
import com.neubofy.reality.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

/**
 * Handle professional app updates via GitHub Releases.
 * Features: 7-day automatic check throttle, manual check, and professional in-app installation.
 */
object UpdateManager {
    private const val TAG = "UpdateManager"
    private const val PREFS_NAME = "update_prefs"
    private const val KEY_LAST_CHECK_TIME = "last_check_time"
    private const val CHECK_INTERVAL_DAYS = 7L

    private const val GITHUB_API_URL = "https://api.github.com/repos/pawanwashudev-official/Reality/releases/latest"

    /**
     * Check for updates.
     * @param silent If true, only checks if 7 days have passed since last check.
     */
    fun checkForUpdates(context: Context, silent: Boolean = true, onNoUpdate: (() -> Unit)? = null) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastCheck = prefs.getLong(KEY_LAST_CHECK_TIME, 0L)
        val now = System.currentTimeMillis()

        if (silent && now - lastCheck < TimeUnit.DAYS.toMillis(CHECK_INTERVAL_DAYS)) {
            Log.d(TAG, "Update check skipped (7-day throttle active)")
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = fetchGitHubRelease()
                if (response != null) {
                    val latestVersion = response.getString("tag_name").replace("v", "")
                    val downloadUrl = response.getJSONArray("assets")
                        .getJSONObject(0) // Logic: first asset is the APK
                        .getString("browser_download_url")
                    val releaseNotes = response.getString("body")

                    if (isNewerVersion(latestVersion, BuildConfig.VERSION_NAME)) {
                        withContext(Dispatchers.Main) {
                            showUpdateDialog(context, latestVersion, downloadUrl, releaseNotes)
                        }
                    } else {
                        if (!silent) withContext(Dispatchers.Main) { onNoUpdate?.invoke() }
                    }
                }
                // Save last check time even if no update found
                prefs.edit().putLong(KEY_LAST_CHECK_TIME, now).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
            }
        }
    }

    private fun fetchGitHubRelease(): JSONObject? {
        return try {
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(content)
        } catch (e: Exception) {
            null
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        return try {
            val latestParts = latest.split(".").map { it.toInt() }
            val currentParts = current.split(".").map { it.toInt() }
            
            for (i in 0 until minOf(latestParts.size, currentParts.size)) {
                if (latestParts[i] > currentParts[i]) return true
                if (latestParts[i] < currentParts[i]) return false
            }
            latestParts.size > currentParts.size
        } catch (e: Exception) {
            latest != current // Fallback to string comparison
        }
    }

    private fun showUpdateDialog(context: Context, version: String, url: String, notes: String) {
        // azhon AppUpdate manager handles the professional UI and installation prompt
        val manager = DownloadManager.Builder(context as android.app.Activity).apply {
            apkUrl(url)
            apkName("Reality-v$version.apk")
            smallIcon(R.mipmap.ic_launcher)
            apkVersionName(version)
            apkDescription(notes)
            // The library automatically handles REQUEST_INSTALL_PACKAGES flow
        }.build()
        manager.download()
    }
}
