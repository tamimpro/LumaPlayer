package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "mounted_folders")
data class MountedFolderEntity(
    @PrimaryKey val treeUri: String,
    val displayName: String,
    val lastScannedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "video_items",
    indices = [
        Index(value = ["folderUri"]),
        Index(value = ["isFavorite"]),
        Index(value = ["lastPlayedAt"])
    ]
)
data class VideoItemEntity(
    @PrimaryKey val uri: String,
    val filePath: String = "",
    val folderUri: String,
    val folderName: String,
    val title: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f,
    val hdrTag: String? = null,
    val videoCodec: String = "",
    val siblingSubtitleUri: String? = null,
    val dateModified: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val lastPositionMs: Long = 0L,
    val lastPlayedAt: Long = 0L
) {
    val progressFraction: Float
        get() = if (durationMs > 0L && lastPositionMs > 0L) {
            (lastPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_entries",
    primaryKeys = ["playlistId", "videoUri"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["playlistId"]), Index(value = ["videoUri"])]
)
data class PlaylistEntryEntity(
    val playlistId: Long,
    val videoUri: String,
    val sortOrder: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "stream_bookmarks")
data class StreamBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val url: String,
    val protocol: String = "AUTO",
    val headersJson: String = "{}",
    val isPinned: Boolean = false,
    val lastPositionMs: Long = 0L,
    val lastPlayedAt: Long = System.currentTimeMillis()
)
