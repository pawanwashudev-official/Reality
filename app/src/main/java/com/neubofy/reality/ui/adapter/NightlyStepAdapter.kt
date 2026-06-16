package com.neubofy.reality.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.neubofy.reality.R
import com.neubofy.reality.databinding.ItemStepCardBinding
import com.neubofy.reality.data.nightly.StepProgress

data class StepItem(
    val stepId: Int,
    val title: String,
    val icon: Int,
    var status: Int = StepProgress.STATUS_PENDING,
    var detail: String = "Pending...",
    var linkUrl: String? = null,
    var isEnabled: Boolean = true,
    var checkboxLabel: String? = null,
    var isChecked: Boolean = false
)

class NightlyStepAdapter(
    private val steps: List<StepItem>,
    private val onStartClick: (StepItem) -> Unit,
    private val onDoubleTap: (StepItem) -> Unit,
    private val onLongPress: (StepItem) -> Unit,
    private val onCheckboxChanged: (StepItem, Boolean) -> Unit
) : RecyclerView.Adapter<NightlyStepAdapter.StepViewHolder>() {

    inner class StepViewHolder(val binding: ItemStepCardBinding) : RecyclerView.ViewHolder(binding.root) {
        private var lastClickTime = 0L
        
        fun bind(item: StepItem) {
            binding.tvStepTitle.text = item.title
            binding.tvStepDetail.text = item.detail
            
            // Status Icon
            val (iconRes, tintColor) = when (item.status) {
                StepProgress.STATUS_RUNNING -> 
                    R.drawable.baseline_sync_24 to R.color.blue_500
                StepProgress.STATUS_COMPLETED -> 
                    R.drawable.baseline_check_circle_24 to R.color.green_500
                StepProgress.STATUS_ERROR -> 
                    R.drawable.baseline_error_24 to R.color.status_error
                StepProgress.STATUS_SKIPPED ->
                    R.drawable.baseline_done_24 to android.R.color.darker_gray
                else -> 
                    R.drawable.baseline_radio_button_unchecked_24 to android.R.color.darker_gray
            }
            binding.iconStatus.setImageResource(iconRes)
            binding.iconStatus.setColorFilter(binding.root.context.getColor(tintColor))
            
            // Action Button Text and State
            // Completed = button disabled (grayed out), user can use long-press Reset if needed
            // Pending/Error = button enabled to run/retry
            when (item.status) {
                StepProgress.STATUS_COMPLETED -> {
                    binding.btnAction.text = "Done"
                    binding.btnAction.isEnabled = false
                    binding.btnAction.alpha = 0.5f
                }
                StepProgress.STATUS_RUNNING -> {
                    binding.btnAction.text = "..."
                    binding.btnAction.isEnabled = false
                    binding.btnAction.alpha = 0.7f
                }
                StepProgress.STATUS_ERROR -> {
                    binding.btnAction.text = "Retry"
                    binding.btnAction.isEnabled = item.isEnabled
                    binding.btnAction.alpha = 1.0f
                }
                else -> {
                    binding.btnAction.text = "Start"
                    binding.btnAction.isEnabled = item.isEnabled
                    binding.btnAction.alpha = 1.0f
                }
            }
            
            // Open Link Button
            binding.btnOpenLink.visibility = if (item.linkUrl != null) View.VISIBLE else View.GONE
            binding.btnOpenLink.visibility = if (item.linkUrl != null) View.VISIBLE else View.GONE
            binding.btnOpenLink.setOnClickListener {
                item.linkUrl?.let { url ->
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    binding.root.context.startActivity(intent)
                }
            }
            
            // Checkbox Logic
            if (item.checkboxLabel != null) {
                binding.cbStepVerification.visibility = View.VISIBLE
                binding.cbStepVerification.text = item.checkboxLabel
                binding.cbStepVerification.setOnCheckedChangeListener(null) // Prevent recursive trigger
                binding.cbStepVerification.isChecked = item.isChecked
                binding.cbStepVerification.setOnCheckedChangeListener { _, isChecked ->
                    item.isChecked = isChecked
                    onCheckboxChanged(item, isChecked)
                }
            } else {
                binding.cbStepVerification.visibility = View.GONE
            }
            
            // Start/Redo Click
            binding.btnAction.setOnClickListener {
                onStartClick(item)
            }
            
            // Double Tap Detection
            binding.cardStep.setOnClickListener {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime < 300) {
                    onDoubleTap(item)
                }
                lastClickTime = currentTime
            }
            
            // Long Press
            binding.cardStep.setOnLongClickListener {
                onLongPress(item)
                true
            }
            
            // Card Alpha for disabled state
            binding.cardStep.alpha = if (item.isEnabled) 1.0f else 0.5f
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StepViewHolder {
        val binding = ItemStepCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return StepViewHolder(binding)
    }

    override fun onBindViewHolder(holder: StepViewHolder, position: Int) {
        holder.bind(steps[position])
    }

    override fun getItemCount() = steps.size
    
    fun updateStep(stepId: Int, status: Int, detail: String, linkUrl: String? = null) {
        val index = steps.indexOfFirst { it.stepId == stepId }
        if (index != -1) {
            steps[index].status = status
            steps[index].detail = detail
            if (linkUrl != null) steps[index].linkUrl = linkUrl
            notifyItemChanged(index)
        }
    }
    
    fun setStepEnabled(stepId: Int, enabled: Boolean) {
        val index = steps.indexOfFirst { it.stepId == stepId }
        if (index != -1) {
            steps[index].isEnabled = enabled
            notifyItemChanged(index)
        }
    }
    
    fun setStepChecked(stepId: Int, checked: Boolean) {
        val index = steps.indexOfFirst { it.stepId == stepId }
        if (index != -1) {
            steps[index].isChecked = checked
            notifyItemChanged(index)
        }
    }
}
