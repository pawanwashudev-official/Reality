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
     * Search for a specific file by name in a specific folder.
     * Returns the first matching file's ID, or null if not found.
     */
    suspend fun searchFile(context: Context, fileName: String, folderId: String? = null): String? {
        return withContext(Dispatchers.IO) {
            try {
                val service = getDriveService(context) ?: return@withContext null
                
                var query = "name = '$fileName' and trashed = false"
                if (folderId != null) {
                    query += " and '$folderId' in parents"
                }
                
                // Exclude folders from file search if we want strictly files? 
                // Usually for diary docs we just want the name match.
                
                val result = service.files().list()
                    .setQ(query)
                    .setFields("files(id, name)")
                    .setPageSize(1)
                    .execute()
                
                if (result.files.isNotEmpty()) {
                    result.files[0].id
                } else {
                    null
                }
            } catch (e: Exception) {
                TerminalLogger.log("DRIVE API: Error searching file - ${e.message}")
                null
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
    
    /**
     * List files in a specific folder (convenience alias).
     */
    suspend fun listFilesInFolder(context: Context, folderId: String, pageSize: Int = 50): List<File> {
        return listFiles(context, folderId, pageSize)
    }
    
    /**
     * Move a file to a specific folder.
     * Removes from current parent and adds to new folder.
     */
    suspend fun moveFileToFolder(context: Context, fileId: String, folderId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val service = getDriveService(context) ?: return@withContext false
                
                // Get current parents
                val file = service.files().get(fileId)
                    .setFields("parents")
                    .execute()
                
                val previousParents = file.parents?.joinToString(",") ?: ""
                
                // Move file to new folder
                service.files().update(fileId, null)
                    .setAddParents(folderId)
                    .setRemoveParents(previousParents)
                    .setFields("id, parents")
                    .execute()
                
                TerminalLogger.log("DRIVE API: Moved file $fileId to folder $folderId")
                true
            } catch (e: Exception) {
                TerminalLogger.log("DRIVE API: Error moving file - ${e.message}")
                false
            }
        }
    }
    
    /**
     * Check if a file exists and is accessible.
     */
    suspend fun checkFileExists(context: Context, fileId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val service = getDriveService(context) ?: return@withContext false
                // Just try to fetch the ID field. If it fails (404), it doesn't exist.
                service.files().get(fileId).setFields("id").execute()
                true
            } catch (e: Exception) {
                // Determine if it's a 404 Not Found
                if (e.message?.contains("404") == true || 
                    e.message?.contains("File not found") == true ||
                    e.message?.contains("notFound") == true) {
                    TerminalLogger.log("DRIVE API: File $fileId not found (deleted?)")
                    return@withContext false
                }
                
                // For network/other errors, assume file EXISTS to prevent wiping memory.
                // It's safer to fail later than to lose the ID.
                TerminalLogger.log("DRIVE API: Error checking file (assuming exists) - ${e.message}")
                true
            }
        }
    }
}
