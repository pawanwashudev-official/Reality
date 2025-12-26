package com.neubofy.reality.ui.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.neubofy.reality.Constants
import com.neubofy.reality.R
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.databinding.ActivityAddTimedActionActivityBinding
import com.neubofy.reality.databinding.CheatHourItemBinding
import com.neubofy.reality.databinding.DialogAddTimedActionBinding
import com.neubofy.reality.services.AppBlockerService
import com.neubofy.reality.utils.SavedPreferencesLoader
import com.neubofy.reality.utils.TimeTools
import nl.joery.timerangepicker.TimeRangePicker
import java.util.Calendar

class TimedActionActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAddTimedActionActivityBinding
    private val savedPreferencesLoader = SavedPreferencesLoader(this)
    private var unifiedList: List<UnifiedScheduleItem> = emptyList()
    private val adapter = UnifiedScheduleAdapter()
    
    // For Manual Adding
    private lateinit var dialogBinding: DialogAddTimedActionBinding
    private var manualList: MutableList<Constants.AutoTimedActionItem> = mutableListOf()

    sealed class UnifiedScheduleItem {
        data class Manual(val item: Constants.AutoTimedActionItem) : UnifiedScheduleItem()
        data class CalendarItem(val event: com.neubofy.reality.data.db.CalendarEvent) : UnifiedScheduleItem()
    }

    private lateinit var selectUnblockedAppsLauncher: androidx.activity.result.ActivityResultLauncher<Intent>
    private var tempSelectedApps: ArrayList<String> = arrayListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityAddTimedActionActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        binding.topAppBar.setNavigationOnClickListener { finish() }
        
        selectUnblockedAppsLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val apps = result.data?.getStringArrayListExtra("SELECTED_APPS")
                if (apps != null) {
                    tempSelectedApps = apps
                    if (::dialogBinding.isInitialized) {
                        dialogBinding.btnSelectUnblockedApps.text = "${apps.size} apps selected"
                    }
                }
            }
        }
        
        // Load initial manual list for editing
        manualList = savedPreferencesLoader.loadAutoFocusHoursList()

        binding.recyclerView2.layoutManager = LinearLayoutManager(this)
        binding.recyclerView2.adapter = adapter

        binding.button.setOnClickListener {
            showAddScheduleDialog()
        }
        
        loadData()
    }

    private fun loadData() {
        lifecycleScope.launch(Dispatchers.IO) {
            // Only load calendar events - custom schedules removed
            val startOfDay = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val calendarEvents = try {
                AppDatabase.getDatabase(applicationContext).calendarEventDao().getUpcomingEvents(startOfDay)
            } catch (e: Exception) { emptyList() }
            
            val temp = mutableListOf<UnifiedScheduleItem>()
            calendarEvents.forEach { temp.add(UnifiedScheduleItem.CalendarItem(it)) }
            
            // Add Manual Items
            manualList.forEach { temp.add(UnifiedScheduleItem.Manual(it)) }
            
            // Sort by start time
            temp.sortBy { 
                when(it) {
                    is UnifiedScheduleItem.CalendarItem -> it.event.startTime
                    is UnifiedScheduleItem.Manual -> getNextTimeInMillis(it.item.startTimeInMins)
                }
            }
              
             withContext(Dispatchers.Main) {
                 unifiedList = temp
                 adapter.setData(unifiedList)
                 checkEmptyState()
             }
        }
    }
    
    private fun getNextTimeInMillis(minutesFromMidnight: Int): Long {
            val cal = Calendar.getInstance()
            val currentMins = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
            
            cal.set(Calendar.HOUR_OF_DAY, minutesFromMidnight / 60)
            cal.set(Calendar.MINUTE, minutesFromMidnight % 60)
            cal.set(Calendar.SECOND, 0)
            
            if (currentMins >= minutesFromMidnight) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
            return cal.timeInMillis
    }
    
    private fun checkEmptyState() {
        if (unifiedList.isEmpty()) {
            binding.layoutEmptyState.visibility = View.VISIBLE
            binding.recyclerView2.visibility = View.GONE
        } else {
            binding.layoutEmptyState.visibility = View.GONE
            binding.recyclerView2.visibility = View.VISIBLE
        }
    }

    @SuppressLint("SetTextI18n")
    private fun showAddScheduleDialog() {
        dialogBinding = DialogAddTimedActionBinding.inflate(layoutInflater)
        dialogBinding.timedTitle.text = "Add Schedule"
        
        tempSelectedApps = arrayListOf() // Reset
        dialogBinding.btnSelectUnblockedApps.visibility = View.VISIBLE
        dialogBinding.btnSelectUnblockedApps.text = "Select Apps (Optional)"
        dialogBinding.btnSelectUnblockedApps.setOnClickListener {
             val intent = Intent(this, com.neubofy.reality.ui.activity.SelectAppsActivity::class.java)
             intent.putStringArrayListExtra("PRE_SELECTED_APPS", tempSelectedApps)
             selectUnblockedAppsLauncher.launch(intent)
        }

        var startTimeMins = 390
        var endTimeMins = 1320
        dialogBinding.picker.startTimeMinutes = startTimeMins
        dialogBinding.picker.endTimeMinutes = endTimeMins
        
        dialogBinding.picker.setOnTimeChangeListener(object : TimeRangePicker.OnTimeChangeListener {
            override fun onStartTimeChange(startTime: TimeRangePicker.Time) {
                dialogBinding.fromTime.text = startTime.toString()
                startTimeMins = dialogBinding.picker.startTimeMinutes
            }
            override fun onEndTimeChange(endTime: TimeRangePicker.Time) {
                dialogBinding.endTime.text = endTime.toString()
                endTimeMins = dialogBinding.picker.endTimeMinutes
            }
            override fun onDurationChange(duration: TimeRangePicker.TimeDuration) {}
        })

        MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .setPositiveButton("Add") { dialog, _ ->
                val title = dialogBinding.cheatHourTitle.text.toString()
                if (title.isEmpty()) {
                    Toast.makeText(this, "Enter a title", Toast.LENGTH_SHORT).show()
                } else {
                    val days = ArrayList<Int>()
                    val chipGroup = dialogBinding.root.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chip_days)
                    if (chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_sun).isChecked) days.add(1)
                    if (chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_mon).isChecked) days.add(2)
                    if (chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_tue).isChecked) days.add(3)
                    if (chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_wed).isChecked) days.add(4)
                    if (chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_thu).isChecked) days.add(5)
                    if (chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_fri).isChecked) days.add(6)
                    if (chipGroup.findViewById<com.google.android.material.chip.Chip>(R.id.chip_sat).isChecked) days.add(7)
                    
                    if (days.isEmpty()) {
                         Toast.makeText(this, "Select days", Toast.LENGTH_SHORT).show()
                         return@setPositiveButton
                    }
                
                    val newItem = Constants.AutoTimedActionItem(title, startTimeMins, endTimeMins, tempSelectedApps, repeatDays = days)
                    manualList.add(newItem)
                    savedPreferencesLoader.saveAutoFocusHoursList(manualList)
                    sendBroadcast(Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE))
                    loadData()
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun deleteManualItem(item: Constants.AutoTimedActionItem) {
        if (!com.neubofy.reality.utils.StrictLockUtils.isModificationAllowed(this)) {
              Toast.makeText(this, "Locked by Strict Mode", Toast.LENGTH_SHORT).show()
              return
        }
        manualList.remove(item)
        savedPreferencesLoader.saveAutoFocusHoursList(manualList)
        sendBroadcast(Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE))
        loadData()
    }

    inner class UnifiedScheduleAdapter : RecyclerView.Adapter<UnifiedScheduleAdapter.ViewHolder>() {
        private var items = listOf<UnifiedScheduleItem>()
        
        @SuppressLint("NotifyDataSetChanged")
        fun setData(newItems: List<UnifiedScheduleItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        inner class ViewHolder(val binding: CheatHourItemBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(CheatHourItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            when(item) {
                is UnifiedScheduleItem.Manual -> {
                    holder.binding.cheatHourTitle.text = item.item.title
                    val start = TimeTools.convertMinutesTo24Hour(item.item.startTimeInMins)
                    val end = TimeTools.convertMinutesTo24Hour(item.item.endTimeInMins)
                    holder.binding.cheatTimings.text = String.format("%02d:%02d to %02d:%02d", start.first, start.second, end.first, end.second)
                    holder.binding.selectedApps.text = "Repeat: ${getDaysString(item.item.repeatDays)}"
                    holder.binding.removeCheatHour.visibility = View.VISIBLE
                    holder.binding.removeCheatHour.setOnClickListener {
                        deleteManualItem(item.item)
                    }
                }
                is UnifiedScheduleItem.CalendarItem -> {
                    holder.binding.cheatHourTitle.text = item.event.title
                    val dateFormat = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                    val endFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                    holder.binding.cheatTimings.text = "${dateFormat.format(java.util.Date(item.event.startTime))} - ${endFormat.format(java.util.Date(item.event.endTime))}"
                    holder.binding.selectedApps.text = "Synced from Calendar"
                    holder.binding.removeCheatHour.visibility = View.GONE
                }
            }
        }
        override fun getItemCount() = items.size
    }
    
    private fun getDaysString(days: List<Int>): String {
        val allDays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
        return days.sorted().joinToString(", ") { allDays[it - 1] }
    }
}