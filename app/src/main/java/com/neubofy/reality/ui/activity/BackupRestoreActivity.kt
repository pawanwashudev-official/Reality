package com.neubofy.reality.ui.activity

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.neubofy.reality.databinding.ActivityBackupRestoreBinding
import com.neubofy.reality.google.GoogleAuthManager
import com.neubofy.reality.utils.BackupManager
import com.neubofy.reality.utils.ThemeManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class BackupRestoreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBackupRestoreBinding

    // Backup toggles
    private val backupToggles = mutableMapOf<BackupManager.BackupCategory, MaterialSwitch>()
    // Restore toggles (built from available backup)
    private val restoreToggles = mutableMapOf<BackupManager.BackupCategory, MaterialSwitch>()

    // Cached backup info
    private var cachedBackupInfo: BackupManager.BackupInfo? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        binding = ActivityBackupRestoreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        setupUI()
        checkSignInStatus()
        loadBackupInfo()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        // Build category toggles for Backup section
        buildCategoryToggles(
            binding.backupCategoriesContainer,
            backupToggles,
            BackupManager.BackupCategory.entries.toSet()
        )

        // Select All / Deselect All for Backup
        binding.btnSelectAll.setOnClickListener {
            backupToggles.values.forEach { it.isChecked = true }
        }
        binding.btnDeselectAll.setOnClickListener {
            backupToggles.values.forEach { it.isChecked = false }
        }

        // Select All / Deselect All for Restore
        binding.btnRestoreSelectAll.setOnClickListener {
            restoreToggles.values.forEach { it.isChecked = true }
        }
        binding.btnRestoreDeselectAll.setOnClickListener {
            restoreToggles.values.forEach { it.isChecked = false }
        }

        // Backup button
        binding.btnBackupNow.setOnClickListener {
            val selected = getSelectedBackupCategories()
            if (selected.isEmpty()) {
                Toast.makeText(this, "Select at least one category to backup", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            performBackup(selected)
        }

        // Restore button
        binding.btnRestoreNow.setOnClickListener {
            val selected = getSelectedRestoreCategories()
            if (selected.isEmpty()) {
                Toast.makeText(this, "Select at least one category to restore", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showRestoreConfirmation(selected)
        }

        // Sign-in button
        binding.btnSignIn.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val result = GoogleAuthManager.signIn(this@BackupRestoreActivity)
                    if (result != null) {
                        checkSignInStatus()
                        loadBackupInfo()
                        Toast.makeText(this@BackupRestoreActivity, "Signed in!", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@BackupRestoreActivity, "Sign-in failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    /**
     * Build MaterialSwitch toggles for categories.
     */
    private fun buildCategoryToggles(
        container: LinearLayout,
        toggleMap: MutableMap<BackupManager.BackupCategory, MaterialSwitch>,
        categories: Set<BackupManager.BackupCategory>
    ) {
        container.removeAllViews()
        toggleMap.clear()

        for (category in categories) {
            val switchView = MaterialSwitch(this).apply {
                text = "${category.icon}  ${category.displayName}"
                isChecked = true
                textSize = 14f
                setPadding(8, 8, 8, 8)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 2
                }
            }
            container.addView(switchView)
            toggleMap[category] = switchView
        }
    }

    private fun getSelectedBackupCategories(): Set<BackupManager.BackupCategory> {
        return backupToggles.filter { it.value.isChecked }.keys
    }

    private fun getSelectedRestoreCategories(): Set<BackupManager.BackupCategory> {
        return restoreToggles.filter { it.value.isChecked }.keys
    }

    private fun checkSignInStatus() {
        val signedIn = GoogleAuthManager.isSignedIn(this)
        binding.cardSignIn.visibility = if (signedIn) View.GONE else View.VISIBLE
        binding.btnBackupNow.isEnabled = signedIn
        binding.btnRestoreNow.isEnabled = signedIn

        if (signedIn) {
            val email = GoogleAuthManager.getUserEmail(this) ?: ""
            binding.tvSignedInAs.visibility = View.VISIBLE
            binding.tvSignedInAs.text = "Signed in as: $email"
        } else {
            binding.tvSignedInAs.visibility = View.GONE
        }
    }

    private fun loadBackupInfo() {
        if (!GoogleAuthManager.isSignedIn(this)) return

        binding.tvNoBackup.visibility = View.GONE
        binding.layoutBackupInfo.visibility = View.GONE
        binding.restoreCategoriesSection.visibility = View.GONE
        binding.tvLoadingBackup.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val info = BackupManager.getBackupInfo(this@BackupRestoreActivity)
                cachedBackupInfo = info

                runOnUiThread {
                    binding.tvLoadingBackup.visibility = View.GONE

                    if (info.exists) {
                        binding.layoutBackupInfo.visibility = View.VISIBLE
                        binding.tvNoBackup.visibility = View.GONE

                        val dateFormat = SimpleDateFormat("MMM dd, yyyy 'at' hh:mm a", Locale.getDefault())
                        binding.tvBackupDate.text = "📅  ${dateFormat.format(Date(info.timestamp))}"
                        binding.tvBackupSize.text = "📦  ${formatSize(info.sizeBytes)}"
                        binding.tvBackupVersion.text = "📱  App v${info.appVersion}"

                        // Build restore category toggles from available categories in backup
                        val availableCategories = info.categories.mapNotNull { name ->
                            try { BackupManager.BackupCategory.valueOf(name) } catch (_: Exception) { null }
                        }.toSet()

                        if (availableCategories.isNotEmpty()) {
                            binding.restoreCategoriesSection.visibility = View.VISIBLE
                            buildCategoryToggles(
                                binding.restoreCategoriesContainer,
                                restoreToggles,
                                availableCategories
                            )
                        }
                    } else {
                        binding.layoutBackupInfo.visibility = View.GONE
                        binding.tvNoBackup.visibility = View.VISIBLE
                        binding.restoreCategoriesSection.visibility = View.GONE
                    }
                }
            } catch (e: com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
                runOnUiThread {
                    binding.tvLoadingBackup.visibility = View.GONE
                    startActivity(e.intent)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.tvLoadingBackup.visibility = View.GONE
                    binding.tvNoBackup.visibility = View.VISIBLE
                    binding.tvNoBackup.text = "⚠️ Could not check backup status"
                }
            }
        }
    }

    private fun performBackup(categories: Set<BackupManager.BackupCategory>) {
        setOperationInProgress(true, "Preparing backup...")

        lifecycleScope.launch {
            try {
                val result = BackupManager.createBackup(
                    this@BackupRestoreActivity,
                    categories
                ) { progress, status ->
                    runOnUiThread {
                        binding.progressBar.progress = (progress * 100).toInt()
                        binding.tvProgressStatus.text = status
                    }
                }

                runOnUiThread {
                    setOperationInProgress(false)
                    if (result.success) {
                        Toast.makeText(this@BackupRestoreActivity, "✅ ${result.message}", Toast.LENGTH_LONG).show()
                        loadBackupInfo()
                    } else if (result.message.startsWith("NEED_PERMISSION:")) {
                        Toast.makeText(this@BackupRestoreActivity, "Please grant Google Drive access", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@BackupRestoreActivity, "❌ ${result.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
                runOnUiThread {
                    setOperationInProgress(false)
                    startActivity(e.intent)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setOperationInProgress(false)
                    Toast.makeText(this@BackupRestoreActivity, "❌ Backup failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showRestoreConfirmation(categories: Set<BackupManager.BackupCategory>) {
        val catNames = categories.joinToString("\n") { "  • ${it.icon} ${it.displayName}" }
        MaterialAlertDialogBuilder(this)
            .setTitle("⚠️ Restore from Backup?")
            .setMessage("The following categories will be restored, overwriting current data:\n\n$catNames\n\nThis cannot be undone. Continue?")
            .setPositiveButton("Restore") { _, _ -> performRestore(categories) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun performRestore(categories: Set<BackupManager.BackupCategory>) {
        setOperationInProgress(true, "Downloading backup...")

        lifecycleScope.launch {
            try {
                val result = BackupManager.restoreBackup(
                    this@BackupRestoreActivity,
                    categories
                ) { progress, status ->
                    runOnUiThread {
                        binding.progressBar.progress = (progress * 100).toInt()
                        binding.tvProgressStatus.text = status
                    }
                }

                runOnUiThread {
                    setOperationInProgress(false)
                    if (result.success) {
                        MaterialAlertDialogBuilder(this@BackupRestoreActivity)
                            .setTitle("✅ Restore Complete")
                            .setMessage("${result.message}\n\nPlease restart the app for all changes to take effect.")
                            .setPositiveButton("OK") { _, _ -> finish() }
                            .setCancelable(false)
                            .show()
                    } else if (result.message.startsWith("NEED_PERMISSION:")) {
                        Toast.makeText(this@BackupRestoreActivity, "Please grant Google Drive access", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this@BackupRestoreActivity, "❌ ${result.message}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException) {
                runOnUiThread {
                    setOperationInProgress(false)
                    startActivity(e.intent)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    setOperationInProgress(false)
                    Toast.makeText(this@BackupRestoreActivity, "❌ Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setOperationInProgress(inProgress: Boolean, statusText: String = "") {
        binding.cardProgress.visibility = if (inProgress) View.VISIBLE else View.GONE
        binding.btnBackupNow.isEnabled = !inProgress
        binding.btnRestoreNow.isEnabled = !inProgress
        binding.progressBar.progress = 0
        if (statusText.isNotEmpty()) {
            binding.tvProgressStatus.text = statusText
        }
    }

    private fun formatSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
