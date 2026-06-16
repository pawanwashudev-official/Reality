package com.neubofy.reality.ui.fragments.installation

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.neubofy.reality.R
import com.neubofy.reality.databinding.FragmentOnboardingPermissionsBinding
import com.neubofy.reality.services.AppBlockerService
import com.neubofy.reality.ui.activity.MainActivity

/**
 * Comprehensive onboarding permissions fragment.
 * 
 * Guides users through granting all 5 essential permissions:
 * 1. Accessibility Service (REQUIRED) - Core blocking functionality
 * 2. Display Over Apps (REQUIRED) - Block screen overlay
 * 3. Usage Statistics (REQUIRED) - Time tracking & limits
 * 4. Notifications (Recommended) - Alerts & reminders
 * 5. Battery Optimization (Recommended) - Background reliability
 * 
 * Each permission has a detailed explanation so users understand
 * exactly WHY it's needed and how their privacy is protected.
 */
class OnboardingPermissionsFragment : Fragment() {

    private var _binding: FragmentOnboardingPermissionsBinding? = null
    private val binding get() = _binding!!

    // Permission states
    private var accessibilityGranted = false
    private var overlayGranted = false
    private var usageStatsGranted = false
    private var notificationsGranted = false
    private var batteryGranted = false

    // Activity Result Launchers
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            notificationsGranted = isGranted
            updateUI()
        }

    private val accessibilitySettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            accessibilityGranted = isAccessibilityServiceEnabled()
            updateUI()
        }

    private val overlaySettingsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            overlayGranted = Settings.canDrawOverlays(requireContext())
            updateUI()
        }

    private val usageStatsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            usageStatsGranted = isUsageStatsPermissionGranted()
            updateUI()
        }

    private val batteryOptimizationLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            batteryGranted = isBatteryOptimizationDisabled()
            updateUI()
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentOnboardingPermissionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Check current permission states
        checkAllPermissions()
        updateUI()

        // Setup click listeners for each permission card
        setupClickListeners()
    }

    private fun setupClickListeners() {
        // 1. Accessibility Service
        binding.cardAccessibility.setOnClickListener {
            if (!accessibilityGranted) {
                showAccessibilityGuide()
            } else {
                Toast.makeText(requireContext(), "✓ Accessibility already enabled", Toast.LENGTH_SHORT).show()
            }
        }

        // 2. Display Over Apps (Overlay)
        binding.cardOverlay.setOnClickListener {
            if (!overlayGranted) {
                openOverlaySettings()
            } else {
                Toast.makeText(requireContext(), "✓ Overlay already enabled", Toast.LENGTH_SHORT).show()
            }
        }

        // 3. Usage Stats
        binding.cardUsageStats.setOnClickListener {
            if (!usageStatsGranted) {
                openUsageStatsSettings()
            } else {
                Toast.makeText(requireContext(), "✓ Usage stats already enabled", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. Notifications
        binding.cardNotifications.setOnClickListener {
            if (!notificationsGranted) {
                requestNotificationPermission()
            } else {
                Toast.makeText(requireContext(), "✓ Notifications already enabled", Toast.LENGTH_SHORT).show()
            }
        }

        // 5. Battery Optimization
        binding.cardBattery.setOnClickListener {
            if (!batteryGranted) {
                requestBatteryOptimization()
            } else {
                Toast.makeText(requireContext(), "✓ Battery optimization disabled", Toast.LENGTH_SHORT).show()
            }
        }

        // Continue button
        binding.btnContinue.setOnClickListener {
            val requiredGranted = accessibilityGranted && overlayGranted && usageStatsGranted
            
            if (requiredGranted) {
                completeOnboarding()
            } else {
                // Guide user to first missing REQUIRED permission
                when {
                    !accessibilityGranted -> {
                        Toast.makeText(requireContext(), "⚠️ Accessibility is required for blocking to work", Toast.LENGTH_LONG).show()
                        showAccessibilityGuide()
                    }
                    !overlayGranted -> {
                        Toast.makeText(requireContext(), "⚠️ Display Over Apps is required to show block screens", Toast.LENGTH_LONG).show()
                        openOverlaySettings()
                    }
                    !usageStatsGranted -> {
                        Toast.makeText(requireContext(), "⚠️ Usage Stats is required for time limits", Toast.LENGTH_LONG).show()
                        openUsageStatsSettings()
                    }
                }
            }
        }

        // Skip text
        binding.tvSkip.setOnClickListener {
            showSkipWarningDialog()
        }
    }

    private fun showAccessibilityGuide() {
        // Show dialog explaining accessibility, then open settings
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("🔐 Enable Accessibility Service")
            .setMessage(
                "To block apps, Reality needs to know which app you're currently using.\n\n" +
                "On the next screen:\n" +
                "1. Find 'Reality' in the list\n" +
                "2. Tap on it\n" +
                "3. Toggle ON 'Use Reality'\n" +
                "4. Confirm the permission\n\n" +
                "🔒 Privacy: Reality ONLY sees the app name. It cannot read your messages, passwords, or any content."
            )
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                accessibilitySettingsLauncher.launch(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    @SuppressLint("BatteryLife")
    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${requireContext().packageName}")
        )
        overlaySettingsLauncher.launch(intent)
    }

    private fun openUsageStatsSettings() {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        try {
            usageStatsLauncher.launch(intent)
        } catch (e: Exception) {
            // Fallback for devices that don't support direct launch
            startActivity(intent)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            notificationsGranted = true
            updateUI()
        }
    }

    @SuppressLint("BatteryLife")
    private fun requestBatteryOptimization() {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${requireContext().packageName}")
        }
        batteryOptimizationLauncher.launch(intent)
    }

    private fun showSkipWarningDialog() {
        val missing = mutableListOf<String>()
        if (!accessibilityGranted) missing.add("• App blocking won't work")
        if (!overlayGranted) missing.add("• Block screens won't appear")
        if (!usageStatsGranted) missing.add("• Time limits won't work")
        if (!notificationsGranted) missing.add("• No reminders or alerts")
        if (!batteryGranted) missing.add("• May stop working in background")

        if (missing.isEmpty()) {
            completeOnboarding()
            return
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("⚠️ Skip Permissions?")
            .setMessage(
                "Without all permissions:\n\n${missing.joinToString("\n")}\n\n" +
                "You can grant them later in Settings.\n\nProceed anyway?"
            )
            .setPositiveButton("Skip Anyway") { _, _ ->
                completeOnboarding()
            }
            .setNegativeButton("Go Back", null)
            .show()
    }

    private fun completeOnboarding() {
        // Mark onboarding as complete
        val prefs = requireContext().getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("first_launch_complete", true)
            .putBoolean("onboarding_v2_complete", true)
            .apply()

        // Navigate to main activity
        val intent = Intent(requireContext(), MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finish()
    }

    private fun checkAllPermissions() {
        accessibilityGranted = isAccessibilityServiceEnabled()
        overlayGranted = Settings.canDrawOverlays(requireContext())
        usageStatsGranted = isUsageStatsPermissionGranted()
        notificationsGranted = isNotificationPermissionGranted()
        batteryGranted = isBatteryOptimizationDisabled()
    }

    private fun updateUI() {
        // Update icons based on permission state
        updateIcon(binding.iconAccessibility, accessibilityGranted, true)
        updateIcon(binding.iconOverlay, overlayGranted, true)
        updateIcon(binding.iconUsage, usageStatsGranted, true)
        updateIcon(binding.iconNotifications, notificationsGranted, false)
        updateIcon(binding.iconBattery, batteryGranted, false)

        // Update arrows (hide if granted)
        binding.arrowAccessibility.visibility = if (accessibilityGranted) View.INVISIBLE else View.VISIBLE
        binding.arrowOverlay.visibility = if (overlayGranted) View.INVISIBLE else View.VISIBLE
        binding.arrowUsage.visibility = if (usageStatsGranted) View.INVISIBLE else View.VISIBLE
        binding.arrowNotifications.visibility = if (notificationsGranted) View.INVISIBLE else View.VISIBLE
        binding.arrowBattery.visibility = if (batteryGranted) View.INVISIBLE else View.VISIBLE

        // Update progress text
        val grantedCount = listOf(
            accessibilityGranted,
            overlayGranted,
            usageStatsGranted,
            notificationsGranted,
            batteryGranted
        ).count { it }

        binding.tvProgress.text = "$grantedCount of 5 permissions granted"

        // Update button text based on required permissions
        val requiredGranted = accessibilityGranted && overlayGranted && usageStatsGranted
        
        binding.btnContinue.text = when {
            grantedCount == 5 -> "🚀 Let's Go!"
            requiredGranted -> "Continue (optional permissions skipped)"
            else -> "Grant Required Permissions"
        }
        
        // Change button color when all required are granted
        if (requiredGranted) {
            binding.btnContinue.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.onboarding_accent))
        }
    }

    private fun updateIcon(icon: ImageView, isGranted: Boolean, isRequired: Boolean) {
        if (isGranted) {
            icon.setImageResource(R.drawable.baseline_check_circle_24)
            icon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.onboarding_icon_granted))
        } else {
            icon.setImageResource(if (isRequired) R.drawable.baseline_error_24 else R.drawable.baseline_radio_button_unchecked_24)
            icon.setColorFilter(
                ContextCompat.getColor(
                    requireContext(),
                    if (isRequired) R.color.onboarding_icon_required else R.color.onboarding_icon_pending
                )
            )
        }
    }

    // Permission Check Helpers
    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            requireContext().contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        
        val colonSplitter = enabledServices.split(":")
        return colonSplitter.any { 
            it.contains(AppBlockerService::class.java.simpleName) ||
            it.contains(requireContext().packageName)
        }
    }

    private fun isUsageStatsPermissionGranted(): Boolean {
        val appOps = requireContext().getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            requireContext().packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isNotificationPermissionGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No permission needed pre-Android 13
        }
    }

    private fun isBatteryOptimizationDisabled(): Boolean {
        val powerManager = requireContext().getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)
    }

    override fun onResume() {
        super.onResume()
        // Re-check all permissions when returning from settings
        checkAllPermissions()
        updateUI()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "OnboardingPermissions"
    }
}
