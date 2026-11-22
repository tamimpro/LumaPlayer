package com.example.ui.screens

import android.app.Activity
import android.os.Build
import android.view.Display
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.AppThemeMode
import com.example.data.model.AspectRatioMode
import com.example.data.model.PlayerOrientationMode
import com.example.data.model.SubtitleEdgeStyle
import com.example.data.preferences.UserSettings
import com.example.data.repository.StorageAccessState
import com.example.ui.theme.BoostAmber
import com.example.ui.theme.HwDecodeGreen
import com.example.ui.theme.LumaMonoStyle
import com.example.ui.viewmodel.MainViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    storageAccess: StorageAccessState,
    viewModel: MainViewModel,
    onRequestAllFilesAccess: () -> Unit,
    onRequestVideoAccess: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val displayReport = remember(context) { inspectDisplaySummary(context as? Activity) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings & Gestures") },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("settings_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // File Access & Storage Control Card
            item {
                SettingsSectionCard(title = "File & Storage Access") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (storageAccess.hasAllFilesAccess) {
                                Icons.Rounded.CheckCircle
                            } else {
                                Icons.Rounded.AdminPanelSettings
                            },
                            contentDescription = null,
                            tint = if (storageAccess.hasAllFilesAccess) HwDecodeGreen else BoostAmber,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = when {
                                storageAccess.hasAllFilesAccess -> "All Files Access Active (Full Move, Rename & Delete)"
                                storageAccess.hasVideoReadPermission -> "Video Read Access Only (Upgrade for Move/Rename)"
                                else -> "File Access Not Granted"
                            },
                            style = LumaMonoStyle.copy(
                                color = if (storageAccess.hasAllFilesAccess) HwDecodeGreen else BoostAmber
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Luma Player automatically scans all local video folders and sibling subtitles with zero manual mounting. All Files Access enables instant moving, renaming, and deleting directly from the app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!storageAccess.hasAllFilesAccess) {
                            Button(
                                onClick = onRequestAllFilesAccess,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("settings_grant_all_files")
                            ) {
                                Text("Grant All Files Access")
                            }
                        } else {
                            OutlinedButton(
                                onClick = onRequestAllFilesAccess,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Manage Access")
                            }
                        }
                        if (!storageAccess.canScanDevice) {
                            OutlinedButton(
                                onClick = onRequestVideoAccess,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Videos Only")
                            }
                        }
                    }
                    if (storageAccess.canScanDevice) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = { viewModel.scanDeviceLibrary(silent = false) }) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Re-scan Device Storage Now")
                        }
                    }
                }
            }

            // Hardware & Display Capabilities Card
            item {
                SettingsSectionCard(title = "Display & Hardware Engine") {
                    Text(
                        text = displayReport,
                        style = LumaMonoStyle.copy(color = HwDecodeGreen)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Luma Player renders via direct HDR SurfaceView with hardware MediaCodec acceleration and automatic fallback, preserving 100% original video quality.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Appearance
            item {
                SettingsSectionCard(title = "Appearance & Theme") {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { viewModel.updateThemeMode(mode) },
                                label = { Text(mode.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("theme_chip_${mode.name}")
                            )
                        }
                    }
                }
            }

            // Playback & 19.5:9 Defaults
            item {
                SettingsSectionCard(title = "Playback & 19.5:9 Display") {
                    Text("Default Aspect Ratio", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AspectRatioMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.defaultAspectRatio == mode,
                                onClick = { viewModel.updateDefaultAspectRatio(mode) },
                                label = { Text(mode.shortLabel) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Default Orientation", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PlayerOrientationMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.defaultOrientation == mode,
                                onClick = { viewModel.updateDefaultOrientation(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsToggleRow(
                        title = "Automatic Resume",
                        subtitle = "Resume videos and streams from your exact last timestamp",
                        checked = settings.autoResume,
                        onCheckedChange = { viewModel.updatePlaybackFlags(autoResume = it) }
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                    ) {
                        Text("Background Audio Playback", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "OFF by default. Tap the Background button on the video screen whenever you want audio to keep playing for that session.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    SettingsToggleRow(
                        title = "Auto Picture-in-Picture",
                        subtitle = "Seamlessly enter PiP window when swiping home while playing",
                        checked = settings.autoEnterPip,
                        onCheckedChange = { viewModel.updatePlaybackFlags(autoPip = it) }
                    )
                    SettingsToggleRow(
                        title = "Match 60/120Hz Display Refresh Rate",
                        subtitle = "Seamless frame-rate switching for zero-judder playback",
                        checked = settings.matchDisplayFrameRate,
                        onCheckedChange = { viewModel.updatePlaybackFlags(matchFps = it) }
                    )
                }
            }

            // MX Player-Style Gesture Customizer
            item {
                SettingsSectionCard(title = "MX-Style Gesture Controls") {
                    SettingsToggleRow(
                        title = "Left-Side Brightness Swipe",
                        subtitle = "Slide vertically on left half to adjust screen brightness",
                        checked = settings.brightnessGestureEnabled,
                        onCheckedChange = { viewModel.updateGestureSettings(brightness = it) }
                    )
                    SettingsToggleRow(
                        title = "Right-Side Volume Swipe",
                        subtitle = "Slide vertically on right half to adjust media volume",
                        checked = settings.volumeGestureEnabled,
                        onCheckedChange = { viewModel.updateGestureSettings(volume = it) }
                    )
                    SettingsToggleRow(
                        title = "Horizontal Swipe Seeking",
                        subtitle = "Slide left or right anywhere to scrub with delta preview",
                        checked = settings.seekGestureEnabled,
                        onCheckedChange = { viewModel.updateGestureSettings(seek = it) }
                    )
                    SettingsToggleRow(
                        title = "Pinch-to-Zoom (50%–400%)",
                        subtitle = "Smooth two-finger zoom on 19.5:9 displays",
                        checked = settings.pinchZoomEnabled,
                        onCheckedChange = { viewModel.updateGestureSettings(pinchZoom = it) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Double-Tap Skip Duration", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 15, 30).forEach { secs ->
                            FilterChip(
                                selected = settings.doubleTapSkipSeconds == secs,
                                onClick = { viewModel.updateGestureSettings(doubleTapSeconds = secs) },
                                label = { Text("${secs}s", style = LumaMonoStyle) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Gesture Sensitivity", style = MaterialTheme.typography.labelLarge)
                        Text(
                            String.format(Locale.US, "%.1fx", settings.gestureSensitivity),
                            style = LumaMonoStyle.copy(color = MaterialTheme.colorScheme.primary)
                        )
                    }
                    Slider(
                        value = settings.gestureSensitivity,
                        onValueChange = { viewModel.updateGestureSettings(sensitivity = it) },
                        valueRange = 0.5f..2.0f
                    )
                }
            }

            // Subtitle Defaults
            item {
                SettingsSectionCard(title = "Subtitle Defaults") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Default Text Size", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "${(settings.subtitleTextScale * 100).toInt()}%",
                            style = LumaMonoStyle.copy(color = MaterialTheme.colorScheme.primary)
                        )
                    }
                    Slider(
                        value = settings.subtitleTextScale,
                        onValueChange = { viewModel.updateSubtitleDefaults(scale = it) },
                        valueRange = 0.6f..2.2f
                    )

                    Text("Edge Outline Style", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SubtitleEdgeStyle.entries.forEach { style ->
                            FilterChip(
                                selected = settings.subtitleEdgeStyle == style,
                                onClick = { viewModel.updateSubtitleDefaults(edgeStyle = style) },
                                label = { Text(style.label) }
                            )
                        }
                    }
                }
            }

            // Privacy Guarantee
            item {
                SettingsSectionCard(title = "Privacy & Independence") {
                    Text(
                        "100% private on-device SQLite library & preferences. Zero ads, zero analytics, zero cloud tracking, and zero artificial video-quality restrictions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun inspectDisplaySummary(activity: Activity?): String {
    if (activity == null) return "SurfaceView HDR Pipeline Active"
    val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.display
    } else {
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay
    }
    val hz = display?.refreshRate?.toInt() ?: 60
    val hdrTypes = mutableListOf<String>()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        display?.hdrCapabilities?.supportedHdrTypes?.forEach { type ->
            when (type) {
                Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> hdrTypes.add("Dolby Vision")
                Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> hdrTypes.add("HDR10+")
                Display.HdrCapabilities.HDR_TYPE_HDR10 -> hdrTypes.add("HDR10")
                Display.HdrCapabilities.HDR_TYPE_HLG -> hdrTypes.add("HLG")
            }
        }
    }
    val hdrText = hdrTypes.distinct().takeIf { it.isNotEmpty() }?.joinToString(" · ") ?: "SDR Panel"
    return "Panel: ${hz}Hz · HDR: $hdrText"
}
