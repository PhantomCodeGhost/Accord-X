package com.phantom.accord.ui.fragments

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.appcompat.widget.AppCompatEditText
import com.phantom.accord.R
import com.phantom.accord.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class LyricsEditorFragment : BaseFragment(wantsPlayer = true) {

    private lateinit var mediaItem: MediaItem
    private var absolutePath: String? = null
    private var lyricsEditorContainer:  RelativeLayout? = null
    
    private var lastFetchedSource: String? = null

    private lateinit var albumArtImageView: ImageView
    private lateinit var headerTitle: TextView
    private lateinit var headerArtist: TextView
    private lateinit var lyricsInput: AppCompatEditText
    private lateinit var sourceChip: Chip

    private var originalLyrics: String = ""
    private var hasUnsavedChanges: Boolean = false

    private val writePermissionLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            saveLyricsInternal()
        } else {
            Toast.makeText(context, "Permission denied to save lyrics", Toast.LENGTH_SHORT).show()
        }
    }

    private fun writeCrashLog(tag: String, e: Throwable) {
        try {
            val crashFile = File(requireContext().filesDir, "lyrics_crash.txt")
            crashFile.writeText("$tag\n${android.util.Log.getStackTraceString(e)}")
            android.util.Log.e("LyricsEditorFragment", "Crash logged to ${crashFile.absolutePath}", e)
        } catch (_: Throwable) {
            android.util.Log.e("LyricsEditorFragment", "$tag (could not write file)", e)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContext = requireActivity().applicationContext
        val defaultHandler = Thread.currentThread().uncaughtExceptionHandler
        Thread.currentThread().uncaughtExceptionHandler = Thread.UncaughtExceptionHandler { t, e ->
            try {
                val crashFile = java.io.File(appContext.filesDir, "lyrics_crash.txt")
                crashFile.writeText("UNCAUGHT on ${t.name}\n${android.util.Log.getStackTraceString(e)}")
            } catch (_: Throwable) {}
            defaultHandler?.uncaughtException(t, e)
        }
        try {
            val args = requireArguments()
            val mediaMetadata = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(args.getString(ARG_TITLE))
                .setArtist(args.getString(ARG_ARTIST))
                .setArtworkUri(args.getString(ARG_ARTWORK_URI)?.let { Uri.parse(it) })
                .build()
                
            mediaItem = MediaItem.Builder()
                .setMediaId(args.getString(ARG_MEDIA_ID) ?: "")
                .setUri(args.getString(ARG_URI))
                .setMediaMetadata(mediaMetadata)
                .build()

            absolutePath = mediaItem.localConfiguration?.uri?.path
        } catch (e: Throwable) {
            writeCrashLog("onCreate", e)
            throw e
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        try {
            val view = inflater.inflate(R.layout.fragment_lyrics_editor, container, false)

            val topAppBar = view.findViewById<MaterialToolbar>(R.id.topAppBar)
            topAppBar.setNavigationOnClickListener {
                handleBackPress()
            }

            val appBarLayout = view.findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.appBarLayout)
            ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, windowInsets ->
                val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
                v.setPadding(0, insets.top, 0, 0)
                windowInsets
            }
            ViewCompat.requestApplyInsets(appBarLayout)

            albumArtImageView = view.findViewById(R.id.lyrics_album_art)
            headerTitle = view.findViewById(R.id.lyrics_header_title)
            headerArtist = view.findViewById(R.id.lyrics_header_artist)
            lyricsInput = view.findViewById(R.id.lyrics_input)
            sourceChip = view.findViewById(R.id.lyrics_source_chip)
            lyricsInput.hint = "Paste or type lyrics here..."

            // Set header from metadata
            val metadata = mediaItem.mediaMetadata
            headerTitle.text = metadata.title ?: "Unknown"
            headerArtist.text = metadata.artist ?: "Unknown"

            albumArtImageView.load(metadata.artworkUri) {
                crossfade(true)
                placeholder(R.drawable.ic_default_cover)
                error(R.drawable.ic_default_cover)
            }

            // Prevent NestedScrollView from intercepting touch events when scrolling inside EditText
            lyricsInput.setOnTouchListener { v, event ->
                if (v.hasFocus()) {
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                    }
                }
                false
            }

            // Track unsaved changes
            lyricsInput.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    hasUnsavedChanges = s.toString() != originalLyrics
                }
            })

            // Back press handler
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBackPress()
                }
            })

            // Button 1: Search Web
            view.findViewById<ImageButton>(R.id.btn_search_web).setOnClickListener {
                val title = metadata.title?.toString() ?: ""
                val artist = metadata.artist?.toString() ?: ""
                val query = URLEncoder.encode("\"$title\" \"$artist\" lyrics", "UTF-8")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$query"))
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    context?.let { Toast.makeText(it, "No web browser installed", Toast.LENGTH_SHORT).show() }
                }
            }

            // Button 2: Download Lyrics
            view.findViewById<ImageButton>(R.id.btn_download_lyrics).setOnClickListener {
                val title = metadata.title?.toString()?.trim() ?: ""
                val artist = metadata.artist?.toString()?.trim() ?: ""

                if (title.isEmpty() || artist.isEmpty()) {
                    return@setOnClickListener
                }

                lyricsInput.setText("Fetching...")
                fetchLyrics(title, artist, true)
            }

            // Button 3: Paste
            view.findViewById<ImageButton>(R.id.btn_paste).setOnClickListener {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip
                if (clip != null && clip.itemCount > 0) {
                    val pasteText = clip.getItemAt(0).text?.toString() ?: ""
                    lyricsInput.setText(pasteText)
                    context?.let { Toast.makeText(it, "Pasted from clipboard", Toast.LENGTH_SHORT).show() }
                } else {
                    context?.let { Toast.makeText(it, "Clipboard is empty", Toast.LENGTH_SHORT).show() }
                }
            }

            // Button 4: Select All
            view.findViewById<ImageButton>(R.id.btn_select_all).setOnClickListener {
                lyricsInput.requestFocus()
                lyricsInput.selectAll()
            }

            // Button 5: Save
            view.findViewById<ImageButton>(R.id.btn_save_lyrics).setOnClickListener {
                saveLyrics()
            }

            // Load existing lyrics
            loadLyrics()

            return view
        } catch (e: Throwable) {
            writeCrashLog("onCreateView", e)
            throw e
        }
    }

    private fun handleBackPress() {
        if (hasUnsavedChanges) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Save lyrics?")
                .setMessage("You have unsaved changes.")
                .setNegativeButton("Cancel") { dialog, _ -> dialog.dismiss() }
                .setPositiveButton("Save") { _, _ -> saveLyrics() }
                .setNeutralButton("Discard") { _, _ ->
                    requireActivity().supportFragmentManager.popBackStack()
                }
                .show()
        } else {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }

    private fun loadLyrics() {
        if (absolutePath == null) {
            Toast.makeText(context, "Invalid file path", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = File(absolutePath!!)
                if (!file.exists()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "File does not exist", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                val audioFile = AudioFileIO.read(file)
                val tag = audioFile.tag
                var lyrics = tag?.getFirst(FieldKey.LYRICS) ?: ""
                if (lyrics.isBlank()) lyrics = tag?.getFirst("SYNCEDLYRICS") ?: ""
                if (lyrics.isBlank()) lyrics = tag?.getFirst("UNSYNCEDLYRICS") ?: ""
                if (lyrics.isBlank()) lyrics = tag?.getFirst("TXXX:LYRICS") ?: ""
                if (lyrics.isBlank() && tag is org.jaudiotagger.tag.id3.AbstractID3v2Tag) {
                    try {
                        val sylt = tag.getFirstField("SYLT")
                        if (sylt != null) {
                            lyrics = sylt.toString()
                        }
                    } catch (e: Exception) {}
                }

                withContext(Dispatchers.Main) {
                    originalLyrics = lyrics
                    lyricsInput.setText(lyrics)
                    hasUnsavedChanges = false
                }
            } catch (e: Exception) {
                android.util.Log.e("LyricsEditorFragment", "Failed to read lyrics", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to read lyrics: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchLyrics(title: String, artist: String, wantSynced: Boolean) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val results = mutableListOf<Triple<String, Int, String>>() // <Text, Type, SourceName>

                // Fetch from LRCLIB
                // Fetch from LRCLIB

                android.util.Log.i("LyricsEditorFragment", "Raw results count: ${results.size}, wantSynced=$wantSynced")
                for (r in results) {
                    android.util.Log.i("LyricsEditorFragment", "Result: ${r.third}, type=${r.second}, length=${r.first.length}")
                }

                // Filter by desired type (wantSynced ? type >= 2 : type == 1)
                val filtered = if (wantSynced) results.filter { it.second >= 2 } else results.filter { it.second == 1 }
                
                android.util.Log.i("LyricsEditorFragment", "Filtered results count: ${filtered.size}")

                val bestLyric = filtered.sortedBy { it.second }.lastOrNull()

                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        if (bestLyric != null) {
                            android.util.Log.i("LyricsEditorFragment", "Selected best: ${bestLyric.third}")
                            lyricsInput.setText(bestLyric.first)
                            sourceChip.text = bestLyric.third
                            sourceChip.visibility = View.VISIBLE
                            lastFetchedSource = bestLyric.third
                            val typeStr = when (bestLyric.second) {
                                3 -> "word-by-word synced"
                                2 -> "line synced"
                                else -> "plain"
                            }
                            context?.let { Toast.makeText(it, "Found $typeStr lyrics from ${bestLyric.third}", Toast.LENGTH_SHORT).show() }
                        } else {
                            android.util.Log.w("LyricsEditorFragment", "No results found from any source")
                            lyricsInput.setText("")
                            context?.let { Toast.makeText(it, "Lyrics not found", Toast.LENGTH_SHORT).show() }
                        }
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.e("LyricsEditorFragment", "Crash in fetchLyrics coroutine", e)
                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        try {
                            context?.let { Toast.makeText(it, "Lyrics fetch failed", Toast.LENGTH_SHORT).show() }
                        } catch (t: Throwable) {}
                    }
                }
            }
        }
    }



    private fun saveLyrics() {
        if (absolutePath == null) return
        saveLyricsInternal()
    }

    private fun saveLyricsInternal() {
        Toast.makeText(context, "Saving…", Toast.LENGTH_SHORT).show()

        val uriStr = arguments?.getString(ARG_URI)
        val uri = if (uriStr != null) Uri.parse(uriStr) else null

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val originalFile = File(absolutePath!!)

                // Copy to temp file
                val tempFile = File(requireContext().cacheDir, "temp_lyrics_edit_" + System.currentTimeMillis() + "." + originalFile.extension)
                if (tempFile.exists()) tempFile.delete()
                originalFile.copyTo(tempFile, overwrite = true)

                // Modify temp file
                val audioFile = AudioFileIO.read(tempFile)
                val tag = audioFile.tagOrCreateAndSetDefault

                val lyricsText = lyricsInput.text.toString()
                if (lyricsText.isEmpty()) {
                    tag.deleteField(org.jaudiotagger.tag.FieldKey.LYRICS)
                } else {
                    tag.setField(org.jaudiotagger.tag.FieldKey.LYRICS, lyricsText)
                }
                
                if (lastFetchedSource != null) {
                    try {
                        tag.setField(org.jaudiotagger.tag.FieldKey.COMMENT, "Lyrics Source: $lastFetchedSource")
                    } catch (e: Exception) {}
                }
                audioFile.commit()

                // Copy temp file back
                val mediaIdLong = mediaItem.mediaId.toLongOrNull()
                val writeUri = if (mediaIdLong != null) {
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaIdLong)
                } else uri

                if (writeUri != null) {
                    try {
                        requireContext().contentResolver.openOutputStream(writeUri, "wt")?.use { outputStream ->
                            tempFile.inputStream().use { inputStream ->
                                inputStream.copyTo(outputStream)
                            }
                        }
                    } catch (e: Exception) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && writeUri.scheme == "content" && writeUri.authority?.contains("media") == true) {
                            try {
                                val pendingIntent = MediaStore.createWriteRequest(requireContext().contentResolver, listOf(writeUri))
                                val request = androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                withContext(Dispatchers.Main) {
                                    writePermissionLauncher.launch(request)
                                }
                            } catch (iae: IllegalArgumentException) {
                                tempFile.copyTo(originalFile, overwrite = true)
                            }
                            tempFile.delete()
                            return@launch
                        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && e is SecurityException && e is RecoverableSecurityException) {
                            val request = androidx.activity.result.IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                            withContext(Dispatchers.Main) {
                                writePermissionLauncher.launch(request)
                            }
                            tempFile.delete()
                            return@launch
                        } else {
                            tempFile.copyTo(originalFile, overwrite = true)
                        }
                    }
                } else {
                    tempFile.copyTo(originalFile, overwrite = true)
                }

                tempFile.delete()

                withContext(Dispatchers.Main) {
                    originalLyrics = lyricsText
                    hasUnsavedChanges = false
                    Toast.makeText(context, "Lyrics saved successfully", Toast.LENGTH_SHORT).show()
                    val activity = activity
                    if (activity is MainActivity) {
                        activity.updateLibrary()
                    }
                    requireActivity().supportFragmentManager.popBackStack()
                }
            } catch (e: Exception) {
                android.util.Log.e("LyricsEditorFragment", "Failed to save lyrics", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to save: ${e.javaClass.simpleName} - ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    companion object {
        private const val ARG_MEDIA_ID = "media_id"
        private const val ARG_URI = "media_uri"
        private const val ARG_TITLE = "title"
        private const val ARG_ARTIST = "artist"
        private const val ARG_ARTWORK_URI = "artwork_uri"

        fun newInstance(mediaItem: MediaItem): LyricsEditorFragment {
            val fragment = LyricsEditorFragment()
            val args = Bundle()
            args.putString(ARG_MEDIA_ID, mediaItem.mediaId)
            args.putString(ARG_URI, mediaItem.localConfiguration?.uri?.toString())
            args.putString(ARG_TITLE, mediaItem.mediaMetadata.title?.toString())
            args.putString(ARG_ARTIST, mediaItem.mediaMetadata.artist?.toString())
            args.putString(ARG_ARTWORK_URI, mediaItem.mediaMetadata.artworkUri?.toString())
            fragment.arguments = args
            return fragment
        }
    }
}
