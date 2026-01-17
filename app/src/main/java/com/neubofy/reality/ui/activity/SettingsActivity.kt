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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import coil.load
import coil.transform.CircleCropTransformation

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
        
        // Terminal Log Toggle
        binding.switchTerminalLog.setOnCheckedChangeListener { _, isChecked ->
            val appPrefs = getSharedPreferences("reality_prefs", MODE_PRIVATE)
            appPrefs.edit().putBoolean("show_terminal_log", isChecked).apply()
        }
        
        // Account / Google Sign-In
        binding.cardAccount.setOnClickListener {
            handleAccountClick()
        }
    }
    
    private fun handleAccountClick() {
        if (com.neubofy.reality.google.GoogleAuthManager.isSignedIn(this)) {
            // Already signed in -> Show Sign Out Dialog
            val email = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(this)
            
            MaterialAlertDialogBuilder(this)
                .setTitle("Google Account")
                .setMessage("Signed in as $email\n\nDo you want to sign out?")
                .setPositiveButton("Sign Out") { _, _ ->
                    com.neubofy.reality.google.GoogleAuthManager.signOut(this)
                    updateUI()
                    android.widget.Toast.makeText(this, "Signed out", android.widget.Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            // Sign In
            performGoogleSignIn()
        }
    }
    
    private fun performGoogleSignIn() {
        lifecycleScope.launch {
            try {
                val credential = com.neubofy.reality.google.GoogleAuthManager.signIn(this@SettingsActivity)
                
                if (credential != null) {
                    android.widget.Toast.makeText(this@SettingsActivity, "Welcome ${credential.displayName}!", android.widget.Toast.LENGTH_LONG).show()
                    updateUI()
                } else {
                    android.widget.Toast.makeText(this@SettingsActivity, "Sign in failed or cancelled", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                com.neubofy.reality.utils.TerminalLogger.log("Sign In Error: ${e.message}")
                android.widget.Toast.makeText(this@SettingsActivity, "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateUI() {
        // Account Status
        if (com.neubofy.reality.google.GoogleAuthManager.isSignedIn(this)) {
            val name = com.neubofy.reality.google.GoogleAuthManager.getUserName(this)
            val email = com.neubofy.reality.google.GoogleAuthManager.getUserEmail(this)
            
            binding.tvAccountTitle.text = name ?: "Google User"
            binding.tvAccountStatus.text = email
            
            try {
                val photoUrl = com.neubofy.reality.google.GoogleAuthManager.getUserPhotoUrl(this)
                if (!photoUrl.isNullOrEmpty()) {
                    binding.ivProfile.load(photoUrl) {
                        crossfade(true)
                        transformations(CircleCropTransformation())
                        placeholder(R.drawable.baseline_account_circle_24)
                        error(R.drawable.baseline_account_circle_24)
                        listener(
                            onError = { _, result ->
                                com.neubofy.reality.utils.TerminalLogger.log("COIL ERROR: ${result.throwable.message}")
                            }
                        )
                    }
                    binding.ivProfile.imageTintList = null
                } else {
                    binding.ivProfile.setImageResource(R.drawable.baseline_account_circle_24)
                    binding.ivProfile.imageTintList = androidx.core.content.ContextCompat.getColorStateList(this, R.color.md_theme_primary)
                }
            } catch (e: Exception) {
                com.neubofy.reality.utils.TerminalLogger.log("SETTINGS IMAGE ERROR: ${e.message}")
                binding.ivProfile.setImageResource(R.drawable.baseline_account_circle_24)
            }
        } else {
            binding.tvAccountTitle.text = "Sign in with Google"
            binding.tvAccountStatus.text = "Sync Tasks, Docs & Drive"
            binding.ivProfile.setColorFilter(android.graphics.Color.GRAY)
        }
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
        
        // Terminal Log Toggle
        val appPrefs = getSharedPreferences("reality_prefs", MODE_PRIVATE)
        val isTerminalLogEnabled = appPrefs.getBoolean("show_terminal_log", true) // Default ON
        binding.switchTerminalLog.isChecked = isTerminalLogEnabled
    }
    

    

}
