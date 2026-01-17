package com.neubofy.reality.ui.fragments

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.neubofy.reality.R
import com.neubofy.reality.databinding.FragmentBlocklistAppsBinding
import com.neubofy.reality.services.AppBlockerService
import com.neubofy.reality.utils.SavedPreferencesLoader
import com.neubofy.reality.utils.StrictLockUtils

class BlocklistAppsFragment : Fragment() {

    private var _binding: FragmentBlocklistAppsBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var selectedAppList: HashSet<String>
    private var appItemList: MutableList<AppItem> = mutableListOf()
    private var allAppsSelected = false
    private lateinit var savedPreferencesLoader: SavedPreferencesLoader

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBlocklistAppsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        savedPreferencesLoader = SavedPreferencesLoader(requireContext())
        
        val v2Data = savedPreferencesLoader.getFocusModeData()
        if (v2Data.selectedApps.isNotEmpty()) {
            selectedAppList = v2Data.selectedApps
        } else {
            selectedAppList = HashSet(savedPreferencesLoader.getFocusModeSelectedApps())
        }
        
        binding.appList.layoutManager = LinearLayoutManager(requireContext())
        
        binding.selectAll.setOnClickListener {
            val adapter = binding.appList.adapter as? ApplicationAdapter ?: return@setOnClickListener
            if (allAppsSelected) {
                // Clearing all (Removing)
                if (!StrictLockUtils.isModificationAllowedFor(requireContext(), StrictLockUtils.FeatureType.BLOCKLIST)) {
                    Toast.makeText(requireContext(), "Removing apps locked by Strict Mode. Available during Maintenance Window (00:00-00:10).", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                adapter.apps.forEach { selectedAppList.remove(it.packageName) }
            } else {
                // Selecting all (Adding) - Always allowed
                adapter.apps.forEach { selectedAppList.add(it.packageName) }
            }
            adapter.notifyDataSetChanged()
            updateSelectAllButton()
        }

        loadApps()

        binding.confirmSelection.setOnClickListener {
            save()
        }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?) = false
            @SuppressLint("NotifyDataSetChanged")
            override fun onQueryTextChange(newText: String?): Boolean {
                val query = newText ?: ""
                val filtered = appItemList.filter { it.displayName.contains(query, ignoreCase = true) }
                (binding.appList.adapter as? ApplicationAdapter)?.updateData(filtered)
                return true
            }
        })
    }
    
    private fun save() {
        // 1. Save to Legacy Prefs
        savedPreferencesLoader.saveFocusModeSelectedApps(ArrayList(selectedAppList))
        
        // 2. Save to FocusModeData (V2) - Critical for AppBlockerService
        val focusData = savedPreferencesLoader.getFocusModeData()
        focusData.selectedApps = selectedAppList
        savedPreferencesLoader.saveFocusModeData(focusData)
        
        // Send refresh intent
        val intent = Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE)
        intent.setPackage(requireContext().packageName)
        requireContext().sendBroadcast(intent)
        
        Toast.makeText(requireContext(), "Blocklist Saved", Toast.LENGTH_SHORT).show()
    }

    private fun loadApps() {
        lifecycleScope.launch(Dispatchers.Default) {
             val launcherApps = requireContext().getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
             val profiles = launcherApps.profiles
             
             // Expanded Whitelist to Hide
             val hiddenApps = setOf(
                 "com.android.calculator2", "com.google.android.calculator",
                 "com.android.dialer", "com.google.android.dialer",
                 "com.android.contacts", "com.google.android.contacts",
                 "com.android.deskclock", "com.google.android.deskclock",
                 "com.android.systemui", 
                 "com.google.android.packageinstaller", "com.android.packageinstaller",
                 "com.neubofy.reality" // Self
             )
             
             val tempAppList = mutableListOf<AppItem>()
             
             for (profile in profiles) {
                 val apps = launcherApps.getActivityList(null, profile).map { it.applicationInfo }
                 apps.forEach { info ->
                     if (!hiddenApps.contains(info.packageName)) {
                         val label = info.loadLabel(requireContext().packageManager).toString()
                         tempAppList.add(AppItem(info.packageName, info, label))
                     }
                 }
             }
             tempAppList.sortBy { it.displayName.lowercase() }
             
             withContext(Dispatchers.Main) {
                 appItemList = tempAppList
                 val sorted = sortSelectedItemsToTop(appItemList)
                 binding.appList.adapter = ApplicationAdapter(sorted, selectedAppList) {
                     // On Config Changed -> Send Broadcast to Refresh Service
                     val intent = Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE)
                     intent.setPackage(requireContext().packageName)
                     requireContext().sendBroadcast(intent)
                 }
                 updateSelectAllButton()
             }
        }
    }

    private fun updateSelectAllButton() {
        val adapter = binding.appList.adapter as? ApplicationAdapter ?: return
        allAppsSelected = adapter.apps.all { selectedAppList.contains(it.packageName) }
        binding.selectAll.text = if (allAppsSelected) "Clear All" else "Select All"
    }

    private fun sortSelectedItemsToTop(list: List<AppItem>): List<AppItem> {
        return list.sortedWith(compareBy<AppItem> { !selectedAppList.contains(it.packageName) }.thenBy { it.displayName.lowercase() })
    }

    inner class ApplicationViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.app_icon)
        val name: TextView = v.findViewById(R.id.app_name)
        val check: CheckBox = v.findViewById(R.id.checkbox)
        val btnExpand: ImageView = v.findViewById(R.id.btnExpand)
        val modeSelectionLayout: View = v.findViewById(R.id.modeSelectionLayout)
        val cbFocus: CheckBox = v.findViewById(R.id.cbFocus)
        val cbAutoFocus: CheckBox = v.findViewById(R.id.cbAutoFocus)
        val cbBedtime: CheckBox = v.findViewById(R.id.cbBedtime)
        val cbCalendar: CheckBox = v.findViewById(R.id.cbCalendar)
    }

    inner class ApplicationAdapter(
        var apps: List<AppItem>, 
        private val selected: HashSet<String>,
        private val onConfigChanged: () -> Unit
    ) : RecyclerView.Adapter<ApplicationViewHolder>() {
        
        private val expandedPositions = mutableSetOf<Int>()
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ApplicationViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_blocklist_app_expandable, parent, false)
            return ApplicationViewHolder(v)
        }
        
        override fun onBindViewHolder(holder: ApplicationViewHolder, position: Int) {
            val item = apps[position]
            val isSelected = selected.contains(item.packageName)
            val isExpanded = expandedPositions.contains(position)
            
            holder.name.text = item.displayName
            holder.check.setOnCheckedChangeListener(null)
            holder.check.isChecked = isSelected
            
            // Show expand button ONLY for selected/checked apps
            holder.btnExpand.visibility = if (isSelected) View.VISIBLE else View.GONE
            holder.modeSelectionLayout.visibility = if (isSelected && isExpanded) View.VISIBLE else View.GONE
            
            // Rotate arrow based on expansion state
            holder.btnExpand.rotation = if (isExpanded) 180f else 0f
            
            // Load icon
            holder.icon.setImageDrawable(null)
            item.appInfo?.let { info ->
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        val drawable = info.loadIcon(requireContext().packageManager)
                        withContext(Dispatchers.Main) { holder.icon.setImageDrawable(drawable) }
                    } catch (e: Exception) {}
                }
            }
            
            // Load per-app mode config
            val config = savedPreferencesLoader.getBlockedAppConfig(item.packageName)
            holder.cbFocus.setOnCheckedChangeListener(null)
            holder.cbAutoFocus.setOnCheckedChangeListener(null)
            holder.cbBedtime.setOnCheckedChangeListener(null)
            holder.cbCalendar.setOnCheckedChangeListener(null)
            
            holder.cbFocus.isChecked = config.blockInFocus
            holder.cbAutoFocus.isChecked = config.blockInAutoFocus
            holder.cbBedtime.isChecked = config.blockInBedtime
            holder.cbCalendar.isChecked = config.blockInCalendar
            
            // Mode checkbox listeners - save immediately AND refresh service
            val modeChangeListener = { _: android.widget.CompoundButton, _: Boolean ->
                val updatedConfig = com.neubofy.reality.Constants.BlockedAppConfig(
                    packageName = item.packageName,
                    blockInFocus = holder.cbFocus.isChecked,
                    blockInAutoFocus = holder.cbAutoFocus.isChecked,
                    blockInBedtime = holder.cbBedtime.isChecked,
                    blockInCalendar = holder.cbCalendar.isChecked
                )
                savedPreferencesLoader.updateBlockedAppConfig(updatedConfig)
                onConfigChanged() // Trigger refresh
            }
            holder.cbFocus.setOnCheckedChangeListener(modeChangeListener)
            holder.cbAutoFocus.setOnCheckedChangeListener(modeChangeListener)
            holder.cbBedtime.setOnCheckedChangeListener(modeChangeListener)
            holder.cbCalendar.setOnCheckedChangeListener(modeChangeListener)
            
            // Expand button click
            holder.btnExpand.setOnClickListener {
                if (expandedPositions.contains(position)) {
                    expandedPositions.remove(position)
                } else {
                    expandedPositions.add(position)
                }
                notifyItemChanged(position)
            }

            // Main checkbox listener
            holder.check.setOnCheckedChangeListener { _, checked ->
                val isAllowed = StrictLockUtils.isModificationAllowedFor(requireContext(), StrictLockUtils.FeatureType.BLOCKLIST)

                // If trying to REMOVE (!checked) and NOT Allowed -> Block
                if (!checked && !isAllowed) {
                    Toast.makeText(requireContext(), "Strict Mode Active. Cannot remove apps.", Toast.LENGTH_SHORT).show()
                    holder.check.isChecked = true // Revert
                    return@setOnCheckedChangeListener
                }
                
                if (checked) {
                    selected.add(item.packageName)
                } else {
                    selected.remove(item.packageName)
                    expandedPositions.remove(position) // Collapse when unchecked
                }
                
                // Update visibility of expand button
                holder.btnExpand.visibility = if (checked) View.VISIBLE else View.GONE
                holder.modeSelectionLayout.visibility = View.GONE
                
                updateSelectAllButton()
            }
            
            // Row click toggles checkbox
            holder.itemView.findViewById<View>(R.id.mainRow).setOnClickListener { 
                holder.check.isChecked = !holder.check.isChecked 
            }
        }
        
        override fun getItemCount() = apps.size
        
        @SuppressLint("NotifyDataSetChanged")
        fun updateData(newList: List<AppItem>) { 
            apps = newList
            expandedPositions.clear()
            notifyDataSetChanged() 
        }
    }

    data class AppItem(val packageName: String, val appInfo: ApplicationInfo? = null, val displayName: String = packageName)

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
