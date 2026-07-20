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

class CreditsDialog(private val metadata: MediaMetadata) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_credits, container, false)
        
        val creditsText = view.findViewById<TextView>(R.id.credits_text)
        
        val sb = StringBuilder()
        
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
        
        creditsText.text = Html.fromHtml(sb.toString(), Html.FROM_HTML_MODE_COMPACT)
        
        return view
    }
    
    companion object {
        const val TAG = "CreditsDialog"
    }
}
