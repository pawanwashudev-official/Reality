package com.neubofy.reality.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.neubofy.reality.R

class SavedModelsAdapter(
    private val models: MutableList<String>,
    private var selectedModel: String,
    private val onSelect: (String) -> Unit,
    private val onDelete: (String) -> Unit
) : RecyclerView.Adapter<SavedModelsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val rbDefault: android.widget.RadioButton = view.findViewById(R.id.rb_default)
        val tvName: TextView = view.findViewById(R.id.tv_model_name)
        val btnDelete: ImageView = view.findViewById(R.id.btn_delete_model)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_saved_model, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val model = models[position]
        holder.tvName.text = model
        
        holder.rbDefault.isChecked = (model == selectedModel || model.endsWith(": $selectedModel"))
        
        holder.itemView.setOnClickListener {
            onSelect(model)
        }
        
        holder.btnDelete.setOnClickListener {
            onDelete(model)
        }
    }

    override fun getItemCount() = models.size

    fun updateData(newModels: List<String>, currentSelected: String) {
        models.clear()
        models.addAll(newModels)
        selectedModel = currentSelected
        notifyDataSetChanged()
    }
    
    fun removeModel(model: String) {
        val index = models.indexOf(model)
        if (index >= 0) {
            models.removeAt(index)
            notifyItemRemoved(index)
        }
    }
    
    fun setSelected(model: String) {
        selectedModel = model
        notifyDataSetChanged()
    }
}
