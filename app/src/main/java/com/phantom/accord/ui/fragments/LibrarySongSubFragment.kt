package com.phantom.accord.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.phantom.accord.R
import com.phantom.accord.logic.data.db.entity.PlaylistWithMediaItem
import com.phantom.accord.logic.enableEdgeToEdgePaddingListener
import com.phantom.accord.logic.ui.MyRecyclerView
import com.phantom.accord.ui.LibraryViewModel
import com.phantom.accord.ui.adapters.SongAdapter
import kotlin.system.measureTimeMillis

class LibrarySongSubFragment : BaseFragment(), Observer<List<PlaylistWithMediaItem>>{
    private val libraryViewModel: LibraryViewModel by activityViewModels()
    private lateinit var songAdapter: SongAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_general_sub, container, false)
        val topAppBar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
        val collapsingToolbarLayout =
            rootView.findViewById<CollapsingToolbarLayout>(R.id.collapsingtoolbar)
        val recyclerView = rootView.findViewById<MyRecyclerView>(R.id.recyclerview)
        val appBarLayout = rootView.findViewById<AppBarLayout>(R.id.appbarlayout)
        appBarLayout.enableEdgeToEdgePaddingListener()

        if (libraryViewModel.albumItemList.value == null) {
            // (still better than crashing, though)
            requireParentFragment().childFragmentManager.popBackStack()
            return null
        }

        val playlistName = arguments?.getString("playlist_name") ?: "favourite"
        val playlist = libraryViewModel.privatePlaylistList.value!!.find {
            it.playlist.name == playlistName
        } ?: return null // Prevent crash if playlist deleted

        val filteredAndSortedList = playlist.mediaItems.mapNotNull { id ->
            libraryViewModel.mediaItemList.value!!.find { it.mediaId.toLong() == id.mediaItemId }
        }

        // Show title text.
        val titleText = if (playlistName == "favourite") {
            ContextCompat.getString(requireContext(), R.string.playlist_favourite)
        } else {
            playlistName
        }
        collapsingToolbarLayout.title = titleText

        songAdapter =
            SongAdapter(
                this,
                songList = filteredAndSortedList,
                true,
                null,
                ownsView = true,
                isSubFragment = true
            )

        recyclerView.enableEdgeToEdgePaddingListener()
        recyclerView.setAppBar(appBarLayout)
        recyclerView.adapter = songAdapter.concatAdapter

        // Build FastScroller.
        recyclerView.fastScroll(songAdapter, songAdapter.itemHeightHelper)

        val itemTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0, androidx.recyclerview.widget.ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: androidx.recyclerview.widget.RecyclerView,
                viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                target: androidx.recyclerview.widget.RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return
                
                val songList = songAdapter.getSongList()
                if (position >= songList.size) return
                val song = songList[position]
                val mediaId = song.mediaId.toLongOrNull() ?: return
                
                val targetPlaylistName = arguments?.getString("playlist_name") ?: "favourite"
                val targetPlaylist = libraryViewModel.privatePlaylistList.value?.find { it.playlist.name == targetPlaylistName }
                if (targetPlaylist != null) {
                    viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        com.phantom.accord.logic.utils.DatabaseUtils.removeFromPlaylist(
                            mediaId,
                            targetPlaylist.playlist.playlistId,
                            libraryViewModel,
                            requireContext()
                        )
                    }
                }
            }

            override fun onChildDraw(
                c: android.graphics.Canvas,
                recyclerView: androidx.recyclerview.widget.RecyclerView,
                viewHolder: androidx.recyclerview.widget.RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val itemView = viewHolder.itemView
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.RED
                    }
                    c.drawRect(
                        itemView.right + dX,
                        itemView.top.toFloat(),
                        itemView.right.toFloat(),
                        itemView.bottom.toFloat(),
                        paint
                    )
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)

        topAppBar.setNavigationOnClickListener {
            (requireParentFragment() as BaseWrapperFragment).childFragmentManager.popBackStack()
        }

        libraryViewModel.privatePlaylistList.observeForever(this)

        return rootView
    }

    override fun onChanged(value: List<PlaylistWithMediaItem>)  {
        val measureTime = measureTimeMillis {
            val mediaItemMap = libraryViewModel.mediaItemList.value?.associateBy { it.mediaId.toLong() }

            val playlistName = arguments?.getString("playlist_name") ?: "favourite"
            val targetPlaylist = value.find { it1 -> it1.playlist.name == playlistName }
            targetPlaylist?.let { it1 ->
                mediaItemMap?.let { map ->
                    val updatedList = it1.mediaItems.mapNotNull { mediaItem ->
                        map[mediaItem.mediaItemId]
                    }
                    songAdapter.updateList(updatedList, now = false, canDiff = true)
                }
            }

        }
    }

    override fun onDestroy() {
        libraryViewModel.privatePlaylistList.removeObserver(this)
        super.onDestroy()
    }

}