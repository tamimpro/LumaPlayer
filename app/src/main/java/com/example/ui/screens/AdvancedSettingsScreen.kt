package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NetworkCheck
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DecoderMode
import com.example.data.model.FormatUtils
import com.example.data.model.LUMA_LANGUAGE_CHOICES
import com.example.data.model.SeekPrecisionMode
import com.example.data.model.UserAgentProfile
import com.example.data.preferences.UserSettings
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.LumaMonoStyle
import com.example.ui.theme.LumaMotion
import com.example.ui.viewmodel.MainViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdvancedSettingsScreen(
    settings: UserSettings,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val streamCacheBytes by viewModel.streamCacheBytes.collectAsStateWithLifecycle()
    val thumbnailCacheBytes by viewModel.thumbnailCacheBytes.collectAsStateWithLifecycle()

    var networkExpanded by remember { mutableStateOf(true) }
    var decoderExpanded by remember { mutableStateOf(true) }
    var languageExpanded by remember { mutableStateOf(false) }
    var skinCacheExpanded by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var customUaDraft by remember(settings.customUserAgent) { mutableStateOf(settings.customUserAgent) }

    LaunchedEffect(Unit) {
        viewModel.refreshCacheSizes()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Advanced Settings")
                    Text(
                        "Engine, Network Seek, Decoders & Language Rules",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("advanced_settings_back_button")
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
            // 1. Network Buffer & Seek Engine Controls
            item(key = "adv_network") {
                ExpandableAdvancedSection(
                    title = "Network Buffer & Seek Engine",
                    subtitle = "${settings.seekPrecisionMode.label} · Back-buffer ${settings.backBufferSeconds}s · Timeout ${settings.networkTimeoutSeconds}s",
                    icon = Icons.Rounded.NetworkCheck,
                    expanded = networkExpanded,
                    onToggle = { networkExpanded = !networkExpanded },
                    headerTag = "adv_section_network"
                ) {
                    Text("Seek Precision Mode", style = MaterialTheme.typography.labelLarge)
                    Text(
                        settings.seekPrecisionMode.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SeekPrecisionMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.seekPrecisionMode == mode,
                                onClick = { viewModel.updateAdvancedNetworkSettings(seekPrecisionMode = mode) },
                                label = { Text(mode.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    AdvancedSliderRow(
                        label = "Min Buffer Watermark",
                        valueLabel = "${settings.minBufferSeconds}s",
                        value = settings.minBufferSeconds.toFloat(),
                        range = 5f..60f,
                        steps = 10,
                        onValueChange = {
                            val nextMin = it.roundToInt()
                            val adjustedMax = maxOf(settings.maxBufferSeconds, nextMin + 10)
                            viewModel.updateAdvancedNetworkSettings(
                                minBufferSeconds = nextMin,
                                maxBufferSeconds = adjustedMax
                            )
                        }
                    )

                    AdvancedSliderRow(
                        label = "Max Buffer Cap",
                        valueLabel = "${settings.maxBufferSeconds}s",
                        value = settings.maxBufferSeconds.toFloat(),
                        range = 15f..180f,
                        steps = 10,
                        onValueChange = {
                            val nextMax = it.roundToInt().coerceAtLeast(settings.minBufferSeconds + 5)
                            viewModel.updateAdvancedNetworkSettings(maxBufferSeconds = nextMax)
                        }
                    )

                    AdvancedSliderRow(
                        label = "Instant Back-Buffer Retention",
                        valueLabel = if (settings.backBufferSeconds == 0) "Off" else "${settings.backBufferSeconds}s",
                        value = settings.backBufferSeconds.toFloat(),
                        range = 0f..120f,
                        steps = 7,
                        onValueChange = {
                            viewModel.updateAdvancedNetworkSettings(backBufferSeconds = it.roundToInt())
                        }
                    )

                    AdvancedSliderRow(
                        label = "HTTP Connect & Read Timeout",
                        valueLabel = "${settings.networkTimeoutSeconds}s",
                        value = settings.networkTimeoutSeconds.toFloat(),
                        range = 10f..60f,
                        steps = 9,
                        onValueChange = {
                            viewModel.updateAdvancedNetworkSettings(networkTimeoutSeconds = it.roundToInt())
                        }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    AdvancedToggleRow(
                        title = "Force Uncompressed HTTP Range (Identity)",
                        subtitle = "Sends Accept-Encoding: identity so servers preserve byte-range seeking and never restart skips at 00:00",
                        checked = settings.forceIdentityEncoding,
                        onCheckedChange = { viewModel.updateAdvancedNetworkSettings(forceIdentityEncoding = it) }
                    )

                    AdvancedToggleRow(
                        title = "Constant-Bitrate (CBR) Seek Indexing",
                        subtitle = "Enables seeking on progressive HTTP MP4/MKV/TS streams that lack a leading seek index",
                        checked = settings.enableCbrSeeking,
                        onCheckedChange = { viewModel.updateAdvancedNetworkSettings(enableCbrSeeking = it) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("HTTP User-Agent Profile", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        UserAgentProfile.entries.forEach { profile ->
                            FilterChip(
                                selected = settings.userAgentProfile == profile,
                                onClick = { viewModel.updateAdvancedNetworkSettings(userAgentProfile = profile) },
                                label = { Text(profile.label) }
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = settings.userAgentProfile == UserAgentProfile.CUSTOM,
                        enter = LumaMotion.accordionEnter(),
                        exit = LumaMotion.accordionExit()
                    ) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            OutlinedTextField(
                                value = customUaDraft,
                                onValueChange = {
                                    customUaDraft = it
                                    viewModel.updateAdvancedNetworkSettings(customUserAgent = it)
                                },
                                label = { Text("Custom User-Agent String") },
                                placeholder = { Text("Mozilla/5.0 ...") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("adv_custom_ua_input")
                            )
                        }
                    }
                }
            }

            // 2. Hardware/Software Decoder & HDR Controls
            item(key = "adv_decoder") {
                ExpandableAdvancedSection(
                    title = "Decoder & HDR Pipeline",
                    subtitle = "${settings.decoderMode.label} · Quality ${if (settings.maxStreamHeightPx == 0) "Max" else "${settings.maxStreamHeightPx}p"}",
                    icon = Icons.Rounded.Memory,
                    expanded = decoderExpanded,
                    onToggle = { decoderExpanded = !decoderExpanded },
                    headerTag = "adv_section_decoder"
                ) {
                    Text("Video Decoder Strategy", style = MaterialTheme.typography.labelLarge)
                    Text(
                        settings.decoderMode.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DecoderMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.decoderMode == mode,
                                onClick = { viewModel.updateAdvancedDecoderSettings(decoderMode = mode) },
                                label = { Text(mode.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    AdvancedToggleRow(
                        title = "HDR Window Color Mode",
                        subtitle = "Requests native HDR10/Dolby Vision display output on supported OLED panels",
                        checked = settings.hdrWindowEnabled,
                        onCheckedChange = { viewModel.updateAdvancedDecoderSettings(hdrWindowEnabled = it) }
                    )

                    AdvancedToggleRow(
                        title = "Hardware Tunneled Playback",
                        subtitle = "Direct hardware A/V sync for 4K HDR streams (enable if supported by your SoC)",
                        checked = settings.enableTunneling,
                        onCheckedChange = { viewModel.updateAdvancedDecoderSettings(enableTunneling = it) }
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Online Adaptive Stream Quality Cap", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    val qualityOptions = listOf(
                        0 to "Unlimited (4K/8K)",
                        2160 to "2160p (4K)",
                        1440 to "1440p",
                        1080 to "1080p",
                        720 to "720p",
                        480 to "480p (Data Saver)"
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        qualityOptions.forEach { (heightPx, label) ->
                            FilterChip(
                                selected = settings.maxStreamHeightPx == heightPx,
                                onClick = { viewModel.updateAdvancedDecoderSettings(maxStreamHeightPx = heightPx) },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }

            // 3. Default Audio & Subtitle Language Rules
            item(key = "adv_language") {
                val audioLabel = LUMA_LANGUAGE_CHOICES.firstOrNull { it.code == settings.preferredAudioLanguage }?.label ?: "Auto / Original"
                val subLabel = LUMA_LANGUAGE_CHOICES.firstOrNull { it.code == settings.preferredSubtitleLanguage }?.label ?: "Auto / Original"
                ExpandableAdvancedSection(
                    title = "Audio & Subtitle Language Rules",
                    subtitle = "Audio: $audioLabel · Subtitles: $subLabel",
                    icon = Icons.Rounded.Language,
                    expanded = languageExpanded,
                    onToggle = { languageExpanded = !languageExpanded },
                    headerTag = "adv_section_language"
                ) {
                    Text("Preferred Default Audio Language", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Automatically picks your preferred language on multi-audio movies (online & local).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LUMA_LANGUAGE_CHOICES.forEach { choice ->
                            FilterChip(
                                selected = settings.preferredAudioLanguage == choice.code,
                                onClick = { viewModel.updateAdvancedLanguageSettings(preferredAudioLanguage = choice.code) },
                                label = { Text(choice.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Preferred Default Subtitle Language", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LUMA_LANGUAGE_CHOICES.forEach { choice ->
                            FilterChip(
                                selected = settings.preferredSubtitleLanguage == choice.code,
                                onClick = { viewModel.updateAdvancedLanguageSettings(preferredSubtitleLanguage = choice.code) },
                                label = { Text(choice.label) }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    AdvancedToggleRow(
                        title = "Auto-Load Sibling Subtitle Files",
                        subtitle = "Automatically attaches matching .srt / .vtt / .ass files next to local videos",
                        checked = settings.autoLoadSiblingSubtitles,
                        onCheckedChange = { viewModel.updateAdvancedLanguageSettings(autoLoadSiblingSubtitles = it) }
                    )

                    AdvancedToggleRow(
                        title = "Skip Audio Silence",
                        subtitle = "Automatically compresses silent gaps during lectures, podcasts, and dialogue",
                        checked = settings.skipSilenceEnabled,
                        onCheckedChange = { viewModel.updateAdvancedLanguageSettings(skipSilenceEnabled = it) }
                    )

                    AdvancedToggleRow(
                        title = "Pause on Headphones Disconnected",
                        subtitle = "Instantly pauses playback if wired or Bluetooth audio disconnects",
                        checked = settings.pauseOnHeadphonesUnplugged,
                        onCheckedChange = { viewModel.updateAdvancedLanguageSettings(pauseOnHeadphonesUnplugged = it) }
                    )
                }
            }

            // 4. Player Skin, Auto-Hide & Cache Controls
            item(key = "adv_skin_cache") {
                ExpandableAdvancedSection(
                    title = "Player Skin, Auto-Hide & Cache",
                    subtitle = "HUD ${settings.hudAutoHideSeconds}s · Stream Cache ${if (settings.streamCacheSizeMb == 0) "Off" else "${settings.streamCacheSizeMb} MB"}",
                    icon = Icons.Rounded.Tune,
                    expanded = skinCacheExpanded,
                    onToggle = { skinCacheExpanded = !skinCacheExpanded },
                    headerTag = "adv_section_skin_cache"
                ) {
                    AdvancedSliderRow(
                        label = "HUD Controls Auto-Hide Delay",
                        valueLabel = "${settings.hudAutoHideSeconds}s",
                        value = settings.hudAutoHideSeconds.toFloat(),
                        range = 2f..10f,
                        steps = 7,
                        onValueChange = {
                            viewModel.updateAdvancedSkinAndCacheSettings(hudAutoHideSeconds = it.roundToInt())
                        }
                    )

                    AdvancedToggleRow(
                        title = "Show Clock & Battery on Player HUD",
                        subtitle = "Displays current time and battery percentage subtly inside the player top bar",
                        checked = settings.showHudClockAndBattery,
                        onCheckedChange = { viewModel.updateAdvancedSkinAndCacheSettings(showHudClockAndBattery = it) }
                    )

                    AdvancedToggleRow(
                        title = "Show Live Bitrate & Buffer % Tag",
                        subtitle = "Displays real-time video bitrate and buffer health inside the player header",
                        checked = settings.showHudNetworkStats,
                        onCheckedChange = { viewModel.updateAdvancedSkinAndCacheSettings(showHudNetworkStats = it) }
                    )

                    AdvancedToggleRow(
                        title = "Wide Double-Tap Skip Zones",
                        subtitle = "Expands left/right double-tap skip areas to 44% of screen width (vs 35% narrow edges)",
                        checked = settings.doubleTapZoneWide,
                        onCheckedChange = { viewModel.updateAdvancedSkinAndCacheSettings(doubleTapZoneWide = it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    Text("Online Stream Disk Cache Cap", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Caches streamed chunks on-device so backward skips and repeat seeks load instantly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    val cacheSizes = listOf(0 to "Off", 128 to "128 MB", 256 to "256 MB", 512 to "512 MB", 1024 to "1 GB")
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        cacheSizes.forEach { (mb, label) ->
                            FilterChip(
                                selected = settings.streamCacheSizeMb == mb,
                                onClick = { viewModel.updateAdvancedSkinAndCacheSettings(streamCacheSizeMb = mb) },
                                label = { Text(label, style = LumaMonoStyle) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.clearStreamCache() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("adv_clear_stream_cache")
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stream (${FormatUtils.formatFileSize(streamCacheBytes)})", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { viewModel.clearThumbnailCache() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("adv_clear_thumb_cache")
                        ) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Thumbs (${FormatUtils.formatFileSize(thumbnailCacheBytes)})", maxLines = 1)
                        }
                    }
                }
            }

            // Reset All Settings Action Card
            item(key = "adv_reset") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Reset All Settings", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Restores all playback, buffer, gesture, and theme preferences to v2.0 defaults.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { showResetConfirm = true },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ErrorRed),
                            border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.6f)),
                            modifier = Modifier.testTag("adv_reset_all_button")
                        ) {
                            Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Reset")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            icon = { Icon(Icons.Rounded.RestartAlt, contentDescription = null, tint = ErrorRed) },
            title = { Text("Reset All Settings?") },
            text = {
                Text("This resets theme, buffer tuning, decoder mode, language preferences, and gesture settings back to Luma Player v2.0 defaults. Your playlists and stream bookmarks remain safe.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAllSettingsToDefaults()
                        showResetConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                    modifier = Modifier.testTag("adv_confirm_reset_button")
                ) {
                    Text("Reset Defaults")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ExpandableAdvancedSection(
    title: String,
    subtitle: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    headerTag: String,
    content: @Composable () -> Unit
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(durationMillis = LumaMotion.FAST_MS),
        label = "chevronRotation"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = LumaMotion.STANDARD_MS))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Surface(
                onClick = onToggle,
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(headerTag)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = subtitle,
                            style = LumaMonoStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.rotate(chevronRotation)
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded,
                enter = LumaMotion.accordionEnter(),
                exit = LumaMotion.accordionExit()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun AdvancedToggleRow(
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
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun AdvancedSliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(
                valueLabel,
                style = LumaMonoStyle.copy(color = MaterialTheme.colorScheme.primary)
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps
        )
    }
}
