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
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    
    private var countdownTimer: CountDownTimer? = null
    
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
            binding.switchAccessibilityProtection to { v: Boolean -> strictData.isAccessibilityProtectionEnabled = v }
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
                }
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
        sendBroadcast(Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE))
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
            } else {
                binding.switchAntiUninstall.isChecked = false
            }
        }
    }
}
