package com.phantom.accord.ui.fragments

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Observer
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputEditText
import com.phantom.accord.R
import com.phantom.accord.logic.data.db.entity.PlaylistWithMediaItem
import com.phantom.accord.logic.enableEdgeToEdgePaddingListener
import com.phantom.accord.ui.LibraryViewModel
import com.phantom.accord.ui.adapters.LibraryPlaylistAdapter
import java.util.Locale

class LibraryPlaylistSubFragment : BaseFragment(), Observer<List<PlaylistWithMediaItem>> {
    private val libraryViewModel: LibraryViewModel by activityViewModels()
    private lateinit var adapter: LibraryPlaylistAdapter
    private lateinit var recyclerView: RecyclerView
    private var allPlaylists: List<PlaylistWithMediaItem> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_library_playlists, container, false)
        
        val topAppBar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
        val appBarLayout = rootView.findViewById<AppBarLayout>(R.id.appbarlayout)
        val searchInput = rootView.findViewById<TextInputEditText>(R.id.search_input)
        recyclerView = rootView.findViewById(R.id.playlist_recycler_view)

        appBarLayout.enableEdgeToEdgePaddingListener()

        topAppBar.setNavigationOnClickListener {
            (requireParentFragment() as BaseWrapperFragment).childFragmentManager.popBackStack()
        }

        libraryViewModel.privatePlaylistList.observeForever(this)

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterPlaylists(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        return rootView
    }

    override fun onChanged(value: List<PlaylistWithMediaItem>) {
        // Exclude 'favourite' since it's already a hardcoded item in the adapter
        allPlaylists = value.filter { it.playlist.name != "favourite" }
        updateAdapter(allPlaylists)
    }

    private fun filterPlaylists(query: String) {
        if (query.isBlank()) {
            updateAdapter(allPlaylists)
            return
        }
        val lowerQuery = query.lowercase(Locale.ROOT)
        val filtered = allPlaylists.filter {
            it.playlist.name.lowercase(Locale.ROOT).contains(lowerQuery)
        }
        updateAdapter(filtered)
    }

    private fun updateAdapter(list: List<PlaylistWithMediaItem>) {
        if (!isAdded) return
        adapter = LibraryPlaylistAdapter(this, libraryViewModel, list)
        recyclerView.adapter = adapter
    }

    override fun onDestroyView() {
        libraryViewModel.privatePlaylistList.removeObserver(this)
        super.onDestroyView()
    }
}
