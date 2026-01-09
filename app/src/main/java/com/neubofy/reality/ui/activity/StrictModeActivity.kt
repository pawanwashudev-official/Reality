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

class StrictModeActivity : AppCompatActivity() {
    
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
        
        binding.toolbar.setNavigationOnClickListener { finish() }
        
        loadSettings()
        attachListeners()
        updateUIState()
    }
    
    override fun onResume() {
        super.onResume()
        strictData = prefsLoader.getStrictModeData()
        updateAntiUninstallSwitch()
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

        binding.switchAutoDndLock.isChecked = strictData.isAutoDndLocked
        binding.switchCalendarLock.isChecked = strictData.isCalendarLocked
        binding.switchAntiTimeCheat.isChecked = strictData.isTimeCheatProtectionEnabled
        binding.switchAccessibilityProtection.isChecked = strictData.isAccessibilityProtectionEnabled
        binding.switchAppInfoProtection.isChecked = strictData.isAppInfoProtectionEnabled
        
        updateLearningStatus()
    }
    
    private fun updateLearningStatus() {
        // Time Cheat Status
        if (learnedPages.timeSettingsPageClass.isNotEmpty()) {
            binding.tvTimeCheatStatus.text = "✓ Learned: ${learnedPages.timeSettingsPageClass.substringAfterLast(".")}"
            binding.tvTimeCheatStatus.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            binding.tvTimeCheatStatus.text = "⚠️ Learning required - tap to learn"
            binding.tvTimeCheatStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
        }
        
        // Accessibility Status
        if (learnedPages.accessibilityPageClass.isNotEmpty()) {
            binding.tvAccessibilityStatus.text = "✓ Learned: ${learnedPages.accessibilityPageClass.substringAfterLast(".")}"
            binding.tvAccessibilityStatus.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            binding.tvAccessibilityStatus.text = "⚠️ Learning required - tap to learn"
            binding.tvAccessibilityStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
        }
        
        // App Info Status
        if (learnedPages.appInfoPageClass.isNotEmpty()) {
            binding.tvAppInfoStatus.text = "✓ Learned: ${learnedPages.appInfoPageClass.substringAfterLast(".")}"
            binding.tvAppInfoStatus.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            binding.tvAppInfoStatus.text = "⚠️ Learning required - tap to learn"
            binding.tvAppInfoStatus.setTextColor(getColor(android.R.color.holo_orange_dark))
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

        binding.switchAutoDndLock.isEnabled = true
        binding.switchAntiTimeCheat.isEnabled = true
        binding.switchAntiUninstall.isEnabled = true
    }
    
    private fun attachListeners() {
        // --- Protection Switches with Ratchet Logic ---
        val switches = listOf(
            binding.switchBlocklistLock to { v: Boolean -> strictData.isBlocklistLocked = v },
            binding.switchBedtimeLock to { v: Boolean -> strictData.isBedtimeLocked = v },
            binding.switchAppLimitLock to { v: Boolean -> strictData.isAppLimitLocked = v },
            binding.switchGroupLimitLock to { v: Boolean -> strictData.isGroupLimitLocked = v },
            binding.switchScheduleLock to { v: Boolean -> strictData.isScheduleLocked = v },

            binding.switchAutoDndLock to { v: Boolean -> strictData.isAutoDndLocked = v },
            binding.switchCalendarLock to { v: Boolean -> strictData.isCalendarLocked = v },
            binding.switchAntiTimeCheat to { v: Boolean -> strictData.isTimeCheatProtectionEnabled = v },
            binding.switchAccessibilityProtection to { v: Boolean -> strictData.isAccessibilityProtectionEnabled = v },
            binding.switchAppInfoProtection to { v: Boolean -> strictData.isAppInfoProtectionEnabled = v }
        )
        
        switches.forEach { (switchView, updateFunc) ->
            switchView.setOnClickListener {
                val isTryingTurnOff = !switchView.isChecked
                
                if (strictData.isEnabled && isTryingTurnOff) {
                    // RATCHET: Cannot turn OFF while active
                    switchView.isChecked = true
                    Toast.makeText(this, "Cannot disable while Strict Mode is active!", Toast.LENGTH_SHORT).show()
                } else {
                    updateFunc(switchView.isChecked)
                    saveSettings()
                    
                    // Trigger learning for specific protections when turned ON
                    if (switchView.isChecked) {
                        when (switchView) {
                            binding.switchAccessibilityProtection -> showLearnAccessibilityDialog()
                            binding.switchAntiTimeCheat -> showLearnTimeSettingsDialog()
                            binding.switchAppInfoProtection -> showLearnAppInfoDialog()
                        }
                    }
                }
            }
        }
        
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
        binding.tvAppInfoStatus.setOnClickListener {
            if (strictData.isEnabled) {
                Toast.makeText(this, "Cannot modify learning while Strict Mode is active!", Toast.LENGTH_SHORT).show()
            } else {
                showLearningManageDialog(
                    Constants.PageType.APP_INFO,
                    learnedPages.appInfoPageClass,
                    { showLearnAppInfoDialog() },
                    { 
                        learnedPages.appInfoPageClass = ""
                        prefsLoader.saveLearnedSettingsPages(learnedPages)
                        binding.switchAppInfoProtection.isChecked = false
                        strictData.isAppInfoProtectionEnabled = false
                        saveSettings()
                        updateLearningStatus()
                    }
                )
            }
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
        // Send BOTH refresh broadcasts so blocker gets updated data
        sendBroadcast(Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE))
        sendBroadcast(Intent(AppBlockerService.INTENT_ACTION_REFRESH_ANTI_UNINSTALL))
    }
    
    private fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
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
    
    private fun showLearnAppInfoDialog() {
        showLearnPageDialog(
            pageType = Constants.PageType.APP_INFO,
            title = "Learn App Info Page",
            description = "Navigate to Reality's App Info page (Settings → Apps → Reality). This blocks Force Stop and Uninstall.",
            settingsAction = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        )
    }
    
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
            Constants.PageType.APP_INFO -> strictData.isAppInfoProtectionEnabled
            Constants.PageType.DEVICE_ADMIN -> strictData.isAntiUninstallEnabled
            else -> false
        }
        
        val pageName = currentLearning.substringAfterLast(".")
        
        if (isToggleOn) {
            // Toggle is ON - only allow re-learn, not delete
            MaterialAlertDialogBuilder(this)
                .setTitle("Page Learned")
                .setMessage("Currently blocking: $pageName\n\nTo delete this learning, first turn OFF the protection toggle.")
                .setPositiveButton("Re-learn") { _, _ -> onRelearn() }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            // Toggle is OFF - allow both re-learn and delete
            MaterialAlertDialogBuilder(this)
                .setTitle("Manage Learned Page")
                .setMessage("Currently learned: $pageName")
                .setPositiveButton("Re-learn") { _, _ -> onRelearn() }
                .setNegativeButton("Delete") { _, _ -> 
                    onDelete()
                    Toast.makeText(this, "Learning deleted", Toast.LENGTH_SHORT).show()
                }
                .setNeutralButton("Cancel", null)
                .show()
        }
    }
}
