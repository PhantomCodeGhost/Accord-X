package com.phantom.accord.ui.fragments

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
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
            ContextCompat.getString(requireContext(), R.string.category_songs)
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

        topAppBar.setNavigationOnClickListener {
            Log.d("TAG", "ok${requireParentFragment().childFragmentManager.fragments.size}")
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
        Log.d("TAG", "MEASURETIME: $measureTime")
    }

    override fun onDestroy() {
        libraryViewModel.privatePlaylistList.removeObserver(this)
        super.onDestroy()
    }

}