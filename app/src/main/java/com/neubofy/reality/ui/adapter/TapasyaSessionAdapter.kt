package com.neubofy.reality.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.neubofy.reality.R
import com.neubofy.reality.data.db.TapasyaSession
import java.text.SimpleDateFormat
import java.util.*

class TapasyaSessionAdapter(
    private var sessions: List<TapasyaSession>,
    private val onDeleteClick: (TapasyaSession) -> Unit
) : RecyclerView.Adapter<TapasyaSessionAdapter.ViewHolder>() {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tv_session_name)
        val tvTime: TextView = view.findViewById(R.id.tv_session_time)
        val tvDuration: TextView = view.findViewById(R.id.tv_session_duration)
        val tvEffective: TextView = view.findViewById(R.id.tv_effective_time)
        val btnDelete: ImageView = view.findViewById(R.id.btn_delete_session)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tapasya_session, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = sessions[position]
        
        holder.tvName.text = session.name
        
        val startTime = timeFormat.format(Date(session.startTime))
        val endTime = timeFormat.format(Date(session.endTime))
        holder.tvTime.text = "$startTime - $endTime"
        
        val totalMins = (session.endTime - session.startTime) / 60000
        holder.tvDuration.text = "Total: ${formatDuration(totalMins)}"
        
        val effectiveMins = session.effectiveTimeMs / 60000
        holder.tvEffective.text = "Effective: ${formatDuration(effectiveMins)}"
        
        holder.btnDelete.setOnClickListener {
            onDeleteClick(session)
        }
    }

    override fun getItemCount() = sessions.size

    fun updateSessions(newSessions: List<TapasyaSession>) {
        sessions = newSessions
        notifyDataSetChanged()
    }
    
    private fun formatDuration(mins: Long): String {
        val h = mins / 60
        val m = mins % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }
}
