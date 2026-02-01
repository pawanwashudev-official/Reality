package com.neubofy.reality.ui.activity

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.ui.base.BaseActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.neubofy.reality.Constants
import com.neubofy.reality.R
import com.neubofy.reality.databinding.ActivityStrictModeSettingsBinding
import com.neubofy.reality.receivers.AdminLockReceiver
import com.neubofy.reality.services.AppBlockerService
import com.neubofy.reality.utils.SavedPreferencesLoader
import com.neubofy.reality.utils.StrictLockUtils
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*

class StrictModeActivity : BaseActivity() {
    
    private lateinit var binding: ActivityStrictModeSettingsBinding
    private lateinit var prefsLoader: SavedPreferencesLoader
    private lateinit var strictData: Constants.StrictModeData
    private lateinit var learnedPages: Constants.LearnedSettingsPages
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    
    private var countdownTimer: CountDownTimer?= null
    
    companion object {
        const val REQUEST_ADMIN_PERMISSION = 1001
        const val FORGOT_PASSWORD_WAIT_MS = 24 * 60 * 60 * 1000L // 24 hours
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStrictModeSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefsLoader = SavedPreferencesLoader(this)
        strictData = prefsLoader.getStrictModeData()
        learnedPages = prefsLoader.getLearnedSettingsPages()
        devicePolicyManager = getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, AdminLockReceiver::class.java)
        
        setupToolbar()
        
        loadSettings()
        attachListeners()
        updateUIState()
    }

    private fun setupToolbar() {
        val toolbar = binding.includeHeader.toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Strict Mode"
        toolbar.setNavigationOnClickListener { finish() }
    }
    
    override fun onResume() {
        super.onResume()
        strictData = prefsLoader.getStrictModeData()
        learnedPages = prefsLoader.getLearnedSettingsPages()
        updateAntiUninstallSwitch()
        updateLearningStatus()
        updateUIState()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        countdownTimer?.cancel()
    }
    
    private fun loadSettings() {
        binding.switchBlocklistLock.isChecked = strictData.isBlocklistLocked
        binding.switchBedtimeLock.isChecked = strictData.isBedtimeLocked
        binding.switchAppLimitLock.isChecked = strictData.isAppLimitLocked
        binding.switchGroupLimitLock.isChecked = strictData.isGroupLimitLocked
        binding.switchScheduleLock.isChecked = strictData.isScheduleLocked
        binding.switchTapasyaLock.isChecked = strictData.isTapasyaLocked

        binding.switchAutoDndLock.isChecked = strictData.isAutoDndLocked
        binding.switchCalendarLock.isChecked = strictData.isCalendarLocked
        binding.switchNightlyLimitLock.isChecked = strictData.isNightlyLimitLocked
        binding.switchGamificationLock.isChecked = strictData.isGamificationLocked
        binding.switchAntiTimeCheat.isChecked = strictData.isTimeCheatProtectionEnabled
        binding.switchAccessibilityProtection.isChecked = strictData.isAccessibilityProtectionEnabled
        binding.switchAntiUninstall.isChecked = strictData.isAntiUninstallEnabled
        
        updateLearningStatus()
    }
    
    private fun updateLearningStatus() {
        // Time Page Status
        if (learnedPages.timeSettingsPageClass.isNotEmpty()) {
            binding.tvTimeCheatStatus.text = "✓ Page: ${learnedPages.timeSettingsPageClass.substringAfterLast(".")}"
            binding.tvTimeCheatStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            
            // Show keywords if any
            if (learnedPages.timeSettingsKeywords.isNotEmpty()) {
                binding.tvTimeKeywordsStatus.text = "🔑 ${learnedPages.timeSettingsKeywords.joinToString(", ")}"
                binding.tvTimeKeywordsStatus.visibility = android.view.View.VISIBLE
            } else {
                binding.tvTimeKeywordsStatus.visibility = android.view.View.GONE
            }
        } else {
            binding.tvTimeCheatStatus.text = "⚠️ Tap to learn page"
            binding.tvTimeCheatStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
            binding.tvTimeKeywordsStatus.visibility = android.view.View.GONE
        }
        
        // Accessibility Page Status
        if (learnedPages.accessibilityPageClass.isNotEmpty()) {
            binding.tvAccessibilityStatus.text = "✓ Page: ${learnedPages.accessibilityPageClass.substringAfterLast(".")}"
            binding.tvAccessibilityStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            
            // Show keywords if any
            if (learnedPages.accessibilityKeywords.isNotEmpty()) {
                binding.tvAccessibilityKeywordsStatus.text = "🔑 ${learnedPages.accessibilityKeywords.joinToString(", ")}"
                binding.tvAccessibilityKeywordsStatus.visibility = android.view.View.VISIBLE
            } else {
                binding.tvAccessibilityKeywordsStatus.visibility = android.view.View.GONE
            }
        } else {
            binding.tvAccessibilityStatus.text = "⚠️ Tap to learn page"
            binding.tvAccessibilityStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
            binding.tvAccessibilityKeywordsStatus.visibility = android.view.View.GONE
        }
        
        // Admin Page Status
        if (learnedPages.deviceAdminPageClass.isNotEmpty()) {
            binding.tvAdminPageStatus.text = "✓ Page: ${learnedPages.deviceAdminPageClass.substringAfterLast(".")}"
            binding.tvAdminPageStatus.setTextColor(getColor(android.R.color.holo_green_dark))
            
            // Show keywords if any
            if (learnedPages.deviceAdminKeywords.isNotEmpty()) {
                binding.tvAdminKeywordsStatus.text = "🔑 ${learnedPages.deviceAdminKeywords.joinToString(", ")}"
                binding.tvAdminKeywordsStatus.visibility = android.view.View.VISIBLE
            } else {
                binding.tvAdminKeywordsStatus.visibility = android.view.View.GONE
            }
        } else {
            binding.tvAdminPageStatus.text = "⚠️ Tap to learn page"
            binding.tvAdminPageStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
            binding.tvAdminKeywordsStatus.visibility = android.view.View.GONE
        }

        
        // Custom pages count
        val pageCount = learnedPages.customBlockedPages.size
        if (pageCount > 0) {
            binding.tvCustomPagesCount.text = "$pageCount custom pages blocked"
        } else {
            binding.tvCustomPagesCount.text = "No custom items added"
        }
    }
    
    private fun updateUIState() {
        countdownTimer?.cancel()
        
        if (strictData.isEnabled) {
            // === STRICT MODE ACTIVE ===
            val errorColor = ContextCompat.getColor(this, android.R.color.holo_red_light)
            binding.imgStatusIcon.setImageResource(R.drawable.baseline_lock_24)
            binding.imgStatusIcon.imageTintList = ColorStateList.valueOf(errorColor)
            binding.tvStatusTitle.text = "Strict Mode Active"
            binding.tvStatusTitle.setTextColor(errorColor)
            
            // Update description based on mode type
            when (strictData.modeType) {
                Constants.StrictModeData.MODE_NONE -> {
                    binding.tvStatusDesc.text = "Tap below to deactivate"
                    binding.tvTimerRemaining.visibility = View.GONE
                    binding.btnForgotPassword.visibility = View.GONE
                }
                Constants.StrictModeData.MODE_TIMER -> {
                    binding.tvStatusDesc.text = "Locked until timer expires"
                    binding.tvTimerRemaining.visibility = View.VISIBLE
                    binding.btnForgotPassword.visibility = View.GONE
                    startTimerCountdown()
                }
                Constants.StrictModeData.MODE_PASSWORD -> {
                    // Check if forgot password timer is active
                    if (strictData.forgotPasswordTimerEndTime > System.currentTimeMillis()) {
                        binding.tvStatusDesc.text = "Forgot password cooldown active"
                        binding.tvTimerRemaining.visibility = View.VISIBLE
                        binding.btnForgotPassword.visibility = View.GONE
                        startForgotPasswordCountdown()
                    } else {
                        binding.tvStatusDesc.text = "Enter password to deactivate"
                        binding.tvTimerRemaining.visibility = View.GONE
                        binding.btnForgotPassword.visibility = View.VISIBLE
                    }
                }
            }
            
            // Disable toggles (ratchet - can enable more but not disable)
            setTogglesEnabled(false)
            
            // Button styling
            binding.btnActivate.text = "Deactivate Strict Mode"
            binding.btnActivate.setBackgroundColor(errorColor)
            binding.btnActivate.setTextColor(Color.WHITE)
            
        } else {
            // === STRICT MODE INACTIVE ===
            binding.imgStatusIcon.setImageResource(R.drawable.baseline_lock_open_24)
            binding.imgStatusIcon.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, android.R.color.darker_gray)
            )
            binding.tvStatusTitle.text = "Strict Mode Inactive"
            binding.tvStatusTitle.setTextColor(
                ContextCompat.getColor(this, android.R.color.white)
            )
            binding.tvStatusDesc.text = "Prevents you from disabling your blocks"
            binding.tvTimerRemaining.visibility = View.GONE
            binding.btnForgotPassword.visibility = View.GONE
            
            setTogglesEnabled(true)
            
            // Button styling
            binding.btnActivate.text = "Activate Strict Mode"
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
            binding.btnActivate.backgroundTintList = ColorStateList.valueOf(typedValue.data)
            binding.btnActivate.setTextColor(Color.WHITE)
        }
    }
    
    private fun setTogglesEnabled(enabled: Boolean) {
        // If strict mode is active, toggles are visually enabled but
        // logic prevents turning OFF (ratchet)
        binding.switchBlocklistLock.isEnabled = true
        binding.switchBedtimeLock.isEnabled = true
        binding.switchAppLimitLock.isEnabled = true
        binding.switchGroupLimitLock.isEnabled = true
        binding.switchScheduleLock.isEnabled = true
        binding.switchTapasyaLock.isEnabled = true

        binding.switchAutoDndLock.isEnabled = true
        binding.switchCalendarLock.isEnabled = true
        binding.switchNightlyLimitLock.isEnabled = true
        binding.switchGamificationLock.isEnabled = true
        binding.switchAntiTimeCheat.isEnabled = true
        binding.switchAntiUninstall.isEnabled = true
    }
    
    private fun attachListeners() {
        // --- Protection Switches with Ratchet Logic ---
        // Ratchet: Can turn ON anytime, but cannot turn OFF when strict mode is active
        val switches = listOf(
            binding.switchBlocklistLock to { v: Boolean -> strictData.isBlocklistLocked = v },
            binding.switchBedtimeLock to { v: Boolean -> strictData.isBedtimeLocked = v },
            binding.switchAppLimitLock to { v: Boolean -> strictData.isAppLimitLocked = v },
            binding.switchGroupLimitLock to { v: Boolean -> strictData.isGroupLimitLocked = v },
            binding.switchScheduleLock to { v: Boolean -> strictData.isScheduleLocked = v },
            binding.switchTapasyaLock to { v: Boolean -> strictData.isTapasyaLocked = v },

            binding.switchAutoDndLock to { v: Boolean -> strictData.isAutoDndLocked = v },
            binding.switchCalendarLock to { v: Boolean -> strictData.isCalendarLocked = v },
            binding.switchNightlyLimitLock to { v: Boolean -> strictData.isNightlyLimitLocked = v },
            binding.switchGamificationLock to { v: Boolean -> strictData.isGamificationLocked = v },
            
            // Page blocking
            binding.switchAntiTimeCheat to { v: Boolean -> strictData.isTimeCheatProtectionEnabled = v },
            binding.switchAccessibilityProtection to { v: Boolean -> strictData.isAccessibilityProtectionEnabled = v },
            binding.switchAntiUninstall to { v: Boolean -> strictData.isAntiUninstallEnabled = v }
        )
        
        switches.forEach { (switchView, updateFunc) ->
            switchView.setOnClickListener {
                val isTryingTurnOff = !switchView.isChecked
                
                if (strictData.isEnabled && isTryingTurnOff) {
                    // RATCHET: Cannot turn OFF while active
                    switchView.isChecked = true
                    Toast.makeText(this, "Cannot disable while Strict Mode is active!", Toast.LENGTH_SHORT).show()
                } else if (!isTryingTurnOff) {
                    // Trying to turn ON - check if learning is required but not done
                    val missingLearnData = when (switchView) {
                        // Page protection switches - require learned page class
                        binding.switchAntiTimeCheat -> learnedPages.timeSettingsPageClass.isEmpty()
                        binding.switchAccessibilityProtection -> learnedPages.accessibilityPageClass.isEmpty()
                        binding.switchAntiUninstall -> learnedPages.deviceAdminPageClass.isEmpty()
                        else -> false
                    }
                    
                    if (missingLearnData) {
                        // Prevent toggle, force learning first
                        switchView.isChecked = false
                        Toast.makeText(this, "⚠️ Please learn the page first!", Toast.LENGTH_SHORT).show()
                        
                        // Auto-open learning dialog
                        when (switchView) {
                            binding.switchAntiTimeCheat -> showLearnTimeSettingsDialog()
                            binding.switchAccessibilityProtection -> showLearnAccessibilityDialog()
                            binding.switchAntiUninstall -> showLearnAdminDialog()
                        }
                        return@setOnClickListener
                    }
                    
                    // Has learning data - allow enable
                    updateFunc(switchView.isChecked)
                    saveSettings()
                } else {
                    updateFunc(switchView.isChecked)
                    saveSettings()
                }
            }
        }
        
        // Grayscale Switch REMOVED - feature requires ADB
        
        // Learning status tap - show manage dialog (only if Strict Mode OFF)
        binding.tvTimeCheatStatus.setOnClickListener {
            if (strictData.isEnabled) {
                Toast.makeText(this, "Cannot modify learning while Strict Mode is active!", Toast.LENGTH_SHORT).show()
            } else {
                showLearningManageDialog(
                    Constants.PageType.TIME_SETTINGS,
                    learnedPages.timeSettingsPageClass,
                    { showLearnTimeSettingsDialog() },
                    { 
                        learnedPages.timeSettingsPageClass = ""
                        prefsLoader.saveLearnedSettingsPages(learnedPages)
                        binding.switchAntiTimeCheat.isChecked = false
                        strictData.isTimeCheatProtectionEnabled = false
                        saveSettings()
                        updateLearningStatus()
                    }
                )
            }
        }
        binding.tvAccessibilityStatus.setOnClickListener {
            if (strictData.isEnabled) {
                Toast.makeText(this, "Cannot modify learning while Strict Mode is active!", Toast.LENGTH_SHORT).show()
            } else {
                showLearningManageDialog(
                    Constants.PageType.ACCESSIBILITY,
                    learnedPages.accessibilityPageClass,
                    { showLearnAccessibilityDialog() },
                    { 
                        learnedPages.accessibilityPageClass = ""
                        prefsLoader.saveLearnedSettingsPages(learnedPages)
                        binding.switchAccessibilityProtection.isChecked = false
                        strictData.isAccessibilityProtectionEnabled = false
                        saveSettings()
                        updateLearningStatus()
                    }
                )
            }
        }
        // App Info long-press handler REMOVED
        
        
        binding.tvAdminPageStatus.setOnClickListener {
            if (strictData.isEnabled) {
                Toast.makeText(this, "Cannot modify learning while Strict Mode is active!", Toast.LENGTH_SHORT).show()
            } else {
                showLearnAdminDialog()
            }
        }

        
        // --- Custom Page/Button Buttons ---
        binding.btnAddCustomPage.setOnClickListener {
            if (strictData.isEnabled) {
                Toast.makeText(this, "Cannot add while Strict Mode is active!", Toast.LENGTH_SHORT).show()
            } else {
                showLearnCustomPageDialog()
            }
        }
        

        
        binding.btnManageCustomItems.setOnClickListener {
            showManageCustomItemsDialog()
        }
        
        // --- Anti-Uninstall ---
        binding.switchAntiUninstall.setOnClickListener {
            val isTryingTurnOff = !binding.switchAntiUninstall.isChecked
            
            if (strictData.isEnabled && isTryingTurnOff) {
                binding.switchAntiUninstall.isChecked = true
                Toast.makeText(this, "Cannot disable while Strict Mode is active!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (binding.switchAntiUninstall.isChecked) {
                requestAdminPermission()
            } else {
                if (isAdminActive()) {
                    showRemoveAdminInstructions()
                    binding.switchAntiUninstall.isChecked = true
                } else {
                    strictData.isAntiUninstallEnabled = false
                    saveSettings()
                }
            }
        }
        
        // --- Main Activate/Deactivate Button ---
        binding.btnActivate.setOnClickListener {
            if (strictData.isEnabled) {
                handleDeactivation()
            } else {
                showActivationMethodDialog()
            }
        }
        
        // --- Forgot Password Button ---
        binding.btnForgotPassword.setOnClickListener {
            showForgotPasswordConfirmation()
        }
    }
    
    // ============== ACTIVATION FLOW ==============
    
    private fun showActivationMethodDialog() {
        val options = arrayOf(
            "🔓  No Lock (Instant Deactivation)",
            "⏱️  Timer Lock (Set Duration)",
            "🔑  Password Lock"
        )
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Choose Unlock Method")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> activateWithNone()
                    1 -> showTimerSetupDialog()
                    2 -> showPasswordSetupDialog()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun activateWithNone() {
        strictData.modeType = Constants.StrictModeData.MODE_NONE
        strictData.isEnabled = true
        strictData.timerEndTime = 0
        strictData.passwordHash = ""
        saveSettings()
        updateUIState()
        Toast.makeText(this, "Strict Mode Activated!", Toast.LENGTH_SHORT).show()
    }
    
    private fun showTimerSetupDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_timer_picker, null)
        val daysPicker = dialogView.findViewById<NumberPicker>(R.id.pickerDays)
        val hoursPicker = dialogView.findViewById<NumberPicker>(R.id.pickerHours)
        
        daysPicker.minValue = 0
        daysPicker.maxValue = 20
        daysPicker.value = 1
        
        hoursPicker.minValue = 0
        hoursPicker.maxValue = 23
        hoursPicker.value = 0
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Set Lock Duration")
            .setView(dialogView)
            .setPositiveButton("Activate") { _, _ ->
                val days = daysPicker.value
                val hours = hoursPicker.value
                
                if (days == 0 && hours == 0) {
                    Toast.makeText(this, "Please set at least 1 hour", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                val durationMs = (days * 24L * 60L * 60L * 1000L) + (hours * 60L * 60L * 1000L)
                
                strictData.modeType = Constants.StrictModeData.MODE_TIMER
                strictData.timerEndTime = System.currentTimeMillis() + durationMs
                strictData.isEnabled = true
                strictData.passwordHash = ""
                saveSettings()
                updateUIState()
                Toast.makeText(this, "Strict Mode Activated for ${days}d ${hours}h!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showPasswordSetupDialog() {
        val input = EditText(this).apply {
            hint = "Create Password (min 4 characters)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(48, 32, 48, 32)
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Set Password")
            .setMessage("This password will be required to deactivate Strict Mode.")
            .setView(input)
            .setPositiveButton("Activate") { _, _ ->
                val password = input.text.toString()
                if (password.length >= 4) {
                    strictData.modeType = Constants.StrictModeData.MODE_PASSWORD
                    strictData.passwordHash = hashPassword(password)
                    strictData.isEnabled = true
                    strictData.timerEndTime = 0
                    strictData.forgotPasswordTimerEndTime = 0
                    saveSettings()
                    updateUIState()
                    Toast.makeText(this, "Strict Mode Activated with Password!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Password must be at least 4 characters", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    // ============== DEACTIVATION FLOW ==============
    
    private fun handleDeactivation() {
        when (strictData.modeType) {
            Constants.StrictModeData.MODE_NONE -> {
                // Instant deactivation
                showDeactivationConfirmation {
                    deactivateStrictMode()
                }
            }
            Constants.StrictModeData.MODE_TIMER -> {
                // Check if timer expired
                if (System.currentTimeMillis() >= strictData.timerEndTime) {
                    deactivateStrictMode()
                } else {
                    val remaining = strictData.timerEndTime - System.currentTimeMillis()
                    val hours = TimeUnit.MILLISECONDS.toHours(remaining)
                    val mins = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60
                    Toast.makeText(this, "Timer still active: ${hours}h ${mins}m remaining", Toast.LENGTH_SHORT).show()
                }
            }
            Constants.StrictModeData.MODE_PASSWORD -> {
                // Check if forgot password cooldown is active
                if (strictData.forgotPasswordTimerEndTime > System.currentTimeMillis()) {
                    val remaining = strictData.forgotPasswordTimerEndTime - System.currentTimeMillis()
                    val hours = TimeUnit.MILLISECONDS.toHours(remaining)
                    val mins = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60
                    
                    if (remaining <= 0) {
                        // Cooldown finished
                        deactivateStrictMode()
                    } else {
                        Toast.makeText(this, "Forgot password cooldown: ${hours}h ${mins}m remaining", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    showPasswordVerifyDialog()
                }
            }
        }
    }
    
    private fun showDeactivationConfirmation(onConfirm: () -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Deactivate Strict Mode?")
            .setMessage("Are you sure you want to disable Strict Mode? Your blocks will become editable.")
            .setPositiveButton("Deactivate") { _, _ -> onConfirm() }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showPasswordVerifyDialog() {
        val input = EditText(this).apply {
            hint = "Enter Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(48, 32, 48, 32)
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Enter Password")
            .setView(input)
            .setPositiveButton("Unlock") { _, _ ->
                val password = input.text.toString()
                if (hashPassword(password) == strictData.passwordHash) {
                    deactivateStrictMode()
                } else {
                    Toast.makeText(this, "Incorrect Password", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showForgotPasswordConfirmation() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Forgot Password?")
            .setMessage("This will start a 24-hour waiting period. After 24 hours, Strict Mode will be automatically deactivated.\n\nAre you sure?")
            .setPositiveButton("Start 24hr Wait") { _, _ ->
                strictData.forgotPasswordTimerEndTime = System.currentTimeMillis() + FORGOT_PASSWORD_WAIT_MS
                saveSettings()
                updateUIState()
                Toast.makeText(this, "24-hour cooldown started", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deactivateStrictMode() {
        strictData.isEnabled = false
        strictData.timerEndTime = 0
        strictData.forgotPasswordTimerEndTime = 0
        // Keep passwordHash for next activation if user wants
        saveSettings()
        updateUIState()
        Toast.makeText(this, "Strict Mode Deactivated", Toast.LENGTH_SHORT).show()
    }
    
    // ============== TIMER COUNTDOWN ==============
    
    private fun startTimerCountdown() {
        countdownTimer?.cancel()
        
        val remaining = strictData.timerEndTime - System.currentTimeMillis()
        if (remaining <= 0) {
            deactivateStrictMode()
            return
        }
        
        countdownTimer = object : CountDownTimer(remaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val days = TimeUnit.MILLISECONDS.toDays(millisUntilFinished)
                val hours = TimeUnit.MILLISECONDS.toHours(millisUntilFinished) % 24
                val mins = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                val secs = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                
                binding.tvTimerRemaining.text = if (days > 0) {
                    "${days}d ${hours}h ${mins}m"
                } else {
                    String.format("%02d:%02d:%02d", hours, mins, secs)
                }
            }
            
            override fun onFinish() {
                deactivateStrictMode()
            }
        }.start()
    }
    
    private fun startForgotPasswordCountdown() {
        countdownTimer?.cancel()
        
        val remaining = strictData.forgotPasswordTimerEndTime - System.currentTimeMillis()
        if (remaining <= 0) {
            deactivateStrictMode()
            return
        }
        
        countdownTimer = object : CountDownTimer(remaining, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val hours = TimeUnit.MILLISECONDS.toHours(millisUntilFinished)
                val mins = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished) % 60
                val secs = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                
                binding.tvTimerRemaining.text = String.format("%02d:%02d:%02d", hours, mins, secs)
            }
            
            override fun onFinish() {
                deactivateStrictMode()
            }
        }.start()
    }
    
    // ============== HELPER FUNCTIONS ==============
    
    private fun saveSettings() {
        prefsLoader.saveStrictModeData(strictData)
        // Optimization: Send single broadcast. Service handles both Strict Mode and Focus Mode updates with this.
        sendBroadcast(Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE))
    }
    
    private fun hashPassword(password: String): String {
        return com.neubofy.reality.utils.SecurityUtils.hashPassword(password)
    }
    
    private fun isAdminActive(): Boolean {
        return devicePolicyManager.isAdminActive(adminComponent)
    }
    
    private fun updateAntiUninstallSwitch() {
        val isAdmin = isAdminActive()
        strictData.isAntiUninstallEnabled = isAdmin
        binding.switchAntiUninstall.isChecked = isAdmin
    }
    
    private fun requestAdminPermission() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, 
            "Reality needs Device Admin to prevent uninstallation while Strict Mode is active.")
        startActivityForResult(intent, REQUEST_ADMIN_PERMISSION)
    }
    
    private fun showRemoveAdminInstructions() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Remove Device Admin")
            .setMessage("To disable Anti-Uninstall, go to:\n\nSettings > Security > Device Admin Apps > Reality > Deactivate")
            .setPositiveButton("OK", null)
            .show()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ADMIN_PERMISSION) {
            if (isAdminActive()) {
                strictData.isAntiUninstallEnabled = true
                saveSettings()
                Toast.makeText(this, "Anti-Uninstall Protection Enabled", Toast.LENGTH_SHORT).show()
                // Trigger learning for Device Admin page
                showLearnPageDialog(
                    pageType = Constants.PageType.DEVICE_ADMIN,
                    title = "Learn Device Admin Page",
                    description = "Let us learn your device's Device Admin settings page so we can block it.",
                    settingsAction = android.provider.Settings.ACTION_SECURITY_SETTINGS
                )
            } else {
                binding.switchAntiUninstall.isChecked = false
            }
        }
    }
    
    // === SETTINGS PAGE LEARNING ===
    private fun showLearnPageDialog(
        pageType: Constants.PageType,
        title: String,
        description: String,
        settingsAction: String
    ) {
        // Check if already learned
        val alreadyLearned = when (pageType) {
            Constants.PageType.ACCESSIBILITY -> learnedPages.accessibilityPageClass.isNotEmpty()
            Constants.PageType.DEVICE_ADMIN -> learnedPages.deviceAdminPageClass.isNotEmpty()
            Constants.PageType.APP_INFO -> learnedPages.appInfoPageClass.isNotEmpty()
            Constants.PageType.TIME_SETTINGS -> learnedPages.timeSettingsPageClass.isNotEmpty()
            Constants.PageType.DEVELOPER_OPTIONS -> learnedPages.developerOptionsPageClass.isNotEmpty()
        }
        
        if (alreadyLearned) {
            // Already learned, skip dialog
            return
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage("$description\n\nA floating button will appear. Navigate to the exact page you want to block, then tap 'Block This'.")
            .setPositiveButton("Start Learning") { _, _ ->
                // Send broadcast to start learning mode
                val intent = Intent(AppBlockerService.INTENT_ACTION_START_LEARNING)
                intent.putExtra(AppBlockerService.EXTRA_PAGE_TYPE, pageType.name)
                sendBroadcast(intent)
                
                // Open the settings page
                try {
                    startActivity(Intent(settingsAction))
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not open Settings. Please navigate manually.", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Skip") { _, _ -> }
            .show()
    }
    
    private fun showLearnAccessibilityDialog() {
        showLearnPageDialog(
            pageType = Constants.PageType.ACCESSIBILITY,
            title = "Learn Accessibility Page",
            description = "Let us learn your device's Accessibility settings page so we can protect it.",
            settingsAction = android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS
        )
    }
    
    private fun showLearnTimeSettingsDialog() {
        showLearnPageDialog(
            pageType = Constants.PageType.TIME_SETTINGS,
            title = "Learn Time Settings Page",
            description = "Let us learn your device's Date & Time settings page to prevent time cheating.",
            settingsAction = android.provider.Settings.ACTION_DATE_SETTINGS
        )
    }
    
    // showLearnAppInfoDialog REMOVED - App Info protection disabled
    
    private fun showLearningManageDialog(
        pageType: Constants.PageType,
        currentLearning: String,
        onRelearn: () -> Unit,
        onDelete: () -> Unit
    ) {
        if (currentLearning.isEmpty()) {
            // No learning yet - just start learning
            onRelearn()
            return
        }
        
        // Check if toggle is ON - delete not allowed
        val isToggleOn = when (pageType) {
            Constants.PageType.TIME_SETTINGS -> strictData.isTimeCheatProtectionEnabled
            Constants.PageType.ACCESSIBILITY -> strictData.isAccessibilityProtectionEnabled
            Constants.PageType.APP_INFO -> false // App Info REMOVED
            Constants.PageType.DEVICE_ADMIN -> strictData.isAntiUninstallEnabled
            else -> false
        }
        
        // Get current keywords
        val currentKeywords = when (pageType) {
            Constants.PageType.TIME_SETTINGS -> learnedPages.timeSettingsKeywords
            Constants.PageType.ACCESSIBILITY -> learnedPages.accessibilityKeywords
            Constants.PageType.DEVICE_ADMIN -> learnedPages.deviceAdminKeywords
            else -> mutableListOf()
        }
        
        val pageName = currentLearning.substringAfterLast(".")
        val keywordsText = if (currentKeywords.isNotEmpty()) 
            "\nKeywords: ${currentKeywords.joinToString(", ")}" else "\nKeywords: None"
        
        if (isToggleOn) {
            // Toggle is ON - only allow re-learn, not delete
            MaterialAlertDialogBuilder(this)
                .setTitle("Page Learned")
                .setMessage("Currently blocking: $pageName$keywordsText\n\nTo delete this learning, first turn OFF the protection toggle.")
                .setPositiveButton("Re-learn") { _, _ -> onRelearn() }
                .setNeutralButton("Edit Keywords") { _, _ -> showEditKeywordsDialog(pageType) }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            // Toggle is OFF - allow both re-learn and delete
            MaterialAlertDialogBuilder(this)
                .setTitle("Manage Learned Page")
                .setMessage("Currently learned: $pageName$keywordsText")
                .setPositiveButton("Re-learn") { _, _ -> onRelearn() }
                .setNeutralButton("Edit Keywords") { _, _ -> showEditKeywordsDialog(pageType) }
                .setNegativeButton("Delete") { _, _ -> 
                    onDelete()
                    Toast.makeText(this, "Learning deleted", Toast.LENGTH_SHORT).show()
                }
                .show()
        }
    }
    
    private fun showEditKeywordsDialog(pageType: Constants.PageType) {
        // Get current keywords
        val currentKeywords = when (pageType) {
            Constants.PageType.TIME_SETTINGS -> learnedPages.timeSettingsKeywords
            Constants.PageType.ACCESSIBILITY -> learnedPages.accessibilityKeywords
            Constants.PageType.DEVICE_ADMIN -> learnedPages.deviceAdminKeywords
            Constants.PageType.APP_INFO -> learnedPages.appInfoKeywords
            Constants.PageType.DEVELOPER_OPTIONS -> learnedPages.developerOptionsKeywords
        }
        
        // Get contextual suggestions (minimal fallback hints - not hardcoded lists)
        val suggestions = com.neubofy.reality.utils.KeywordSuggestions
            .getContextualSuggestions(pageType, emptyList())
            .filter { it !in currentKeywords }
            .take(5)
        
        // Create container
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        
        // Input field
        val input = EditText(this).apply {
            hint = "Enter keywords (comma separated)"
            setText(currentKeywords.joinToString(", "))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(input)
        
        // Only show suggestions section if we have suggestions
        if (suggestions.isNotEmpty()) {
            // Suggestions label
            val suggestLabel = android.widget.TextView(this).apply {
                text = "💡 Quick add (optional):"
                setTextColor(getColor(android.R.color.darker_gray))
                textSize = 12f
                setPadding(0, 24, 0, 8)
            }
            container.addView(suggestLabel)
            
            // Suggestions chips container (horizontal scroll)
            val chipsScroll = android.widget.HorizontalScrollView(this)
            val chipsContainer = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
            }
            
            // Add suggestion chips
            suggestions.forEach { suggestion ->
                val chip = com.google.android.material.chip.Chip(this).apply {
                    text = suggestion
                    isClickable = true
                    setOnClickListener {
                        // Add to input
                        val currentText = input.text.toString().trim()
                        input.setText(
                            if (currentText.isEmpty()) suggestion
                            else "$currentText, $suggestion"
                        )
                        input.setSelection(input.text.length)
                        // Remove this chip
                        (parent as? android.view.ViewGroup)?.removeView(this)
                    }
                }
                chipsContainer.addView(chip)
            }
            
            chipsScroll.addView(chipsContainer)
            container.addView(chipsScroll)
        }
        
        // Max info
        val infoText = android.widget.TextView(this).apply {
            text = "📝 Max 10 keywords, each max 50 chars"
            setTextColor(getColor(android.R.color.darker_gray))
            textSize = 11f
            setPadding(0, 16, 0, 0)
        }
        container.addView(infoText)
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Keywords")
            .setMessage("Enter keywords to match against page content.\nIf empty, only class name is matched.")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                // Use SecurityUtils for validation (filters empty, duplicates, too-long, max 10)
                val newKeywords = com.neubofy.reality.utils.SecurityUtils
                    .validateKeywords(input.text.toString())
                    .toMutableList()
                
                // Save keywords
                when (pageType) {
                    Constants.PageType.TIME_SETTINGS -> learnedPages.timeSettingsKeywords = newKeywords
                    Constants.PageType.ACCESSIBILITY -> learnedPages.accessibilityKeywords = newKeywords
                    Constants.PageType.DEVICE_ADMIN -> learnedPages.deviceAdminKeywords = newKeywords
                    Constants.PageType.APP_INFO -> learnedPages.appInfoKeywords = newKeywords
                    Constants.PageType.DEVELOPER_OPTIONS -> learnedPages.developerOptionsKeywords = newKeywords
                }
                
                prefsLoader.saveLearnedSettingsPages(learnedPages)
                
                // Rebuild SettingsBox immediately
                com.neubofy.reality.utils.SettingsBox.rebuildBox(applicationContext)
                
                updateLearningStatus()
                Toast.makeText(this, "✓ Keywords saved!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    // === NEW LEARNING DIALOGS ===
    
    private fun showLearnAdminDialog() {
        showLearnPageDialog(
            pageType = Constants.PageType.DEVICE_ADMIN,
            title = "Learn Device Admin Page",
            description = "Let us learn your device's Device Admin settings page to protect Reality.",
            settingsAction = android.provider.Settings.ACTION_SECURITY_SETTINGS
        )
    }
    

    
    private fun showLearnCustomPageDialog() {
        // First, ask for a name
        val inputLayout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        
        val nameInput = android.widget.EditText(this).apply {
            hint = "e.g., Developer Options"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        inputLayout.addView(nameInput)
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Add Custom Page")
            .setMessage("Name this page (for your reference):")
            .setView(inputLayout)
            .setPositiveButton("Next") { _, _ ->
                val pageName = nameInput.text.toString().ifEmpty { "Custom Page" }
                
                MaterialAlertDialogBuilder(this)
                    .setTitle("Learn: $pageName")
                    .setMessage("A draggable overlay will appear.\n\n1. Navigate to the Settings page\n2. Tap 'Block This' when on the correct page")
                    .setPositiveButton("Start") { _, _ ->
                        val intent = Intent(AppBlockerService.INTENT_ACTION_START_CUSTOM_PAGE_LEARNING)
                        intent.putExtra("custom_name", pageName)
                        sendBroadcast(intent)
                        
                        try {
                            startActivity(Intent(android.provider.Settings.ACTION_SETTINGS))
                        } catch (e: Exception) {
                            Toast.makeText(this, "Could not open Settings.", Toast.LENGTH_LONG).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun showManageCustomItemsDialog() {
        val scrollView = android.widget.ScrollView(this)
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 24, 48, 24)
        }
        scrollView.addView(container)
        
        // Helper to resolve theme color
        fun getThemeColor(attrId: Int): Int {
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(attrId, typedValue, true)
            return typedValue.data
        }
        
        val textColor = getThemeColor(com.google.android.material.R.attr.colorOnSurface)
        val subTextColor = getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        
        // Helper to add section
        fun addSection(title: String, items: MutableSet<String>, isPage: Boolean) {
            val titleView = android.widget.TextView(this).apply {
                text = title
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(textColor)
                setPadding(0, 32, 0, 16)
            }
            container.addView(titleView)
            
            if (items.isEmpty()) {
                val emptyView = android.widget.TextView(this).apply {
                    text = "No items yet"
                    setTextColor(subTextColor)
                    setPadding(0, 0, 0, 16)
                }
                container.addView(emptyView)
                return
            }
            
            // Copy list to avoid concurrent modification issues during iteration/update
            val itemList = items.toList()
            
            for (originalString in itemList) {
                // Parse: ENABLED|NAME|VALUE
                var isEnabled = true
                var name = ""
                var value = ""
                
                val parts = originalString.split("|")
                // Check if new format: Starts with 0 or 1
                if (parts.isNotEmpty() && (parts[0] == "0" || parts[0] == "1") && parts.size >= 3) {
                    isEnabled = parts[0] == "1"
                    name = parts[1]
                    // Reconstruct value from remaining parts
                    value = parts.drop(2).joinToString("|")
                } else {
                    // Legacy format
                    isEnabled = true
                    name = if (isPage) "Custom Page" else "Custom Button"
                    value = originalString
                }
                
                // Row View
                val row = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.HORIZONTAL
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    setPadding(0, 16, 0, 16)
                }
                
                // Text Container
                val textContainer = android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    layoutParams = android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                
                val nameView = android.widget.TextView(this).apply {
                    text = name
                    textSize = 16f
                    setTextColor(textColor)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                }
                
                val valueView = android.widget.TextView(this).apply {
                    text = value.take(50) + if(value.length > 50) "..." else ""
                    textSize = 12f
                    setTextColor(subTextColor)
                }
                
                textContainer.addView(nameView)
                textContainer.addView(valueView)
                row.addView(textContainer)
                
                // Switch
                val toggle = com.google.android.material.materialswitch.MaterialSwitch(this).apply {
                    isChecked = isEnabled
                }
                
                toggle.setOnCheckedChangeListener { _, isChecked ->
                     // Strict Mode Check: Cannot turn OFF if Strict Mode is active
                     if (!isChecked && strictData.isEnabled) {
                         toggle.isChecked = true // Revert
                         Toast.makeText(this@StrictModeActivity, "🚫 Strict Mode Active: Cannot disable protection!", Toast.LENGTH_SHORT).show()
                         return@setOnCheckedChangeListener
                     }
                     
                     // Update in set
                     items.remove(originalString)
                     val newStatus = if (isChecked) "1" else "0"
                     val newString = "$newStatus|$name|$value"
                     items.add(newString)
                     
                     prefsLoader.saveLearnedSettingsPages(learnedPages)
                     // Trigger service reload
                     sendBroadcast(Intent(AppBlockerService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL))
                }
                
                row.addView(toggle)
                
                // Delete Button
                val deleteBtn = android.widget.ImageButton(this).apply {
                    setImageResource(android.R.drawable.ic_menu_delete) // Standard delete icon
                    background = null
                    setColorFilter(0xFFFF4444.toInt()) // Red
                    setPadding(24, 8, 8, 8)
                    
                    setOnClickListener {
                        if (strictData.isEnabled) {
                            Toast.makeText(this@StrictModeActivity, "🚫 Strict Mode Active: Cannot delete items!", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        
                        MaterialAlertDialogBuilder(this@StrictModeActivity)
                            .setTitle("Delete Item?")
                            .setMessage("Remove '$name' from protections?")
                            .setPositiveButton("Delete") { _, _ ->
                                items.remove(originalString)
                                prefsLoader.saveLearnedSettingsPages(learnedPages)
                                sendBroadcast(Intent(AppBlockerService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL))
                                container.removeView(row) // Remove row visually
                                updateLearningStatus() // Update count
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
                
                row.addView(deleteBtn)
                container.addView(row)
                
                // Divider
                val divider = android.view.View(this).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 1)
                    setBackgroundColor(0x33AAAAAA.toInt())
                }
                container.addView(divider)
            }
        }
        
        addSection("Custom Pages", learnedPages.customBlockedPages, true)
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Manage Custom Items")
            .setView(scrollView)
            .setPositiveButton("Done") { _, _ -> updateLearningStatus() }
            .setNeutralButton("🗑️ Reset All Data") { _, _ -> showResetAllDataDialog() }
            .show()
    }
    
    /**
     * Reset ALL learned data - pages, keywords
     * Shows double confirmation to prevent accidents
     */
    private fun showResetAllDataDialog() {
        if (strictData.isEnabled) {
            Toast.makeText(this, "🚫 Cannot reset while Strict Mode is active!", Toast.LENGTH_SHORT).show()
            return
        }
        
        val pageCount = listOf(
            learnedPages.accessibilityPageClass,
            learnedPages.deviceAdminPageClass,
            learnedPages.appInfoPageClass,
            learnedPages.timeSettingsPageClass,
            learnedPages.developerOptionsPageClass
        ).count { it.isNotEmpty() }
        
        val customPages = learnedPages.customBlockedPages.size
        
        val summary = """
            This will DELETE all learned data:
            
            • $pageCount standard pages
            • $customPages custom pages
            • All keywords
            
            This cannot be undone!
        """.trimIndent()
        
        MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ Reset All Learned Data")
            .setMessage(summary)
            .setPositiveButton("Reset Everything") { _, _ ->
                // Double confirmation
                MaterialAlertDialogBuilder(this)
                    .setTitle("Are you absolutely sure?")
                    .setMessage("All pages and keywords will be permanently deleted.")
                    .setPositiveButton("Yes, Delete All") { _, _ ->
                        performResetAllData()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun performResetAllData() {
        // Clear all page learnings
        learnedPages.accessibilityPageClass = ""
        learnedPages.accessibilityPagePackage = ""
        learnedPages.accessibilityKeywords.clear()
        
        learnedPages.deviceAdminPageClass = ""
        learnedPages.deviceAdminPagePackage = ""
        learnedPages.deviceAdminKeywords.clear()
        
        learnedPages.appInfoPageClass = ""
        learnedPages.appInfoPagePackage = ""
        learnedPages.appInfoKeywords.clear()
        
        learnedPages.timeSettingsPageClass = ""
        learnedPages.timeSettingsPagePackage = ""
        learnedPages.timeSettingsKeywords.clear()
        
        learnedPages.developerOptionsPageClass = ""
        learnedPages.developerOptionsPagePackage = ""
        learnedPages.developerOptionsKeywords.clear()
        
        // Clear custom items
        learnedPages.customBlockedPages.clear()
        
        // Save
        prefsLoader.saveLearnedSettingsPages(learnedPages)
        
        // Rebuild SettingsBox
        com.neubofy.reality.utils.SettingsBox.rebuildBox(applicationContext)
        
        // Update UI
        updateLearningStatus()
        
        Toast.makeText(this, "✓ All learned data has been reset", Toast.LENGTH_LONG).show()
    }
}
