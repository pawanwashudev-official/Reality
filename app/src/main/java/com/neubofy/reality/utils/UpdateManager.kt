package com.neubofy.reality.utils

import android.content.Context
import android.util.Log
import android.app.AlertDialog
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
import java.text.SimpleDateFormat
import java.util.Locale

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
                    val assetsArray = response.getJSONArray("assets")

                    val apkAssets = mutableListOf<Pair<String, String>>()
                    for (i in 0 until assetsArray.length()) {
                        val asset = assetsArray.getJSONObject(i)
                        val name = asset.getString("name")
                        if (name.endsWith(".apk")) {
                            apkAssets.add(Pair(name, asset.getString("browser_download_url")))
                        }
                    }

                    val releaseNotes = response.getString("body")

                    // The published_at date of the GitHub release
                    val publishedAtStr = response.getString("published_at")
                    val releaseTime = try {
                        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                        format.parse(publishedAtStr)?.time ?: 0L
                    } catch (e: Exception) {
                        0L
                    }

                    val appLastUpdateTime = try {
                        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
                    } catch (e: Exception) {
                        0L
                    }

                    // If manual check (!silent), allow same version update. Otherwise, require strictly newer.
                    var isEligible = if (!silent) {
                        isNewerOrEqualVersion(latestVersion, BuildConfig.VERSION_NAME)
                    } else {
                        isNewerVersion(latestVersion, BuildConfig.VERSION_NAME)
                    }

                    // If it's a silent check but the release was published AFTER our app was last updated,
                    // it means the APK changed without a version bump, so it's a valid new update.
                    if (silent && !isEligible && latestVersion == BuildConfig.VERSION_NAME && releaseTime > appLastUpdateTime && releaseTime > 0L) {
                        isEligible = true
                    }

                    if (isEligible && apkAssets.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            if (apkAssets.size == 1) {
                                showUpdateDialog(context, latestVersion, apkAssets[0].second, releaseNotes)
                            } else {
                                showApkSelectionDialog(context, latestVersion, apkAssets, releaseNotes)
                            }
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

    private fun isNewerOrEqualVersion(latest: String, current: String): Boolean {
        return try {
            val latestParts = latest.split(".").map { it.toInt() }
            val currentParts = current.split(".").map { it.toInt() }

            for (i in 0 until minOf(latestParts.size, currentParts.size)) {
                if (latestParts[i] > currentParts[i]) return true
                if (latestParts[i] < currentParts[i]) return false
            }
            latestParts.size >= currentParts.size
        } catch (e: Exception) {
            true // Fallback to allow if string comparison fails
        }
    }

    private fun showApkSelectionDialog(context: Context, version: String, apks: List<Pair<String, String>>, notes: String) {
        val names = apks.map { it.first }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle("Select Update APK")
            .setItems(names) { _, which ->
                showUpdateDialog(context, version, apks[which].second, notes)
            }
            .setNegativeButton("Cancel", null)
            .show()
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
