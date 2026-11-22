package com.example.player

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.LumaApplication
import com.example.data.model.AspectRatioMode
import com.example.data.model.AudioTrackOption
import com.example.data.model.EqPreset
import com.example.data.model.FormatUtils
import com.example.data.model.PlaybackQueueItem
import com.example.data.model.PlayerOrientationMode
import com.example.data.model.SubtitleEdgeStyle
import com.example.data.model.SubtitleTrackOption
import com.example.data.model.VideoDiagnostics
import com.example.data.model.VideoTrackOption
import com.example.data.preferences.UserSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

enum class ActivePlayerSheet {
    NONE, AUDIO_TRACKS, TRACKS, AUDIO_FX, SPEED, TOOLS, DIAGNOSTICS
}

data class PlayerUiState(
    val title: String = "Luma Player",
    val isStream: Boolean = false,
    val isCurrentItemSeekable: Boolean = true,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val playbackSpeed: Float = 1.0f,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    // 0x0 until real decoded video dimensions arrive (prevents portrait-to-landscape flash on launch)
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val aspectRatioMode: AspectRatioMode = AspectRatioMode.FIT,
    val zoomScale: Float = 1.0f,
    val isLocked: Boolean = false,
    val orientationMode: PlayerOrientationMode = PlayerOrientationMode.AUTO_VIDEO,
    val abPointAMs: Long? = null,
    val abPointBMs: Long? = null,
    val sleepTimerRemainingMs: Long? = null,
    val sleepAtEndOfVideo: Boolean = false,
    val activeSheet: ActivePlayerSheet = ActivePlayerSheet.NONE,
    val videoTracks: List<VideoTrackOption> = emptyList(),
    val isAutoMaxQuality: Boolean = true,
    val audioTracks: List<AudioTrackOption> = emptyList(),
    val audioDisabled: Boolean = false,
    val activeAudioLanguageCode: String? = null,
    val subtitleTracks: List<SubtitleTrackOption> = emptyList(),
    val subtitlesDisabled: Boolean = false,
    val activeCues: List<Cue> = emptyList(),
    val statusToast: String? = null
)

@OptIn(UnstableApi::class)
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as LumaApplication
    private val prefs = app.preferencesRepository

    val settings: StateFlow<UserSettings> = prefs.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UserSettings()
    )

    val playerFlow: StateFlow<ExoPlayer?> = PlayerSessionHolder.activePlayer
    val currentItem: StateFlow<PlaybackQueueItem?> = PlayerSessionHolder.currentItem
    val diagnostics: StateFlow<VideoDiagnostics> = PlayerSessionHolder.diagnostics
    val audioFxState: StateFlow<AudioFxState> = PlayerSessionHolder.audioFxController.state
    val backgroundPlayActive: StateFlow<Boolean> = PlayerSessionHolder.backgroundPlayActive
    val playbackError: StateFlow<String?> = PlayerSessionHolder.playbackError

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var appliedInitialPrefs = false
    private var pendingSeekTargetMs: Long? = null
    private var pendingSeekExpiresAtMs: Long = 0L

    init {
        viewModelScope.launch {
            settings.collect { userSettings ->
                if (!appliedInitialPrefs) {
                    appliedInitialPrefs = true
                    _uiState.value = _uiState.value.copy(
                        aspectRatioMode = userSettings.defaultAspectRatio,
                        orientationMode = userSettings.defaultOrientation
                    )
                }
            }
        }

        // Event-driven track extraction — runs ONLY when Media3 reports track changes
        viewModelScope.launch {
            combine(PlayerSessionHolder.activePlayer, PlayerSessionHolder.tracksVersion) { player, _ -> player }
                .collect { player ->
                    if (player != null) {
                        refreshTracks(player)
                    }
                }
        }

        // Lightweight playback ticker (200ms) for timeline, buffer bar, A-B loop, sleep timer, and synced cues
        viewModelScope.launch {
            var lastSleepTick = System.currentTimeMillis()
            while (isActive) {
                val now = System.currentTimeMillis()
                val elapsed = (now - lastSleepTick).coerceIn(0L, 1000L)
                lastSleepTick = now

                val player = PlayerSessionHolder.activePlayer.value
                val item = PlayerSessionHolder.currentItem.value
                val current = _uiState.value

                if (player != null) {
                    val rawPos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
                    val buf = player.bufferedPosition.coerceAtLeast(0L)
                    val isPlaying = player.isPlaying
                    val isBuffering = player.playbackState == Player.STATE_BUFFERING
                    val isSeekable = player.isCurrentMediaItemSeekable
                    val size = player.videoSize
                    val vWidth = if (size.width > 0) size.width else current.videoWidth
                    val vHeight = if (size.height > 0) size.height else current.videoHeight

                    // Keep rapid online/offline skip position steady while remote stream buffers to target
                    val pendingTarget = pendingSeekTargetMs
                    val displayPos = if (pendingTarget != null && now <= pendingSeekExpiresAtMs) {
                        if (!isBuffering && abs(rawPos - pendingTarget) < 1_800L) {
                            pendingSeekTargetMs = null
                            rawPos
                        } else {
                            pendingTarget
                        }
                    } else {
                        pendingSeekTargetMs = null
                        rawPos
                    }

                    // A-B Repeat check
                    val ptA = current.abPointAMs
                    val ptB = current.abPointBMs
                    if (ptA != null && ptB != null && ptB > ptA + 400L && rawPos >= ptB && isSeekable) {
                        player.seekTo(ptA)
                    }

                    // Sleep timer countdown
                    var remainingSleep = current.sleepTimerRemainingMs
                    if (remainingSleep != null && isPlaying) {
                        val nextRem = remainingSleep - elapsed
                        if (nextRem <= 0L) {
                            player.pause()
                            remainingSleep = null
                            showToast("Sleep timer finished · Playback paused")
                        } else {
                            remainingSleep = nextRem
                        }
                    }

                    // Sleep at end of video
                    var sleepEnd = current.sleepAtEndOfVideo
                    if (sleepEnd && player.playbackState == Player.STATE_ENDED) {
                        player.pause()
                        sleepEnd = false
                        showToast("Playback paused at end of video")
                    }

                    val resolvedCues = PlayerSessionHolder.subtitleSyncController.resolveCuesForPosition(rawPos)
                    val bufferedPct = player.bufferedPercentage.coerceIn(0, 100)
                    if (PlayerSessionHolder.diagnostics.value.bufferedPercent != bufferedPct) {
                        PlayerSessionHolder.updateDiagnostics { it.copy(bufferedPercent = bufferedPct) }
                    }

                    _uiState.value = current.copy(
                        title = item?.title ?: player.mediaMetadata.title?.toString() ?: "Luma Player",
                        isStream = item?.isStream ?: false,
                        isCurrentItemSeekable = isSeekable,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        positionMs = displayPos,
                        durationMs = dur,
                        bufferedPositionMs = buf,
                        playbackSpeed = player.playbackParameters.speed,
                        hasNext = player.hasNextMediaItem(),
                        hasPrevious = player.hasPreviousMediaItem(),
                        videoWidth = vWidth,
                        videoHeight = vHeight,
                        sleepTimerRemainingMs = remainingSleep,
                        sleepAtEndOfVideo = sleepEnd,
                        activeCues = resolvedCues
                    )
                }
                delay(200L)
            }
        }
    }

    private fun refreshTracks(player: ExoPlayer) {
        val extracted = extractTracks(player)
        _uiState.value = _uiState.value.copy(
            videoTracks = extracted.videoTracks,
            isAutoMaxQuality = extracted.isAutoMaxQuality,
            audioTracks = extracted.audioTracks,
            audioDisabled = extracted.audioDisabled,
            activeAudioLanguageCode = extracted.activeAudioLanguageCode,
            subtitleTracks = extracted.subtitleTracks,
            subtitlesDisabled = extracted.subtitlesDisabled
        )
    }

    fun togglePlayPause() {
        val player = PlayerSessionHolder.activePlayer.value ?: return
        if (player.playbackState == Player.STATE_ENDED) {
            pendingSeekTargetMs = null
            player.seekTo(0L)
            player.play()
        } else if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    /**
     * Layer 4 online seek safeguard:
     * Never calls player.seekTo() blindly on an unseekable remote stream (which causes ExoPlayer
     * to reset playback back to 00:00), and clamps targets safely when duration is unknown or near EOF.
     */
    fun seekTo(positionMs: Long): Boolean {
        val player = PlayerSessionHolder.activePlayer.value ?: return false
        if (!player.isCurrentMediaItemSeekable) {
            showToast("Server does not allow skipping on this stream")
            return false
        }
        val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
        val safeUpper = when {
            dur != null -> (dur - 400L).coerceAtLeast(0L)
            else -> maxOf(player.bufferedPosition, player.currentPosition)
        }
        val target = positionMs.coerceIn(0L, safeUpper.coerceAtLeast(0L))
        pendingSeekTargetMs = target
        pendingSeekExpiresAtMs = System.currentTimeMillis() + 1_100L
        player.seekTo(target)
        _uiState.value = _uiState.value.copy(positionMs = target)
        return true
    }

    fun seekBy(deltaMs: Long): Boolean {
        val player = PlayerSessionHolder.activePlayer.value ?: return false
        if (!player.isCurrentMediaItemSeekable) {
            showToast("Server does not allow skipping on this stream")
            return false
        }
        val now = System.currentTimeMillis()
        val basePos = if (pendingSeekTargetMs != null && now <= pendingSeekExpiresAtMs) {
            pendingSeekTargetMs!!
        } else {
            player.currentPosition.coerceAtLeast(0L)
        }
        val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
        val safeUpper = when {
            dur != null -> (dur - 400L).coerceAtLeast(0L)
            else -> maxOf(player.bufferedPosition, basePos)
        }
        val target = (basePos + deltaMs).coerceIn(0L, safeUpper.coerceAtLeast(0L))
        pendingSeekTargetMs = target
        pendingSeekExpiresAtMs = now + 1_200L
        player.seekTo(target)
        _uiState.value = _uiState.value.copy(positionMs = target)
        return true
    }

    fun skipNext() {
        val player = PlayerSessionHolder.activePlayer.value ?: return
        if (player.hasNextMediaItem()) {
            pendingSeekTargetMs = null
            player.seekToNextMediaItem()
        }
    }

    fun skipPrevious() {
        val player = PlayerSessionHolder.activePlayer.value ?: return
        pendingSeekTargetMs = null
        if (player.currentPosition > 4_000L && player.isCurrentMediaItemSeekable) {
            player.seekTo(0L)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else if (player.isCurrentMediaItemSeekable) {
            player.seekTo(0L)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.25f, 4.0f)
        PlayerSessionHolder.activePlayer.value?.setPlaybackSpeed(clamped)
        _uiState.value = _uiState.value.copy(playbackSpeed = clamped)
    }

    fun setAspectRatioMode(mode: AspectRatioMode) {
        _uiState.value = _uiState.value.copy(aspectRatioMode = mode, zoomScale = 1.0f)
        showToast("Aspect Ratio: ${mode.label}")
    }

    fun cycleAspectRatio() {
        val all = AspectRatioMode.entries
        val nextIdx = (all.indexOf(_uiState.value.aspectRatioMode) + 1) % all.size
        setAspectRatioMode(all[nextIdx])
    }

    fun updateZoomScale(scale: Float) {
        _uiState.value = _uiState.value.copy(zoomScale = scale.coerceIn(0.5f, 4.0f))
    }

    fun resetZoom() {
        _uiState.value = _uiState.value.copy(zoomScale = 1.0f, aspectRatioMode = AspectRatioMode.FIT)
        showToast("Reset to Fit (Original Ratio)")
    }

    fun toggleScreenLock() {
        val next = !_uiState.value.isLocked
        _uiState.value = _uiState.value.copy(
            isLocked = next,
            activeSheet = if (next) ActivePlayerSheet.NONE else _uiState.value.activeSheet
        )
    }

    fun setOrientationMode(mode: PlayerOrientationMode) {
        _uiState.value = _uiState.value.copy(orientationMode = mode)
        showToast("Orientation: ${mode.label}")
    }

    fun cycleOrientation() {
        val next = when (_uiState.value.orientationMode) {
            PlayerOrientationMode.AUTO_VIDEO, PlayerOrientationMode.SENSOR -> PlayerOrientationMode.LANDSCAPE
            PlayerOrientationMode.LANDSCAPE -> PlayerOrientationMode.PORTRAIT
            PlayerOrientationMode.PORTRAIT -> PlayerOrientationMode.REVERSE_LANDSCAPE
            PlayerOrientationMode.REVERSE_LANDSCAPE -> PlayerOrientationMode.SENSOR
        }
        setOrientationMode(next)
    }

    fun openSheet(sheet: ActivePlayerSheet) {
        _uiState.value = _uiState.value.copy(activeSheet = sheet)
    }

    fun closeSheet() {
        _uiState.value = _uiState.value.copy(activeSheet = ActivePlayerSheet.NONE)
    }

    fun selectAutoMaxQuality() {
        val selector = PlayerSessionHolder.serviceInstance?.getTrackSelector() ?: return
        selector.setParameters(
            selector.buildUponParameters()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .setForceHighestSupportedBitrate(true)
        )
        PlayerSessionHolder.activePlayer.value?.let { refreshTracks(it) }
        showToast("Quality: Auto Maximum (HDR Priority)")
    }

    fun selectVideoTrack(option: VideoTrackOption) {
        val player = PlayerSessionHolder.activePlayer.value ?: return
        val selector = PlayerSessionHolder.serviceInstance?.getTrackSelector() ?: return
        val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        selector.setParameters(
            selector.buildUponParameters()
                .setForceHighestSupportedBitrate(false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
        )
        refreshTracks(player)
        showToast("Quality locked: ${option.label}")
    }

    fun selectAudioTrack(option: AudioTrackOption?) {
        val player = PlayerSessionHolder.activePlayer.value ?: return
        val selector = PlayerSessionHolder.serviceInstance?.getTrackSelector() ?: return
        if (option == null) {
            selector.setParameters(
                selector.buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true)
            )
            refreshTracks(player)
            showToast("Audio Muted")
            return
        }
        val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        selector.setParameters(
            selector.buildUponParameters()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
        )
        refreshTracks(player)
        showToast("Audio: ${option.label}")
    }

    fun selectSubtitleTrack(option: SubtitleTrackOption?) {
        val player = PlayerSessionHolder.activePlayer.value ?: return
        val selector = PlayerSessionHolder.serviceInstance?.getTrackSelector() ?: return
        if (option == null) {
            selector.setParameters(
                selector.buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            )
            refreshTracks(player)
            showToast("Subtitles Off")
        } else {
            val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
            selector.setParameters(
                selector.buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
            )
            refreshTracks(player)
            showToast("Subtitles: ${option.label}")
        }
    }

    fun attachExternalSubtitle(uri: Uri) {
        PlayerSessionHolder.serviceInstance?.attachExternalSubtitle(uri)
        showToast("External subtitle loaded")
    }

    fun updateSubtitleStyle(
        scale: Float? = null,
        colorArgb: Int? = null,
        bgOpacity: Float? = null,
        edgeStyle: SubtitleEdgeStyle? = null,
        bottomPadding: Float? = null,
        syncOffsetMs: Long? = null
    ) {
        syncOffsetMs?.let { PlayerSessionHolder.subtitleSyncController.setSyncOffsetMs(it) }
        viewModelScope.launch {
            prefs.updateSubtitleSettings(
                scale = scale,
                colorArgb = colorArgb,
                bgOpacity = bgOpacity,
                edgeStyle = edgeStyle,
                bottomPadding = bottomPadding,
                syncOffsetMs = syncOffsetMs
            )
        }
    }

    fun setEqEnabled(enabled: Boolean) {
        val current = PlayerSessionHolder.audioFxController.state.value
        PlayerSessionHolder.audioFxController.applyConfig(
            eqEnabled = enabled,
            preset = current.preset,
            bandLevels = current.bandLevelsMillibels,
            boostPercent = current.boostPercent
        )
        viewModelScope.launch { prefs.updateAudioFx(eqEnabled = enabled) }
    }

    fun selectEqPreset(preset: EqPreset) {
        val current = PlayerSessionHolder.audioFxController.state.value
        val bands = if (preset == EqPreset.CUSTOM) current.bandLevelsMillibels else preset.bandOffsetsMillibels
        PlayerSessionHolder.audioFxController.applyConfig(
            eqEnabled = true,
            preset = preset,
            bandLevels = bands,
            boostPercent = current.boostPercent
        )
        viewModelScope.launch {
            prefs.updateAudioFx(eqEnabled = true, preset = preset, bands = bands)
        }
    }

    fun setEqBandLevel(bandIndex: Int, levelMillibels: Int) {
        PlayerSessionHolder.audioFxController.setBandLevel(bandIndex, levelMillibels)
        val updated = PlayerSessionHolder.audioFxController.state.value
        viewModelScope.launch {
            prefs.updateAudioFx(
                eqEnabled = true,
                preset = EqPreset.CUSTOM,
                bands = updated.bandLevelsMillibels
            )
        }
    }

    fun setAudioBoost(percent: Int) {
        val clamped = percent.coerceIn(0, 200)
        PlayerSessionHolder.audioFxController.setBoostPercent(clamped)
        viewModelScope.launch { prefs.updateAudioFx(boostPercent = clamped) }
    }

    fun setAbPointA() {
        val pos = PlayerSessionHolder.activePlayer.value?.currentPosition ?: return
        val existingB = _uiState.value.abPointBMs
        _uiState.value = _uiState.value.copy(
            abPointAMs = pos,
            abPointBMs = if (existingB != null && existingB <= pos) null else existingB
        )
        showToast("A-B Point A set")
    }

    fun setAbPointB() {
        val pos = PlayerSessionHolder.activePlayer.value?.currentPosition ?: return
        val existingA = _uiState.value.abPointAMs ?: 0L
        if (pos <= existingA + 400L) {
            showToast("Point B must be after Point A")
            return
        }
        _uiState.value = _uiState.value.copy(abPointAMs = existingA, abPointBMs = pos)
        showToast("A-B Repeat Active")
    }

    fun clearAbRepeat() {
        _uiState.value = _uiState.value.copy(abPointAMs = null, abPointBMs = null)
        showToast("A-B Repeat Cleared")
    }

    fun setSleepTimerMinutes(minutes: Int) {
        _uiState.value = _uiState.value.copy(
            sleepTimerRemainingMs = minutes * 60_000L,
            sleepAtEndOfVideo = false
        )
        showToast("Sleep timer: $minutes min")
    }

    fun setSleepAtEndOfVideo() {
        _uiState.value = _uiState.value.copy(
            sleepTimerRemainingMs = null,
            sleepAtEndOfVideo = true
        )
        showToast("Will pause at end of video")
    }

    fun cancelSleepTimer() {
        _uiState.value = _uiState.value.copy(
            sleepTimerRemainingMs = null,
            sleepAtEndOfVideo = false
        )
        showToast("Sleep timer canceled")
    }

    fun enableBackgroundAudioForSession() {
        PlayerSessionHolder.setBackgroundPlayActive(true)
        val player = PlayerSessionHolder.activePlayer.value
        if (player != null && !player.isPlaying && player.playbackState != Player.STATE_ENDED) {
            player.play()
        }
        showToast("Playing in background (this session)")
    }

    fun toggleBackgroundPlayForSession() {
        val next = !PlayerSessionHolder.backgroundPlayActive.value
        PlayerSessionHolder.setBackgroundPlayActive(next)
        showToast(
            if (next) "Background audio on for this session"
            else "Background audio off"
        )
    }

    fun retryPlayback() {
        PlayerSessionHolder.serviceInstance?.retryCurrentPlayback()
    }

    fun persistProgressNow() {
        PlayerSessionHolder.serviceInstance?.persistCurrentProgress()
    }

    fun showToast(message: String) {
        _uiState.value = _uiState.value.copy(statusToast = message)
        viewModelScope.launch {
            delay(2_200L)
            if (_uiState.value.statusToast == message) {
                _uiState.value = _uiState.value.copy(statusToast = null)
            }
        }
    }

    private data class ExtractedTracks(
        val videoTracks: List<VideoTrackOption>,
        val isAutoMaxQuality: Boolean,
        val audioTracks: List<AudioTrackOption>,
        val audioDisabled: Boolean,
        val activeAudioLanguageCode: String?,
        val subtitleTracks: List<SubtitleTrackOption>,
        val subtitlesDisabled: Boolean
    )

    private fun extractTracks(player: ExoPlayer): ExtractedTracks {
        val videoList = mutableListOf<VideoTrackOption>()
        val audioList = mutableListOf<AudioTrackOption>()
        val subList = mutableListOf<SubtitleTrackOption>()

        val selectorParams = PlayerSessionHolder.serviceInstance?.getTrackSelector()?.parameters
        val hasVideoOverride = selectorParams?.overrides?.values?.any { it.type == C.TRACK_TYPE_VIDEO } == true
        val subsDisabled = selectorParams?.disabledTrackTypes?.contains(C.TRACK_TYPE_TEXT) == true
        val audioDisabled = selectorParams?.disabledTrackTypes?.contains(C.TRACK_TYPE_AUDIO) == true

        player.currentTracks.groups.forEachIndexed { groupIdx, group ->
            when (group.type) {
                C.TRACK_TYPE_VIDEO -> {
                    for (i in 0 until group.length) {
                        if (!group.isTrackSupported(i)) continue
                        val f = group.getTrackFormat(i)
                        val hdr = LumaPlaybackService.detectHdrLabel(f).takeIf { it != "SDR" }
                        videoList.add(
                            VideoTrackOption(
                                id = "${groupIdx}_$i",
                                groupIndex = groupIdx,
                                trackIndex = i,
                                width = f.width,
                                height = f.height,
                                bitrateBps = f.bitrate,
                                frameRate = f.frameRate,
                                codec = LumaPlaybackService.simplifyCodec(f.sampleMimeType, f.codecs),
                                hdrLabel = hdr,
                                isSelected = group.isTrackSelected(i)
                            )
                        )
                    }
                }
                C.TRACK_TYPE_AUDIO -> {
                    for (i in 0 until group.length) {
                        val f = group.getTrackFormat(i)
                        val shortLang = FormatUtils.shortLanguageCode(f.language)
                        val displayLang = FormatUtils.decodeLanguageName(f.language)
                        val rawLabel = f.label?.trim().orEmpty()
                        val formattedLabel = when {
                            rawLabel.isNotBlank() && !rawLabel.equals(displayLang, ignoreCase = true) ->
                                "$displayLang · $rawLabel"
                            displayLang != "Original" ->
                                "$displayLang (Track ${audioList.size + 1})"
                            else ->
                                "Audio Track ${audioList.size + 1}"
                        }
                        audioList.add(
                            AudioTrackOption(
                                id = "${groupIdx}_$i",
                                groupIndex = groupIdx,
                                trackIndex = i,
                                label = formattedLabel,
                                language = shortLang,
                                languageDisplayName = displayLang,
                                codec = LumaPlaybackService.simplifyCodec(f.sampleMimeType, f.codecs),
                                channels = f.channelCount,
                                sampleRateHz = f.sampleRate,
                                bitrateBps = f.bitrate,
                                isSelected = !audioDisabled && group.isTrackSelected(i)
                            )
                        )
                    }
                }
                C.TRACK_TYPE_TEXT -> {
                    for (i in 0 until group.length) {
                        val f = group.getTrackFormat(i)
                        val shortLang = if (f.language.isNullOrBlank()) "EXT" else FormatUtils.shortLanguageCode(f.language)
                        val displayLang = if (f.language.isNullOrBlank()) "External" else FormatUtils.decodeLanguageName(f.language)
                        val rawLabel = f.label?.trim().orEmpty()
                        val formattedLabel = when {
                            rawLabel.isNotBlank() && !rawLabel.equals(displayLang, ignoreCase = true) ->
                                "$displayLang · $rawLabel"
                            displayLang != "Original" ->
                                "$displayLang (Sub ${subList.size + 1})"
                            else ->
                                "Subtitle ${subList.size + 1}"
                        }
                        subList.add(
                            SubtitleTrackOption(
                                id = "${groupIdx}_$i",
                                groupIndex = groupIdx,
                                trackIndex = i,
                                label = formattedLabel,
                                language = shortLang,
                                languageDisplayName = displayLang,
                                mimeType = f.sampleMimeType?.substringAfter('/')?.uppercase() ?: "TEXT",
                                isSelected = !subsDisabled && group.isTrackSelected(i)
                            )
                        )
                    }
                }
            }
        }

        val activeAudioCode = if (audioDisabled) {
            "MUTE"
        } else {
            audioList.firstOrNull { it.isSelected }?.language
        }

        return ExtractedTracks(
            videoTracks = videoList.sortedWith(compareByDescending<VideoTrackOption> { it.height }.thenByDescending { it.bitrateBps }),
            isAutoMaxQuality = !hasVideoOverride,
            audioTracks = audioList,
            audioDisabled = audioDisabled,
            activeAudioLanguageCode = activeAudioCode,
            subtitleTracks = subList,
            subtitlesDisabled = subsDisabled || subList.none { it.isSelected }
        )
    }
}
