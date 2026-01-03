package com.neubofy.reality.ui.activity

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.neubofy.reality.R
import com.neubofy.reality.utils.BlockCache

/**
 * ActiveBlocksActivity - Shows all currently blocked apps from THE BOX.
 * 
 * This reads directly from BlockCache (persisted to disk).
 * Always shows the exact same data that the blocker is using.
 */
class ActiveBlocksActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var statusText: TextView
    private val adapter = BlockedAppAdapter()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_active_blocks)
        
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        
        recyclerView = findViewById(R.id.recyclerBlocked)
        emptyView = findViewById(R.id.emptyView)
        statusText = findViewById(R.id.statusText)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        loadBlockedApps()
    }
    
    override fun onResume() {
        super.onResume()
        loadBlockedApps()
    }
    
    private fun loadBlockedApps() {
        // Load from disk first (in case RAM was cleared)
        BlockCache.loadFromDisk(this)
        
        val blockedApps = BlockCache.getAllBlockedApps()
        val isEmergencyActive = BlockCache.emergencySessionEndTime > System.currentTimeMillis()
        val isActive = BlockCache.isAnyBlockingModeActive
        
        // Build UI list
        val list = mutableListOf<BlockedAppItem>()
        
        for ((packageName, reasons) in blockedApps) {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                val label = packageManager.getApplicationLabel(appInfo).toString()
                val icon = packageManager.getApplicationIcon(appInfo)
                
                list.add(BlockedAppItem(
                    name = label,
                    packageName = packageName,
                    icon = icon,
                    reasons = reasons.toList(),
                    isPaused = isEmergencyActive
                ))
            } catch (e: PackageManager.NameNotFoundException) {
                // App uninstalled, skip
            }
        }
        
        // Sort alphabetically
        list.sortBy { it.name.lowercase() }
        
        adapter.setData(list)
        
        // Update status
        statusText.text = when {
            isEmergencyActive -> "⏸️ Emergency Mode Active - All blocks paused"
            isActive -> "🛡️ Blocking ${list.size} apps"
            else -> "😊 No active blocking session"
        }
        
        if (list.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            emptyView.text = if (isActive) "No apps in current blocklist" else "Start a Focus session to block apps"
        } else {
            emptyView.visibility = View.GONE
        }
    }
    
    data class BlockedAppItem(
        val name: String,
        val packageName: String,
        val icon: android.graphics.drawable.Drawable,
        val reasons: List<String>,
        val isPaused: Boolean
    )
    
    class BlockedAppAdapter : RecyclerView.Adapter<BlockedAppAdapter.ViewHolder>() {
        private val items = mutableListOf<BlockedAppItem>()
        
        fun setData(newItems: List<BlockedAppItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }
        
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.appIcon)
            val name: TextView = view.findViewById(R.id.appName)
            val reasons: TextView = view.findViewById(R.id.appReasons)
            val status: TextView = view.findViewById(R.id.appStatus)
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_blocked_app, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.icon.setImageDrawable(item.icon)
            holder.name.text = item.name
            holder.reasons.text = item.reasons.joinToString(" • ")
            
            if (item.isPaused) {
                holder.status.text = "PAUSED"
                holder.status.setTextColor(android.graphics.Color.parseColor("#FFA500"))
            } else {
                holder.status.text = "BLOCKED"
                holder.status.setTextColor(android.graphics.Color.parseColor("#FF4444"))
            }
        }
        
        override fun getItemCount() = items.size
    }
}
