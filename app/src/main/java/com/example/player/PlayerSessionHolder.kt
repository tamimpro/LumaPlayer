package com.example.player

import androidx.media3.exoplayer.ExoPlayer
import com.example.data.model.PlaybackQueueItem
import com.example.data.model.PlaybackRequest
import com.example.data.model.VideoDiagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide singleton bridge coordinating LumaPlaybackService, PlayerActivity,
 * and MainActivity without parceling complex custom HTTP header maps or losing active playback
 * during PiP, rotation, or background playback transitions.
 */
object PlayerSessionHolder {

    val audioFxController = AudioFxController()
    val subtitleSyncController = SubtitleSyncController()

    private val _activePlayer = MutableStateFlow<ExoPlayer?>(null)
    val activePlayer: StateFlow<ExoPlayer?> = _activePlayer.asStateFlow()

    private val _currentRequest = MutableStateFlow<PlaybackRequest?>(null)
    val currentRequest: StateFlow<PlaybackRequest?> = _currentRequest.asStateFlow()

    private val _currentItem = MutableStateFlow<PlaybackQueueItem?>(null)
    val currentItem: StateFlow<PlaybackQueueItem?> = _currentItem.asStateFlow()

    private val _diagnostics = MutableStateFlow(VideoDiagnostics())
    val diagnostics: StateFlow<VideoDiagnostics> = _diagnostics.asStateFlow()

    private val _backgroundPlayActive = MutableStateFlow(false)
    val backgroundPlayActive: StateFlow<Boolean> = _backgroundPlayActive.asStateFlow()

    private val _playbackError = MutableStateFlow<String?>(null)
    val playbackError: StateFlow<String?> = _playbackError.asStateFlow()

    private val _tracksVersion = MutableStateFlow(0L)
    val tracksVersion: StateFlow<Long> = _tracksVersion.asStateFlow()

    @Volatile
    var pendingRequest: PlaybackRequest? = null

    @Volatile
    var serviceInstance: LumaPlaybackService? = null

    fun submitRequest(request: PlaybackRequest) {
        pendingRequest = request
        _currentRequest.value = request
        _currentItem.value = request.queue.getOrNull(request.startIndex) ?: request.queue.firstOrNull()
        _playbackError.value = null
        serviceInstance?.loadPendingRequestIfAny()
    }

    fun registerPlayer(service: LumaPlaybackService, player: ExoPlayer) {
        serviceInstance = service
        _activePlayer.value = player
        notifyTracksChanged()
    }

    fun unregisterPlayer(service: LumaPlaybackService) {
        if (serviceInstance == service) {
            serviceInstance = null
            _activePlayer.value = null
            _backgroundPlayActive.value = false
            audioFxController.release()
            subtitleSyncController.clear()
        }
    }

    fun updateCurrentItem(item: PlaybackQueueItem?) {
        _currentItem.value = item
    }

    fun updateDiagnostics(transform: (VideoDiagnostics) -> VideoDiagnostics) {
        _diagnostics.value = transform(_diagnostics.value)
    }

    fun setBackgroundPlayActive(active: Boolean) {
        _backgroundPlayActive.value = active
    }

    fun setPlaybackError(error: String?) {
        _playbackError.value = error
    }

    fun notifyTracksChanged() {
        _tracksVersion.value += 1L
    }
}
