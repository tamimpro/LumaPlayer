package com.example.ui.viewmodel

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.LumaApplication
import com.example.data.local.MountedFolderEntity
import com.example.data.local.PlaylistWithCount
import com.example.data.local.StreamBookmarkEntity
import com.example.data.local.VideoItemEntity
import com.example.data.model.AppThemeMode
import com.example.data.model.AspectRatioMode
import com.example.data.model.FolderSummary
import com.example.data.model.PlaybackQueueItem
import com.example.data.model.PlaybackRequest
import com.example.data.model.PlayerOrientationMode
import com.example.data.model.StreamProtocol
import com.example.data.model.SubtitleEdgeStyle
import com.example.data.model.VideoSortOption
import com.example.data.preferences.UserSettings
import com.example.data.repository.FileOpResult
import com.example.data.repository.StorageAccessState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LumaApplication
    private val repository = app.libraryRepository
    private val prefs = app.preferencesRepository

    val settings: StateFlow<UserSettings> = prefs.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UserSettings()
    )

    val mountedFolders: StateFlow<List<MountedFolderEntity>> = repository.mountedFolders.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val folderSummaries: StateFlow<List<FolderSummary>> = repository.folderSummaries.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val allVideos: StateFlow<List<VideoItemEntity>> = repository.allVideos.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val favoriteVideos: StateFlow<List<VideoItemEntity>> = repository.favoriteVideos.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val watchHistory: StateFlow<List<VideoItemEntity>> = repository.watchHistory.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val playlists: StateFlow<List<PlaylistWithCount>> = repository.playlists.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val streamBookmarks: StateFlow<List<StreamBookmarkEntity>> = repository.streamBookmarks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val _storageAccess = MutableStateFlow(repository.checkStorageAccess())
    val storageAccess: StateFlow<StorageAccessState> = _storageAccess.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _favoritesOnly = MutableStateFlow(false)
    val favoritesOnly: StateFlow<Boolean> = _favoritesOnly.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _bannerMessage = MutableStateFlow<String?>(null)
    val bannerMessage: StateFlow<String?> = _bannerMessage.asStateFlow()

    private val _allFilesPromptReason = MutableStateFlow<String?>(null)
    val allFilesPromptReason: StateFlow<String?> = _allFilesPromptReason.asStateFlow()

    private val _pendingMediaStoreDelete = MutableStateFlow<Pair<IntentSender, String>?>(null)
    val pendingMediaStoreDelete: StateFlow<Pair<IntentSender, String>?> = _pendingMediaStoreDelete.asStateFlow()

    val filteredSortedVideos: StateFlow<List<VideoItemEntity>> = combine(
        allVideos,
        searchQuery,
        favoritesOnly,
        settings
    ) { videos, query, favOnly, userSettings ->
        val q = query.trim().lowercase()
        val filtered = videos.filter { v ->
            (!favOnly || v.isFavorite) &&
                (q.isEmpty() || v.title.lowercase().contains(q) || v.folderName.lowercase().contains(q))
        }
        sortVideos(filtered, userSettings.videoSortOption)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    init {
        refreshStorageAccessAndScan(silent = true)
    }

    fun refreshStorageAccessAndScan(silent: Boolean = false) {
        val current = repository.checkStorageAccess()
        _storageAccess.value = current
        if (current.canScanDevice) {
            scanDeviceLibrary(silent = silent)
        }
    }

    fun scanDeviceLibrary(silent: Boolean = false) {
        val access = repository.checkStorageAccess()
        _storageAccess.value = access
        if (!access.canScanDevice) {
            if (!silent) {
                showBanner("Grant file access to scan device videos automatically")
            }
            return
        }
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.value = true
            val count = repository.scanDeviceStorage()
            _isScanning.value = false
            if (!silent) {
                showBanner("Library synced · $count video(s) found")
            }
        }
    }

    fun deleteVideoFile(video: VideoItemEntity) {
        viewModelScope.launch {
            when (val result = repository.deleteVideoFile(video)) {
                is FileOpResult.Success -> showBanner(result.message)
                is FileOpResult.NeedsAllFilesAccess -> _allFilesPromptReason.value = result.reason
                is FileOpResult.NeedsMediaStoreDeleteIntent -> {
                    _pendingMediaStoreDelete.value = result.intentSender to result.videoUri
                }
                is FileOpResult.Error -> showBanner(result.message)
            }
        }
    }

    fun onMediaStoreDeleteConfirmed(videoUri: String) {
        _pendingMediaStoreDelete.value = null
        viewModelScope.launch {
            repository.deleteVideoEntry(videoUri)
            showBanner("Deleted video from device")
        }
    }

    fun clearPendingMediaStoreDelete() {
        _pendingMediaStoreDelete.value = null
    }

    fun moveVideoFile(video: VideoItemEntity, destinationDirPath: String) {
        viewModelScope.launch {
            when (val result = repository.moveVideoFile(video, destinationDirPath)) {
                is FileOpResult.Success -> showBanner(result.message)
                is FileOpResult.NeedsAllFilesAccess -> _allFilesPromptReason.value = result.reason
                is FileOpResult.NeedsMediaStoreDeleteIntent -> Unit
                is FileOpResult.Error -> showBanner(result.message)
            }
        }
    }

    fun renameVideoFile(video: VideoItemEntity, newBaseName: String) {
        viewModelScope.launch {
            when (val result = repository.renameVideoFile(video, newBaseName)) {
                is FileOpResult.Success -> showBanner(result.message)
                is FileOpResult.NeedsAllFilesAccess -> _allFilesPromptReason.value = result.reason
                is FileOpResult.NeedsMediaStoreDeleteIntent -> Unit
                is FileOpResult.Error -> showBanner(result.message)
            }
        }
    }

    fun getSuggestedMoveDestinations(): List<File> {
        val existingPaths = folderSummaries.value.map { it.folderUri }
        return repository.getSuggestedMoveDestinations(existingPaths)
    }

    fun dismissAllFilesPrompt() {
        _allFilesPromptReason.value = null
    }

    fun observeVideosInFolder(folderUri: String): Flow<List<VideoItemEntity>> =
        combine(repository.observeVideosInFolder(folderUri), settings) { list, userSettings ->
            sortVideos(list, userSettings.videoSortOption)
        }

    fun observeVideosInPlaylist(playlistId: Long): Flow<List<VideoItemEntity>> =
        repository.observeVideosInPlaylist(playlistId)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFavoritesFilter() {
        _favoritesOnly.value = !_favoritesOnly.value
    }

    fun setSortOption(option: VideoSortOption) {
        viewModelScope.launch { prefs.updateVideoSort(option) }
    }

    fun mountAndScanFolder(treeUri: Uri) {
        viewModelScope.launch {
            _isScanning.value = true
            val count = repository.mountAndScanFolder(treeUri)
            _isScanning.value = false
            showBanner(if (count > 0) "Indexed $count external video(s)" else "External folder added")
        }
    }

    fun unmountFolder(treeUri: String) {
        viewModelScope.launch {
            repository.unmountFolder(treeUri)
            showBanner("External folder removed")
        }
    }

    fun importVideos(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isScanning.value = true
            val count = repository.importPickedVideos(uris)
            _isScanning.value = false
            showBanner("Imported $count video(s)")
        }
    }

    fun toggleFavorite(video: VideoItemEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(video.uri, !video.isFavorite)
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            repository.clearWatchHistory()
            showBanner("Playback history cleared")
        }
    }

    fun createPlaylist(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(name)
            showBanner("Created playlist \"$name\"")
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.renamePlaylist(playlistId, newName)
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            showBanner("Playlist deleted")
        }
    }

    fun addVideoToPlaylist(playlistId: Long, videoUri: String, playlistName: String) {
        viewModelScope.launch {
            repository.addVideoToPlaylist(playlistId, videoUri)
            showBanner("Added to $playlistName")
        }
    }

    fun removeVideoFromPlaylist(playlistId: Long, videoUri: String) {
        viewModelScope.launch {
            repository.removeVideoFromPlaylist(playlistId, videoUri)
        }
    }

    fun saveStreamAndBuildRequest(
        title: String,
        url: String,
        protocol: StreamProtocol,
        headers: Map<String, String>,
        pinBookmark: Boolean,
        onReady: (PlaybackRequest) -> Unit
    ) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) return
        viewModelScope.launch {
            val saved = repository.saveOrUpdateStreamBookmark(
                title = title,
                url = trimmedUrl,
                protocol = protocol,
                headers = headers,
                isPinned = pinBookmark
            )
            onReady(
                PlaybackRequest(
                    queue = listOf(
                        PlaybackQueueItem(
                            uri = saved.url,
                            title = saved.title,
                            resumePositionMs = saved.lastPositionMs,
                            isStream = true,
                            protocol = runCatching { StreamProtocol.valueOf(saved.protocol) }.getOrDefault(StreamProtocol.AUTO),
                            headers = headers
                        )
                    ),
                    startIndex = 0
                )
            )
        }
    }

    fun buildStreamRequestFromBookmark(bookmark: StreamBookmarkEntity): PlaybackRequest {
        val headers = repository.parseHeaders(bookmark.headersJson)
        val protocol = runCatching { StreamProtocol.valueOf(bookmark.protocol) }.getOrDefault(StreamProtocol.AUTO)
        return PlaybackRequest(
            queue = listOf(
                PlaybackQueueItem(
                    uri = bookmark.url,
                    title = bookmark.title,
                    resumePositionMs = bookmark.lastPositionMs,
                    isStream = true,
                    protocol = protocol,
                    headers = headers
                )
            ),
            startIndex = 0
        )
    }

    fun parseHeadersJson(json: String): Map<String, String> = repository.parseHeaders(json)

    fun toggleStreamPinned(bookmark: StreamBookmarkEntity) {
        viewModelScope.launch {
            repository.toggleStreamPinned(bookmark.id, !bookmark.isPinned)
        }
    }

    fun deleteStreamBookmark(id: Long) {
        viewModelScope.launch {
            repository.deleteStreamBookmark(id)
        }
    }

    fun buildPlaybackRequest(
        videos: List<VideoItemEntity>,
        startIndex: Int,
        startInBackground: Boolean = false
    ): PlaybackRequest {
        return PlaybackRequest(
            queue = videos.map { v ->
                PlaybackQueueItem(
                    uri = v.uri,
                    title = v.title,
                    subtitleUri = v.siblingSubtitleUri,
                    resumePositionMs = v.lastPositionMs,
                    isStream = false
                )
            },
            startIndex = startIndex.coerceIn(0, (videos.size - 1).coerceAtLeast(0)),
            startInBackground = startInBackground
        )
    }

    fun updateThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { prefs.updateThemeMode(mode) }
    }

    fun cycleThemeQuick() {
        val next = when (settings.value.themeMode) {
            AppThemeMode.AMOLED_DARK -> AppThemeMode.LIGHT
            AppThemeMode.LIGHT -> AppThemeMode.AMOLED_DARK
            AppThemeMode.SYSTEM -> AppThemeMode.AMOLED_DARK
        }
        updateThemeMode(next)
    }

    fun updateDefaultAspectRatio(mode: AspectRatioMode) {
        viewModelScope.launch { prefs.updateDefaultAspectRatio(mode) }
    }

    fun updateDefaultOrientation(mode: PlayerOrientationMode) {
        viewModelScope.launch { prefs.updateDefaultOrientation(mode) }
    }

    fun updatePlaybackFlags(
        autoResume: Boolean? = null,
        backgroundPlay: Boolean? = null,
        autoPip: Boolean? = null,
        preferHw: Boolean? = null,
        matchFps: Boolean? = null
    ) {
        viewModelScope.launch {
            prefs.updatePlaybackFlags(autoResume, backgroundPlay, autoPip, preferHw, matchFps)
        }
    }

    fun updateGestureSettings(
        brightness: Boolean? = null,
        volume: Boolean? = null,
        seek: Boolean? = null,
        pinchZoom: Boolean? = null,
        doubleTapSeconds: Int? = null,
        sensitivity: Float? = null
    ) {
        viewModelScope.launch {
            prefs.updateGestureSettings(brightness, volume, seek, pinchZoom, doubleTapSeconds, sensitivity)
        }
    }

    fun updateSubtitleDefaults(
        scale: Float? = null,
        bgOpacity: Float? = null,
        edgeStyle: SubtitleEdgeStyle? = null
    ) {
        viewModelScope.launch {
            prefs.updateSubtitleSettings(scale = scale, bgOpacity = bgOpacity, edgeStyle = edgeStyle)
        }
    }

    fun clearBanner() {
        _bannerMessage.value = null
    }

    private fun showBanner(message: String) {
        _bannerMessage.value = message
    }

    private fun sortVideos(videos: List<VideoItemEntity>, sort: VideoSortOption): List<VideoItemEntity> {
        return when (sort) {
            VideoSortOption.DATE_DESC -> videos.sortedByDescending { it.dateModified }
            VideoSortOption.NAME_ASC -> videos.sortedBy { it.title.lowercase() }
            VideoSortOption.NAME_DESC -> videos.sortedByDescending { it.title.lowercase() }
            VideoSortOption.SIZE_DESC -> videos.sortedByDescending { it.sizeBytes }
            VideoSortOption.DURATION_DESC -> videos.sortedByDescending { it.durationMs }
            VideoSortOption.RESOLUTION_DESC -> videos.sortedByDescending { it.width * it.height }
        }
    }
}
