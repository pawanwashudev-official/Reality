package com.neubofy.reality.ui.dialogs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.neubofy.reality.R
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.data.db.TaskListConfig
import com.neubofy.reality.google.GoogleTasksManager
import com.neubofy.reality.ui.adapter.TaskListConfigAdapter
import kotlinx.coroutines.launch

class AutoSetupDialog : BaseDialog() {

    private lateinit var rvTaskLists: RecyclerView
    private lateinit var btnAddList: MaterialButton
    private lateinit var adapter: TaskListConfigAdapter
    private val db by lazy { AppDatabase.getDatabase(requireContext()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_auto_setup, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        rvTaskLists = view.findViewById(R.id.rvTaskLists)
        btnAddList = view.findViewById(R.id.btnAddList)
        
        adapter = TaskListConfigAdapter(mutableListOf(), 
            onEdit = { showAddEditDialog(it) },
            onDelete = { deleteConfig(it) }
        )
        rvTaskLists.adapter = adapter
        
        btnAddList.setOnClickListener {
            if (adapter.itemCount >= 5) {
                Toast.makeText(requireContext(), "Maximum 5 task lists allowed", Toast.LENGTH_SHORT).show()
            } else {
                showAddEditDialog(null)
            }
        }
        
        loadConfigs()
    }

    private fun loadConfigs() {
        lifecycleScope.launch {
            val configs = db.taskListConfigDao().getAll()
            adapter.updateData(configs)
            btnAddList.visibility = if (configs.size >= 5) View.GONE else View.VISIBLE
        }
    }

    private fun deleteConfig(config: TaskListConfig) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Delete Configuration")
            .setMessage("Are you sure you want to remove '${config.displayName}'?")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    db.taskListConfigDao().delete(config)
                    loadConfigs()
                    sendRefreshRequest("com.neubofy.reality.REFRESH_REQUEST")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showAddEditDialog(config: TaskListConfig?) {
        if (config == null) {
            // New List: Show Choice Dialog
            val options = arrayOf("🆕 Create New List", "📂 Select Existing List")
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add Task List")
                .setItems(options) { _, which ->
                    showTaskInputDialog(isCreate = (which == 0), config = null)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } else {
            // Existing List: Show Edit Dialog (Description only)
            showTaskInputDialog(isCreate = false, config = config)
        }
    }

    private fun showTaskInputDialog(isCreate: Boolean, config: TaskListConfig?) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_edit_task_list, null)
        val etName = dialogView.findViewById<TextInputEditText>(R.id.etListName)
        val etDesc = dialogView.findViewById<TextInputEditText>(R.id.etDescription)
        
        if (config != null) {
            etName.setText(config.displayName)
            etDesc.setText(config.description)
            etName.isEnabled = false // Cannot change name/id of existing list
        }
        
        val title = when {
            config != null -> "Edit Description"
            isCreate -> "Create New List"
            else -> "Select Existing List"
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(dialogView)
            .setPositiveButton("Proceed") { _, _ ->
                val name = etName.text.toString().trim()
                val desc = etDesc.text.toString().trim()
                
                if (name.isEmpty() || desc.isEmpty()) {
                    Toast.makeText(requireContext(), "Name and description are required", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                
                startSyncFlow(isCreate, name, desc, config)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startSyncFlow(isCreate: Boolean, name: String, desc: String, config: TaskListConfig?) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_setup_progress, null)
        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tv_title)
        val tvLog = dialogView.findViewById<android.widget.TextView>(R.id.tv_log)
        val progressBar = dialogView.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.progress_bar)
        
        tvTitle.text = if (isCreate) "Creating Task List..." else "Finding Task List..."
        
        val progressDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()
        progressDialog.show()

        fun log(msg: String) {
            lifecycleScope.launch {
                tvLog.append("$msg\n")
                (tvLog.parent as? android.widget.ScrollView)?.fullScroll(View.FOCUS_DOWN)
            }
        }

        lifecycleScope.launch {
            try {
                log("Starting Google Tasks sync for: $name")
                
                val taskList = if (config != null) {
                    log("✓ Using existing linked list ID: ${config.googleListId}")
                    null // We don't need to find/create if we already have the config
                } else if (isCreate) {
                    log("Creating new list on Google Tasks...")
                    GoogleTasksManager.createTaskList(requireContext(), name)
                } else {
                    log("Searching for list name: $name")
                    GoogleTasksManager.findTaskListByName(requireContext(), name)
                }

                if (config != null || taskList != null) {
                    log("✓ Success! List identified correctly.")
                    log("Updating local database...")
                    
                    val finalConfig = TaskListConfig(
                        id = config?.id ?: 0,
                        googleListId = config?.googleListId ?: taskList!!.id,
                        displayName = config?.displayName ?: taskList!!.title ?: name,
                        description = desc
                    )
                    
                    if (config == null) {
                        db.taskListConfigDao().insert(finalConfig)
                    } else {
                        db.taskListConfigDao().update(finalConfig)
                    }
                    
                    log("✓ Configuration saved successfully.")
                    progressBar.isIndeterminate = false
                    progressBar.progress = 100
                    kotlinx.coroutines.delay(1000)
                    progressDialog.dismiss()
                    loadConfigs()
                    sendRefreshRequest("com.neubofy.reality.REFRESH_REQUEST")
                } else {
                    log("❌ Error: List could not be ${if (isCreate) "created" else "found"}.")
                    progressBar.isIndeterminate = false
                    kotlinx.coroutines.delay(2000)
                    progressDialog.dismiss()
                    Toast.makeText(requireContext(), "Failed to sync with Google Tasks", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                log("❌ Exception: ${e.message}")
                progressBar.isIndeterminate = false
                kotlinx.coroutines.delay(3000)
                progressDialog.dismiss()
                Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val TAG = "AutoSetupDialog"
        fun newInstance() = AutoSetupDialog()
    }
}
