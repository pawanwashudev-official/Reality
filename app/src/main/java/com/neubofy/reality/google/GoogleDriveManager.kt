package com.neubofy.reality.google

import android.content.Context
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.FileList
import com.neubofy.reality.utils.TerminalLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Google Drive API wrapper.
 * 
 * Provides easy access to:
 * - List files
 * - Upload/Download files
 * - Create folders
 * - App-specific data storage
 */
object GoogleDriveManager {
    
    private const val APP_NAME = "Reality"
    private const val APP_FOLDER_NAME = "Reality App Data"
    
    private fun getDriveService(context: Context): Drive? {
        val credential = GoogleAuthManager.getGoogleAccountCredential(context) ?: return null
        
        return Drive.Builder(
            GoogleAuthManager.getHttpTransport(),
            GoogleAuthManager.getJsonFactory(),
            credential
        )
            .setApplicationName("com.neubofy.reality")
            .build()
    }
    
    /**
     * List files in Drive (or specific folder).
     */
    suspend fun listFiles(context: Context, folderId: String? = null, pageSize: Int = 20): List<File> {
        return withContext(Dispatchers.IO) {
            try {
                val service = getDriveService(context) ?: return@withContext emptyList()
                
                var query = "trashed = false"
                if (folderId != null) {
                    query += " and '$folderId' in parents"
                }
                
                val result = service.files().list()
                    .setQ(query)
                    .setPageSize(pageSize)
                    .setFields("files(id, name, mimeType, modifiedTime, size)")
                    .execute()
                
                result.files ?: emptyList()
            } catch (e: Exception) {
                TerminalLogger.log("DRIVE API: Error listing files - ${e.message}")
                emptyList()
            }
        }
    }
    
    /**
     * Create or get a folder by name in Drive.
     * Returns Pair(folderId, wasCreated)
     * Throws UserRecoverableAuthIOException if permission is needed.
     */
    suspend fun getOrCreateFolder(context: Context, folderName: String): Pair<String, Boolean> {
        return withContext(Dispatchers.IO) {
            val service = getDriveService(context) 
                ?: throw IllegalStateException("Not signed in")
            
            // Check if folder exists
            val query = "name = '$folderName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
            val existing = service.files().list()
                .setQ(query)
                .setFields("files(id, name)")
                .execute()
            
            if (existing.files?.isNotEmpty() == true) {
                return@withContext Pair(existing.files[0].id, false)
            }
            
            // Create new folder
            val folderMetadata = File().apply {
                name = folderName
                mimeType = "application/vnd.google-apps.folder"
            }
            
            val folder = service.files().create(folderMetadata)
                .setFields("id")
                .execute()
            
            TerminalLogger.log("DRIVE API: Created folder '$folderName' with ID: ${folder.id}")
            Pair(folder.id, true)
        }
    }
    
    /**
     * Create or get the app's dedicated folder in Drive (legacy).
     */
    suspend fun getOrCreateAppFolder(context: Context): String? {
        return try {
            getOrCreateFolder(context, APP_FOLDER_NAME).first
        } catch (e: Exception) {
            TerminalLogger.log("DRIVE API: Error creating app folder - ${e.message}")
            null
        }
    }
    
    /**
     * Upload a file to Drive.
     */
    suspend fun uploadFile(
        context: Context,
        fileName: String,
        mimeType: String,
        content: InputStream,
        folderId: String? = null
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                val service = getDriveService(context) ?: return@withContext null
                
                val fileMetadata = File().apply {
                    name = fileName
                    if (folderId != null) {
                        parents = listOf(folderId)
                    }
                }
                
                val mediaContent = com.google.api.client.http.InputStreamContent(mimeType, content)
                
                val file = service.files().create(fileMetadata, mediaContent)
                    .setFields("id")
                    .execute()
                
                TerminalLogger.log("DRIVE API: Uploaded file with ID: ${file.id}")
                file.id
            } catch (e: Exception) {
                TerminalLogger.log("DRIVE API: Error uploading file - ${e.message}")
                null
            }
        }
    }
    
    /**
     * Download a file from Drive.
     */
    suspend fun downloadFile(context: Context, fileId: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val service = getDriveService(context) ?: return@withContext null
                
                val outputStream = ByteArrayOutputStream()
                service.files().get(fileId).executeMediaAndDownloadTo(outputStream)
                
                outputStream.toByteArray()
            } catch (e: Exception) {
                TerminalLogger.log("DRIVE API: Error downloading file - ${e.message}")
                null
            }
        }
    }
    
    /**
     * Delete a file from Drive.
     */
    suspend fun deleteFile(context: Context, fileId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val service = getDriveService(context) ?: return@withContext false
                service.files().delete(fileId).execute()
                true
            } catch (e: Exception) {
                TerminalLogger.log("DRIVE API: Error deleting file - ${e.message}")
                false
            }
        }
    }
}
