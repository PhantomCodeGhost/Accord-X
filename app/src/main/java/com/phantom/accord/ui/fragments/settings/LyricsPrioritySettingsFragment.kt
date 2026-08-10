package com.phantom.accord.ui.fragments.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.phantom.accord.R
import com.phantom.accord.logic.enableEdgeToEdgePaddingListener
import com.phantom.accord.ui.adapters.LyricsPriorityAdapter
import com.phantom.accord.ui.adapters.LyricsSource
import com.phantom.accord.ui.fragments.BaseFragment
import org.json.JSONArray
import org.json.JSONException

class LyricsPrioritySettingsFragment : BaseFragment() {

    private lateinit var adapter: LyricsPriorityAdapter
    private val sources = mutableListOf<LyricsSource>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_lyrics_priority_settings, container, false)
        val topAppBar = rootView.findViewById<MaterialToolbar>(R.id.topAppBar)
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())

        rootView.findViewById<AppBarLayout>(R.id.appbarlayout).enableEdgeToEdgePaddingListener()

        topAppBar.setNavigationOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        val defaultSources = listOf(
            LyricsSource("lyrics_source_paxsenix", "Paxsenix (Apple Music)", "Proxy for Apple Music lyrics (supports word-by-word)"),
            LyricsSource("lyrics_source_musixmatch", "Musixmatch", "Musixmatch Macro API (supports word-by-word)"),
            LyricsSource("lyrics_source_lrclib", "LRCLIB", "Global open-source lyrics database"),
            LyricsSource("lyrics_source_netease", "NetEase", "NetEase Cloud Music lyrics API"),
            LyricsSource("lyrics_source_kugou", "Kugou", "Kugou Music lyrics API"),
            LyricsSource("lyrics_source_kuwo", "Kuwo", "Kuwo Music lyrics API")
        )

        // Load saved order
        val savedOrderStr = prefs.getString("lyrics_source_order", null)
        if (savedOrderStr != null) {
            try {
                val array = JSONArray(savedOrderStr)
                for (i in 0 until array.length()) {
                    val key = array.getString(i)
                    defaultSources.find { it.key == key }?.let { sources.add(it) }
                }
                // Add any missing new sources at the end
                for (source in defaultSources) {
                    if (!sources.contains(source)) {
                        sources.add(source)
                    }
                }
            } catch (e: JSONException) {
                sources.addAll(defaultSources)
            }
        } else {
            sources.addAll(defaultSources)
        }

        val recyclerView = rootView.findViewById<RecyclerView>(R.id.recyclerview)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                adapter.moveItem(viewHolder.adapterPosition, target.adapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled() = false
        })
        
        adapter = LyricsPriorityAdapter(sources, prefs, { viewHolder ->
            itemTouchHelper.startDrag(viewHolder)
        }, {
            saveOrder()
        })
        
        recyclerView.adapter = adapter
        itemTouchHelper.attachToRecyclerView(recyclerView)

        return rootView
    }

    private fun saveOrder() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val array = JSONArray()
        for (source in sources) {
            array.put(source.key)
        }
        prefs.edit().putString("lyrics_source_order", array.toString()).apply()
    }
}
