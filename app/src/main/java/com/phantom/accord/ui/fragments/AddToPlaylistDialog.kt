package com.phantom.accord.ui.fragments

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.phantom.accord.R
import com.phantom.accord.logic.data.db.entity.PlaylistWithMediaItem
import com.phantom.accord.logic.utils.DatabaseUtils
import com.phantom.accord.ui.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import coil3.load
import coil3.request.error
import coil3.request.placeholder

class AddToPlaylistDialog(private val mediaItem: androidx.media3.common.MediaItem) : BottomSheetDialogFragment() {

    private val libraryViewModel: LibraryViewModel by activityViewModels()
    private lateinit var adapter: PlaylistAdapter
    private var allPlaylists: List<PlaylistWithMediaItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_add_to_playlist, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.playlist_recycler_view)

        adapter = PlaylistAdapter()
        recyclerView.adapter = adapter

        libraryViewModel.privatePlaylistList.observe(viewLifecycleOwner) { playlists ->
            allPlaylists = playlists ?: emptyList()
            filterList("")
        }

        return view
    }

    private fun filterList(query: String) {
        val filtered = allPlaylists.filter {
            it.playlist.name != "favourite" && 
            it.playlist.name.contains(query, ignoreCase = true)
        }
        adapter.submitList(filtered)
    }

    private inner class PlaylistAdapter : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {

        private var playlists = emptyList<PlaylistWithMediaItem>()
        private val ITEM_NEW_PLAYLIST = 0
        private val ITEM_FAVOURITES = 1
        private val ITEM_PLAYLIST = 2

        fun submitList(list: List<PlaylistWithMediaItem>) {
            playlists = list
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = playlists.size + 2 // +2 for New Playlist and Favourites

        override fun getItemViewType(position: Int): Int {
            return when (position) {
                0 -> ITEM_NEW_PLAYLIST
                1 -> ITEM_FAVOURITES
                else -> ITEM_PLAYLIST
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.adapter_add_to_playlist, parent, false)
            return ViewHolder(view)
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
                        val ctx = requireContext()
                        val appContext = ctx.applicationContext
                        val activityView = requireActivity().findViewById<View>(android.R.id.content)
                        val input = EditText(ctx)
                        input.hint = "Playlist Name"
                        com.google.android.material.dialog.MaterialAlertDialogBuilder(ctx)
                            .setTitle("New Playlist")
                            .setView(input)
                            .setPositiveButton("Create") { _, _ ->
                                val name = input.text.toString()
                                if (name.isNotBlank()) {
                                    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            val newPlaylist = com.phantom.accord.logic.data.db.entity.Playlist(System.currentTimeMillis(), name, null)
                                            val playlistDao = com.phantom.accord.logic.data.db.AppDatabase.getInstance(appContext).playlistDao()
                                            playlistDao.addPlaylist(newPlaylist)
                                            
                                            val allPlaylists = playlistDao.getAllPlaylists()
                                            val created = allPlaylists.find { it.playlist.name == name }
                                            
                                            if (created != null) {
                                                DatabaseUtils.addSongToPlaylist(mediaItem.mediaId.toLong(), created.playlist.playlistId, libraryViewModel, appContext)
                                                DatabaseUtils.getPrivatePlaylist(libraryViewModel, appContext)
                                                
                                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                    val sb = com.google.android.material.snackbar.Snackbar.make(activityView, "Playlist created & song added", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                                                    sb.setBackgroundTint(android.graphics.Color.BLACK)
                                                    sb.setTextColor(android.graphics.Color.WHITE)
                                                    sb.show()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                                val sb = com.google.android.material.snackbar.Snackbar.make(activityView, "Error: ${e.message}", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                                                sb.setBackgroundTint(android.graphics.Color.BLACK)
                                                sb.setTextColor(android.graphics.Color.WHITE)
                                                sb.show()
                                            }
                                        }
                                    }
                                }
                                dismiss()
                            }
                            .setNegativeButton("Cancel") { _, _ -> dismiss() }
                            .setOnCancelListener { dismiss() }
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
                        val appContext = requireContext().applicationContext
                        val activityView = requireActivity().findViewById<View>(android.R.id.content)
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            try {
                                if (!DatabaseUtils.isFavourite(mediaItem.mediaId.toLong(), libraryViewModel)) {
                                    DatabaseUtils.favouriteSong(mediaItem.mediaId.toLong(), libraryViewModel, appContext)
                                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                                        val sb = com.google.android.material.snackbar.Snackbar.make(activityView, "Added to favourites", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                                        sb.setBackgroundTint(android.graphics.Color.BLACK)
                                        sb.setTextColor(android.graphics.Color.WHITE)
                                        sb.show()
                                    }
                                }
                            } catch (e: Exception) {
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    val sb = com.google.android.material.snackbar.Snackbar.make(activityView, "Error adding to favourites", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                                    sb.setBackgroundTint(android.graphics.Color.BLACK)
                                    sb.setTextColor(android.graphics.Color.WHITE)
                                    sb.show()
                                }
                            }
                        }
                        dismiss()
                    }
                }
                ITEM_PLAYLIST -> {
                    val playlistItem = playlists[position - 2]
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
                        val appContext = requireContext().applicationContext
                        val activityView = requireActivity().findViewById<View>(android.R.id.content)
                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                            try {
                                DatabaseUtils.addSongToPlaylist(mediaItem.mediaId.toLong(), playlistItem.playlist.playlistId, libraryViewModel, appContext)
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    val sb = com.google.android.material.snackbar.Snackbar.make(activityView, "Added to playlist", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                                    sb.setBackgroundTint(android.graphics.Color.BLACK)
                                    sb.setTextColor(android.graphics.Color.WHITE)
                                    sb.show()
                                }
                            } catch (e: Exception) {
                                kotlinx.coroutines.withContext(Dispatchers.Main) {
                                    val sb = com.google.android.material.snackbar.Snackbar.make(activityView, "Error adding to playlist", com.google.android.material.snackbar.Snackbar.LENGTH_SHORT)
                                    sb.setBackgroundTint(android.graphics.Color.BLACK)
                                    sb.setTextColor(android.graphics.Color.WHITE)
                                    sb.show()
                                }
                            }
                        }
                        dismiss()
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

    companion object {
        const val TAG = "AddToPlaylistDialog"
    }
}
