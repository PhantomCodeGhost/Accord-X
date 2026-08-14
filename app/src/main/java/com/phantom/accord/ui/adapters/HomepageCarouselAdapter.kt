package com.phantom.accord.ui.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.phantom.accord.R
import com.phantom.accord.logic.resourceUri
import com.phantom.accord.logic.utils.MediaStoreUtils

import androidx.media3.common.MediaItem
import com.phantom.accord.ui.LibraryViewModel
import java.util.Collections
import coil3.load
import coil3.request.error
import coil3.request.placeholder
import com.phantom.accord.logic.ui.coolCrossfade

class HomepageCarouselAdapter(
    private val context: Context,
    libraryViewModel: LibraryViewModel
) : RecyclerView.Adapter<HomepageCarouselAdapter.ViewHolder>() {

    val carouselList: MutableList<MediaStoreUtils.HomepageCarouselHolder>

    init {
        carouselList = mutableListOf()
        updateData(libraryViewModel)
    }

    fun updateData(libraryViewModel: LibraryViewModel) {
        val allSongs = libraryViewModel.mediaItemList.value ?: emptyList()
        
        // Filter for songs that actually have an artwork URI
        val songsWithArt = allSongs.filter { it.mediaMetadata.artworkUri != null && it.mediaMetadata.artworkUri.toString().isNotBlank() }
        
        val targetSongs = mutableListOf<androidx.media3.common.MediaItem>()
        val sourceList = if (songsWithArt.isNotEmpty()) songsWithArt else allSongs
        
        if (sourceList.isNotEmpty()) {
            val sortedByNew = sourceList.sortedByDescending { it.mediaMetadata.extras?.getLong("AddDate") ?: 0L }
            val newestSong = sortedByNew.first()
            targetSongs.add(newestSong)
            
            val remainingSongs = sourceList.filter { it.mediaId != newestSong.mediaId }
            targetSongs.addAll(remainingSongs.shuffled().take(3))
        }
        
        carouselList.clear()
        
        // Use original banners to maintain the "Daily Shuffle" aesthetic
        val banners = listOf(
            R.drawable.accord_mix_1_banner,
            R.drawable.accord_mix_2_banner,
            R.drawable.accord_mix_3_banner,
            R.drawable.accord_mix_4_banner
        )
        
        for ((index, song) in targetSongs.withIndex()) {
            val uri = song.mediaMetadata.artworkUri ?: android.net.Uri.EMPTY
            val bannerRes = banners[index % banners.size]
            
            carouselList.add(
                MediaStoreUtils.HomepageCarouselHolder(
                    MediaStoreUtils.CarouselType.CUSTOM,
                    cover = uri,
                    banner = context.resourceUri(bannerRes),
                    songList = mutableListOf(song),
                    hint = ContextCompat.getString(context, R.string.daily_shuffle)
                )
            )
        }
        
        // Fallback to static if empty
        if (carouselList.isEmpty()) {
            carouselList.addAll(listOf(
                MediaStoreUtils.HomepageCarouselHolder(
                    MediaStoreUtils.CarouselType.CUSTOM,
                    cover = context.resourceUri(R.drawable.accord_mix_3),
                    banner = context.resourceUri(R.drawable.accord_mix_3_banner),
                    songList = if (allSongs.isNotEmpty()) mutableListOf(allSongs.random()) else mutableListOf(),
                    hint = ContextCompat.getString(context, R.string.daily_shuffle)
                ),
                MediaStoreUtils.HomepageCarouselHolder(
                    MediaStoreUtils.CarouselType.CUSTOM,
                    cover = context.resourceUri(R.drawable.accord_mix_2),
                    banner = context.resourceUri(R.drawable.accord_mix_2_banner),
                    songList = if (allSongs.isNotEmpty()) mutableListOf(allSongs.random()) else mutableListOf(),
                    hint = ContextCompat.getString(context, R.string.daily_shuffle)
                ),
                MediaStoreUtils.HomepageCarouselHolder(
                    MediaStoreUtils.CarouselType.CUSTOM,
                    cover = context.resourceUri(R.drawable.accord_mix_1),
                    banner = context.resourceUri(R.drawable.accord_mix_1_banner),
                    songList = if (allSongs.isNotEmpty()) mutableListOf(allSongs.random()) else mutableListOf(),
                    hint = ContextCompat.getString(context, R.string.daily_shuffle)
                ),
                MediaStoreUtils.HomepageCarouselHolder(
                    MediaStoreUtils.CarouselType.CUSTOM,
                    cover = context.resourceUri(R.drawable.accord_mix_4),
                    banner = context.resourceUri(R.drawable.accord_mix_4_banner),
                    songList = if (allSongs.isNotEmpty()) mutableListOf(allSongs.random()) else mutableListOf(),
                    hint = ContextCompat.getString(context, R.string.daily_shuffle)
                )
            ))
        }
        
        notifyDataSetChanged()
    }

    inner class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val coverImageView: ImageView = view.findViewById(R.id.cover)
        val bannerImageView: ImageView = view.findViewById(R.id.banner)
        val hintTextView: TextView = view.findViewById(R.id.hint)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.homepage_carousel, parent, false))

    override fun getItemCount(): Int = carouselList.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.coverImageView.load(carouselList[position].cover) {
            coolCrossfade(true)
            placeholder(R.drawable.ic_default_cover)
            error(R.drawable.ic_default_cover)
        }
        holder.bannerImageView.load(carouselList[position].banner)
        holder.hintTextView.text = carouselList[position].hint
        
        holder.itemView.setOnClickListener {
            val songList = carouselList[position].songList
            if (songList.isNotEmpty()) {
                val activity = holder.itemView.context as? com.phantom.accord.ui.MainActivity
                val mediaController = activity?.getPlayer()
                
                val mixList = mutableListOf<androidx.media3.common.MediaItem>()
                mixList.addAll(songList)
                
                val allSongs = activity?.libraryViewModel?.mediaItemList?.value ?: emptyList()
                val remainingSongs = allSongs.filter { it.mediaId != songList.firstOrNull()?.mediaId }
                mixList.addAll(remainingSongs.shuffled().take(20))
                
                mediaController?.apply {
                    setMediaItems(mixList, 0, androidx.media3.common.C.TIME_UNSET)
                    prepare()
                    play()
                }
            }
        }
    }
}