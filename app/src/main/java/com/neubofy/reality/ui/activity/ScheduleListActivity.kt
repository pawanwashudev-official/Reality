package com.neubofy.reality.ui.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.neubofy.reality.R
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.data.db.CalendarEvent
import com.neubofy.reality.databinding.ActivityScheduleListBinding
import com.neubofy.reality.databinding.ItemCalendarEventBinding
import java.text.SimpleDateFormat
import java.util.*

class ScheduleListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScheduleListBinding
    private val events = mutableListOf<CalendarEvent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScheduleListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.recyclerSchedules.layoutManager = LinearLayoutManager(this)
        binding.recyclerSchedules.adapter = ScheduleAdapter(events)

        loadSchedules()
    }

    private var isStrictLocked = false

    private fun loadSchedules() {
        lifecycleScope.launch(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(applicationContext)
            
            // Check Strict Mode
            val loader = com.neubofy.reality.utils.SavedPreferencesLoader(applicationContext)
            val strict = loader.getStrictModeData()
            isStrictLocked = strict.isEnabled && !com.neubofy.reality.utils.StrictLockUtils.isMaintenanceWindow()
            
            // Get Future Events Only
            val now = System.currentTimeMillis()
            val upcomingEvents = db.calendarEventDao().getUpcomingEvents(now)
            
            withContext(Dispatchers.Main) {
                events.clear()
                events.addAll(upcomingEvents)
                binding.recyclerSchedules.adapter?.notifyDataSetChanged()
                
                if (events.isEmpty()) {
                    binding.tvEmpty.visibility = View.VISIBLE
                    binding.recyclerSchedules.visibility = View.GONE
                } else {
                    binding.tvEmpty.visibility = View.GONE
                    binding.recyclerSchedules.visibility = View.VISIBLE
                }
            }
        }
    }

    inner class ScheduleAdapter(private val items: List<CalendarEvent>) : RecyclerView.Adapter<ScheduleAdapter.ViewHolder>() {

        inner class ViewHolder(private val binding: ItemCalendarEventBinding) : RecyclerView.ViewHolder(binding.root) {
            fun bind(event: CalendarEvent) {
                binding.eventTitle.text = event.title
                
                val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
                
                binding.eventDate.text = dateFormat.format(Date(event.startTime))
                binding.eventTime.text = "${timeFormat.format(Date(event.startTime))} - ${timeFormat.format(Date(event.endTime))}"
                
                binding.switchEventEnabled.setOnCheckedChangeListener(null)
                binding.switchEventEnabled.isChecked = event.isEnabled
                
                if (isStrictLocked) {
                    binding.switchEventEnabled.isEnabled = false
                    binding.switchEventEnabled.alpha = 0.5f // Visual cue
                } else {
                    binding.switchEventEnabled.isEnabled = true
                    binding.switchEventEnabled.alpha = 1.0f
                    binding.switchEventEnabled.setOnCheckedChangeListener { _, isChecked ->
                        val updatedEvent = event.copy(isEnabled = isChecked)
                        lifecycleScope.launch(Dispatchers.IO) {
                            val db = AppDatabase.getDatabase(applicationContext)
                            db.calendarEventDao().updateEvent(updatedEvent)
                            
                            val intent = android.content.Intent("com.neubofy.reality.refresh.focus_mode")
                            applicationContext.sendBroadcast(intent)
                        }
                    }
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            return ViewHolder(ItemCalendarEventBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
        override fun getItemCount() = items.size
    }
}
