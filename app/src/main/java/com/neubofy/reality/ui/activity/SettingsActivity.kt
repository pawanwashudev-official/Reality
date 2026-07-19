package com.neubofy.reality.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.ui.base.BaseActivity
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

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SavedPreferencesLoader

    private val dndPermissionLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) {
        updateUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
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

        // Show Settings Tour
        val appPrefs = getSharedPreferences("app_preferences", MODE_PRIVATE)
        if (!appPrefs.getBoolean("settings_tour_shown", false)) {
            binding.root.post { showSettingsTour() }
        }
    }

    private fun setupToolbar() {
        // Standard Toolbar logic
        val toolbar = binding.includeHeader.toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Settings"
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupListeners() {
        // Features Toggle Dialog
        binding.cardFeatures.setOnClickListener {
            showFeaturesDialog()
        }

        // Language Settings
        binding.cardLanguage.setOnClickListener {
            val intent = Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS)
            intent.data = android.net.Uri.parse("package:$packageName")
            try {
                startActivity(intent)
            } catch (e: Exception) {
                android.widget.Toast.makeText(this, "Language settings not found.", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // Unified Blocklist is on Home Page -> Focus Wall card now

        // Block Messages
        binding.cardBlockMessages.setOnClickListener {
            startActivity(Intent(this, BlockMessagesActivity::class.java))
        }



        binding.cardPermissionManager.setOnClickListener {
            val intent = Intent(this, PermissionManagerActivity::class.java)
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
        
        // AI Settings
        binding.cardAiSettings.setOnClickListener {
            startActivity(Intent(this, AISettingsActivity::class.java))
        }

        // Nightly Settings
        binding.cardNightlySettings.setOnClickListener {
            startActivity(Intent(this, NightlySettingsActivity::class.java))
        }

        // Reflection Settings
        binding.cardReflectionSettings.setOnClickListener {
            startActivity(Intent(this, ReflectionSettingsActivity::class.java))
        }

        // Tapasya Settings
        binding.cardTapasyaSettings.setOnClickListener {
            val intent = Intent(this, TapasyaActivity::class.java)
            intent.putExtra("OPEN_SETTINGS", true)
            startActivity(intent)
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
        
        // Reality Sleep Mode (Android 15+)
        binding.cardRealitySleep.setOnClickListener {
            if (!com.neubofy.reality.utils.ZenModeManager.isSupported()) return@setOnClickListener
            
            val notificationManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (!notificationManager.isNotificationPolicyAccessGranted) {
                val intent = Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                dndPermissionLauncher.launch(intent)
            } else {
                val currentState = prefs.isRealitySleepEnabled()
                val isTryingToTurnOff = currentState
                
                val strictData = prefs.getStrictModeData()
                if (isTryingToTurnOff && strictData.isEnabled && strictData.isRealitySleepLocked) {
                    android.widget.Toast.makeText(this, "Locked by Strict Mode", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    prefs.saveRealitySleepEnabled(!currentState)
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

    private fun showSettingsTour() {
        val permissionCard = findViewById<android.view.View>(R.id.card_permission_manager)
        val featuresCard = findViewById<android.view.View>(R.id.card_features)
        val strictModeCard = findViewById<android.view.View>(R.id.card_strict_mode)
        val aboutCard = findViewById<android.view.View>(R.id.card_about)

        if (permissionCard == null || featuresCard == null || strictModeCard == null || aboutCard == null) return

        com.getkeepsafe.taptargetview.TapTargetSequence(this)
            .targets(
                com.getkeepsafe.taptargetview.TapTarget.forView(permissionCard, "Permission Manager", "The most important section. Manage core permissions like Accessibility and Usage Access centrally here.")
                    .outerCircleColor(R.color.md_theme_primary)
                    .targetCircleColor(android.R.color.white)
                    .titleTextSize(20)
                    .titleTextColor(android.R.color.white)
                    .descriptionTextSize(14)
                    .descriptionTextColor(android.R.color.white)
                    .cancelable(true)
                    .transparentTarget(false),
                com.getkeepsafe.taptargetview.TapTarget.forView(featuresCard, "Neural Features", "Enable or disable Reality Elite Member, AI, Tapasya, and more.")
                    .outerCircleColor(R.color.md_theme_primary)
                    .targetCircleColor(android.R.color.white)
                    .titleTextSize(20)
                    .titleTextColor(android.R.color.white)
                    .descriptionTextSize(14)
                    .descriptionTextColor(android.R.color.white)
                    .cancelable(true)
                    .transparentTarget(false),
                com.getkeepsafe.taptargetview.TapTarget.forView(strictModeCard, "Strict Mode", "Lock your app settings with a timer or password to prevent tampering.")
                    .outerCircleColor(R.color.md_theme_primary)
                    .targetCircleColor(android.R.color.white)
                    .titleTextSize(20)
                    .titleTextColor(android.R.color.white)
                    .descriptionTextSize(14)
                    .descriptionTextColor(android.R.color.white)
                    .cancelable(true)
                    .transparentTarget(false),
                com.getkeepsafe.taptargetview.TapTarget.forView(aboutCard, "About Reality", "Check for OTA updates and contact support.")
                    .outerCircleColor(R.color.md_theme_primary)
                    .targetCircleColor(android.R.color.white)
                    .titleTextSize(20)
                    .titleTextColor(android.R.color.white)
                    .descriptionTextSize(14)
                    .descriptionTextColor(android.R.color.white)
                    .cancelable(true)
                    .transparentTarget(false)
            )
            .listener(object : com.getkeepsafe.taptargetview.TapTargetSequence.Listener {
                override fun onSequenceFinish() {
                    getSharedPreferences("app_preferences", android.content.Context.MODE_PRIVATE).edit().putBoolean("settings_tour_shown", true).apply()
                }
                override fun onSequenceStep(lastTarget: com.getkeepsafe.taptargetview.TapTarget?, targetClicked: Boolean) {}
                override fun onSequenceCanceled(lastTarget: com.getkeepsafe.taptargetview.TapTarget?) {
                    getSharedPreferences("app_preferences", android.content.Context.MODE_PRIVATE).edit().putBoolean("settings_tour_shown", true).apply()
                }
            })
            .start()
    }
    
    private fun handleAccountClick() {
        startActivity(Intent(this, ProfileActivity::class.java))
    }
    

    private fun showFeaturesDialog() {
        val featureManager = com.neubofy.reality.utils.FeatureManager(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_features, null)

        val switchRealityPro = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_feature_reality_pro)
        val switchAi = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_feature_ai)
        val switchTapasya = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_feature_tapasya)
        val switchReminder = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_feature_reminder)
        val switchHealth = dialogView.findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switch_feature_health)


        // Init states
        switchRealityPro.isChecked = featureManager.isRealityProEnabled()
        switchAi.isChecked = featureManager.isAiEnabled()
        switchTapasya.isChecked = featureManager.isTapasyaEnabled()
        switchReminder.isChecked = featureManager.isReminderEnabled()
        switchHealth.isChecked = featureManager.isHealthConnectEnabled()

        // Set listeners
        switchRealityPro.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !featureManager.isRealityProVerified() && !featureManager.isTrialActive()) {
                switchRealityPro.isChecked = false
                val intent = Intent(this, RealityEliteActivity::class.java)
                startActivity(intent)
            } else if (!isChecked || featureManager.isRealityProVerified() || featureManager.isTrialActive()) {
                featureManager.setRealityProEnabled(isChecked)
                updateUI()
            } else {
                switchRealityPro.isChecked = false
            }
        }
        switchAi.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !featureManager.isRealityProVerified() && !featureManager.isTrialActive()) {
                switchAi.isChecked = false
                val intent = Intent(this, RealityEliteActivity::class.java)
                startActivity(intent)
            } else if (!isChecked || featureManager.isRealityProVerified() || featureManager.isTrialActive()) {
                featureManager.setAiEnabled(isChecked)
                updateUI()
            } else {
                switchAi.isChecked = false
            }
        }
        switchTapasya.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !featureManager.isRealityProVerified() && !featureManager.isTrialActive()) {
                switchTapasya.isChecked = false
                val intent = Intent(this, RealityEliteActivity::class.java)
                startActivity(intent)
            } else if (!isChecked || featureManager.isRealityProVerified() || featureManager.isTrialActive()) {
                featureManager.setTapasyaEnabled(isChecked)
                updateUI()
            } else {
                switchTapasya.isChecked = false
            }
        }
        switchReminder.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !featureManager.isRealityProVerified() && !featureManager.isTrialActive()) {
                switchReminder.isChecked = false
                val intent = Intent(this, RealityEliteActivity::class.java)
                startActivity(intent)
            } else {
                featureManager.setReminderEnabled(isChecked)
                updateUI()
            }
        }
        switchHealth.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked && !featureManager.isRealityProVerified() && !featureManager.isTrialActive()) {
                switchHealth.isChecked = false
                val intent = Intent(this, RealityEliteActivity::class.java)
                startActivity(intent)
            } else {
                featureManager.setHealthConnectEnabled(isChecked)
                updateUI()
            }
        }


        MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setPositiveButton("Close", null)
            .show()
    }

    private fun updateUI() {
        val cardEncryption = findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_data_encryption)
        cardEncryption?.visibility = android.view.View.GONE


        // Features State
        val featureManager = com.neubofy.reality.utils.FeatureManager(this)

        val isRealityProEnabled = featureManager.isRealityProEnabled()
        val visibilityRealityPro = if (isRealityProEnabled) android.view.View.VISIBLE else android.view.View.GONE


        // Reality Elite Member Verification Card Logic
        val verificationCard = findViewById<com.google.android.material.card.MaterialCardView>(R.id.card_reality_pro_verification)
        if (verificationCard != null) {
            verificationCard.visibility = android.view.View.VISIBLE
            verificationCard.setOnClickListener {
                val intent = Intent(this, RealityEliteActivity::class.java)
                startActivity(intent)
            }
        }

        binding.tvAccountHeader.visibility = visibilityRealityPro
        binding.cardAccount.visibility = visibilityRealityPro
        binding.cardNightlySettings.visibility = visibilityRealityPro
        binding.cardReflectionSettings.visibility = visibilityRealityPro

        binding.cardAiSettings.visibility = if (featureManager.isAiEnabled()) android.view.View.VISIBLE else android.view.View.GONE
        binding.cardTapasyaSettings.visibility = if (featureManager.isTapasyaEnabled()) android.view.View.VISIBLE else android.view.View.GONE


        // Encryption Status
        val encPrefs = com.neubofy.reality.utils.SecurePreferences.get(this, "reality_encryption_prefs")
        val hasEncryption = encPrefs.contains("backup_password")
        val tvEncryptionStatus = findViewById<android.widget.TextView>(R.id.tv_encryption_status)
        if (tvEncryptionStatus != null) {
            if (hasEncryption) {
                tvEncryptionStatus.text = "Active (Custom Password)"
                tvEncryptionStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.holo_green_light))
            } else {
                tvEncryptionStatus.text = "Default Encryption"
                tvEncryptionStatus.setTextColor(androidx.core.content.ContextCompat.getColor(this, android.R.color.darker_gray))
            }
        }

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
        
        // Reality Sleep Mode (Android 15+ only)
        if (com.neubofy.reality.utils.ZenModeManager.isSupported()) {
            binding.cardRealitySleep.visibility = android.view.View.VISIBLE
            val isRealitySleepEnabled = prefs.isRealitySleepEnabled()
            val notifManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val hasDndPermission = notifManager.isNotificationPolicyAccessGranted
            
            binding.switchRealitySleep.isChecked = isRealitySleepEnabled && hasDndPermission
            
            if (!hasDndPermission) {
                binding.tvRealitySleepStatus.text = "DND permission required. Tap to grant."
                binding.tvRealitySleepStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_light))
                if (isRealitySleepEnabled) prefs.saveRealitySleepEnabled(false)
            } else if (isRealitySleepEnabled) {
                binding.tvRealitySleepStatus.text = "Enabled (Grayscale, Dim & Dark)"
                binding.tvRealitySleepStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
            } else {
                binding.tvRealitySleepStatus.text = "Grayscale, Dim & Dark Mode"
                binding.tvRealitySleepStatus.setTextColor(ContextCompat.getColor(this, android.R.color.darker_gray))
            }
        } else {
            binding.cardRealitySleep.visibility = android.view.View.GONE
        }
        
        // Terminal Log Toggle
        val appPrefs = getSharedPreferences("reality_prefs", MODE_PRIVATE)
        val isTerminalLogEnabled = appPrefs.getBoolean("show_terminal_log", false) // Default OFF
        binding.switchTerminalLog.isChecked = isTerminalLogEnabled
    }
    

    

}
