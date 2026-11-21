package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.VideoItemEntity
import com.example.ui.components.VideoListRowCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    title: String,
    subtitle: String,
    videos: List<VideoItemEntity>,
    onBack: () -> Unit,
    onPlayVideo: (List<VideoItemEntity>, Int, Boolean) -> Unit,
    onToggleFavorite: (VideoItemEntity) -> Unit,
    onAddToPlaylist: (VideoItemEntity) -> Unit,
    onShowInfo: (VideoItemEntity) -> Unit,
    onMoveVideo: (VideoItemEntity) -> Unit,
    onRenameVideo: (VideoItemEntity) -> Unit,
    onDeleteVideo: (VideoItemEntity) -> Unit,
    onRemoveFromPlaylist: ((VideoItemEntity) -> Unit)? = null,
    onClearAll: (() -> Unit)? = null,
    clearAllLabel: String = "Clear"
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("collection_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                if (onClearAll != null && videos.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(clearAllLabel)
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        if (videos.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { onPlayVideo(videos, 0, false) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("collection_play_all_button")
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Play All (${videos.size})")
                }
            }
        }

        if (videos.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No Videos Here Yet", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Videos you add or watch will appear in this collection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(videos, key = { _, v -> v.uri }) { index, video ->
                    VideoListRowCard(
                        video = video,
                        onPlay = { onPlayVideo(videos, index, false) },
                        onPlayInBackground = { onPlayVideo(videos, index, true) },
                        onToggleFavorite = { onToggleFavorite(video) },
                        onAddToPlaylist = { onAddToPlaylist(video) },
                        onShowInfo = { onShowInfo(video) },
                        onMoveFile = { onMoveVideo(video) },
                        onRenameFile = { onRenameVideo(video) },
                        onDeleteFile = { onDeleteVideo(video) },
                        onRemoveFromPlaylist = onRemoveFromPlaylist?.let { fn -> { fn(video) } }
                    )
                }
            }
        }
    }
}
