package com.neubofy.reality.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.neubofy.reality.R
import com.neubofy.reality.data.CustomReminder

class CustomReminderAdapter(
    private var items: MutableList<CustomReminder>,
    private val onEdit: (CustomReminder) -> Unit,
    private val onDelete: (CustomReminder) -> Unit
) : RecyclerView.Adapter<CustomReminderAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val cardRoot: MaterialCardView = view.findViewById(R.id.card_root)
        val typeIndicator: View = view.findViewById(R.id.type_indicator)
        val tvTitle: TextView = view.findViewById(R.id.tv_title)
        val tvTime: TextView = view.findViewById(R.id.tv_time)
        val tvUrl: TextView = view.findViewById(R.id.tv_url)
        val btnDelete: ImageView = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_custom_reminder, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        
        // Check if synced (virtual) reminder
        val isSynced = item.id.startsWith("synced_")
        
        // Color differentiation: Custom = Purple, Synced = Blue
        val indicatorColor = if (isSynced) R.color.blue_500 else R.color.purple_500
        holder.typeIndicator.setBackgroundColor(ContextCompat.getColor(context, indicatorColor))
        
        // Hide delete for synced reminders
        holder.btnDelete.visibility = if (isSynced) View.GONE else View.VISIBLE
        
        holder.tvTitle.text = item.title
        
        val timeFormat = String.format("%02d:%02d", item.hour, item.minute)
        
        val daysStr = if (item.repeatDays.isEmpty()) {
            "Once"
        } else if (item.repeatDays.size == 7) {
            "Daily"
        } else {
            val days = mapOf(
                java.util.Calendar.SUNDAY to "Sun",
                java.util.Calendar.MONDAY to "Mon",
                java.util.Calendar.TUESDAY to "Tue",
                java.util.Calendar.WEDNESDAY to "Wed",
                java.util.Calendar.THURSDAY to "Thu",
                java.util.Calendar.FRIDAY to "Fri",
                java.util.Calendar.SATURDAY to "Sat"
            )
            item.repeatDays.sorted().joinToString(", ") { days[it] ?: "" }
        }
        
        val retryStr = if (item.retryIntervalMins > 0) " (Retries every ${item.retryIntervalMins}m)" else ""
        val typeStr = if (isSynced) " • 📆 Synced" else " • 📅 Custom"
        
        holder.tvTime.text = "$timeFormat • $daysStr$retryStr$typeStr"
        
        if (!item.url.isNullOrEmpty()) {
            holder.tvUrl.visibility = View.VISIBLE
            holder.tvUrl.text = "Opens: ${item.url}"
        } else {
            holder.tvUrl.visibility = View.GONE
        }
        
        holder.btnDelete.setOnClickListener {
            onDelete(item)
        }
        
        holder.itemView.setOnClickListener {
            onEdit(item)
        }
    }

    override fun getItemCount() = items.size
    
    fun updateList(newList: List<CustomReminder>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }
}
