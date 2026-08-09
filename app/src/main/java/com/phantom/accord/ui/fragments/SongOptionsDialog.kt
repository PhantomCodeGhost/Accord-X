package com.phantom.accord.ui.fragments

import android.content.ContentUris
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.phantom.accord.R
import com.phantom.accord.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

        view.findViewById<View>(R.id.option_tag_editor)?.setOnClickListener {
            dismiss()
            val activity = activity
            if (activity is MainActivity) {
                activity.startFragment(TagEditorFragment.newInstance(mediaItem))
            }
        }

        view.findViewById<View>(R.id.option_edit_lyrics)?.setOnClickListener {
            dismiss()
            val activity = activity
            if (activity is MainActivity) {
                activity.startFragment(LyricsEditorFragment.newInstance(mediaItem))
            }
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

        view.findViewById<View>(R.id.option_delete_song)?.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Delete song?")
                .setMessage("\"${metadata.title}\" will be permanently deleted from your device.")
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .setPositiveButton("Delete") { _, _ ->
                    deleteSong()
                }
                .show()
        }

        return view
    }

    private fun deleteSong() {
        val mediaIdLong = mediaItem.mediaId.toLongOrNull()
        if (mediaIdLong == null) {
            Toast.makeText(context, "Cannot delete: invalid media ID", Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }

        val deleteUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaIdLong)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pendingIntent = MediaStore.createDeleteRequest(requireContext().contentResolver, listOf(deleteUri))
                    withContext(Dispatchers.Main) {
                        requireActivity().startIntentSenderForResult(
                            pendingIntent.intentSender,
                            42,
                            null, 0, 0, 0, null
                        )
                    }
                } else {
                    val rows = requireContext().contentResolver.delete(deleteUri, null, null)
                    withContext(Dispatchers.Main) {
                        if (rows > 0) {
                            Toast.makeText(context, "Song deleted", Toast.LENGTH_SHORT).show()
                            val activity = activity
                            if (activity is MainActivity) {
                                activity.updateLibrary()
                            }
                        } else {
                            Toast.makeText(context, "Failed to delete song", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    dismiss()
                }
            } catch (e: Exception) {
                android.util.Log.e("SongOptionsDialog", "Failed to delete song", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    dismiss()
                }
            }
        }
    }
    
    companion object {
        const val TAG = "SongOptionsDialog"
    }
}
