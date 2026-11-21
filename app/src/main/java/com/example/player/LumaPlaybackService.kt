package com.example.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.LumaApplication
import com.example.data.model.PlaybackQueueItem
import com.example.data.model.PlaybackRequest
import com.example.data.model.StreamProtocol
import com.example.data.model.VideoDiagnostics
import com.example.data.preferences.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class LumaPlaybackService : MediaSessionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var currentQueue: List<PlaybackQueueItem> = emptyList()
    private var latestSettings: UserSettings = UserSettings()
    private var totalDroppedFrames: Int = 0

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as LumaApplication

        serviceScope.launch {
            app.preferencesRepository.settingsFlow.collect { settings ->
                latestSettings = settings
                PlayerSessionHolder.subtitleSyncController.setSyncOffsetMs(settings.subtitleSyncOffsetMs)
                applyFrameRateStrategy(settings.matchDisplayFrameRate)
            }
        }

        val selector = DefaultTrackSelector(this).apply {
            setParameters(
                buildUponParameters()
                    // Preserve 100% original maximum quality & HDR capability supported by device hardware
                    .setForceHighestSupportedBitrate(true)
                    .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                    .setMaxVideoBitrate(Int.MAX_VALUE)
                    .setMaxVideoFrameRate(240)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowVideoNonSeamlessAdaptiveness(true)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
                    .setExceedVideoConstraintsIfNecessary(false)
                    .setExceedRendererCapabilitiesIfNecessary(false)
            )
        }
        trackSelector = selector

        val renderersFactory = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)

        // Tuned buffer watermarks for smooth 4K/HDR/high-bitrate playback and fast buffering recovery
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 60_000,
                /* bufferForPlaybackMs = */ 1_500,
                /* bufferForPlaybackAfterRebufferMs = */ 3_000
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        val player = ExoPlayer.Builder(this, renderersFactory)
            .setTrackSelector(selector)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            player.videoChangeFrameRateStrategy = C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
        }

        player.addListener(playerListener)
        player.addAnalyticsListener(analyticsListener)
        exoPlayer = player

        val sessionIntent = Intent(this, PlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1001,
            sessionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .build()

        PlayerSessionHolder.registerPlayer(this, player)
        loadPendingRequestIfAny()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        loadPendingRequestIfAny()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = exoPlayer
        if (player == null || !player.playWhenReady || !PlayerSessionHolder.backgroundPlayActive.value) {
            persistCurrentProgress()
            stopSelf()
        }
    }

    override fun onDestroy() {
        persistCurrentProgress()
        PlayerSessionHolder.unregisterPlayer(this)
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.removeAnalyticsListener(analyticsListener)
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
        serviceScope.cancel()
        super.onDestroy()
    }

    fun loadPendingRequestIfAny() {
        val request = PlayerSessionHolder.pendingRequest ?: return
        PlayerSessionHolder.pendingRequest = null
        loadRequest(request)
    }

    fun loadRequest(request: PlaybackRequest) {
        val player = exoPlayer ?: return
        if (request.queue.isEmpty()) return

        persistCurrentProgress()
        totalDroppedFrames = 0
        PlayerSessionHolder.subtitleSyncController.clear()
        PlayerSessionHolder.setPlaybackError(null)
        PlayerSessionHolder.updateDiagnostics {
            VideoDiagnostics(
                displayHdrCapabilities = it.displayHdrCapabilities,
                displayRefreshRateHz = it.displayRefreshRateHz
            )
        }

        currentQueue = request.queue
        val startIndex = request.startIndex.coerceIn(0, request.queue.lastIndex)
        val activeItem = request.queue[startIndex]
        PlayerSessionHolder.updateCurrentItem(activeItem)
        PlayerSessionHolder.setBackgroundPlayActive(request.startInBackground || latestSettings.backgroundPlaybackEnabled)

        val mergedHeaders = activeItem.headers
        val mediaSources = request.queue.map { item ->
            buildMediaSourceForItem(item, item.headers.ifEmpty { mergedHeaders })
        }

        // Reset manual track overrides so each new video starts at maximum supported quality & HDR
        trackSelector?.let { selector ->
            selector.setParameters(
                selector.buildUponParameters()
                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    .setForceHighestSupportedBitrate(true)
            )
        }

        val resumePos = if (latestSettings.autoResume && activeItem.resumePositionMs > 2_000L) {
            activeItem.resumePositionMs
        } else {
            0L
        }

        player.setMediaSources(mediaSources, startIndex, resumePos)
        player.prepare()
        player.playWhenReady = true
    }

    fun attachExternalSubtitle(subtitleUri: Uri) {
        val player = exoPlayer ?: return
        val index = player.currentMediaItemIndex
        val item = currentQueue.getOrNull(index) ?: return
        val currentPos = player.currentPosition
        val wasPlaying = player.playWhenReady

        val updatedItem = item.copy(subtitleUri = subtitleUri.toString())
        val updatedQueue = currentQueue.toMutableList().apply {
            this[index] = updatedItem
        }
        currentQueue = updatedQueue
        PlayerSessionHolder.updateCurrentItem(updatedItem)

        val newSource = buildMediaSourceForItem(updatedItem, updatedItem.headers)
        player.setMediaSource(newSource, currentPos)
        player.prepare()
        player.playWhenReady = wasPlaying
    }

    fun retryCurrentPlayback() {
        val player = exoPlayer ?: return
        PlayerSessionHolder.setPlaybackError(null)
        val pos = player.currentPosition.coerceAtLeast(0L)
        player.seekTo(pos)
        player.prepare()
        player.playWhenReady = true
    }

    fun getTrackSelector(): DefaultTrackSelector? = trackSelector

    fun persistCurrentProgress() {
        val player = exoPlayer ?: return
        val item = currentQueue.getOrNull(player.currentMediaItemIndex)
            ?: PlayerSessionHolder.currentItem.value
            ?: return
        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        val app = application as LumaApplication
        serviceScope.launch(Dispatchers.IO) {
            app.libraryRepository.savePlaybackProgress(
                uri = item.uri,
                title = item.title,
                positionMs = pos,
                durationMs = dur,
                isStream = item.isStream
            )
        }
    }

    private fun buildMediaSourceForItem(
        item: PlaybackQueueItem,
        headers: Map<String, String>
    ): androidx.media3.exoplayer.source.MediaSource {
        val httpFactory = OkHttpDataSource.Factory(okHttpClient).apply {
            val cleanHeaders = headers.filterKeys { it.isNotBlank() }.filterValues { it.isNotBlank() }
            if (cleanHeaders.isNotEmpty()) {
                setDefaultRequestProperties(cleanHeaders)
            }
            val customUa = cleanHeaders.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
            setUserAgent(customUa ?: "LumaPlayer/1.0 (Android; Media3)")
        }

        val dataSourceFactory = DefaultDataSource.Factory(this, httpFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        val mediaItemBuilder = MediaItem.Builder()
            .setUri(Uri.parse(item.uri))
            .setMediaId(item.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setDisplayTitle(item.title)
                    .build()
            )

        when (item.protocol) {
            StreamProtocol.HLS -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            StreamProtocol.DASH -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            StreamProtocol.PROGRESSIVE, StreamProtocol.AUTO -> {
                val lower = item.uri.lowercase()
                when {
                    lower.contains(".m3u8") -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
                    lower.contains(".mpd") -> mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
                }
            }
        }

        if (!item.subtitleUri.isNullOrBlank()) {
            val subUri = Uri.parse(item.subtitleUri)
            val subMime = guessSubtitleMime(item.subtitleUri)
            val subConfig = MediaItem.SubtitleConfiguration.Builder(subUri)
                .setMimeType(subMime)
                .setLabel("External Subtitle")
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            mediaItemBuilder.setSubtitleConfigurations(listOf(subConfig))
        }

        return mediaSourceFactory.createMediaSource(mediaItemBuilder.build())
    }

    private fun guessSubtitleMime(uriStr: String): String {
        val lower = uriStr.lowercase()
        return when {
            lower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            lower.endsWith(".ass") || lower.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            lower.endsWith(".ttml") || lower.endsWith(".xml") -> MimeTypes.APPLICATION_TTML
            else -> MimeTypes.APPLICATION_SUBRIP
        }
    }

    private fun applyFrameRateStrategy(enabled: Boolean) {
        val player = exoPlayer ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            player.videoChangeFrameRateStrategy = if (enabled) {
                C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
            } else {
                C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
            }
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            persistCurrentProgress()
            val idx = exoPlayer?.currentMediaItemIndex ?: return
            currentQueue.getOrNull(idx)?.let {
                PlayerSessionHolder.updateCurrentItem(it)
            }
            PlayerSessionHolder.subtitleSyncController.clear()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) {
                persistCurrentProgress()
            }
        }

        override fun onCues(cueGroup: CueGroup) {
            val pos = exoPlayer?.currentPosition ?: 0L
            PlayerSessionHolder.subtitleSyncController.onCuesReceived(pos, cueGroup.cues)
        }

        override fun onPlayerError(error: PlaybackException) {
            val detail = error.cause?.localizedMessage?.takeIf { it.isNotBlank() }
                ?: error.localizedMessage
                ?: error.errorCodeName
            PlayerSessionHolder.setPlaybackError("Playback error (${error.errorCodeName}): $detail")
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onAudioSessionIdChanged(
            eventTime: AnalyticsListener.EventTime,
            audioSessionId: Int
        ) {
            val app = application as LumaApplication
            serviceScope.launch {
                val settings = app.preferencesRepository.settingsFlow.first()
                PlayerSessionHolder.audioFxController.attachToSession(
                    audioSessionId = audioSessionId,
                    initialEqEnabled = settings.eqEnabled,
                    initialPreset = settings.eqPreset,
                    initialBands = settings.eqBandLevelsMillibels,
                    initialBoostPercent = settings.audioBoostPercent
                )
            }
        }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long
        ) {
            val isHw = !decoderName.contains("google", ignoreCase = true) &&
                !decoderName.contains("sw", ignoreCase = true) &&
                !decoderName.contains("ffmpeg", ignoreCase = true)
            PlayerSessionHolder.updateDiagnostics {
                it.copy(
                    videoDecoderName = decoderName,
                    isHardwareAccelerated = isHw
                )
            }
        }

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            val hdrLabel = detectHdrLabel(format)
            val colorSpace = describeColorInfo(format.colorInfo)
            val codecName = simplifyCodec(format.sampleMimeType, format.codecs)
            PlayerSessionHolder.updateDiagnostics {
                it.copy(
                    width = format.width.takeIf { w -> w > 0 } ?: it.width,
                    height = format.height.takeIf { h -> h > 0 } ?: it.height,
                    frameRate = format.frameRate.takeIf { fps -> fps > 0f } ?: it.frameRate,
                    videoCodec = codecName,
                    hdrFormat = hdrLabel,
                    colorSpace = colorSpace,
                    videoBitrateBps = format.bitrate.takeIf { br -> br > 0 } ?: it.videoBitrateBps
                )
            }
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?
        ) {
            val codecName = simplifyCodec(format.sampleMimeType, format.codecs)
            PlayerSessionHolder.updateDiagnostics {
                it.copy(
                    audioCodec = codecName,
                    audioChannels = format.channelCount.takeIf { c -> c > 0 } ?: it.audioChannels,
                    audioSampleRateHz = format.sampleRate.takeIf { r -> r > 0 } ?: it.audioSampleRateHz
                )
            }
        }

        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long
        ) {
            totalDroppedFrames += droppedFrames
            PlayerSessionHolder.updateDiagnostics {
                it.copy(droppedFrames = totalDroppedFrames)
            }
        }
    }

    companion object {
        fun detectHdrLabel(format: Format): String {
            val mime = format.sampleMimeType.orEmpty().lowercase()
            val codecs = format.codecs.orEmpty().lowercase()
            if (mime == MimeTypes.VIDEO_DOLBY_VISION || codecs.startsWith("dvhe") || codecs.startsWith("dvh1") || codecs.startsWith("dav1")) {
                return "Dolby Vision"
            }
            val colorInfo = format.colorInfo ?: return "SDR"
            return when (colorInfo.colorTransfer) {
                C.COLOR_TRANSFER_ST2084 -> {
                    if (colorInfo.hdrStaticInfo != null) "HDR10+" else "HDR10"
                }
                C.COLOR_TRANSFER_HLG -> "HLG HDR"
                else -> "SDR"
            }
        }

        private fun describeColorInfo(colorInfo: ColorInfo?): String {
            if (colorInfo == null) return "BT.709 (8-bit SDR)"
            val space = when (colorInfo.colorSpace) {
                C.COLOR_SPACE_BT2020 -> "BT.2020"
                C.COLOR_SPACE_BT709 -> "BT.709"
                C.COLOR_SPACE_BT601 -> "BT.601"
                else -> "Standard"
            }
            val transfer = when (colorInfo.colorTransfer) {
                C.COLOR_TRANSFER_ST2084 -> "PQ (ST2084)"
                C.COLOR_TRANSFER_HLG -> "HLG"
                C.COLOR_TRANSFER_SDR -> "SDR"
                else -> "Linear"
            }
            val bits = if (colorInfo.lumaBitdepth > 0) "${colorInfo.lumaBitdepth}-bit" else "8-bit"
            return "$space · $transfer · $bits"
        }

        fun simplifyCodec(mimeType: String?, codecs: String?): String {
            val mime = mimeType.orEmpty().lowercase()
            return when {
                mime.contains("dolby-vision") -> "Dolby Vision"
                mime.contains("av01") -> "AV1"
                mime.contains("hevc") || mime.contains("h265") -> "HEVC (H.265)"
                mime.contains("avc") || mime.contains("h264") -> "H.264 (AVC)"
                mime.contains("x-vnd.on2.vp9") -> "VP9"
                mime.contains("x-vnd.on2.vp8") -> "VP8"
                mime.contains("mp4a") -> "AAC"
                mime.contains("eac3") -> "E-AC3 (Dolby Digital+)"
                mime.contains("ac3") -> "AC3 (Dolby Digital)"
                mime.contains("flac") -> "FLAC Lossless"
                mime.contains("opus") -> "Opus"
                mime.contains("vorbis") -> "Vorbis"
                mime.contains("truehd") -> "Dolby TrueHD"
                mime.contains("dts") -> "DTS"
                !codecs.isNullOrBlank() -> codecs.uppercase()
                mime.isNotBlank() -> mime.substringAfter('/').uppercase()
                else -> "—"
            }
        }
    }
}
