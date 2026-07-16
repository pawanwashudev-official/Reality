package com.neubofy.reality.ui.activity

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.ui.base.BaseActivity
import com.neubofy.reality.databinding.ActivityAboutBinding
import com.neubofy.reality.utils.UpdateManager
import android.view.View
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.EditText
import android.widget.LinearLayout
import android.net.Uri
import java.net.URLEncoder


import androidx.lifecycle.lifecycleScope
import io.noties.markwon.Markwon
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.coil.CoilImagesPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class AboutActivity : BaseActivity() {

    private lateinit var binding: ActivityAboutBinding


    companion object {
        const val ABOUT_MD_URL = "https://raw.githubusercontent.com/pawanwashudev-official/Reality/main/ABOUT.md"
        const val GITHUB_PROFILE = "https://github.com/pawanwashudev-official"
        const val GITHUB_REPO = "https://github.com/pawanwashudev-official/Reality"
        const val TELEGRAM = "https://t.me/pawanwashudev"
        const val WHATSAPP = "https://wa.me/pawanwashudev"
        const val ARRATAI = "https://arratai.com/@pawanwashudev"
        const val INSTAGRAM = "https://instagram.com/pawanwashudev"
        const val LINKEDIN = "https://linkedin.com/in/pawanwashudev"
        const val EMAIL = "support@neubofy.in"
        const val PRIVACY_POLICY = "https://reality.neubofy.in/privacypolicy"
        const val WEBSITE = "https://reality.neubofy.in"
        const val NEUBOFY_TELEGRAM = "https://t.me/neubofy"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = binding.includeHeader.toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        toolbar.setNavigationOnClickListener { finish() }

        // Version
        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            val version = pInfo.versionName
            binding.tvVersion.text = "Version $version"
        } catch (e: PackageManager.NameNotFoundException) {
            com.neubofy.reality.utils.TerminalLogger.log("ERROR: ${e.message}")
            binding.tvVersion.text = "Version Unknown"
        }

        // Social Links
        binding.btnGithub.setOnClickListener { openUrl(GITHUB_PROFILE) }
        
        // Website
        binding.btnWebsite.setOnClickListener { openUrl(WEBSITE) }

        // Telegram
        binding.btnTelegram.setOnClickListener { openUrl(TELEGRAM) }

        // WhatsApp
        binding.btnWhatsapp.setOnClickListener { openUrl(WHATSAPP) }

        // Instagram
        binding.btnInstagram.setOnClickListener { openUrl(INSTAGRAM) }

        // LinkedIn
        binding.btnLinkedin.setOnClickListener { openUrl(LINKEDIN) }

        // Neubofy Telegram
        // binding.btnNeubofyTelegram.setOnClickListener { openUrl(NEUBOFY_TELEGRAM) }

        // Arratai
        // binding.btnArratai.setOnClickListener { openUrl(ARRATAI) }

        // Email / Contact
        binding.btnContact.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:$EMAIL")
                putExtra(Intent.EXTRA_SUBJECT, "Reality App - Support Request")
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                // Fallback: copy email to clipboard
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Email", EMAIL)
                clipboard.setPrimaryClip(clip)
                android.widget.Toast.makeText(this, "Email copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
            }
        }

        // Source Code
        binding.btnSourceCode.setOnClickListener { openUrl(GITHUB_REPO) }

        // Privacy Policy
        binding.btnPrivacyPolicy.setOnClickListener { openUrl(PRIVACY_POLICY) }
        


        // Update Check
        binding.cardUpdate.setOnClickListener {
            binding.progressUpdate.visibility = View.VISIBLE
            binding.tvUpdateStatus.text = "Checking for updates..."
            
            UpdateManager.checkForUpdates(this, silent = false, isBeta = false, onCheckComplete = {
                // This callback only runs if NO update is found (silent=false)
                runOnUiThread {
                    binding.progressUpdate.visibility = View.GONE
                    binding.tvUpdateStatus.text = "Keep Reality at its best"

                }
            })
        }


        // Beta Update Check
        binding.cardBetaUpdate.setOnClickListener {
            binding.progressBetaUpdate.visibility = View.VISIBLE
            binding.tvBetaUpdateStatus.text = "Checking for beta updates..."

            UpdateManager.checkForUpdates(this, silent = false, isBeta = true, onCheckComplete = {
                runOnUiThread {
                    binding.progressBetaUpdate.visibility = View.GONE
                    binding.tvBetaUpdateStatus.text = "Get early access features"

                }
            })
        }

        // Raise Issue
        binding.cardRaiseIssue.setOnClickListener {
            val context = this
            val layout = LinearLayout(context)
            layout.orientation = LinearLayout.VERTICAL
                        val paddingPx = android.util.TypedValue.applyDimension(
                android.util.TypedValue.COMPLEX_UNIT_DIP, 24f, context.resources.displayMetrics
            ).toInt()
            layout.setPadding(paddingPx, paddingPx, paddingPx, paddingPx)

            val titleBox = EditText(context)
            titleBox.hint = "Issue Title"
            layout.addView(titleBox)

            val descBox = EditText(context)
            descBox.hint = "Issue Description"
            descBox.minLines = 3
            layout.addView(descBox)

            MaterialAlertDialogBuilder(context)
                .setTitle("Report an Issue")
                .setView(layout)
                .setPositiveButton("Submit") { _, _ ->
                    val title = URLEncoder.encode(titleBox.text.toString(), "UTF-8")
                    val body = URLEncoder.encode(descBox.text.toString(), "UTF-8")
                    val url = "https://github.com/pawanwashudev-official/Reality/issues/new?title=$title&body=$body"
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    try {
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // Load Markdown Content
        loadAboutContent()
    }

    private fun loadAboutContent() {
        val prefs = getSharedPreferences("about_cache", android.content.Context.MODE_PRIVATE)
        val cachedContent = prefs.getString("markdown_content", null)

        val markwon = Markwon.builder(this)
            // .usePlugin(CoilImagesPlugin.create(this)) // Add this if you want image support
            .build()

        if (cachedContent != null) {
            markwon.setMarkdown(binding.tvMarkdownContent, cachedContent)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val content = URL(ABOUT_MD_URL).readText()
                withContext(Dispatchers.Main) {
                    markwon.setMarkdown(binding.tvMarkdownContent, content)
                    prefs.edit().putString("markdown_content", content).apply()
                }
            } catch (e: Exception) {
                com.neubofy.reality.utils.TerminalLogger.log("ERROR: ${e.message}")
            }
        }
    }


    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Could not open link", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
