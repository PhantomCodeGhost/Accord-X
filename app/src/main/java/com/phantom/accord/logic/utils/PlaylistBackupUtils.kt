package com.phantom.accord.logic.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.preference.PreferenceManager
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
    private const val JSON_VERSION = 2

    /**
     * Export app settings and favourite songs to a JSON file at the given URI.
     */
    suspend fun exportPlaylistsToJson(
        context: Context,
        libraryViewModel: LibraryViewModel,
        uri: Uri,
        anchorView: View
    ) {
        try {
            var favouritesCount = 0
            val json = withContext(Dispatchers.IO) {
                val database = AppDatabase.getInstance(context)
                val allPlaylists = database.playlistDao().getAllPlaylists()
                val allSongs = libraryViewModel.mediaItemList.value ?: emptyList()
                val songLookup = allSongs.associateBy { it.mediaId.toLong() }

                val root = JSONObject()
                root.put("version", JSON_VERSION)
                root.put("exportDate", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).format(Date()))

                // 1. Export App Settings
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val settingsObj = JSONObject()
                for ((key, value) in prefs.all) {
                    when (value) {
                        is Boolean -> settingsObj.put(key, value)
                        is Int -> settingsObj.put(key, value)
                        is Long -> settingsObj.put(key, value)
                        is Float -> settingsObj.put(key, value.toDouble())
                        is String -> settingsObj.put(key, value)
                        is Set<*> -> {
                            val array = JSONArray()
                            for (item in value) {
                                array.put(item.toString())
                            }
                            settingsObj.put(key, array)
                        }
                    }
                }
                root.put("settings", settingsObj)

                // 2. Export Favourites Only
                val favouritesArray = JSONArray()
                val favouritePlaylist = allPlaylists.find { it.playlist.name == "favourite" }
                if (favouritePlaylist != null) {
                    for (mediaItem in favouritePlaylist.mediaItems) {
                        val song = songLookup[mediaItem.mediaItemId]
                        if (song != null) {
                            val songObj = JSONObject()
                            songObj.put("title", song.mediaMetadata.title?.toString() ?: "")
                            songObj.put("artist", song.mediaMetadata.artist?.toString() ?: "")
                            songObj.put("album", song.mediaMetadata.albumTitle?.toString() ?: "")
                            favouritesArray.put(songObj)
                        }
                    }
                }
                root.put("favourites", favouritesArray)
                favouritesCount = favouritesArray.length()

                root.toString(2)
            }

            // Write to the file
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(json.toByteArray(Charsets.UTF_8))
                } ?: throw Exception("Could not open output stream")
            }

            withContext(Dispatchers.Main) {
                showSnackbar(
                    anchorView,
                    "Exported settings and $favouritesCount favourite songs successfully",
                    isSuccess = true,
                    context = context
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error exporting backup", e)
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
     * Import app settings and favourite songs from a JSON file at the given URI.
     */
    suspend fun importPlaylistsFromJson(
        context: Context,
        libraryViewModel: LibraryViewModel,
        uri: Uri,
        anchorView: View
    ) {
        try {
            var restoredSettingsCount = 0
            var restoredFavouritesCount = 0
            var totalFavourites = 0

            withContext(Dispatchers.IO) {
                val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw Exception("Could not open input stream")

                val root = JSONObject(jsonString)
                val version = root.optInt("version", 0)
                if (version < 1) {
                    throw Exception("Invalid backup file format")
                }

                // 1. Restore Settings
                if (root.has("settings")) {
                    val settingsObj = root.getJSONObject("settings")
                    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                    val editor = prefs.edit()
                    val keys = settingsObj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val value = settingsObj.get(key)
                        when (value) {
                            is Boolean -> editor.putBoolean(key, value)
                            is Int -> editor.putInt(key, value)
                            is Long -> editor.putLong(key, value)
                            is Double -> editor.putFloat(key, value.toFloat())
                            is String -> editor.putString(key, value)
                            is JSONArray -> {
                                val set = mutableSetOf<String>()
                                for (k in 0 until value.length()) {
                                    set.add(value.getString(k))
                                }
                                editor.putStringSet(key, set)
                            }
                        }
                        restoredSettingsCount++
                    }
                    editor.apply()
                }

                // 2. Restore Favourites
                val allSongs = libraryViewModel.mediaItemList.value ?: emptyList()
                val database = AppDatabase.getInstance(context)
                val playlistDao = database.playlistDao()
                val mediaItemDao = database.mediaItemDao()

                // Lookup maps for song matching
                val titleArtistMap = mutableMapOf<Pair<String, String>, Long>()
                val titleMap = mutableMapOf<String, Long>()
                for (song in allSongs) {
                    val title = song.mediaMetadata.title?.toString()?.lowercase(Locale.ROOT) ?: continue
                    val artist = song.mediaMetadata.artist?.toString()?.lowercase(Locale.ROOT) ?: ""
                    val songId = song.mediaId.toLong()
                    titleArtistMap[Pair(title, artist)] = songId
                    titleMap[title] = songId
                }

                // Ensure favourite playlist exists
                val allPlaylists = playlistDao.getAllPlaylists()
                var favPlaylist = allPlaylists.find { it.playlist.name == "favourite" }
                val favPlaylistId: Long
                if (favPlaylist == null) {
                    val newFav = Playlist(0, "favourite", null)
                    playlistDao.addPlaylist(newFav)
                    favPlaylistId = 0
                } else {
                    favPlaylistId = favPlaylist.playlist.playlistId
                }

                // Parse favourites from "favourites" array, or fallback to "playlists" array in v1
                val favSongsArray: JSONArray? = if (root.has("favourites")) {
                    root.getJSONArray("favourites")
                } else if (root.has("playlists")) {
                    val playlistsArray = root.getJSONArray("playlists")
                    var foundFavArray: JSONArray? = null
                    for (i in 0 until playlistsArray.length()) {
                        val pObj = playlistsArray.getJSONObject(i)
                        if (pObj.optString("name").equals("favourite", ignoreCase = true)) {
                            foundFavArray = pObj.optJSONArray("songs")
                            break
                        }
                    }
                    foundFavArray
                } else null

                if (favSongsArray != null) {
                    totalFavourites = favSongsArray.length()
                    for (j in 0 until favSongsArray.length()) {
                        val songObj = favSongsArray.getJSONObject(j)
                        val title = songObj.optString("title", "").lowercase(Locale.ROOT)
                        val artist = songObj.optString("artist", "").lowercase(Locale.ROOT)

                        if (title.isBlank()) continue

                        val matchedId = titleArtistMap[Pair(title, artist)] ?: titleMap[title]
                        if (matchedId != null) {
                            mediaItemDao.addMediaItem(MediaItem(matchedId))
                            mediaItemDao.addMediaItemToPlaylist(favPlaylistId, matchedId)
                            restoredFavouritesCount++
                        }
                    }
                }
            }

            // Refresh the ViewModel
            DatabaseUtils.getPrivatePlaylist(libraryViewModel, context)

            withContext(Dispatchers.Main) {
                val message = "Restored settings ($restoredSettingsCount items) and $restoredFavouritesCount favourite songs"
                showSnackbar(
                    anchorView,
                    message,
                    isSuccess = true,
                    context = context
                )
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error importing backup", e)
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

    private fun showSnackbar(view: View, message: String, isSuccess: Boolean, context: Context) {
        val snackbar = Snackbar.make(view, message, Snackbar.LENGTH_LONG)
        snackbar.setBackgroundTint(android.graphics.Color.BLACK)
        snackbar.setTextColor(android.graphics.Color.WHITE)
        
        val tv = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        val iconRes = if (isSuccess) R.drawable.ic_check else R.drawable.ic_warning
        val drawable = androidx.core.content.ContextCompat.getDrawable(context, iconRes)?.mutate()
        drawable?.setTint(android.graphics.Color.WHITE)
        tv.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null)
        tv.compoundDrawablePadding = (16 * context.resources.displayMetrics.density).toInt()

        snackbar.show()
    }
}
