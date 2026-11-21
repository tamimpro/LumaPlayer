package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val itemCount: Int
)

@Dao
interface LumaDao {
    // Mounted Folders
    @Query("SELECT * FROM mounted_folders ORDER BY displayName ASC")
    fun observeMountedFolders(): Flow<List<MountedFolderEntity>>

    @Query("SELECT * FROM mounted_folders")
    suspend fun getMountedFoldersOnce(): List<MountedFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMountedFolder(folder: MountedFolderEntity)

    @Query("DELETE FROM mounted_folders WHERE treeUri = :treeUri")
    suspend fun deleteMountedFolder(treeUri: String)

    // Video Items
    @Query("SELECT * FROM video_items ORDER BY dateModified DESC")
    fun observeAllVideos(): Flow<List<VideoItemEntity>>

    @Query("SELECT * FROM video_items")
    suspend fun getAllVideosOnce(): List<VideoItemEntity>

    @Query("SELECT * FROM video_items WHERE folderUri = :folderUri ORDER BY title ASC")
    fun observeVideosInFolder(folderUri: String): Flow<List<VideoItemEntity>>

    @Query("SELECT * FROM video_items WHERE isFavorite = 1 ORDER BY lastPlayedAt DESC, title ASC")
    fun observeFavoriteVideos(): Flow<List<VideoItemEntity>>

    @Query("SELECT * FROM video_items WHERE lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT 30")
    fun observeWatchHistory(): Flow<List<VideoItemEntity>>

    @Query("SELECT * FROM video_items WHERE uri = :uri LIMIT 1")
    suspend fun getVideoByUri(uri: String): VideoItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVideo(video: VideoItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertVideos(videos: List<VideoItemEntity>)

    @Query("DELETE FROM video_items WHERE uri = :uri")
    suspend fun deleteVideoByUri(uri: String)

    @Query("DELETE FROM video_items WHERE uri IN (:uris)")
    suspend fun deleteVideosByUris(uris: List<String>)

    @Query("DELETE FROM video_items WHERE folderUri LIKE :prefix || '%'")
    suspend fun deleteVideosUnderFolderPrefix(prefix: String)

    @Query("UPDATE video_items SET isFavorite = :favorite WHERE uri = :uri")
    suspend fun setFavorite(uri: String, favorite: Boolean)

    @Query("UPDATE video_items SET lastPositionMs = :positionMs, durationMs = CASE WHEN :durationMs > 0 THEN :durationMs ELSE durationMs END, lastPlayedAt = :playedAt WHERE uri = :uri")
    suspend fun updatePlaybackProgress(uri: String, positionMs: Long, durationMs: Long, playedAt: Long)

    @Query("UPDATE video_items SET lastPositionMs = 0, lastPlayedAt = 0")
    suspend fun clearWatchHistory()

    // Playlists
    @Query(
        """
        SELECT p.id, p.name, p.createdAt, COUNT(e.videoUri) AS itemCount
        FROM playlists p
        LEFT JOIN playlist_entries e ON p.id = e.playlistId
        GROUP BY p.id
        ORDER BY p.createdAt DESC
        """
    )
    fun observePlaylistsWithCount(): Flow<List<PlaylistWithCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, newName: String)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPlaylistEntry(entry: PlaylistEntryEntity)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId AND videoUri = :videoUri")
    suspend fun removePlaylistEntry(playlistId: Long, videoUri: String)

    @Query(
        """
        SELECT v.* FROM video_items v
        INNER JOIN playlist_entries e ON v.uri = e.videoUri
        WHERE e.playlistId = :playlistId
        ORDER BY e.sortOrder ASC, e.addedAt ASC
        """
    )
    fun observeVideosInPlaylist(playlistId: Long): Flow<List<VideoItemEntity>>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun getNextPlaylistSortOrder(playlistId: Long): Int

    // Stream Bookmarks & History
    @Query("SELECT * FROM stream_bookmarks ORDER BY isPinned DESC, lastPlayedAt DESC")
    fun observeStreamBookmarks(): Flow<List<StreamBookmarkEntity>>

    @Query("SELECT * FROM stream_bookmarks WHERE url = :url LIMIT 1")
    suspend fun getStreamByUrl(url: String): StreamBookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertStreamBookmark(bookmark: StreamBookmarkEntity): Long

    @Update
    suspend fun updateStreamBookmark(bookmark: StreamBookmarkEntity)

    @Query("UPDATE stream_bookmarks SET lastPositionMs = :positionMs, lastPlayedAt = :playedAt WHERE url = :url")
    suspend fun updateStreamProgress(url: String, positionMs: Long, playedAt: Long)

    @Query("UPDATE stream_bookmarks SET isPinned = :pinned WHERE id = :id")
    suspend fun setStreamPinned(id: Long, pinned: Boolean)

    @Query("DELETE FROM stream_bookmarks WHERE id = :id")
    suspend fun deleteStreamBookmark(id: Long)
}
