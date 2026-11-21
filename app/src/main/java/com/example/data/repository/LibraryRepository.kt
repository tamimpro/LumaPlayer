package com.example.data.repository

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.documentfile.provider.DocumentFile
import com.example.data.local.LumaDao
import com.example.data.local.MountedFolderEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistEntryEntity
import com.example.data.local.PlaylistWithCount
import com.example.data.local.StreamBookmarkEntity
import com.example.data.local.VideoItemEntity
import com.example.data.model.FolderSummary
import com.example.data.model.StreamProtocol
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

sealed class FileOpResult {
    data class Success(val message: String) : FileOpResult()
    data class NeedsAllFilesAccess(val reason: String) : FileOpResult()
    data class NeedsMediaStoreDeleteIntent(val intentSender: IntentSender, val videoUri: String) : FileOpResult()
    data class Error(val message: String) : FileOpResult()
}

data class StorageAccessState(
    val hasAllFilesAccess: Boolean = false,
    val hasVideoReadPermission: Boolean = false
) {
    val canScanDevice: Boolean
        get() = hasAllFilesAccess || hasVideoReadPermission
}

class LibraryRepository(
    private val context: Context,
    private val dao: LumaDao
) {
    private val moshi: Moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val mapType = Types.newParameterizedType(Map::class.java, String::class.java, String::class.java)
    private val headersAdapter = moshi.adapter<Map<String, String>>(mapType)

    val mountedFolders: Flow<List<MountedFolderEntity>> = dao.observeMountedFolders()
    val allVideos: Flow<List<VideoItemEntity>> = dao.observeAllVideos()
    val favoriteVideos: Flow<List<VideoItemEntity>> = dao.observeFavoriteVideos()
    val watchHistory: Flow<List<VideoItemEntity>> = dao.observeWatchHistory()
    val playlists: Flow<List<PlaylistWithCount>> = dao.observePlaylistsWithCount()
    val streamBookmarks: Flow<List<StreamBookmarkEntity>> = dao.observeStreamBookmarks()

    val folderSummaries: Flow<List<FolderSummary>> = allVideos.map { videos ->
        videos.groupBy { it.folderUri }.map { (folderUri, items) ->
            val sorted = items.sortedByDescending { it.dateModified }
            FolderSummary(
                folderUri = folderUri,
                folderName = sorted.firstOrNull()?.folderName ?: "Videos",
                videoCount = items.size,
                totalSizeBytes = items.sumOf { it.sizeBytes },
                previewThumbnailUri = sorted.firstOrNull()?.uri,
                latestModified = sorted.maxOfOrNull { it.dateModified } ?: 0L
            )
        }.sortedByDescending { it.latestModified }
    }

    fun checkStorageAccess(): StorageAccessState {
        val allFiles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        val videoRead = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_VIDEO
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }

        return StorageAccessState(
            hasAllFilesAccess = allFiles,
            hasVideoReadPermission = videoRead
        )
    }

    fun observeVideosInFolder(folderUri: String): Flow<List<VideoItemEntity>> =
        dao.observeVideosInFolder(folderUri)

    fun observeVideosInPlaylist(playlistId: Long): Flow<List<VideoItemEntity>> =
        dao.observeVideosInPlaylist(playlistId)

    /**
     * Automatically scans all local device videos & folders using MediaStore + direct filesystem
     * traversal (when All Files Access is granted) so every folder, video, MKV/AVI/WebM, and sibling
     * subtitle appears seamlessly without manual folder mounting.
     */
    suspend fun scanDeviceStorage(): Int = withContext(Dispatchers.IO) {
        val access = checkStorageAccess()
        if (!access.canScanDevice) return@withContext 0

        val existingByUri = dao.getAllVideosOnce().associateBy { it.uri }
        val existingByPath = existingByUri.values
            .filter { it.filePath.isNotBlank() }
            .associateBy { it.filePath }

        val discoveredByPath = linkedMapOf<String, VideoItemEntity>()
        val discoveredByUri = linkedMapOf<String, VideoItemEntity>()

        // 1. Fast MediaStore Video Scan (works with both All Files Access and READ_MEDIA_VIDEO)
        scanMediaStoreVideos(existingByUri, existingByPath, discoveredByPath, discoveredByUri)

        // 2. Direct Filesystem Scan when All Files Access is granted (catches MKV/AVI/TS/folders & sibling subtitles)
        if (access.hasAllFilesAccess) {
            val roots = collectStorageRoots()
            for (root in roots) {
                scanDirectoryRecursively(
                    dir = root,
                    depth = 0,
                    existingByUri = existingByUri,
                    existingByPath = existingByPath,
                    discoveredByPath = discoveredByPath,
                    discoveredByUri = discoveredByUri
                )
            }
        }

        // Also scan any optional manually mounted SAF trees (e.g. external OTG drives)
        val mounted = dao.getMountedFoldersOnce()
        for (folder in mounted) {
            val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(folder.treeUri)) ?: continue
            scanDocumentTree(rootDoc, folder.treeUri, folder.displayName)
        }

        val allDiscovered = discoveredByUri.values.toList()
        if (allDiscovered.isNotEmpty()) {
            dao.upsertVideos(allDiscovered)
        }

        // Reconcile stale local device entries whose files were deleted/moved outside the app
        val staleUris = existingByUri.values
            .filter { existing ->
                val isDeviceManaged = existing.filePath.isNotBlank() ||
                    existing.uri.startsWith("file://") ||
                    existing.uri.startsWith("content://media/")
                if (!isDeviceManaged) return@filter false
                val stillFound = discoveredByUri.containsKey(existing.uri) ||
                    (existing.filePath.isNotBlank() && discoveredByPath.containsKey(existing.filePath))
                !stillFound
            }
            .map { it.uri }

        if (staleUris.isNotEmpty()) {
            dao.deleteVideosByUris(staleUris)
        }

        allDiscovered.size
    }

    private fun scanMediaStoreVideos(
        existingByUri: Map<String, VideoItemEntity>,
        existingByPath: Map<String, VideoItemEntity>,
        discoveredByPath: MutableMap<String, VideoItemEntity>,
        discoveredByUri: MutableMap<String, VideoItemEntity>
    ) {
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATA,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.BUCKET_ID,
            MediaStore.Video.Media.BUCKET_DISPLAY_NAME
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_MODIFIED} DESC"
        runCatching {
            context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val durCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val wCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val hCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val modCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndex(MediaStore.Video.Media.MIME_TYPE)
                val bucketIdCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_ID)
                val bucketNameCol = cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    val uriStr = contentUri.toString()
                    val path = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
                    val fileObj = path.takeIf { it.isNotBlank() }?.let { File(it) }
                    if (fileObj != null && !fileObj.exists()) continue

                    val displayName = cursor.getString(nameCol) ?: fileObj?.name ?: "Video"
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else (fileObj?.length() ?: 0L)
                    val duration = if (durCol >= 0) cursor.getLong(durCol) else 0L
                    val width = if (wCol >= 0) cursor.getInt(wCol) else 0
                    val height = if (hCol >= 0) cursor.getInt(hCol) else 0
                    val modifiedSec = if (modCol >= 0) cursor.getLong(modCol) else 0L
                    val modifiedMs = if (modifiedSec > 0L) modifiedSec * 1000L else (fileObj?.lastModified() ?: System.currentTimeMillis())
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: guessMimeType(displayName) else guessMimeType(displayName)

                    val parentDir = fileObj?.parentFile
                    val folderPath = parentDir?.absolutePath
                        ?: (if (bucketIdCol >= 0) "bucket://${cursor.getString(bucketIdCol)}" else "bucket://videos")
                    val folderName = parentDir?.name?.takeIf { it.isNotBlank() }
                        ?: (if (bucketNameCol >= 0) cursor.getString(bucketNameCol) else null)
                        ?: "Internal Storage"

                    val existing = existingByUri[uriStr] ?: existingByPath[path]
                    val siblingSub = fileObj?.let { findSiblingSubtitleFile(it) } ?: existing?.siblingSubtitleUri

                    val entity = VideoItemEntity(
                        uri = uriStr,
                        filePath = path,
                        folderUri = folderPath,
                        folderName = folderName,
                        title = displayName.substringBeforeLast('.').ifBlank { displayName },
                        mimeType = mime,
                        sizeBytes = size,
                        durationMs = if (duration > 0L) duration else (existing?.durationMs ?: 0L),
                        width = if (width > 0) width else (existing?.width ?: 0),
                        height = if (height > 0) height else (existing?.height ?: 0),
                        frameRate = existing?.frameRate ?: 0f,
                        hdrTag = existing?.hdrTag,
                        videoCodec = existing?.videoCodec?.ifBlank { mime.substringAfter('/').uppercase() }
                            ?: mime.substringAfter('/').uppercase(),
                        siblingSubtitleUri = siblingSub,
                        dateModified = modifiedMs,
                        isFavorite = existing?.isFavorite ?: false,
                        lastPositionMs = existing?.lastPositionMs ?: 0L,
                        lastPlayedAt = existing?.lastPlayedAt ?: 0L
                    )

                    discoveredByUri[uriStr] = entity
                    if (path.isNotBlank()) {
                        discoveredByPath[path] = entity
                    }
                }
            }
        }
    }

    private fun scanDirectoryRecursively(
        dir: File,
        depth: Int,
        existingByUri: Map<String, VideoItemEntity>,
        existingByPath: Map<String, VideoItemEntity>,
        discoveredByPath: MutableMap<String, VideoItemEntity>,
        discoveredByUri: MutableMap<String, VideoItemEntity>
    ) {
        if (depth > 8 || !dir.isDirectory || !dir.canRead()) return
        val name = dir.name
        if (depth > 0 && (name.startsWith(".") || name == "Android" || name == "LOST.DIR")) return

        val children = dir.listFiles() ?: return
        // Respect .nomedia in deep subdirs if needed? Power users often want to see their video folders,
        // so we skip only system Android/data & hidden dot folders.
        for (child in children) {
            if (child.isDirectory) {
                scanDirectoryRecursively(
                    dir = child,
                    depth = depth + 1,
                    existingByUri = existingByUri,
                    existingByPath = existingByPath,
                    discoveredByPath = discoveredByPath,
                    discoveredByUri = discoveredByUri
                )
            } else if (child.isFile && isVideoFileName(child.name)) {
                val path = child.absolutePath
                if (discoveredByPath.containsKey(path)) continue

                val fileUri = Uri.fromFile(child)
                val uriStr = fileUri.toString()
                val existing = existingByPath[path] ?: existingByUri[uriStr]
                val siblingSub = findSiblingSubtitleFile(child) ?: existing?.siblingSubtitleUri

                val entity = extractVideoMetadata(
                    uri = fileUri,
                    filePath = path,
                    folderUri = dir.absolutePath,
                    folderName = dir.name.ifBlank { "Internal Storage" },
                    fallbackName = child.name,
                    fallbackSize = child.length(),
                    fallbackMime = guessMimeType(child.name),
                    fallbackModified = child.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
                    siblingSubtitleUri = siblingSub,
                    existing = existing
                )
                if (entity != null) {
                    discoveredByPath[path] = entity
                    discoveredByUri[entity.uri] = entity
                }
            }
        }
    }

    private fun collectStorageRoots(): List<File> {
        val roots = linkedSetOf<File>()
        runCatching {
            val primary = Environment.getExternalStorageDirectory()
            if (primary != null && primary.exists() && primary.canRead()) {
                roots.add(primary)
            }
        }
        // Also detect removable SD cards via externalFilesDirs parent resolution
        runCatching {
            context.getExternalFilesDirs(null).forEach { dir ->
                val path = dir?.absolutePath ?: return@forEach
                val idx = path.indexOf("/Android/data")
                if (idx > 0) {
                    val root = File(path.substring(0, idx))
                    if (root.exists() && root.canRead()) {
                        roots.add(root)
                    }
                }
            }
        }
        return roots.toList()
    }

    private fun findSiblingSubtitleFile(videoFile: File): String? {
        val parent = videoFile.parentFile ?: return null
        val base = videoFile.nameWithoutExtension
        for (ext in SUBTITLE_EXTENSIONS) {
            val candidate = File(parent, "$base.$ext")
            if (candidate.exists() && candidate.isFile) {
                return Uri.fromFile(candidate).toString()
            }
        }
        return null
    }

    /**
     * Seamless file deletion directly from device storage.
     */
    suspend fun deleteVideoFile(video: VideoItemEntity): FileOpResult = withContext(Dispatchers.IO) {
        val access = checkStorageAccess()

        // 1. Direct File deletion when filePath is known and All Files Access is granted
        if (video.filePath.isNotBlank()) {
            val file = File(video.filePath)
            if (file.exists()) {
                if (file.delete()) {
                    deleteFromMediaStoreByPathOrUri(video.uri, video.filePath)
                    dao.deleteVideoByUri(video.uri)
                    return@withContext FileOpResult.Success("Deleted \"${video.title}\" from device")
                }
            } else {
                // Already absent on disk; clean up MediaStore & Room
                deleteFromMediaStoreByPathOrUri(video.uri, video.filePath)
                dao.deleteVideoByUri(video.uri)
                return@withContext FileOpResult.Success("Removed \"${video.title}\"")
            }
        }

        // 2. Content URI deletion (MediaStore or SAF)
        val uri = Uri.parse(video.uri)
        if (uri.scheme == "content") {
            val deleted = runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
            if (deleted) {
                dao.deleteVideoByUri(video.uri)
                return@withContext FileOpResult.Success("Deleted \"${video.title}\"")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && video.uri.startsWith("content://media/")) {
                val pendingIntent = runCatching {
                    MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
                }.getOrNull()
                if (pendingIntent != null) {
                    return@withContext FileOpResult.NeedsMediaStoreDeleteIntent(pendingIntent.intentSender, video.uri)
                }
            }
        }

        if (!access.hasAllFilesAccess) {
            FileOpResult.NeedsAllFilesAccess("Grant All Files Access to delete videos seamlessly without extra prompts.")
        } else {
            FileOpResult.Error("Unable to delete \"${video.title}\" on this storage volume.")
        }
    }

    /**
     * Seamless physical file move to another directory on device storage (moving sibling subtitles too).
     */
    suspend fun moveVideoFile(video: VideoItemEntity, targetDirPath: String): FileOpResult = withContext(Dispatchers.IO) {
        val access = checkStorageAccess()
        if (!access.hasAllFilesAccess) {
            return@withContext FileOpResult.NeedsAllFilesAccess(
                "Grant All Files Access to move videos between folders seamlessly."
            )
        }

        val sourcePath = video.filePath
        if (sourcePath.isBlank()) {
            return@withContext FileOpResult.Error("Direct file path unavailable for this item.")
        }
        val sourceFile = File(sourcePath)
        if (!sourceFile.exists()) {
            return@withContext FileOpResult.Error("Source file no longer exists on disk.")
        }

        val targetDir = File(targetDirPath)
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            return@withContext FileOpResult.Error("Could not create destination folder.")
        }
        if (sourceFile.parentFile?.absolutePath == targetDir.absolutePath) {
            return@withContext FileOpResult.Error("Video is already in ${targetDir.name}.")
        }

        val destFile = resolveNonCollidingFile(targetDir, sourceFile.name)
        val moved = moveFileAtomicallyOrCopy(sourceFile, destFile)
        if (!moved) {
            return@withContext FileOpResult.Error("Failed to move \"${video.title}\".")
        }

        // Move matching sibling subtitle if present
        val newSiblingSub = moveSiblingSubtitle(sourceFile, destFile)

        // Refresh MediaStore & Room preserving favourites and exact resume timestamp
        deleteFromMediaStoreByPathOrUri(video.uri, sourceFile.absolutePath)
        MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)

        val updated = video.copy(
            uri = Uri.fromFile(destFile).toString(),
            filePath = destFile.absolutePath,
            folderUri = targetDir.absolutePath,
            folderName = targetDir.name.ifBlank { "Internal Storage" },
            title = destFile.nameWithoutExtension,
            siblingSubtitleUri = newSiblingSub ?: video.siblingSubtitleUri,
            dateModified = System.currentTimeMillis()
        )
        dao.deleteVideoByUri(video.uri)
        dao.upsertVideo(updated)
        FileOpResult.Success("Moved to ${targetDir.name}")
    }

    /**
     * Seamless physical file rename on device storage (renaming sibling subtitles too).
     */
    suspend fun renameVideoFile(video: VideoItemEntity, newBaseName: String): FileOpResult = withContext(Dispatchers.IO) {
        val access = checkStorageAccess()
        if (!access.hasAllFilesAccess) {
            return@withContext FileOpResult.NeedsAllFilesAccess(
                "Grant All Files Access to rename video files seamlessly."
            )
        }

        val sanitized = newBaseName.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (sanitized.isBlank()) {
            return@withContext FileOpResult.Error("Enter a valid file name.")
        }

        val sourcePath = video.filePath
        if (sourcePath.isBlank()) {
            return@withContext FileOpResult.Error("Direct file path unavailable for this item.")
        }
        val sourceFile = File(sourcePath)
        val parentDir = sourceFile.parentFile
        if (!sourceFile.exists() || parentDir == null) {
            return@withContext FileOpResult.Error("Source file no longer exists on disk.")
        }

        val ext = sourceFile.extension
        val targetName = if (ext.isNotBlank()) "$sanitized.$ext" else sanitized
        val destFile = File(parentDir, targetName)
        if (destFile.exists()) {
            return@withContext FileOpResult.Error("A file named \"$targetName\" already exists.")
        }

        if (!sourceFile.renameTo(destFile)) {
            return@withContext FileOpResult.Error("Failed to rename file.")
        }

        val newSiblingSub = moveSiblingSubtitle(sourceFile, destFile)

        deleteFromMediaStoreByPathOrUri(video.uri, sourceFile.absolutePath)
        MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)

        val updated = video.copy(
            uri = Uri.fromFile(destFile).toString(),
            filePath = destFile.absolutePath,
            title = destFile.nameWithoutExtension,
            siblingSubtitleUri = newSiblingSub ?: video.siblingSubtitleUri,
            dateModified = System.currentTimeMillis()
        )
        dao.deleteVideoByUri(video.uri)
        dao.upsertVideo(updated)
        FileOpResult.Success("Renamed to \"${destFile.nameWithoutExtension}\"")
    }

    fun getSuggestedMoveDestinations(existingFolderUris: List<String>): List<File> {
        val result = linkedMapOf<String, File>()
        existingFolderUris.forEach { path ->
            if (path.startsWith("/")) {
                val f = File(path)
                if (f.exists() && f.isDirectory) {
                    result[f.absolutePath] = f
                }
            }
        }
        val primary = Environment.getExternalStorageDirectory()
        if (primary != null && primary.exists()) {
            listOf("Movies", "Download", "DCIM", "Documents").forEach { sub ->
                val dir = File(primary, sub)
                result.putIfAbsent(dir.absolutePath, dir)
            }
        }
        return result.values.sortedBy { it.name.lowercase() }
    }

    private fun moveFileAtomicallyOrCopy(source: File, dest: File): Boolean {
        if (source.renameTo(dest)) return true
        return runCatching {
            source.inputStream().use { input ->
                dest.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 256 * 1024)
                }
            }
            if (dest.exists() && dest.length() == source.length()) {
                source.delete()
                true
            } else {
                dest.delete()
                false
            }
        }.getOrDefault(false)
    }

    private fun moveSiblingSubtitle(oldVideo: File, newVideo: File): String? {
        val oldParent = oldVideo.parentFile ?: return null
        val newParent = newVideo.parentFile ?: return null
        val oldBase = oldVideo.nameWithoutExtension
        val newBase = newVideo.nameWithoutExtension
        for (ext in SUBTITLE_EXTENSIONS) {
            val oldSub = File(oldParent, "$oldBase.$ext")
            if (oldSub.exists() && oldSub.isFile) {
                val newSub = File(newParent, "$newBase.$ext")
                if (moveFileAtomicallyOrCopy(oldSub, newSub)) {
                    return Uri.fromFile(newSub).toString()
                }
            }
        }
        return null
    }

    private fun resolveNonCollidingFile(dir: File, fileName: String): File {
        var candidate = File(dir, fileName)
        if (!candidate.exists()) return candidate
        val base = fileName.substringBeforeLast('.', fileName)
        val ext = fileName.substringAfterLast('.', "")
        var idx = 1
        while (candidate.exists()) {
            val nextName = if (ext.isNotBlank()) "${base}_$idx.$ext" else "${base}_$idx"
            candidate = File(dir, nextName)
            idx++
        }
        return candidate
    }

    private fun deleteFromMediaStoreByPathOrUri(uriStr: String, filePath: String) {
        runCatching {
            if (uriStr.startsWith("content://media/")) {
                context.contentResolver.delete(Uri.parse(uriStr), null, null)
            }
        }
        if (filePath.isNotBlank()) {
            runCatching {
                context.contentResolver.delete(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Video.Media.DATA} = ?",
                    arrayOf(filePath)
                )
            }
        }
    }

    suspend fun mountAndScanFolder(treeUri: Uri): Int = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
        }

        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return@withContext 0
        val rootName = rootDoc.name?.takeIf { it.isNotBlank() } ?: "External Folder"
        dao.upsertMountedFolder(
            MountedFolderEntity(
                treeUri = treeUri.toString(),
                displayName = rootName,
                lastScannedAt = System.currentTimeMillis()
            )
        )
        scanDocumentTree(rootDoc, treeUri.toString(), rootName)
    }

    suspend fun unmountFolder(treeUri: String) = withContext(Dispatchers.IO) {
        dao.deleteMountedFolder(treeUri)
        dao.deleteVideosUnderFolderPrefix(treeUri)
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                Uri.parse(treeUri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    suspend fun importPickedVideos(uris: List<Uri>, folderName: String = "Imported Videos"): Int =
        withContext(Dispatchers.IO) {
            var count = 0
            val imported = mutableListOf<VideoItemEntity>()
            for (uri in uris) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
                val existing = dao.getVideoByUri(uri.toString())
                val entity = extractVideoMetadata(
                    uri = uri,
                    filePath = "",
                    folderUri = "luma://imported",
                    folderName = folderName,
                    fallbackName = queryDisplayName(uri),
                    fallbackSize = queryFileSize(uri),
                    fallbackMime = context.contentResolver.getType(uri) ?: "video/*",
                    fallbackModified = System.currentTimeMillis(),
                    siblingSubtitleUri = existing?.siblingSubtitleUri,
                    existing = existing
                )
                if (entity != null) {
                    imported.add(entity)
                    count++
                }
            }
            if (imported.isNotEmpty()) {
                dao.upsertVideos(imported)
            }
            count
        }

    private suspend fun scanDocumentTree(
        dir: DocumentFile,
        rootTreeUri: String,
        folderDisplayName: String,
        depth: Int = 0
    ): Int {
        if (depth > 8 || !dir.isDirectory) return 0
        val children = runCatching { dir.listFiles() }.getOrElse { emptyArray() }
        if (children.isEmpty()) return 0

        val subtitleMap = mutableMapOf<String, String>()
        for (child in children) {
            if (child.isFile) {
                val name = child.name ?: continue
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in SUBTITLE_EXTENSIONS) {
                    subtitleMap[name.substringBeforeLast('.').lowercase()] = child.uri.toString()
                }
            }
        }

        val foundVideos = mutableListOf<VideoItemEntity>()
        var count = 0
        val currentFolderUri = if (depth == 0) rootTreeUri else dir.uri.toString()
        val currentFolderName = if (depth == 0) folderDisplayName else (dir.name ?: folderDisplayName)

        for (child in children) {
            if (child.isDirectory) {
                val name = child.name ?: ""
                if (!name.startsWith(".") && name != "Android") {
                    count += scanDocumentTree(child, rootTreeUri, name, depth + 1)
                }
            } else if (child.isFile && isVideoFile(child)) {
                val fileName = child.name ?: "Untitled Video"
                val baseName = fileName.substringBeforeLast('.').lowercase()
                val existing = dao.getVideoByUri(child.uri.toString())
                val entity = extractVideoMetadata(
                    uri = child.uri,
                    filePath = "",
                    folderUri = currentFolderUri,
                    folderName = currentFolderName,
                    fallbackName = fileName,
                    fallbackSize = child.length(),
                    fallbackMime = child.type ?: guessMimeType(fileName),
                    fallbackModified = child.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
                    siblingSubtitleUri = subtitleMap[baseName] ?: existing?.siblingSubtitleUri,
                    existing = existing
                )
                if (entity != null) {
                    foundVideos.add(entity)
                    count++
                }
            }
        }

        if (foundVideos.isNotEmpty()) {
            dao.upsertVideos(foundVideos)
        }
        return count
    }

    private fun extractVideoMetadata(
        uri: Uri,
        filePath: String,
        folderUri: String,
        folderName: String,
        fallbackName: String,
        fallbackSize: Long,
        fallbackMime: String,
        fallbackModified: Long,
        siblingSubtitleUri: String?,
        existing: VideoItemEntity?
    ): VideoItemEntity? {
        if (existing != null && existing.sizeBytes == fallbackSize && existing.durationMs > 0L) {
            return existing.copy(
                filePath = filePath.ifBlank { existing.filePath },
                folderUri = folderUri,
                folderName = folderName,
                siblingSubtitleUri = siblingSubtitleUri ?: existing.siblingSubtitleUri
            )
        }

        var durationMs = existing?.durationMs ?: 0L
        var width = existing?.width ?: 0
        var height = existing?.height ?: 0
        var frameRate = existing?.frameRate ?: 0f
        var hdrTag = existing?.hdrTag
        var codec = existing?.videoCodec ?: ""

        val retriever = MediaMetadataRetriever()
        try {
            if (filePath.isNotBlank()) {
                retriever.setDataSource(filePath)
            } else {
                retriever.setDataSource(context, uri)
            }
            durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: durationMs
            val rawWidth = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val rawHeight = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) {
                width = rawHeight
                height = rawWidth
            } else {
                width = rawWidth
                height = rawHeight
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val frameCount = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)
                    ?.toFloatOrNull()
                if (frameCount != null && durationMs > 0L) {
                    val computedFps = (frameCount * 1000f) / durationMs.toFloat()
                    if (computedFps in 10f..240f) {
                        frameRate = computedFps
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val transfer = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)
                    ?.toIntOrNull()
                hdrTag = when (transfer) {
                    MediaFormat.COLOR_TRANSFER_ST2084 -> "HDR10"
                    MediaFormat.COLOR_TRANSFER_HLG -> "HLG"
                    else -> hdrTag
                }
            }
            val detectedMime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            if (!detectedMime.isNullOrBlank()) {
                codec = detectedMime.substringAfter('/').uppercase()
            }
        } catch (_: Exception) {
        } finally {
            runCatching { retriever.release() }
        }

        val cleanTitle = fallbackName.substringBeforeLast('.').ifBlank { fallbackName }
        return VideoItemEntity(
            uri = uri.toString(),
            filePath = filePath,
            folderUri = folderUri,
            folderName = folderName,
            title = cleanTitle,
            mimeType = fallbackMime,
            sizeBytes = fallbackSize,
            durationMs = durationMs,
            width = width,
            height = height,
            frameRate = frameRate,
            hdrTag = hdrTag,
            videoCodec = codec,
            siblingSubtitleUri = siblingSubtitleUri,
            dateModified = fallbackModified,
            isFavorite = existing?.isFavorite ?: false,
            lastPositionMs = existing?.lastPositionMs ?: 0L,
            lastPlayedAt = existing?.lastPlayedAt ?: 0L
        )
    }

    suspend fun toggleFavorite(uri: String, isFavorite: Boolean) {
        dao.setFavorite(uri, isFavorite)
    }

    suspend fun deleteVideoEntry(uri: String) {
        dao.deleteVideoByUri(uri)
    }

    suspend fun savePlaybackProgress(
        uri: String,
        title: String,
        positionMs: Long,
        durationMs: Long,
        isStream: Boolean
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val safePosition = if (durationMs > 10_000L && positionMs >= durationMs - 4_000L) 0L else positionMs.coerceAtLeast(0L)
        if (isStream) {
            val existing = dao.getStreamByUrl(uri)
            if (existing != null) {
                dao.updateStreamProgress(uri, safePosition, now)
            } else {
                dao.upsertStreamBookmark(
                    StreamBookmarkEntity(
                        title = title,
                        url = uri,
                        lastPositionMs = safePosition,
                        lastPlayedAt = now
                    )
                )
            }
        } else {
            val existing = dao.getVideoByUri(uri)
            if (existing != null) {
                dao.updatePlaybackProgress(uri, safePosition, durationMs, now)
            } else {
                dao.upsertVideo(
                    VideoItemEntity(
                        uri = uri,
                        folderUri = "luma://external",
                        folderName = "External Files",
                        title = title,
                        mimeType = "video/*",
                        durationMs = durationMs,
                        lastPositionMs = safePosition,
                        lastPlayedAt = now
                    )
                )
            }
        }
    }

    suspend fun clearWatchHistory() {
        dao.clearWatchHistory()
    }

    suspend fun createPlaylist(name: String): Long =
        dao.insertPlaylist(PlaylistEntity(name = name.trim()))

    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        dao.renamePlaylist(playlistId, newName.trim())
    }

    suspend fun deletePlaylist(playlistId: Long) {
        dao.deletePlaylist(playlistId)
    }

    suspend fun addVideoToPlaylist(playlistId: Long, videoUri: String) {
        val nextOrder = dao.getNextPlaylistSortOrder(playlistId)
        dao.addPlaylistEntry(
            PlaylistEntryEntity(
                playlistId = playlistId,
                videoUri = videoUri,
                sortOrder = nextOrder
            )
        )
    }

    suspend fun removeVideoFromPlaylist(playlistId: Long, videoUri: String) {
        dao.removePlaylistEntry(playlistId, videoUri)
    }

    suspend fun saveOrUpdateStreamBookmark(
        title: String,
        url: String,
        protocol: StreamProtocol,
        headers: Map<String, String>,
        isPinned: Boolean = false
    ): StreamBookmarkEntity = withContext(Dispatchers.IO) {
        val existing = dao.getStreamByUrl(url)
        val json = serializeHeaders(headers)
        val cleanTitle = title.trim().ifBlank { deriveTitleFromUrl(url) }
        val entity = if (existing != null) {
            existing.copy(
                title = cleanTitle,
                protocol = protocol.name,
                headersJson = json,
                isPinned = isPinned || existing.isPinned,
                lastPlayedAt = System.currentTimeMillis()
            )
        } else {
            StreamBookmarkEntity(
                title = cleanTitle,
                url = url.trim(),
                protocol = protocol.name,
                headersJson = json,
                isPinned = isPinned,
                lastPlayedAt = System.currentTimeMillis()
            )
        }
        val id = dao.upsertStreamBookmark(entity)
        entity.copy(id = if (entity.id == 0L) id else entity.id)
    }

    suspend fun toggleStreamPinned(id: Long, pinned: Boolean) {
        dao.setStreamPinned(id, pinned)
    }

    suspend fun deleteStreamBookmark(id: Long) {
        dao.deleteStreamBookmark(id)
    }

    fun serializeHeaders(headers: Map<String, String>): String {
        val filtered = headers.filterKeys { it.isNotBlank() }.filterValues { it.isNotBlank() }
        return runCatching { headersAdapter.toJson(filtered) }.getOrDefault("{}")
    }

    fun parseHeaders(json: String): Map<String, String> {
        return runCatching { headersAdapter.fromJson(json).orEmpty() }.getOrDefault(emptyMap())
    }

    private fun isVideoFile(file: DocumentFile): Boolean {
        val mime = file.type.orEmpty().lowercase()
        if (mime.startsWith("video/")) return true
        return isVideoFileName(file.name.orEmpty())
    }

    private fun isVideoFileName(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in VIDEO_EXTENSIONS
    }

    private fun guessMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "mp4", "m4v" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "ts", "m2ts" -> "video/mp2t"
            "3gp" -> "video/3gpp"
            else -> "video/*"
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx) ?: "Video"
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Video"
    }

    private fun queryFileSize(uri: Uri): Long {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0) return cursor.getLong(idx)
                }
            }
        return 0L
    }

    private fun deriveTitleFromUrl(url: String): String {
        val parsed = Uri.parse(url)
        val segment = parsed.lastPathSegment?.substringBefore('?')?.takeIf { it.isNotBlank() }
        return segment ?: parsed.host ?: "Network Stream"
    }

    companion object {
        private val VIDEO_EXTENSIONS = setOf(
            "mp4", "mkv", "avi", "mov", "webm", "m4v", "ts", "m2ts", "mts", "3gp", "flv", "wmv", "ogv", "mpg", "mpeg"
        )
        private val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa", "ttml")
    }
}
