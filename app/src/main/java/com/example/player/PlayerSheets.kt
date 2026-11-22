package com.example.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NightlightRound
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AspectRatioMode
import com.example.data.model.EqPreset
import com.example.data.model.FormatUtils
import com.example.data.model.PlayerOrientationMode
import com.example.data.model.SubtitleEdgeStyle
import com.example.data.model.VideoDiagnostics
import com.example.data.preferences.UserSettings
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.BoostAmber
import com.example.ui.theme.ChampagneGold
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.HwDecodeGreen
import com.example.ui.theme.LumaMonoStyle
import com.example.ui.theme.TimelineIceCyan
import com.example.ui.theme.WarmBorder
import com.example.ui.theme.WarmIvory
import com.example.ui.theme.WarmObsidianContainer
import com.example.ui.theme.WarmObsidianHigh
import com.example.ui.theme.WarmObsidianLow
import com.example.ui.theme.WarmStoneSecondary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerActiveModalSheet(
    uiState: PlayerUiState,
    settings: UserSettings,
    audioFxState: AudioFxState,
    diagnostics: VideoDiagnostics,
    backgroundPlayActive: Boolean,
    viewModel: PlayerViewModel,
    onEnterBackgroundAudio: () -> Unit,
    onPickExternalSubtitle: () -> Unit
) {
    if (uiState.activeSheet == ActivePlayerSheet.NONE) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { viewModel.closeSheet() },
        sheetState = sheetState,
        containerColor = WarmObsidianLow.copy(alpha = 0.98f),
        contentColor = WarmIvory,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            when (uiState.activeSheet) {
                ActivePlayerSheet.AUDIO_TRACKS -> AudioTrackSelectorSheet(
                    uiState = uiState,
                    viewModel = viewModel
                )
                ActivePlayerSheet.TRACKS -> TracksAndSubtitlesSheet(
                    uiState = uiState,
                    settings = settings,
                    viewModel = viewModel,
                    onPickExternalSubtitle = onPickExternalSubtitle
                )
                ActivePlayerSheet.AUDIO_FX -> AudioFxSheet(
                    audioFxState = audioFxState,
                    viewModel = viewModel
                )
                ActivePlayerSheet.SPEED -> PlaybackSpeedSheet(
                    currentSpeed = uiState.playbackSpeed,
                    onSelectSpeed = { viewModel.setPlaybackSpeed(it) },
                    onClose = { viewModel.closeSheet() }
                )
                ActivePlayerSheet.TOOLS -> PlayerToolsSheet(
                    uiState = uiState,
                    backgroundPlayActive = backgroundPlayActive,
                    onEnterBackgroundAudio = onEnterBackgroundAudio,
                    viewModel = viewModel
                )
                ActivePlayerSheet.DIAGNOSTICS -> VideoDiagnosticsSheet(
                    diagnostics = diagnostics,
                    onClose = { viewModel.closeSheet() }
                )
                ActivePlayerSheet.NONE -> Unit
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SheetHeader(title: String, subtitle: String? = null, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = WarmIvory)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = WarmStoneSecondary)
            }
        }
        IconButton(onClick = onClose, modifier = Modifier.testTag("sheet_close_button")) {
            Icon(Icons.Rounded.Close, contentDescription = "Close sheet", tint = WarmStoneSecondary)
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun AudioTrackSelectorSheet(
    uiState: PlayerUiState,
    viewModel: PlayerViewModel
) {
    val countLabel = if (uiState.audioTracks.size > 1) {
        "${uiState.audioTracks.size} language tracks available · Switch instantly"
    } else {
        "Multi-language audio selector for online & local videos"
    }

    SheetHeader(
        title = "Audio Language",
        subtitle = countLabel,
        onClose = { viewModel.closeSheet() }
    )

    if (uiState.audioTracks.isEmpty()) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = WarmObsidianContainer,
            border = BorderStroke(1.dp, WarmBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Audiotrack, contentDescription = null, tint = CinemaAmber)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Default audio stream is active. If this stream contains multiple languages, they will appear here once detected.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WarmStoneSecondary
                )
            }
        }
    } else {
        uiState.audioTracks.forEach { track ->
            val detail = buildString {
                append(track.codec)
                append(" · ")
                append(FormatUtils.channelLayoutLabel(track.channels))
                if (track.sampleRateHz > 0) append(" · ${track.sampleRateHz / 1000} kHz")
                if (track.bitrateBps > 0) append(" · ${FormatUtils.formatBitrate(track.bitrateBps)}")
            }
            TrackSelectionRow(
                title = track.label,
                subtitle = detail,
                selected = track.isSelected && !uiState.audioDisabled,
                badge = track.language,
                onClick = {
                    viewModel.selectAudioTrack(track)
                    viewModel.closeSheet()
                },
                modifier = Modifier.testTag("audio_track_option_${track.id}")
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
    TrackSelectionRow(
        title = "Disable / Mute Audio",
        subtitle = "Turn off audio track output for this video",
        selected = uiState.audioDisabled,
        badge = "MUTE",
        onClick = { viewModel.selectAudioTrack(null) },
        modifier = Modifier.testTag("audio_track_mute_option")
    )

    Spacer(modifier = Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { viewModel.openSheet(ActivePlayerSheet.AUDIO_FX) },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Rounded.Equalizer, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Equalizer & Boost")
        }
        OutlinedButton(
            onClick = { viewModel.openSheet(ActivePlayerSheet.TRACKS) },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Rounded.VolumeOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Subtitles & Quality")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TracksAndSubtitlesSheet(
    uiState: PlayerUiState,
    settings: UserSettings,
    viewModel: PlayerViewModel,
    onPickExternalSubtitle: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Video Quality", "Audio (${uiState.audioTracks.size})", "Subtitles")

    SheetHeader(
        title = "Tracks & Subtitles",
        subtitle = "100% original quality · Multi-language audio & synced captions",
        onClose = { viewModel.closeSheet() }
    )

    TabRow(
        selectedTabIndex = selectedTab,
        containerColor = WarmObsidianContainer,
        contentColor = CinemaAmber,
        modifier = Modifier.clip(RoundedCornerShape(12.dp))
    ) {
        tabs.forEachIndexed { index, label ->
            Tab(
                selected = selectedTab == index,
                onClick = { selectedTab = index },
                text = { Text(label, style = MaterialTheme.typography.labelLarge) }
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    when (selectedTab) {
        0 -> {
            TrackSelectionRow(
                title = "Auto Maximum Quality (HDR Priority)",
                subtitle = "Starts at highest resolution, bitrate & HDR profile supported by hardware",
                selected = uiState.isAutoMaxQuality,
                badge = "MAX",
                onClick = { viewModel.selectAutoMaxQuality() }
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (uiState.videoTracks.isEmpty()) {
                Text(
                    "Playing native source stream without downscaling.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WarmStoneSecondary,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                uiState.videoTracks.forEach { track ->
                    TrackSelectionRow(
                        title = track.label,
                        subtitle = "${track.width}×${track.height} · ${track.codec}",
                        selected = !uiState.isAutoMaxQuality && track.isSelected,
                        badge = track.hdrLabel ?: FormatUtils.resolutionBadge(track.width, track.height),
                        onClick = { viewModel.selectVideoTrack(track) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
        1 -> {
            if (uiState.audioTracks.isEmpty()) {
                Text(
                    "Default audio track active.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WarmStoneSecondary
                )
            } else {
                uiState.audioTracks.forEach { track ->
                    val detail = buildString {
                        append(track.codec)
                        append(" · ")
                        append(FormatUtils.channelLayoutLabel(track.channels))
                        if (track.sampleRateHz > 0) append(" · ${track.sampleRateHz / 1000} kHz")
                        if (track.bitrateBps > 0) append(" · ${FormatUtils.formatBitrate(track.bitrateBps)}")
                    }
                    TrackSelectionRow(
                        title = track.label,
                        subtitle = detail,
                        selected = track.isSelected && !uiState.audioDisabled,
                        badge = track.language,
                        onClick = { viewModel.selectAudioTrack(track) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            TrackSelectionRow(
                title = "Disable / Mute Audio",
                subtitle = "Silence audio output",
                selected = uiState.audioDisabled,
                badge = "MUTE",
                onClick = { viewModel.selectAudioTrack(null) }
            )
        }
        2 -> {
            Button(
                onClick = onPickExternalSubtitle,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CinemaAmber,
                    contentColor = AmoledBlack
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pick_external_subtitle_button")
            ) {
                Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Load External Subtitle (.srt, .vtt, .ass)")
            }

            Spacer(modifier = Modifier.height(12.dp))

            TrackSelectionRow(
                title = "Subtitles Off",
                subtitle = "Disable all captions",
                selected = uiState.subtitlesDisabled,
                badge = "OFF",
                onClick = { viewModel.selectSubtitleTrack(null) }
            )
            Spacer(modifier = Modifier.height(8.dp))
            uiState.subtitleTracks.forEach { track ->
                TrackSelectionRow(
                    title = track.label,
                    subtitle = track.mimeType,
                    selected = track.isSelected,
                    badge = track.language,
                    onClick = { viewModel.selectSubtitleTrack(track) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            HorizontalDivider(color = WarmBorder, modifier = Modifier.padding(vertical = 12.dp))

            // Subtitle Sync Offset (-10.0s to +10.0s in 100ms steps)
            Text("Subtitle Sync Offset", style = MaterialTheme.typography.titleMedium, color = WarmIvory)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedButton(
                    onClick = { viewModel.updateSubtitleStyle(syncOffsetMs = settings.subtitleSyncOffsetMs - 100L) },
                    modifier = Modifier.testTag("sub_sync_minus")
                ) {
                    Icon(Icons.Rounded.Remove, contentDescription = "Advance subtitles 100ms")
                    Text("0.1s")
                }
                Text(
                    text = String.format(Locale.US, "%+.1fs", settings.subtitleSyncOffsetMs / 1000f),
                    style = LumaMonoStyle.copy(color = CinemaAmber)
                )
                OutlinedButton(
                    onClick = { viewModel.updateSubtitleStyle(syncOffsetMs = settings.subtitleSyncOffsetMs + 100L) },
                    modifier = Modifier.testTag("sub_sync_plus")
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Delay subtitles 100ms")
                    Text("0.1s")
                }
                OutlinedButton(
                    onClick = { viewModel.updateSubtitleStyle(syncOffsetMs = 0L) }
                ) {
                    Text("Reset")
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "Text Scale (${(settings.subtitleTextScale * 100).toInt()}%)",
                style = MaterialTheme.typography.labelLarge,
                color = WarmIvory
            )
            Slider(
                value = settings.subtitleTextScale,
                onValueChange = { viewModel.updateSubtitleStyle(scale = it) },
                valueRange = 0.6f..2.2f,
                colors = SliderDefaults.colors(thumbColor = CinemaAmber, activeTrackColor = CinemaAmber)
            )

            Text(
                "Background Opacity (${(settings.subtitleBgOpacity * 100).toInt()}%)",
                style = MaterialTheme.typography.labelLarge,
                color = WarmIvory
            )
            Slider(
                value = settings.subtitleBgOpacity,
                onValueChange = { viewModel.updateSubtitleStyle(bgOpacity = it) },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(thumbColor = CinemaAmber, activeTrackColor = CinemaAmber)
            )

            Text("Text Color", style = MaterialTheme.typography.labelLarge, color = WarmIvory)
            Spacer(modifier = Modifier.height(6.dp))
            val colorSwatches = listOf(
                0xFFFFFFFF.toInt() to "White",
                0xFFFFEB3B.toInt() to "Cinema Yellow",
                0xFF38E8FF.toInt() to "Ice Cyan",
                0xFF4ADE80.toInt() to "Mint"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                colorSwatches.forEach { (argb, name) ->
                    val selected = settings.subtitleTextColorArgb == argb
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(argb))
                            .clickable { viewModel.updateSubtitleStyle(colorArgb = argb) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Rounded.CheckCircle,
                                contentDescription = name,
                                tint = Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Edge Style", style = MaterialTheme.typography.labelLarge, color = WarmIvory)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubtitleEdgeStyle.entries.forEach { style ->
                    FilterChip(
                        selected = settings.subtitleEdgeStyle == style,
                        onClick = { viewModel.updateSubtitleStyle(edgeStyle = style) },
                        label = { Text(style.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CinemaAmber.copy(alpha = 0.22f),
                            selectedLabelColor = CinemaAmber
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackSelectionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    badge: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) CinemaAmber.copy(alpha = 0.14f) else WarmObsidianContainer,
        border = BorderStroke(1.dp, if (selected) CinemaAmber else WarmBorder),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) CinemaAmber else WarmIvory
                )
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = WarmStoneSecondary)
            }
            if (badge.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (selected) CinemaAmber.copy(alpha = 0.2f) else WarmObsidianHigh
                ) {
                    Text(
                        badge,
                        style = LumaMonoStyle.copy(color = if (selected) CinemaAmber else WarmIvory),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AudioFxSheet(
    audioFxState: AudioFxState,
    viewModel: PlayerViewModel
) {
    SheetHeader(
        title = "Audio Equalizer & Boost",
        subtitle = "Hardware 5-band Equalizer + Loudness Enhancer",
        onClose = { viewModel.closeSheet() }
    )

    // Audio Boost (0% to +200%)
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = WarmObsidianContainer,
        border = BorderStroke(1.dp, if (audioFxState.boostPercent > 0) CinemaAmber.copy(alpha = 0.55f) else WarmBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.VolumeUp,
                        contentDescription = null,
                        tint = CinemaAmber
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Audio Volume Boost", style = MaterialTheme.typography.titleMedium, color = WarmIvory)
                }
                Text(
                    text = if (audioFxState.boostPercent == 0) "Off" else "+${audioFxState.boostPercent}%",
                    style = LumaMonoStyle.copy(
                        color = if (audioFxState.boostPercent > 0) CinemaAmber else WarmStoneSecondary
                    )
                )
            }
            Slider(
                value = audioFxState.boostPercent.toFloat(),
                onValueChange = { viewModel.setAudioBoost(it.toInt()) },
                valueRange = 0f..200f,
                colors = SliderDefaults.colors(
                    thumbColor = CinemaAmber,
                    activeTrackColor = CinemaAmber
                ),
                modifier = Modifier.testTag("audio_boost_slider")
            )
            Text(
                "Amplifies quiet dialogue up to +15 dB via hardware LoudnessEnhancer.",
                style = MaterialTheme.typography.bodySmall,
                color = WarmStoneSecondary
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Equalizer, contentDescription = null, tint = CinemaAmber)
            Spacer(modifier = Modifier.width(8.dp))
            Text("5-Band Equalizer", style = MaterialTheme.typography.titleMedium, color = WarmIvory)
        }
        Switch(
            checked = audioFxState.eqEnabled,
            onCheckedChange = { viewModel.setEqEnabled(it) },
            modifier = Modifier.testTag("eq_toggle_switch")
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        EqPreset.entries.forEach { preset ->
            FilterChip(
                selected = audioFxState.preset == preset && audioFxState.eqEnabled,
                onClick = { viewModel.selectEqPreset(preset) },
                label = { Text(preset.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CinemaAmber.copy(alpha = 0.22f),
                    selectedLabelColor = CinemaAmber
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    val minDb = audioFxState.minLevelMillibels.toFloat()
    val maxDb = audioFxState.maxLevelMillibels.toFloat()
    audioFxState.bandLevelsMillibels.forEachIndexed { idx, levelMb ->
        val freqHz = audioFxState.bandFrequenciesHz.getOrElse(idx) { 1000 }
        val freqLabel = if (freqHz >= 1000) String.format(Locale.US, "%.1fkHz", freqHz / 1000f) else "${freqHz}Hz"
        val dbText = String.format(Locale.US, "%+.1f dB", levelMb / 100f)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                freqLabel,
                style = LumaMonoStyle.copy(color = WarmStoneSecondary),
                modifier = Modifier.width(56.dp)
            )
            Slider(
                value = levelMb.toFloat().coerceIn(minDb, maxDb),
                onValueChange = { viewModel.setEqBandLevel(idx, it.toInt()) },
                valueRange = minDb..maxDb,
                enabled = audioFxState.eqEnabled,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = CinemaAmber,
                    activeTrackColor = ChampagneGold
                )
            )
            Text(
                dbText,
                style = LumaMonoStyle.copy(color = WarmIvory),
                modifier = Modifier.width(62.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaybackSpeedSheet(
    currentSpeed: Float,
    onSelectSpeed: (Float) -> Unit,
    onClose: () -> Unit
) {
    SheetHeader(
        title = "Playback Speed",
        subtitle = "Precision pitch-corrected range from 0.25x to 4.0x",
        onClose = onClose
    )

    Text(
        text = String.format(Locale.US, "%.2fx", currentSpeed),
        style = MaterialTheme.typography.displaySmall.copy(color = CinemaAmber),
        modifier = Modifier.padding(bottom = 8.dp)
    )

    Slider(
        value = currentSpeed,
        onValueChange = { onSelectSpeed((it * 20).toInt() / 20f) },
        valueRange = 0.25f..4.0f,
        colors = SliderDefaults.colors(thumbColor = CinemaAmber, activeTrackColor = CinemaAmber),
        modifier = Modifier.testTag("playback_speed_slider")
    )

    Spacer(modifier = Modifier.height(10.dp))

    val presets = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { preset ->
            val selected = kotlin.math.abs(currentSpeed - preset) < 0.03f
            FilterChip(
                selected = selected,
                onClick = { onSelectSpeed(preset) },
                label = { Text("${preset}x", style = LumaMonoStyle) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CinemaAmber.copy(alpha = 0.22f),
                    selectedLabelColor = CinemaAmber
                )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerToolsSheet(
    uiState: PlayerUiState,
    backgroundPlayActive: Boolean,
    onEnterBackgroundAudio: () -> Unit,
    viewModel: PlayerViewModel
) {
    SheetHeader(
        title = "Playback Tools",
        subtitle = "Background Audio, A-B Repeat, Sleep Timer & Aspect Ratio",
        onClose = { viewModel.closeSheet() }
    )

    // Session-Scoped Opt-In Background Audio Card
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = WarmObsidianContainer,
        border = BorderStroke(1.dp, if (backgroundPlayActive) CinemaAmber.copy(alpha = 0.55f) else WarmBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Headphones, contentDescription = null, tint = CinemaAmber)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Background Audio (This Session)", style = MaterialTheme.typography.titleMedium, color = WarmIvory)
                        Text(
                            "OFF by default · Resets when video ends or changes",
                            style = MaterialTheme.typography.bodySmall,
                            color = WarmStoneSecondary
                        )
                    }
                }
                Switch(
                    checked = backgroundPlayActive,
                    onCheckedChange = { viewModel.toggleBackgroundPlayForSession() },
                    modifier = Modifier.testTag("background_play_switch")
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onEnterBackgroundAudio,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CinemaAmber,
                    contentColor = AmoledBlack
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("tools_enter_background_button")
            ) {
                Icon(Icons.Rounded.Headphones, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play in Background Now")
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // A-B Repeat
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = WarmObsidianContainer,
        border = BorderStroke(1.dp, WarmBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Repeat, contentDescription = null, tint = CinemaAmber)
                Spacer(modifier = Modifier.width(8.dp))
                Text("A-B Loop Repeat", style = MaterialTheme.typography.titleMedium, color = WarmIvory)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.setAbPointA() },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ab_set_a_button")
                ) {
                    Text(uiState.abPointAMs?.let { "A: ${FormatUtils.formatDuration(it)}" } ?: "Set Point A")
                }
                OutlinedButton(
                    onClick = { viewModel.setAbPointB() },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ab_set_b_button")
                ) {
                    Text(uiState.abPointBMs?.let { "B: ${FormatUtils.formatDuration(it)}" } ?: "Set Point B")
                }
                if (uiState.abPointAMs != null || uiState.abPointBMs != null) {
                    OutlinedButton(onClick = { viewModel.clearAbRepeat() }) {
                        Text("Clear")
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Sleep Timer
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = WarmObsidianContainer,
        border = BorderStroke(1.dp, WarmBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.NightlightRound, contentDescription = null, tint = CinemaAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sleep Timer", style = MaterialTheme.typography.titleMedium, color = WarmIvory)
                }
                val activeLabel = when {
                    uiState.sleepTimerRemainingMs != null -> FormatUtils.formatDuration(uiState.sleepTimerRemainingMs)
                    uiState.sleepAtEndOfVideo -> "End of video"
                    else -> "Off"
                }
                Text(activeLabel, style = LumaMonoStyle.copy(color = CinemaAmber))
            }
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(15, 30, 45, 60).forEach { mins ->
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.setSleepTimerMinutes(mins) },
                        label = { Text("${mins}m") }
                    )
                }
                FilterChip(
                    selected = uiState.sleepAtEndOfVideo,
                    onClick = { viewModel.setSleepAtEndOfVideo() },
                    label = { Text("End of Video") }
                )
                if (uiState.sleepTimerRemainingMs != null || uiState.sleepAtEndOfVideo) {
                    FilterChip(
                        selected = false,
                        onClick = { viewModel.cancelSleepTimer() },
                        label = { Text("Cancel") }
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Aspect Ratio Selector
    Text("Aspect Ratio (19.5:9 Optimized)", style = MaterialTheme.typography.titleSmall, color = WarmIvory)
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AspectRatioMode.entries.forEach { mode ->
            FilterChip(
                selected = uiState.aspectRatioMode == mode,
                onClick = { viewModel.setAspectRatioMode(mode) },
                label = { Text(mode.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CinemaAmber.copy(alpha = 0.22f),
                    selectedLabelColor = CinemaAmber
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Orientation Control
    Text("Screen Orientation", style = MaterialTheme.typography.titleSmall, color = WarmIvory)
    Spacer(modifier = Modifier.height(8.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PlayerOrientationMode.entries.forEach { mode ->
            FilterChip(
                selected = uiState.orientationMode == mode,
                onClick = { viewModel.setOrientationMode(mode) },
                label = { Text(mode.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ChampagneGold.copy(alpha = 0.25f),
                    selectedLabelColor = WarmIvory
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // Quick Action Links to EQ & Diagnostics
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { viewModel.openSheet(ActivePlayerSheet.AUDIO_FX) },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Rounded.Equalizer, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Equalizer & Boost")
        }
        OutlinedButton(
            onClick = { viewModel.openSheet(ActivePlayerSheet.DIAGNOSTICS) },
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Video Stats")
        }
    }
}

@Composable
private fun VideoDiagnosticsSheet(
    diagnostics: VideoDiagnostics,
    onClose: () -> Unit
) {
    SheetHeader(
        title = "Signal & Hardware Diagnostics",
        subtitle = "Bit-accurate Media3 + SurfaceView HDR pipeline telemetry",
        onClose = onClose
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = WarmObsidianContainer,
        border = BorderStroke(1.dp, WarmBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DiagRow("Resolution", if (diagnostics.width > 0) "${diagnostics.width} × ${diagnostics.height} (${diagnostics.resolutionBadge})" else "Probing…")
            DiagRow("Frame Rate", if (diagnostics.frameRate > 0f) String.format(Locale.US, "%.2f fps (Display %.0f Hz)", diagnostics.frameRate, diagnostics.displayRefreshRateHz) else "Display ${diagnostics.displayRefreshRateHz.toInt()} Hz")
            DiagRow("HDR Pipeline", "${diagnostics.hdrFormat} · Direct SurfaceView", accent = if (diagnostics.hdrFormat != "SDR") TimelineIceCyan else WarmIvory)
            DiagRow("Color Space", diagnostics.colorSpace)
            DiagRow(
                "Decoder Engine",
                diagnostics.videoDecoderName,
                accent = if (diagnostics.isHardwareAccelerated) HwDecodeGreen else BoostAmber
            )
            DiagRow("Acceleration", if (diagnostics.isHardwareAccelerated) "Hardware MediaCodec (HW)" else "Software Fallback (SW)", accent = if (diagnostics.isHardwareAccelerated) HwDecodeGreen else BoostAmber)
            DiagRow("Video Codec & Bitrate", "${diagnostics.videoCodec} · ${FormatUtils.formatBitrate(diagnostics.videoBitrateBps)}")
            DiagRow("Audio Stream", "${diagnostics.audioCodec} · ${FormatUtils.channelLayoutLabel(diagnostics.audioChannels)} · ${if (diagnostics.audioSampleRateHz > 0) "${diagnostics.audioSampleRateHz / 1000} kHz" else "—"}")
            DiagRow("Dropped Frames", "${diagnostics.droppedFrames}", accent = if (diagnostics.droppedFrames == 0) HwDecodeGreen else BoostAmber)
            DiagRow("Buffer Health", "${diagnostics.bufferedPercent}%")
            DiagRow(
                "Display HDR Support",
                diagnostics.displayHdrCapabilities.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "SDR Panel"
            )
        }
    }
}

@Composable
private fun DiagRow(label: String, value: String, accent: Color = WarmIvory) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = WarmStoneSecondary,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            value,
            style = LumaMonoStyle.copy(color = accent, fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(0.58f)
        )
    }
}
