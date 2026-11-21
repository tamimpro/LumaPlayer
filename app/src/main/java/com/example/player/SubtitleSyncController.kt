package com.example.player

import android.graphics.Color
import android.graphics.Typeface
import androidx.annotation.OptIn
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.example.data.model.SubtitleEdgeStyle
import com.example.data.preferences.UserSettings
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

private data class TimedCueSnapshot(
    val sourcePositionMs: Long,
    val cues: List<Cue>
)

/**
 * Manages live subtitle styling and real-time cue synchronization offset (-10s to +10s).
 * When sync offset is 0ms, cues render directly with zero latency.
 * When offset is positive (delay subtitles), cues are held in a small timed ring buffer
 * and displayed once playback reaches sourcePosition + offset.
 */
class SubtitleSyncController {

    private val cueHistory = CopyOnWriteArrayList<TimedCueSnapshot>()
    @Volatile
    private var syncOffsetMs: Long = 0L

    @Volatile
    private var latestDirectCues: List<Cue> = emptyList()

    fun setSyncOffsetMs(offsetMs: Long) {
        syncOffsetMs = offsetMs.coerceIn(-10_000L, 10_000L)
    }

    fun clear() {
        cueHistory.clear()
        latestDirectCues = emptyList()
    }

    fun onCuesReceived(currentPositionMs: Long, cues: List<Cue>) {
        latestDirectCues = cues
        cueHistory.add(TimedCueSnapshot(currentPositionMs, cues))
        while (cueHistory.size > 64) {
            cueHistory.removeAt(0)
        }
    }

    fun resolveCuesForPosition(currentPositionMs: Long): List<Cue> {
        val offset = syncOffsetMs
        if (offset == 0L) return latestDirectCues

        // Positive offset delays subtitles: display cues that arrived at (currentPosition - offset).
        // Negative offset advances subtitles: match closest recent non-empty cue window.
        val targetSourcePos = currentPositionMs - offset
        val matching = cueHistory
            .filter { it.sourcePositionMs <= targetSourcePos + 150L }
            .maxByOrNull { it.sourcePositionMs }

        return when {
            offset > 0L -> {
                if (matching != null && abs(targetSourcePos - matching.sourcePositionMs) < 12_000L) {
                    matching.cues
                } else {
                    emptyList()
                }
            }
            else -> latestDirectCues
        }
    }

    companion object {
        @OptIn(UnstableApi::class)
        fun applyStyle(subtitleView: SubtitleView, settings: UserSettings) {
            val bgAlpha = (settings.subtitleBgOpacity.coerceIn(0f, 1f) * 255).toInt()
            val bgColor = Color.argb(bgAlpha, 0, 0, 0)
            val edgeType = when (settings.subtitleEdgeStyle) {
                SubtitleEdgeStyle.OUTLINE -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
                SubtitleEdgeStyle.DROP_SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                SubtitleEdgeStyle.RAISED -> CaptionStyleCompat.EDGE_TYPE_RAISED
                SubtitleEdgeStyle.NONE -> CaptionStyleCompat.EDGE_TYPE_NONE
            }

            val style = CaptionStyleCompat(
                settings.subtitleTextColorArgb,
                bgColor,
                Color.TRANSPARENT,
                edgeType,
                Color.BLACK,
                Typeface.SANS_SERIF
            )

            subtitleView.setApplyEmbeddedStyles(false)
            subtitleView.setApplyEmbeddedFontSizes(false)
            subtitleView.setStyle(style)
            subtitleView.setFractionalTextSize(
                SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * settings.subtitleTextScale.coerceIn(0.5f, 2.5f)
            )
            subtitleView.setBottomPaddingFraction(settings.subtitleBottomPaddingFraction.coerceIn(0.02f, 0.35f))
        }
    }
}
