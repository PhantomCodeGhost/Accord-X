package com.phantom.accord.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.widget.RemoteViews
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil3.asDrawable
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.phantom.accord.R
import com.phantom.accord.logic.GramophonePlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PlaybackWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Use the static service instance to read player state safely
        val service = GramophonePlaybackService.instanceForWidgetAndLyricsOnly
        val player = service?.endedWorkaroundPlayer
        if (player != null) {
            val isPlaying = player.isPlaying
            val metadata = player.currentMediaItem?.mediaMetadata
            val title = metadata?.title?.toString() ?: "Not Playing"
            val artist = metadata?.artist?.toString() ?: ""
            val artworkUri = metadata?.artworkUri?.toString()

            CoroutineScope(Dispatchers.IO).launch {
                val views = buildRemoteViews(context, isPlaying, title, artist, artworkUri)
                withContext(Dispatchers.Main) {
                    for (id in appWidgetIds) {
                        appWidgetManager.updateAppWidget(id, views)
                    }
                }
            }
        } else {
            // Service not running, show default state
            for (id in appWidgetIds) {
                val views = buildDefaultViews(context)
                appWidgetManager.updateAppWidget(id, views)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        if (action == ACTION_TOGGLE_PLAY || action == ACTION_NEXT || action == ACTION_PREV) {
            // Forward the command to the service via a service intent
            // This avoids the "BroadcastReceiver cannot bind to services" crash
            val serviceIntent = Intent(context, GramophonePlaybackService::class.java).apply {
                this.action = action
            }
            try {
                context.startService(serviceIntent)
            } catch (_: Exception) {
                // Service may not be running
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_PLAY = "com.phantom.accord.widget.ACTION_TOGGLE_PLAY"
        const val ACTION_NEXT = "com.phantom.accord.widget.ACTION_NEXT"
        const val ACTION_PREV = "com.phantom.accord.widget.ACTION_PREV"

        /**
         * Called from GramophonePlaybackService on the main thread whenever
         * playback state changes. Safe to read player state here.
         */
        fun notifyUpdate(context: Context, player: Player) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, PlaybackWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isEmpty()) return

            // Read player state on the main thread (safe)
            val isPlaying = player.isPlaying
            val metadata = player.currentMediaItem?.mediaMetadata
            val title = metadata?.title?.toString() ?: "Not Playing"
            val artist = metadata?.artist?.toString() ?: ""
            val artworkUri = metadata?.artworkUri?.toString()

            CoroutineScope(Dispatchers.IO).launch {
                val views = buildRemoteViews(context, isPlaying, title, artist, artworkUri)
                withContext(Dispatchers.Main) {
                    appWidgetManager.updateAppWidget(componentName, views)
                }
            }
        }

        private fun buildDefaultViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_playback)
            views.setOnClickPendingIntent(R.id.widget_btn_play_pause, getPendingIntent(context, ACTION_TOGGLE_PLAY))
            views.setOnClickPendingIntent(R.id.widget_btn_next, getPendingIntent(context, ACTION_NEXT))
            views.setOnClickPendingIntent(R.id.widget_btn_prev, getPendingIntent(context, ACTION_PREV))
            
            val launchIntent = Intent(context, com.phantom.accord.ui.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(context, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            
            views.setTextViewText(R.id.widget_title, "Not Playing")
            views.setTextViewText(R.id.widget_artist, "")
            views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_default_cover)
            views.setInt(R.id.widget_bg_image, "setColorFilter", Color.parseColor("#2A2A2A"))
            return views
        }

        private suspend fun buildRemoteViews(
            context: Context,
            isPlaying: Boolean,
            title: String,
            artist: String,
            artworkUri: String?
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_playback)

            // Wire up buttons
            views.setOnClickPendingIntent(R.id.widget_btn_play_pause, getPendingIntent(context, ACTION_TOGGLE_PLAY))
            views.setOnClickPendingIntent(R.id.widget_btn_next, getPendingIntent(context, ACTION_NEXT))
            views.setOnClickPendingIntent(R.id.widget_btn_prev, getPendingIntent(context, ACTION_PREV))

            // Play/pause icon
            if (isPlaying) {
                views.setImageViewResource(R.id.widget_btn_play_pause, R.drawable.ic_nowplaying_mp_pause)
            } else {
                views.setImageViewResource(R.id.widget_btn_play_pause, R.drawable.ic_nowplaying_mp_play)
            }

            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)

            if (artworkUri != null) {
                try {
                    val loader = context.imageLoader
                    val request = ImageRequest.Builder(context)
                        .data(artworkUri)
                        .size(300)
                        .allowHardware(false)
                        .build()

                    val result = loader.execute(request)
                    val drawable = result.image?.asDrawable(context.resources)

                    if (drawable is BitmapDrawable) {
                        val bitmap = drawable.bitmap
                        
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            views.setImageViewBitmap(R.id.widget_album_art, bitmap)
                            views.setViewOutlinePreferredRadius(
                                R.id.widget_album_art,
                                12f,
                                android.util.TypedValue.COMPLEX_UNIT_DIP
                            )
                        } else {
                            // 12dp approx in pixels (assuming ~3x density for fallback)
                            val slightlyRounded = getRoundedCornerBitmap(bitmap, 36f)
                            views.setImageViewBitmap(R.id.widget_album_art, slightlyRounded)
                        }

                        // Create a blurred, saturated version of the artwork as the background (matching BlendView)
                        val bgBitmap = createBlendBackground(context, bitmap)
                        views.setImageViewBitmap(R.id.widget_bg_image, bgBitmap)
                    } else {
                        setDefaultArtwork(views)
                    }
                } catch (_: Exception) {
                    setDefaultArtwork(views)
                }
            } else {
                setDefaultArtwork(views)
            }

            return views
        }

        /**
         * Generates a premium dark gradient background using the Palette API,
         * exactly matching the lush Apple Music widget aesthetic.
         */
        private fun createBlendBackground(context: Context, source: Bitmap): Bitmap {
            val palette = androidx.palette.graphics.Palette.from(source).generate()
            
            val defaultDark = android.graphics.Color.parseColor("#1C1C1E")
            val color1 = palette.getVibrantColor(palette.getDominantColor(defaultDark))
            val color2 = palette.getDarkVibrantColor(palette.getMutedColor(defaultDark))
            
            // Keep colors rich and punchy, only darkening very slightly (0.85) for text contrast
            val finalColor1 = darkenColor(color1, 0.85f)
            val finalColor2 = darkenColor(color2, 0.85f)

            // Create a perfectly smooth gradient bitmap
            val width = 400
            val height = 200
            val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            
            val gradient = android.graphics.LinearGradient(
                0f, 0f, width.toFloat(), height.toFloat(),
                intArrayOf(finalColor1, finalColor2),
                null,
                android.graphics.Shader.TileMode.CLAMP
            )
            
            val paint = Paint()
            paint.shader = gradient
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

            return output
        }

        private fun darkenColor(color: Int, factor: Float): Int {
            val a = android.graphics.Color.alpha(color)
            val r = Math.round(android.graphics.Color.red(color) * factor)
            val g = Math.round(android.graphics.Color.green(color) * factor)
            val b = Math.round(android.graphics.Color.blue(color) * factor)
            return android.graphics.Color.argb(a,
                r.coerceAtMost(255),
                g.coerceAtMost(255),
                b.coerceAtMost(255)
            )
        }

        private fun setDefaultArtwork(views: RemoteViews) {
            views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_default_cover)
            views.setInt(R.id.widget_bg_image, "setColorFilter", Color.parseColor("#2A2A2A"))
        }

        private fun getPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, PlaybackWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        private fun getRoundedCornerBitmap(bitmap: Bitmap, radiusDp: Float): Bitmap {
            val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val rect = Rect(0, 0, bitmap.width, bitmap.height)
            val rectF = RectF(rect)
            val radius = radiusDp * (bitmap.width / 100f)
            canvas.drawRoundRect(rectF, radius, radius, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, rect, rect, paint)
            return output
        }
    }
}

