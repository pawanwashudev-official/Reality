package com.neubofy.reality.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object SecurePreferences {
    fun get(context: Context, prefName: String): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val securePrefName = "${prefName}_secure"
        return try {
            EncryptedSharedPreferences.create(
                context,
                securePrefName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // In case of any corruption or key scheme issues, delete the corrupted file and recreate
            context.getSharedPreferences(securePrefName, Context.MODE_PRIVATE).edit().clear().apply()
            val file = java.io.File(context.filesDir.parentFile?.absolutePath + "/shared_prefs/$securePrefName.xml")
            if (file.exists()) file.delete()

            EncryptedSharedPreferences.create(
                context,
                securePrefName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }
}
