package com.neubofy.reality.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

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
            // In case of any corruption or key scheme issues, delete the corrupted file and recreate
            context.applicationContext.getSharedPreferences(securePrefName, Context.MODE_PRIVATE).edit().clear().apply()
            val file = java.io.File(context.applicationContext.filesDir.parentFile?.absolutePath + "/shared_prefs/$securePrefName.xml")
            if (file.exists()) file.delete()

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
