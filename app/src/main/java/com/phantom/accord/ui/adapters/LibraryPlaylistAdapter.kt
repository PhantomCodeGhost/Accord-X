package com.phantom.accord.ui.adapters

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.phantom.accord.R
import com.phantom.accord.logic.data.db.AppDatabase
import com.phantom.accord.logic.data.db.entity.Playlist
import com.phantom.accord.logic.data.db.entity.PlaylistWithMediaItem
import com.phantom.accord.logic.findBaseWrapperFragment
import com.phantom.accord.logic.utils.DatabaseUtils
import com.phantom.accord.ui.LibraryViewModel
import com.phantom.accord.ui.fragments.LibrarySongSubFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import coil3.load
import coil3.request.error
import coil3.request.placeholder

class LibraryPlaylistAdapter(
    private val fragment: Fragment,
    private val libraryViewModel: LibraryViewModel,
    private val playlists: List<PlaylistWithMediaItem>
) : RecyclerView.Adapter<LibraryPlaylistAdapter.ViewHolder>() {

    companion object {
        private const val ITEM_NEW_PLAYLIST = 0
        private const val ITEM_FAVOURITES = 1
        private const val ITEM_AUTO = 2
        private const val ITEM_PLAYLIST = 3
    }

    override fun getItemViewType(position: Int): Int {
        return when (position) {
            0 -> ITEM_NEW_PLAYLIST
            1 -> ITEM_FAVOURITES
            2 -> ITEM_AUTO
            else -> ITEM_PLAYLIST
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.adapter_add_to_playlist, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int {
        // "New Playlist...", "Favourite Songs", "Auto Playlist", + all custom playlists
        return 3 + playlists.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when (getItemViewType(position)) {
            ITEM_NEW_PLAYLIST -> {
                holder.title.text = "New Playlist..."
                holder.title.setTextColor(Color.parseColor("#FF0000"))
                holder.iconImage.setImageResource(R.drawable.ic_add)
                holder.iconImage.imageTintList = ColorStateList.valueOf(Color.parseColor("#FF0000"))
                holder.iconCard.setCardBackgroundColor(Color.parseColor("#1AFFFFFF")) // Dark grey
                
                holder.iconImage.visibility = View.VISIBLE
                holder.coverImage.visibility = View.GONE
                holder.gridCoverFrame.visibility = View.GONE
                
                holder.itemView.setOnClickListener {
                    val ctx = fragment.requireContext()
                    val input = EditText(ctx)
                    input.hint = "Playlist Name"
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                        .setTitle("New Playlist")
                        .setView(input)
                        .setPositiveButton("Create") { _, _ ->
                            val name = input.text.toString()
                            if (name.isNotBlank()) {
                                fragment.lifecycleScope.launch(Dispatchers.IO) {
                                    val newPlaylist = Playlist(System.currentTimeMillis(), name, null)
                                    AppDatabase.getInstance(ctx.applicationContext).playlistDao().addPlaylist(newPlaylist)
                                    DatabaseUtils.getPrivatePlaylist(libraryViewModel, ctx.applicationContext)
                                }
                            }
                        }
                        .setNegativeButton("Cancel", null)
                        .show()
                }
            }
            ITEM_FAVOURITES -> {
                holder.title.text = "Favourite Songs"
                holder.title.setTextColor(Color.WHITE)
                holder.iconImage.setImageResource(R.drawable.ic_star_filled)
                holder.iconImage.imageTintList = ColorStateList.valueOf(Color.parseColor("#FF0000"))
                holder.iconCard.setCardBackgroundColor(Color.WHITE)
                
                holder.iconImage.visibility = View.VISIBLE
                holder.coverImage.visibility = View.GONE
                holder.gridCoverFrame.visibility = View.GONE
                
                holder.itemView.setOnClickListener {
                    // Navigate to favourites. We can reuse LibrarySongSubFragment?
                    // Actually, LibrarySongSubFragment is hardcoded to show 'favourite' playlist!
                    fragment.findBaseWrapperFragment()?.replaceFragment(LibrarySongSubFragment())
                }
            }
            ITEM_AUTO -> {
                holder.title.text = "Auto Playlist (Folders)"
                holder.title.setTextColor(Color.parseColor("#FF0000"))
                holder.iconImage.setImageResource(R.drawable.ic_folder)
                holder.iconImage.imageTintList = ColorStateList.valueOf(Color.parseColor("#FF0000"))
                holder.iconCard.setCardBackgroundColor(Color.parseColor("#1AFFFFFF"))
                
                holder.iconImage.visibility = View.VISIBLE
                holder.coverImage.visibility = View.GONE
                holder.gridCoverFrame.visibility = View.GONE
                
                holder.itemView.setOnClickListener {
                    val ctx = fragment.requireContext()
                    val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(ctx)
                    val uriString = prefs.getString("auto_playlist_folder_uri", null)
                    
                    if (uriString == null) {
                        android.widget.Toast.makeText(ctx, "Please select a music folder first.", android.widget.Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    
                    val uri = android.net.Uri.parse(uriString)
                    val path = uri.path
                    if (path == null) {
                        android.widget.Toast.makeText(ctx, "Invalid folder URI", android.widget.Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    
                    val split = path.split(":")
                    var absolutePath = ""
                    if (split.size > 1 && split[0].contains("primary")) {
                        absolutePath = android.os.Environment.getExternalStorageDirectory().absolutePath + "/" + split[1]
                    } else if (split.size > 1) {
                        val volumeId = split[0].substringAfterLast("/")
                        absolutePath = "/storage/$volumeId/${split[1]}"
                    } else {
                        android.widget.Toast.makeText(ctx, "Unsupported storage URI", android.widget.Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                    
                    android.widget.Toast.makeText(ctx, "Scanning selected folder...", android.widget.Toast.LENGTH_SHORT).show()
                    
                    fragment.lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            val musicDir = java.io.File(absolutePath)
                                    

                            if (musicDir.exists() && musicDir.isDirectory) {
                                val folders = musicDir.listFiles { file -> file.isDirectory && !file.isHidden && !file.name.startsWith(".") }
                                val db = AppDatabase.getInstance(ctx.applicationContext)
                                val allMediaItems = libraryViewModel.mediaItemList.value ?: emptyList()
                                        
                                        folders?.forEach { folder ->
                                            val playlistName = folder.name
                                            val songsInFolder = allMediaItems.filter { item ->
                                                val path = item.localConfiguration?.uri?.path
                                                path != null && path.startsWith(folder.absolutePath)
                                            }
                                            
                                            if (songsInFolder.isNotEmpty()) {
                                                val allPlaylists = db.playlistDao().getAllPlaylists()
                                                var targetPlaylist = allPlaylists.find { it.playlist.name == playlistName }
                                                
                                                if (targetPlaylist == null) {
                                                    val newPlaylist = Playlist(System.currentTimeMillis(), playlistName, null)
                                                    db.playlistDao().addPlaylist(newPlaylist)
                                                    targetPlaylist = db.playlistDao().getAllPlaylists().find { it.playlist.name == playlistName }
                                                }
                                                
                                                if (targetPlaylist != null) {
                                                    val existingMediaIds = targetPlaylist.mediaItems.map { it.mediaItemId }
                                                    songsInFolder.forEach { song ->
                                                        val mediaId = song.mediaId.toLongOrNull()
                                                        if (mediaId != null && !existingMediaIds.contains(mediaId)) {
                                                            com.phantom.accord.logic.utils.DatabaseUtils.addSongToPlaylist(mediaId, targetPlaylist.playlist.playlistId, libraryViewModel, ctx.applicationContext)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                val backupDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
                                val backupFile = java.io.File(backupDir, "Accord_Backup.json")
                                val uri = android.net.Uri.fromFile(backupFile)
                                        
                                com.phantom.accord.logic.utils.PlaylistBackupUtils.exportPlaylistsToJson(
                                    ctx, libraryViewModel, uri, fragment.requireView()
                                )
                                        
                                DatabaseUtils.getPrivatePlaylist(libraryViewModel, ctx.applicationContext)
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    android.widget.Toast.makeText(ctx, "Auto playlists created and backed up to Documents/Accord_Backup.json", android.widget.Toast.LENGTH_LONG).show()
                                }
                            } else {
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    android.widget.Toast.makeText(ctx, "Selected folder not found", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(ctx, "Auto playlist failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            }
            ITEM_PLAYLIST -> {
                val playlistItem = playlists[position - 3]
                holder.title.text = playlistItem.playlist.name
                holder.title.setTextColor(Color.WHITE)
                
                holder.iconCard.setCardBackgroundColor(Color.parseColor("#1AFFFFFF"))
                
                val songs = playlistItem.mediaItems
                val allMedia = libraryViewModel.mediaItemList.value.orEmpty()
                val songUris = songs.mapNotNull { dbItem -> 
                    allMedia.find { it.mediaId == dbItem.mediaItemId.toString() }?.mediaMetadata?.artworkUri 
                }

                if (songUris.isEmpty()) {
                    holder.iconImage.visibility = View.VISIBLE
                    holder.coverImage.visibility = View.GONE
                    holder.gridCoverFrame.visibility = View.GONE
                    holder.iconImage.setImageResource(R.drawable.ic_default_cover_playlist)
                    holder.iconImage.imageTintList = null
                } else if (songUris.size < 4) {
                    holder.iconImage.visibility = View.GONE
                    holder.gridCoverFrame.visibility = View.GONE
                    holder.coverImage.visibility = View.VISIBLE
                    holder.coverImage.load(songUris[0]) {
                        error(R.drawable.ic_default_cover_playlist)
                        placeholder(R.drawable.ic_default_cover_playlist)
                    }
                } else {
                    holder.iconImage.visibility = View.GONE
                    holder.coverImage.visibility = View.GONE
                    holder.gridCoverFrame.visibility = View.VISIBLE
                    val imageViews = listOf(holder.coverImage1, holder.coverImage2, holder.coverImage3, holder.coverImage4)
                    for (i in 0..3) {
                        imageViews[i].load(songUris[i]) {
                            error(R.drawable.ic_default_cover_playlist)
                            placeholder(R.drawable.ic_default_cover_playlist)
                        }
                    }
                }
                
                holder.itemView.setOnClickListener {
                    // Navigate to custom playlist.
                    // LibrarySongSubFragment currently is hardcoded to favourite. We'll need to pass arguments or handle it.
                    // For now, let's just use GeneralSubFragment or pass bundle.
                    // Wait, GeneralSubFragment takes a position and Item!
                    // Let's pass the playlist name to LibrarySongSubFragment and make it dynamic.
                    val subFrag = LibrarySongSubFragment().apply {
                        arguments = android.os.Bundle().apply {
                            putString("playlist_name", playlistItem.playlist.name)
                        }
                    }
                    fragment.findBaseWrapperFragment()?.replaceFragment(subFrag)
                }
            }
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconCard = view.findViewById<com.google.android.material.card.MaterialCardView>(R.id.icon_card)
        val iconImage = view.findViewById<ImageView>(R.id.icon_image)
        val coverImage = view.findViewById<ImageView>(R.id.cover_image)
        val gridCoverFrame = view.findViewById<View>(R.id.grid_cover_frame)
        val coverImage1 = view.findViewById<ImageView>(R.id.cover_image_1)
        val coverImage2 = view.findViewById<ImageView>(R.id.cover_image_2)
        val coverImage3 = view.findViewById<ImageView>(R.id.cover_image_3)
        val coverImage4 = view.findViewById<ImageView>(R.id.cover_image_4)
        val title = view.findViewById<TextView>(R.id.title)
    }
}
