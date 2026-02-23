package com.neubofy.reality.ui.activity

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.ui.base.BaseActivity
import com.neubofy.reality.databinding.ActivityAboutBinding
import com.neubofy.reality.utils.UpdateManager
import android.view.View
import android.widget.Toast

class AboutActivity : BaseActivity() {

    private lateinit var binding: ActivityAboutBinding

    companion object {
        const val GITHUB_PROFILE = "https://github.com/pawanwashudev-official"
        const val GITHUB_REPO = "https://github.com/pawanwashudev-official/Reality"
        const val TELEGRAM = "https://t.me/pawanwashudev"
        const val ARRATAI = "https://arratai.com/@pawanwashudev"
        const val INSTAGRAM = "https://instagram.com/pawan_washudev"
        const val EMAIL = "pawanwashudev@gmail.com"
        const val PRIVACY_POLICY = "https://realityprivicypolicy.vercel.app"
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
            e.printStackTrace()
            binding.tvVersion.text = "Version Unknown"
        }

        // Social Links
        binding.btnGithub.setOnClickListener { openUrl(GITHUB_PROFILE) }
        
        // Website
        binding.btnWebsite.setOnClickListener { openUrl("https://neubofyreality.vercel.app") }

        // Telegram
        binding.btnTelegram.setOnClickListener { openUrl(TELEGRAM) }

        // Arratai
        binding.btnArratai.setOnClickListener { openUrl(ARRATAI) }

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
        
        // Support Us
        binding.btnSupportUs.setOnClickListener {
            startActivity(Intent(this, SupportUsActivity::class.java))
        }
        binding.cardSupportUs.setOnClickListener {
            startActivity(Intent(this, SupportUsActivity::class.java))
        }

        // Update Check
        binding.cardUpdate.setOnClickListener {
            binding.progressUpdate.visibility = View.VISIBLE
            binding.tvUpdateStatus.text = "Checking for updates..."
            
            UpdateManager.checkForUpdates(this, silent = false) {
                // This callback only runs if NO update is found (silent=false)
                runOnUiThread {
                    binding.progressUpdate.visibility = View.GONE
                    binding.tvUpdateStatus.text = "You're on the latest version"
                    Toast.makeText(this, "Reality is up to date!", Toast.LENGTH_SHORT).show()
                }
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
