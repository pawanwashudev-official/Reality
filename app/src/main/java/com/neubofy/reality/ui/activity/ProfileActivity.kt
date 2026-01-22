package com.neubofy.reality.ui.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import coil.transform.CircleCropTransformation
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import com.google.api.services.calendar.CalendarScopes
import com.google.api.services.docs.v1.DocsScopes
import com.google.api.services.drive.DriveScopes
import com.google.api.services.tasks.TasksScopes
import com.neubofy.reality.R
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.neubofy.reality.databinding.ActivityProfileBinding
import com.neubofy.reality.google.GoogleAuthManager
import com.neubofy.reality.google.GoogleDocsManager
import com.neubofy.reality.google.GoogleDriveManager
import com.neubofy.reality.google.GoogleTasksManager
import com.neubofy.reality.utils.TerminalLogger
import com.neubofy.reality.utils.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as DriveFile
import com.google.api.services.tasks.Tasks
import com.google.api.services.tasks.model.TaskList
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    
    private val PREF_NAME = "google_connector_prefs"
    private val NIGHTLY_PREFS = "nightly_prefs"
    private val KEY_TASKS_CONNECTED = "tasks_connected"
    private val KEY_DRIVE_CONNECTED = "drive_connected"
    private val KEY_DOCS_CONNECTED = "docs_connected"
    private val KEY_CALENDAR_CONNECTED = "calendar_connected"
    
    private var pendingAction: (() -> Unit)? = null
    private var pendingServiceName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        ThemeManager.applyAccentTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupBackNavigation()
        setupProfileCard()
        setupConnectors()
        setupSetupListeners()
        updateUI()
        loadSetupData()
        setupRefreshReceiver()
        setupInfoButton()
    }

    private fun setupInfoButton() {
        binding.btnInfoProfile.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Need Help?")
                .setMessage("If you encounter any errors or need assistance with setting up your Google connections, please contact support.\n\nYou can also find more information about the app's features and privacy policy in the About page.")
                .setPositiveButton("About Reality") { _, _ ->
                    startActivity(Intent(this, AboutActivity::class.java))
                }
                .setNegativeButton("Close", null)
                .setNeutralButton("Contact Support") { _, _ ->
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = android.net.Uri.parse("mailto:support@neubofy.com")
                        putExtra(Intent.EXTRA_SUBJECT, "Reality App Support")
                    }
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
                    }
                }
                .show()
        }
    }
    
    private val refreshReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            loadSetupData()
        }
    }

    private fun setupRefreshReceiver() {
        LocalBroadcastManager.getInstance(this).registerReceiver(
            refreshReceiver,
            IntentFilter("com.neubofy.reality.REFRESH_REQUEST")
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(refreshReceiver)
    }
    
    override fun onResume() {
        super.onResume()
        updateUI()
        loadSetupData()
    }
    
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_AUTH_TASKS, REQUEST_AUTH_DRIVE, REQUEST_AUTH_DOCS, REQUEST_AUTH_CALENDAR -> {
                if (resultCode == RESULT_OK) {
                    TerminalLogger.log("PROFILE: Permission granted for $pendingServiceName")
                    pendingAction?.invoke()
                } else {
                    TerminalLogger.log("PROFILE: Permission denied for $pendingServiceName")
                }
                pendingAction = null
                pendingServiceName = null
            }
        }
    }
    
    private fun setupProfileCard() {
        binding.btnSignInOut.setOnClickListener {
            if (GoogleAuthManager.isSignedIn(this)) {
                GoogleAuthManager.signOut(this)
                clearAllConnections()
                updateUI()
                TerminalLogger.log("PROFILE: Signed out")
                Toast.makeText(this, "Signed out", Toast.LENGTH_SHORT).show()
            } else {
                performSignIn()
            }
        }
    }
    
    private fun performSignIn() {
        TerminalLogger.log("PROFILE: Starting sign-in...")
        lifecycleScope.launch {
            try {
                val credential = GoogleAuthManager.signIn(this@ProfileActivity)
                if (credential != null) {
                    TerminalLogger.log("PROFILE: Signed in as ${credential.displayName}")
                    Toast.makeText(this@ProfileActivity, "Welcome ${credential.displayName}!", Toast.LENGTH_SHORT).show()
                    updateUI()
                } else {
                    TerminalLogger.log("PROFILE: Sign-in cancelled")
                }
            } catch (e: Exception) {
                TerminalLogger.log("PROFILE: Sign-in error: ${e.message}")
                Toast.makeText(this@ProfileActivity, "Sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun setupConnectors() {
        // Tasks connector - Simple list test
        binding.btnConnectTasks.setOnClickListener {
            connectAndTestService("Tasks", KEY_TASKS_CONNECTED, REQUEST_AUTH_TASKS) {
                val email = GoogleAuthManager.getUserEmail(this@ProfileActivity)
                val credential = GoogleAuthManager.getGoogleAccountCredential(this, email)
                    ?: throw IllegalStateException("Not signed in - email missing")
                
                val tasksService = com.google.api.services.tasks.Tasks.Builder(
                    GoogleAuthManager.getHttpTransport(),
                    GoogleAuthManager.getJsonFactory(),
                    credential
                ).setApplicationName("com.neubofy.reality").build()
                
                val taskLists = tasksService.tasklists().list().execute()
                val count = taskLists.items?.size ?: 0
                "Found $count task list(s)"
            }
        }
        
        // Drive connector - Simple list test
        binding.btnConnectDrive.setOnClickListener {
            connectAndTestService("Drive", KEY_DRIVE_CONNECTED, REQUEST_AUTH_DRIVE) {
                val email = GoogleAuthManager.getUserEmail(this@ProfileActivity)
                val credential = GoogleAuthManager.getGoogleAccountCredential(this, email)
                    ?: throw IllegalStateException("Not signed in - email missing")
                
                val driveService = com.google.api.services.drive.Drive.Builder(
                    GoogleAuthManager.getHttpTransport(),
                    GoogleAuthManager.getJsonFactory(),
                    credential
                ).setApplicationName("com.neubofy.reality").build()
                
                val files = driveService.files().list()
                    .setPageSize(10)
                    .setFields("files(id, name)")
                    .execute()
                val count = files.files?.size ?: 0
                "Found $count file(s) in Drive"
            }
        }
        
        // Docs connector - Create a test doc
        binding.btnConnectDocs.setOnClickListener {
            connectAndTestService("Docs", KEY_DOCS_CONNECTED, REQUEST_AUTH_DOCS) {
                val email = GoogleAuthManager.getUserEmail(this@ProfileActivity)
                val credential = GoogleAuthManager.getGoogleAccountCredential(this, email)
                    ?: throw IllegalStateException("Not signed in - email missing")
                
                val docsService = com.google.api.services.docs.v1.Docs.Builder(
                    GoogleAuthManager.getHttpTransport(),
                    GoogleAuthManager.getJsonFactory(),
                    credential
                ).setApplicationName("com.neubofy.reality").build()
                
                val doc = com.google.api.services.docs.v1.model.Document().apply {
                    title = "Reality Test"
                }
                val createdDoc = docsService.documents().create(doc).execute()
                "Created doc: ${createdDoc.title}"
            }
        }
        
        // Calendar connector - List calendars
        binding.btnConnectCalendar.setOnClickListener {
            connectAndTestService("Calendar", KEY_CALENDAR_CONNECTED, REQUEST_AUTH_CALENDAR) {
                val email = GoogleAuthManager.getUserEmail(this@ProfileActivity)
                val credential = GoogleAuthManager.getGoogleAccountCredential(this, email)
                    ?: throw IllegalStateException("Not signed in - email missing")
                
                val calendarService = com.google.api.services.calendar.Calendar.Builder(
                    GoogleAuthManager.getHttpTransport(),
                    GoogleAuthManager.getJsonFactory(),
                    credential
                ).setApplicationName("com.neubofy.reality").build()
                
                val calendars = calendarService.calendarList().list().execute()
                val count = calendars.items?.size ?: 0
                "Found $count calendar(s)"
            }
        }
    }
    
    private fun connectAndTestService(
        serviceName: String,
        prefKey: String,
        requestCode: Int,
        testBlock: suspend Context.() -> String
    ) {
        try {
            // Check sign-in first
            val isSignedIn = GoogleAuthManager.isSignedIn(this)
            val email = GoogleAuthManager.getUserEmail(this)
            TerminalLogger.log("PROFILE: $serviceName - SignedIn: $isSignedIn, Email: $email")
            
            if (!isSignedIn) {
                TerminalLogger.log("PROFILE: $serviceName - Not signed in!")
                Toast.makeText(this, "Please sign in first", Toast.LENGTH_SHORT).show()
                return
            }
            
            if (email.isNullOrEmpty()) {
                TerminalLogger.log("PROFILE: $serviceName - Email is empty! Sign out and sign in again.")
                Toast.makeText(this, "Email missing. Please sign out and sign in again.", Toast.LENGTH_LONG).show()
                return
            }
            
            TerminalLogger.log("PROFILE: Testing $serviceName connection...")
            
            lifecycleScope.launch {
                try {
                    val result = withContext(Dispatchers.IO) {
                        testBlock(this@ProfileActivity)
                    }
                    setConnected(prefKey, true)
                    TerminalLogger.log("PROFILE: $serviceName SUCCESS - $result")
                    Toast.makeText(this@ProfileActivity, "$serviceName: $result", Toast.LENGTH_LONG).show()
                    updateUI()
                } catch (e: UserRecoverableAuthIOException) {
                    pendingServiceName = serviceName
                    pendingAction = {
                        connectAndTestService(serviceName, prefKey, requestCode, testBlock)
                    }
                    TerminalLogger.log("PROFILE: $serviceName needs permission, showing consent...")
                    startActivityForResult(e.intent, requestCode)
                } catch (t: Throwable) {
                    val errorMessage = t.message ?: t.javaClass.simpleName
                    val stackTrace = android.util.Log.getStackTraceString(t)
                    TerminalLogger.log("PROFILE: $serviceName CRASHED - $errorMessage\n$stackTrace")
                    Toast.makeText(this@ProfileActivity, "$serviceName: $errorMessage", Toast.LENGTH_LONG).show()
                    t.printStackTrace()
                }
            }
        } catch (t: Throwable) {
            val crashMessage = t.message ?: t.javaClass.simpleName
            TerminalLogger.log("PROFILE: $serviceName CRITICAL ERROR - $crashMessage")
            Toast.makeText(this, "$serviceName: $crashMessage", Toast.LENGTH_LONG).show()
            t.printStackTrace()
        }
    }
    
    private fun updateUI() {
        val isSignedIn = GoogleAuthManager.isSignedIn(this)
        
        if (isSignedIn) {
            val name = GoogleAuthManager.getUserName(this) ?: "Google User"
            val email = GoogleAuthManager.getUserEmail(this) ?: ""
            val photoUrl = GoogleAuthManager.getUserPhotoUrl(this)
            
            binding.tvUserName.text = name
            binding.tvUserEmail.text = email
            binding.btnSignInOut.text = "Sign Out"
            binding.btnSignInOut.setIconResource(R.drawable.baseline_logout_24)
            
            try {
                if (!photoUrl.isNullOrEmpty()) {
                    binding.ivProfile.load(photoUrl) {
                        crossfade(true)
                        transformations(CircleCropTransformation())
                        placeholder(R.drawable.baseline_account_circle_24)
                        error(R.drawable.baseline_account_circle_24)
                    }
                    binding.ivProfile.imageTintList = null
                }
            } catch (e: Exception) {
                TerminalLogger.log("PROFILE: Image load error: ${e.message}")
            }
            
            // Show connectors
            binding.tvConnectorsTitle.visibility = View.VISIBLE
            binding.cardTasks.visibility = View.VISIBLE
            binding.cardDrive.visibility = View.VISIBLE
            binding.cardDocs.visibility = View.VISIBLE
            binding.cardCalendar.visibility = View.VISIBLE
            
            // Show Detailed Setup Sections
            binding.tvSetupTitle.visibility = View.VISIBLE
            binding.cardDriveSetup.visibility = View.VISIBLE
            binding.cardTasksSetup.visibility = View.VISIBLE
            binding.btnEraseAllSetup.visibility = View.VISIBLE
            
            // Update connector statuses
            updateConnectorStatus(binding.tvTasksStatus, binding.btnConnectTasks, KEY_TASKS_CONNECTED)
            updateConnectorStatus(binding.tvDriveStatus, binding.btnConnectDrive, KEY_DRIVE_CONNECTED)
            updateConnectorStatus(binding.tvDocsStatus, binding.btnConnectDocs, KEY_DOCS_CONNECTED)
            updateConnectorStatus(binding.tvCalendarStatus, binding.btnConnectCalendar, KEY_CALENDAR_CONNECTED)
            
        } else {
            binding.tvUserName.text = "Not signed in"
            binding.tvUserEmail.text = "Sign in to connect services"
            binding.btnSignInOut.text = "Sign in with Google"
            binding.btnSignInOut.setIconResource(R.drawable.baseline_account_circle_24)
            binding.ivProfile.setImageResource(R.drawable.baseline_account_circle_24)
            binding.ivProfile.imageTintList = androidx.core.content.ContextCompat.getColorStateList(this, R.color.md_theme_primary)
            
            // Hide connectors
            binding.tvConnectorsTitle.visibility = View.GONE
            binding.cardTasks.visibility = View.GONE
            binding.cardDrive.visibility = View.GONE
            binding.cardDocs.visibility = View.GONE
            binding.cardCalendar.visibility = View.GONE

            // Hide Detailed Setup
            binding.tvSetupTitle.visibility = View.GONE
            binding.cardDriveSetup.visibility = View.GONE
            binding.cardTasksSetup.visibility = View.GONE
            binding.btnEraseAllSetup.visibility = View.GONE
        }
    }
    
    private fun updateConnectorStatus(
        statusView: android.widget.TextView,
        button: com.google.android.material.button.MaterialButton,
        prefKey: String
    ) {
        val isConnected = getSharedPreferences(PREF_NAME, MODE_PRIVATE).getBoolean(prefKey, false)
        if (isConnected) {
            statusView.text = "Connected ✓"
            statusView.setTextColor(getColor(R.color.md_theme_primary))
            button.text = "Test"
        } else {
            statusView.text = "Not connected"
            statusView.setTextColor(getColor(R.color.md_theme_onSurfaceVariant))
            button.text = "Connect"
        }
    }
    
    private fun setConnected(prefKey: String, connected: Boolean) {
        getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().putBoolean(prefKey, connected).apply()
    }
    
    private fun clearAllConnections() {
        getSharedPreferences(PREF_NAME, MODE_PRIVATE).edit().clear().apply()
        getSharedPreferences(NIGHTLY_PREFS, MODE_PRIVATE).edit().clear().apply()
        
        // Also clear task list configs from DB
        lifecycleScope.launch(Dispatchers.IO) {
            val db = com.neubofy.reality.data.db.AppDatabase.getDatabase(this@ProfileActivity)
            db.taskListConfigDao().getAll().forEach { db.taskListConfigDao().delete(it) }
            withContext(Dispatchers.Main) {
                loadSetupData()
            }
        }
    }
    
    private fun setupSetupListeners() {
        binding.btnAutoSetupDrive.setOnClickListener { showDriveSetupDialog() }
        binding.cardTasksSetup.setOnClickListener { 
            val dialog = com.neubofy.reality.ui.dialogs.AutoSetupDialog()
            dialog.show(supportFragmentManager, "AutoSetupDialog")
        }
        binding.btnForgetDrive.setOnClickListener { forgetDrive() }
        binding.btnForgetTasks.setOnClickListener { forgetTasks() }
        binding.btnEraseAllSetup.setOnClickListener { eraseAllSetupData() }
    }

    private fun loadSetupData() {
        val prefs = getSharedPreferences(NIGHTLY_PREFS, MODE_PRIVATE)

        // Drive Folder IDs
        val realityId = prefs.getString("reality_folder_id", null)
        val diaryId = prefs.getString("diary_folder_id", null)
        val planId = prefs.getString("plan_folder_id", null)
        val reportId = prefs.getString("report_folder_id", null)
        
        val hasDriveFolders = !realityId.isNullOrEmpty() || !diaryId.isNullOrEmpty() || !planId.isNullOrEmpty() || !reportId.isNullOrEmpty()

        if (hasDriveFolders) {
            val sb = StringBuilder()
            if (!realityId.isNullOrEmpty()) sb.append("Reality: $realityId\n")
            if (!diaryId.isNullOrEmpty()) sb.append("Daily Diary: $diaryId\n")
            if (!planId.isNullOrEmpty()) sb.append("Plan: $planId\n")
            if (!reportId.isNullOrEmpty()) sb.append("Report: $reportId")
            binding.tvSavedDriveInfo.text = sb.toString().trim()
            binding.btnForgetDrive.visibility = View.VISIBLE
        } else {
            binding.tvSavedDriveInfo.text = "No folders configured."
            binding.btnForgetDrive.visibility = View.GONE
        }

        // New Task List Configs (Database driven)
        lifecycleScope.launch(Dispatchers.IO) {
            val configs = com.neubofy.reality.data.db.AppDatabase.getDatabase(this@ProfileActivity)
                .taskListConfigDao().getAll()
            
            withContext(Dispatchers.Main) {
                if (configs.isNotEmpty()) {
                    val sb = StringBuilder()
                    configs.forEach { config ->
                        sb.append("• ${config.displayName}\n")
                    }
                    binding.tvSavedTasksInfo.text = sb.toString().trim()
                    binding.btnForgetTasks.visibility = View.VISIBLE
                } else {
                    binding.tvSavedTasksInfo.text = "No task lists configured."
                    binding.btnForgetTasks.visibility = View.GONE
                }
            }
        }
    }

    private fun forgetDrive() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Forget Google Drive Setup?")
            .setMessage("This will remove the saved folder IDs. Your files on Google Drive will NOT be deleted.")
            .setPositiveButton("Forget") { _, _ ->
                getSharedPreferences(NIGHTLY_PREFS, MODE_PRIVATE).edit()
                    .remove("reality_folder_id")
                    .remove("diary_folder_id")
                    .remove("plan_folder_id")
                    .remove("report_folder_id")
                    .apply()
                loadSetupData()
                Toast.makeText(this, "Drive setup cleared", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun forgetTasks() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Forget Google Tasks Setup?")
            .setMessage("This will remove the saved task list IDs.")
            .setPositiveButton("Forget") { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val db = com.neubofy.reality.data.db.AppDatabase.getDatabase(this@ProfileActivity)
                    db.taskListConfigDao().getAll().forEach { db.taskListConfigDao().delete(it) }
                    withContext(Dispatchers.Main) {
                        loadSetupData()
                        Toast.makeText(this@ProfileActivity, "Tasks setup cleared", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun eraseAllSetupData() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Erase All Protocol Setup?")
            .setMessage("This will clear all Nightly Protocol configurations, including schedules, prompts, and Google IDs.")
            .setPositiveButton("Erase All") { _, _ ->
                getSharedPreferences(NIGHTLY_PREFS, MODE_PRIVATE).edit().clear().apply()
                loadSetupData()
                Toast.makeText(this, "All setup data erased", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ============ DRIVE SETUP DIALOG WORKFLOW ============
    
    private fun showDriveSetupDialog() {
        if (!GoogleAuthManager.isSignedIn(this)) {
            Toast.makeText(this, "Please sign in with Google first", Toast.LENGTH_LONG).show()
            return
        }

        val options = arrayOf(
            "🆕 Create New Folders",
            "📂 Folder Already Exists"
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("Drive Setup")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> createNewDriveFolders()
                    1 -> showExistingFolderOptions()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showExistingFolderOptions() {
        val options = arrayOf(
            "📝 Enter URLs Manually",
            "🔍 Auto Find Folders"
        )

        MaterialAlertDialogBuilder(this)
            .setTitle("Existing Folders")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showManualUrlDialog()
                    1 -> autoFindDriveFolders()
                }
            }
            .setNegativeButton("Back") { _, _ -> showDriveSetupDialog() }
            .show()
    }

    private fun createNewDriveFolders() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_setup_progress, null)
        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tv_title)
        val tvLog = dialogView.findViewById<android.widget.TextView>(R.id.tv_log)
        val progressBar = dialogView.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.progress_bar)
        
        tvTitle.text = "Creating Drive Folders..."
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.show()
        
        fun log(msg: String) {
            runOnUiThread {
                tvLog.append("$msg\n")
                (tvLog.parent as? android.widget.ScrollView)?.fullScroll(View.FOCUS_DOWN)
            }
        }

        lifecycleScope.launch {
            try {
                log("Getting credentials...")
                val results = withContext(Dispatchers.IO) {
                    val credential = GoogleAuthManager.getGoogleAccountCredential(this@ProfileActivity)
                        ?: throw Exception("Failed to get Google credential")

                    val driveService = Drive.Builder(
                        GoogleAuthManager.getHttpTransport(),
                        GoogleAuthManager.getJsonFactory(),
                        credential
                    ).setApplicationName("Reality").build()

                    log("Creating Reality folder...")
                    val realityFolderId = findOrCreateFolder(driveService, "Reality", null)
                    log("✓ Reality folder: $realityFolderId")
                    
                    log("Creating Reality Diary folder...")
                    val diaryFolderId = findOrCreateFolder(driveService, "Reality Diary", realityFolderId)
                    log("✓ Reality Diary: $diaryFolderId")
                    
                    log("Creating Reality Plan folder...")
                    val planFolderId = findOrCreateFolder(driveService, "Reality Plan", realityFolderId)
                    log("✓ Reality Plan: $planFolderId")
                    
                    log("Creating Reality Report folder...")
                    val reportFolderId = findOrCreateFolder(driveService, "Reality Report", realityFolderId)
                    log("✓ Reality Report: $reportFolderId")

                    listOf(realityFolderId, diaryFolderId, planFolderId, reportFolderId)
                }

                log("Saving configuration...")
                getSharedPreferences(NIGHTLY_PREFS, MODE_PRIVATE).edit()
                    .putString("reality_folder_id", results[0])
                    .putString("diary_folder_id", results[1])
                    .putString("plan_folder_id", results[2])
                    .putString("report_folder_id", results[3])
                    .apply()

                log("✅ Setup complete!")
                progressBar.isIndeterminate = false
                progressBar.progress = 100
                
                kotlinx.coroutines.delay(1000)
                dialog.dismiss()
                loadSetupData()
                Toast.makeText(this@ProfileActivity, "Folders created successfully!", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                log("❌ Error: ${e.message}")
                e.printStackTrace()
                progressBar.isIndeterminate = false
                kotlinx.coroutines.delay(2000)
                dialog.dismiss()
                Toast.makeText(this@ProfileActivity, "Failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showManualUrlDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_drive_urls, null)
        val etReality = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_reality_url)
        val etDiary = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_diary_url)
        val etPlan = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_plan_url)
        val etReport = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_report_url)
        val btnCancel = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_cancel)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btn_save)

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        btnCancel.setOnClickListener { dialog.dismiss() }
        
        btnSave.setOnClickListener {
            val realityId = extractFolderIdFromUrl(etReality.text.toString())
            val diaryId = extractFolderIdFromUrl(etDiary.text.toString())
            val planId = extractFolderIdFromUrl(etPlan.text.toString())
            val reportId = extractFolderIdFromUrl(etReport.text.toString())

            if (realityId == null && diaryId == null && planId == null && reportId == null) {
                Toast.makeText(this, "Please enter at least one valid folder URL", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Verify folders exist on Drive
            verifyAndSaveFolderIds(realityId, diaryId, planId, reportId, dialog)
        }

        dialog.show()
    }

    private fun extractFolderIdFromUrl(url: String): String? {
        if (url.isBlank()) return null
        
        // Pattern 1: https://drive.google.com/drive/folders/FOLDER_ID
        // Pattern 2: https://drive.google.com/drive/u/0/folders/FOLDER_ID
        val regex = Regex("""/folders/([a-zA-Z0-9_-]+)""")
        val match = regex.find(url)
        if (match != null) return match.groupValues[1]
        
        // If URL is just the ID itself
        if (url.matches(Regex("""^[a-zA-Z0-9_-]{20,}$"""))) return url
        
        return null
    }

    private fun verifyAndSaveFolderIds(realityId: String?, diaryId: String?, planId: String?, reportId: String?, dialog: androidx.appcompat.app.AlertDialog) {
        lifecycleScope.launch {
            try {
                Toast.makeText(this@ProfileActivity, "Verifying folders...", Toast.LENGTH_SHORT).show()
                
                withContext(Dispatchers.IO) {
                    val credential = GoogleAuthManager.getGoogleAccountCredential(this@ProfileActivity)
                        ?: throw Exception("Failed to get credential")
                    
                    val driveService = Drive.Builder(
                        GoogleAuthManager.getHttpTransport(),
                        GoogleAuthManager.getJsonFactory(),
                        credential
                    ).setApplicationName("Reality").build()

                    // Verify each folder exists
                    listOf(realityId, diaryId, planId, reportId).filterNotNull().forEach { id ->
                        val file = driveService.files().get(id).setFields("id,name,mimeType").execute()
                        if (file.mimeType != "application/vnd.google-apps.folder") {
                            throw Exception("$id is not a folder")
                        }
                    }
                }

                // Save
                val prefs = getSharedPreferences(NIGHTLY_PREFS, MODE_PRIVATE).edit()
                realityId?.let { prefs.putString("reality_folder_id", it) }
                diaryId?.let { prefs.putString("diary_folder_id", it) }
                planId?.let { prefs.putString("plan_folder_id", it) }
                reportId?.let { prefs.putString("report_folder_id", it) }
                prefs.apply()

                dialog.dismiss()
                loadSetupData()
                Toast.makeText(this@ProfileActivity, "Folders verified and saved!", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "Verification failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun autoFindDriveFolders() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_setup_progress, null)
        val tvTitle = dialogView.findViewById<android.widget.TextView>(R.id.tv_title)
        val tvLog = dialogView.findViewById<android.widget.TextView>(R.id.tv_log)
        val progressBar = dialogView.findViewById<com.google.android.material.progressindicator.LinearProgressIndicator>(R.id.progress_bar)
        
        tvTitle.text = "Finding Reality Folders..."
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        dialog.show()
        
        fun log(msg: String) {
            runOnUiThread {
                tvLog.append("$msg\n")
                (tvLog.parent as? android.widget.ScrollView)?.fullScroll(View.FOCUS_DOWN)
            }
        }

        lifecycleScope.launch {
            try {
                log("Searching for Reality folders...")
                
                var realityId: String? = null
                var diaryId: String? = null
                var planId: String? = null
                var reportId: String? = null

                withContext(Dispatchers.IO) {
                    val credential = GoogleAuthManager.getGoogleAccountCredential(this@ProfileActivity)
                        ?: throw Exception("Failed to get credential")

                    val driveService = Drive.Builder(
                        GoogleAuthManager.getHttpTransport(),
                        GoogleAuthManager.getJsonFactory(),
                        credential
                    ).setApplicationName("Reality").build()

                    // Find Reality root folder
                    log("Looking for 'Reality' folder in root...")
                    val rootFolders = driveService.files().list()
                        .setQ("mimeType='application/vnd.google-apps.folder' and name='Reality' and trashed=false and 'root' in parents")
                        .setFields("files(id, name)")
                        .execute()
                    
                    if (rootFolders.files.isNotEmpty()) {
                        realityId = rootFolders.files[0].id
                        log("✓ Found Reality: $realityId")
                        
                        // Find sub-folders
                        val subFolders = driveService.files().list()
                            .setQ("mimeType='application/vnd.google-apps.folder' and '$realityId' in parents and trashed=false")
                            .setFields("files(id, name)")
                            .execute()
                        
                        subFolders.files?.forEach { folder ->
                            when {
                                folder.name.contains("Diary", ignoreCase = true) -> {
                                    diaryId = folder.id
                                    log("✓ Found Diary: ${folder.name}")
                                }
                                folder.name.contains("Plan", ignoreCase = true) -> {
                                    planId = folder.id
                                    log("✓ Found Plan: ${folder.name}")
                                }
                                folder.name.contains("Report", ignoreCase = true) -> {
                                    reportId = folder.id
                                    log("✓ Found Report: ${folder.name}")
                                }
                            }
                        }
                    } else {
                        log("⚠ No 'Reality' folder found in root")
                    }
                }

                if (realityId != null) {
                    log("Saving configuration...")
                    val prefs = getSharedPreferences(NIGHTLY_PREFS, MODE_PRIVATE).edit()
                    realityId?.let { prefs.putString("reality_folder_id", it) }
                    diaryId?.let { prefs.putString("diary_folder_id", it) }
                    planId?.let { prefs.putString("plan_folder_id", it) }
                    reportId?.let { prefs.putString("report_folder_id", it) }
                    prefs.apply()
                    
                    log("✅ Setup complete!")
                    progressBar.isIndeterminate = false
                    progressBar.progress = 100
                    
                    kotlinx.coroutines.delay(1000)
                    dialog.dismiss()
                    loadSetupData()
                    Toast.makeText(this@ProfileActivity, "Folders found and saved!", Toast.LENGTH_SHORT).show()
                } else {
                    log("❌ Could not find Reality folders")
                    log("Please create them first or enter URLs manually")
                    progressBar.isIndeterminate = false
                    kotlinx.coroutines.delay(3000)
                    dialog.dismiss()
                }

            } catch (e: Exception) {
                log("❌ Error: ${e.message}")
                progressBar.isIndeterminate = false
                kotlinx.coroutines.delay(2000)
                dialog.dismiss()
                Toast.makeText(this@ProfileActivity, "Search failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun findOrCreateFolder(driveService: Drive, name: String, parentId: String?): String {
        val query = if (parentId == null) {
            "mimeType='application/vnd.google-apps.folder' and name='$name' and trashed=false and 'root' in parents"
        } else {
            "mimeType='application/vnd.google-apps.folder' and name='$name' and trashed=false and '$parentId' in parents"
        }

        val result = driveService.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()

        if (result.files.isNotEmpty()) return result.files[0].id

        val folderMetadata = DriveFile().apply {
            this.name = name
            this.mimeType = "application/vnd.google-apps.folder"
            if (parentId != null) this.parents = listOf(parentId)
        }

        return driveService.files().create(folderMetadata).setFields("id").execute().id
    }

    private fun setupBackNavigation() {
        binding.btnBack.setOnClickListener {
            finish()
        }
    }
    
    companion object {
        private const val REQUEST_AUTH_TASKS = 1001
        private const val REQUEST_AUTH_DRIVE = 1002
        private const val REQUEST_AUTH_DOCS = 1003
        private const val REQUEST_AUTH_CALENDAR = 1004
    }
}
