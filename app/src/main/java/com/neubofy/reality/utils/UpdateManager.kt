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
import org.json.JSONArray
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

    private const val GITHUB_API_URL = "https://api.github.com/repos/pawanwashudev-official/Reality/releases"

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
                val releases = fetchGitHubReleases()
                if (releases != null && releases.length() > 0) {
                    processReleases(context, releases, silent, onNoUpdate)
                } else {
                    if (!silent) withContext(Dispatchers.Main) { onNoUpdate?.invoke() }
                }

                // Save last check time even if no update found
                prefs.edit().putLong(KEY_LAST_CHECK_TIME, now).apply()
            } catch (e: Exception) {
                Log.e(TAG, "Update check failed", e)
                if (!silent) withContext(Dispatchers.Main) { onNoUpdate?.invoke() }
            }
        }
    }

    private suspend fun processReleases(context: Context, releases: JSONArray, silent: Boolean, onNoUpdate: (() -> Unit)?) {
        // Find the most recent release
        var targetRelease: JSONObject? = null
        var isPreRelease = false

        if (releases.length() > 0) {
            targetRelease = releases.getJSONObject(0)
            isPreRelease = targetRelease.optBoolean("prerelease", false)
        }

        if (targetRelease == null) {
            if (!silent) withContext(Dispatchers.Main) { onNoUpdate?.invoke() }
            return
        }

        if (isPreRelease && !silent) {
            // Ask user if they want the pre-release
            withContext(Dispatchers.Main) {
                AlertDialog.Builder(context)
                    .setTitle("Pre-release Available")
                    .setMessage("A pre-release version is available. Do you want to install it?")
                    .setPositiveButton("Yes") { _, _ ->
                        CoroutineScope(Dispatchers.IO).launch {
                            evaluateReleaseAndPrompt(context, targetRelease, silent, onNoUpdate)
                        }
                    }
                    .setNegativeButton("No") { _, _ ->
                        CoroutineScope(Dispatchers.IO).launch {
                            // Find the first non-prerelease
                            val stableRelease = run {
                                for (i in 0 until releases.length()) {
                                    val rel = releases.getJSONObject(i)
                                    if (!rel.optBoolean("prerelease", false)) {
                                        return@run rel
                                    }
                                }
                                null
                            }
                            if (stableRelease != null) {
                                evaluateReleaseAndPrompt(context, stableRelease, silent, onNoUpdate)
                            } else {
                                withContext(Dispatchers.Main) { onNoUpdate?.invoke() }
                            }
                        }
                    }
                    .setCancelable(false)
                    .show()
            }
        } else if (isPreRelease && silent) {
            // In silent mode, skip prereleases
            val stableRelease = run {
                for (i in 0 until releases.length()) {
                    val rel = releases.getJSONObject(i)
                    if (!rel.optBoolean("prerelease", false)) {
                        return@run rel
                    }
                }
                null
            }
            if (stableRelease != null) {
                evaluateReleaseAndPrompt(context, stableRelease, silent, onNoUpdate)
            }
        } else {
            // It's a stable release or silent check is ok
            evaluateReleaseAndPrompt(context, targetRelease, silent, onNoUpdate)
        }
    }

    private suspend fun evaluateReleaseAndPrompt(context: Context, release: JSONObject, silent: Boolean, onNoUpdate: (() -> Unit)?) {
        val latestVersion = release.getString("tag_name").replace("v", "")
        val assetsArray = release.getJSONArray("assets")

        val apkAssets = mutableListOf<Pair<String, String>>()
        for (i in 0 until assetsArray.length()) {
            val asset = assetsArray.getJSONObject(i)
            val name = asset.getString("name")
            if (name.endsWith(".apk")) {
                apkAssets.add(Pair(name, asset.getString("browser_download_url")))
            }
        }

        val releaseNotes = release.getString("body")

        // The published_at date of the GitHub release
        val publishedAtStr = release.optString("published_at", "")
        var releaseTime = 0L
        if (publishedAtStr.isNotEmpty()) {
            releaseTime = try {
                val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                format.timeZone = java.util.TimeZone.getTimeZone("UTC")
                format.parse(publishedAtStr)?.time ?: 0L
            } catch (e: Exception) {
                0L
            }
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

        // If the release was published AFTER our app was last updated,
        // it means the APK changed without a version bump, so it's a valid new update.
        if (!isEligible && latestVersion == BuildConfig.VERSION_NAME && releaseTime > appLastUpdateTime && releaseTime > 0L) {
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

    private fun fetchGitHubReleases(): JSONArray? {
        return try {
            val url = URL(GITHUB_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val content = connection.inputStream.bufferedReader().use { it.readText() }
            JSONArray(content)
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
