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

class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    
    private val PREF_NAME = "google_connector_prefs"
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
        
        setupBottomNav()
        setupProfileCard()
        setupConnectors()
        updateUI()
    }
    
    override fun onResume() {
        super.onResume()
        updateUI()
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
    }
    
    private fun setupBottomNav() {
        binding.bottomNavigation.selectedItemId = R.id.nav_profile
        
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    overridePendingTransition(0, 0)
                    finish()
                    true
                }
                R.id.nav_tasks -> {
                    val launchIntent = packageManager.getLaunchIntentForPackage("com.google.android.apps.tasks")
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                    } else {
                        Toast.makeText(this, "Google Tasks not installed", Toast.LENGTH_SHORT).show()
                    }
                    false
                }
                R.id.nav_calendar -> {
                    val launchIntent = packageManager.getLaunchIntentForPackage("com.google.android.calendar")
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                    } else {
                        Toast.makeText(this, "Google Calendar not installed", Toast.LENGTH_SHORT).show()
                    }
                    false
                }
                R.id.nav_profile -> true
                else -> false
            }
        }
    }
    
    companion object {
        private const val REQUEST_AUTH_TASKS = 1001
        private const val REQUEST_AUTH_DRIVE = 1002
        private const val REQUEST_AUTH_DOCS = 1003
        private const val REQUEST_AUTH_CALENDAR = 1004
    }
}
