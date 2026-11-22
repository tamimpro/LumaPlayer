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
import com.example.data.model.EqPreset
import com.example.data.model.PlayerOrientationMode
import com.example.data.model.SubtitleEdgeStyle
import com.example.data.model.VideoSortOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.lumaDataStore: DataStore<Preferences> by preferencesDataStore(name = "luma_player_prefs")

data class UserSettings(
    val themeMode: AppThemeMode = AppThemeMode.AMOLED_DARK,
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
    val audioBoostPercent: Int = 0
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
    }

    val settingsFlow: Flow<UserSettings> = context.lumaDataStore.data.map { prefs ->
        val bandsRaw = prefs[Keys.EQ_BANDS]
        val parsedBands = bandsRaw?.split(",")?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.size == 5 } ?: listOf(0, 0, 0, 0, 0)

        UserSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                ?: AppThemeMode.AMOLED_DARK,
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
            audioBoostPercent = prefs[Keys.AUDIO_BOOST] ?: 0
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
}
