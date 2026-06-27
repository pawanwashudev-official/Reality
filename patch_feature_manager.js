const fs = require('fs');

let kt = fs.readFileSync('app/src/main/java/com/neubofy/reality/utils/FeatureManager.kt', 'utf8');

// The most robust way to store a "do not bypass" flag locally without a server and without external storage
// is to use `android.provider.Settings.System` OR write a small file to Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
// Let's create a robust trial manager.

const robustTrialLogic = `
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
                    val lines = content.split("\\n")
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
        val trialEndTime = System.currentTimeMillis() + trialDurationMs

        // 1. Save local
        prefs.edit().putLong("trial_end_time_$userId", trialEndTime).apply()

        // 2. Save external to survive data clear
        try {
            val extFile = getExternalTrialFile()
            if (extFile != null) {
                val deviceId = getDeviceUniqueId()
                extFile.appendText("$userId=$trialEndTime\\n")
                extFile.appendText("$deviceId=$trialEndTime\\n")
            }
        } catch (e: Exception) {
            // Ignore
        }
    }
`;

kt = kt.replace(
    /fun isTrialActive\(\): Boolean \{[\s\S]*?fun activateTrial\(\) \{[\s\S]*?prefs\.edit\(\)\.putLong\("trial_end_time_\$userId", trialEndTime\)\.apply\(\)\n    \}/,
    robustTrialLogic.trim()
);

fs.writeFileSync('app/src/main/java/com/neubofy/reality/utils/FeatureManager.kt', kt);
