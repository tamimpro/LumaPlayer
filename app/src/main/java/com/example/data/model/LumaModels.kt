package com.example.data.model

import java.util.Locale
import kotlin.math.abs

enum class AppThemeMode(val label: String) {
    AMOLED_DARK("AMOLED Dark"),
    LIGHT("Crisp Light"),
    SYSTEM("System Default")
}

enum class AspectRatioMode(val label: String, val shortLabel: String) {
    FIT("Fit Screen (Original Ratio)", "Fit"),
    FILL("Fill / Crop (19.5:9)", "Fill"),
    STRETCH("Stretch", "Stretch"),
    RATIO_16_9("16:9 Widescreen", "16:9"),
    RATIO_21_9("21:9 Cinema", "21:9"),
    RATIO_4_3("4:3 Classic", "4:3"),
    ORIGINAL("Original 1:1 Pixels", "1:1")
}

enum class VideoSortOption(val label: String) {
    DATE_DESC("Newest First"),
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    SIZE_DESC("Largest Size"),
    DURATION_DESC("Longest Duration"),
    RESOLUTION_DESC("Highest Resolution")
}

enum class StreamProtocol(val label: String) {
    AUTO("Auto Detect"),
    HLS("HLS (.m3u8)"),
    DASH("MPEG-DASH (.mpd)"),
    PROGRESSIVE("Direct HTTP / File")
}

enum class SubtitleEdgeStyle(val label: String) {
    OUTLINE("Outline"),
    DROP_SHADOW("Drop Shadow"),
    RAISED("Raised"),
    NONE("None")
}

enum class EqPreset(val label: String, val bandOffsetsMillibels: List<Int>) {
    FLAT("Flat", listOf(0, 0, 0, 0, 0)),
    CINEMA("Cinema", listOf(350, 150, 0, 250, 400)),
    BASS_BOOST("Bass Boost", listOf(600, 400, 100, 0, 50)),
    VOCAL_CLARITY("Vocal Clarity", listOf(-150, 100, 450, 400, 150)),
    LATE_NIGHT("Late Night", listOf(-200, 150, 300, 100, -250)),
    CUSTOM("Custom", listOf(0, 0, 0, 0, 0))
}

enum class PlayerOrientationMode(val label: String) {
    AUTO_VIDEO("Smart Video"),
    LANDSCAPE("Landscape"),
    REVERSE_LANDSCAPE("Reverse Landscape"),
    PORTRAIT("Portrait"),
    SENSOR("Auto Sensor")
}

data class PlaybackQueueItem(
    val uri: String,
    val title: String,
    val subtitleUri: String? = null,
    val resumePositionMs: Long = 0L,
    val isStream: Boolean = false,
    val protocol: StreamProtocol = StreamProtocol.AUTO,
    val headers: Map<String, String> = emptyMap()
)

data class PlaybackRequest(
    val queue: List<PlaybackQueueItem>,
    val startIndex: Int = 0,
    val startInBackground: Boolean = false
)

data class VideoTrackOption(
    val id: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val width: Int,
    val height: Int,
    val bitrateBps: Int,
    val frameRate: Float,
    val codec: String,
    val hdrLabel: String?,
    val isSelected: Boolean
) {
    val label: String
        get() {
            val res = if (height > 0) "${height}p" else "Unknown"
            val fps = if (frameRate >= 45f) " ${frameRate.toInt()}fps" else ""
            val hdr = if (!hdrLabel.isNullOrBlank()) " · $hdrLabel" else ""
            val br = if (bitrateBps > 0) " · ${FormatUtils.formatBitrate(bitrateBps)}" else ""
            return "$res$fps$hdr$br"
        }
}

data class AudioTrackOption(
    val id: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String,
    val codec: String,
    val channels: Int,
    val sampleRateHz: Int,
    val bitrateBps: Int,
    val isSelected: Boolean
)

data class SubtitleTrackOption(
    val id: String,
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val language: String,
    val mimeType: String,
    val isSelected: Boolean
)

data class VideoDiagnostics(
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f,
    val videoCodec: String = "—",
    val videoDecoderName: String = "—",
    val isHardwareAccelerated: Boolean = true,
    val hdrFormat: String = "SDR",
    val colorSpace: String = "—",
    val videoBitrateBps: Int = 0,
    val audioCodec: String = "—",
    val audioChannels: Int = 0,
    val audioSampleRateHz: Int = 0,
    val droppedFrames: Int = 0,
    val renderedFrames: Int = 0,
    val bufferedPercent: Int = 0,
    val displayHdrCapabilities: List<String> = emptyList(),
    val displayRefreshRateHz: Float = 60f
) {
    val resolutionBadge: String
        get() = FormatUtils.resolutionBadge(width, height)
}

data class FolderSummary(
    val folderUri: String,
    val folderName: String,
    val videoCount: Int,
    val totalSizeBytes: Long,
    val previewThumbnailUri: String?,
    val latestModified: Long
)

object FormatUtils {
    fun formatDuration(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val totalSeconds = ms / 1000L
        val seconds = totalSeconds % 60
        val minutes = (totalSeconds / 60) % 60
        val hours = totalSeconds / 3600
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    fun formatSignedDelta(deltaMs: Long): String {
        val sign = if (deltaMs >= 0) "+" else "-"
        return "$sign${formatDuration(abs(deltaMs))}"
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f GB", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f MB", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.0f KB", bytes / kb)
            else -> "$bytes B"
        }
    }

    fun formatBitrate(bps: Int): String {
        if (bps <= 0) return "—"
        return if (bps >= 1_000_000) {
            String.format(Locale.US, "%.1f Mbps", bps / 1_000_000f)
        } else {
            String.format(Locale.US, "%d kbps", bps / 1_000)
        }
    }

    fun resolutionBadge(width: Int, height: Int): String {
        val maxDim = maxOf(width, height)
        val minDim = minOf(width, height)
        return when {
            maxDim >= 7600 || minDim >= 4300 -> "8K"
            maxDim >= 3800 || minDim >= 2100 -> "4K"
            maxDim >= 2500 || minDim >= 1400 -> "1440p"
            maxDim >= 1900 || minDim >= 1040 -> "1080p"
            maxDim >= 1260 || minDim >= 700 -> "720p"
            minDim > 0 -> "${minDim}p"
            else -> ""
        }
    }

    fun channelLayoutLabel(channels: Int): String = when (channels) {
        1 -> "Mono"
        2 -> "Stereo 2.0"
        6 -> "5.1 Surround"
        8 -> "7.1 Surround"
        else -> if (channels > 0) "${channels}ch" else "—"
    }
}
