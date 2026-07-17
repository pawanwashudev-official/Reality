package com.neubofy.reality.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object SecurePreferences {
    private val cache = mutableMapOf<String, SharedPreferences>()

    @Synchronized
    fun get(context: Context, prefName: String): SharedPreferences {
        val securePrefName = "${prefName}_secure"
        cache[securePrefName]?.let { return it }

        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val prefs = try {
            EncryptedSharedPreferences.create(
                context.applicationContext,
                securePrefName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Encrypted prefs corrupted (e.g., key scheme change after system update).
            // Delete the corrupted file and recreate, then trigger identity refresh to recover data.
            android.util.Log.e("SecurePreferences", "CRITICAL: Encrypted prefs '$securePrefName' corrupted, recreating. Error: ${e.message}")
            context.applicationContext.getSharedPreferences(securePrefName, Context.MODE_PRIVATE).edit().clear().apply()
            val file = java.io.File(context.applicationContext.filesDir.parentFile?.absolutePath + "/shared_prefs/$securePrefName.xml")
            if (file.exists()) file.delete()

            // Trigger async identity refresh to recover subscription/identity data
            // Only if this is identity-related prefs being corrupted
            if (securePrefName.contains("identity") || securePrefName.contains("features") || securePrefName.contains("pro")) {
                try {
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            IdentityManager.refreshIdentity(context.applicationContext)
                        } catch (_: Exception) {}
                    }
                } catch (_: Exception) {}
            }

            EncryptedSharedPreferences.create(
                context.applicationContext,
                securePrefName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
        cache[securePrefName] = prefs
        return prefs
    }
}
