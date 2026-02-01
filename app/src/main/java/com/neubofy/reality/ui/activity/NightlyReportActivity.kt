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

import com.neubofy.reality.data.model.ChatMessage
import com.neubofy.reality.ui.adapter.ChatAdapter

class NightlyReportActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNightlyReportBinding
    private var selectedDate: LocalDate = LocalDate.now()
    private lateinit var adapter: ChatAdapter
    
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

    private var availableDates: List<LocalDate> = emptyList()

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }
        
        val mainPrefs = getSharedPreferences("MainPrefs", android.content.Context.MODE_PRIVATE)
        val userName = mainPrefs.getString("user_name", "User") ?: "User"

        adapter = ChatAdapter(mutableListOf(), userName)
        binding.recyclerChat.layoutManager = LinearLayoutManager(this)
        binding.recyclerChat.adapter = adapter
        
        binding.btnPrevDate.setOnClickListener {
            val prev = availableDates.lastOrNull { it.isBefore(selectedDate) }
            if (prev != null) {
                selectedDate = prev
                loadReport()
                updateNavigationButtons()
            }
        }
        
        binding.btnNextDate.setOnClickListener {
            val next = availableDates.firstOrNull { it.isAfter(selectedDate) }
            if (next != null) {
                selectedDate = next
                loadReport()
                updateNavigationButtons()
            }
        }
        
        updateDateDisplay()
        updateNavigationButtons()
    }
    
    private fun updateNavigationButtons() {
        lifecycleScope.launch {
            availableDates = NightlyRepository.getAvailableDates(this@NightlyReportActivity)
            
            val hasPrev = availableDates.any { it.isBefore(selectedDate) }
            val hasNext = availableDates.any { it.isAfter(selectedDate) }
            
            binding.btnPrevDate.isEnabled = hasPrev
            binding.btnPrevDate.alpha = if (hasPrev) 1.0f else 0.3f
            
            binding.btnNextDate.isEnabled = hasNext
            binding.btnNextDate.alpha = if (hasNext) 1.0f else 0.3f
        }
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
                        messages.add(ChatMessage(section.trim(), false, sender = "Reality AI"))
                    }
                } else {
                     messages.add(ChatMessage(content, false, sender = "Reality AI"))
                }
            } else {
                messages.add(ChatMessage("No report generated for this date.", false, sender = "System"))
            }
            
            adapter.submitList(messages)
        }
    }
}
