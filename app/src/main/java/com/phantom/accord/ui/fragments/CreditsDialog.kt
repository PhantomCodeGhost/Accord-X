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
        val fileSizeChipCard = view.findViewById<View>(R.id.file_size_chip_card)
        val fileSizeChip = view.findViewById<TextView>(R.id.file_size_chip)
        
        fileSizeChipCard.visibility = View.GONE
        
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
                    fileSizeChipCard.visibility = View.VISIBLE
                    
                    // Try to read ID3 Comment for Lyrics Source
                    val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
                    val tag = audioFile.tag
                    val comment = tag?.getFirst(org.jaudiotagger.tag.FieldKey.COMMENT)
                    if (!comment.isNullOrBlank() && comment.startsWith("Lyrics Source:")) {
                        val source = comment.replace("Lyrics Source:", "").trim()
                        appendField("Lyrics Source", source)
                        // Re-set the HTML text since we added a new field
                        creditsText.text = android.text.Html.fromHtml(sb.toString(), android.text.Html.FROM_HTML_MODE_COMPACT)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Error getting file size", e)
            }
        }

        return view
    }
    
    companion object {
        const val TAG = "CreditsDialog"
    }
}
