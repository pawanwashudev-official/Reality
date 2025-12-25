package com.neubofy.reality.ui.activity

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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
import java.util.Calendar
import kotlinx.coroutines.*

class StrictModeActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityStrictModeSettingsBinding
    private lateinit var prefsLoader: SavedPreferencesLoader
    private lateinit var strictData: Constants.StrictModeData
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    
    companion object {
        const val REQUEST_ADMIN_PERMISSION = 1001
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
        
        setupProtectionModeUI()
        attachListeners()
        loadSettings() 
    }
    
    override fun onResume() {
        super.onResume()
        updateAntiUninstallSwitch()
        updateTimerDisplay()
        updateUIState()
    }
    
    private fun updateUIState() {
        if (strictData.isEnabled) {
            // STRICT MODE ACTIVE
            binding.tvStatusTitle.text = "Strict Mode Active"
            val errorColor = ContextCompat.getColor(this, android.R.color.holo_red_light)
            binding.tvStatusTitle.setTextColor(errorColor)
            binding.tvStatusDesc.text = "Settings are locked. Deactivate to edit."
            binding.imgStatusIcon.setImageResource(R.drawable.baseline_lock_24)
            binding.imgStatusIcon.imageTintList = ColorStateList.valueOf(errorColor)
            binding.cardStatus.setCardBackgroundColor(Color.parseColor("#F5F5F5")) 

            setIsEditingEnabled(false)
            
            // SHOW DEACTIVATE BUTTON
            binding.btnActivate.visibility = View.VISIBLE
            binding.btnActivate.text = "Deactivate Strict Mode"
            binding.btnActivate.setBackgroundColor(errorColor) // or tint
            binding.btnActivate.setTextColor(Color.WHITE)
            
        } else {
            // STRICT MODE INACTIVE
            binding.tvStatusTitle.text = "Strict Mode Inactive"
            binding.tvStatusTitle.setTextColor(Color.WHITE)
            
            binding.imgStatusIcon.setImageResource(R.drawable.baseline_lock_open_24)
            binding.imgStatusIcon.imageTintList = null
            binding.cardStatus.setCardBackgroundColor(Color.parseColor("#2D2D2D")) 

            binding.tvStatusDesc.text = "Configure your protection settings below."
            
            setIsEditingEnabled(true)
            
            // SHOW ACTIVATE BUTTON
            binding.btnActivate.visibility = View.VISIBLE
            binding.btnActivate.text = "Save & Activate"
            
            // Get Primary Color from Theme
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
            val primaryColor = typedValue.data
            
            binding.btnActivate.backgroundTintList = ColorStateList.valueOf(primaryColor)
            binding.btnActivate.setTextColor(Color.WHITE) // Assuming primary is dark enough, or use OnPrimary
        }
    }
    
    private fun setIsEditingEnabled(enabled: Boolean) {
        binding.radioGroupProtection.isEnabled = enabled
        for (i in 0 until binding.radioGroupProtection.childCount) {
             binding.radioGroupProtection.getChildAt(i).isEnabled = enabled
        }
        
        binding.btnSetTimer.isEnabled = enabled
        binding.btnSetPassword.isEnabled = enabled
        binding.etPassword.isEnabled = enabled
        
        binding.switchBlocklistLock.isEnabled = enabled
        binding.switchBedtimeLock.isEnabled = enabled
        binding.switchAppLimitLock.isEnabled = enabled
        binding.switchGroupLimitLock.isEnabled = enabled
        binding.switchAntiUninstall.isEnabled = enabled
    }
    
    private fun setupProtectionModeUI() {
        val radios = listOf(binding.radioNone, binding.radioTimer, binding.radioPassword)
        
        radios.forEach { rb ->
            rb.setOnClickListener {
                if (!binding.radioGroupProtection.isEnabled) {
                    rb.isChecked = !rb.isChecked // Revert if disabled logic fails, but we used setIsEditingEnabled to handle View enabling.
                    // Actually, if disabled, click listener might still fire if view is clickable.
                    // But setIsEditingEnabled disables the children.
                    return@setOnClickListener
                }
                
                // Enforce single selection
                radios.forEach { other -> 
                    if (other != rb) other.isChecked = false 
                }
                rb.isChecked = true
                
                when (rb.id) {
                    binding.radioNone.id -> {
                        strictData.modeType = Constants.StrictModeData.MODE_NONE
                        binding.layoutTimer.visibility = View.GONE
                        binding.layoutPassword.visibility = View.GONE
                    }
                    binding.radioTimer.id -> {
                        strictData.modeType = Constants.StrictModeData.MODE_TIMER
                        binding.layoutTimer.visibility = View.VISIBLE
                        binding.layoutPassword.visibility = View.GONE
                        updateTimerDisplay()
                    }
                    binding.radioPassword.id -> {
                        strictData.modeType = Constants.StrictModeData.MODE_PASSWORD
                        binding.layoutTimer.visibility = View.GONE
                        binding.layoutPassword.visibility = View.VISIBLE
                    }
                }
                saveSettings()
            }
        }
    }
    
    private fun loadSettings() {
        when (strictData.modeType) {
            Constants.StrictModeData.MODE_NONE -> binding.radioNone.isChecked = true
            Constants.StrictModeData.MODE_TIMER -> {
                binding.radioTimer.isChecked = true
                binding.layoutTimer.visibility = View.VISIBLE
            }
            Constants.StrictModeData.MODE_PASSWORD -> {
                binding.radioPassword.isChecked = true
                binding.layoutPassword.visibility = View.VISIBLE
            }
        }
        
        binding.switchBlocklistLock.isChecked = strictData.isBlocklistLocked
        binding.switchBedtimeLock.isChecked = strictData.isBedtimeLocked
        binding.switchAppLimitLock.isChecked = strictData.isAppLimitLocked
        binding.switchGroupLimitLock.isChecked = strictData.isGroupLimitLocked
        
        updateAntiUninstallSwitch()
        updateUIState()
    }
    
    private fun attachListeners() {
        binding.btnSetTimer.setOnClickListener { showTimerPicker() }
        
        binding.btnSetPassword.setOnClickListener {
            val password = binding.etPassword.text.toString()
            if (password.length >= 4) {
                strictData.passwordHash = hashPassword(password)
                strictData.modeType = Constants.StrictModeData.MODE_PASSWORD
                // Automatically activate strict mode when password is set
                strictData.isEnabled = true
                saveSettings()
                binding.etPassword.text?.clear()
                updateUIState()
                Toast.makeText(this, "Password set and Strict Mode Activated!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Min 4 characters required", Toast.LENGTH_SHORT).show()
            }
        }
        
        val switches = listOf(
            binding.switchBlocklistLock to { v: Boolean -> strictData.isBlocklistLocked = v },
            binding.switchBedtimeLock to { v: Boolean -> strictData.isBedtimeLocked = v },
            binding.switchAppLimitLock to { v: Boolean -> strictData.isAppLimitLocked = v },
            binding.switchGroupLimitLock to { v: Boolean -> strictData.isGroupLimitLocked = v }
        )
        
        switches.forEach { (switchView, updateFunc) ->
            switchView.setOnClickListener {
                if (switchView.isEnabled) {
                    updateFunc(switchView.isChecked)
                    saveSettings()
                }
            }
        }
        
        binding.switchAntiUninstall.setOnClickListener {
            if (!binding.switchAntiUninstall.isEnabled) return@setOnClickListener
            
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
        
        binding.btnActivate.setOnClickListener {
            if (strictData.isEnabled) {
                // TRYING TO DEACTIVATE
                handleDeactivationAttempt()
            } else {
                // TRYING TO ACTIVATE
                if (validateActivation()) {
                    strictData.isEnabled = true
                    saveSettings()
                    updateUIState()
                    Toast.makeText(this, "Strict Mode Activated!", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        binding.btnManageLimits.setOnClickListener {
             if (strictData.isEnabled) {
                 Toast.makeText(this, "Strict Mode is Active. Unlock to edit.", Toast.LENGTH_SHORT).show()
             } else {
                 showManageLimitsDialog()
             }
        }
    }
    
    // Wrapper for List Items
    sealed class StrictItem {
        data class App(val entity: com.neubofy.reality.data.db.AppLimitEntity) : StrictItem()
        data class Group(val entity: com.neubofy.reality.data.db.AppGroupEntity) : StrictItem()
    }
    
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.Job())

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun showManageLimitsDialog() {
        android.widget.Toast.makeText(this, "Loading...", android.widget.Toast.LENGTH_SHORT).show()
        
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val db = com.neubofy.reality.data.db.AppDatabase.getDatabase(applicationContext)
            val limits = try { db.appLimitDao().getAllLimits() } catch(e: Exception) { emptyList() }
            val groups = try { db.appGroupDao().getAllGroups() } catch(e: Exception) { emptyList() }
            
            if (limits.isEmpty() && groups.isEmpty()) {
                 kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                     android.widget.Toast.makeText(this@StrictModeActivity, "No limits found to configure", android.widget.Toast.LENGTH_SHORT).show()
                 }
                 return@launch
            }
            
            val itemList = mutableListOf<StrictItem>()
            val labels = mutableListOf<String>()
            val checkedStates = BooleanArray(limits.size + groups.size)
            var index = 0
            
            // Add Groups
            groups.forEach { group ->
                itemList.add(StrictItem.Group(group))
                labels.add("Group: ${group.name}")
                checkedStates[index] = group.isStrict
                index++
            }
            
            // Add Apps
            val pm = packageManager
            limits.forEach { limit ->
                itemList.add(StrictItem.App(limit))
                val appName = try {
                     pm.getApplicationLabel(pm.getApplicationInfo(limit.packageName, 0)).toString()
                } catch (e: Exception) { limit.packageName }
                labels.add("App: $appName")
                checkedStates[index] = limit.isStrict
                index++
            }
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                MaterialAlertDialogBuilder(this@StrictModeActivity)
                    .setTitle("Select Strict Limits")
                    .setMultiChoiceItems(labels.toTypedArray(), checkedStates) { _, which, isChecked ->
                        checkedStates[which] = isChecked
                    }
                    .setPositiveButton("Save") { _, _ ->
                        saveStrictLimits(itemList, checkedStates)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }
    
    private fun saveStrictLimits(items: List<StrictItem>, states: BooleanArray) {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val db = com.neubofy.reality.data.db.AppDatabase.getDatabase(applicationContext)
            var changes = 0
            
            items.forEachIndexed { i, item ->
                val newStrict = states[i]
                when(item) {
                    is StrictItem.App -> {
                        if (item.entity.isStrict != newStrict) {
                            val updated = item.entity.copy(isStrict = newStrict)
                            db.appLimitDao().insert(updated) // REPLACE
                            changes++
                        }
                    }
                    is StrictItem.Group -> {
                        if (item.entity.isStrict != newStrict) {
                            val updated = item.entity.copy(isStrict = newStrict)
                            db.appGroupDao().update(updated)
                            changes++
                        }
                    }
                }
            }
            
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (changes > 0) {
                    android.widget.Toast.makeText(this@StrictModeActivity, "Updated $changes limits", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    android.widget.Toast.makeText(this@StrictModeActivity, "No changes made", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun handleDeactivationAttempt() {
        // 1. Maintenance Window Check
        if (StrictLockUtils.isMaintenanceWindow()) {
            disableStrictMode()
            Toast.makeText(this, "Disabled via Maintenance Window", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 2. Timer Check
        if (strictData.modeType == Constants.StrictModeData.MODE_TIMER) {
            if (System.currentTimeMillis() < strictData.timerEndTime) {
                val remaining = strictData.timerEndTime - System.currentTimeMillis()
                val hrs = remaining / 3600000
                val mins = (remaining % 3600000) / 60000
                
                MaterialAlertDialogBuilder(this)
                    .setTitle("Locked by Timer")
                    .setMessage("Strict Mode is active for another ${hrs}h ${mins}m.\nYou cannot disable it until the timer expires.")
                    .setPositiveButton("OK", null)
                    .show()
                return
            } else {
                // Timer expired -> Allow disable
                disableStrictMode()
                Toast.makeText(this, "Timer Expired - Strict Mode Disabled", Toast.LENGTH_SHORT).show()
            }
        } 
        // 3. Password Check
        else if (strictData.modeType == Constants.StrictModeData.MODE_PASSWORD) {
            showPasswordUnlockDialog()
        } 
        // 4. None Check (Standard)
        else {
            disableStrictMode()
        }
    }
    
    private fun showPasswordUnlockDialog() {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        input.hint = "Enter Password"
        val padding = (16 * resources.displayMetrics.density).toInt()
        input.setPadding(padding, padding, padding, padding)
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Enter Password")
            .setView(input)
            .setPositiveButton("Unlock") { _, _ ->
                val entered = input.text.toString()
                if (hashPassword(entered) == strictData.passwordHash) {
                    disableStrictMode()
                    Toast.makeText(this, "Unlocked Successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Incorrect Password", Toast.LENGTH_SHORT).show()
                    // 12-Hour Bailout Offer?
                    offerRecovery()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun offerRecovery() {
        // Simple logic: If failed, show a snackbar/toast or nothing.
        // Or specific dialog.
    }
    
    private fun disableStrictMode() {
        strictData.isEnabled = false
        saveSettings()
        // Legacy Support
        getSharedPreferences("anti_uninstall", MODE_PRIVATE).edit().putBoolean("is_anti_uninstall_on", false).apply()
        updateUIState()
    }
    
    private fun validateActivation(): Boolean {
        if (strictData.modeType == Constants.StrictModeData.MODE_PASSWORD) {
            if (strictData.passwordHash.isEmpty()) {
                Toast.makeText(this, "Please set a password first", Toast.LENGTH_LONG).show()
                return false
            }
        } else if (strictData.modeType == Constants.StrictModeData.MODE_TIMER) {
            if (strictData.timerEndTime <= System.currentTimeMillis()) {
                 Toast.makeText(this, "Please set a timer duration first", Toast.LENGTH_LONG).show()
                 showTimerPicker()
                 return false
            }
        }
        return true
    }
    
    private fun MaterialAlertDialogBuilder(context: android.content.Context) = com.google.android.material.dialog.MaterialAlertDialogBuilder(context)

    private fun requestAdminPermission() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required for Anti-Uninstall.")
        startActivityForResult(intent, REQUEST_ADMIN_PERMISSION)
    }
    
    private fun showRemoveAdminInstructions() {
        AlertDialog.Builder(this)
            .setTitle("Remove Admin First")
            .setMessage("To disable Anti-Uninstall, go to Android Settings -> Security -> Device Admin apps and deactivate Reality.")
            .setPositiveButton("OK", null)
            .show()
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ADMIN_PERMISSION) {
            if (resultCode == RESULT_OK && isAdminActive()) {
                strictData.isAntiUninstallEnabled = true
                saveSettings()
                binding.switchAntiUninstall.isChecked = true
                Toast.makeText(this, "Anti-Uninstall Enabled", Toast.LENGTH_SHORT).show()
            } else {
                binding.switchAntiUninstall.isChecked = false
            }
        }
    }
    
    private fun showTimerPicker() {
         val items = arrayOf("1 hour", "3 hours", "6 hours", "12 hours", "1 day", "3 days", "7 days")
         val durations = arrayOf(1L, 3L, 6L, 12L, 24L, 72L, 168L)
         
         AlertDialog.Builder(this)
            .setTitle("Set Protection Timer")
            .setItems(items) { _, which ->
                val durationMs = durations[which] * 3600 * 1000
                strictData.timerEndTime = System.currentTimeMillis() + durationMs
                saveSettings()
                updateTimerDisplay()
                Toast.makeText(this, "${items[which]} selected", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun updateTimerDisplay() {
        if (strictData.timerEndTime > System.currentTimeMillis()) {
             val remaining = strictData.timerEndTime - System.currentTimeMillis()
             val h = remaining / (3600 * 1000)
             val m = (remaining % (3600 * 1000)) / (60 * 1000)
             binding.tvTimerEndTime.text = "Ends in: ${h}h ${m}m"
        } else {
             binding.tvTimerEndTime.text = "No timer set"
        }
    }
    
    private fun hashPassword(password: String): String {
        val bytes = password.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
    
    private fun isAdminActive(): Boolean = devicePolicyManager.isAdminActive(adminComponent)
    
    private fun updateAntiUninstallSwitch() {
        val active = isAdminActive()
        binding.switchAntiUninstall.isChecked = active && strictData.isAntiUninstallEnabled
        if (!active && strictData.isAntiUninstallEnabled) {
            strictData.isAntiUninstallEnabled = false
            saveSettings()
        }
    }
    
    private fun saveSettings() {
        prefsLoader.saveStrictModeData(strictData)
        val intent = Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE)
        intent.setPackage(packageName)
        sendBroadcast(intent)
    }
}
