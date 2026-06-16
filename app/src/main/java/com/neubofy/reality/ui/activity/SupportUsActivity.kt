package com.neubofy.reality.ui.activity

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.databinding.ActivitySupportUsBinding
import java.text.SimpleDateFormat
import java.util.*

class SupportUsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySupportUsBinding
    
    companion object {
        const val UPI_ID = "neubofy@pnb"
        const val PAYEE_NAME = "Neubofy"
        const val MIN_AMOUNT = 1
        const val MAX_AMOUNT = 49000
        val SUGGESTED_AMOUNTS = listOf(50, 100, 500, 1000)
        
        const val DEVELOPER_STORY = """Reality was founded on a singular mission: to restore the baseline of human focus in a world designed for distraction.

As an independent developer, I realized that modern technology often consumes our time rather than serving it. Reality is my response—a commitment to technical excellence, absolute privacy, and uncompromised efficacy. Every 'Elite' feature we build is designed to help you reclaim your most valuable asset: your attention.

The journey from a student's vision to a world-class focus tool has been fueled by the incredible support of this community. Reality remains 100% open-source and ad-free, ensuring that digital mindfulness is accessible to everyone without compromise.

Your support fuels:
• High-performance development of advanced focus tools.
• Sustaining a privacy-first infrastructure with zero telemetry.
• Empowering the next generation of open-source engineering.

Thank you for choosing to live in Reality. Together, we are redefining our relationship with technology.

— Pawan Washudev
Founder & Lead Developer"""
    }
    
    private var selectedAmount: Int = 100
    private var userName: String = "Supporter"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySupportUsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Get user name from SharedPreferences
        val prefs = getSharedPreferences("reality_prefs", Context.MODE_PRIVATE)
        userName = prefs.getString("user_name", "Supporter") ?: "Supporter"
        
        setupToolbar()
        setupStoryAnimation()
        setupAmountInput()
        setupSuggestedAmounts()
        setupPaymentOptions()
    }
    
    private fun setupStoryAnimation() {
        // Typewriter animation for the story
        val story = DEVELOPER_STORY
        binding.tvStoryAnimated.text = ""
        
        var index = 0
        val handler = android.os.Handler(mainLooper)
        val runnable = object : Runnable {
            override fun run() {
                if (index < story.length) {
                    binding.tvStoryAnimated.text = story.substring(0, index + 1)
                    index++
                    // Faster for spaces, slower for punctuation
                    val delay = when {
                        index < story.length && story[index] == '\n' -> 100L
                        index < story.length && story[index] in ".,!?" -> 80L
                        else -> 15L
                    }
                    handler.postDelayed(this, delay)
                }
            }
        }
        handler.postDelayed(runnable, 500) // Start after 500ms delay
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }
    
    private fun setupAmountInput() {
        binding.etAmount.setText(selectedAmount.toString())
        
        binding.etAmount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val text = s?.toString() ?: ""
                if (text.isNotEmpty()) {
                    try {
                        val amount = text.toInt()
                        when {
                            amount < MIN_AMOUNT -> {
                                binding.tilAmount.error = "Minimum ₹$MIN_AMOUNT"
                            }
                            amount > MAX_AMOUNT -> {
                                binding.tilAmount.error = "Maximum ₹$MAX_AMOUNT"
                            }
                            else -> {
                                binding.tilAmount.error = null
                                selectedAmount = amount
                                updateSuggestedButtonStates()
                            }
                        }
                    } catch (e: NumberFormatException) {
                        binding.tilAmount.error = "Invalid amount"
                    }
                }
            }
        })
    }
    
    private fun setupSuggestedAmounts() {
        binding.btnAmount50.setOnClickListener { setAmount(50) }
        binding.btnAmount100.setOnClickListener { setAmount(100) }
        binding.btnAmount500.setOnClickListener { setAmount(500) }
        binding.btnAmount1000.setOnClickListener { setAmount(1000) }
        
        updateSuggestedButtonStates()
    }
    
    private fun setAmount(amount: Int) {
        selectedAmount = amount
        binding.etAmount.setText(amount.toString())
        binding.etAmount.setSelection(amount.toString().length)
        updateSuggestedButtonStates()
    }
    
    private fun updateSuggestedButtonStates() {
        val buttons = listOf(
            binding.btnAmount50 to 50,
            binding.btnAmount100 to 100,
            binding.btnAmount500 to 500,
            binding.btnAmount1000 to 1000
        )
        
        buttons.forEach { (button, amount) ->
            button.isSelected = (selectedAmount == amount)
        }
    }
    
    private fun setupPaymentOptions() {
        // Option 1: Open UPI App with amount
        binding.btnOpenUpiApp.setOnClickListener {
            if (validateAmount()) {
                openUpiApp()
            }
        }
        
        // Option 2: Generate QR Code (show in popup)
        binding.btnGenerateQr.setOnClickListener {
            if (validateAmount()) {
                showQrDialog()
            }
        }
        
        // Option 3: Copy UPI ID (bottom card)
        binding.btnCopyUpi.setOnClickListener {
            copyUpiId()
        }
        
        // Inline copy button (in fallback section)
        binding.btnCopyUpiInline.setOnClickListener {
            copyUpiId()
        }
    }
    
    private fun copyUpiId() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("UPI ID", UPI_ID)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "UPI ID copied: $UPI_ID", Toast.LENGTH_SHORT).show()
    }
    
    private fun openUpiApp() {
        try {
            val upiUri = buildUpiUriForQr() // Same URI works for both
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(upiUri))
            
            val resolveInfoList = packageManager.queryIntentActivities(intent, 0)
            if (resolveInfoList.isNotEmpty()) {
                val chooserIntent = Intent.createChooser(intent, "Pay ₹$selectedAmount with")
                startActivityForResult(chooserIntent, 100)
            } else {
                Toast.makeText(this, "No UPI app found. Try QR code or copy UPI ID.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open UPI app. Try QR code instead.", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showQrDialog() {
        val upiUri = buildUpiUriForQr()
        
        try {
            val qrBitmap = generateQrCode(upiUri, 512)
            
            // Create dialog
            val dialog = android.app.AlertDialog.Builder(this, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle("Scan to Pay ₹$selectedAmount")
                .setMessage("UPI ID: $UPI_ID")
                .setPositiveButton("Done") { d, _ -> d.dismiss() }
                .setNeutralButton("Copy UPI ID") { _, _ ->
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("UPI ID", UPI_ID)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, "UPI ID copied!", Toast.LENGTH_SHORT).show()
                }
                .create()
            
            // Create ImageView for QR
            val imageView = android.widget.ImageView(this).apply {
                setImageBitmap(qrBitmap)
                setPadding(48, 48, 48, 24)
                adjustViewBounds = true
            }
            
            dialog.setView(imageView)
            dialog.show()
            
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to generate QR code", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun validateAmount(): Boolean {
        return when {
            selectedAmount < MIN_AMOUNT -> {
                Toast.makeText(this, "Minimum amount is ₹$MIN_AMOUNT", Toast.LENGTH_SHORT).show()
                false
            }
            selectedAmount > MAX_AMOUNT -> {
                Toast.makeText(this, "Maximum amount is ₹$MAX_AMOUNT", Toast.LENGTH_SHORT).show()
                false
            }
            else -> true
        }
    }
    
    private fun buildPaymentNote(): String {
        val customNote = binding.etNote.text?.toString()?.trim() ?: ""
        
        // Build a friendly note with user's name
        return if (customNote.isNotEmpty()) {
            // User provided a custom note
            "$userName - $customNote".take(50)
        } else {
            // Default note with user's name
            "$userName paid via Reality".take(50)
        }.replace(Regex("[^a-zA-Z0-9 ]"), "") // Keep only safe characters
    }
    
    private fun generateTransactionId(): String {
        // Generate unique transaction reference
        return "REALITY${System.currentTimeMillis()}"
    }
    
    private fun buildUpiUriForQr(): String {
        val note = buildPaymentNote()
        val transactionId = generateTransactionId()
        
        return StringBuilder("upi://pay?")
            .append("pa=").append(UPI_ID)
            .append("&pn=").append(Uri.encode(PAYEE_NAME))
            .append("&tr=").append(transactionId)
            .append("&tn=").append(Uri.encode(note))
            .append("&am=").append(selectedAmount)
            .append("&cu=INR")
            .toString()
    }
    
    /**
     * Simple QR Code generator using built-in Android graphics
     * This is a basic implementation for UPI strings
     */
    private fun generateQrCode(content: String, size: Int): Bitmap {
        // Use Android's built-in QR encoding via Google's ZXing (bundled with most devices)
        // Fallback to a simple pattern if not available
        try {
            val writer = com.google.zxing.qrcode.QRCodeWriter()
            val bitMatrix = writer.encode(content, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            return bitmap
        } catch (e: Exception) {
            // Fallback: create a placeholder with UPI text
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            
            val paint = android.graphics.Paint().apply {
                color = Color.BLACK
                textSize = 24f
                textAlign = android.graphics.Paint.Align.CENTER
            }
            canvas.drawText("Scan with UPI App", size / 2f, size / 2f, paint)
            canvas.drawText(UPI_ID, size / 2f, size / 2f + 40, paint)
            
            return bitmap
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 100) {
            // Payment flow completed (success/failure handled by UPI app)
            val status = data?.getStringExtra("Status") ?: data?.getStringExtra("status")
            when (status?.lowercase()) {
                "success" -> {
                    Toast.makeText(this, "Thank you for your support! 💚", Toast.LENGTH_LONG).show()
                    finish()
                }
                "failure" -> {
                    Toast.makeText(this, "Payment failed. Please try again.", Toast.LENGTH_SHORT).show()
                }
                "submitted" -> {
                    Toast.makeText(this, "Payment submitted for processing", Toast.LENGTH_SHORT).show()
                }
                else -> {
                    // User may have cancelled or status unknown
                }
            }
        }
    }
}
