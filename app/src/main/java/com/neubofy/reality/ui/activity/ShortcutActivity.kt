package com.neubofy.reality.ui.activity

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.services.AppBlockerService
import com.neubofy.reality.ui.dialogs.StartFocusMode
import com.neubofy.reality.utils.SavedPreferencesLoader

class ShortcutActivity : AppCompatActivity() {

    private val savedPreferencesLoader = SavedPreferencesLoader(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isFocusedModeOn = savedPreferencesLoader.getFocusModeData().isTurnedOn
        if(isFocusedModeOn){
            Toast.makeText(this,"Focus Mode is already active",Toast.LENGTH_SHORT).show()
            finish()
        }

        val isGeneralSettingsOn = isAccessibilityServiceEnabled(AppBlockerService::class.java)
        if(!isGeneralSettingsOn){
            Toast.makeText(this,"Find 'App Blocker' and press enable",Toast.LENGTH_LONG).show()
            openAccessibilityServiceScreen(cls = AppBlockerService::class.java)
            finish()
        }
        StartFocusMode(savedPreferencesLoader, onPositiveButtonPressed = {
            finish()
        }).show(
            supportFragmentManager,
            "start_focus_mode_from_shortcut"
        )
    }


    private fun openAccessibilityServiceScreen(cls: Class<*>) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            val componentName = ComponentName(this, cls)

            intent.putExtra(":settings:fragment_args_key", componentName.flattenToString())
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to general Accessibility Settings
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
    }

    private fun isAccessibilityServiceEnabled(serviceClass: Class<out AccessibilityService>): Boolean {
        val serviceName = ComponentName(this, serviceClass).flattenToString()
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val isAccessibilityEnabled = Settings.Secure.getInt(
            contentResolver,
            Settings.Secure.ACCESSIBILITY_ENABLED,
            0
        )
        return isAccessibilityEnabled == 1 && enabledServices.contains(serviceName)
    }
}