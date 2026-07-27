package com.phantom.accord.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.phantom.accord.R
import com.phantom.accord.logic.utils.DatabaseUtils
import kotlinx.coroutines.launch

class SongOptionsDialog(private val mediaItem: androidx.media3.common.MediaItem) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_song_options, container, false)
        
        val albumCoverImageView = view.findViewById<ImageView>(R.id.dialog_album_art)
        val titleTextView = view.findViewById<TextView>(R.id.dialog_song_title)
        val artistTextView = view.findViewById<TextView>(R.id.dialog_song_artist)
        val albumTextView = view.findViewById<TextView>(R.id.dialog_song_album)

        val metadata = mediaItem.mediaMetadata

        albumCoverImageView.load(metadata.artworkUri) {
            crossfade(true)
            placeholder(R.drawable.ic_default_cover)
            error(R.drawable.ic_default_cover)
        }
        
        titleTextView.text = metadata.title
        artistTextView.text = metadata.artist
        albumTextView.text = metadata.albumTitle

        view.findViewById<View>(R.id.option_sleep_timer)?.setOnClickListener {
            dismiss()
            val sleepTimerDialog = SleepTimerDialog()
            sleepTimerDialog.show(parentFragmentManager, SleepTimerDialog.TAG)
        }

        view.findViewById<View>(R.id.option_view_credits)?.setOnClickListener {
            dismiss()
            val creditsDialog = CreditsDialog(mediaItem)
            creditsDialog.show(parentFragmentManager, CreditsDialog.TAG)
        }

        view.findViewById<View>(R.id.option_create_playlist)?.setOnClickListener {
            dismiss()
            val addToPlaylistDialog = AddToPlaylistDialog(mediaItem)
            addToPlaylistDialog.show(parentFragmentManager, AddToPlaylistDialog.TAG)
        }


        return view
    }
    
    companion object {
        const val TAG = "SongOptionsDialog"
    }
}
