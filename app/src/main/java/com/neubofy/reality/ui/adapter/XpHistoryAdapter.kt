package com.neubofy.reality.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.neubofy.reality.databinding.ItemXpHistoryBinding
import com.neubofy.reality.utils.XPManager
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class XpHistoryAdapter(private var items: List<XPManager.XPBreakdown>) : RecyclerView.Adapter<XpHistoryAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemXpHistoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemXpHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        
        // Format Date
        try {
            val date = LocalDate.parse(item.date)
            val formatter = DateTimeFormatter.ofPattern("EEE, MMM dd")
            holder.binding.tvDate.text = date.format(formatter)
        } catch (e: Exception) {
             holder.binding.tvDate.text = item.date
        }

        // Total
        holder.binding.tvTotalXpRow.text = "${item.totalDailyXP} XP"
        holder.binding.tvTotalXpRow.setTextColor(
            if (item.totalDailyXP > 0) context.getColor(com.neubofy.reality.R.color.accent_focus) 
            else context.getColor(android.R.color.darker_gray)
        )

        // Values
        holder.binding.tvTapasyaVal.text = "🧘 Tapasya: +${item.tapasyaXP}"
        holder.binding.tvTaskVal.text = "✅ Task: ${formatVal(item.taskXP)}"
        holder.binding.tvSessionVal.text = "📅 Session: +${item.sessionXP}"
        holder.binding.tvDiaryVal.text = "📖 Diary: +${item.reflectionXP}"
        // Unified Distraction XP
        // Using distractionXP field (renamed from screenTimeXP)
        val distXP = item.distractionXP 
        val distStr = if (distXP > 0) "+$distXP" else "$distXP"
        holder.binding.tvBonusVal.text = "📱 Distraction: $distStr"
        
        // Dynamic Color for History
        val colorRes = if (distXP >= 0) com.neubofy.reality.R.color.accent_focus else com.neubofy.reality.R.color.error_color
        holder.binding.tvBonusVal.setTextColor(context.getColor(colorRes))
        
        // Hide Penalty Row completely
        holder.binding.tvPenaltyVal.text = "" 
        holder.binding.tvPenaltyVal.alpha = 0f
        
        // Hide penalty if 0
        holder.binding.tvPenaltyVal.alpha = if (item.penaltyXP > 0) 1f else 0.3f
    }
    
    private fun formatVal(valInt: Int): String {
        return if (valInt >= 0) "+$valInt" else "$valInt"
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<XPManager.XPBreakdown>) {
        items = newItems
        notifyDataSetChanged()
    }
}
