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
import androidx.media3.common.Tracks
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.LumaApplication
import com.example.data.model.DecoderMode
import com.example.data.model.PlaybackQueueItem
import com.example.data.model.PlaybackRequest
import com.example.data.model.SeekPrecisionMode
import com.example.data.model.StreamProtocol
import com.example.data.model.UserAgentProfile
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

    private data class EngineBuildKey(
        val minBufferSec: Int,
        val maxBufferSec: Int,
        val backBufferSec: Int,
        val decoderMode: DecoderMode
    )

    private data class HttpClientKey(
        val timeoutSec: Int,
        val forceIdentityEncoding: Boolean
    )

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var currentQueue: List<PlaybackQueueItem> = emptyList()
    private var latestSettings: UserSettings = UserSettings()
    private var activeEngineKey: EngineBuildKey? = null
    private var activeHttpKey: HttpClientKey? = null
    private var cachedOkHttpClient: OkHttpClient? = null
    private var totalDroppedFrames: Int = 0

    override fun onCreate() {
        super.onCreate()
        val app = application as LumaApplication

        // Build initial engine with defaults immediately so service startup is instant
        rebuildPlayerEngine(latestSettings)

        serviceScope.launch {
            app.preferencesRepository.settingsFlow.collect { settings ->
                val prev = latestSettings
                latestSettings = settings
                PlayerSessionHolder.subtitleSyncController.setSyncOffsetMs(settings.subtitleSyncOffsetMs)
                applyLiveSettingsToPlayer(settings)

                // If core engine parameters changed while idle (nothing playing), rebuild proactively
                val desiredKey = engineKeyFor(settings)
                val player = exoPlayer
                val isIdle = player == null ||
                    (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) &&
                    currentQueue.isEmpty()
                if (desiredKey != activeEngineKey && isIdle) {
                    rebuildPlayerEngine(settings)
                } else if (prev.preferredAudioLanguage != settings.preferredAudioLanguage ||
                    prev.preferredSubtitleLanguage != settings.preferredSubtitleLanguage ||
                    prev.maxStreamHeightPx != settings.maxStreamHeightPx ||
                    prev.enableTunneling != settings.enableTunneling
                ) {
                    applyTrackSelectorPreferences(settings, clearVideoOverride = false)
                }
            }
        }

        loadPendingRequestIfAny()
    }

    private fun engineKeyFor(settings: UserSettings): EngineBuildKey = EngineBuildKey(
        minBufferSec = settings.minBufferSeconds,
        maxBufferSec = settings.maxBufferSeconds,
        backBufferSec = settings.backBufferSeconds,
        decoderMode = settings.decoderMode
    )

    private fun rebuildPlayerEngine(settings: UserSettings) {
        val oldPlayer = exoPlayer
        oldPlayer?.removeListener(playerListener)
        oldPlayer?.removeAnalyticsListener(analyticsListener)

        val selector = DefaultTrackSelector(this)
        trackSelector = selector
        applyTrackSelectorPreferences(settings, clearVideoOverride = true)

        val renderersFactory = buildRenderersFactory(settings.decoderMode)

        val minMs = (settings.minBufferSeconds * 1000).coerceIn(5_000, 60_000)
        val maxMs = (settings.maxBufferSeconds * 1000).coerceIn(maxOf(minMs + 5_000, 15_000), 180_000)
        val backMs = (settings.backBufferSeconds * 1000).coerceIn(0, 120_000)

        // Configurable buffer watermarks + retained back-buffer for instant backward online skips
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ minMs,
                /* maxBufferMs = */ maxMs,
                /* bufferForPlaybackMs = */ 1_200,
                /* bufferForPlaybackAfterRebufferMs = */ 2_500
            )
            .setBackBuffer(
                /* backBufferDurationMs = */ backMs,
                /* retainBackBufferFromKeyframe = */ true
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
            .setHandleAudioBecomingNoisy(settings.pauseOnHeadphonesUnplugged)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            .build()

        player.addListener(playerListener)
        player.addAnalyticsListener(analyticsListener)
        exoPlayer = player
        activeEngineKey = engineKeyFor(settings)

        applyLiveSettingsToPlayer(settings)

        // Update or create MediaSession cleanly
        val sessionIntent = Intent(this, PlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            1001,
            sessionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val existingSession = mediaSession
        if (existingSession != null) {
            existingSession.player = player
        } else {
            mediaSession = MediaSession.Builder(this, player)
                .setSessionActivity(pendingIntent)
                .build()
        }

        oldPlayer?.release()
        PlayerSessionHolder.registerPlayer(this, player)
    }

    private fun buildRenderersFactory(decoderMode: DecoderMode): DefaultRenderersFactory {
        val factory = DefaultRenderersFactory(this)
        return when (decoderMode) {
            DecoderMode.AUTO_HW_FALLBACK -> factory
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                .setEnableDecoderFallback(true)
            DecoderMode.PREFER_HARDWARE -> factory
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
                .setEnableDecoderFallback(true)
            DecoderMode.PREFER_SOFTWARE -> factory
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                .setEnableDecoderFallback(true)
                .setMediaCodecSelector(
                    MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                        val infos = MediaCodecUtil.getDecoderInfos(
                            mimeType,
                            requiresSecureDecoder,
                            requiresTunnelingDecoder
                        )
                        // Prioritize software decoders first while preserving hardware fallback
                        infos.sortedByDescending { info ->
                            info.softwareOnly ||
                                info.name.contains("google", ignoreCase = true) ||
                                info.name.contains("sw", ignoreCase = true)
                        }
                    }
                )
        }
    }

    private fun getOrBuildOkHttpClient(settings: UserSettings): OkHttpClient {
        val key = HttpClientKey(
            timeoutSec = settings.networkTimeoutSeconds,
            forceIdentityEncoding = settings.forceIdentityEncoding
        )
        val existing = cachedOkHttpClient
        if (existing != null && activeHttpKey == key) {
            return existing
        }
        val timeout = settings.networkTimeoutSeconds.toLong().coerceIn(10L, 60L)
        val builder = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(timeout, TimeUnit.SECONDS)
            .readTimeout(timeout, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (settings.forceIdentityEncoding) {
            // Prevents OkHttp BridgeInterceptor from injecting Accept-Encoding: gzip, which causes
            // many video hosts to omit Content-Length / Accept-Ranges and restart seeks from 00:00!
            builder.addInterceptor { chain ->
                val req = chain.request()
                val updated = req.newBuilder()
                    .header("Accept-Encoding", "identity")
                    .build()
                chain.proceed(updated)
            }
        }

        return builder.build().also {
            cachedOkHttpClient = it
            activeHttpKey = key
        }
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
        if (request.queue.isEmpty()) return

        // Rebuild ExoPlayer cleanly if Advanced buffer/decoder settings changed since last playback
        if (engineKeyFor(latestSettings) != activeEngineKey || exoPlayer == null) {
            rebuildPlayerEngine(latestSettings)
        }
        val player = exoPlayer ?: return

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

        // Respect autoLoadSiblingSubtitles preference for local videos
        val effectiveQueue = if (latestSettings.autoLoadSiblingSubtitles) {
            request.queue
        } else {
            request.queue.map { item -> if (!item.isStream) item.copy(subtitleUri = null) else item }
        }

        currentQueue = effectiveQueue
        val startIndex = request.startIndex.coerceIn(0, effectiveQueue.lastIndex)
        val activeItem = effectiveQueue[startIndex]
        PlayerSessionHolder.updateCurrentItem(activeItem)
        // Session-scoped opt-in: OFF by default unless explicitly launched for background audio
        PlayerSessionHolder.setBackgroundPlayActive(request.startInBackground)
        applySeekParameters(player, activeItem, latestSettings.seekPrecisionMode)

        val mergedHeaders = activeItem.headers
        val mediaSources = effectiveQueue.map { item ->
            buildMediaSourceForItem(item, item.headers.ifEmpty { mergedHeaders })
        }

        // Apply preferred audio/subtitle languages, resolution cap, and reset manual video overrides
        applyTrackSelectorPreferences(latestSettings, clearVideoOverride = true)

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
        val currentPos = player.currentPosition.coerceAtLeast(0L)
        val wasPlaying = player.playWhenReady

        val updatedItem = item.copy(subtitleUri = subtitleUri.toString())
        val updatedQueue = currentQueue.toMutableList().apply {
            this[index] = updatedItem
        }
        currentQueue = updatedQueue
        PlayerSessionHolder.updateCurrentItem(updatedItem)

        // Bug fix: Rebuild full queue so attaching a subtitle never discards the rest of the playlist
        val mediaSources = updatedQueue.map { qItem ->
            buildMediaSourceForItem(qItem, qItem.headers)
        }
        player.setMediaSources(mediaSources, index, currentPos)
        player.prepare()
        player.playWhenReady = wasPlaying
    }

    fun retryCurrentPlayback() {
        val player = exoPlayer ?: return
        PlayerSessionHolder.setPlaybackError(null)
        val pos = player.currentPosition.coerceAtLeast(0L)
        if (currentQueue.isNotEmpty()) {
            val idx = player.currentMediaItemIndex.coerceIn(0, currentQueue.lastIndex)
            val sources = currentQueue.map { buildMediaSourceForItem(it, it.headers) }
            player.setMediaSources(sources, idx, pos)
            player.prepare()
            player.playWhenReady = true
        } else {
            player.seekTo(pos)
            player.prepare()
            player.playWhenReady = true
        }
    }

    fun getTrackSelector(): DefaultTrackSelector? = trackSelector

    fun persistCurrentProgress() {
        val player = exoPlayer ?: return
        val item = currentQueue.getOrNull(player.currentMediaItemIndex)
            ?: PlayerSessionHolder.currentItem.value
            ?: return
        val pos = player.currentPosition.coerceAtLeast(0L)
        val dur = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
        // Avoid overwriting saved resume progress if playback never got past initial buffering
        if (pos < 1_500L && player.playbackState != Player.STATE_ENDED) return
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

    private fun resolveUserAgent(customHeaderUa: String?): String {
        if (!customHeaderUa.isNullOrBlank()) return customHeaderUa
        return when (latestSettings.userAgentProfile) {
            UserAgentProfile.CUSTOM -> latestSettings.customUserAgent.trim()
                .ifBlank { UserAgentProfile.CHROME_MOBILE.userAgentValue }
            else -> latestSettings.userAgentProfile.userAgentValue
        }
    }

    private fun buildMediaSourceForItem(
        item: PlaybackQueueItem,
        headers: Map<String, String>
    ): androidx.media3.exoplayer.source.MediaSource {
        val client = getOrBuildOkHttpClient(latestSettings)
        val cleanHeaders = headers
            .filterKeys { it.isNotBlank() }
            .filterValues { it.isNotBlank() }
            .toMutableMap()

        if (latestSettings.forceIdentityEncoding &&
            cleanHeaders.keys.none { it.equals("Accept-Encoding", ignoreCase = true) }
        ) {
            cleanHeaders["Accept-Encoding"] = "identity"
        }

        val customUa = cleanHeaders.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
        val resolvedUa = resolveUserAgent(customUa)

        val httpFactory = OkHttpDataSource.Factory(client).apply {
            setUserAgent(resolvedUa)
            if (cleanHeaders.isNotEmpty()) {
                setDefaultRequestProperties(cleanHeaders)
            }
        }

        // Layer 3: Wrap remote streams in LRU SimpleCache when enabled so backward/repeat seeks stay local
        val upstreamFactory: DataSource.Factory = if (item.isStream && latestSettings.streamCacheSizeMb > 0) {
            val cache = LumaStreamCache.getOrCreateCache(this, latestSettings.streamCacheSizeMb)
            if (cache != null) {
                CacheDataSource.Factory()
                    .setCache(cache)
                    .setUpstreamDataSourceFactory(httpFactory)
                    .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            } else {
                httpFactory
            }
        } else {
            httpFactory
        }

        val dataSourceFactory = DefaultDataSource.Factory(this, upstreamFactory)

        // Layer 2: Constant-bitrate seeking flags keep unindexed HTTP MP4/MKV/TS streams seekable!
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(latestSettings.enableCbrSeeking)
            .setConstantBitrateSeekingAlwaysEnabled(latestSettings.enableCbrSeeking)

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

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

    private fun applyLiveSettingsToPlayer(settings: UserSettings) {
        val player = exoPlayer ?: return
        applyFrameRateStrategy(settings.matchDisplayFrameRate)
        player.skipSilenceEnabled = settings.skipSilenceEnabled
        player.setHandleAudioBecomingNoisy(settings.pauseOnHeadphonesUnplugged)
        val activeItem = PlayerSessionHolder.currentItem.value
        if (activeItem != null) {
            applySeekParameters(player, activeItem, settings.seekPrecisionMode)
        }
    }

    private fun applyTrackSelectorPreferences(
        settings: UserSettings,
        clearVideoOverride: Boolean
    ) {
        val selector = trackSelector ?: return
        val builder = selector.buildUponParameters()
            .setAllowVideoMixedMimeTypeAdaptiveness(true)
            .setAllowVideoNonSeamlessAdaptiveness(true)
            .setAllowAudioMixedMimeTypeAdaptiveness(true)
            .setExceedVideoConstraintsIfNecessary(false)
            .setExceedRendererCapabilitiesIfNecessary(false)
            .setTunnelingEnabled(settings.enableTunneling)

        if (clearVideoOverride) {
            builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        }

        if (settings.maxStreamHeightPx > 0) {
            builder
                .setForceHighestSupportedBitrate(false)
                .setMaxVideoSize(Int.MAX_VALUE, settings.maxStreamHeightPx)
                .setMaxVideoBitrate(Int.MAX_VALUE)
                .setMaxVideoFrameRate(240)
        } else {
            builder
                .setForceHighestSupportedBitrate(true)
                .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                .setMaxVideoBitrate(Int.MAX_VALUE)
                .setMaxVideoFrameRate(240)
        }

        if (settings.preferredAudioLanguage.isNotBlank()) {
            builder.setPreferredAudioLanguages(settings.preferredAudioLanguage)
        } else {
            builder.setPreferredAudioLanguage(null)
        }

        if (settings.preferredSubtitleLanguage.isNotBlank()) {
            builder.setPreferredTextLanguages(settings.preferredSubtitleLanguage)
        } else {
            builder.setPreferredTextLanguage(null)
        }

        selector.setParameters(builder)
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

    private fun applySeekParameters(
        player: ExoPlayer,
        item: PlaybackQueueItem,
        mode: SeekPrecisionMode
    ) {
        val params = when (mode) {
            SeekPrecisionMode.CLOSEST_SYNC -> SeekParameters.CLOSEST_SYNC
            SeekPrecisionMode.PREVIOUS_SYNC -> SeekParameters.PREVIOUS_SYNC
            SeekPrecisionMode.EXACT -> if (item.isStream) SeekParameters.EXACT else SeekParameters.DEFAULT
        }
        player.setSeekParameters(params)
    }

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            persistCurrentProgress()
            val player = exoPlayer ?: return
            val idx = player.currentMediaItemIndex
            currentQueue.getOrNull(idx)?.let { item ->
                PlayerSessionHolder.updateCurrentItem(item)
                applySeekParameters(player, item, latestSettings.seekPrecisionMode)
            }
            PlayerSessionHolder.subtitleSyncController.clear()
            PlayerSessionHolder.notifyTracksChanged()
        }

        override fun onTracksChanged(tracks: Tracks) {
            PlayerSessionHolder.notifyTracksChanged()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                PlayerSessionHolder.setBackgroundPlayActive(false)
            }
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
