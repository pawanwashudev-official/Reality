package com.neubofy.reality.ui.activity

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.neubofy.reality.R
import com.neubofy.reality.databinding.ActivityLauncherBinding
import com.neubofy.reality.databinding.LauncherItemBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LauncherActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLauncherBinding
    private lateinit var adapter: ApplicationAdapter
    private val pinnedAppPackages = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityLauncherBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val packageChangeIntentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package") // Important for detecting package changes
        }
        registerReceiver(packageChangeReceiver, packageChangeIntentFilter)

        pinnedAppPackages.addAll(loadPinnedApps())
        adapter = ApplicationAdapter(listOf())
        binding.appList.layoutManager = LinearLayoutManager(this)
        binding.appList.adapter = adapter
        reloadApps()

        binding.appList.addItemDecoration(
            DividerItemDecoration(
                this, OrientationHelper.VERTICAL
            )
        )
        binding.tasks.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }
        binding.clock.text = getCurrentTime()
        handleBackPress()

    }
    override fun onResume() {
        super.onResume()
        binding.clock.text = getCurrentTime()
    }
    private fun getCurrentTime(): String {
        val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
        return sdf.format(Date())
    }

    fun launchAppByPackageName(context: Context, packageName: String) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
            } else {
                Toast.makeText(context, "App not found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace() // Handle any exceptions
            Toast.makeText(context, "Error launching app", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                return
            }
        })
    }


    private fun reloadApps(){

        lifecycleScope.launch(Dispatchers.IO) {
            val launcherApps = getSystemService(LAUNCHER_APPS_SERVICE) as LauncherApps
            val allApps = launcherApps.getActivityList(null, android.os.Process.myUserHandle()).mapNotNull {
                it.applicationInfo
            }.filter {
                it.packageName != packageName
            }

            // Separate pinned and unpinned apps
            val pinnedApps = allApps.filter { pinnedAppPackages.contains(it.packageName) }
            val unpinnedApps = allApps.filter { !pinnedAppPackages.contains(it.packageName) }.shuffled()

            val sortedApps = pinnedApps + unpinnedApps // Pinned apps first, followed by shuffled unpinned apps

            lifecycleScope.launch(Dispatchers.Main) {
                adapter.updatePackages(sortedApps)
            }
        }
    }
    private val packageChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action == Intent.ACTION_PACKAGE_ADDED || action == Intent.ACTION_PACKAGE_REMOVED) {
                reloadApps()
            }
        }
    }


    fun openAppInfo(context: Context, packageName: String) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                data = Uri.parse("package:$packageName")
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Unable to open app info", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadPinnedApps(): Set<String> {
        val sharedPreferences = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        return sharedPreferences.getStringSet("pinned_apps", emptySet()) ?: emptySet()
    }


    inner class ApplicationViewHolder(private val binding: LauncherItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(app: ApplicationInfo, packageManager: PackageManager) {
            binding.appName.text = app.loadLabel(packageManager)
            binding.root.setOnClickListener {
                launchAppByPackageName(baseContext,app.packageName)
            }
            binding.root.setOnLongClickListener{
                openAppInfo(baseContext,app.packageName)
                return@setOnLongClickListener true
            }
        }
    }



    inner class ApplicationAdapter(private var packages: List<ApplicationInfo>) :
        RecyclerView.Adapter<ApplicationViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ApplicationViewHolder {
            val binding = LauncherItemBinding.inflate(layoutInflater, parent, false)
            return ApplicationViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ApplicationViewHolder, position: Int) {
            holder.bind(packages[position], packageManager)
        }

        override fun getItemCount(): Int = packages.size

        @SuppressLint("NotifyDataSetChanged")
        fun updatePackages(newPackages: List<ApplicationInfo>){
            packages = newPackages
            notifyDataSetChanged()
        }
    }
}