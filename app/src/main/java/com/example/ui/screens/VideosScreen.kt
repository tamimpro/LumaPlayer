package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.VideoItemEntity
import com.example.data.model.VideoSortOption
import com.example.data.repository.StorageAccessState
import com.example.ui.components.NoLocalVideosHero
import com.example.ui.components.StoragePermissionHero
import com.example.ui.components.VideoListRowCard

@Composable
fun VideosScreen(
    videos: List<VideoItemEntity>,
    storageAccess: StorageAccessState,
    searchQuery: String,
    favoritesOnly: Boolean,
    sortOption: VideoSortOption,
    onSearchQueryChange: (String) -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onSelectSort: (VideoSortOption) -> Unit,
    onPlayVideo: (List<VideoItemEntity>, Int, Boolean) -> Unit,
    onToggleFavorite: (VideoItemEntity) -> Unit,
    onAddToPlaylist: (VideoItemEntity) -> Unit,
    onShowInfo: (VideoItemEntity) -> Unit,
    onMoveVideo: (VideoItemEntity) -> Unit,
    onRenameVideo: (VideoItemEntity) -> Unit,
    onDeleteVideo: (VideoItemEntity) -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onRequestVideoAccess: () -> Unit,
    onRescanDevice: () -> Unit,
    onOpenStreamTab: () -> Unit
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search videos or folders…") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("videos_search_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = favoritesOnly,
                        onClick = onToggleFavoritesOnly,
                        label = { Text("Favourites") },
                        leadingIcon = {
                            Icon(
                                if (favoritesOnly) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = null
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("videos_favorites_filter_chip")
                    )

                    Box {
                        FilterChip(
                            selected = false,
                            onClick = { sortMenuExpanded = true },
                            label = { Text(sortOption.label) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = null) },
                            modifier = Modifier.testTag("videos_sort_chip")
                        )
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            VideoSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        sortMenuExpanded = false
                                        onSelectSort(option)
                                    }
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "${videos.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (videos.isEmpty()) {
                item {
                    if (searchQuery.isNotBlank() || favoritesOnly) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (favoritesOnly) "No favourite videos yet" else "No matching videos found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Try adjusting your search query or favourite filter.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (!storageAccess.canScanDevice) {
                        StoragePermissionHero(
                            onRequestAllFilesAccess = onRequestAllFilesAccess,
                            onRequestVideoAccess = onRequestVideoAccess,
                            onOpenStreamTab = onOpenStreamTab
                        )
                    } else {
                        NoLocalVideosHero(
                            onRescanDevice = onRescanDevice,
                            onOpenStreamTab = onOpenStreamTab
                        )
                    }
                }
            } else {
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
                        onDeleteFile = { onDeleteVideo(video) }
                    )
                }
            }
        }
    }
}
