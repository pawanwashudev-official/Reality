package com.neubofy.reality.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.neubofy.reality.R
import com.neubofy.reality.data.repository.CalendarRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CalendarEventAdapter(
    private var events: List<CalendarRepository.CalendarEvent>,
    private val onEventClick: (CalendarRepository.CalendarEvent) -> Unit
) : RecyclerView.Adapter<CalendarEventAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tv_event_title)
        val tvTime: TextView = view.findViewById(R.id.tv_event_time)
        val tvDuration: TextView = view.findViewById(R.id.tv_event_duration)
        val colorStrip: View = view.findViewById(R.id.view_color_strip)
        val layoutProgress: View = view.findViewById(R.id.layout_progress)
        val progressEvent: com.google.android.material.progressindicator.LinearProgressIndicator = view.findViewById(R.id.progress_event)
        val tvProgressPercent: TextView = view.findViewById(R.id.tv_progress_percent)
        val btnStart: View = view.findViewById(R.id.btn_start_event)
        val ivCompleted: View = view.findViewById(R.id.iv_completed)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_event, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val event = events[position]
        holder.tvTitle.text = event.title
        
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val startStr = timeFormat.format(Date(event.startTime))
        val endStr = timeFormat.format(Date(event.endTime))
        holder.tvTime.text = "$startStr - $endStr"
        
        val durationMins = (event.endTime - event.startTime) / (1000 * 60)
        holder.tvDuration.text = "${durationMins}m"
        
        if (event.color != 0) {
            holder.colorStrip.setBackgroundColor(event.color)
        } else {
            holder.colorStrip.setBackgroundColor(holder.itemView.context.getColor(R.color.teal_200))
        }

        // Highlight "Upcoming" / "Running"
        val card = holder.itemView as com.google.android.material.card.MaterialCardView
        if (event.status == CalendarRepository.EventStatus.UPCOMING || event.status == CalendarRepository.EventStatus.RUNNING) {
            card.strokeWidth = 4
            card.strokeColor = holder.itemView.context.getColor(R.color.onboarding_accent) 
        } else {
            card.strokeWidth = 0
        }

        // Status Logic
        when (event.status) {
            CalendarRepository.EventStatus.COMPLETED -> {
                holder.btnStart.visibility = View.GONE
                holder.ivCompleted.visibility = View.VISIBLE
                
                holder.layoutProgress.visibility = View.VISIBLE
                holder.progressEvent.progress = 100
                holder.tvProgressPercent.text = "Completed"
            }
            CalendarRepository.EventStatus.RUNNING, CalendarRepository.EventStatus.UPCOMING, CalendarRepository.EventStatus.PENDING -> {
                holder.ivCompleted.visibility = View.GONE
                holder.btnStart.visibility = if (event.status == CalendarRepository.EventStatus.PENDING) View.VISIBLE else View.VISIBLE 

                // Always show progress bar even if 0%
                holder.layoutProgress.visibility = View.VISIBLE
                val progressInt = (event.progress * 100).toInt()
                holder.progressEvent.progress = progressInt
                holder.tvProgressPercent.text = if (progressInt > 0) "$progressInt% Completed" else "Not Started"
            }
        }

        // Disable click if completed? Maybe allow to restart? User said "no button to start", implying read-only.
        if (event.status == CalendarRepository.EventStatus.COMPLETED) {
            holder.itemView.setOnClickListener { 
                // Optional: Show "Already completed" toast
            }
        } else {
            holder.itemView.setOnClickListener { onEventClick(event) }
        }
    }

    override fun getItemCount() = events.size

    fun updateEvents(newEvents: List<CalendarRepository.CalendarEvent>) {
        events = newEvents
        notifyDataSetChanged()
    }
}
