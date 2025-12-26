package com.neubofy.reality.ui.activity

import android.Manifest
import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.neubofy.reality.databinding.ActivityCalendarSettingsBinding
import com.neubofy.reality.workers.CalendarSyncWorker
import java.util.concurrent.TimeUnit

class CalendarSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalendarSettingsBinding
    private val calendarPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            checkPermissionAndLoad()
        } else {
            Toast.makeText(this, "Calendar permission required", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalendarSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnRequestPermission.setOnClickListener {
            calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
        }

        binding.btnSelectCalendars.setOnClickListener {
            selectCalendars()
        }

        binding.btnSyncNow.setOnClickListener {
            syncCalendarManually()
        }

        binding.btnViewSchedules.setOnClickListener {
            startActivity(Intent(this, ScheduleListActivity::class.java))
        }

        setupAutoSyncSwitch()
        checkPermissionAndLoad()
    }

    private fun setupAutoSyncSwitch() {
        val prefs = getSharedPreferences("calendar_sync", Context.MODE_PRIVATE)
        val isSyncEnabled = prefs.getBoolean("sync_enabled", true) // Default true
        
        binding.switchAutoSync.isChecked = isSyncEnabled
        
        // Strict Mode Check
        val strictData = com.neubofy.reality.utils.SavedPreferencesLoader(this).getStrictModeData()
        if (strictData.isEnabled) {
            binding.switchAutoSync.isEnabled = false
            binding.switchAutoSync.text = "Auto-Sync (Locked by Strict Mode)"
            binding.switchAutoSync.alpha = 0.5f
        }

        binding.switchAutoSync.setOnCheckedChangeListener { _, isChecked ->
            if (binding.switchAutoSync.isEnabled) {
                prefs.edit().putBoolean("sync_enabled", isChecked).apply()
                
                if (isChecked) {
                    val syncRequest = PeriodicWorkRequestBuilder<CalendarSyncWorker>(1, TimeUnit.HOURS).build()
                    WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                        "CalendarSync",
                        ExistingPeriodicWorkPolicy.KEEP,
                        syncRequest
                    )
                    Toast.makeText(this, "Auto-sync enabled", Toast.LENGTH_SHORT).show()
                } else {
                    WorkManager.getInstance(this).cancelUniqueWork("CalendarSync")
                    Toast.makeText(this, "Auto-sync disabled", Toast.LENGTH_SHORT).show()
                }
            } else {
                 // Should not happen if disabled, but for safety
                 binding.switchAutoSync.isChecked = !isChecked 
            }
        }
    }

    private fun checkPermissionAndLoad() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
            binding.btnRequestPermission.isEnabled = false
            binding.btnRequestPermission.text = "Permission Granted"
            
            // Show saved selection
            val prefs = getSharedPreferences("calendar_sync", Context.MODE_PRIVATE)
            val selectedIds = prefs.getStringSet("selected_calendar_ids", emptySet()) ?: emptySet()
            binding.tvSelectedCalendars.text = if (selectedIds.isEmpty()) {
                "No calendars selected - Tap 'Select Calendars' to choose"
            } else {
                "Selected: ${selectedIds.size} calendar(s)"
            }
        }
    }

    private fun selectCalendars() {
        lifecycleScope.launch(Dispatchers.IO) {
            val calendars = mutableListOf<Pair<String, String>>()
            val projection = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME
            )

            contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val name = cursor.getString(1) ?: "Unknown"
                    val account = cursor.getString(2) ?: "Local"
                    
                    // Filter out Holiday and Family calendars as requested
                    val lowerName = name.lowercase()
                    if (lowerName.contains("holiday") || lowerName.contains("family") || lowerName.contains("birthday") || lowerName.contains("week number")) {
                        continue
                    }
                    
                    calendars.add(Pair(id, "$name ($account)"))
                }
            }

            withContext(Dispatchers.Main) {
                if (calendars.isEmpty()) {
                    Toast.makeText(this@CalendarSettingsActivity, "No calendars found", Toast.LENGTH_SHORT).show()
                } else {
                    showCalendarSelectionDialog(calendars)
                }
            }
        }
    }

    private fun showCalendarSelectionDialog(calendars: List<Pair<String, String>>) {
        val prefs = getSharedPreferences("calendar_sync", Context.MODE_PRIVATE)
        val selectedIds = prefs.getStringSet("selected_calendar_ids", emptySet())?.toMutableSet() ?: mutableSetOf()

        val items = calendars.map { it.second }.toTypedArray()
        val checkedItems = calendars.map { selectedIds.contains(it.first) }.toBooleanArray()

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Calendars to Sync")
            .setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
                if (isChecked) {
                    selectedIds.add(calendars[which].first)
                } else {
                    selectedIds.remove(calendars[which].first)
                }
            }
            .setPositiveButton("OK") { _, _ ->
                prefs.edit().putStringSet("selected_calendar_ids", selectedIds).apply()
                binding.tvSelectedCalendars.text = "Selected: ${selectedIds.size} calendar(s)"
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun syncCalendarManually() {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Syncing Calendar")
            .setMessage("Please wait...")
            .setCancelable(false)
            .create()
        dialog.show()

        val workRequest = androidx.work.OneTimeWorkRequestBuilder<CalendarSyncWorker>().build()
        WorkManager.getInstance(applicationContext).enqueue(workRequest)
        
        WorkManager.getInstance(applicationContext).getWorkInfoByIdLiveData(workRequest.id)
            .observe(this) { workInfo ->
                if (workInfo != null && (workInfo.state.isFinished)) {
                    dialog.dismiss()
                    if (workInfo.state == androidx.work.WorkInfo.State.SUCCEEDED) {
                        Toast.makeText(this, "Sync Complete!", Toast.LENGTH_SHORT).show()
                    } else {
                         Toast.makeText(this, "Sync Failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    private fun saveSettings() {
        val prefs = getSharedPreferences("calendar_sync", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("sync_enabled", true).apply()

        // Schedule periodic sync every hour
        val syncRequest = PeriodicWorkRequestBuilder<CalendarSyncWorker>(1, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CalendarSync",
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )

        Toast.makeText(this, "Calendar sync enabled", Toast.LENGTH_SHORT).show()
        finish()
    }
}
