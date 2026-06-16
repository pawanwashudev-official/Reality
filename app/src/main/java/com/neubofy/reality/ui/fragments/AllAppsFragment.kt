package com.neubofy.reality.ui.fragments

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.data.db.AppLimitEntity
import com.neubofy.reality.databinding.FragmentAllAppsBinding
import com.neubofy.reality.databinding.ItemAppUsageBinding
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Statistics-only fragment showing app usage times from Android's UsageStatsManager.
 * Uses system data (like Digital Wellbeing) - no self-monitoring needed.
 */
class AllAppsFragment : Fragment() {

    private var _binding: FragmentAllAppsBinding? = null
    private val binding get() = _binding!!
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    private var appList = mutableListOf<AppUsageItem>()
    private var adapter: AppUsageAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAllAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.recyclerAllApps.layoutManager = LinearLayoutManager(requireContext())
        adapter = AppUsageAdapter()
        binding.recyclerAllApps.adapter = adapter
        
        loadAppsAndUsage()
    }
    
    override fun onResume() {
        super.onResume()
        loadAppsAndUsage()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        scope.cancel()
    }

    private fun loadAppsAndUsage() {
        scope.launch(Dispatchers.IO) {
            try {
                val context = context ?: return@launch
                val pm = context.packageManager
                
                // Get usage stats from system (like Digital Wellbeing)
                // Get accurate usage
                val usageMap = com.neubofy.reality.utils.UsageUtils.getUsageSinceMidnight(context)
                
                // Load limits from DB
                val db = AppDatabase.getDatabase(context)
                val limits = db.appLimitDao().getAllLimits()
                val limitMap = limits.associateBy { it.packageName }
                
                // Build app list with usage data
                val apps = mutableListOf<AppUsageItem>()
                val seenPackages = mutableSetOf<String>()
                
                // Process usage stats
                for ((pkg, usedTime) in usageMap) {
                    if (pkg == context.packageName) continue
                    if (usedTime <= 0) continue // Skip apps with no usage
                    
                    try {
                        val appInfo = pm.getApplicationInfo(pkg, 0)
                        
                        // Filter out System Apps to match Digital Wellbeing
                        // Exception: Allow updated system apps (like Chrome, Photos) that are user-facing
                        val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                        val isUpdatedSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                        
                        // Heuristic: If it's a System app AND NOT updated (factory version), skip it.
                        // However, some system apps are important (Youtube, Chrome).
                        // Better check: Launch Intent. If it has a launch intent, it's likely a user app.
                        val hasLaunchIntent = pm.getLaunchIntentForPackage(pkg) != null
                        
                        // Logic: Include if (User App OR (System App AND Has Launcher Icon))
                        val shouldInclude = !isSystem || isUpdatedSystem || hasLaunchIntent
                        
                        if (!shouldInclude) continue

                        val label = appInfo.loadLabel(pm).toString()
                        val limitEntity = limitMap[pkg]
                        
                        apps.add(AppUsageItem(
                            packageName = pkg,
                            appName = label,
                            usedMs = usedTime,
                            limitMins = limitEntity?.limitInMinutes ?: 0,
                            limitEntity = limitEntity
                        ))
                    } catch (e: Exception) {
                        // App might be uninstalled, skip
                    }
                }
                
                // Sort by usage (most used first)
                apps.sortByDescending { it.usedMs }
                
                // Get accurate total screen time (Screen On/Off events)
                val totalScreenTimeDb = com.neubofy.reality.utils.UsageUtils.getScreenTimeSinceMidnight(context)
                
                // Calculate total screen time (Clamped to elapsed time to avoid > 24h errors)
                val calendar = Calendar.getInstance()
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val midnightTime = calendar.timeInMillis
                val activeTime = System.currentTimeMillis() - midnightTime
                
                // Clamp absolute max to current time (can't use phone more than day exists)
                // Use the Screen Time from events as the primary source of truth
                val finalTotalMs = if (totalScreenTimeDb > activeTime) activeTime else totalScreenTimeDb
                
                val totalMins = TimeUnit.MILLISECONDS.toMinutes(finalTotalMs)
                val totalHours = totalMins / 60
                val totalMinsRemainder = totalMins % 60
                val totalTimeText = if (totalHours > 0) {
                    "${totalHours}h ${totalMinsRemainder}m"
                } else {
                    "${totalMins}m"
                }
                
                withContext(Dispatchers.Main) {
                    if (_binding == null) return@withContext
                    
                    // Update total screen time
                    binding.tvTotalUsage.text = totalTimeText
                    
                    appList.clear()
                    appList.addAll(apps)
                    adapter?.notifyDataSetChanged()
                    
                    // Show empty state if no usage data
                    if (apps.isEmpty()) {
                        binding.tvTotalUsage.text = "0m"
                        android.widget.Toast.makeText(
                            context, 
                            "No user app usage data available.", 
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context, 
                        "Error: ${e.message}. Check Usage Access permission.", 
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    inner class AppUsageAdapter : RecyclerView.Adapter<AppUsageAdapter.VH>() {
        
        // Simple memory cache for icons to prevent flickering/lag
        private val iconCache = android.util.LruCache<String, android.graphics.drawable.Drawable>(50)
        
        inner class VH(val binding: ItemAppUsageBinding) : RecyclerView.ViewHolder(binding.root)
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(ItemAppUsageBinding.inflate(LayoutInflater.from(parent.context), parent, false))
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = appList[position]
            holder.binding.tvAppName.text = item.appName
            
            // Format usage time
            val usedMins = TimeUnit.MILLISECONDS.toMinutes(item.usedMs)
            val usedHours = usedMins / 60
            val usedMinsRemainder = usedMins % 60
            
            val usageText = if (usedHours > 0) {
                "${usedHours}h ${usedMinsRemainder}m"
            } else if (usedMins > 0) {
                "${usedMins}m"
            } else {
                "<1m"
            }
            
            // Show limit info if set
            if (item.limitMins > 0) {
                val limitHours = item.limitMins / 60
                val limitMinsRemainder = item.limitMins % 60
                val limitText = if (limitHours > 0) {
                    "${limitHours}h ${limitMinsRemainder}m"
                } else {
                    "${item.limitMins}m"
                }
                holder.binding.tvUsageTime.text = "$usageText / $limitText"
                
                // Color based on usage vs limit
                val usagePercent = (usedMins.toFloat() / item.limitMins * 100).toInt()
                when {
                    usagePercent >= 100 -> holder.binding.tvUsageTime.setTextColor(0xFFFF6B6B.toInt())
                    usagePercent >= 80 -> holder.binding.tvUsageTime.setTextColor(0xFFFFB74D.toInt())
                    else -> holder.binding.tvUsageTime.setTextColor(0xFF81C784.toInt())
                }
                
                // Progress bar
                holder.binding.progressBar.max = item.limitMins
                holder.binding.progressBar.progress = usedMins.toInt().coerceAtMost(item.limitMins)
            } else {
                holder.binding.tvUsageTime.text = usageText
                holder.binding.tvUsageTime.setTextColor(0xFFFFFFFF.toInt())
                holder.binding.progressBar.max = 100
                holder.binding.progressBar.progress = 0
            }
            
            // Load icon with caching
            val cachedIcon = iconCache.get(item.packageName)
            if (cachedIcon != null) {
                holder.binding.ivIcon.setImageDrawable(cachedIcon)
            } else {
                // Placeholder or clear
                holder.binding.ivIcon.setImageDrawable(null) 
                
                scope.launch(Dispatchers.IO) {
                    try {
                        val icon = holder.itemView.context.packageManager.getApplicationIcon(item.packageName)
                        iconCache.put(item.packageName, icon)
                        withContext(Dispatchers.Main) { 
                            // Verify holder is still bound to the same item
                            if (appList.getOrNull(holder.adapterPosition)?.packageName == item.packageName) {
                                holder.binding.ivIcon.setImageDrawable(icon) 
                            }
                        }
                    } catch(e: Exception) {}
                }
            }
        }

        override fun getItemCount() = appList.size
    }

    data class AppUsageItem(
        val packageName: String,
        var appName: String,
        var usedMs: Long,
        var limitMins: Int,
        var limitEntity: AppLimitEntity?
    )
}
