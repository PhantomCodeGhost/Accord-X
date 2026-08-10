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

                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Fetch Lyrics")
                    .setItems(arrayOf("Normal (Plain Text)", "Synced (Line or Word-by-Word)")) { _, which ->
                        val wantSynced = which == 1
                        lyricsInput.setText("Fetching...")
                        fetchLyrics(title, artist, wantSynced)
                    }
                    .show()
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

    private fun fetchLyrics(title: String, artist: String, wantSynced: Boolean) {
        val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(requireContext())
        val usePaxsenix = prefs.getBoolean("lyrics_source_paxsenix", true)
        val useMusixmatch = prefs.getBoolean("lyrics_source_musixmatch", true)
        val useLrclib = prefs.getBoolean("lyrics_source_lrclib", true)
        val useNetEase = prefs.getBoolean("lyrics_source_netease", true)
        val useKugou = prefs.getBoolean("lyrics_source_kugou", true)
        val useKuwo = prefs.getBoolean("lyrics_source_kuwo", true)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val results = mutableListOf<Triple<String, Int, String>>() // <Text, Type, SourceName>

                // Fetch from Paxsenix
                if (usePaxsenix) {
                    try {
                        val query = URLEncoder.encode("$title $artist", "UTF-8")
                        val searchUrl = URL("https://lyrics.paxsenix.org/apple-music/search?q=$query")
                        val searchConn = searchUrl.openConnection() as HttpURLConnection
                        searchConn.requestMethod = "GET"
                        searchConn.setRequestProperty("User-Agent", "Accord-X-AndroidApp/1.0")
                        searchConn.connectTimeout = 8000
                        searchConn.readTimeout = 8000
                        if (searchConn.responseCode == HttpURLConnection.HTTP_OK) {
                            val searchResponse = searchConn.inputStream.bufferedReader().use { it.readText() }
                            val trackId = parseAppleMusicTrackId(searchResponse)
                            if (trackId != null) {
                                val lyricsUrl = URL("https://lyrics.paxsenix.org/apple-music/lyrics?id=$trackId")
                                val lyricsConn = lyricsUrl.openConnection() as HttpURLConnection
                                lyricsConn.requestMethod = "GET"
                                lyricsConn.setRequestProperty("User-Agent", "Accord-X-AndroidApp/1.0")
                                lyricsConn.connectTimeout = 8000
                                lyricsConn.readTimeout = 8000
                                if (lyricsConn.responseCode == HttpURLConnection.HTTP_OK) {
                                    val lyricsResponse = lyricsConn.inputStream.bufferedReader().use { it.readText() }
                                    val parsed = extractLyricsFromPaxsenix(lyricsResponse)
                                    if (parsed != null) {
                                        results.add(Triple(parsed.first, parsed.second, "Paxsenix (Apple Music)"))
                                    }
                                }
                                lyricsConn.disconnect()
                            }
                        }
                        searchConn.disconnect()
                    } catch (e: Exception) {
                        android.util.Log.e("LyricsEditorFragment", "Paxsenix fetch fail", e)
                    }
                }

                // Fetch from Musixmatch
                if (useMusixmatch) {
                    try {
                        var mxmToken = prefs.getString("musixmatch_token", null)
                        if (mxmToken == null) {
                            val tokenUrl = URL("https://apic-desktop.musixmatch.com/ws/1.1/token.get?app_id=web-desktop-app-v1.0")
                            val tokenConn = tokenUrl.openConnection() as HttpURLConnection
                            tokenConn.requestMethod = "GET"
                            tokenConn.setRequestProperty("User-Agent", "Mozilla/5.0")
                            tokenConn.connectTimeout = 8000
                            tokenConn.readTimeout = 8000
                            if (tokenConn.responseCode == HttpURLConnection.HTTP_OK) {
                                val tokenResponse = tokenConn.inputStream.bufferedReader().use { it.readText() }
                                val tokenJson = JSONObject(tokenResponse)
                                mxmToken = tokenJson.optJSONObject("message")?.optJSONObject("body")?.optString("user_token")
                                if (mxmToken != null) {
                                    prefs.edit().putString("musixmatch_token", mxmToken).apply()
                                }
                            }
                            tokenConn.disconnect()
                        }

                        if (mxmToken != null) {
                            val encTitle = URLEncoder.encode(title, "UTF-8")
                            val encArtist = URLEncoder.encode(artist, "UTF-8")
                            val urlStr = "https://apic-desktop.musixmatch.com/ws/1.1/macro.subtitles.get?format=json&namespace=lyrics_richsynced&q_track=$encTitle&q_artist=$encArtist&user_token=$mxmToken&app_id=web-desktop-app-v1.0"
                            val url = URL(urlStr)
                            val conn = url.openConnection() as HttpURLConnection
                            conn.requestMethod = "GET"
                            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                            conn.connectTimeout = 8000
                            conn.readTimeout = 8000
                            if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                                val response = conn.inputStream.bufferedReader().use { it.readText() }
                                val json = JSONObject(response)
                                val body = json.optJSONObject("message")?.optJSONObject("body")?.optJSONObject("macro_calls")
                                
                                val trackRichsync = body?.optJSONObject("track.subtitles.get")?.optJSONObject("message")?.optJSONObject("body")?.optJSONArray("subtitle_list")
                                val trackLyrics = body?.optJSONObject("track.lyrics.get")?.optJSONObject("message")?.optJSONObject("body")?.optJSONObject("lyrics")
                                
                                var found = false
                                if (trackRichsync != null && trackRichsync.length() > 0) {
                                    val subtitle = trackRichsync.getJSONObject(0).optJSONObject("subtitle")
                                    val subtitleBody = subtitle?.optString("subtitle_body")
                                    if (subtitleBody != null && subtitleBody.isNotEmpty()) {
                                        val mxmParsed = extractLyricsFromMusixmatch(subtitleBody)
                                        if (mxmParsed != null) {
                                            results.add(Triple(mxmParsed.first, mxmParsed.second, "Musixmatch"))
                                            found = true
                                        }
                                    }
                                }
                                
                                if (!found && trackLyrics != null) {
                                    val plainBody = trackLyrics.optString("lyrics_body")
                                    if (plainBody.isNotEmpty()) {
                                        val cleaned = plainBody.replace(Regex("\\*\\*\\*\\*\\*\\*\\* This Lyrics is NOT for Commercial use \\*\\*\\*\\*\\*\\*\\*\\n\\(\\d+\\)"), "").trim()
                                        results.add(Triple(cleaned, 1, "Musixmatch"))
                                    }
                                }
                            }
                            conn.disconnect()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("LyricsEditorFragment", "Musixmatch fetch fail", e)
                    }
                }

                // Fetch from LRCLIB
                if (useLrclib) {
                    try {
                    val encodedTitle = URLEncoder.encode(title, "UTF-8")
                    val encodedArtist = URLEncoder.encode(artist, "UTF-8")
                    val urlStr = "https://lrclib.net/api/get?track_name=$encodedTitle&artist_name=$encodedArtist"
                    val url = URL(urlStr)
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("User-Agent", "Accord-X-AndroidApp/1.0")
                    conn.connectTimeout = 8000
                    conn.readTimeout = 8000
                    if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                        val response = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(response)
                        if (json.has("syncedLyrics") && !json.isNull("syncedLyrics")) {
                            val synced = json.getString("syncedLyrics")
                            if (synced.isNotBlank()) results.add(Triple(synced, 2, "LRCLIB"))
                        }
                        if (json.has("plainLyrics") && !json.isNull("plainLyrics")) {
                            val plain = json.getString("plainLyrics")
                            if (plain.isNotBlank()) results.add(Triple(plain, 1, "LRCLIB"))
                        }
                    }
                    conn.disconnect()
                    } catch (e: Exception) {
                        android.util.Log.e("LyricsEditorFragment", "LRCLIB fetch fail", e)
                    }
                }

                // Fetch from NetEase
                if (useNetEase) {
                    try {
                        val query = URLEncoder.encode("$title $artist", "UTF-8")
                        val searchUrl = URL("https://music.163.com/api/search/pc?s=$query&type=1&limit=1")
                        val searchConn = searchUrl.openConnection() as HttpURLConnection
                        searchConn.requestMethod = "POST"
                        searchConn.setRequestProperty("User-Agent", "Mozilla/5.0")
                        searchConn.setRequestProperty("Referer", "https://music.163.com/")
                        searchConn.setRequestProperty("Cookie", "os=pc; osver=Microsoft-Windows-10-Professional-build-10586-64bit; appver=2.0.3.131777; channel=netease; __remember_me=true")
                        searchConn.connectTimeout = 8000
                        searchConn.readTimeout = 8000
                        if (searchConn.responseCode == HttpURLConnection.HTTP_OK) {
                            val response = searchConn.inputStream.bufferedReader().use { it.readText() }
                            val json = JSONObject(response)
                            val songs = json.optJSONObject("result")?.optJSONArray("songs")
                            if (songs != null && songs.length() > 0) {
                                val trackId = songs.getJSONObject(0).getInt("id")
                                val lyricsUrl = URL("https://music.163.com/api/song/lyric?id=$trackId&lv=-1&tv=-1")
                                val lyricsConn = lyricsUrl.openConnection() as HttpURLConnection
                                lyricsConn.requestMethod = "GET"
                                lyricsConn.setRequestProperty("User-Agent", "Mozilla/5.0")
                                lyricsConn.setRequestProperty("Referer", "https://music.163.com/")
                                lyricsConn.setRequestProperty("Cookie", "os=pc; osver=Microsoft-Windows-10-Professional-build-10586-64bit; appver=2.0.3.131777; channel=netease; __remember_me=true")
                                lyricsConn.connectTimeout = 8000
                                lyricsConn.readTimeout = 8000
                                if (lyricsConn.responseCode == HttpURLConnection.HTTP_OK) {
                                    val lyricsResponse = lyricsConn.inputStream.bufferedReader().use { it.readText() }
                                    val lyricsJson = JSONObject(lyricsResponse)
                                    val lrc = lyricsJson.optJSONObject("lrc")?.optString("lyric", "") ?: ""
                                    if (lrc.isNotBlank()) {
                                        val type = if (lrc.contains(Regex("\\[\\d+:\\d{2}(\\.\\d+)?\\]"))) 2 else 1
                                        results.add(Triple(lrc, type, "NetEase"))
                                    }
                                }
                                lyricsConn.disconnect()
                            }
                        }
                        searchConn.disconnect()
                    } catch (e: Exception) {
                        android.util.Log.e("LyricsEditorFragment", "NetEase fetch fail", e)
                    }
                }
                // Fetch from Kugou
                if (useKugou) {
                    try {
                        val query = URLEncoder.encode("$title $artist", "UTF-8")
                        val searchUrl = URL("https://lyrics.kugou.com/search?keyword=$query&duration=0&client=pc&ver=1&man=yes")
                        val searchConn = searchUrl.openConnection() as HttpURLConnection
                        searchConn.requestMethod = "GET"
                        searchConn.connectTimeout = 8000
                        searchConn.readTimeout = 8000
                        if (searchConn.responseCode == HttpURLConnection.HTTP_OK) {
                            val response = searchConn.inputStream.bufferedReader().use { it.readText() }
                            val json = JSONObject(response)
                            val candidates = json.optJSONArray("candidates")
                            if (candidates != null && candidates.length() > 0) {
                                val first = candidates.getJSONObject(0)
                                val id = first.optString("id")
                                val accesskey = first.optString("accesskey")
                                if (id.isNotEmpty() && accesskey.isNotEmpty()) {
                                    val dlUrl = URL("https://lyrics.kugou.com/download?id=$id&accesskey=$accesskey&fmt=lrc&charset=utf8&client=pc&ver=1")
                                    val dlConn = dlUrl.openConnection() as HttpURLConnection
                                    dlConn.requestMethod = "GET"
                                    dlConn.connectTimeout = 8000
                                    dlConn.readTimeout = 8000
                                    if (dlConn.responseCode == HttpURLConnection.HTTP_OK) {
                                        val dlResponse = dlConn.inputStream.bufferedReader().use { it.readText() }
                                        val dlJson = JSONObject(dlResponse)
                                        val base64Lrc = dlJson.optString("content")
                                        if (base64Lrc.isNotEmpty()) {
                                            val decodedBytes = android.util.Base64.decode(base64Lrc, android.util.Base64.DEFAULT)
                                            val lrc = String(decodedBytes, Charsets.UTF_8)
                                            if (lrc.isNotBlank()) {
                                                val type = if (lrc.contains(Regex("\\[\\d+:\\d{2}(\\.\\d+)?\\]"))) 2 else 1
                                                results.add(Triple(lrc, type, "Kugou"))
                                            }
                                        }
                                    }
                                    dlConn.disconnect()
                                }
                            }
                        }
                        searchConn.disconnect()
                    } catch (e: Exception) {
                        android.util.Log.e("LyricsEditorFragment", "Kugou fetch fail", e)
                    }
                }

                // Kuwo skipped as per user request

                // 1. Read priority from SharedPreferences
                val orderStr = prefs.getString("lyrics_source_order", null)
                val defaultOrder = listOf("lyrics_source_paxsenix", "lyrics_source_lrclib", "lyrics_source_netease", "lyrics_source_kugou", "lyrics_source_kuwo")
                val priorityOrder = mutableListOf<String>()
                if (orderStr != null) {
                    try {
                        val array = org.json.JSONArray(orderStr)
                        for (i in 0 until array.length()) priorityOrder.add(array.getString(i))
                    } catch (e: Exception) {}
                }
                if (priorityOrder.isEmpty()) priorityOrder.addAll(defaultOrder)
                
                // Helper to get priority score (lower index = higher priority score)
                fun getPriorityScore(sourceName: String): Int {
                    val key = when (sourceName) {
                        "Paxsenix (Apple Music)" -> "lyrics_source_paxsenix"
                        "Musixmatch" -> "lyrics_source_musixmatch"
                        "LRCLIB" -> "lyrics_source_lrclib"
                        "NetEase" -> "lyrics_source_netease"
                        "Kugou" -> "lyrics_source_kugou"
                        "Kuwo" -> "lyrics_source_kuwo"
                        else -> ""
                    }
                    val index = priorityOrder.indexOf(key)
                    return if (index >= 0) priorityOrder.size - index else -1
                }

                // Filter by desired type (wantSynced ? type >= 2 : type == 1)
                val filtered = if (wantSynced) results.filter { it.second >= 2 } else results.filter { it.second == 1 }
                
                // Sort by: 1. type level (3 is better than 2), 2. custom source priority score
                val bestLyric = filtered.sortedWith(compareBy({ it.second }, { getPriorityScore(it.third) })).lastOrNull()

                withContext(Dispatchers.Main) {
                    if (isAdded) {
                        if (bestLyric != null) {
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

    private fun extractLyricsFromMusixmatch(jsonStr: String): Pair<String, Int>? {
        try {
            val arr = org.json.JSONArray(jsonStr)
            val sb = java.lang.StringBuilder()
            var hasSyllables = false
            for (i in 0 until arr.length()) {
                val lineObj = arr.optJSONObject(i) ?: continue
                val lineStart = lineObj.optDouble("ts", -1.0)
                val wordsArr = lineObj.optJSONArray("l")
                
                if (lineStart >= 0) {
                    val lineStartMillis = (lineStart * 1000).toLong()
                    val m = lineStartMillis / 60000
                    val s = (lineStartMillis % 60000) / 1000
                    val ms = (lineStartMillis % 1000) / 10
                    sb.append(String.format("[%02d:%02d.%02d]", m, s, ms))
                }
                
                if (wordsArr != null && wordsArr.length() > 0) {
                    hasSyllables = true
                    for (j in 0 until wordsArr.length()) {
                        val wordObj = wordsArr.optJSONObject(j) ?: continue
                        val wordText = wordObj.optString("c", "")
                        val wordStart = wordObj.optDouble("ts", -1.0)
                        if (wordStart >= 0) {
                            val wordStartMillis = (wordStart * 1000).toLong()
                            val wm = wordStartMillis / 60000
                            val ws = (wordStartMillis % 60000) / 1000
                            val wms = (wordStartMillis % 1000) / 10
                            sb.append(String.format("<%02d:%02d.%02d>", wm, ws, wms))
                        }
                        sb.append(wordText)
                    }
                } else {
                    val text = lineObj.optJSONObject("x")?.optString("text") ?: lineObj.optString("text", "")
                    sb.append(text)
                }
                sb.append("\n")
            }
            if (sb.isNotEmpty()) {
                return Pair(sb.toString().trim(), if (hasSyllables) 3 else 1)
            }
        } catch (e: Exception) {
            android.util.Log.e("LyricsEditorFragment", "Failed to parse Musixmatch rich synced lyrics", e)
        }
        return null
    }

    private fun extractLyricsFromPaxsenix(json: String): Pair<String, Int>? {
        try {
            val obj = JSONObject(json)
            
            // Check for word-by-word synced content (original logic)
            if (obj.has("content")) {
                val content = obj.get("content")
                if (content is String && content.isNotEmpty()) {
                    return Pair(content, 3)
                }
            }
            
            if (obj.has("lyrics")) {
                val lyricsArr = obj.optJSONArray("lyrics")
                if (lyricsArr != null && lyricsArr.length() > 0) {
                    var hasSyllables = false
                    for (i in 0 until minOf(5, lyricsArr.length())) {
                        val line = lyricsArr.getJSONObject(i)
                        if (line.has("syllables")) {
                            val syllables = line.optJSONArray("syllables")
                            if (syllables != null && syllables.length() > 0) {
                                hasSyllables = true
                                break
                            }
                        }
                    }

                    if (hasSyllables) {
                        val sb = StringBuilder()
                        for (i in 0 until lyricsArr.length()) {
                            val line = lyricsArr.getJSONObject(i)
                            val lineTimestamp = line.optString("timestamp", "")
                            val syllables = line.optJSONArray("syllables")

                            if (lineTimestamp.isNotEmpty()) {
                                sb.append("[$lineTimestamp]")
                            }

                            if (syllables != null && syllables.length() > 0) {
                                for (j in 0 until syllables.length()) {
                                    val syllable = syllables.getJSONObject(j)
                                    val sTime = syllable.optString("timestamp", "")
                                    val sText = syllable.optString("text", "")
                                    if (sTime.isNotEmpty()) {
                                        sb.append("<$sTime>")
                                    }
                                    sb.append(sText)
                                }
                            } else {
                                sb.append(line.optString("text", ""))
                            }
                            sb.append("\n")
                        }
                        val result = sb.toString().trim()
                        if (result.isNotEmpty()) return Pair(result, 3)
                    } else {
                        val sb = StringBuilder()
                        var hasTimestamps = false
                        for (i in 0 until lyricsArr.length()) {
                            val line = lyricsArr.getJSONObject(i)
                            val timestamp = line.optString("timestamp", "")
                            val text = line.optString("text", "")
                            if (timestamp.isNotEmpty()) {
                                sb.append("[$timestamp]$text\n")
                                hasTimestamps = true
                            } else {
                                sb.append("$text\n")
                            }
                        }
                        val result = sb.toString().trim()
                        if (hasTimestamps && result.isNotEmpty()) return Pair(result, 2)
                    }
                }
            }
            if (obj.has("syncedLyrics") && !obj.isNull("syncedLyrics")) {
                val synced = obj.getString("syncedLyrics")
                if (synced.isNotBlank()) return Pair(synced, 2)
            }
            if (obj.has("plainLyrics") && !obj.isNull("plainLyrics")) {
                val plain = obj.getString("plainLyrics")
                if (plain.isNotBlank()) return Pair(plain, 1)
            }
        } catch (e: Exception) {
            android.util.Log.e("LyricsEditorFragment", "Extract paxsenix fail", e)
        }
        return null
    }

    private fun parseAppleMusicTrackId(json: String): String? {
        try {
            val jsonTrimmed = json.trim()
            if (jsonTrimmed.startsWith("[")) {
                val arr = JSONArray(jsonTrimmed)
                if (arr.length() > 0) {
                    val firstResult = arr.getJSONObject(0)
                    if (firstResult.has("id")) {
                        return firstResult.getString("id")
                    }
                }
            } else if (jsonTrimmed.startsWith("{")) {
                val obj = JSONObject(jsonTrimmed)
                if (obj.has("data")) {
                    val data = obj.get("data")
                    if (data is JSONArray && data.length() > 0) {
                        val firstResult = data.getJSONObject(0)
                        if (firstResult.has("id")) {
                            return firstResult.getString("id")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LyricsEditorFragment", "Failed to parse Apple Music search", e)
        }
        return null
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
