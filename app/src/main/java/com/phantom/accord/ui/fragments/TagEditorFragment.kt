package com.phantom.accord.ui.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import coil3.load
import coil3.request.crossfade
import coil3.request.error
import coil3.request.placeholder
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import com.phantom.accord.R
import com.phantom.accord.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.jaudiotagger.tag.images.ArtworkFactory
import androidx.media3.common.MediaItem
import java.io.File

class TagEditorFragment : BaseFragment(wantsPlayer = true) {

    private lateinit var mediaItem: MediaItem
    private var absolutePath: String? = null

    private lateinit var albumArtImageView: ImageView
    private lateinit var headerTitle: TextView
    private lateinit var headerArtist: TextView
    
    private lateinit var inputTitle: TextInputEditText
    private lateinit var inputAlbum: TextInputEditText
    private lateinit var inputArtist: TextInputEditText
    private lateinit var inputAlbumArtist: TextInputEditText
    private lateinit var inputComposer: TextInputEditText
    private lateinit var inputGenres: TextInputEditText
    private lateinit var inputYear: TextInputEditText
    private lateinit var inputTrackNum: TextInputEditText
    private lateinit var inputTrackTotal: TextInputEditText
    private lateinit var inputDiscNum: TextInputEditText
    private lateinit var inputDiscTotal: TextInputEditText
    private lateinit var inputLyrics: TextInputEditText
    
    private lateinit var fabSave: FloatingActionButton
    
    private var selectedArtworkUri: Uri? = null
    private var isArtworkDeleted: Boolean = false

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            try {
                selectedArtworkUri = uri
                isArtworkDeleted = false
                if (::albumArtImageView.isInitialized) {
                    albumArtImageView.load(uri) {
                        crossfade(true)
                        placeholder(R.drawable.ic_default_cover)
                        error(R.drawable.ic_default_cover)
                    }
                }
            } catch (e: Throwable) {
                Toast.makeText(context, "Error loading image: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    private val writePermissionLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            saveTagsInternal()
        } else {
            Toast.makeText(context, "Permission denied to save tags", Toast.LENGTH_SHORT).show()
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
        val view = inflater.inflate(R.layout.fragment_tag_editor, container, false)

        val topAppBar = view.findViewById<MaterialToolbar>(R.id.topAppBar)
        topAppBar.setNavigationOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        val appBarLayout = view.findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.appBarLayout)
        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, insets.top, 0, 0)
            windowInsets
        }
        ViewCompat.requestApplyInsets(appBarLayout)

        albumArtImageView = view.findViewById(R.id.tag_album_art)
        headerTitle = view.findViewById(R.id.tag_header_title)
        headerArtist = view.findViewById(R.id.tag_header_artist)
        
        inputTitle = view.findViewById(R.id.tag_input_title)
        inputAlbum = view.findViewById(R.id.tag_input_album)
        inputArtist = view.findViewById(R.id.tag_input_artist)
        inputAlbumArtist = view.findViewById(R.id.tag_input_album_artist)
        inputComposer = view.findViewById(R.id.tag_input_composer)
        inputGenres = view.findViewById(R.id.tag_input_genres)
        inputYear = view.findViewById(R.id.tag_input_year)
        inputTrackNum = view.findViewById(R.id.tag_input_track_number)
        inputTrackTotal = view.findViewById(R.id.tag_input_track_total)
        inputDiscNum = view.findViewById(R.id.tag_input_disc_number)
        inputDiscTotal = view.findViewById(R.id.tag_input_disc_total)
        inputLyrics = view.findViewById(R.id.tag_input_lyrics)
        
        fabSave = view.findViewById(R.id.fab_save)
        
        fabSave.setOnClickListener {
            saveTags()
        }
        
        val btnEditArt = view.findViewById<ImageButton>(R.id.btn_edit_art)
        val btnDeleteArt = view.findViewById<ImageButton>(R.id.btn_delete_art)

        btnEditArt.setOnClickListener {
            pickImageLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        btnDeleteArt.setOnClickListener {
            selectedArtworkUri = null
            isArtworkDeleted = true
            albumArtImageView.setImageResource(R.drawable.ic_default_cover)
        }

        loadTags()

        return view
    }
    
    private fun loadTags() {
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
                
                val title = tag?.getFirst(FieldKey.TITLE) ?: ""
                val album = tag?.getFirst(FieldKey.ALBUM) ?: ""
                val artist = tag?.getFirst(FieldKey.ARTIST) ?: ""
                val albumArtist = tag?.getFirst(FieldKey.ALBUM_ARTIST) ?: ""
                val composer = tag?.getFirst(FieldKey.COMPOSER) ?: ""
                val genre = tag?.getFirst(FieldKey.GENRE) ?: ""
                val year = tag?.getFirst(FieldKey.YEAR) ?: ""
                val trackNum = tag?.getFirst(FieldKey.TRACK) ?: ""
                val trackTotal = tag?.getFirst(FieldKey.TRACK_TOTAL) ?: ""
                val discNum = tag?.getFirst(FieldKey.DISC_NO) ?: ""
                val discTotal = tag?.getFirst(FieldKey.DISC_TOTAL) ?: ""
                val lyrics = tag?.getFirst(FieldKey.LYRICS) ?: ""
                
                withContext(Dispatchers.Main) {
                    headerTitle.text = title.ifEmpty { file.name }
                    headerArtist.text = artist
                    
                    inputTitle.setText(title)
                    inputAlbum.setText(album)
                    inputArtist.setText(artist)
                    inputAlbumArtist.setText(albumArtist)
                    inputComposer.setText(composer)
                    inputGenres.setText(genre)
                    inputYear.setText(year)
                    inputTrackNum.setText(trackNum)
                    inputTrackTotal.setText(trackTotal)
                    inputDiscNum.setText(discNum)
                    inputDiscTotal.setText(discTotal)
                    inputLyrics.setText(lyrics)
                    
                    // Load artwork
                    val artwork = tag?.firstArtwork
                    if (artwork != null && artwork.binaryData != null) {
                        albumArtImageView.load(artwork.binaryData) {
                            crossfade(true)
                            placeholder(R.drawable.ic_default_cover)
                            error(R.drawable.ic_default_cover)
                        }
                    } else {
                        albumArtImageView.setImageResource(R.drawable.ic_default_cover)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to read tags: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun saveTags() {
        if (absolutePath == null) return
        saveTagsInternal()
    }

    private fun saveTagsInternal() {
        Toast.makeText(context, "Saving...", Toast.LENGTH_SHORT).show()
        
        val uriStr = arguments?.getString(ARG_URI)
        val uri = if (uriStr != null) Uri.parse(uriStr) else null
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val originalFile = java.io.File(absolutePath!!)
                
                // 1. Copy to temp file in cache to allow jaudiotagger to work freely
                val tempFile = java.io.File(requireContext().cacheDir, "temp_tag_edit_" + System.currentTimeMillis() + "." + originalFile.extension)
                if (tempFile.exists()) tempFile.delete()
                originalFile.copyTo(tempFile, overwrite = true)
                
                // 2. Modify temp file with jaudiotagger
                val audioFile = AudioFileIO.read(tempFile)
                val tag = audioFile.tagOrCreateAndSetDefault
                
                fun updateField(key: FieldKey, text: String) {
                    if (text.isEmpty()) {
                        tag.deleteField(key)
                    } else {
                        tag.setField(key, text)
                    }
                }
                
                updateField(FieldKey.TITLE, inputTitle.text.toString().trim())
                updateField(FieldKey.ALBUM, inputAlbum.text.toString().trim())
                updateField(FieldKey.ARTIST, inputArtist.text.toString().trim())
                updateField(FieldKey.ALBUM_ARTIST, inputAlbumArtist.text.toString().trim())
                updateField(FieldKey.COMPOSER, inputComposer.text.toString().trim())
                updateField(FieldKey.GENRE, inputGenres.text.toString().trim())
                updateField(FieldKey.YEAR, inputYear.text.toString().trim())
                updateField(FieldKey.TRACK, inputTrackNum.text.toString().trim())
                updateField(FieldKey.TRACK_TOTAL, inputTrackTotal.text.toString().trim())
                updateField(FieldKey.DISC_NO, inputDiscNum.text.toString().trim())
                updateField(FieldKey.DISC_TOTAL, inputDiscTotal.text.toString().trim())
                updateField(FieldKey.LYRICS, inputLyrics.text.toString().trim())
                
                if (isArtworkDeleted) {
                    tag.deleteArtworkField()
                } else if (selectedArtworkUri != null) {
                    val inputStream = requireContext().contentResolver.openInputStream(selectedArtworkUri!!)
                    val bytes = inputStream?.readBytes()
                    inputStream?.close()
                    if (bytes != null) {
                        val artwork = ArtworkFactory.getNew()
                        artwork.binaryData = bytes
                        artwork.mimeType = requireContext().contentResolver.getType(selectedArtworkUri!!) ?: "image/jpeg"
                        tag.deleteArtworkField()
                        tag.setField(artwork)
                    }
                }
                
                // Write the changes to the TEMP file
                audioFile.commit()
                
                // 3. Copy temp file back to the original URI
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
                        // On Android 11+, FileNotFoundException is often thrown when write permission is needed
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try {
                                val pendingIntent = MediaStore.createWriteRequest(requireContext().contentResolver, listOf(writeUri))
                                val request = androidx.activity.result.IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                withContext(Dispatchers.Main) {
                                    writePermissionLauncher.launch(request)
                                }
                            } catch (iae: IllegalArgumentException) {
                                throw Exception("Cannot edit tags of non-MediaStore files directly. Please move the file to a standard Music folder.", iae)
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
                    // Fallback to standard file write (e.g. API < 29 with WRITE_EXTERNAL_STORAGE)
                    tempFile.copyTo(originalFile, overwrite = true)
                }
                
                // Clean up
                tempFile.delete()
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Tags saved successfully", Toast.LENGTH_SHORT).show()
                    val activity = activity
                    if (activity is MainActivity) {
                        activity.updateLibrary()
                    }
                    requireActivity().onBackPressedDispatcher.onBackPressed()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to save: ${e.javaClass.simpleName} - ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }


    companion object {
        private const val ARG_MEDIA_ID = "media_id"
        private const val ARG_URI = "media_uri"

        fun newInstance(mediaItem: MediaItem): TagEditorFragment {
            val fragment = TagEditorFragment()
            val args = Bundle()
            args.putString(ARG_MEDIA_ID, mediaItem.mediaId)
            args.putString(ARG_URI, mediaItem.localConfiguration?.uri?.toString())
            fragment.arguments = args
            return fragment
        }
    }
}
