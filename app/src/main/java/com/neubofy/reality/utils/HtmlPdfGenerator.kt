package com.neubofy.reality.utils

import android.content.Context
import android.graphics.pdf.PdfDocument
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object HtmlPdfGenerator {

    /**
     * Generates a PDF from an HTML string using a WebView.
     * Steps:
     * 1. Create WebView on Main Thread.
     * 2. Load HTML.
     * 3. Wait for load.
     * 4. Measure content height.
     * 5. Resize WebView to full content height.
     * 6. Draw WebView to PDF Canvas (Single Long Page).
     */
    suspend fun generatePdf(context: Context, htmlContent: String, outputFile: File): File {
        return withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { continuation ->
                try {
                    val webView = WebView(context)
                    webView.settings.javaScriptEnabled = false // Static content
                    
                    // Set initial width for layout (Standard A4 width in pixels approx 595 at 72dpi, 
                    // but screens are higher density. Let's use a reasonable fixed width like 800px 
                    // and let height grow).
                    val printWidth = 794 // A4 width at 96dpi approx
                    
                    webView.layout(0, 0, printWidth, 1) // Height 1 initially
                    
                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            try {
                                // Calculate content height
                                // measure logic
                                // Calculate reliable content height
                                var contentHeight = view.measuredHeight
                                val internalHeight = (view.contentHeight * view.scale).toInt()
                                
                                // Use the larger of the two to prevent cropping
                                if (internalHeight > contentHeight) {
                                    contentHeight = internalHeight
                                }
                                
                                // Ensure minimum A4 height (approx 1123px at 96dpi)
                                if (contentHeight < 1123) contentHeight = 1123
                                
                                // Ensure layout is updated to full height
                                view.layout(0, 0, printWidth, contentHeight)
                                
                                // Create PDF - Single Continuous Page
                                val document = PdfDocument()
                                val pageInfo = PdfDocument.PageInfo.Builder(printWidth, contentHeight, 1).create()
                                val page = document.startPage(pageInfo)
                                
                                // Draw
                                view.draw(page.canvas)
                                document.finishPage(page)
                                
                                // Save
                                val fos = FileOutputStream(outputFile)
                                document.writeTo(fos)
                                document.close()
                                fos.close()
                                
                                if (continuation.isActive) {
                                    continuation.resume(outputFile)
                                }
                            } catch (e: Exception) {
                                if (continuation.isActive) {
                                    continuation.resumeWithException(e)
                                }
                            }
                        }
                    }
                    
                    // Load
                    webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
                    
                } catch (e: Exception) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }
            }
        }
    }
}
