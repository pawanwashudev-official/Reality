package com.neubofy.reality.ui.activity

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.neubofy.reality.R
import com.neubofy.reality.utils.BlockCache
import com.neubofy.reality.utils.SettingsBox
import com.neubofy.reality.utils.SavedPreferencesLoader

/**
 * ActiveBlocksActivity - Shows all currently blocked entities.
 * 
 * 1. Apps (from BlockCache.packages)
 * 2. Settings Pages (from SettingsBox)
 * 3. Websites (from BlockCache.FocusModeData.blockedWebsites)
 * 
 * Reads directly from MEMORY/DISK cache (Truth), ignoring Database.
 */
class ActiveBlocksActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: TextView
    private lateinit var statusText: TextView
    private val adapter = UnifiedBlockAdapter()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_active_blocks)
        
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
        supportActionBar?.title = "Active Blocks"
        
        recyclerView = findViewById(R.id.recyclerBlocked)
        emptyView = findViewById(R.id.emptyView)
        statusText = findViewById(R.id.statusText)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        loadAllBlocks()
    }
    
    override fun onResume() {
        super.onResume()
        loadAllBlocks()
    }
    
    private fun loadAllBlocks() {
        // Force refresh from disk
        BlockCache.loadFromDisk(this)
        val unifiedList = mutableListOf<BlockItem>()
        
        // 1. APPS from BlockCache
        val blockedApps = BlockCache.getAllBlockedApps() // Returns map<pkg, reasons>
        
        for ((pkg, reasons) in blockedApps) {
            try {
                // Verify if actually blocked
                val (shouldBlock, _) = BlockCache.shouldBlock(pkg)
                val appInfo = packageManager.getApplicationInfo(pkg, 0)
                val label = packageManager.getApplicationLabel(appInfo).toString()
                
                unifiedList.add(BlockItem(
                    type = BlockType.APP,
                    title = label,
                    subtitle = pkg,
                    reasons = reasons.toList(),
                    isActive = shouldBlock, // If false, means paused/emergency
                    iconPackage = pkg
                ))
            } catch (e: PackageManager.NameNotFoundException) {
                // App uninstalled
            }
        }
        
        // 2. SETTINGS PAGES from SettingsBox
        val settings = SettingsBox.getAllProtectedPages()
        for (page in settings) {
             unifiedList.add(BlockItem(
                 type = BlockType.SETTING,
                 title = page.blockReason, // e.g. "Time Settings Protection"
                 subtitle = "${page.packageName}/${page.className}",
                 reasons = listOf(page.pageType.name),
                 isActive = SettingsBox.isAnyProtectionActive(), 
                 iconPackage = "com.android.settings"
             ))
        }
        
        // 3. WEBSITES from Focus Mode / Schedules
        // BlockCache tracks if website blocking is active, but individual sites are in Prefs
        val prefs = SavedPreferencesLoader(this)
        val blockedSites = prefs.getFocusModeData().blockedWebsites
        val isWebBlockActive = BlockCache.isAnyBlockingModeActive
        
        for (site in blockedSites) {
            if (site.isNotBlank()) {
                unifiedList.add(BlockItem(
                    type = BlockType.WEBSITE,
                    title = site,
                    subtitle = "Browser Block",
                    reasons = listOf("Focus Mode"),
                    isActive = isWebBlockActive,
                    iconPackage = "com.android.chrome" // Default to chrome icon
                ))
            }
        }
        
        // Sort: Apps Name -> Settings -> Websites
        unifiedList.sortWith(compareBy({ it.type.ordinal }, { it.title.lowercase() }))
        
        adapter.setData(unifiedList)
        
        val activeCount = unifiedList.count { it.isActive }
        statusText.text = if (activeCount > 0) "🛡️ ${activeCount} active blocks" else "😊 No active blocks"
        
        emptyView.isVisible = unifiedList.isEmpty()
    }
    
    enum class BlockType { APP, SETTING, WEBSITE }
    
    data class BlockItem(
        val type: BlockType,
        val title: String,
        val subtitle: String,
        val reasons: List<String>,
        val isActive: Boolean,
        val iconPackage: String
    )
    
    inner class UnifiedBlockAdapter : RecyclerView.Adapter<UnifiedBlockAdapter.ViewHolder>() {
        private val items = mutableListOf<BlockItem>()
        
        fun setData(newItems: List<BlockItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }
        
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
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
            
            holder.name.text = item.title
            holder.reasons.text = item.subtitle
            
            // Icon Logic
            try {
                val icon = packageManager.getApplicationIcon(item.iconPackage)
                holder.icon.setImageDrawable(icon)
            } catch (e: Exception) {
                holder.icon.setImageResource(android.R.drawable.sym_def_app_icon)
            }
            
            // Status Logic
            if (item.isActive) {
                holder.status.text = when(item.type) {
                    BlockType.APP -> "BLOCKED"
                    BlockType.SETTING -> "PROTECTED"
                    BlockType.WEBSITE -> "BLOCKED"
                }
                holder.status.setTextColor(checkColor("#FF4444")) // Red
            } else {
                holder.status.text = "PAUSED"
                holder.status.setTextColor(checkColor("#FFA500")) // Orange
            }
        }
        
        private fun checkColor(hex: String): Int {
            return android.graphics.Color.parseColor(hex)
        }
        
        override fun getItemCount() = items.size
    }
}
