package com.neubofy.reality.ui.activity

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.ui.base.BaseActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.neubofy.reality.R
import com.neubofy.reality.databinding.ActivitySelectAppsBinding

class SelectAppsActivity : BaseActivity() {

    private lateinit var binding: ActivitySelectAppsBinding
    private lateinit var selectedAppList: HashSet<String>
    private var appItemList: MutableList<AppItem> = mutableListOf()
    private var allAppsSelected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySelectAppsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        selectedAppList = intent.getStringArrayListExtra("PRE_SELECTED_APPS")?.toHashSet() ?: HashSet()
        
        binding.appList.layoutManager = LinearLayoutManager(this)
        
        // Remove Magic Button (PackageWand) since it's deleted
        binding.selectAppsMagic.visibility = View.GONE

        binding.selectAll.setOnClickListener {
            val adapter = binding.appList.adapter as? ApplicationAdapter ?: return@setOnClickListener
            if (allAppsSelected) {
                // Clearing all (Removing)
                if (!com.neubofy.reality.utils.StrictLockUtils.isModificationAllowed(this)) {
                    Toast.makeText(this, "Removing apps locked by Strict Mode. Available 00:00-00:10.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                adapter.apps.forEach { selectedAppList.remove(it.packageName) }
            } else {
                // Selecting all (Adding) - Included
                adapter.apps.forEach { selectedAppList.add(it.packageName) }
            }
            adapter.notifyDataSetChanged()
            updateSelectAllButton()
        }

        loadApps()

        binding.confirmSelection.setOnClickListener {
            val resultIntent = intent.apply {
                putStringArrayListExtra("SELECTED_APPS", ArrayList(selectedAppList))
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(q: String?) = false
            @SuppressLint("NotifyDataSetChanged")
            override fun onQueryTextChange(newText: String?): Boolean {
                val query = newText ?: ""
                val filtered = appItemList.filter { it.displayName.contains(query, ignoreCase = true) }
                (binding.appList.adapter as? ApplicationAdapter)?.updateData(filtered)
                
                // Show empty state if no results
                if (filtered.isEmpty() && query.isNotEmpty()) {
                    binding.emptyState.visibility = View.VISIBLE
                    binding.appList.visibility = View.GONE
                } else {
                    binding.emptyState.visibility = View.GONE
                    binding.appList.visibility = View.VISIBLE
                }
                
                return true
            }
        })
    }

    private fun loadApps() {
        // Show loading
        binding.loadingProgress.visibility = View.VISIBLE
        binding.appList.visibility = View.GONE
        binding.emptyState.visibility = View.GONE
        
        lifecycleScope.launch(Dispatchers.IO) {
            val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
            val profiles = launcherApps.profiles
            
            for (profile in profiles) {
                val apps = launcherApps.getActivityList(null, profile).map { it.applicationInfo }
                apps.forEach { info ->
                    if (info.packageName != packageName) {
                        val label = info.loadLabel(packageManager).toString()
                        appItemList.add(AppItem(info.packageName, info, label))
                    }
                }
            }
            appItemList.sortBy { it.displayName.lowercase() }
            val sorted = sortSelectedItemsToTop(appItemList)
            
            withContext(Dispatchers.Main) {
                // Hide loading
                binding.loadingProgress.visibility = View.GONE
                
                if (sorted.isEmpty()) {
                    // Show empty state
                    binding.emptyState.visibility = View.VISIBLE
                    binding.appList.visibility = View.GONE
                } else {
                    // Show list
                    binding.appList.visibility = View.VISIBLE
                    binding.emptyState.visibility = View.GONE
                }
                
                binding.appList.adapter = ApplicationAdapter(sorted, selectedAppList)
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

    private fun isWithinUnlockWindow(): Boolean {
        val calendar = java.util.Calendar.getInstance()
        val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val minute = calendar.get(java.util.Calendar.MINUTE)
        return hour == 0 && minute < 10
    }

    inner class ApplicationViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.app_icon)
        val name: TextView = v.findViewById(R.id.app_name)
        val check: CheckBox = v.findViewById(R.id.checkbox)
    }

    inner class ApplicationAdapter(var apps: List<AppItem>, private val selected: HashSet<String>) : RecyclerView.Adapter<ApplicationViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ApplicationViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.select_apps_item, parent, false)
            return ApplicationViewHolder(v)
        }
        override fun onBindViewHolder(holder: ApplicationViewHolder, position: Int) {
            val item = apps[position]
            holder.name.text = item.displayName
            holder.check.setOnCheckedChangeListener(null)
            holder.check.isChecked = selected.contains(item.packageName)
            
            holder.icon.setImageDrawable(null)
            item.appInfo?.let { info ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val drawable = info.loadIcon(packageManager)
                    withContext(Dispatchers.Main) { holder.icon.setImageDrawable(drawable) }
                }
            }

            holder.check.setOnCheckedChangeListener { _, checked ->
                val isAllowed = com.neubofy.reality.utils.StrictLockUtils.isModificationAllowed(this@SelectAppsActivity)

                // If trying to REMOVE (!checked) and NOT Allowed -> Block
                if (!checked && !isAllowed) {
                    Toast.makeText(this@SelectAppsActivity, "Removing apps is locked by Strict Mode. Available 00:00-00:10.", Toast.LENGTH_SHORT).show()
                    holder.check.isChecked = true // Revert
                    return@setOnCheckedChangeListener
                }
                
                if (checked) selected.add(item.packageName) else selected.remove(item.packageName)
                updateSelectAllButton()
            }
            holder.itemView.setOnClickListener { 
                holder.check.isChecked = !holder.check.isChecked 
            }
        }
        override fun getItemCount() = apps.size
        @SuppressLint("NotifyDataSetChanged")
        fun updateData(newList: List<AppItem>) { apps = newList; notifyDataSetChanged() }
    }

    data class AppItem(val packageName: String, val appInfo: ApplicationInfo? = null, val displayName: String = packageName)
}