package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppThemeMode
import com.example.data.model.AspectRatioMode
import com.example.data.model.DecoderMode
import com.example.data.model.EqPreset
import com.example.data.model.PlayerOrientationMode
import com.example.data.model.SeekPrecisionMode
import com.example.data.model.SubtitleEdgeStyle
import com.example.data.model.UserAgentProfile
import com.example.data.model.VideoSortOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.lumaDataStore: DataStore<Preferences> by preferencesDataStore(name = "luma_player_prefs")

data class UserSettings(
    // v2.0 default: Crisp Light (user can switch to AMOLED Dark or System Default anytime)
    val themeMode: AppThemeMode = AppThemeMode.LIGHT,
    val defaultAspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val defaultOrientation: PlayerOrientationMode = PlayerOrientationMode.AUTO_VIDEO,
    val videoSortOption: VideoSortOption = VideoSortOption.DATE_DESC,
    val autoResume: Boolean = true,
    val backgroundPlaybackEnabled: Boolean = false,
    val autoEnterPip: Boolean = true,
    val preferHardwareDecoder: Boolean = true,
    val matchDisplayFrameRate: Boolean = true,
    val brightnessGestureEnabled: Boolean = true,
    val volumeGestureEnabled: Boolean = true,
    val seekGestureEnabled: Boolean = true,
    val pinchZoomEnabled: Boolean = true,
    val doubleTapSkipSeconds: Int = 10,
    val gestureSensitivity: Float = 1.0f,
    val subtitleTextScale: Float = 1.0f,
    val subtitleTextColorArgb: Int = 0xFFFFFFFF.toInt(),
    val subtitleBgOpacity: Float = 0.55f,
    val subtitleEdgeStyle: SubtitleEdgeStyle = SubtitleEdgeStyle.OUTLINE,
    val subtitleBottomPaddingFraction: Float = 0.08f,
    val subtitleSyncOffsetMs: Long = 0L,
    val eqEnabled: Boolean = false,
    val eqPreset: EqPreset = EqPreset.FLAT,
    val eqBandLevelsMillibels: List<Int> = listOf(0, 0, 0, 0, 0),
    val audioBoostPercent: Int = 0,

    // Advanced Settings — 1. Network Buffer & Seek Engine
    val seekPrecisionMode: SeekPrecisionMode = SeekPrecisionMode.CLOSEST_SYNC,
    val minBufferSeconds: Int = 15,
    val maxBufferSeconds: Int = 60,
    val backBufferSeconds: Int = 30,
    val forceIdentityEncoding: Boolean = true,
    val enableCbrSeeking: Boolean = true,
    val networkTimeoutSeconds: Int = 25,
    val userAgentProfile: UserAgentProfile = UserAgentProfile.CHROME_MOBILE,
    val customUserAgent: String = "",

    // Advanced Settings — 2. Decoder & HDR Pipeline
    val decoderMode: DecoderMode = DecoderMode.AUTO_HW_FALLBACK,
    val hdrWindowEnabled: Boolean = true,
    val enableTunneling: Boolean = false,
    val maxStreamHeightPx: Int = 0, // 0 = Unlimited (Original Max / 4K / 8K)

    // Advanced Settings — 3. Audio & Subtitle Language Rules
    val preferredAudioLanguage: String = "",
    val preferredSubtitleLanguage: String = "",
    val autoLoadSiblingSubtitles: Boolean = true,
    val skipSilenceEnabled: Boolean = false,
    val pauseOnHeadphonesUnplugged: Boolean = true,

    // Advanced Settings — 4. Player Skin, Auto-Hide & Cache
    val hudAutoHideSeconds: Int = 4,
    val showHudClockAndBattery: Boolean = true,
    val showHudNetworkStats: Boolean = false,
    val doubleTapZoneWide: Boolean = true,
    val streamCacheSizeMb: Int = 256
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEFAULT_ASPECT = stringPreferencesKey("default_aspect")
        val DEFAULT_ORIENTATION = stringPreferencesKey("default_orientation")
        val VIDEO_SORT = stringPreferencesKey("video_sort")
        val AUTO_RESUME = booleanPreferencesKey("auto_resume")
        val BACKGROUND_PLAY = booleanPreferencesKey("background_play")
        val AUTO_PIP = booleanPreferencesKey("auto_pip")
        val PREFER_HW = booleanPreferencesKey("prefer_hw")
        val MATCH_FPS = booleanPreferencesKey("match_fps")
        val GESTURE_BRIGHTNESS = booleanPreferencesKey("gesture_brightness")
        val GESTURE_VOLUME = booleanPreferencesKey("gesture_volume")
        val GESTURE_SEEK = booleanPreferencesKey("gesture_seek")
        val GESTURE_PINCH = booleanPreferencesKey("gesture_pinch")
        val DOUBLE_TAP_SECONDS = intPreferencesKey("double_tap_seconds")
        val GESTURE_SENSITIVITY = floatPreferencesKey("gesture_sensitivity")
        val SUB_SCALE = floatPreferencesKey("sub_scale")
        val SUB_COLOR = intPreferencesKey("sub_color")
        val SUB_BG_OPACITY = floatPreferencesKey("sub_bg_opacity")
        val SUB_EDGE = stringPreferencesKey("sub_edge")
        val SUB_BOTTOM_PADDING = floatPreferencesKey("sub_bottom_padding")
        val SUB_SYNC_OFFSET = longPreferencesKey("sub_sync_offset")
        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val EQ_BANDS = stringPreferencesKey("eq_bands")
        val AUDIO_BOOST = intPreferencesKey("audio_boost")

        // Advanced Settings keys
        val SEEK_PRECISION = stringPreferencesKey("adv_seek_precision")
        val MIN_BUFFER_SEC = intPreferencesKey("adv_min_buffer_sec")
        val MAX_BUFFER_SEC = intPreferencesKey("adv_max_buffer_sec")
        val BACK_BUFFER_SEC = intPreferencesKey("adv_back_buffer_sec")
        val FORCE_IDENTITY_ENCODING = booleanPreferencesKey("adv_force_identity_encoding")
        val ENABLE_CBR_SEEKING = booleanPreferencesKey("adv_enable_cbr_seeking")
        val NETWORK_TIMEOUT_SEC = intPreferencesKey("adv_network_timeout_sec")
        val USER_AGENT_PROFILE = stringPreferencesKey("adv_user_agent_profile")
        val CUSTOM_USER_AGENT = stringPreferencesKey("adv_custom_user_agent")

        val DECODER_MODE = stringPreferencesKey("adv_decoder_mode")
        val HDR_WINDOW_ENABLED = booleanPreferencesKey("adv_hdr_window_enabled")
        val ENABLE_TUNNELING = booleanPreferencesKey("adv_enable_tunneling")
        val MAX_STREAM_HEIGHT_PX = intPreferencesKey("adv_max_stream_height_px")

        val PREFERRED_AUDIO_LANG = stringPreferencesKey("adv_preferred_audio_lang")
        val PREFERRED_SUB_LANG = stringPreferencesKey("adv_preferred_sub_lang")
        val AUTO_LOAD_SIBLING_SUBS = booleanPreferencesKey("adv_auto_load_sibling_subs")
        val SKIP_SILENCE = booleanPreferencesKey("adv_skip_silence")
        val PAUSE_ON_NOISY = booleanPreferencesKey("adv_pause_on_noisy")

        val HUD_AUTO_HIDE_SEC = intPreferencesKey("adv_hud_auto_hide_sec")
        val SHOW_HUD_CLOCK_BATTERY = booleanPreferencesKey("adv_show_hud_clock_battery")
        val SHOW_HUD_NET_STATS = booleanPreferencesKey("adv_show_hud_net_stats")
        val DOUBLE_TAP_ZONE_WIDE = booleanPreferencesKey("adv_double_tap_zone_wide")
        val STREAM_CACHE_MB = intPreferencesKey("adv_stream_cache_mb")
    }

    val settingsFlow: Flow<UserSettings> = context.lumaDataStore.data.map { prefs ->
        val bandsRaw = prefs[Keys.EQ_BANDS]
        val parsedBands = bandsRaw?.split(",")?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.size == 5 } ?: listOf(0, 0, 0, 0, 0)

        val minBuf = (prefs[Keys.MIN_BUFFER_SEC] ?: 15).coerceIn(5, 60)
        val maxBuf = (prefs[Keys.MAX_BUFFER_SEC] ?: 60).coerceIn(maxOf(minBuf + 5, 15), 180)

        UserSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                ?: AppThemeMode.LIGHT,
            defaultAspectRatio = prefs[Keys.DEFAULT_ASPECT]?.let { runCatching { AspectRatioMode.valueOf(it) }.getOrNull() }
                ?: AspectRatioMode.FIT,
            defaultOrientation = prefs[Keys.DEFAULT_ORIENTATION]?.let { runCatching { PlayerOrientationMode.valueOf(it) }.getOrNull() }
                ?: PlayerOrientationMode.AUTO_VIDEO,
            videoSortOption = prefs[Keys.VIDEO_SORT]?.let { runCatching { VideoSortOption.valueOf(it) }.getOrNull() }
                ?: VideoSortOption.DATE_DESC,
            autoResume = prefs[Keys.AUTO_RESUME] ?: true,
            backgroundPlaybackEnabled = prefs[Keys.BACKGROUND_PLAY] ?: false,
            autoEnterPip = prefs[Keys.AUTO_PIP] ?: true,
            preferHardwareDecoder = prefs[Keys.PREFER_HW] ?: true,
            matchDisplayFrameRate = prefs[Keys.MATCH_FPS] ?: true,
            brightnessGestureEnabled = prefs[Keys.GESTURE_BRIGHTNESS] ?: true,
            volumeGestureEnabled = prefs[Keys.GESTURE_VOLUME] ?: true,
            seekGestureEnabled = prefs[Keys.GESTURE_SEEK] ?: true,
            pinchZoomEnabled = prefs[Keys.GESTURE_PINCH] ?: true,
            doubleTapSkipSeconds = prefs[Keys.DOUBLE_TAP_SECONDS] ?: 10,
            gestureSensitivity = prefs[Keys.GESTURE_SENSITIVITY] ?: 1.0f,
            subtitleTextScale = prefs[Keys.SUB_SCALE] ?: 1.0f,
            subtitleTextColorArgb = prefs[Keys.SUB_COLOR] ?: 0xFFFFFFFF.toInt(),
            subtitleBgOpacity = prefs[Keys.SUB_BG_OPACITY] ?: 0.55f,
            subtitleEdgeStyle = prefs[Keys.SUB_EDGE]?.let { runCatching { SubtitleEdgeStyle.valueOf(it) }.getOrNull() }
                ?: SubtitleEdgeStyle.OUTLINE,
            subtitleBottomPaddingFraction = prefs[Keys.SUB_BOTTOM_PADDING] ?: 0.08f,
            subtitleSyncOffsetMs = prefs[Keys.SUB_SYNC_OFFSET] ?: 0L,
            eqEnabled = prefs[Keys.EQ_ENABLED] ?: false,
            eqPreset = prefs[Keys.EQ_PRESET]?.let { runCatching { EqPreset.valueOf(it) }.getOrNull() }
                ?: EqPreset.FLAT,
            eqBandLevelsMillibels = parsedBands,
            audioBoostPercent = prefs[Keys.AUDIO_BOOST] ?: 0,

            seekPrecisionMode = prefs[Keys.SEEK_PRECISION]?.let { runCatching { SeekPrecisionMode.valueOf(it) }.getOrNull() }
                ?: SeekPrecisionMode.CLOSEST_SYNC,
            minBufferSeconds = minBuf,
            maxBufferSeconds = maxBuf,
            backBufferSeconds = (prefs[Keys.BACK_BUFFER_SEC] ?: 30).coerceIn(0, 120),
            forceIdentityEncoding = prefs[Keys.FORCE_IDENTITY_ENCODING] ?: true,
            enableCbrSeeking = prefs[Keys.ENABLE_CBR_SEEKING] ?: true,
            networkTimeoutSeconds = (prefs[Keys.NETWORK_TIMEOUT_SEC] ?: 25).coerceIn(10, 60),
            userAgentProfile = prefs[Keys.USER_AGENT_PROFILE]?.let { runCatching { UserAgentProfile.valueOf(it) }.getOrNull() }
                ?: UserAgentProfile.CHROME_MOBILE,
            customUserAgent = prefs[Keys.CUSTOM_USER_AGENT] ?: "",

            decoderMode = prefs[Keys.DECODER_MODE]?.let { runCatching { DecoderMode.valueOf(it) }.getOrNull() }
                ?: DecoderMode.AUTO_HW_FALLBACK,
            hdrWindowEnabled = prefs[Keys.HDR_WINDOW_ENABLED] ?: true,
            enableTunneling = prefs[Keys.ENABLE_TUNNELING] ?: false,
            maxStreamHeightPx = prefs[Keys.MAX_STREAM_HEIGHT_PX] ?: 0,

            preferredAudioLanguage = prefs[Keys.PREFERRED_AUDIO_LANG] ?: "",
            preferredSubtitleLanguage = prefs[Keys.PREFERRED_SUB_LANG] ?: "",
            autoLoadSiblingSubtitles = prefs[Keys.AUTO_LOAD_SIBLING_SUBS] ?: true,
            skipSilenceEnabled = prefs[Keys.SKIP_SILENCE] ?: false,
            pauseOnHeadphonesUnplugged = prefs[Keys.PAUSE_ON_NOISY] ?: true,

            hudAutoHideSeconds = (prefs[Keys.HUD_AUTO_HIDE_SEC] ?: 4).coerceIn(2, 10),
            showHudClockAndBattery = prefs[Keys.SHOW_HUD_CLOCK_BATTERY] ?: true,
            showHudNetworkStats = prefs[Keys.SHOW_HUD_NET_STATS] ?: false,
            doubleTapZoneWide = prefs[Keys.DOUBLE_TAP_ZONE_WIDE] ?: true,
            streamCacheSizeMb = prefs[Keys.STREAM_CACHE_MB] ?: 256
        )
    }

    suspend fun updateThemeMode(mode: AppThemeMode) {
        context.lumaDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun updateDefaultAspectRatio(mode: AspectRatioMode) {
        context.lumaDataStore.edit { it[Keys.DEFAULT_ASPECT] = mode.name }
    }

    suspend fun updateDefaultOrientation(mode: PlayerOrientationMode) {
        context.lumaDataStore.edit { it[Keys.DEFAULT_ORIENTATION] = mode.name }
    }

    suspend fun updateVideoSort(option: VideoSortOption) {
        context.lumaDataStore.edit { it[Keys.VIDEO_SORT] = option.name }
    }

    suspend fun updatePlaybackFlags(
        autoResume: Boolean? = null,
        backgroundPlay: Boolean? = null,
        autoPip: Boolean? = null,
        preferHw: Boolean? = null,
        matchFps: Boolean? = null
    ) {
        context.lumaDataStore.edit { prefs ->
            autoResume?.let { prefs[Keys.AUTO_RESUME] = it }
            backgroundPlay?.let { prefs[Keys.BACKGROUND_PLAY] = it }
            autoPip?.let { prefs[Keys.AUTO_PIP] = it }
            preferHw?.let { prefs[Keys.PREFER_HW] = it }
            matchFps?.let { prefs[Keys.MATCH_FPS] = it }
        }
    }

    suspend fun updateGestureSettings(
        brightness: Boolean? = null,
        volume: Boolean? = null,
        seek: Boolean? = null,
        pinchZoom: Boolean? = null,
        doubleTapSeconds: Int? = null,
        sensitivity: Float? = null
    ) {
        context.lumaDataStore.edit { prefs ->
            brightness?.let { prefs[Keys.GESTURE_BRIGHTNESS] = it }
            volume?.let { prefs[Keys.GESTURE_VOLUME] = it }
            seek?.let { prefs[Keys.GESTURE_SEEK] = it }
            pinchZoom?.let { prefs[Keys.GESTURE_PINCH] = it }
            doubleTapSeconds?.let { prefs[Keys.DOUBLE_TAP_SECONDS] = it }
            sensitivity?.let { prefs[Keys.GESTURE_SENSITIVITY] = it.coerceIn(0.4f, 2.0f) }
        }
    }

    suspend fun updateSubtitleSettings(
        scale: Float? = null,
        colorArgb: Int? = null,
        bgOpacity: Float? = null,
        edgeStyle: SubtitleEdgeStyle? = null,
        bottomPadding: Float? = null,
        syncOffsetMs: Long? = null
    ) {
        context.lumaDataStore.edit { prefs ->
            scale?.let { prefs[Keys.SUB_SCALE] = it.coerceIn(0.5f, 2.5f) }
            colorArgb?.let { prefs[Keys.SUB_COLOR] = it }
            bgOpacity?.let { prefs[Keys.SUB_BG_OPACITY] = it.coerceIn(0f, 1f) }
            edgeStyle?.let { prefs[Keys.SUB_EDGE] = it.name }
            bottomPadding?.let { prefs[Keys.SUB_BOTTOM_PADDING] = it.coerceIn(0.02f, 0.35f) }
            syncOffsetMs?.let { prefs[Keys.SUB_SYNC_OFFSET] = it.coerceIn(-10_000L, 10_000L) }
        }
    }

    suspend fun updateAudioFx(
        eqEnabled: Boolean? = null,
        preset: EqPreset? = null,
        bands: List<Int>? = null,
        boostPercent: Int? = null
    ) {
        context.lumaDataStore.edit { prefs ->
            eqEnabled?.let { prefs[Keys.EQ_ENABLED] = it }
            preset?.let { prefs[Keys.EQ_PRESET] = it.name }
            bands?.let { prefs[Keys.EQ_BANDS] = it.joinToString(",") }
            boostPercent?.let { prefs[Keys.AUDIO_BOOST] = it.coerceIn(0, 200) }
        }
    }

    suspend fun updateAdvancedNetworkSettings(
        seekPrecisionMode: SeekPrecisionMode? = null,
        minBufferSeconds: Int? = null,
        maxBufferSeconds: Int? = null,
        backBufferSeconds: Int? = null,
        forceIdentityEncoding: Boolean? = null,
        enableCbrSeeking: Boolean? = null,
        networkTimeoutSeconds: Int? = null,
        userAgentProfile: UserAgentProfile? = null,
        customUserAgent: String? = null
    ) {
        context.lumaDataStore.edit { prefs ->
            seekPrecisionMode?.let { prefs[Keys.SEEK_PRECISION] = it.name }
            minBufferSeconds?.let { prefs[Keys.MIN_BUFFER_SEC] = it.coerceIn(5, 60) }
            maxBufferSeconds?.let { prefs[Keys.MAX_BUFFER_SEC] = it.coerceIn(15, 180) }
            backBufferSeconds?.let { prefs[Keys.BACK_BUFFER_SEC] = it.coerceIn(0, 120) }
            forceIdentityEncoding?.let { prefs[Keys.FORCE_IDENTITY_ENCODING] = it }
            enableCbrSeeking?.let { prefs[Keys.ENABLE_CBR_SEEKING] = it }
            networkTimeoutSeconds?.let { prefs[Keys.NETWORK_TIMEOUT_SEC] = it.coerceIn(10, 60) }
            userAgentProfile?.let { prefs[Keys.USER_AGENT_PROFILE] = it.name }
            customUserAgent?.let { prefs[Keys.CUSTOM_USER_AGENT] = it }
        }
    }

    suspend fun updateAdvancedDecoderSettings(
        decoderMode: DecoderMode? = null,
        hdrWindowEnabled: Boolean? = null,
        enableTunneling: Boolean? = null,
        maxStreamHeightPx: Int? = null
    ) {
        context.lumaDataStore.edit { prefs ->
            decoderMode?.let {
                prefs[Keys.DECODER_MODE] = it.name
                prefs[Keys.PREFER_HW] = (it != DecoderMode.PREFER_SOFTWARE)
            }
            hdrWindowEnabled?.let { prefs[Keys.HDR_WINDOW_ENABLED] = it }
            enableTunneling?.let { prefs[Keys.ENABLE_TUNNELING] = it }
            maxStreamHeightPx?.let { prefs[Keys.MAX_STREAM_HEIGHT_PX] = it.coerceAtLeast(0) }
        }
    }

    suspend fun updateAdvancedLanguageSettings(
        preferredAudioLanguage: String? = null,
        preferredSubtitleLanguage: String? = null,
        autoLoadSiblingSubtitles: Boolean? = null,
        skipSilenceEnabled: Boolean? = null,
        pauseOnHeadphonesUnplugged: Boolean? = null
    ) {
        context.lumaDataStore.edit { prefs ->
            preferredAudioLanguage?.let { prefs[Keys.PREFERRED_AUDIO_LANG] = it }
            preferredSubtitleLanguage?.let { prefs[Keys.PREFERRED_SUB_LANG] = it }
            autoLoadSiblingSubtitles?.let { prefs[Keys.AUTO_LOAD_SIBLING_SUBS] = it }
            skipSilenceEnabled?.let { prefs[Keys.SKIP_SILENCE] = it }
            pauseOnHeadphonesUnplugged?.let { prefs[Keys.PAUSE_ON_NOISY] = it }
        }
    }

    suspend fun updateAdvancedSkinAndCacheSettings(
        hudAutoHideSeconds: Int? = null,
        showHudClockAndBattery: Boolean? = null,
        showHudNetworkStats: Boolean? = null,
        doubleTapZoneWide: Boolean? = null,
        streamCacheSizeMb: Int? = null
    ) {
        context.lumaDataStore.edit { prefs ->
            hudAutoHideSeconds?.let { prefs[Keys.HUD_AUTO_HIDE_SEC] = it.coerceIn(2, 10) }
            showHudClockAndBattery?.let { prefs[Keys.SHOW_HUD_CLOCK_BATTERY] = it }
            showHudNetworkStats?.let { prefs[Keys.SHOW_HUD_NET_STATS] = it }
            doubleTapZoneWide?.let { prefs[Keys.DOUBLE_TAP_ZONE_WIDE] = it }
            streamCacheSizeMb?.let { prefs[Keys.STREAM_CACHE_MB] = it.coerceIn(0, 1024) }
        }
    }

    suspend fun resetAllToDefaults() {
        context.lumaDataStore.edit { it.clear() }
    }
}
