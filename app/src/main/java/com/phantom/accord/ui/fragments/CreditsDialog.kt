package com.phantom.accord.ui.fragments

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.media3.common.MediaMetadata
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.phantom.accord.R

class CreditsDialog(private val mediaItem: androidx.media3.common.MediaItem) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_credits, container, false)
        
        val creditsText = view.findViewById<TextView>(R.id.credits_text)
        val fileSizeChip = view.findViewById<TextView>(R.id.file_size_chip)
        
        val metadata = mediaItem.mediaMetadata
        val sb = java.lang.StringBuilder()
        
        fun appendField(label: String, value: CharSequence?) {
            if (!value.isNullOrBlank()) {
                sb.append("<b>$label:</b> $value<br><br>")
            }
        }
        
        appendField("Title", metadata.title)
        appendField("Artist", metadata.artist)
        appendField("Album", metadata.albumTitle)
        appendField("Album Artist", metadata.albumArtist)
        appendField("Composer", metadata.composer)
        appendField("Writer", metadata.writer)
        appendField("Genre", metadata.genre)
        appendField("Release Year", metadata.releaseYear?.toString())
        appendField("Track Number", metadata.trackNumber?.toString())
        appendField("Disc Number", metadata.discNumber?.toString())
        
        if (sb.isEmpty()) {
            sb.append("No metadata available.")
        }
        
        creditsText.text = android.text.Html.fromHtml(sb.toString(), android.text.Html.FROM_HTML_MODE_COMPACT)
        
        // Add file size and extension to chip
        val uri = mediaItem.localConfiguration?.uri
        if (uri != null && uri.scheme == "file") {
            try {
                val file = java.io.File(uri.path!!)
                if (file.exists()) {
                    val sizeInMb = file.length() / (1024.0 * 1024.0)
                    val ext = file.extension.uppercase()
                    val sizeStr = String.format("%.1fMB", sizeInMb)
                    fileSizeChip.text = "$sizeStr • $ext"
                    fileSizeChip.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return view
    }
    
    companion object {
        const val TAG = "CreditsDialog"
    }
}
