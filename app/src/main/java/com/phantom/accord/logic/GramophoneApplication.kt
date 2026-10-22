/*
 *     Copyright (C) 2024 Phantom Foundation
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.phantom.accord.logic

import android.app.Application
import android.app.NotificationManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.os.StrictMode.VmPolicy
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatDelegate
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.preference.PreferenceManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.NullRequestDataException
import coil3.request.allowHardware
import coil3.size.pxOrElse
import coil3.util.Logger
import com.phantom.accord.BuildConfig
import com.phantom.accord.ui.BugHandlerActivity
import java.io.File
import java.io.IOException
import kotlin.system.exitProcess

/**
 * GramophoneApplication
 *
 * @author PhantomTan, nift4
 */
class GramophoneApplication : Application(), SingletonImageLoader.Factory, Thread.UncaughtExceptionHandler {

    lateinit var prefs: SharedPreferences
        private set

    var createdActivitiesCount = 0
        private set

    init {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        
        registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: Bundle?) {
                createdActivitiesCount++
            }
            override fun onActivityStarted(activity: android.app.Activity) {}
            override fun onActivityResumed(activity: android.app.Activity) {}
            override fun onActivityPaused(activity: android.app.Activity) {}
            override fun onActivityStopped(activity: android.app.Activity) {}
            override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: android.app.Activity) {
                createdActivitiesCount--
            }
        })

        if (BuildConfig.DEBUG) {
            // Use StrictMode to find anti-pattern issues
            StrictMode.setThreadPolicy(
                ThreadPolicy.Builder()
                    .detectAll().permitDiskReads() // permit disk reads due to media3 setMetadata() TODO extra player thread
                    .penaltyLog().build())
            StrictMode.setVmPolicy(
                VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog().build())
        }

        // This is a separate thread to avoid disk read on main thread and improve startup time
        Thread {
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            // Set application theme when launching.
            when (prefs.getString("theme_mode", "0")) {
                "0" -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                }

                "1" -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                }

                "2" -> {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
            }

            // https://github.com/androidx/media/issues/805
            if (needsMissingOnDestroyCallWorkarounds()) {
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID)
            }
        }.start()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .diskCache(null)
            .components {
                add(Fetcher.Factory { data, options, _ ->
                    val targetW = options.size.width.pxOrElse { 1024 }
                    val targetH = options.size.height.pxOrElse { 1024 }

                    // Case 1: Pair<File, Size?>
                    if (data is Pair<*, *>) {
                        val file = data.first as? File ?: return@Factory null
                        val size = data.second as? Size
                        val w = options.size.width.pxOrElse { size?.width ?: 1024 }
                        val h = options.size.height.pxOrElse { size?.height ?: 1024 }
                        return@Factory Fetcher {
                            val bitmap = extractAudioEmbeddedPicture(context, file, null, w, h)
                            if (bitmap != null) {
                                ImageFetchResult(bitmap.asImage(), true, DataSource.DISK)
                            } else {
                                throw IOException("No album art found")
                            }
                        }
                    }

                    // Case 2: File object
                    if (data is File) {
                        if (isAudioPath(data.path) && data.exists()) {
                            return@Factory Fetcher {
                                val bitmap = extractAudioEmbeddedPicture(context, data, null, targetW, targetH)
                                if (bitmap != null) {
                                    ImageFetchResult(bitmap.asImage(), true, DataSource.DISK)
                                } else {
                                    throw IOException("No album art found")
                                }
                            }
                        }
                    }

                    // Case 3: Uri
                    if (data is Uri) {
                        val uriStr = data.toString()

                        // 3a. MediaStore song albumart URI
                        if (uriStr.contains("audio/media") && uriStr.contains("albumart")) {
                            val idStr = uriStr.substringAfter("audio/media/").substringBefore("/")
                            val id = idStr.toLongOrNull()
                            if (id != null) {
                                return@Factory Fetcher {
                                    val audioUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                                    var songFile: File? = null
                                    try {
                                        val proj = arrayOf(MediaStore.Audio.Media.DATA)
                                        context.contentResolver.query(audioUri, proj, null, null, null)?.use { cursor ->
                                            if (cursor.moveToFirst()) {
                                                val path = cursor.getString(0)
                                                if (!path.isNullOrBlank()) {
                                                    val f = File(path)
                                                    if (f.exists()) songFile = f
                                                }
                                            }
                                        }
                                    } catch (_: Throwable) {}

                                    val bitmap = extractAudioEmbeddedPicture(context, songFile, audioUri, targetW, targetH)
                                    if (bitmap != null) {
                                        ImageFetchResult(bitmap.asImage(), true, DataSource.DISK)
                                    } else {
                                        throw IOException("No album art found")
                                    }
                                }
                            }
                        }

                        // 3b. MediaStore albumart URI (by album id)
                        if (uriStr.contains("audio/albumart")) {
                            val albumId = uriStr.substringAfter("audio/albumart/").substringBefore("/").substringBefore("?").toLongOrNull()
                            if (albumId != null) {
                                return@Factory Fetcher {
                                    var firstSongUri: Uri? = null
                                    var firstSongFile: File? = null
                                    try {
                                        context.contentResolver.query(
                                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DATA),
                                            "${MediaStore.Audio.Media.ALBUM_ID} = ?",
                                            arrayOf(albumId.toString()),
                                            "${MediaStore.Audio.Media._ID} ASC"
                                        )?.use { cursor ->
                                            if (cursor.moveToFirst()) {
                                                val songId = cursor.getLong(0)
                                                firstSongUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, songId)
                                                val path = cursor.getString(1)
                                                if (!path.isNullOrBlank()) {
                                                    val f = File(path)
                                                    if (f.exists()) firstSongFile = f
                                                }
                                            }
                                        }
                                    } catch (_: Throwable) {}

                                    val bitmap = extractAudioEmbeddedPicture(context, firstSongFile, firstSongUri, targetW, targetH)
                                    if (bitmap != null) {
                                        ImageFetchResult(bitmap.asImage(), true, DataSource.DISK)
                                    } else {
                                        throw IOException("No album art found")
                                    }
                                }
                            }
                        }

                        // 3c. Direct MediaStore audio media URI
                        if (uriStr.startsWith("content://media/external/audio/media/") && !uriStr.contains("albumart")) {
                            val id = data.lastPathSegment?.toLongOrNull()
                            if (id != null) {
                                return@Factory Fetcher {
                                    var songFile: File? = null
                                    try {
                                        val proj = arrayOf(MediaStore.Audio.Media.DATA)
                                        context.contentResolver.query(data, proj, null, null, null)?.use { cursor ->
                                            if (cursor.moveToFirst()) {
                                                val path = cursor.getString(0)
                                                if (!path.isNullOrBlank()) {
                                                    val f = File(path)
                                                    if (f.exists()) songFile = f
                                                }
                                            }
                                        }
                                    } catch (_: Throwable) {}

                                    val bitmap = extractAudioEmbeddedPicture(context, songFile, data, targetW, targetH)
                                    if (bitmap != null) {
                                        ImageFetchResult(bitmap.asImage(), true, DataSource.DISK)
                                    } else {
                                        throw IOException("No album art found")
                                    }
                                }
                            }
                        }

                        // 3d. Direct file:// audio URI
                        if (data.scheme == "file") {
                            val path = data.path
                            if (path != null && isAudioPath(path)) {
                                val f = File(path)
                                if (f.exists()) {
                                    return@Factory Fetcher {
                                        val bitmap = extractAudioEmbeddedPicture(context, f, null, targetW, targetH)
                                        if (bitmap != null) {
                                            ImageFetchResult(bitmap.asImage(), true, DataSource.DISK)
                                        } else {
                                            throw IOException("No album art found")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    null
                })
            }
            .run {
                if (!BuildConfig.DEBUG) this else
                    logger(object : Logger {
                        override var minLevel = Logger.Level.Verbose
                        override fun log(
                            tag: String,
                            level: Logger.Level,
                            message: String?,
                            throwable: Throwable?
                        ) {
                            if (level < minLevel) return
                            val priority = level.ordinal + 2 // obviously the best way to do it
                            if (message != null) {
                                Log.println(priority, tag, message)
                            }
                            // Let's keep the log readable and ignore normal events' stack traces.
                            if (throwable != null && throwable !is NullRequestDataException && (throwable !is IOException || throwable.message != "No album art found")) {
                                Log.println(priority, tag, Log.getStackTraceString(throwable))
                            }
                        }
                    })
            }
            .build()
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        val exceptionMessage = Log.getStackTraceString(e)
        val threadName = t.name
        val intent = Intent(this, BugHandlerActivity::class.java)
        intent.putExtra("exception_message", exceptionMessage)
        intent.putExtra("thread", threadName)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        exitProcess(10)
    }

    private fun extractAudioEmbeddedPicture(
        context: PlatformContext,
        file: File?,
        contentUri: Uri?,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        var pfd: android.os.ParcelFileDescriptor? = null
        var fis: java.io.FileInputStream? = null
        try {
            var dataSourceSet = false

            // Try 1: Open ParcelFileDescriptor via ContentResolver if contentUri is available
            if (contentUri != null) {
                try {
                    pfd = context.contentResolver.openFileDescriptor(contentUri, "r")
                    if (pfd != null) {
                        retriever.setDataSource(pfd.fileDescriptor)
                        dataSourceSet = true
                    }
                } catch (e: Throwable) {
                    Log.d("GramophoneApp", "contentUri fd failed: ${e.message}")
                }
            }

            // Try 2: Open FileInputStream in Java to obtain fd if file is available
            if (!dataSourceSet && file != null && file.exists()) {
                try {
                    fis = java.io.FileInputStream(file)
                    retriever.setDataSource(fis.fd)
                    dataSourceSet = true
                } catch (e: Throwable) {
                    Log.d("GramophoneApp", "file fis failed: ${e.message}")
                }
            }

            // Try 3: Direct contentUri with context
            if (!dataSourceSet && contentUri != null) {
                try {
                    retriever.setDataSource(context, contentUri)
                    dataSourceSet = true
                } catch (e: Throwable) {
                    Log.d("GramophoneApp", "context contentUri failed: ${e.message}")
                }
            }

            // Try 4: Direct file path fallback
            if (!dataSourceSet && file != null && file.exists()) {
                try {
                    retriever.setDataSource(file.absolutePath)
                    dataSourceSet = true
                } catch (e: Throwable) {
                    Log.d("GramophoneApp", "absolutePath failed: ${e.message}")
                }
            }

            if (!dataSourceSet) return null

            val pic = retriever.embeddedPicture
            if (pic != null && pic.isNotEmpty()) {
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(pic, 0, pic.size, boundsOpts)

                var sampleSize = 1
                val reqW = if (targetWidth > 0) targetWidth else 1024
                val reqH = if (targetHeight > 0) targetHeight else 1024
                while (boundsOpts.outWidth / (sampleSize * 2) >= reqW && boundsOpts.outHeight / (sampleSize * 2) >= reqH) {
                    sampleSize *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                return BitmapFactory.decodeByteArray(pic, 0, pic.size, decodeOpts)
            }
        } catch (e: Throwable) {
            Log.e("GramophoneApp", "extractAudioEmbeddedPicture failed", e)
            return null
        } finally {
            try { fis?.close() } catch (_: Throwable) {}
            try { pfd?.close() } catch (_: Throwable) {}
            try { retriever.release() } catch (_: Throwable) {}
        }
        return null
    }

    private fun isAudioPath(path: String): Boolean {
        val ext = path.substringAfterLast('.', "").lowercase()
        return ext in setOf("mp3", "m4a", "flac", "ogg", "wav", "aac", "opus", "wma", "oga")
    }
}

