package com.neubofy.reality.ui.fragments.installation

import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity.POWER_SERVICE
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.neubofy.reality.R
import com.neubofy.reality.databinding.FragmentPermissionsBinding

class PermissionsFragment : Fragment() {

    companion object {
        const val FRAGMENT_ID = "permission_fragment"
    }

    private var _binding: FragmentPermissionsBinding? = null
    private val binding get() = _binding!!

    private var notificationGranted = false
    private var batteryGranted = false
    private var usageStatsGranted = false

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            notificationGranted = isGranted
            updateUI()
        }

    private val batteryOptimizationLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            batteryGranted = isBackgroundPermissionGiven()
            updateUI()
        }
        
    private val usageStatsLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            usageStatsGranted = isUsageStatsPermissionGiven()
            updateUI()
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentPermissionsBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("BatteryLife")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        applyPremiumTheme()

        notificationGranted = isNotificationPermissionGiven()
        batteryGranted = isBackgroundPermissionGiven()
        usageStatsGranted = isUsageStatsPermissionGiven()

        updateUI()

        binding.notifPermRoot.setOnClickListener {
            if (!notificationGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        binding.bgPermRoot.setOnClickListener {
            if (!batteryGranted) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${requireContext().packageName}")
                }
                batteryOptimizationLauncher.launch(intent)
            }
        }
        
        binding.usagePermRoot.setOnClickListener {
            if (!usageStatsGranted) {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                try {
                    usageStatsLauncher.launch(intent)
                } catch (e: Exception) {
                    // Fallback
                    startActivity(intent)
                }
            }
        }

        binding.btnNext.setOnClickListener {
            val allGranted = notificationGranted && batteryGranted && usageStatsGranted
            
            if (allGranted) {
                moveToNextScreen()
            } else {
                // Show Warning Dialog before skipping
                androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Permissions Missing")
                    .setMessage("Without these permissions, some blocking features will not work correctly. You can grant them later in Settings.\n\nProceed anyway?")
                    .setPositiveButton("Proceed") { _, _ -> moveToNextScreen() }
                    .setNegativeButton("Go Back", null)
                    .show()
            }
        }
    }
    
    private fun moveToNextScreen() {
        val sharedPreferences = requireContext().getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("first_launch_complete", true).apply()

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_holder, AccessibilityGuide())
            .addToBackStack(null)
            .commit()
    }
    
    private fun applyPremiumTheme() {
        val deepBlue = Color.parseColor("#003366")
        val solidGold = Color.parseColor("#FFD700")
        val metallicBlack = Color.parseColor("#0A0A0A")
        
        // Background
        binding.root.setBackgroundColor(metallicBlack)
        
        // Cards
        binding.notifPermRoot.setCardBackgroundColor(deepBlue)
        binding.bgPermRoot.setCardBackgroundColor(deepBlue)
        binding.usagePermRoot.setCardBackgroundColor(deepBlue)
        
        // Button
        binding.btnNext.backgroundTintList = ColorStateList.valueOf(solidGold)
        binding.btnNext.setTextColor(Color.BLACK)
    }

    private fun updateUI() {
        updatePermissionIcon(notificationGranted, binding.notifPermIcon)
        updatePermissionIcon(batteryGranted, binding.bgPermIcon)
        updatePermissionIcon(usageStatsGranted, binding.usagePermIcon)
        
        // Allow proceeding even if not all granted ("On the go" permission model)
        binding.btnNext.isEnabled = true
        binding.btnNext.text = if (notificationGranted && batteryGranted && usageStatsGranted) "Next" else "Skip for Now"
    }

    private fun updatePermissionIcon(isEnabled: Boolean, icon: android.widget.ImageView) {
        val solidGold = Color.parseColor("#FFD700")
        val errorRed = Color.parseColor("#FF5252") // Keep red for error/missing
        
        if (isEnabled) {
            icon.setImageResource(R.drawable.baseline_done_24)
            icon.setColorFilter(solidGold)
        } else {
            icon.setImageResource(R.drawable.baseline_close_24)
            icon.setColorFilter(errorRed)
        }
    }

    private fun isBackgroundPermissionGiven(): Boolean {
        val powerManager = requireContext().getSystemService(POWER_SERVICE) as PowerManager
        return powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)
    }

    private fun isNotificationPermissionGiven(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ActivityCompat.checkSelfPermission(
                requireContext(), Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }
    
    private fun isUsageStatsPermissionGiven(): Boolean {
        val appOps = requireContext().getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            requireContext().packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    
    // Resume to re-check
    override fun onResume() {
        super.onResume()
        notificationGranted = isNotificationPermissionGiven()
        batteryGranted = isBackgroundPermissionGiven()
        usageStatsGranted = isUsageStatsPermissionGiven()
        updateUI()
    }
}