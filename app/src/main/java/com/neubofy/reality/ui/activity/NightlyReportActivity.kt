package com.neubofy.reality.ui.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.neubofy.reality.databinding.ActivityNightlyReportBinding
import com.neubofy.reality.databinding.ItemChatMessageBinding
import com.neubofy.reality.data.repository.NightlyRepository
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin

class NightlyReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNightlyReportBinding
    private var selectedDate: LocalDate = LocalDate.now()
    private val adapter = ChatAdapter()
    
    private val markwon: Markwon by lazy {
        Markwon.builder(this)
            .usePlugin(TablePlugin.create(this))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(this))
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNightlyReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Init date from Intent or Default
        val dateStr = intent.getStringExtra("date")
        selectedDate = if (dateStr != null) LocalDate.parse(dateStr) else LocalDate.now()

        setupUI()
        loadReport()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }
        
        binding.recyclerChat.layoutManager = LinearLayoutManager(this)
        binding.recyclerChat.adapter = adapter
        
        binding.btnPrevDate.setOnClickListener {
            val minDate = LocalDate.now().minusDays(3)
            if (selectedDate.isAfter(minDate)) {
                selectedDate = selectedDate.minusDays(1)
                loadReport()
                updateNavigationButtons()
            }
        }
        
        binding.btnNextDate.setOnClickListener {
            val maxDate = LocalDate.now()
            if (selectedDate.isBefore(maxDate)) {
                selectedDate = selectedDate.plusDays(1)
                loadReport()
                updateNavigationButtons()
            }
        }
        
        updateDateDisplay()
        updateNavigationButtons()
    }
    
    private fun updateNavigationButtons() {
        val today = LocalDate.now()
        val minDate = today.minusDays(3)
        
        val canGoBack = selectedDate.isAfter(minDate)
        val canGoForward = selectedDate.isBefore(today)
        
        binding.btnPrevDate.isEnabled = canGoBack
        binding.btnPrevDate.alpha = if (canGoBack) 1.0f else 0.3f
        
        binding.btnNextDate.isEnabled = canGoForward
        binding.btnNextDate.alpha = if (canGoForward) 1.0f else 0.3f
    }
    
    private fun updateDateDisplay() {
        binding.tvDate.text = selectedDate.format(DateTimeFormatter.ofPattern("EEEE, MMMM d"))
    }

    private fun loadReport() {
        updateDateDisplay()
        binding.progressLoading.visibility = android.view.View.VISIBLE
        adapter.submitList(emptyList()) // clear

        lifecycleScope.launch {
            val content = NightlyRepository.getReportContent(this@NightlyReportActivity, selectedDate)
            
            binding.progressLoading.visibility = android.view.View.GONE
            
            val messages = mutableListOf<ChatMessage>()
            
            if (content != null) {
                // Split report logic
                val sections = content.split(Regex("(?m)^(?=#{1,3}\\s|\\*\\*|^\\[[A-Z\\s]+\\])")).filter { it.isNotBlank() }
                if (sections.isNotEmpty()) {
                    sections.forEach { section ->
                        messages.add(ChatMessage("Reality AI", section.trim()))
                    }
                } else {
                     messages.add(ChatMessage("Reality AI", content))
                }
            } else {
                messages.add(ChatMessage("System", "No report generated for this date."))
            }
            
            adapter.submitList(messages)
        }
    }

    data class ChatMessage(val sender: String, val content: String)

    inner class ChatAdapter : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {
        private var items: List<ChatMessage> = emptyList()

        fun submitList(newItems: List<ChatMessage>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = ItemChatMessageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.binding.tvSender.visibility = android.view.View.VISIBLE
            holder.binding.tvSender.text = item.sender
            // Render Markdown
            markwon.setMarkdown(holder.binding.tvMessage, item.content)
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(val binding: ItemChatMessageBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
