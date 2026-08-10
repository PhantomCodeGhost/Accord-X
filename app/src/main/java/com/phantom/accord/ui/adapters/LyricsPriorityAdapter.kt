package com.phantom.accord.ui.adapters

import android.content.SharedPreferences
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.materialswitch.MaterialSwitch
import com.phantom.accord.R
import java.util.Collections

data class LyricsSource(val key: String, val title: String, val summary: String)

class LyricsPriorityAdapter(
    private val sources: MutableList<LyricsSource>,
    private val prefs: SharedPreferences,
    private val onDragStartListener: (RecyclerView.ViewHolder) -> Unit,
    private val onOrderChanged: () -> Unit
) : RecyclerView.Adapter<LyricsPriorityAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dragHandle: ImageView = view.findViewById(R.id.drag_handle)
        val title: TextView = view.findViewById(R.id.source_title)
        val summary: TextView = view.findViewById(R.id.source_summary)
        val switch: MaterialSwitch = view.findViewById(R.id.source_switch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lyrics_source, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val source = sources[position]
        holder.title.text = source.title
        holder.summary.text = source.summary
        holder.switch.isChecked = prefs.getBoolean(source.key, true)

        holder.switch.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(source.key, isChecked).apply()
        }

        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                onDragStartListener(holder)
            }
            false
        }
    }

    override fun getItemCount() = sources.size

    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(sources, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(sources, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        onOrderChanged()
    }
}
