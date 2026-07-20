package com.phantom.accord.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.RectF
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.palette.graphics.Palette
import com.phantom.accord.R
import com.phantom.accord.logic.GramophonePlaybackService
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import androidx.media3.common.MediaItem
import androidx.media3.common.Player

class PlaybackWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Build the controller async to update widget based on current playback state
        val sessionToken = SessionToken(context, ComponentName(context, GramophonePlaybackService::class.java))
        val controllerFuture: ListenableFuture<MediaController> =
            MediaController.Builder(context, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                updateAllWidgets(context, appWidgetManager, appWidgetIds, controller)
                controller.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return

        if (action == ACTION_TOGGLE_PLAY || action == ACTION_NEXT || action == ACTION_PREV) {
            val sessionToken = SessionToken(context, ComponentName(context, GramophonePlaybackService::class.java))
            val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
            controllerFuture.addListener({
                try {
                    val controller = controllerFuture.get()
                    when (action) {
                        ACTION_TOGGLE_PLAY -> {
                            if (controller.isPlaying) controller.pause() else controller.play()
                        }
                        ACTION_NEXT -> controller.seekToNextMediaItem()
                        ACTION_PREV -> controller.seekToPreviousMediaItem()
                    }
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, PlaybackWidgetProvider::class.java))
                    updateAllWidgets(context, appWidgetManager, appWidgetIds, controller)
                    controller.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    private fun updateAllWidgets(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        controller: MediaController
    ) {
        for (appWidgetId in appWidgetIds) {
            val views = buildRemoteViews(context, controller)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }

    companion object {
        const val ACTION_TOGGLE_PLAY = "com.phantom.accord.widget.ACTION_TOGGLE_PLAY"
        const val ACTION_NEXT = "com.phantom.accord.widget.ACTION_NEXT"
        const val ACTION_PREV = "com.phantom.accord.widget.ACTION_PREV"

        fun notifyUpdate(context: Context, player: Player) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, PlaybackWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isEmpty()) return

            val views = buildRemoteViews(context, player)
            appWidgetManager.updateAppWidget(componentName, views)
        }

        private fun buildRemoteViews(context: Context, player: Player): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_playback)

            // Setup buttons
            views.setOnClickPendingIntent(R.id.widget_btn_play_pause, getPendingIntent(context, ACTION_TOGGLE_PLAY))
            views.setOnClickPendingIntent(R.id.widget_btn_next, getPendingIntent(context, ACTION_NEXT))
            views.setOnClickPendingIntent(R.id.widget_btn_prev, getPendingIntent(context, ACTION_PREV))

            // Update UI state
            if (player.isPlaying) {
                views.setImageViewResource(R.id.widget_btn_play_pause, R.drawable.ic_pause_filled)
            } else {
                views.setImageViewResource(R.id.widget_btn_play_pause, R.drawable.ic_play_arrow)
            }

            val currentMediaItem = player.currentMediaItem
            val metadata = currentMediaItem?.mediaMetadata
            
            val title = metadata?.title?.toString() ?: "Not Playing"
            val artist = metadata?.artist?.toString() ?: "Artist"
            
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)

            val artworkData = metadata?.artworkData
            if (artworkData != null && artworkData.isNotEmpty()) {
                val bitmap = BitmapFactory.decodeByteArray(artworkData, 0, artworkData.size)
                if (bitmap != null) {
                    val roundedBitmap = getRoundedCornerBitmap(bitmap, 24f)
                    views.setImageViewBitmap(R.id.widget_album_art, roundedBitmap)
                    
                    // Extract color
                    val palette = Palette.from(bitmap).generate()
                    val dominantColor = palette.getDominantColor(android.graphics.Color.DKGRAY)
                    
                    views.setInt(R.id.widget_bg_image, "setColorFilter", dominantColor)
                } else {
                    setDefaultArtwork(views)
                }
            } else {
                setDefaultArtwork(views)
            }

            return views
        }

        private fun setDefaultArtwork(views: RemoteViews) {
            views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_music_note_24dp)
            views.setInt(R.id.widget_bg_image, "setColorFilter", android.graphics.Color.DKGRAY)
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

        private fun getRoundedCornerBitmap(bitmap: Bitmap, pixels: Float): Bitmap {
            val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val color = -0xbdbdbe
            val paint = Paint()
            val rect = Rect(0, 0, bitmap.width, bitmap.height)
            val rectF = RectF(rect)
            paint.isAntiAlias = true
            canvas.drawARGB(0, 0, 0, 0)
            paint.color = color
            canvas.drawRoundRect(rectF, pixels, pixels, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(bitmap, rect, rect, paint)
            return output
        }
    }
}
