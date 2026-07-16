package com.neubofy.reality.ui.activity

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.neubofy.reality.R
import com.neubofy.reality.receivers.AdminLockReceiver
import com.neubofy.reality.services.AppBlockerService
import com.neubofy.reality.ui.base.BaseActivity
import com.neubofy.reality.utils.FeatureManager
import com.neubofy.reality.utils.ThemeManager
import com.neubofy.reality.utils.UsageUtils

class PermissionManagerActivity : BaseActivity() {

    private lateinit var container: LinearLayout
    private lateinit var featureManager: FeatureManager

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        updateUI()
    }
    private val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        updateUI()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_permission_manager)
        ThemeManager.applyTheme(this)

        container = findViewById(R.id.permissions_container)
        featureManager = FeatureManager(this)

        // Setup Header
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        if (toolbar != null) {
            setSupportActionBar(toolbar)
            supportActionBar?.title = "Permission Manager"
            toolbar.setNavigationOnClickListener { finish() }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun updateUI() {
        container.removeAllViews()

        // Introductory Text
        val intro = TextView(this).apply {
            text = "Reality requests permissions dynamically based on the features you have enabled."
            setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            textSize = 14f
            setPadding(0, 0, 0, 48) // bottom margin equivalent
        }
        container.addView(intro)

        // 1. Accessibility (Core)
        addPermissionCard(
            title = "Accessibility Service",
            desc = "Required for app blocking functionality.",
            whyText = "We need this to detect when you open an app so we can block it if it exceeds your limits or if a blocking mode is active.",
            iconRes = R.drawable.baseline_accessibility_24,
            isGranted = isAccessibilityServiceEnabled(AppBlockerService::class.java),
            onClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                settingsLauncher.launch(intent)
            }
        )

        // 2. Usage Access (Core)
        addPermissionCard(
            title = "Usage Access",
            desc = "Required to enforce app limits and track screen time.",
            whyText = "This allows us to track how much time you spend on each app so your usage limits are accurately enforced.",
            iconRes = R.drawable.baseline_timer_24,
            isGranted = UsageUtils.hasUsageStatsPermission(this),
            onClick = {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                settingsLauncher.launch(intent)
            }
        )

        // 3. Display Over Apps (Core)
        addPermissionCard(
            title = "Display Over Other Apps",
            desc = "Required to show the block screen over restricted apps.",
            whyText = "When an app is blocked, we use this permission to display our custom block screen over it, preventing you from accessing it.",
            iconRes = R.drawable.baseline_layers_24,
            isGranted = Settings.canDrawOverlays(this),
            onClick = {
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                settingsLauncher.launch(intent)
            }
        )

        // 4. Battery Optimization (Core)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        addPermissionCard(
            title = "Ignore Battery Optimization",
            desc = "Required to keep the blocking service running reliably in the background.",
            whyText = "Android often kills background apps to save battery. This permission ensures our blocking features remain active at all times.",
            iconRes = R.drawable.baseline_bolt_24,
            isGranted = pm.isIgnoringBatteryOptimizations(packageName),
            onClick = {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                intent.data = Uri.parse("package:$packageName")
                try {
                    settingsLauncher.launch(intent)
                } catch (e: Exception) {
                    settingsLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }
        )

        // 5. Notifications (Core)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            addPermissionCard(
                title = "Notifications",
                desc = "Required for reminders, alarms, and blocking status.",
                whyText = "We need this to remind you of your limits and ensure background services run correctly without the system pausing them.",
                iconRes = R.drawable.baseline_notifications_24,
                isGranted = granted,
                onClick = {
                    if (!granted) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            )
        }

        // 6. Device Admin (Anti-Uninstall - Optional Core)
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, AdminLockReceiver::class.java)
        addPermissionCard(
            title = "Device Admin (Anti-Uninstall)",
            desc = "Prevents uninstalling Reality when Strict Mode is active.",
            whyText = "When Strict Mode is active, you could bypass it simply by uninstalling the app. Device Admin protection prevents you from uninstalling the app to ensure your limits are respected.",
            iconRes = R.drawable.baseline_security_24,
            isGranted = dpm.isAdminActive(adminComponent),
            onClick = {
                if (!dpm.isAdminActive(adminComponent)) {
                    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                    intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Reality Anti-Uninstall Protection")
                    settingsLauncher.launch(intent)
                }
            }
        )

        // Conditional Permissions based on Feature Manager

        // 7. AI Voice Chat (If Enabled)
        if (featureManager.isAiEnabled()) {
            val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            addPermissionCard(
                title = "Microphone",
                desc = "Required for AI voice interactions.",
                whyText = "The RECORD_AUDIO permission is needed specifically for the voice interaction button on the AI page. We do not listen to you outside of those interactions.",
                iconRes = R.drawable.baseline_mic_24,
                isGranted = granted,
                onClick = {
                    if (!granted) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            )
        }

        // 8. Health Connect (If Enabled)
        if (featureManager.isHealthConnectEnabled()) {
            addPermissionCard(
                title = "Health Connect",
                desc = "Required to sync sleep and step data.",
                whyText = "By linking with Health Connect, we can integrate your sleep duration and step count directly into Reality.",
                iconRes = R.drawable.baseline_favorite_24,
                isGranted = ContextCompat.checkSelfPermission(this, "android.permission.health.READ_SLEEP") == PackageManager.PERMISSION_GRANTED,
                onClick = {
                    // This is a simplification; full health connect request uses Health Connect API.
                    // We just route to Health Dashboard where the full request logic exists.
                    startActivity(Intent(this, HealthDashboardActivity::class.java))
                }
            )
        }
        
        // 8.5 Physical Activity / Google Sleep API (If Enabled)
        val prefs = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        if (prefs.getBoolean("google_sleep_enabled", false)) {
            val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
            } else {
                true // Granted implicitly on older versions
            }
            addPermissionCard(
                title = "Physical Activity",
                desc = "Required to accurately detect your sleep patterns.",
                whyText = "When Google Sleep Detection is enabled, we use this permission to access the Google Play Services Sleep API, which detects sleep extremely accurately using motion and light sensors.",
                iconRes = R.drawable.baseline_accessibility_24,
                isGranted = isGranted,
                onClick = {
                    if (!isGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        permissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                    }
                }
            )
        }

        // 9. Exact Alarm (If Reminders/Alarms Enabled)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && featureManager.isReminderEnabled()) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            addPermissionCard(
                title = "Exact Alarms",
                desc = "Required to fire reminders and alarms on time.",
                whyText = "Reminders and bedtime alarms need this permission to trigger at the exact time you schedule them, rather than being delayed by the OS.",
                iconRes = R.drawable.baseline_access_time_24,
                isGranted = alarmManager.canScheduleExactAlarms(),
                onClick = {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    settingsLauncher.launch(intent)
                }
            )
        }



        // 11. Camera
        val cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        addPermissionCard(
            title = "Camera",
            desc = "Required to scan QR codes for Tapasya import.",
            whyText = "When importing Tapasya configurations, you can do so by scanning a QR code with the Camera. We do not use the camera for any other purpose.",
            iconRes = R.drawable.baseline_qr_code_24,
            isGranted = cameraGranted,
            onClick = {
                if (!cameraGranted) permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        )
    }

    private fun addPermissionCard(title: String, desc: String, whyText: String, iconRes: Int, isGranted: Boolean, onClick: () -> Unit) {
        val view = LayoutInflater.from(this).inflate(R.layout.item_permission_card, container, false)
        val rootCard = view.findViewById<MaterialCardView>(R.id.card_root)
        val tvTitle = view.findViewById<TextView>(R.id.tv_title)
        val tvDesc = view.findViewById<TextView>(R.id.tv_desc)
        val ivIcon = view.findViewById<ImageView>(R.id.iv_icon)
        val ivStatus = view.findViewById<ImageView>(R.id.iv_status)
        val btnWhy = view.findViewById<android.widget.ImageButton>(R.id.btn_why)

        tvTitle.text = title
        tvDesc.text = desc
        ivIcon.setImageResource(iconRes)

        btnWhy.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Why is this needed?")
                .setMessage(whyText)
                .setPositiveButton("Got it", null)
                .show()
        }

        if (isGranted) {
            ivStatus.setImageResource(R.drawable.baseline_check_circle_24)
            ivStatus.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_light))
            rootCard.setOnClickListener(null) // Do nothing if already granted
        } else {
            ivStatus.setImageResource(R.drawable.baseline_error_24)
            ivStatus.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_red_light))
            rootCard.setOnClickListener { onClick() }
        }

        container.addView(view)
    }

    private fun isAccessibilityServiceEnabled(service: Class<out AccessibilityService>): Boolean {
        val expectedComponentName = ComponentName(this, service)
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(expectedComponentName.flattenToString()) == true
    }
}
