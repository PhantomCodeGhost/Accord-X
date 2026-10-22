package com.phantom.accord.logic.utils

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.MediaItem
import androidx.preference.PreferenceManager
import com.phantom.accord.logic.data.db.AppDatabase
import com.phantom.accord.logic.data.db.entity.Playlist
import com.phantom.accord.logic.getFile
import com.phantom.accord.ui.LibraryViewModel
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URLDecoder

object PlaylistFileUtils {
    private const val TAG = "PlaylistFileUtils"

    /**
     * Checks if the app has All Files Access on Android 11+ (API 30+).
     */
    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * Requests All Files Access from the user via System Settings.
     */
    fun requestAllFilesAccess(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                val intent = Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                try {
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }

    /**
     * Resolves the root music folder chosen by the user in auto_playlist_folder_uri,
     * or falls back to the system Music directory or an optional fallback parent.
     */
    fun getRootMusicFolder(context: Context, fallbackParent: File? = null): File {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val uriString = prefs.getString("auto_playlist_folder_uri", null)
        if (uriString != null) {
            try {
                val uri = Uri.parse(uriString)
                val docId = try {
                    DocumentsContract.getTreeDocumentId(uri)
                } catch (_: Exception) {
                    val raw = uri.path ?: ""
                    URLDecoder.decode(raw, "UTF-8")
                }
                val decoded = URLDecoder.decode(docId, "UTF-8")
                if (decoded.contains(":")) {
                    val split = decoded.split(":")
                    val relPath = if (split.size > 1) split[1] else ""
                    if (split[0].contains("primary", ignoreCase = true)) {
                        val dir = if (relPath.isNotBlank()) File(Environment.getExternalStorageDirectory(), relPath) else Environment.getExternalStorageDirectory()
                        dir.mkdirs()
                        if (dir.exists()) return dir
                    } else {
                        val vol = split[0].substringAfterLast("/").substringAfterLast("%2F")
                        val dir = if (relPath.isNotBlank()) File("/storage/$vol/$relPath") else File("/storage/$vol")
                        dir.mkdirs()
                        if (dir.exists()) return dir
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving auto_playlist_folder_uri", e)
            }
        }

        if (fallbackParent != null && fallbackParent.exists() && fallbackParent.isDirectory) {
            return fallbackParent
        }

        val defaultDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        if (!defaultDir.exists()) {
            defaultDir.mkdirs()
        }
        return defaultDir
    }

    /**
     * Creates a folder for the given playlist name in the root music directory if it doesn't already exist.
     */
    fun createPlaylistFolder(context: Context, playlistName: String, fallbackDir: File? = null): File {
        val rootDir = getRootMusicFolder(context, fallbackDir)
        val playlistDir = File(rootDir, playlistName)
        if (!playlistDir.exists()) {
            playlistDir.mkdirs()
        }
        return playlistDir
    }

    /**
     * Resolves the actual File on disk for a MediaItem.
     */
    fun resolveMediaItemFile(context: Context, mediaItem: MediaItem): File? {
        // 1. Try URI path with decoding
        val uri = mediaItem.localConfiguration?.uri
        if (uri != null) {
            if (uri.scheme == "file") {
                try {
                    val rawPath = uri.path ?: ""
                    val decoded = URLDecoder.decode(rawPath, "UTF-8")
                    val f = File(decoded)
                    if (f.exists()) return f
                    val direct = File(rawPath)
                    if (direct.exists()) return direct
                } catch (_: Throwable) {}
            }
        }

        // 2. Try getFile() directly (with try-catch for non-file schemes)
        try {
            val directFile = mediaItem.getFile()
            if (directFile != null && directFile.exists()) {
                return directFile
            }
        } catch (_: Throwable) {}

        // 3. Try mediaId lookup via MediaStore
        val mediaIdLong = mediaItem.mediaId.toLongOrNull()
        if (mediaIdLong != null) {
            try {
                val contentUri = android.content.ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaIdLong
                )
                val proj = arrayOf(MediaStore.Audio.Media.DATA)
                context.contentResolver.query(contentUri, proj, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val colIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                        if (colIdx != -1) {
                            val path = cursor.getString(colIdx)
                            if (!path.isNullOrBlank()) {
                                val file = File(path)
                                if (file.exists()) return file
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving mediaId to file", e)
            }
        }

        // 4. Try matching title in root music folder
        val title = mediaItem.mediaMetadata.title?.toString()
        if (!title.isNullOrBlank()) {
            val root = getRootMusicFolder(context)
            try {
                root.walkTopDown().maxDepth(3).find { file ->
                    file.isFile && file.nameWithoutExtension.equals(title, ignoreCase = true)
                }?.let { return it }
            } catch (_: Exception) {}
        }

        return null
    }

    /**
     * Moves a song file into the playlist's folder safely without corrupting the file.
     * Physically moves the file locally on disk and deletes the original file.
     */
    fun moveSongToPlaylistFolder(context: Context, mediaItem: MediaItem, playlistName: String): File? {
        val sourceFile = resolveMediaItemFile(context, mediaItem) ?: run {
            Log.e(TAG, "Could not resolve source file for ${mediaItem.mediaMetadata.title}")
            return null
        }

        // Determine fallback parent: if sourceFile parent is root Music or a subfolder
        val fallbackParent = if (sourceFile.parentFile?.parentFile != null && 
            sourceFile.parentFile?.name.equals(playlistName, ignoreCase = true)) {
            sourceFile.parentFile?.parentFile
        } else {
            sourceFile.parentFile
        }

        val targetDir = createPlaylistFolder(context, playlistName, fallbackParent)
        val targetFile = File(targetDir, sourceFile.name)

        // If already in the target directory, nothing to move
        if (sourceFile.parentFile?.canonicalPath == targetDir.canonicalPath) {
            Log.i(TAG, "Song is already in target playlist folder: ${targetFile.absolutePath}")
            return targetFile
        }

        // Strategy 1: Try atomic rename
        val renamed = try {
            sourceFile.renameTo(targetFile)
        } catch (_: Exception) {
            false
        }

        if (renamed && targetFile.exists() && targetFile.length() > 0) {
            Log.i(TAG, "Moved file via atomic rename to: ${targetFile.absolutePath}")
            notifyMediaScanner(context, sourceFile, targetFile)
            return targetFile
        }

        // Strategy 2: Streaming copy with atomic temp-file replace, then DELETE source
        val tempFile = File(targetDir, "${sourceFile.name}.moving_${System.currentTimeMillis()}")
        var movedViaStream = false
        try {
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                    output.flush()
                }
            }

            // Verify integrity before replacing source
            if (tempFile.exists() && tempFile.length() > 0 && tempFile.length() == sourceFile.length()) {
                if (targetFile.exists()) {
                    targetFile.delete()
                }
                if (tempFile.renameTo(targetFile)) {
                    var deleted = sourceFile.delete()
                    if (!deleted) {
                        deleted = deleteFileViaSafOrMediaStore(context, sourceFile, mediaItem)
                    }
                    if (deleted || !sourceFile.exists()) {
                        Log.i(TAG, "Moved file safely via copy to: ${targetFile.absolutePath}")
                        notifyMediaScanner(context, sourceFile, targetFile)
                        movedViaStream = true
                        return targetFile
                    } else {
                        // Could not delete original, remove duplicate to avoid corruption
                        targetFile.delete()
                        Log.e(TAG, "Could not delete original file: ${sourceFile.absolutePath}")
                    }
                } else {
                    tempFile.delete()
                }
            } else {
                tempFile.delete()
            }
        } catch (e: Exception) {
            tempFile.delete()
            Log.w(TAG, "Stream copy failed: ${e.message}")
        }

        if (movedViaStream) return targetFile

        // Strategy 3: Storage Access Framework (SAF DocumentFile) using tree URI permission
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val treeUriStr = prefs.getString("auto_playlist_folder_uri", null)
        if (treeUriStr != null) {
            try {
                val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(treeUriStr))
                if (rootDoc != null && rootDoc.exists()) {
                    val rootDir = getRootMusicFolder(context, fallbackParent)
                    var playlistDoc = rootDoc.findFile(playlistName)
                    if (playlistDoc == null || !playlistDoc.isDirectory) {
                        playlistDoc = rootDoc.createDirectory(playlistName)
                    }
                    if (playlistDoc != null) {
                        val sourceDoc = findDocumentFile(rootDoc, rootDir, sourceFile)
                        if (sourceDoc != null && sourceDoc.exists()) {
                            var movedViaSaf = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                                try {
                                    val sourceParentDoc = sourceDoc.parentFile ?: rootDoc
                                    val movedUri = DocumentsContract.moveDocument(
                                        context.contentResolver,
                                        sourceDoc.uri,
                                        sourceParentDoc.uri,
                                        playlistDoc.uri
                                    )
                                    if (movedUri != null) {
                                        movedViaSaf = true
                                    }
                                } catch (e: Exception) {
                                    Log.d(TAG, "DocumentsContract.moveDocument failed: ${e.message}")
                                }
                            }

                            if (!movedViaSaf) {
                                playlistDoc.findFile(sourceFile.name)?.delete()
                                val targetDoc = playlistDoc.createFile("audio/*", sourceFile.name)
                                if (targetDoc != null) {
                                    context.contentResolver.openInputStream(sourceDoc.uri)?.use { inStream ->
                                        context.contentResolver.openOutputStream(targetDoc.uri)?.use { outStream ->
                                            val buffer = ByteArray(64 * 1024)
                                            var bytesRead: Int
                                            while (inStream.read(buffer).also { bytesRead = it } != -1) {
                                                outStream.write(buffer, 0, bytesRead)
                                            }
                                            outStream.flush()
                                        }
                                    }
                                    sourceDoc.delete()
                                    movedViaSaf = true
                                }
                            }

                            if (movedViaSaf) {
                                Log.i(TAG, "Moved file via SAF DocumentFile to: ${targetFile.absolutePath}")
                                notifyMediaScanner(context, sourceFile, targetFile)
                                return targetFile
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "SAF DocumentFile move failed", e)
            }
        }

        return null
    }

    private fun findDocumentFile(rootDoc: DocumentFile, rootDir: File, targetFile: File): DocumentFile? {
        try {
            val rootPath = rootDir.canonicalPath
            val targetPath = targetFile.canonicalPath
            if (targetPath.startsWith(rootPath)) {
                val rel = targetPath.removePrefix(rootPath).trim('/', '\\')
                val segments = rel.split(File.separatorChar).filter { it.isNotBlank() }
                var curr: DocumentFile? = rootDoc
                for (segment in segments) {
                    curr = curr?.findFile(segment)
                    if (curr == null) break
                }
                if (curr != null && curr.exists()) return curr
            }
        } catch (_: Exception) {}

        rootDoc.findFile(targetFile.name)?.let { return it }

        try {
            for (child in rootDoc.listFiles()) {
                if (child.isDirectory) {
                    child.findFile(targetFile.name)?.let { return it }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    private fun deleteFileViaSafOrMediaStore(context: Context, file: File, mediaItem: MediaItem): Boolean {
        // 1. Try SAF tree uri
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val treeUriStr = prefs.getString("auto_playlist_folder_uri", null)
        if (treeUriStr != null) {
            try {
                val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(treeUriStr))
                val rootDir = getRootMusicFolder(context)
                if (rootDoc != null) {
                    val doc = findDocumentFile(rootDoc, rootDir, file)
                    if (doc != null && doc.delete()) {
                        return true
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Try MediaStore delete
        val mediaIdLong = mediaItem.mediaId.toLongOrNull()
        if (mediaIdLong != null) {
            try {
                val uri = android.content.ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaIdLong
                )
                val deletedRows = context.contentResolver.delete(uri, null, null)
                if (deletedRows > 0) return true
            } catch (_: Exception) {}
        }

        return false
    }

    private fun notifyMediaScanner(context: Context, oldFile: File, newFile: File) {
        try {
            MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(oldFile.absolutePath, newFile.absolutePath),
                null
            ) { path, uri ->
                Log.d(TAG, "Scanned $path -> $uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error notifying media scanner", e)
        }
    }

    /**
     * Scans subdirectories in the root music folder, creates any missing playlists in Room,
     * and associates songs whose paths reside within those folders.
     */
    suspend fun syncFoldersToPlaylists(context: Context, libraryViewModel: LibraryViewModel) {
        try {
            val rootDir = getRootMusicFolder(context)
            if (!rootDir.exists() || !rootDir.isDirectory) return

            // Clean up any unwanted .thumbnail directory in root music folder
            try {
                val t1 = File(rootDir, ".thumbnail")
                if (t1.exists() && t1.isDirectory) t1.deleteRecursively()
                val t2 = File(rootDir, ".thumbnails")
                if (t2.exists() && t2.isDirectory) t2.deleteRecursively()
            } catch (_: Throwable) {}

            val folders = rootDir.listFiles { file -> 
                file.isDirectory && !file.isHidden && !file.name.startsWith(".") && !file.name.contains("thumbnail", ignoreCase = true)
            } ?: return

            val db = AppDatabase.getInstance(context)
            val playlistDao = db.playlistDao()
            val existingPlaylists = playlistDao.getAllPlaylists()
            val allMediaItems = libraryViewModel.mediaItemList.value.orEmpty()

            var modified = false

            for (folder in folders) {
                // Clean up any unwanted .thumbnail directory inside playlist folder
                try {
                    val t1 = File(folder, ".thumbnail")
                    if (t1.exists() && t1.isDirectory) t1.deleteRecursively()
                    val t2 = File(folder, ".thumbnails")
                    if (t2.exists() && t2.isDirectory) t2.deleteRecursively()
                } catch (_: Throwable) {}

                val playlistName = folder.name
                if (playlistName.equals("favourite", ignoreCase = true)) continue

                var targetPlaylist = existingPlaylists.find { it.playlist.name.equals(playlistName, ignoreCase = true) }
                if (targetPlaylist == null) {
                    val newPlaylist = Playlist(System.currentTimeMillis(), playlistName, null)
                    playlistDao.addPlaylist(newPlaylist)
                    targetPlaylist = playlistDao.getAllPlaylists().find { it.playlist.name == playlistName }
                    modified = true
                }

                if (targetPlaylist != null) {
                    val existingSongIds = targetPlaylist.mediaItems.map { it.mediaItemId }.toSet()
                    val songsInFolder = allMediaItems.filter { item ->
                        val path = resolveMediaItemFile(context, item)?.absolutePath
                            ?: item.localConfiguration?.uri?.path
                        path != null && path.startsWith(folder.absolutePath)
                    }

                    for (song in songsInFolder) {
                        val mediaId = song.mediaId.toLongOrNull()
                        if (mediaId != null && !existingSongIds.contains(mediaId)) {
                            db.mediaItemDao().addMediaItem(com.phantom.accord.logic.data.db.entity.MediaItem(mediaId))
                            db.mediaItemDao().addMediaItemToPlaylist(targetPlaylist.playlist.playlistId, mediaId)
                            modified = true
                        }
                    }
                }
            }

            if (modified) {
                DatabaseUtils.getPrivatePlaylist(libraryViewModel, context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing folders to playlists", e)
        }
    }
}

