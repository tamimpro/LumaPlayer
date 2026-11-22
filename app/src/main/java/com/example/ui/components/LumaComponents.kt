package com.example.ui.components

import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.R
import com.example.data.local.PlaylistWithCount
import com.example.data.local.VideoItemEntity
import com.example.data.model.FolderSummary
import com.example.data.model.FormatUtils
import com.example.ui.theme.CinemaAmber
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.HdrGold
import com.example.ui.theme.HwDecodeGreen
import com.example.ui.theme.LumaMonoStyle
import com.example.ui.theme.WarmObsidianLow
import java.io.File

@Composable
fun LumaBrandMark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.luma_player_icon_1795185395366),
            contentDescription = "Luma Player logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
        )
        Text(
            text = "LUMA",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        )
    }
}

@Composable
fun VideoThumbnailBox(
    uri: String,
    durationMs: Long,
    width: Int,
    height: Int,
    hdrTag: String?,
    frameRate: Float,
    progressFraction: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val request = remember(uri) {
        ImageRequest.Builder(context)
            .data(Uri.parse(uri))
            .videoFrameMillis(2_000L)
            .crossfade(true)
            .build()
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0x40000000), Color.Transparent, Color(0x99000000))
                    )
                )
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val res = FormatUtils.resolutionBadge(width, height)
            if (res.isNotBlank()) {
                QualityBadgePill(res, CinemaAmber)
            }
            if (!hdrTag.isNullOrBlank()) {
                QualityBadgePill(hdrTag, HdrGold)
            }
            if (frameRate >= 50f) {
                QualityBadgePill("${frameRate.toInt()}fps", HwDecodeGreen)
            }
        }

        if (durationMs > 0L) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = WarmObsidianLow.copy(alpha = 0.85f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
            ) {
                Text(
                    text = FormatUtils.formatDuration(durationMs),
                    style = LumaMonoStyle.copy(color = Color.White),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        if (progressFraction > 0.02f) {
            LinearProgressIndicator(
                progress = { progressFraction },
                color = CinemaAmber,
                trackColor = Color(0x55FFFFFF),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
fun QualityBadgePill(text: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(5.dp),
        color = WarmObsidianLow.copy(alpha = 0.85f),
        border = BorderStroke(0.75.dp, accent.copy(alpha = 0.65f))
    ) {
        Text(
            text = text,
            style = LumaMonoStyle.copy(color = accent, fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
fun VideoListRowCard(
    video: VideoItemEntity,
    onPlay: () -> Unit,
    onPlayInBackground: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShowInfo: () -> Unit,
    onMoveFile: (() -> Unit)? = null,
    onRenameFile: (() -> Unit)? = null,
    onDeleteFile: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        onClick = onPlay,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("video_card_${video.title.take(12)}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VideoThumbnailBox(
                uri = video.uri,
                durationMs = video.durationMs,
                width = video.width,
                height = video.height,
                hdrTag = video.hdrTag,
                frameRate = video.frameRate,
                progressFraction = video.progressFraction,
                modifier = Modifier
                    .width(134.dp)
                    .aspectRatio(16f / 9f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = buildString {
                        append(video.folderName)
                        if (video.sizeBytes > 0L) {
                            append(" · ")
                            append(FormatUtils.formatFileSize(video.sizeBytes))
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (video.lastPositionMs > 2_000L && video.durationMs > 0L) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Resume at ${FormatUtils.formatDuration(video.lastPositionMs)}",
                        style = LumaMonoStyle.copy(color = MaterialTheme.colorScheme.primary)
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (video.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = if (video.isFavorite) "Remove from favourites" else "Add to favourites",
                        tint = if (video.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("video_menu_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "Video options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Play in Background") },
                            leadingIcon = { Icon(Icons.Rounded.Headphones, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onPlayInBackground()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Add to Playlist") },
                            leadingIcon = { Icon(Icons.Rounded.PlaylistAdd, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onAddToPlaylist()
                            }
                        )
                        if (onMoveFile != null) {
                            DropdownMenuItem(
                                text = { Text("Move to Folder") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onMoveFile()
                                }
                            )
                        }
                        if (onRenameFile != null) {
                            DropdownMenuItem(
                                text = { Text("Rename File") },
                                leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onRenameFile()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Media Info") },
                            leadingIcon = { Icon(Icons.Rounded.Info, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onShowInfo()
                            }
                        )
                        if (onRemoveFromPlaylist != null) {
                            DropdownMenuItem(
                                text = { Text("Remove from Playlist") },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onRemoveFromPlaylist()
                                }
                            )
                        }
                        if (onDeleteFile != null) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete from Device", color = ErrorRed) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.DeleteForever,
                                        contentDescription = null,
                                        tint = ErrorRed
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDeleteFile()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FolderCard(
    folder: FolderSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .testTag("folder_card_${folder.folderName.take(12)}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 62.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (folder.previewThumbnailUri != null) {
                    val context = LocalContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(Uri.parse(folder.previewThumbnailUri))
                            .videoFrameMillis(2_000L)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0x55000000))
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folder.folderName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${folder.videoCount} video${if (folder.videoCount == 1) "" else "s"} · ${FormatUtils.formatFileSize(folder.totalSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "${folder.videoCount}",
                    style = LumaMonoStyle.copy(color = MaterialTheme.colorScheme.onPrimaryContainer),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun StoragePermissionHero(
    onRequestAllFilesAccess: () -> Unit,
    onRequestVideoAccess: () -> Unit,
    onOpenStreamTab: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.09f),
                            Color.Transparent,
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.09f)
                        )
                    )
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.luma_player_icon_1795185395366),
                contentDescription = "Luma Player Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(18.dp))
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Allow File Access",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Grant All Files Access so Luma Player automatically discovers every video folder, loads sibling subtitles, and lets you move, rename, or delete videos seamlessly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onRequestAllFilesAccess,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("grant_all_files_button")
            ) {
                Icon(Icons.Rounded.AdminPanelSettings, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Allow All Files Access (Recommended)")
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onRequestVideoAccess,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("grant_video_access_button")
                ) {
                    Icon(Icons.Rounded.VideoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Videos Only", maxLines = 1)
                }
                OutlinedButton(
                    onClick = onOpenStreamTab,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("empty_stream_button")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stream URL", maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun NoLocalVideosHero(
    onRescanDevice: () -> Unit,
    onOpenStreamTab: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Rounded.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No Local Videos Found",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Add or download videos to your device storage and tap Scan Now, or launch an online 4K HDR stream.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRescanDevice,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("rescan_empty_button")
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan Now")
                }
                OutlinedButton(
                    onClick = onOpenStreamTab,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Stream URL")
                }
            }
        }
    }
}

@Composable
fun StorageAccessPromptDialog(
    title: String,
    message: String,
    showVideosOnlyOption: Boolean,
    onGrantAllFiles: () -> Unit,
    onGrantVideosOnly: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.AdminPanelSettings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = { Text(title) },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onGrantAllFiles,
                modifier = Modifier.testTag("dialog_grant_all_files")
            ) {
                Text("Allow All Files Access")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showVideosOnlyOption) {
                    TextButton(
                        onClick = onGrantVideosOnly,
                        modifier = Modifier.testTag("dialog_grant_videos_only")
                    ) {
                        Text("Videos Only")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Later")
                }
            }
        }
    )
}

@Composable
fun MoveVideoDialog(
    video: VideoItemEntity,
    destinations: List<File>,
    onMoveToPath: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var customFolderName by remember { mutableStateOf("") }
    val primaryRoot = remember { Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0" }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Move Video") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                destinations.forEach { dir ->
                    val isCurrent = video.folderUri == dir.absolutePath
                    Surface(
                        onClick = { if (!isCurrent) onMoveToPath(dir.absolutePath) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isCurrent) "${dir.name} (Current)" else dir.name,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = dir.absolutePath.removePrefix(primaryRoot).ifBlank { "/" },
                                    style = LumaMonoStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                OutlinedTextField(
                    value = customFolderName,
                    onValueChange = { customFolderName = it },
                    label = { Text("Or create new folder") },
                    placeholder = { Text("Movies/Favorites") },
                    leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("move_custom_folder_input")
                )
            }
        },
        confirmButton = {
            if (customFolderName.isNotBlank()) {
                Button(
                    onClick = {
                        val cleaned = customFolderName.trim().trimStart('/')
                        val target = File(primaryRoot, cleaned).absolutePath
                        onMoveToPath(target)
                    },
                    modifier = Modifier.testTag("move_create_confirm_button")
                ) {
                    Text("Move Here")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun RenameVideoDialog(
    video: VideoItemEntity,
    onConfirmRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember(video.uri) { mutableStateOf(video.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DriveFileRenameOutline, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text("Rename Video") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New File Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_video_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirmRename(newName) },
                enabled = newName.isNotBlank() && newName.trim() != video.title
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DeleteVideoConfirmDialog(
    video: VideoItemEntity,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = ErrorRed) },
        title = { Text("Delete Video from Device?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = "This permanently deletes the file (${FormatUtils.formatFileSize(video.sizeBytes)}) from your device storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed, contentColor = Color.White),
                modifier = Modifier.testTag("confirm_delete_video_button")
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AddToPlaylistDialog(
    video: VideoItemEntity,
    playlists: List<PlaylistWithCount>,
    onSelectPlaylist: (PlaylistWithCount) -> Unit,
    onCreateNewPlaylist: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (playlists.isEmpty()) {
                    Text(
                        "No playlists yet. Create one below.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    playlists.forEach { playlist ->
                        Surface(
                            onClick = { onSelectPlaylist(playlist) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(playlist.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${playlist.itemCount} items",
                                    style = LumaMonoStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreateNewPlaylist) {
                Text("New Playlist")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun VideoInfoDialog(
    video: VideoItemEntity,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        title = {
            Text(
                video.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoLine("Folder", video.folderName)
                if (video.filePath.isNotBlank()) {
                    InfoLine("Path", video.filePath)
                }
                InfoLine("Resolution", if (video.width > 0) "${video.width} × ${video.height} (${FormatUtils.resolutionBadge(video.width, video.height)})" else "Detected on playback")
                InfoLine("HDR Profile", video.hdrTag ?: "Standard / Auto-detected on play")
                InfoLine("Frame Rate", if (video.frameRate > 0f) String.format(java.util.Locale.US, "%.2f fps", video.frameRate) else "Auto")
                InfoLine("Format", video.videoCodec.ifBlank { video.mimeType })
                InfoLine("Duration", FormatUtils.formatDuration(video.durationMs))
                InfoLine("File Size", FormatUtils.formatFileSize(video.sizeBytes))
                if (!video.siblingSubtitleUri.isNullOrBlank()) {
                    InfoLine("Subtitles", "Sibling subtitle auto-matched")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            value,
            style = LumaMonoStyle.copy(color = MaterialTheme.colorScheme.onSurface),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
