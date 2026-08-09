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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
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

    private lateinit var albumArtImageView: ImageView
    private lateinit var headerTitle: TextView
    private lateinit var headerArtist: TextView
    private lateinit var lyricsInput: TextInputEditText

    private var originalLyrics: String = ""
    private var hasUnsavedChanges: Boolean = false

    private val writePermissionLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            saveLyricsInternal()
        } else {
            Toast.makeText(context, "Permission denied to save lyrics", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val args = requireArguments()
        mediaItem = MediaItem.Builder()
            .setMediaId(args.getString(ARG_MEDIA_ID) ?: "")
            .setUri(args.getString(ARG_URI))
            .build()

        absolutePath = mediaItem.localConfiguration?.uri?.path
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
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

        // Set header from metadata
        val metadata = mediaItem.mediaMetadata
        headerTitle.text = metadata.title ?: "Unknown"
        headerArtist.text = metadata.artist ?: "Unknown"

        albumArtImageView.load(metadata.artworkUri) {
            crossfade(true)
            placeholder(R.drawable.ic_default_cover)
            error(R.drawable.ic_default_cover)
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
            startActivity(intent)
        }

        // Button 2: Download Lyrics
        view.findViewById<ImageButton>(R.id.btn_download_lyrics).setOnClickListener {
            val title = metadata.title?.toString()?.trim() ?: ""
            val artist = metadata.artist?.toString()?.trim() ?: ""

            if (title.isEmpty() || artist.isEmpty()) {
                Toast.makeText(context, "Title and Artist are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            Toast.makeText(context, "Fetching lyrics…", Toast.LENGTH_SHORT).show()
            fetchLyrics(title, artist)
        }

        // Button 3: Paste
        view.findViewById<ImageButton>(R.id.btn_paste).setOnClickListener {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val pasteText = clip.getItemAt(0).text?.toString() ?: ""
                lyricsInput.setText(pasteText)
                Toast.makeText(context, "Pasted from clipboard", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
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
                val lyrics = tag?.getFirst(FieldKey.LYRICS) ?: ""

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

    private fun fetchLyrics(title: String, artist: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            // Step 1: Try Paxsenix Apple Music (word-by-word sync)
            val paxsenixResult = fetchFromPaxsenix(title, artist)
            if (paxsenixResult != null) {
                withContext(Dispatchers.Main) {
                    lyricsInput.setText(paxsenixResult)
                    Toast.makeText(context, "Word-synced lyrics fetched (Apple Music)", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            // Step 2: Fallback to LRCLIB
            val lrclibResult = fetchFromLrclib(title, artist)
            if (lrclibResult != null) {
                withContext(Dispatchers.Main) {
                    lyricsInput.setText(lrclibResult)
                    Toast.makeText(context, "Synced lyrics fetched (LRCLIB)", Toast.LENGTH_SHORT).show()
                }
                return@launch
            }

            withContext(Dispatchers.Main) {
                Toast.makeText(context, "No lyrics found for this song.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fetchFromPaxsenix(title: String, artist: String): String? {
        try {
            // Search for the track on Apple Music
            val query = URLEncoder.encode("$title $artist", "UTF-8")
            val searchUrl = URL("https://lyrics.paxsenix.org/apple-music/search?q=$query")
            val searchConn = searchUrl.openConnection() as HttpURLConnection
            searchConn.requestMethod = "GET"
            searchConn.setRequestProperty("User-Agent", "Accord-X-AndroidApp/1.0")
            searchConn.connectTimeout = 10000
            searchConn.readTimeout = 10000

            if (searchConn.responseCode != HttpURLConnection.HTTP_OK) {
                searchConn.disconnect()
                return null
            }

            val searchResponse = searchConn.inputStream.bufferedReader().use { it.readText() }
            searchConn.disconnect()

            // Parse search results to get track ID
            val trackId = parseAppleMusicTrackId(searchResponse) ?: return null

            // Fetch lyrics by track ID
            val lyricsUrl = URL("https://lyrics.paxsenix.org/apple-music/lyrics?id=$trackId")
            val lyricsConn = lyricsUrl.openConnection() as HttpURLConnection
            lyricsConn.requestMethod = "GET"
            lyricsConn.setRequestProperty("User-Agent", "Accord-X-AndroidApp/1.0")
            lyricsConn.connectTimeout = 10000
            lyricsConn.readTimeout = 10000

            if (lyricsConn.responseCode != HttpURLConnection.HTTP_OK) {
                lyricsConn.disconnect()
                return null
            }

            val lyricsResponse = lyricsConn.inputStream.bufferedReader().use { it.readText() }
            lyricsConn.disconnect()

            return parseAppleMusicLyrics(lyricsResponse)
        } catch (e: Exception) {
            android.util.Log.e("LyricsEditorFragment", "Paxsenix fetch failed", e)
            return null
        }
    }

    private fun parseAppleMusicTrackId(json: String): String? {
        return try {
            val obj = JSONObject(json)
            // Try to get track ID from the response
            // The search response might be an object with a "data" array
            if (obj.has("data")) {
                val data = obj.get("data")
                if (data is JSONArray && data.length() > 0) {
                    val firstResult = data.getJSONObject(0)
                    if (firstResult.has("id")) {
                        return firstResult.getString("id")
                    }
                }
            }
            // Direct array response
            val arr = try { JSONArray(json) } catch (_: Exception) { null }
            if (arr != null && arr.length() > 0) {
                val firstResult = arr.getJSONObject(0)
                if (firstResult.has("id")) {
                    return firstResult.getString("id")
                }
            }
            null
        } catch (e: Exception) {
            android.util.Log.e("LyricsEditorFragment", "Failed to parse Apple Music search", e)
            null
        }
    }

    private fun parseAppleMusicLyrics(json: String): String? {
        return try {
            val obj = JSONObject(json)

            // Check for word-by-word synced content
            if (obj.has("content")) {
                val content = obj.get("content")
                if (content is String && content.isNotEmpty()) {
                    return content
                }
            }

            // Check for lyrics array with timestamps
            if (obj.has("lyrics")) {
                val lyricsArr = obj.optJSONArray("lyrics")
                if (lyricsArr != null && lyricsArr.length() > 0) {
                    val sb = StringBuilder()
                    for (i in 0 until lyricsArr.length()) {
                        val line = lyricsArr.getJSONObject(i)
                        val timestamp = line.optString("timestamp", "")
                        val text = line.optString("text", "")
                        if (timestamp.isNotEmpty()) {
                            sb.append("[$timestamp]$text\n")
                        } else {
                            sb.append("$text\n")
                        }
                    }
                    val result = sb.toString().trim()
                    if (result.isNotEmpty()) return result
                }
            }

            // Fallback: try syncedLyrics or plainLyrics
            if (obj.has("syncedLyrics") && !obj.isNull("syncedLyrics")) {
                return obj.getString("syncedLyrics")
            }
            if (obj.has("plainLyrics") && !obj.isNull("plainLyrics")) {
                return obj.getString("plainLyrics")
            }

            null
        } catch (e: Exception) {
            android.util.Log.e("LyricsEditorFragment", "Failed to parse Apple Music lyrics", e)
            null
        }
    }

    private fun fetchFromLrclib(title: String, artist: String): String? {
        try {
            val encodedTitle = URLEncoder.encode(title, "UTF-8")
            val encodedArtist = URLEncoder.encode(artist, "UTF-8")
            val urlStr = "https://lrclib.net/api/get?track_name=$encodedTitle&artist_name=$encodedArtist"

            val url = URL(urlStr)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Accord-X-AndroidApp/1.0")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return null
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            val json = JSONObject(response)
            if (json.has("syncedLyrics") && !json.isNull("syncedLyrics")) {
                return json.getString("syncedLyrics")
            }
            if (json.has("plainLyrics") && !json.isNull("plainLyrics")) {
                return json.getString("plainLyrics")
            }
            return null
        } catch (e: Exception) {
            android.util.Log.e("LyricsEditorFragment", "LRCLIB fetch failed", e)
            return null
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
                    tag.deleteField(FieldKey.LYRICS)
                } else {
                    tag.setField(FieldKey.LYRICS, lyricsText)
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
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                val pendingIntent = MediaStore.createWriteRequest(requireContext().contentResolver, listOf(writeUri))
                                val request = androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                withContext(Dispatchers.Main) {
                                    writePermissionLauncher.launch(request)
                                }
                            } catch (iae: IllegalArgumentException) {
                                throw Exception("Cannot edit tags of non-MediaStore files directly.", iae)
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
                            throw e
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

        fun newInstance(mediaItem: MediaItem): LyricsEditorFragment {
            val fragment = LyricsEditorFragment()
            val args = Bundle()
            args.putString(ARG_MEDIA_ID, mediaItem.mediaId)
            args.putString(ARG_URI, mediaItem.localConfiguration?.uri?.toString())
            fragment.arguments = args
            return fragment
        }
    }
}
