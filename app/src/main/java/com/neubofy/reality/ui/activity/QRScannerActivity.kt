package com.neubofy.reality.ui.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.neubofy.reality.ui.base.BaseActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.neubofy.reality.data.db.AppDatabase
import com.neubofy.reality.data.db.TapasyaSession
import com.neubofy.reality.databinding.ActivityQrScannerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class QRScannerActivity : BaseActivity() {

    private lateinit var binding: ActivityQrScannerBinding
    private lateinit var cameraExecutor: ExecutorService
    private var isProcessing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }

        binding.btnCancelScan.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        binding.btnShareScan.setOnClickListener {
            val qrBitmap = com.neubofy.reality.utils.QRUtils.generateQRCode("reality://smart_sleep")
            if (qrBitmap != null) {
                com.neubofy.reality.utils.QRUtils.shareQR(this, qrBitmap)
            } else {
                Toast.makeText(this, "Failed to generate QR Code", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                com.neubofy.reality.utils.TerminalLogger.log("ERROR: ${exc.message}")
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private val scanner = BarcodeScanning.getClient()

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (isProcessing) return@addOnSuccessListener
                    for (barcode in barcodes) {
                        if (barcode.valueType == Barcode.TYPE_TEXT || barcode.valueType == Barcode.TYPE_URL) {
                            val rawValue = barcode.rawValue
                            if (rawValue != null) {
                                if (rawValue.startsWith("Reality:Tapashya?data=")) {
                                    isProcessing = true
                                    handleTapashyaSync(rawValue)
                                    break
                                }
                            }
                        }
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        }
    }

    private fun handleTapashyaSync(rawValue: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val encodedData = rawValue.substringAfter("Reality:Tapashya?data=")
                val decodedString = String(Base64.decode(encodedData, Base64.DEFAULT))
                // Web app used encodeURIComponent, so we should URL decode it if necessary.
                // In android we can use java.net.URLDecoder
                val unescapedString = java.net.URLDecoder.decode(decodedString, "UTF-8")

                val sessionStrings = unescapedString.split("~")

                val now = System.currentTimeMillis()
                val sevenDaysAgo = now - 7L * 24 * 60 * 60 * 1000

                val dao = AppDatabase.getDatabase(this@QRScannerActivity).tapasyaSessionDao()

                var importedCount = 0
                var ignoredCount = 0

                for (sessionStr in sessionStrings) {
                    if (sessionStr.isBlank()) continue

                    val parts = sessionStr.split("|")
                    if (parts.size >= 9) {
                        val id = parts[0]
                        val name = parts[1]
                        val targetTime = parts[2].toLongOrNull() ?: 0L
                        val startTime = parts[3].toLongOrNull() ?: 0L
                        val endTime = parts[4].toLongOrNull() ?: 0L
                        val effectiveTime = parts[5].toLongOrNull() ?: 0L
                        val totalPause = parts[6].toLongOrNull() ?: 0L
                        val pauseLimit = parts[7].toLongOrNull() ?: 0L
                        val wasAutoStopped = parts[8] == "1"

                        // Expiration check
                        if (startTime < sevenDaysAgo) {
                            ignoredCount++
                            continue
                        }

                        // Duplicate check
                        val existing = dao.getSessionById(id)
                        if (existing != null) {
                            ignoredCount++
                            continue
                        }

                        // Safe insert
                        val newSession = TapasyaSession(
                            sessionId = id,
                            name = name,
                            targetTimeMs = targetTime,
                            startTime = startTime,
                            endTime = endTime,
                            effectiveTimeMs = effectiveTime,
                            totalPauseMs = totalPause,
                            pauseLimitMs = pauseLimit,
                            wasAutoStopped = wasAutoStopped
                        )
                        dao.insert(newSession)
                        importedCount++
                    } else {
                        ignoredCount++
                    }
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@QRScannerActivity, "Imported $importedCount new sessions, ignored $ignoredCount expired/duplicate", Toast.LENGTH_LONG).show()
                    setResult(RESULT_OK)
                    finish()
                }

            } catch (e: Exception) {
                Log.e("QRScanner", "Error syncing Tapashya data", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@QRScannerActivity, "Invalid QR Code format", Toast.LENGTH_SHORT).show()
                    isProcessing = false
                }
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        scanner.close()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}
