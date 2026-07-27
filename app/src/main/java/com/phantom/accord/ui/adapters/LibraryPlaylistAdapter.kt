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
        private const val ITEM_PLAYLIST = 2
    }

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

    override fun getItemCount(): Int {
        // "New Playlist...", "Favourite Songs", + all custom playlists
        return 2 + playlists.size
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
