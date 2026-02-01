package com.neubofy.reality.ui.activity

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.databinding.ActivityUserManualBinding
import com.neubofy.reality.utils.ThemeManager
import com.neubofy.reality.utils.SavedPreferencesLoader
import com.neubofy.reality.Constants

class UserManualActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserManualBinding
    private lateinit var savedPreferencesLoader: SavedPreferencesLoader
    
    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        
        binding = ActivityUserManualBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        savedPreferencesLoader = SavedPreferencesLoader(this)
        
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "User Manual"
        
        loadSystemStatus()
    }
    
    private fun loadSystemStatus() {
        val strictData = savedPreferencesLoader.getStrictModeData()
        val emergencyData = savedPreferencesLoader.getEmergencyData()
        val blockedApps = savedPreferencesLoader.loadBlockedAppConfigs() // Using V2 configs
        val v1BlockedApps = savedPreferencesLoader.loadBlockedApps()
        
        val sb = StringBuilder()
        
        // Strict Mode
        sb.append("• Strict Mode: ")
        if (strictData.isEnabled) {
            sb.append("ACTIVE (${strictData.modeType})\n")
            if (strictData.timerEndTime > System.currentTimeMillis()) {
               val remaining = (strictData.timerEndTime - System.currentTimeMillis()) / 60000
               sb.append("  (Locked for ${remaining}m)\n")
            }
        } else {
            sb.append("Inactive\n")
        }
        sb.append("  Anti-Uninstall: ${if (strictData.isAntiUninstallEnabled) "ON" else "OFF"}\n\n")
        
        // Blocklist
        val count = if (blockedApps.isNotEmpty()) blockedApps.size else v1BlockedApps.size
        sb.append("• Blocklist: $count apps configured\n\n")
        
        // Emergency Access
        sb.append("• Emergency Access: ")
        sb.append("${emergencyData.usesRemaining} / ${Constants.EMERGENCY_MAX_USES} uses remaining today\n")
        
        binding.tvSystemStatusDetails.text = sb.toString()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
