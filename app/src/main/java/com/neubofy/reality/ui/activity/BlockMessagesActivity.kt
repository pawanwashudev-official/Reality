package com.neubofy.reality.ui.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.neubofy.reality.Constants
import com.neubofy.reality.databinding.ActivityBlockMessagesBinding
import com.neubofy.reality.databinding.DialogAddMessageBinding
import com.neubofy.reality.databinding.ItemBlockMessageBinding
import com.neubofy.reality.utils.SavedPreferencesLoader

class BlockMessagesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBlockMessagesBinding
    private lateinit var prefs: SavedPreferencesLoader
    private lateinit var adapter: MessagesAdapter
    private var messages = mutableListOf<Constants.BlockMessage>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBlockMessagesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Custom Messages"

        prefs = SavedPreferencesLoader(this)
        
        setupRecyclerView()
        loadMessages()
        
        binding.fabAdd.setOnClickListener {
            showAddDialog()
        }
    }

    private fun setupRecyclerView() {
        adapter = MessagesAdapter()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun loadMessages() {
        messages = prefs.getBlockMessages()
        adapter.notifyDataSetChanged()
        
        if (messages.isEmpty()) {
            binding.tvEmpty.visibility = View.VISIBLE
        } else {
            binding.tvEmpty.visibility = View.GONE
        }
    }

    private fun showAddDialog() {
        val dialogBinding = DialogAddMessageBinding.inflate(layoutInflater)
        
        MaterialAlertDialogBuilder(this)
            .setTitle("Add Message")
            .setView(dialogBinding.root)
            .setPositiveButton("Add") { _, _ ->
                val text = dialogBinding.etMessage.text.toString().trim()
                if (text.isNotEmpty()) {
                    val tags = mutableListOf<String>()
                    if (dialogBinding.chipAll.isChecked) tags.add("ALL")
                    else {
                        if (dialogBinding.chipFocus.isChecked) tags.add("FOCUS")
                        if (dialogBinding.chipBedtime.isChecked) tags.add("BEDTIME")
                        if (dialogBinding.chipLimit.isChecked) tags.add("LIMIT")
                    }
                    
                    if (tags.isEmpty()) tags.add("ALL") // Default if nothing selected
                    
                    val newMessage = Constants.BlockMessage(message = text, tags = tags)
                    messages.add(newMessage)
                    prefs.saveBlockMessages(messages)
                    loadMessages()
                    Toast.makeText(this, "Message Added", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteMessage(position: Int) {
        if (position in messages.indices) {
            messages.removeAt(position)
            prefs.saveBlockMessages(messages)
            loadMessages()
            Toast.makeText(this, "Message Deleted", Toast.LENGTH_SHORT).show()
        }
    }

    inner class MessagesAdapter : RecyclerView.Adapter<MessagesAdapter.ViewHolder>() {
        
        inner class ViewHolder(val binding: ItemBlockMessageBinding) : RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemBlockMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = messages[position]
            holder.binding.tvMessage.text = item.message
            
            // Format tags
            val tagsStr = if (item.tags.contains("ALL")) "ALL MODES" else item.tags.joinToString(", ")
            holder.binding.tvTags.text = "TAGS: $tagsStr"
            
            holder.binding.btnDelete.setOnClickListener {
                MaterialAlertDialogBuilder(this@BlockMessagesActivity)
                    .setTitle("Delete Message?")
                    .setMessage(item.message)
                    .setPositiveButton("Delete") { _, _ ->
                        deleteMessage(holder.adapterPosition)
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }

        override fun getItemCount() = messages.size
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
