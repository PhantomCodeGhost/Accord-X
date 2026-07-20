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

package com.phantom.accord.ui

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import com.phantom.accord.logic.data.db.entity.PlaylistWithMediaItem
import com.phantom.accord.logic.utils.MediaStoreUtils
import com.phantom.accord.logic.utils.RecommendationFactory

/**
 * LibraryViewModel:
 *   A ViewModel that contains library information.
 * Used across the application.
 *
 * @author PhantomTan, nift4
 */
class LibraryViewModel : ViewModel() {
    val mediaItemList: MutableLiveData<List<MediaItem>> = MutableLiveData()
    val albumItemList: MutableLiveData<List<MediaStoreUtils.Album>> = MutableLiveData()
    val albumArtistItemList: MutableLiveData<List<MediaStoreUtils.Artist>> = MutableLiveData()
    val artistItemList: MutableLiveData<List<MediaStoreUtils.Artist>> = MutableLiveData()
    val genreItemList: MutableLiveData<List<MediaStoreUtils.Genre>> = MutableLiveData()
    val dateItemList: MutableLiveData<List<MediaStoreUtils.Date>> = MutableLiveData()
    val playlistList: MutableLiveData<List<MediaStoreUtils.Playlist>> = MutableLiveData()
    val folderStructure: MutableLiveData<MediaStoreUtils.FileNode> = MutableLiveData()
    val shallowFolderStructure: MutableLiveData<MediaStoreUtils.FileNode> = MutableLiveData()
    val allFolderSet: MutableLiveData<Set<String>> = MutableLiveData()
    val privatePlaylistList: MutableLiveData<List<PlaylistWithMediaItem>> = MutableLiveData()
    val privateAlbumList: MutableList<MediaStoreUtils.Album> = mutableListOf()
    val recommendList: MutableLiveData<RecommendationFactory.RecommendList> = MutableLiveData()
    var privatePlaylistId: Long = 0
}