package com.neubofy.reality.google

import android.content.Context
import com.google.api.services.docs.v1.Docs
import com.google.api.services.docs.v1.model.BatchUpdateDocumentRequest
import com.google.api.services.docs.v1.model.Document
import com.google.api.services.docs.v1.model.InsertTextRequest
import com.google.api.services.docs.v1.model.Location
import com.google.api.services.docs.v1.model.Request
import com.neubofy.reality.utils.TerminalLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Google Docs API wrapper.
 * 
 * Provides easy access to:
 * - Create documents
 * - Read document content
 * - Update/Append text
 * - Use as study notes, journal, etc.
 */
object GoogleDocsManager {
    
    private const val APP_NAME = "Reality"
    
    private fun getDocsService(context: Context): Docs? {
        val credential = GoogleAuthManager.getGoogleAccountCredential(context) ?: return null
        
        return Docs.Builder(
            GoogleAuthManager.getHttpTransport(),
            GoogleAuthManager.getJsonFactory(),
            credential
        )
            .setApplicationName("com.neubofy.reality")
            .build()
    }
    
    /**
     * Create a new Google Doc.
     * Throws UserRecoverableAuthIOException if permission is needed.
     */
    suspend fun createDocument(context: Context, title: String): String {
        return withContext(Dispatchers.IO) {
            val service = getDocsService(context) 
                ?: throw IllegalStateException("Not signed in")
            
            val doc = Document().apply {
                this.title = title
            }
            
            val createdDoc = service.documents().create(doc).execute()
            TerminalLogger.log("DOCS API: Created document with ID: ${createdDoc.documentId}")
            createdDoc.documentId
        }
    }
    
    /**
     * Get or create a document by name.
     * Uses Drive API to search for existing doc.
     * Returns Pair(documentId, wasCreated)
     */
    suspend fun getOrCreateDocument(context: Context, title: String): Pair<String, Boolean> {
        return withContext(Dispatchers.IO) {
            // First search in Drive for existing doc with this name
            val driveCredential = GoogleAuthManager.getGoogleAccountCredential(context)
                ?: throw IllegalStateException("Not signed in")
            
            val driveService = com.google.api.services.drive.Drive.Builder(
                GoogleAuthManager.getHttpTransport(),
                GoogleAuthManager.getJsonFactory(),
                driveCredential
            ).setApplicationName("com.neubofy.reality").build()
            
            val query = "name = '$title' and mimeType = 'application/vnd.google-apps.document' and trashed = false"
            val existing = driveService.files().list()
                .setQ(query)
                .setFields("files(id, name)")
                .execute()
            
            if (existing.files?.isNotEmpty() == true) {
                TerminalLogger.log("DOCS API: Found existing doc '$title'")
                return@withContext Pair(existing.files[0].id, false)
            }
            
            // Create new doc
            val service = getDocsService(context)
                ?: throw IllegalStateException("Not signed in")
            
            val doc = Document().apply {
                this.title = title
            }
            
            val createdDoc = service.documents().create(doc).execute()
            TerminalLogger.log("DOCS API: Created document '$title' with ID: ${createdDoc.documentId}")
            Pair(createdDoc.documentId, true)
        }
    }
    
    /**
     * Get document content (plain text extraction).
     */
    suspend fun getDocumentContent(context: Context, documentId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val service = getDocsService(context) ?: return@withContext null
                
                val doc = service.documents().get(documentId).execute()
                
                // Extract text from document body
                val content = StringBuilder()
                doc.body?.content?.forEach { element ->
                    element.paragraph?.elements?.forEach { paragraphElement ->
                        paragraphElement.textRun?.content?.let { text ->
                            content.append(text)
                        }
                    }
                }
                
                content.toString()
            } catch (e: Exception) {
                TerminalLogger.log("DOCS API: Error reading document - ${e.message}")
                null
            }
        }
    }
    
    /**
     * Append text to the end of a document.
     */
    suspend fun appendText(context: Context, documentId: String, text: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val service = getDocsService(context) ?: return@withContext false
                
                // First, get current document to find end index
                val doc = service.documents().get(documentId).execute()
                val endIndex = doc.body?.content?.lastOrNull()?.endIndex ?: 1
                
                val requests = listOf(
                    Request().setInsertText(
                        InsertTextRequest()
                            .setText(text)
                            .setLocation(Location().setIndex(endIndex - 1))
                    )
                )
                
                val batchUpdateRequest = BatchUpdateDocumentRequest().setRequests(requests)
                service.documents().batchUpdate(documentId, batchUpdateRequest).execute()
                
                TerminalLogger.log("DOCS API: Appended text to document")
                true
            } catch (e: Exception) {
                TerminalLogger.log("DOCS API: Error appending text - ${e.message}")
                false
            }
        }
    }
    
    /**
     * Get document metadata (title, last modified, etc).
     */
    suspend fun getDocumentMetadata(context: Context, documentId: String): Document? {
        return withContext(Dispatchers.IO) {
            try {
                val service = getDocsService(context) ?: return@withContext null
                service.documents().get(documentId).execute()
            } catch (e: Exception) {
                TerminalLogger.log("DOCS API: Error getting document metadata - ${e.message}")
                null
            }
        }
    }
}
