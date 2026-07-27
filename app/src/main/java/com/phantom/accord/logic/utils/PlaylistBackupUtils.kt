package com.phantom.accord.logic.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.TextView
import com.google.android.material.snackbar.Snackbar
import com.phantom.accord.R
import com.phantom.accord.logic.data.db.AppDatabase
import com.phantom.accord.logic.data.db.entity.MediaItem
import com.phantom.accord.logic.data.db.entity.Playlist
import com.phantom.accord.ui.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PlaylistBackupUtils {

    private const val TAG = "PlaylistBackupUtils"
    private const val JSON_VERSION = 1

    /**
     * Export all playlists to a JSON file at the given URI.
     */
    suspend fun exportPlaylistsToJson(
        context: Context,
        libraryViewModel: LibraryViewModel,
        uri: Uri,
        anchorView: View
    ) {
        try {
            val json = withContext(Dispatchers.IO) {
                val database = AppDatabase.getInstance(context)
                val allPlaylists = database.playlistDao().getAllPlaylists()
                val allSongs = libraryViewModel.mediaItemList.value ?: emptyList()

                // Build a lookup map: mediaId (Long) -> MediaItem
                val songLookup = allSongs.associateBy { it.mediaId.toLong() }

                val root = JSONObject()
                root.put("version", JSON_VERSION)
                root.put("exportDate", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))

                val playlistsArray = JSONArray()

                for (playlistWithItems in allPlaylists) {
                    val playlistObj = JSONObject()
                    playlistObj.put("name", playlistWithItems.playlist.name)

                    val songsArray = JSONArray()
                    for (mediaItem in playlistWithItems.mediaItems) {
                        val song = songLookup[mediaItem.mediaItemId]
                        if (song != null) {
                            val songObj = JSONObject()
                            songObj.put("title", song.mediaMetadata.title?.toString() ?: "")
                            songObj.put("artist", song.mediaMetadata.artist?.toString() ?: "")
                            songsArray.put(songObj)
                        }
                    }

                    playlistObj.put("songs", songsArray)
                    playlistsArray.put(playlistObj)
                }

                root.put("playlists", playlistsArray)
                root.toString(2) // Pretty print with 2 spaces
            }

            // Write to the file
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray(Charsets.UTF_8))
                } ?: throw Exception("Could not open output stream")
            }

            // Count playlists exported
            val playlistCount = JSONObject(json).getJSONArray("playlists").length()

            withContext(Dispatchers.Main) {
                showSnackbar(
                    anchorView,
                    "Exported $playlistCount playlists successfully",
                    isSuccess = true,
                    context = context
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error exporting playlists", e)
            withContext(Dispatchers.Main) {
                showSnackbar(
                    anchorView,
                    "Export failed: ${e.localizedMessage}",
                    isSuccess = false,
                    context = context
                )
            }
        }
    }

    /**
     * Import playlists from a JSON file at the given URI.
     * Uses title + artist matching (case-insensitive) to find songs on this device.
     */
    suspend fun importPlaylistsFromJson(
        context: Context,
        libraryViewModel: LibraryViewModel,
        uri: Uri,
        anchorView: View
    ) {
        try {
            var playlistCount = 0
            var totalSongs = 0
            var missedSongs = 0

            withContext(Dispatchers.IO) {
                // Read the JSON file
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw Exception("Could not open input stream")

                val root = JSONObject(jsonString)
                val version = root.optInt("version", 0)
                if (version < 1) {
                    throw Exception("Invalid backup file format")
                }

                val allSongs = libraryViewModel.mediaItemList.value ?: emptyList()
                val database = AppDatabase.getInstance(context)
                val playlistDao = database.playlistDao()
                val mediaItemDao = database.mediaItemDao()

                // Build a lookup: (lowercase title, lowercase artist) -> mediaId
                val songMatchMap = mutableMapOf<Pair<String, String>, Long>()
                for (song in allSongs) {
                    val title = song.mediaMetadata.title?.toString()?.lowercase(Locale.ROOT) ?: continue
                    val artist = song.mediaMetadata.artist?.toString()?.lowercase(Locale.ROOT) ?: ""
                    songMatchMap[Pair(title, artist)] = song.mediaId.toLong()
                }

                val playlistsArray = root.getJSONArray("playlists")
                playlistCount = playlistsArray.length()

                for (i in 0 until playlistsArray.length()) {
                    val playlistObj = playlistsArray.getJSONObject(i)
                    val playlistName = playlistObj.getString("name")

                    // Check if playlist already exists
                    val existingPlaylists = playlistDao.getAllPlaylists()
                    val existing = existingPlaylists.find {
                        it.playlist.name.equals(playlistName, ignoreCase = true)
                    }

                    val playlistId: Long
                    if (existing != null) {
                        // Merge into existing playlist
                        playlistId = existing.playlist.playlistId
                    } else {
                        // Create new playlist
                        val newPlaylist = Playlist(System.currentTimeMillis(), playlistName, null)
                        playlistDao.addPlaylist(newPlaylist)
                        // Re-fetch to get the actual ID
                        val refreshed = playlistDao.getAllPlaylists()
                        playlistId = refreshed.find {
                            it.playlist.name.equals(playlistName, ignoreCase = true)
                        }?.playlist?.playlistId ?: continue
                    }

                    val songsArray = playlistObj.getJSONArray("songs")
                    for (j in 0 until songsArray.length()) {
                        totalSongs++
                        val songObj = songsArray.getJSONObject(j)
                        val title = songObj.optString("title", "").lowercase(Locale.ROOT)
                        val artist = songObj.optString("artist", "").lowercase(Locale.ROOT)

                        if (title.isBlank()) {
                            missedSongs++
                            continue
                        }

                        val matchedId = songMatchMap[Pair(title, artist)]
                        if (matchedId != null) {
                            // Ensure MediaItem exists in the table
                            mediaItemDao.addMediaItem(MediaItem(matchedId))
                            // Add to playlist (INSERT OR REPLACE handles duplicates)
                            mediaItemDao.addMediaItemToPlaylist(playlistId, matchedId)
                        } else {
                            missedSongs++
                        }
                    }
                }
            }

            // Refresh the ViewModel
            DatabaseUtils.getPrivatePlaylist(libraryViewModel, context)

            withContext(Dispatchers.Main) {
                val message = if (missedSongs > 0) {
                    "Imported $playlistCount playlists, $missedSongs of $totalSongs songs not found"
                } else {
                    "Imported $playlistCount playlists successfully"
                }
                showSnackbar(
                    anchorView,
                    message,
                    isSuccess = missedSongs == 0,
                    context = context
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error importing playlists", e)
            withContext(Dispatchers.Main) {
                showSnackbar(
                    anchorView,
                    "Import failed: ${e.localizedMessage}",
                    isSuccess = false,
                    context = context
                )
            }
        }
    }

    /**
     * Show a styled snackbar matching SleepTimerDialog's design.
     */
    private fun showSnackbar(view: View, message: String, isSuccess: Boolean, context: Context) {
        val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
        snackbar.setBackgroundTint(android.graphics.Color.BLACK)
        snackbar.setTextColor(android.graphics.Color.WHITE)
        
        // Add checkmark or error icon based on success
        val tv = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)

        val iconRes = if (isSuccess) R.drawable.ic_check else R.drawable.ic_warning
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, iconRes)?.mutate()
        drawable?.setTint(android.graphics.Color.WHITE)
        tv.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
        tv.compoundDrawablePadding = (16 * context.resources.displayMetrics.density).toInt()

        snackbar.show()
    }
}
