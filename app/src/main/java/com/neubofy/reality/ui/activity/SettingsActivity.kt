package com.neubofy.reality.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.neubofy.reality.Constants
import com.neubofy.reality.R
import com.neubofy.reality.databinding.ActivitySettingsBinding
import com.neubofy.reality.utils.SavedPreferencesLoader
import com.neubofy.reality.utils.ThemeManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SavedPreferencesLoader

    private val dndPermissionLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) {
        updateUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = SavedPreferencesLoader(this)

        setupToolbar()
        setupListeners()
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun setupToolbar() {
        // Using custom header instead of standard Toolbar
        binding.btnBack.setOnClickListener { finish() }
    }

    private fun setupListeners() {
        
        // Unified Blocklist is on Home Page -> Focus Wall card now

        // Block Messages
        binding.cardBlockMessages.setOnClickListener {
            startActivity(Intent(this, BlockMessagesActivity::class.java))
        }

        // Reminder Settings (Opens ReminderActivity with settings dialog)
        binding.cardSettingsReminders.setOnClickListener {
            val intent = Intent(this, ReminderActivity::class.java)
            intent.putExtra("OPEN_SETTINGS", true)
            startActivity(intent)
        }

        // Schedule Settings (Opens ScheduleListActivity with settings dialog)
        binding.cardSettingsCalendar.setOnClickListener {
            val intent = Intent(this, ScheduleListActivity::class.java)
            intent.putExtra("OPEN_SETTINGS", true)
            startActivity(intent)
        }

        // Strict Mode Navigation
        binding.cardStrictMode.setOnClickListener {
            startActivity(Intent(this, StrictModeActivity::class.java))
        }
        
        // About Reality
        binding.cardAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        
        // Appearance (Pro Theme Picker)
        binding.cardTheme.setOnClickListener {
            startActivity(Intent(this, AppearanceActivity::class.java))
        }

        // Auto DND Logic
        binding.cardAutoDnd.setOnClickListener {
            val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                // Request Permission
                val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                dndPermissionLauncher.launch(intent)
            } else {
                // Toggle
                val currentState = prefs.isAutoDndEnabled()
                val isTryingToTurnOff = currentState // If enabled, we are turning it off
                
                val strictData = prefs.getStrictModeData()
                if (isTryingToTurnOff && strictData.isEnabled && strictData.isAutoDndLocked) {
                    android.widget.Toast.makeText(this, "Locked by Strict Mode", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    prefs.saveAutoDndEnabled(!currentState)
                    updateUI()
                }
            }
        }
        
        // Study URL moved to ReminderActivity
        
        // Sync switch with card click (Since switch is disabled/not clickable, card handles it)
    }

    private fun updateUI() {
        // Theme Status
        val themeMode = prefs.getThemeMode()
        binding.tvThemeStatus.text = when (themeMode) {
            1 -> "Light Mode"
            2 -> "Dark Mode"
            else -> "System Default"
        }

        // Strict Mode
        val data = prefs.getStrictModeData()
        
        if (data.isEnabled) {
            val status = when (data.modeType) {
                Constants.StrictModeData.MODE_TIMER -> "Active (Timer)"
                Constants.StrictModeData.MODE_PASSWORD -> "Active (Password)"
                else -> "Active"
            }
            binding.tvStrictStatus.text = status
            // Use Holo Red for active warning state
            binding.tvStrictStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
        } else {
            binding.tvStrictStatus.text = "Inactive"
            binding.tvStrictStatus.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        }
        

        
        // Auto DND
        val isDndEnabled = prefs.isAutoDndEnabled()
        val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val hasPermission = notificationManager.isNotificationPolicyAccessGranted
        
        binding.switchAutoDnd.isChecked = isDndEnabled && hasPermission
        
        if (!hasPermission) {
            binding.tvDndStatus.text = "Permission required. Tap to grant."
            binding.tvDndStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
            // Force disable if permission revoked
            if (isDndEnabled) prefs.saveAutoDndEnabled(false)
        } else {
            if (isDndEnabled) {
                binding.tvDndStatus.text = "Enabled (Syncs with blocking)"
                binding.tvDndStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            } else {
                binding.tvDndStatus.text = "Disabled"
                binding.tvDndStatus.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            }
        }
    }
    

    

}
