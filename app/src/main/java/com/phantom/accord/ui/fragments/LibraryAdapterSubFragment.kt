package com.phantom.accord.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.activityViewModels
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.phantom.accord.R
import com.phantom.accord.logic.enableEdgeToEdgePaddingListener
import com.phantom.accord.logic.ui.MyRecyclerView
import com.phantom.accord.ui.LibraryViewModel
import com.phantom.accord.ui.adapters.AlbumAdapter
import com.phantom.accord.ui.adapters.ArtistAdapter

class LibraryAdapterSubFragment : BaseFragment() {
    private val libraryViewModel: LibraryViewModel by activityViewModels()
    private lateinit var adapter: AdapterFragment.BaseInterface<*>

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

        val categoryId = arguments?.getInt("ID", -1) ?: -1
        
        adapter = when (categoryId) {
            R.id.albums -> AlbumAdapter(this, libraryViewModel.albumItemList)
            R.id.artists -> ArtistAdapter(this, libraryViewModel.artistItemList, libraryViewModel.albumArtistItemList)
            else -> throw IllegalArgumentException("invalid ID value")
        }

        collapsingToolbarLayout.title = when (categoryId) {
            R.id.albums -> ContextCompat.getString(requireContext(), R.string.category_albums)
            R.id.artists -> ContextCompat.getString(requireContext(), R.string.category_artists)
            else -> ""
        }

        recyclerView.enableEdgeToEdgePaddingListener()
        recyclerView.setAppBar(appBarLayout)
        recyclerView.adapter = adapter.concatAdapter
        recyclerView.fastScroll(adapter, adapter.itemHeightHelper)

        topAppBar.setNavigationOnClickListener {
            (requireParentFragment() as BaseWrapperFragment).childFragmentManager.popBackStack()
        }

        return rootView
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        adapter.concatAdapter.adapters.forEach {
            it.onDetachedFromRecyclerView(requireView().findViewById(R.id.recyclerview))
        }
    }
}
