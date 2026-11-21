package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.VideoItemEntity
import com.example.data.model.FolderSummary
import com.example.data.model.FormatUtils
import com.example.data.repository.StorageAccessState
import com.example.ui.components.FolderCard
import com.example.ui.components.NoLocalVideosHero
import com.example.ui.components.StoragePermissionHero
import com.example.ui.components.VideoThumbnailBox
import com.example.ui.theme.LumaMonoStyle

@Composable
fun FoldersScreen(
    folders: List<FolderSummary>,
    continueWatching: List<VideoItemEntity>,
    storageAccess: StorageAccessState,
    isScanning: Boolean,
    onOpenFolder: (FolderSummary) -> Unit,
    onResumeVideo: (List<VideoItemEntity>, Int) -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onRequestVideoAccess: () -> Unit,
    onRescanDevice: () -> Unit,
    onOpenStreamTab: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (isScanning) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Scanning device storage for videos…",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // If only Read-Only Video permission is granted, offer a sleek 1-tap upgrade banner for seamless Move/Rename/Delete
        if (storageAccess.hasVideoReadPermission && !storageAccess.hasAllFilesAccess) {
            item {
                Surface(
                    onClick = onRequestAllFilesAccess,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("upgrade_all_files_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Rounded.AdminPanelSettings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Enable All Files Access",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Unlock instant move, rename, delete & MKV/subtitle scanning",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = onRequestAllFilesAccess) {
                            Text("Allow")
                        }
                    }
                }
            }
        }

        if (continueWatching.isNotEmpty()) {
            item {
                Text(
                    text = "Continue Watching",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(continueWatching.take(8), key = { _, v -> v.uri }) { index, video ->
                        ContinueWatchingCard(
                            video = video,
                            onClick = { onResumeVideo(continueWatching, index) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }

        if (!storageAccess.canScanDevice && folders.isEmpty()) {
            item {
                StoragePermissionHero(
                    onRequestAllFilesAccess = onRequestAllFilesAccess,
                    onRequestVideoAccess = onRequestVideoAccess,
                    onOpenStreamTab = onOpenStreamTab
                )
            }
        } else if (folders.isEmpty()) {
            item {
                NoLocalVideosHero(
                    onRescanDevice = onRescanDevice,
                    onOpenStreamTab = onOpenStreamTab
                )
            }
        } else {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Device Folders (${folders.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    IconButton(
                        onClick = onRescanDevice,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("folders_rescan_button")
                    ) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = "Refresh device library",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            items(folders, key = { it.folderUri }) { folder ->
                FolderCard(
                    folder = folder,
                    onClick = { onOpenFolder(folder) }
                )
            }
        }
    }
}

@Composable
private fun ContinueWatchingCard(
    video: VideoItemEntity,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .width(216.dp)
            .testTag("continue_watching_card")
    ) {
        Column {
            VideoThumbnailBox(
                uri = video.uri,
                durationMs = video.durationMs,
                width = video.width,
                height = video.height,
                hdrTag = video.hdrTag,
                frameRate = video.frameRate,
                progressFraction = video.progressFraction,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (video.lastPositionMs > 0L) {
                        "Resume ${FormatUtils.formatDuration(video.lastPositionMs)}"
                    } else {
                        "Watched recently"
                    },
                    style = LumaMonoStyle.copy(color = MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
