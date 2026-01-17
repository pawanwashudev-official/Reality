package com.neubofy.reality.ui.activity

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.TasksScopes
import com.google.api.services.tasks.model.TaskList
import com.neubofy.reality.databinding.ActivityNightlySettingsBinding
import com.neubofy.reality.google.GoogleAuthManager
import com.neubofy.reality.utils.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

class NightlySettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNightlySettingsBinding
    private val PREFS_NAME = "nightly_prefs"

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityNightlySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        setupListeners()
        loadSavedData()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.header.setPadding(
                binding.header.paddingLeft,
                systemBars.top + 16,
                binding.header.paddingRight,
                binding.header.paddingBottom
            )
            insets
        }
    }

    private fun setupListeners() {
        binding.btnBack.setOnClickListener { finish() }

        // Auto Setup Folders (Drive)
        binding.cardAutoSetup.setOnClickListener {
            autoSetupDriveFolders()
        }

        // Save Manual Folder Links
        binding.btnSaveFolders.setOnClickListener {
            saveManualFolderLinks()
        }

        // Auto Setup Task Lists
        binding.cardAutoTasks.setOnClickListener {
            autoSetupTaskLists()
        }

        // Find Task List IDs by Name
        binding.btnFindTaskLists.setOnClickListener {
            findTaskListsByName()
        }

        // Erase All Setup Data
        binding.btnEraseSetup.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Erase Setup Data?")
                .setMessage("This will remove all saved folder IDs and task list IDs. You will need to set up again.")
                .setPositiveButton("Erase") { _, _ ->
                    eraseAllSetupData()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Verify All Connections
        binding.btnVerifySetup.setOnClickListener {
            verifyAllConnections()
        }
    }

    private fun loadSavedData() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        // Load folder IDs
        val realityId = prefs.getString("reality_folder_id", null)
        val diaryId = prefs.getString("diary_folder_id", null)
        val reportId = prefs.getString("report_folder_id", null)
        
        val hasDriveFolders = !realityId.isNullOrEmpty() || !diaryId.isNullOrEmpty() || !reportId.isNullOrEmpty()

        if (hasDriveFolders) {
            // Hide setup forms, show saved IDs
            binding.cardAutoSetup.visibility = View.GONE
            binding.cardManualSetup.visibility = View.GONE
            binding.cardSavedFolders.visibility = View.VISIBLE
            
            val sb = StringBuilder()
            if (!realityId.isNullOrEmpty()) sb.append("Reality: $realityId\n")
            if (!diaryId.isNullOrEmpty()) sb.append("Daily Diary: $diaryId\n")
            if (!reportId.isNullOrEmpty()) sb.append("Report: $reportId")
            binding.tvSavedFolderIds.text = sb.toString().trim()
        } else {
            // Show setup forms, hide saved IDs
            binding.cardAutoSetup.visibility = View.VISIBLE
            binding.cardManualSetup.visibility = View.VISIBLE
            binding.cardSavedFolders.visibility = View.GONE
        }

        // Load task list IDs
        val taskList1Id = prefs.getString("task_list_1_id", null)
        val taskList1Name = prefs.getString("task_list_1_name", null)
        val taskList2Id = prefs.getString("task_list_2_id", null)
        val taskList2Name = prefs.getString("task_list_2_name", null)
        
        val hasTaskLists = !taskList1Id.isNullOrEmpty() || !taskList2Id.isNullOrEmpty()

        if (hasTaskLists) {
            // Hide setup forms, show saved IDs
            binding.cardAutoTasks.visibility = View.GONE
            binding.cardManualTasks.visibility = View.GONE
            binding.cardSavedTasks.visibility = View.VISIBLE
            
            val sb = StringBuilder()
            if (!taskList1Id.isNullOrEmpty()) sb.append("${taskList1Name ?: "List 1"}: $taskList1Id\n")
            if (!taskList2Id.isNullOrEmpty()) sb.append("${taskList2Name ?: "List 2"}: $taskList2Id")
            binding.tvSavedTaskIds.text = sb.toString().trim()
        } else {
            // Show setup forms, hide saved IDs
            binding.cardAutoTasks.visibility = View.VISIBLE
            binding.cardManualTasks.visibility = View.VISIBLE
            binding.cardSavedTasks.visibility = View.GONE
        }
    }

    private fun extractFolderIdFromUrl(url: String): String? {
        // Patterns:
        // https://drive.google.com/drive/folders/FOLDER_ID
        // https://drive.google.com/drive/u/0/folders/FOLDER_ID
        val pattern = Pattern.compile("folders/([a-zA-Z0-9_-]+)")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    private fun saveManualFolderLinks() {
        val realityUrl = binding.etRealityFolder.text.toString().trim()
        val diaryUrl = binding.etDiaryFolder.text.toString().trim()
        val reportUrl = binding.etReportFolder.text.toString().trim()

        val realityId = if (realityUrl.isNotEmpty()) extractFolderIdFromUrl(realityUrl) ?: realityUrl else null
        val diaryId = if (diaryUrl.isNotEmpty()) extractFolderIdFromUrl(diaryUrl) ?: diaryUrl else null
        val reportId = if (reportUrl.isNotEmpty()) extractFolderIdFromUrl(reportUrl) ?: reportUrl else null

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
        if (realityId != null) prefs.putString("reality_folder_id", realityId)
        if (diaryId != null) prefs.putString("diary_folder_id", diaryId)
        if (reportId != null) prefs.putString("report_folder_id", reportId)
        prefs.apply()

        Toast.makeText(this, "Folder links saved!", Toast.LENGTH_SHORT).show()
        loadSavedData()
        
        // Clear inputs
        binding.etRealityFolder.text?.clear()
        binding.etDiaryFolder.text?.clear()
        binding.etReportFolder.text?.clear()
    }

    private fun autoSetupDriveFolders() {
        if (!GoogleAuthManager.isSignedIn(this)) {
            Toast.makeText(this, "Please sign in with Google first (Settings > Account)", Toast.LENGTH_LONG).show()
            return
        }
        
        val accountEmail = GoogleAuthManager.getUserEmail(this)

        lifecycleScope.launch {
            try {
                Toast.makeText(this@NightlySettingsActivity, "Creating folders...", Toast.LENGTH_SHORT).show()

                val (realityId, diaryId, reportId) = withContext(Dispatchers.IO) {
                    val credential = GoogleAuthManager.getGoogleAccountCredential(this@NightlySettingsActivity)
                    if (credential == null) {
                        throw Exception("Failed to get Google credential")
                    }

                    val driveService = Drive.Builder(
                        GoogleAuthManager.getHttpTransport(),
                        GoogleAuthManager.getJsonFactory(),
                        credential
                    ).setApplicationName("Reality").build()

                    // Create or find Reality folder
                    val realityFolderId = findOrCreateFolder(driveService, "Reality", null)

                    // Create Daily Diary folder inside Reality
                    val diaryFolderId = findOrCreateFolder(driveService, "Daily Diary", realityFolderId)

                    // Create Report of the Day folder inside Reality
                    val reportFolderId = findOrCreateFolder(driveService, "Report of the Day", realityFolderId)

                    Triple(realityFolderId, diaryFolderId, reportFolderId)
                }

                // Save to prefs
                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                prefs.putString("reality_folder_id", realityId)
                prefs.putString("diary_folder_id", diaryId)
                prefs.putString("report_folder_id", reportId)
                prefs.apply()

                Toast.makeText(this@NightlySettingsActivity, "Folders created successfully!", Toast.LENGTH_SHORT).show()
                loadSavedData()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@NightlySettingsActivity,
                    "Failed to create folders: ${e.message}\n\nPlease create manually and paste links.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun findOrCreateFolder(driveService: Drive, name: String, parentId: String?): String {
        // First, search ANYWHERE for an existing folder with this name (for Reality folder)
        // For subfolders, we specifically look within the parent
        val query = if (parentId == null) {
            // For root-level folders like "Reality", search anywhere first
            "mimeType='application/vnd.google-apps.folder' and name='$name' and trashed=false"
        } else {
            // For subfolders, search specifically within the parent
            "mimeType='application/vnd.google-apps.folder' and name='$name' and trashed=false and '$parentId' in parents"
        }

        val result = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        if (result.files.isNotEmpty()) {
            // Found existing folder, use it
            return result.files[0].id
        }

        // Create new folder (only if not found anywhere)
        val folderMetadata = DriveFile().apply {
            this.name = name
            this.mimeType = "application/vnd.google-apps.folder"
            if (parentId != null) {
                this.parents = listOf(parentId)
            }
        }

        val folder = driveService.files().create(folderMetadata)
            .setFields("id")
            .execute()

        return folder.id
    }

    private fun autoSetupTaskLists() {
        if (!GoogleAuthManager.isSignedIn(this)) {
            Toast.makeText(this, "Please sign in with Google first (Settings > Account)", Toast.LENGTH_LONG).show()
            return
        }
        
        val accountEmail = GoogleAuthManager.getUserEmail(this)

        lifecycleScope.launch {
            try {
                Toast.makeText(this@NightlySettingsActivity, "Setting up task lists...", Toast.LENGTH_SHORT).show()

                val (list1Id, list1Name, list2Id, list2Name) = withContext(Dispatchers.IO) {
                    val credential = GoogleAuthManager.getGoogleAccountCredential(this@NightlySettingsActivity)
                    if (credential == null) {
                        throw Exception("Failed to get Google credential")
                    }

                    val tasksService = Tasks.Builder(
                        GoogleAuthManager.getHttpTransport(),
                        GoogleAuthManager.getJsonFactory(),
                        credential
                    ).setApplicationName("Reality").build()

                    // Get all task lists
                    val taskLists = tasksService.tasklists().list().execute().items ?: emptyList()

                    // Try to find or create "Reality Daily" and "Reality Tomorrow"
                    val dailyList = taskLists.find { it.title == "Reality Daily" }
                        ?: createTaskList(tasksService, "Reality Daily")

                    val tomorrowList = taskLists.find { it.title == "Reality Tomorrow" }
                        ?: createTaskList(tasksService, "Reality Tomorrow")

                    listOf(dailyList.id, dailyList.title, tomorrowList.id, tomorrowList.title)
                }

                // Save to prefs
                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                prefs.putString("task_list_1_id", list1Id)
                prefs.putString("task_list_1_name", list1Name)
                prefs.putString("task_list_2_id", list2Id)
                prefs.putString("task_list_2_name", list2Name)
                prefs.apply()

                Toast.makeText(this@NightlySettingsActivity, "Task lists configured!", Toast.LENGTH_SHORT).show()
                loadSavedData()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(
                    this@NightlySettingsActivity,
                    "Failed to setup task lists: ${e.message}\n\nPlease create manually and enter names below.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun createTaskList(tasksService: Tasks, name: String): TaskList {
        val newList = TaskList().setTitle(name)
        return tasksService.tasklists().insert(newList).execute()
    }

    private fun findTaskListsByName() {
        val name1 = binding.etTaskList1.text.toString().trim()
        val name2 = binding.etTaskList2.text.toString().trim()

        if (name1.isEmpty() && name2.isEmpty()) {
            Toast.makeText(this, "Please enter at least one task list name", Toast.LENGTH_SHORT).show()
            return
        }

        if (!GoogleAuthManager.isSignedIn(this)) {
            Toast.makeText(this, "Please sign in with Google first (Settings > Account)", Toast.LENGTH_LONG).show()
            return
        }
        
        val accountEmail = GoogleAuthManager.getUserEmail(this)

        lifecycleScope.launch {
            try {
                Toast.makeText(this@NightlySettingsActivity, "Finding task lists...", Toast.LENGTH_SHORT).show()

                val result = withContext(Dispatchers.IO) {
                    val credential = GoogleAuthManager.getGoogleAccountCredential(this@NightlySettingsActivity)
                    if (credential == null) {
                        throw Exception("Failed to get Google credential")
                    }

                    val tasksService = Tasks.Builder(
                        GoogleAuthManager.getHttpTransport(),
                        GoogleAuthManager.getJsonFactory(),
                        credential
                    ).setApplicationName("Reality").build()

                    val taskLists = tasksService.tasklists().list().execute().items ?: emptyList()

                    val list1 = if (name1.isNotEmpty()) taskLists.find { it.title.equals(name1, ignoreCase = true) } else null
                    val list2 = if (name2.isNotEmpty()) taskLists.find { it.title.equals(name2, ignoreCase = true) } else null

                    Pair(list1, list2)
                }

                val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                var foundCount = 0

                result.first?.let {
                    prefs.putString("task_list_1_id", it.id)
                    prefs.putString("task_list_1_name", it.title)
                    foundCount++
                }

                result.second?.let {
                    prefs.putString("task_list_2_id", it.id)
                    prefs.putString("task_list_2_name", it.title)
                    foundCount++
                }

                prefs.apply()

                if (foundCount > 0) {
                    Toast.makeText(this@NightlySettingsActivity, "Found $foundCount task list(s)!", Toast.LENGTH_SHORT).show()
                    loadSavedData()
                    binding.etTaskList1.text?.clear()
                    binding.etTaskList2.text?.clear()
                } else {
                    Toast.makeText(this@NightlySettingsActivity, "No matching task lists found. Check names.", Toast.LENGTH_LONG).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@NightlySettingsActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun eraseAllSetupData() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
        prefs.clear()
        prefs.apply()

        Toast.makeText(this, "All setup data erased.", Toast.LENGTH_SHORT).show()
        loadSavedData()
    }

    private fun verifyAllConnections() {
        if (!GoogleAuthManager.isSignedIn(this)) {
            Toast.makeText(this, "Please sign in with Google first", Toast.LENGTH_LONG).show()
            return
        }

        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val realityId = prefs.getString("reality_folder_id", null)
        val diaryId = prefs.getString("diary_folder_id", null)
        val reportId = prefs.getString("report_folder_id", null)
        val taskList1Id = prefs.getString("task_list_1_id", null)
        val taskList2Id = prefs.getString("task_list_2_id", null)

        if (realityId.isNullOrEmpty() && diaryId.isNullOrEmpty() && taskList1Id.isNullOrEmpty()) {
            Toast.makeText(this, "No setup data to verify. Please set up first.", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                Toast.makeText(this@NightlySettingsActivity, "Verifying connections...", Toast.LENGTH_SHORT).show()

                val results = withContext(Dispatchers.IO) {
                    val credential = GoogleAuthManager.getGoogleAccountCredential(this@NightlySettingsActivity)
                    if (credential == null) {
                        throw Exception("Failed to get Google credential")
                    }

                    val results = mutableListOf<String>()

                    // Verify Drive folders
                    if (!realityId.isNullOrEmpty() || !diaryId.isNullOrEmpty() || !reportId.isNullOrEmpty()) {
                        val driveService = Drive.Builder(
                            GoogleAuthManager.getHttpTransport(),
                            GoogleAuthManager.getJsonFactory(),
                            credential
                        ).setApplicationName("Reality").build()

                        realityId?.let {
                            try {
                                val file = driveService.files().get(it).setFields("id, name").execute()
                                results.add("✓ Reality folder: ${file.name}")
                            } catch (e: Exception) {
                                results.add("✗ Reality folder: Not accessible")
                            }
                        }

                        diaryId?.let {
                            try {
                                val file = driveService.files().get(it).setFields("id, name").execute()
                                results.add("✓ Diary folder: ${file.name}")
                            } catch (e: Exception) {
                                results.add("✗ Diary folder: Not accessible")
                            }
                        }

                        reportId?.let {
                            try {
                                val file = driveService.files().get(it).setFields("id, name").execute()
                                results.add("✓ Report folder: ${file.name}")
                            } catch (e: Exception) {
                                results.add("✗ Report folder: Not accessible")
                            }
                        }
                    }

                    // Verify Task Lists
                    if (!taskList1Id.isNullOrEmpty() || !taskList2Id.isNullOrEmpty()) {
                        val tasksService = Tasks.Builder(
                            GoogleAuthManager.getHttpTransport(),
                            GoogleAuthManager.getJsonFactory(),
                            credential
                        ).setApplicationName("Reality").build()

                        taskList1Id?.let {
                            try {
                                val list = tasksService.tasklists().get(it).execute()
                                results.add("✓ Task List 1: ${list.title}")
                            } catch (e: Exception) {
                                results.add("✗ Task List 1: Not accessible")
                            }
                        }

                        taskList2Id?.let {
                            try {
                                val list = tasksService.tasklists().get(it).execute()
                                results.add("✓ Task List 2: ${list.title}")
                            } catch (e: Exception) {
                                results.add("✗ Task List 2: Not accessible")
                            }
                        }
                    }

                    results
                }

                // Show results in dialog
                MaterialAlertDialogBuilder(this@NightlySettingsActivity)
                    .setTitle("Verification Results")
                    .setMessage(results.joinToString("\n"))
                    .setPositiveButton("OK", null)
                    .show()

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@NightlySettingsActivity, "Verification failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
