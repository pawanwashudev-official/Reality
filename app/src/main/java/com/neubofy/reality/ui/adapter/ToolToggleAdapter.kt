package com.neubofy.reality.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.neubofy.reality.databinding.ItemToolToggleBinding
import com.neubofy.reality.utils.ToolRegistry

class ToolToggleAdapter(
    private val tools: List<ToolRegistry.ToolMeta>,
    private val onToggle: (String, Boolean) -> Unit
) : RecyclerView.Adapter<ToolToggleAdapter.ViewHolder>() {

    private val enabledStates = mutableMapOf<String, Boolean>()

    fun setEnabledStates(states: Map<String, Boolean>) {
        enabledStates.clear()
        enabledStates.putAll(states)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemToolToggleBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val tool = tools[position]
        holder.bind(tool, enabledStates[tool.id] ?: tool.defaultEnabled)
    }

    override fun getItemCount() = tools.size

    inner class ViewHolder(private val binding: ItemToolToggleBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(tool: ToolRegistry.ToolMeta, isEnabled: Boolean) {
            binding.tvToolName.text = tool.name
            binding.tvToolDesc.text = tool.shortDesc
            
            // Prevent callback during initial bind
            binding.switchTool.setOnCheckedChangeListener(null)
            binding.switchTool.isChecked = isEnabled
            
            binding.switchTool.setOnCheckedChangeListener { _, checked ->
                enabledStates[tool.id] = checked
                onToggle(tool.id, checked)
            }
        }
    }
}
