package com.example.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.SurfaceView
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import com.example.data.model.AspectRatioMode
import com.example.data.model.FormatUtils
import com.example.data.model.SubtitleEdgeStyle
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.BoostAmber
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.HdrGold
import com.example.ui.theme.HwDecodeGreen
import com.example.ui.theme.LumaMonoStyle
import com.example.ui.theme.TimelineBufferIce
import com.example.ui.theme.TimelineIceCyan
import com.example.ui.theme.TimelineRailShadow
import com.example.ui.theme.TimelineTrackBg
import com.example.ui.theme.WarmBorder
import com.example.ui.theme.WarmIvory
import com.example.ui.theme.WarmObsidianContainer
import com.example.ui.theme.WarmObsidianHigh
import com.example.ui.theme.WarmObsidianLow
import com.example.ui.theme.WarmStoneSecondary
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class GestureKind { NONE, BRIGHTNESS, VOLUME, SEEK, ZOOM }

private data class SubtitleStyleKey(
    val scale: Float,
    val colorArgb: Int,
    val bgOpacity: Float,
    val edgeStyle: SubtitleEdgeStyle,
    val bottomPadding: Float
)

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    isInPipMode: Boolean,
    onNavigateBack: () -> Unit,
    onEnterPip: () -> Unit,
    onEnterBackgroundAudio: () -> Unit,
    onPickExternalSubtitle: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val coroutineScope = rememberCoroutineScope()

    val player by viewModel.playerFlow.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val audioFxState by viewModel.audioFxState.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()
    val backgroundPlayActive by viewModel.backgroundPlayActive.collectAsStateWithLifecycle()
    val playbackError by viewModel.playbackError.collectAsStateWithLifecycle()

    var controlsVisible by remember { mutableStateOf(true) }
    var lockButtonVisible by remember { mutableStateOf(false) }

    // Live gesture HUD overlays
    var activeGesture by remember { mutableStateOf(GestureKind.NONE) }
    var brightnessLevel by remember { mutableFloatStateOf(readCurrentBrightness(activity)) }
    var volumeLevel by remember { mutableFloatStateOf(readCurrentVolumeFraction(audioManager)) }
    var scrubPreviewMs by remember { mutableLongStateOf(0L) }
    var scrubDeltaMs by remember { mutableLongStateOf(0L) }
    var doubleTapFeedback by remember { mutableStateOf<Pair<Boolean, Int>?>(null) } // isForward to cumulative seconds
    var doubleTapSeq by remember { mutableIntStateOf(0) }
    var isSliderDragging by remember { mutableStateOf(false) }
    var sliderDragPositionMs by remember { mutableLongStateOf(0L) }

    // Auto-hide HUD while playing
    LaunchedEffect(controlsVisible, uiState.isPlaying, uiState.activeSheet, isSliderDragging, activeGesture) {
        if (controlsVisible && uiState.isPlaying && uiState.activeSheet == ActivePlayerSheet.NONE && !isSliderDragging && activeGesture == GestureKind.NONE) {
            delay(3_500L)
            controlsVisible = false
        }
    }

    LaunchedEffect(lockButtonVisible) {
        if (lockButtonVisible) {
            delay(2_800L)
            lockButtonVisible = false
        }
    }

    LaunchedEffect(doubleTapSeq) {
        if (doubleTapFeedback != null) {
            delay(800L)
            doubleTapFeedback = null
        }
    }

    val latestSettings by rememberUpdatedState(settings)
    val latestUiState by rememberUpdatedState(uiState)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .clipToBounds()
    ) {
        // 1. True HDR SurfaceView with 19.5:9 Aspect Ratio & Continuous Pinch-to-Zoom Container
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val containerW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val containerH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val containerRatio = containerW / containerH
            val sourceRatio = if (uiState.videoWidth > 0 && uiState.videoHeight > 0) {
                uiState.videoWidth.toFloat() / uiState.videoHeight.toFloat()
            } else {
                16f / 9f
            }

            val (targetW, targetH) = computeVideoViewportSize(
                mode = uiState.aspectRatioMode,
                containerW = containerW,
                containerH = containerH,
                containerRatio = containerRatio,
                sourceRatio = sourceRatio,
                videoW = uiState.videoWidth,
                videoH = uiState.videoHeight
            )

            val density = LocalDensity.current
            val widthDp = with(density) { targetW.toDp() }
            val heightDp = with(density) { targetH.toDp() }

            var surfaceViewRef by remember { mutableStateOf<SurfaceView?>(null) }

            DisposableEffect(player, surfaceViewRef) {
                val sv = surfaceViewRef
                if (player != null && sv != null) {
                    player?.setVideoSurfaceView(sv)
                }
                onDispose {
                    if (player != null && sv != null) {
                        player?.clearVideoSurfaceView(sv)
                    }
                }
            }

            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        keepScreenOn = true
                        surfaceViewRef = this
                        player?.setVideoSurfaceView(this)
                    }
                },
                update = { sv ->
                    if (surfaceViewRef !== sv) {
                        surfaceViewRef = sv
                        player?.setVideoSurfaceView(sv)
                    }
                },
                modifier = Modifier
                    .size(width = widthDp, height = heightDp)
                    .graphicsLayer {
                        scaleX = uiState.zoomScale
                        scaleY = uiState.zoomScale
                    }
            )
        }

        // 2. Optimized Native SubtitleView — diffs style & cues to avoid redundant View invalidations
        val styleKey = remember(
            settings.subtitleTextScale,
            settings.subtitleTextColorArgb,
            settings.subtitleBgOpacity,
            settings.subtitleEdgeStyle,
            settings.subtitleBottomPaddingFraction
        ) {
            SubtitleStyleKey(
                scale = settings.subtitleTextScale,
                colorArgb = settings.subtitleTextColorArgb,
                bgOpacity = settings.subtitleBgOpacity,
                edgeStyle = settings.subtitleEdgeStyle,
                bottomPadding = settings.subtitleBottomPaddingFraction
            )
        }
        var lastAppliedStyle by remember { mutableStateOf<SubtitleStyleKey?>(null) }
        var lastAppliedCues by remember { mutableStateOf<List<Cue>?>(null) }

        AndroidView(
            factory = { ctx ->
                SubtitleView(ctx).apply {
                    SubtitleSyncController.applyStyle(this, settings)
                    lastAppliedStyle = styleKey
                }
            },
            update = { view ->
                if (lastAppliedStyle != styleKey) {
                    SubtitleSyncController.applyStyle(view, settings)
                    lastAppliedStyle = styleKey
                }
                if (lastAppliedCues != uiState.activeCues) {
                    view.setCues(uiState.activeCues)
                    lastAppliedCues = uiState.activeCues
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        )

        // 3. Unified Online & Offline Gesture Layer (Zero-Flicker Double-Tap + Chained Skip + Brightness/Volume/Seek/Zoom)
        if (!isInPipMode) {
            var lastTapTime by remember { mutableLongStateOf(0L) }
            var lastTapX by remember { mutableFloatStateOf(0f) }
            var chainedSideIsRight by remember { mutableStateOf<Boolean?>(null) }
            var chainedTotalSeconds by remember { mutableIntStateOf(0) }
            var chainedUntilMs by remember { mutableLongStateOf(0L) }
            var pendingSingleTapJob by remember { mutableStateOf<Job?>(null) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            // requireUnconsumed = true isolates HUD button & timeline touches cleanly
                            val firstDown = awaitFirstDown(requireUnconsumed = true)
                            val startX = firstDown.position.x
                            val viewWidth = size.width.toFloat().coerceAtLeast(1f)
                            val viewHeight = size.height.toFloat().coerceAtLeast(1f)
                            val touchSlop = viewConfiguration.touchSlop

                            var mode = GestureKind.NONE
                            var moved = false
                            var totalDx = 0f
                            var totalDy = 0f
                            val startBrightness = readCurrentBrightness(activity)
                            val startVolume = readCurrentVolumeFraction(audioManager)
                            val startPosition = latestUiState.positionMs
                            var currentZoom = latestUiState.zoomScale

                            do {
                                val event = awaitPointerEvent()
                                val pressedCount = event.changes.count { it.pressed }

                                if (latestUiState.isLocked) {
                                    event.changes.forEach { it.consume() }
                                    continue
                                }

                                // Two-finger pinch-to-zoom (works identically on online & offline videos)
                                if (pressedCount >= 2 && latestSettings.pinchZoomEnabled) {
                                    val zoomDelta = event.calculateZoom()
                                    val centroidSize = event.calculateCentroidSize(useCurrent = true)
                                    if (centroidSize > 0f && abs(zoomDelta - 1f) > 0.005f) {
                                        mode = GestureKind.ZOOM
                                        moved = true
                                        pendingSingleTapJob?.cancel()
                                        currentZoom = (currentZoom * zoomDelta).coerceIn(0.5f, 4.0f)
                                        viewModel.updateZoomScale(currentZoom)
                                        activeGesture = GestureKind.ZOOM
                                        event.changes.forEach { it.consume() }
                                    }
                                    continue
                                }

                                val change = event.changes.firstOrNull() ?: continue
                                if (!change.pressed) continue

                                val dx = change.position.x - change.previousPosition.x
                                val dy = change.position.y - change.previousPosition.y
                                totalDx += dx
                                totalDy += dy

                                if (mode == GestureKind.NONE && (abs(totalDx) > touchSlop || abs(totalDy) > touchSlop)) {
                                    moved = true
                                    pendingSingleTapJob?.cancel()
                                    mode = if (abs(totalDx) > abs(totalDy) * 1.15f) {
                                        if (latestSettings.seekGestureEnabled) GestureKind.SEEK else GestureKind.NONE
                                    } else {
                                        val isLeftSide = startX < viewWidth * 0.5f
                                        when {
                                            isLeftSide && latestSettings.brightnessGestureEnabled -> GestureKind.BRIGHTNESS
                                            !isLeftSide && latestSettings.volumeGestureEnabled -> GestureKind.VOLUME
                                            else -> GestureKind.NONE
                                        }
                                    }
                                    activeGesture = mode
                                }

                                val sens = latestSettings.gestureSensitivity
                                when (mode) {
                                    GestureKind.BRIGHTNESS -> {
                                        val delta = (-totalDy / (viewHeight * 0.65f)) * sens
                                        val nextB = (startBrightness + delta).coerceIn(0.01f, 1.0f)
                                        brightnessLevel = nextB
                                        applyWindowBrightness(activity, nextB)
                                        change.consume()
                                    }
                                    GestureKind.VOLUME -> {
                                        val delta = (-totalDy / (viewHeight * 0.65f)) * sens
                                        val nextV = (startVolume + delta).coerceIn(0f, 1f)
                                        volumeLevel = nextV
                                        applyMediaVolume(audioManager, nextV)
                                        change.consume()
                                    }
                                    GestureKind.SEEK -> {
                                        val maxSpanMs = minOf(latestUiState.durationMs.coerceAtLeast(60_000L), 180_000L)
                                        val deltaMs = ((totalDx / viewWidth) * maxSpanMs * sens).toLong()
                                        scrubDeltaMs = deltaMs
                                        val dur = latestUiState.durationMs.coerceAtLeast(0L)
                                        scrubPreviewMs = (startPosition + deltaMs).coerceIn(0L, if (dur > 0L) dur else Long.MAX_VALUE)
                                        change.consume()
                                    }
                                    else -> Unit
                                }
                            } while (event.changes.any { it.pressed })

                            if (mode == GestureKind.SEEK) {
                                viewModel.seekTo(scrubPreviewMs)
                            }
                            activeGesture = GestureKind.NONE

                            if (!moved) {
                                if (latestUiState.isLocked) {
                                    lockButtonVisible = !lockButtonVisible
                                } else {
                                    val now = System.currentTimeMillis()
                                    val isRightSide = startX >= viewWidth * 0.5f
                                    val inSkipZone = startX <= viewWidth * 0.44f || startX >= viewWidth * 0.56f
                                    val isContinuingChain = inSkipZone &&
                                        chainedSideIsRight == isRightSide &&
                                        now <= chainedUntilMs
                                    val isDoubleTap = inSkipZone &&
                                        (now - lastTapTime) < 280L &&
                                        abs(startX - lastTapX) < viewWidth * 0.35f

                                    if (isContinuingChain || isDoubleTap) {
                                        pendingSingleTapJob?.cancel()
                                        val skipSecs = latestSettings.doubleTapSkipSeconds
                                        val nextTotal = if (isContinuingChain) chainedTotalSeconds + skipSecs else skipSecs
                                        chainedSideIsRight = isRightSide
                                        chainedTotalSeconds = nextTotal
                                        chainedUntilMs = now + 700L
                                        lastTapTime = now
                                        lastTapX = startX
                                        viewModel.seekBy(if (isRightSide) skipSecs * 1000L else -skipSecs * 1000L)
                                        doubleTapFeedback = isRightSide to nextTotal
                                        doubleTapSeq += 1
                                    } else {
                                        lastTapTime = now
                                        lastTapX = startX
                                        chainedSideIsRight = null
                                        chainedTotalSeconds = 0
                                        chainedUntilMs = 0L
                                        // Slight delay prevents HUD flicker when user is starting a double-tap skip
                                        pendingSingleTapJob?.cancel()
                                        pendingSingleTapJob = coroutineScope.launch {
                                            delay(230L)
                                            controlsVisible = !controlsVisible
                                        }
                                    }
                                }
                            }
                        }
                    }
            )
        }

        // 4. Buffering Spinner (High-Contrast Ice Cyan)
        if (uiState.isBuffering && playbackError == null) {
            CircularProgressIndicator(
                color = TimelineIceCyan,
                strokeWidth = 3.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
            )
        }

        // 5. Gesture Feedback HUD Pills (Brightness / Volume / Seek / Zoom)
        AnimatedVisibility(
            visible = activeGesture != GestureKind.NONE && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = WarmObsidianLow.copy(alpha = 0.88f),
                border = BorderStroke(1.dp, WarmBorder)
            ) {
                when (activeGesture) {
                    GestureKind.BRIGHTNESS -> {
                        GestureMeterContent(
                            icon = Icons.Rounded.Brightness6,
                            label = "Brightness",
                            valueText = "${(brightnessLevel * 100).toInt()}%",
                            progress = brightnessLevel,
                            accent = CinemaAmber
                        )
                    }
                    GestureKind.VOLUME -> {
                        val boostTag = if (audioFxState.boostPercent > 0) " (+${audioFxState.boostPercent}%)" else ""
                        GestureMeterContent(
                            icon = Icons.Rounded.VolumeUp,
                            label = "Volume$boostTag",
                            valueText = "${(volumeLevel * 100).toInt()}%",
                            progress = volumeLevel,
                            accent = if (audioFxState.boostPercent > 0) BoostAmber else TimelineIceCyan
                        )
                    }
                    GestureKind.SEEK -> {
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = FormatUtils.formatDuration(scrubPreviewMs),
                                style = MaterialTheme.typography.headlineMedium.copy(color = WarmIvory)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = FormatUtils.formatSignedDelta(scrubDeltaMs),
                                style = LumaMonoStyle.copy(color = TimelineIceCyan, fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                    GestureKind.ZOOM -> {
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${(uiState.zoomScale * 100).toInt()}%",
                                style = MaterialTheme.typography.headlineMedium.copy(color = CinemaAmber)
                            )
                            Text(
                                text = "Pinch Zoom",
                                style = MaterialTheme.typography.labelMedium,
                                color = WarmStoneSecondary
                            )
                        }
                    }
                    GestureKind.NONE -> Unit
                }
            }
        }

        // 6. Chained Double-Tap Directional Skip Feedback (Ice Cyan Glow)
        doubleTapFeedback?.let { (isForward, seconds) ->
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = WarmObsidianLow.copy(alpha = 0.86f),
                border = BorderStroke(1.dp, TimelineIceCyan.copy(alpha = 0.55f)),
                modifier = Modifier
                    .align(if (isForward) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 32.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isForward) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                        contentDescription = null,
                        tint = TimelineIceCyan,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${if (isForward) "+" else "-"}${seconds}s",
                        style = LumaMonoStyle.copy(
                            color = WarmIvory,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            }
        }

        // 7. Transient Status Toast Pill
        AnimatedVisibility(
            visible = uiState.statusToast != null && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 72.dp)
        ) {
            uiState.statusToast?.let { toast ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = WarmObsidianContainer.copy(alpha = 0.92f),
                    border = BorderStroke(1.dp, CinemaAmber.copy(alpha = 0.45f))
                ) {
                    Text(
                        text = toast,
                        style = MaterialTheme.typography.labelLarge,
                        color = CinemaAmber,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // 8. Screen Lock Floating Unlock Button
        AnimatedVisibility(
            visible = uiState.isLocked && lockButtonVisible && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(start = 24.dp)
        ) {
            Surface(
                onClick = { viewModel.toggleScreenLock() },
                shape = RoundedCornerShape(16.dp),
                color = WarmObsidianLow.copy(alpha = 0.9f),
                border = BorderStroke(1.dp, CinemaAmber.copy(alpha = 0.5f)),
                modifier = Modifier.testTag("player_unlock_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.LockOpen, contentDescription = "Unlock screen", tint = CinemaAmber)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock", style = MaterialTheme.typography.labelLarge, color = WarmIvory)
                }
            }
        }

        // 9. Main Translucent Player HUD (Hidden in PiP & Lock Mode)
        AnimatedVisibility(
            visible = controlsVisible && !uiState.isLocked && !isInPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top Scrim & Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xE0000000), Color(0x66000000), Color.Transparent)
                            )
                        )
                        .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("player_back_button")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = WarmIvory
                        )
                    }
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 6.dp)) {
                        Text(
                            text = uiState.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = WarmIvory,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val resBadge = diagnostics.resolutionBadge
                            if (resBadge.isNotBlank()) {
                                HudMiniTag(resBadge, CinemaAmber)
                            }
                            if (diagnostics.hdrFormat != "SDR") {
                                HudMiniTag(diagnostics.hdrFormat, HdrGold)
                            }
                            if (diagnostics.frameRate >= 48f) {
                                HudMiniTag("${diagnostics.frameRate.toInt()}fps", HwDecodeGreen)
                            }
                            if (uiState.isStream && uiState.durationMs == 0L) {
                                HudMiniTag("LIVE", ErrorRed)
                            }
                            if (uiState.abPointAMs != null && uiState.abPointBMs != null) {
                                HudMiniTag("A-B", CinemaAmber)
                            }
                            if (uiState.sleepTimerRemainingMs != null) {
                                HudMiniTag("Sleep ${FormatUtils.formatDuration(uiState.sleepTimerRemainingMs!!)}", WarmStoneSecondary)
                            }
                        }
                    }

                    IconButton(
                        onClick = { viewModel.openSheet(ActivePlayerSheet.TRACKS) },
                        modifier = Modifier.testTag("player_tracks_button")
                    ) {
                        Icon(Icons.Rounded.ClosedCaption, contentDescription = "Audio, quality & subtitles", tint = WarmIvory)
                    }
                    IconButton(
                        onClick = { viewModel.openSheet(ActivePlayerSheet.AUDIO_FX) },
                        modifier = Modifier.testTag("player_eq_button")
                    ) {
                        Icon(
                            Icons.Rounded.Equalizer,
                            contentDescription = "Equalizer and boost",
                            tint = if (audioFxState.eqEnabled || audioFxState.boostPercent > 0) CinemaAmber else WarmIvory
                        )
                    }
                    IconButton(
                        onClick = onEnterPip,
                        modifier = Modifier.testTag("player_pip_button")
                    ) {
                        Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = "Picture in Picture", tint = WarmIvory)
                    }
                    IconButton(
                        onClick = { viewModel.openSheet(ActivePlayerSheet.TOOLS) },
                        modifier = Modifier.testTag("player_tools_button")
                    ) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "More playback tools", tint = WarmIvory)
                    }
                }

                // Center Playback Controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (uiState.hasPrevious) {
                        IconButton(
                            onClick = { viewModel.skipPrevious() },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0x73000000))
                        ) {
                            Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous video", tint = WarmIvory, modifier = Modifier.size(28.dp))
                        }
                    }

                    IconButton(
                        onClick = { viewModel.seekBy(-settings.doubleTapSkipSeconds * 1000L) },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0x73000000))
                            .testTag("player_rewind_button")
                    ) {
                        Icon(Icons.Rounded.Replay10, contentDescription = "Skip back", tint = WarmIvory, modifier = Modifier.size(30.dp))
                    }

                    Surface(
                        onClick = { viewModel.togglePlayPause() },
                        shape = CircleShape,
                        color = CinemaAmber,
                        modifier = Modifier
                            .size(66.dp)
                            .testTag("player_play_pause_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (uiState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                                tint = AmoledBlack,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { viewModel.seekBy(settings.doubleTapSkipSeconds * 1000L) },
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0x73000000))
                            .testTag("player_forward_button")
                    ) {
                        Icon(Icons.Rounded.Forward10, contentDescription = "Skip forward", tint = WarmIvory, modifier = Modifier.size(30.dp))
                    }

                    if (uiState.hasNext) {
                        IconButton(
                            onClick = { viewModel.skipNext() },
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(0x73000000))
                        ) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next video", tint = WarmIvory, modifier = Modifier.size(28.dp))
                        }
                    }
                }

                // Bottom Scrim, High-Contrast Ice Cyan Timeline & Quick Controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color(0x99000000), Color(0xEC000000))
                            )
                        )
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 18.dp)
                ) {
                    if (abs(uiState.zoomScale - 1.0f) > 0.05f) {
                        Surface(
                            onClick = { viewModel.resetZoom() },
                            shape = RoundedCornerShape(16.dp),
                            color = WarmObsidianContainer.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, CinemaAmber.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = "Zoom ${(uiState.zoomScale * 100).toInt()}% · Tap to Reset",
                                style = LumaMonoStyle.copy(color = CinemaAmber),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }

                    val displayPos = if (isSliderDragging) sliderDragPositionMs else uiState.positionMs

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = FormatUtils.formatDuration(displayPos),
                            style = LumaMonoStyle.copy(color = WarmIvory, fontWeight = FontWeight.SemiBold)
                        )
                        CinemaTimelineBar(
                            positionMs = displayPos,
                            bufferedPositionMs = uiState.bufferedPositionMs,
                            durationMs = uiState.durationMs,
                            abPointAMs = uiState.abPointAMs,
                            abPointBMs = uiState.abPointBMs,
                            isDragging = isSliderDragging,
                            onScrubStart = { pos ->
                                isSliderDragging = true
                                sliderDragPositionMs = pos
                            },
                            onScrubMove = { pos ->
                                isSliderDragging = true
                                sliderDragPositionMs = pos
                            },
                            onScrubCommit = { pos ->
                                isSliderDragging = false
                                viewModel.seekTo(pos)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 10.dp)
                                .testTag("player_seek_slider")
                        )
                        Text(
                            text = if (uiState.isStream && uiState.durationMs <= 0L) {
                                "LIVE"
                            } else {
                                FormatUtils.formatDuration(uiState.durationMs)
                            },
                            style = LumaMonoStyle.copy(
                                color = if (uiState.isStream && uiState.durationMs <= 0L) ErrorRed else WarmStoneSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.toggleScreenLock() },
                                modifier = Modifier.testTag("player_lock_button")
                            ) {
                                Icon(Icons.Rounded.Lock, contentDescription = "Lock screen", tint = WarmIvory)
                            }
                            HudActionPill(
                                text = String.format(java.util.Locale.US, "%.2gx", uiState.playbackSpeed),
                                highlighted = abs(uiState.playbackSpeed - 1.0f) > 0.02f,
                                onClick = { viewModel.openSheet(ActivePlayerSheet.SPEED) },
                                tag = "player_speed_pill"
                            )
                            // Dedicated One-Tap Opt-In Background Audio Button (OFF by default, resets per session)
                            Surface(
                                onClick = onEnterBackgroundAudio,
                                shape = RoundedCornerShape(12.dp),
                                color = if (backgroundPlayActive) {
                                    CinemaAmber.copy(alpha = 0.24f)
                                } else {
                                    WarmObsidianHigh.copy(alpha = 0.8f)
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (backgroundPlayActive) CinemaAmber else WarmBorder
                                ),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("player_bg_audio_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Headphones,
                                        contentDescription = "Play audio in background",
                                        tint = if (backgroundPlayActive) CinemaAmber else WarmIvory,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (backgroundPlayActive) "BG On" else "Background",
                                        style = LumaMonoStyle.copy(
                                            color = if (backgroundPlayActive) CinemaAmber else WarmIvory,
                                            fontWeight = FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HudActionPill(
                                text = uiState.aspectRatioMode.shortLabel,
                                highlighted = uiState.aspectRatioMode != AspectRatioMode.FIT,
                                onClick = { viewModel.cycleAspectRatio() },
                                tag = "player_aspect_pill"
                            )
                            IconButton(
                                onClick = { viewModel.cycleOrientation() },
                                modifier = Modifier.testTag("player_rotate_button")
                            ) {
                                Icon(Icons.Rounded.ScreenRotation, contentDescription = "Rotate orientation", tint = WarmIvory)
                            }
                        }
                    }
                }
            }
        }

        // 10. Robust Playback Error Overlay with Instant Retry
        if (playbackError != null && !isInPipMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xD9000000))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = WarmObsidianContainer,
                    border = BorderStroke(1.dp, WarmBorder),
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth(0.9f)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Unable to Play Stream", style = MaterialTheme.typography.titleLarge, color = WarmIvory)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            playbackError ?: "Unknown playback error",
                            style = MaterialTheme.typography.bodySmall,
                            color = WarmStoneSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { viewModel.retryPlayback() },
                                colors = ButtonDefaults.buttonColors(containerColor = CinemaAmber, contentColor = AmoledBlack),
                                modifier = Modifier.testTag("player_retry_button")
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry")
                            }
                            Button(
                                onClick = onNavigateBack,
                                colors = ButtonDefaults.buttonColors(containerColor = WarmObsidianHigh, contentColor = WarmIvory)
                            ) {
                                Text("Back")
                            }
                        }
                    }
                }
            }
        }

        // 11. In-Player Modal Bottom Sheets
        if (!isInPipMode) {
            PlayerActiveModalSheet(
                uiState = uiState,
                settings = settings,
                audioFxState = audioFxState,
                diagnostics = diagnostics,
                backgroundPlayActive = backgroundPlayActive,
                viewModel = viewModel,
                onEnterBackgroundAudio = onEnterBackgroundAudio,
                onPickExternalSubtitle = onPickExternalSubtitle
            )
        }
    }
}

/**
 * High-contrast Ice Cyan Cinema Timeline Scrubber:
 * - Dark translucent contrast rail backdrop so it stays clearly visible over bright white scenes
 * - Crisp unplayed track + sky-ice buffered range for online HLS/DASH/HTTP streams
 * - Vivid Ice Cyan (#38E8FF) played progress bar with glowing white-centered thumb
 * - Warm Cinema Amber A-B repeat segment and pins
 * - Tap-to-seek and smooth drag scrubbing with floating timecode preview bubble
 */
@Composable
private fun CinemaTimelineBar(
    positionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long,
    abPointAMs: Long?,
    abPointBMs: Long?,
    isDragging: Boolean,
    onScrubStart: (Long) -> Unit,
    onScrubMove: (Long) -> Unit,
    onScrubCommit: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    val seekable = durationMs > 0L
    val playedFraction = if (seekable) (positionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f) else 1f
    val bufferedFraction = if (seekable) (bufferedPositionMs.toFloat() / safeDuration.toFloat()).coerceIn(playedFraction, 1f) else 1f

    val trackHeight by animateDpAsState(targetValue = if (isDragging) 6.dp else 4.5.dp, label = "timelineTrackHeight")
    val thumbCoreRadius by animateDpAsState(targetValue = if (isDragging) 8.5.dp else 6.5.dp, label = "timelineThumbCore")
    val thumbHaloRadius by animateDpAsState(targetValue = if (isDragging) 17.dp else 12.5.dp, label = "timelineThumbHalo")

    val latestDuration by rememberUpdatedState(durationMs)
    val latestOnStart by rememberUpdatedState(onScrubStart)
    val latestOnMove by rememberUpdatedState(onScrubMove)
    val latestOnCommit by rememberUpdatedState(onScrubCommit)

    BoxWithConstraints(
        modifier = modifier
            .height(48.dp)
            .semantics { contentDescription = "Video playback timeline" },
        contentAlignment = Alignment.Center
    ) {
        val barWidthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val density = LocalDensity.current

        // Floating timecode preview bubble directly above thumb while dragging
        if (isDragging && seekable) {
            val bubbleHalfPx = with(density) { 42.dp.toPx() }
            val thumbCenterX = (playedFraction * barWidthPx)
                .coerceIn(bubbleHalfPx, (barWidthPx - bubbleHalfPx).coerceAtLeast(bubbleHalfPx))
            val offsetX = (thumbCenterX - bubbleHalfPx).roundToInt()
            val offsetY = with(density) { (-38).dp.roundToPx() }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = WarmObsidianLow.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, TimelineIceCyan.copy(alpha = 0.75f)),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(offsetX, offsetY) }
            ) {
                Text(
                    text = FormatUtils.formatDuration(positionMs),
                    style = LumaMonoStyle.copy(color = TimelineIceCyan, fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(seekable) {
                    if (!seekable) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = true)
                        down.consume()
                        val w = size.width.toFloat().coerceAtLeast(1f)
                        val dur = latestDuration.coerceAtLeast(1L)

                        fun xToMs(x: Float): Long {
                            val frac = (x / w).coerceIn(0f, 1f)
                            return (frac * dur).toLong()
                        }

                        var currentTarget = xToMs(down.position.x)
                        latestOnStart(currentTarget)

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.pressed) {
                                currentTarget = xToMs(change.position.x)
                                latestOnMove(currentTarget)
                                change.consume()
                            }
                        } while (event.changes.any { it.pressed })

                        latestOnCommit(currentTarget)
                    }
                }
        ) {
            val centerY = size.height / 2f
            val hPad = with(density) { 8.dp.toPx() }
            val usableW = (size.width - hPad * 2f).coerceAtLeast(1f)
            val trackH = trackHeight.toPx()
            val shadowH = trackH + with(density) { 4.dp.toPx() }
            val radius = CornerRadius(trackH / 2f, trackH / 2f)

            // 1. Dark contrast rail backdrop (ensures 100% legibility over bright white scenes)
            drawRoundRect(
                color = TimelineRailShadow,
                topLeft = Offset(hPad - 2f, centerY - shadowH / 2f),
                size = Size(usableW + 4f, shadowH),
                cornerRadius = CornerRadius(shadowH / 2f, shadowH / 2f)
            )

            // 2. Unplayed rail
            drawRoundRect(
                color = TimelineTrackBg,
                topLeft = Offset(hPad, centerY - trackH / 2f),
                size = Size(usableW, trackH),
                cornerRadius = radius
            )

            // 3. Visible buffered track (Sky-Ice ahead of playhead for online streams)
            val bufferedW = (usableW * bufferedFraction).coerceAtLeast(0f)
            if (bufferedW > 0f) {
                drawRoundRect(
                    color = TimelineBufferIce,
                    topLeft = Offset(hPad, centerY - trackH / 2f),
                    size = Size(bufferedW, trackH),
                    cornerRadius = radius
                )
            }

            // 4. High-Contrast Ice Cyan played bar
            val playedW = (usableW * playedFraction).coerceAtLeast(0f)
            if (playedW > 0f) {
                drawRoundRect(
                    color = TimelineIceCyan,
                    topLeft = Offset(hPad, centerY - trackH / 2f),
                    size = Size(playedW, trackH),
                    cornerRadius = radius
                )
            }

            // 5. Warm Cinema Amber A-B Repeat segment & pin ticks
            if (seekable && abPointAMs != null) {
                val aFrac = (abPointAMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                val aX = hPad + usableW * aFrac
                val pinHalf = with(density) { 7.dp.toPx() }

                if (abPointBMs != null && abPointBMs > abPointAMs) {
                    val bFrac = (abPointBMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
                    val bX = hPad + usableW * bFrac
                    drawRoundRect(
                        color = CinemaAmber.copy(alpha = 0.85f),
                        topLeft = Offset(aX, centerY - trackH / 2f),
                        size = Size((bX - aX).coerceAtLeast(2f), trackH),
                        cornerRadius = radius
                    )
                    drawLine(
                        color = CinemaAmber,
                        start = Offset(bX, centerY - pinHalf),
                        end = Offset(bX, centerY + pinHalf),
                        strokeWidth = with(density) { 2.5.dp.toPx() },
                        cap = StrokeCap.Round
                    )
                }
                drawLine(
                    color = CinemaAmber,
                    start = Offset(aX, centerY - pinHalf),
                    end = Offset(aX, centerY + pinHalf),
                    strokeWidth = with(density) { 2.5.dp.toPx() },
                    cap = StrokeCap.Round
                )
            }

            // 6. Glowing Ice Cyan + Pure White Thumb
            if (seekable) {
                val thumbX = hPad + usableW * playedFraction
                drawCircle(
                    color = TimelineIceCyan.copy(alpha = if (isDragging) 0.42f else 0.28f),
                    radius = thumbHaloRadius.toPx(),
                    center = Offset(thumbX, centerY)
                )
                drawCircle(
                    color = TimelineIceCyan,
                    radius = thumbCoreRadius.toPx() + with(density) { 1.8.dp.toPx() },
                    center = Offset(thumbX, centerY)
                )
                drawCircle(
                    color = Color.White,
                    radius = thumbCoreRadius.toPx(),
                    center = Offset(thumbX, centerY)
                )
            }
        }
    }
}

@Composable
private fun HudMiniTag(text: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = accent.copy(alpha = 0.2f)
    ) {
        Text(
            text = text,
            style = LumaMonoStyle.copy(color = accent, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun HudActionPill(
    text: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (highlighted) CinemaAmber.copy(alpha = 0.22f) else WarmObsidianHigh.copy(alpha = 0.8f),
        border = BorderStroke(1.dp, if (highlighted) CinemaAmber else WarmBorder),
        modifier = Modifier
            .height(36.dp)
            .testTag(tag)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Text(
                text = text,
                style = LumaMonoStyle.copy(color = if (highlighted) CinemaAmber else WarmIvory)
            )
        }
    }
}

@Composable
private fun GestureMeterContent(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    valueText: String,
    progress: Float,
    accent: Color
) {
    Column(
        modifier = Modifier
            .width(196.dp)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = accent, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = WarmStoneSecondary)
        Text(valueText, style = MaterialTheme.typography.titleLarge, color = WarmIvory)
        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            color = accent,
            trackColor = Color(0x33FFFFFF),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}

private fun computeVideoViewportSize(
    mode: AspectRatioMode,
    containerW: Float,
    containerH: Float,
    containerRatio: Float,
    sourceRatio: Float,
    videoW: Int,
    videoH: Int
): Pair<Float, Float> {
    fun fitRatio(targetRatio: Float): Pair<Float, Float> {
        return if (containerRatio > targetRatio) {
            (containerH * targetRatio) to containerH
        } else {
            containerW to (containerW / targetRatio)
        }
    }

    fun fillRatio(targetRatio: Float): Pair<Float, Float> {
        return if (containerRatio > targetRatio) {
            containerW to (containerW / targetRatio)
        } else {
            (containerH * targetRatio) to containerH
        }
    }

    return when (mode) {
        // Maximizes display area without cropping or distorting original video aspect ratio
        AspectRatioMode.FIT -> fitRatio(sourceRatio)
        AspectRatioMode.FILL -> fillRatio(sourceRatio)
        AspectRatioMode.STRETCH -> containerW to containerH
        AspectRatioMode.RATIO_16_9 -> fitRatio(16f / 9f)
        AspectRatioMode.RATIO_21_9 -> fitRatio(21f / 9f)
        AspectRatioMode.RATIO_4_3 -> fitRatio(4f / 3f)
        AspectRatioMode.ORIGINAL -> {
            if (videoW > 0 && videoH > 0) {
                minOf(videoW.toFloat(), containerW) to minOf(videoH.toFloat(), containerH)
            } else {
                fitRatio(sourceRatio)
            }
        }
    }
}

private fun readCurrentBrightness(activity: Activity?): Float {
    val attr = activity?.window?.attributes?.screenBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
    return if (attr in 0.01f..1.0f) attr else 0.5f
}

private fun applyWindowBrightness(activity: Activity?, fraction: Float) {
    val window = activity?.window ?: return
    val params = window.attributes
    params.screenBrightness = fraction.coerceIn(0.01f, 1.0f)
    window.attributes = params
}

private fun readCurrentVolumeFraction(audioManager: AudioManager): Float {
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    return cur.toFloat() / max.toFloat()
}

private fun applyMediaVolume(audioManager: AudioManager, fraction: Float) {
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val target = (fraction.coerceIn(0f, 1f) * max).toInt()
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
}
