package com.neubofy.reality.receivers

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.neubofy.reality.utils.SavedPreferencesLoader
import java.util.Calendar

class AdminLockReceiver : DeviceAdminReceiver() {
    
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        // Check if Anti-Uninstall is enabled under Strict Mode
        val prefs = SavedPreferencesLoader(context)
        val strictData = prefs.getStrictModeData()
        
        if (strictData.isEnabled && strictData.isAntiUninstallEnabled) {
            // Check maintenance window
            if (com.neubofy.reality.utils.StrictLockUtils.isMaintenanceWindow()) {
                return "Maintenance Window: Admin permission will be removed"
            }
            
            // Block removal - return warning message
            return "⚠️ ANTI-UNINSTALL PROTECTION ACTIVE\n\n" +
                   "Admin permissions are locked by Strict Mode.\n" +
                   "To disable:\n" +
                   "1. Turn off Strict Mode\n" +
                   "2. OR wait for Maintenance Window (00:00-00:10)"
        }
        
        // Not protected - allow removal
        return "Admin permission will be removed"
    }
    
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        
        // Sync with strict mode data
        val prefs = SavedPreferencesLoader(context)
        val strictData = prefs.getStrictModeData()
        
        // If it was disabled, turn off anti-uninstall flag
        if (strictData.isAntiUninstallEnabled) {
            strictData.isAntiUninstallEnabled = false
            prefs.saveStrictModeData(strictData)
        }
        
        Toast.makeText(context, "Reality Admin Protection Disabled", Toast.LENGTH_SHORT).show()
    }
    
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Toast.makeText(context, "Reality Admin Protection Enabled", Toast.LENGTH_SHORT).show()
    }
}
