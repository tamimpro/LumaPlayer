package com.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.data.local.VideoItemEntity
import com.example.data.model.AppThemeMode
import com.example.player.PlayerActivity
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.DeleteVideoConfirmDialog
import com.example.ui.components.LumaBrandMark
import com.example.ui.components.MoveVideoDialog
import com.example.ui.components.RenameVideoDialog
import com.example.ui.components.StorageAccessPromptDialog
import com.example.ui.components.VideoInfoDialog
import com.example.ui.screens.CollectionDetailScreen
import com.example.ui.screens.FoldersScreen
import com.example.ui.screens.PlaylistNameDialog
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.StreamScreen
import com.example.ui.screens.VideosScreen
import com.example.ui.theme.LumaPlayerTheme
import com.example.ui.viewmodel.MainViewModel

private const val ROUTE_FOLDERS = "folders"
private const val ROUTE_VIDEOS = "videos"
private const val ROUTE_PLAYLISTS = "playlists"
private const val ROUTE_STREAM = "stream"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_FOLDER_DETAIL = "folder_detail/{folderUri}/{folderName}"
private const val ROUTE_PLAYLIST_DETAIL = "playlist_detail/{playlistId}/{playlistName}"
private const val ROUTE_FAVORITES_DETAIL = "favorites_detail"
private const val ROUTE_HISTORY_DETAIL = "history_detail"

private data class LumaNavTab(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val MAIN_TABS = listOf(
    LumaNavTab(ROUTE_FOLDERS, "Folders", Icons.Rounded.Folder, Icons.Outlined.Folder, "tab_folders"),
    LumaNavTab(ROUTE_VIDEOS, "Videos", Icons.Rounded.Movie, Icons.Outlined.Movie, "tab_videos"),
    LumaNavTab(ROUTE_PLAYLISTS, "Playlists", Icons.AutoMirrored.Rounded.PlaylistPlay, Icons.AutoMirrored.Rounded.PlaylistPlay, "tab_playlists"),
    LumaNavTab(ROUTE_STREAM, "Stream", Icons.Rounded.WifiTethering, Icons.Outlined.WifiTethering, "tab_stream")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            LumaPlayerTheme(themeMode = settings.themeMode) {
                LumaMainApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LumaMainApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: ROUTE_FOLDERS

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val storageAccess by viewModel.storageAccess.collectAsStateWithLifecycle()
    val folders by viewModel.folderSummaries.collectAsStateWithLifecycle()
    val filteredVideos by viewModel.filteredSortedVideos.collectAsStateWithLifecycle()
    val favoriteVideos by viewModel.favoriteVideos.collectAsStateWithLifecycle()
    val watchHistory by viewModel.watchHistory.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val streamBookmarks by viewModel.streamBookmarks.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val favoritesOnly by viewModel.favoritesOnly.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val allFilesPromptReason by viewModel.allFilesPromptReason.collectAsStateWithLifecycle()
    val pendingMediaStoreDelete by viewModel.pendingMediaStoreDelete.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    var playlistPickerTarget by remember { mutableStateOf<VideoItemEntity?>(null) }
    var videoInfoTarget by remember { mutableStateOf<VideoItemEntity?>(null) }
    var moveTarget by remember { mutableStateOf<VideoItemEntity?>(null) }
    var renameTarget by remember { mutableStateOf<VideoItemEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<VideoItemEntity?>(null) }
    var showQuickCreatePlaylist by remember { mutableStateOf(false) }
    var startupPermissionPromptDismissed by rememberSaveable { mutableStateOf(false) }

    // Automatically refresh permission state & scan local videos whenever returning from Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshStorageAccessAndScan(silent = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(bannerMessage) {
        bannerMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    // Standard Video Media Permission Launcher (READ_MEDIA_VIDEO on API 33+, READ_EXTERNAL_STORAGE on API <= 32)
    val videoPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.refreshStorageAccessAndScan(silent = false)
        } else {
            viewModel.refreshStorageAccessAndScan(silent = true)
        }
    }

    // Legacy Write Storage permission for Android 9/10 devices
    val legacyWritePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshStorageAccessAndScan(silent = false)
    }

    // MediaStore OS Delete confirmation fallback (when user only granted READ_MEDIA_VIDEO)
    val mediaStoreDeleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val pending = pendingMediaStoreDelete
        if (result.resultCode == Activity.RESULT_OK && pending != null) {
            viewModel.onMediaStoreDeleteConfirmed(pending.second)
        } else {
            viewModel.clearPendingMediaStoreDelete()
        }
    }

    LaunchedEffect(pendingMediaStoreDelete) {
        val pending = pendingMediaStoreDelete ?: return@LaunchedEffect
        runCatching {
            mediaStoreDeleteLauncher.launch(IntentSenderRequest.Builder(pending.first).build())
        }.onFailure {
            viewModel.clearPendingMediaStoreDelete()
        }
    }

    fun requestAllFilesAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val appIntent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            val launched = runCatching {
                context.startActivity(appIntent)
                true
            }.getOrDefault(false)
            if (!launched) {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
        } else {
            legacyWritePermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    fun requestVideoReadAccess() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        videoPermissionLauncher.launch(permission)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    fun ensureNotificationPermissionIfNeeded(background: Boolean) {
        if (background && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun launchVideos(list: List<VideoItemEntity>, index: Int, inBackground: Boolean) {
        ensureNotificationPermissionIfNeeded(inBackground)
        val req = viewModel.buildPlaybackRequest(list, index, inBackground)
        PlayerActivity.launch(context, req)
    }

    val isTopLevelTab = MAIN_TABS.any { it.route == currentRoute }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Row(modifier = Modifier.fillMaxSize()) {
            if (useNavigationRail && isTopLevelTab) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxHeight()
                ) {
                    MAIN_TABS.forEach { tab ->
                        val selected = currentRoute == tab.route
                        NavigationRailItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(tab.route) {
                                        popUpTo(ROUTE_FOLDERS) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                    contentDescription = tab.label
                                )
                            },
                            label = { Text(tab.label) },
                            modifier = Modifier.testTag(tab.testTag)
                        )
                    }
                }
            }

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    if (isTopLevelTab) {
                        TopAppBar(
                            title = { LumaBrandMark() },
                            actions = {
                                IconButton(
                                    onClick = { viewModel.cycleThemeQuick() },
                                    modifier = Modifier.testTag("theme_quick_toggle")
                                ) {
                                    Icon(
                                        imageVector = if (settings.themeMode == AppThemeMode.LIGHT) {
                                            Icons.Outlined.DarkMode
                                        } else {
                                            Icons.Outlined.LightMode
                                        },
                                        contentDescription = "Switch theme"
                                    )
                                }
                                IconButton(
                                    onClick = { navController.navigate(ROUTE_SETTINGS) },
                                    modifier = Modifier.testTag("open_settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = "Settings"
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background
                            )
                        )
                    }
                },
                bottomBar = {
                    if (!useNavigationRail && isTopLevelTab) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            windowInsets = WindowInsets.navigationBars,
                            tonalElevation = 0.dp
                        ) {
                            MAIN_TABS.forEach { tab ->
                                val selected = currentRoute == tab.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        if (!selected) {
                                            navController.navigate(tab.route) {
                                                popUpTo(ROUTE_FOLDERS) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = ROUTE_FOLDERS,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 840.dp)
                    ) {
                        composable(ROUTE_FOLDERS) {
                            FoldersScreen(
                                folders = folders,
                                continueWatching = watchHistory,
                                storageAccess = storageAccess,
                                isScanning = isScanning,
                                onOpenFolder = { folder ->
                                    val encodedUri = Uri.encode(folder.folderUri)
                                    val encodedName = Uri.encode(folder.folderName)
                                    navController.navigate("folder_detail/$encodedUri/$encodedName")
                                },
                                onResumeVideo = { list, index -> launchVideos(list, index, false) },
                                onRequestAllFilesAccess = { requestAllFilesAccess() },
                                onRequestVideoAccess = { requestVideoReadAccess() },
                                onRescanDevice = { viewModel.scanDeviceLibrary(silent = false) },
                                onOpenStreamTab = { navController.navigate(ROUTE_STREAM) }
                            )
                        }

                        composable(ROUTE_VIDEOS) {
                            VideosScreen(
                                videos = filteredVideos,
                                storageAccess = storageAccess,
                                searchQuery = searchQuery,
                                favoritesOnly = favoritesOnly,
                                sortOption = settings.videoSortOption,
                                onSearchQueryChange = viewModel::setSearchQuery,
                                onToggleFavoritesOnly = viewModel::toggleFavoritesFilter,
                                onSelectSort = viewModel::setSortOption,
                                onPlayVideo = { list, idx, bg -> launchVideos(list, idx, bg) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onAddToPlaylist = { playlistPickerTarget = it },
                                onShowInfo = { videoInfoTarget = it },
                                onMoveVideo = { moveTarget = it },
                                onRenameVideo = { renameTarget = it },
                                onDeleteVideo = { deleteTarget = it },
                                onRequestAllFilesAccess = { requestAllFilesAccess() },
                                onRequestVideoAccess = { requestVideoReadAccess() },
                                onRescanDevice = { viewModel.scanDeviceLibrary(silent = false) },
                                onOpenStreamTab = { navController.navigate(ROUTE_STREAM) }
                            )
                        }

                        composable(ROUTE_PLAYLISTS) {
                            PlaylistsScreen(
                                playlists = playlists,
                                favoritesCount = favoriteVideos.size,
                                historyCount = watchHistory.size,
                                onOpenFavorites = { navController.navigate(ROUTE_FAVORITES_DETAIL) },
                                onOpenHistory = { navController.navigate(ROUTE_HISTORY_DETAIL) },
                                onOpenPlaylist = { playlist ->
                                    val encodedName = Uri.encode(playlist.name)
                                    navController.navigate("playlist_detail/${playlist.id}/$encodedName")
                                },
                                onCreatePlaylist = viewModel::createPlaylist,
                                onRenamePlaylist = viewModel::renamePlaylist,
                                onDeletePlaylist = viewModel::deletePlaylist
                            )
                        }

                        composable(ROUTE_STREAM) {
                            StreamScreen(
                                bookmarks = streamBookmarks,
                                parseHeaders = viewModel::parseHeadersJson,
                                onPlayStream = { title, url, proto, headers, pin ->
                                    viewModel.saveStreamAndBuildRequest(title, url, proto, headers, pin) { req ->
                                        PlayerActivity.launch(context, req)
                                    }
                                },
                                onPlayBookmark = { bookmark ->
                                    PlayerActivity.launch(context, viewModel.buildStreamRequestFromBookmark(bookmark))
                                },
                                onTogglePinBookmark = viewModel::toggleStreamPinned,
                                onDeleteBookmark = viewModel::deleteStreamBookmark
                            )
                        }

                        composable(ROUTE_SETTINGS) {
                            SettingsScreen(
                                settings = settings,
                                storageAccess = storageAccess,
                                viewModel = viewModel,
                                onRequestAllFilesAccess = { requestAllFilesAccess() },
                                onRequestVideoAccess = { requestVideoReadAccess() },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = ROUTE_FOLDER_DETAIL,
                            arguments = listOf(
                                navArgument("folderUri") { type = NavType.StringType },
                                navArgument("folderName") { type = NavType.StringType }
                            )
                        ) { entry ->
                            val folderUri = Uri.decode(entry.arguments?.getString("folderUri").orEmpty())
                            val folderName = Uri.decode(entry.arguments?.getString("folderName").orEmpty())
                            val folderVideos by remember(folderUri) {
                                viewModel.observeVideosInFolder(folderUri)
                            }.collectAsStateWithLifecycle(initialValue = emptyList())

                            CollectionDetailScreen(
                                title = folderName,
                                subtitle = "${folderVideos.size} local video(s)",
                                videos = folderVideos,
                                onBack = { navController.popBackStack() },
                                onPlayVideo = { list, idx, bg -> launchVideos(list, idx, bg) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onAddToPlaylist = { playlistPickerTarget = it },
                                onShowInfo = { videoInfoTarget = it },
                                onMoveVideo = { moveTarget = it },
                                onRenameVideo = { renameTarget = it },
                                onDeleteVideo = { deleteTarget = it }
                            )
                        }

                        composable(
                            route = ROUTE_PLAYLIST_DETAIL,
                            arguments = listOf(
                                navArgument("playlistId") { type = NavType.LongType },
                                navArgument("playlistName") { type = NavType.StringType }
                            )
                        ) { entry ->
                            val playlistId = entry.arguments?.getLong("playlistId") ?: -1L
                            val playlistName = Uri.decode(entry.arguments?.getString("playlistName").orEmpty())
                            val items by remember(playlistId) {
                                viewModel.observeVideosInPlaylist(playlistId)
                            }.collectAsStateWithLifecycle(initialValue = emptyList())

                            CollectionDetailScreen(
                                title = playlistName,
                                subtitle = "Custom Playlist · ${items.size} video(s)",
                                videos = items,
                                onBack = { navController.popBackStack() },
                                onPlayVideo = { list, idx, bg -> launchVideos(list, idx, bg) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onAddToPlaylist = { playlistPickerTarget = it },
                                onShowInfo = { videoInfoTarget = it },
                                onMoveVideo = { moveTarget = it },
                                onRenameVideo = { renameTarget = it },
                                onDeleteVideo = { deleteTarget = it },
                                onRemoveFromPlaylist = { viewModel.removeVideoFromPlaylist(playlistId, it.uri) }
                            )
                        }

                        composable(ROUTE_FAVORITES_DETAIL) {
                            CollectionDetailScreen(
                                title = "Favourites",
                                subtitle = "${favoriteVideos.size} starred video(s)",
                                videos = favoriteVideos,
                                onBack = { navController.popBackStack() },
                                onPlayVideo = { list, idx, bg -> launchVideos(list, idx, bg) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onAddToPlaylist = { playlistPickerTarget = it },
                                onShowInfo = { videoInfoTarget = it },
                                onMoveVideo = { moveTarget = it },
                                onRenameVideo = { renameTarget = it },
                                onDeleteVideo = { deleteTarget = it }
                            )
                        }

                        composable(ROUTE_HISTORY_DETAIL) {
                            CollectionDetailScreen(
                                title = "Playback History",
                                subtitle = "Automatic resume timestamps · 100% private",
                                videos = watchHistory,
                                onBack = { navController.popBackStack() },
                                onPlayVideo = { list, idx, bg -> launchVideos(list, idx, bg) },
                                onToggleFavorite = viewModel::toggleFavorite,
                                onAddToPlaylist = { playlistPickerTarget = it },
                                onShowInfo = { videoInfoTarget = it },
                                onMoveVideo = { moveTarget = it },
                                onRenameVideo = { renameTarget = it },
                                onDeleteVideo = { deleteTarget = it },
                                onClearAll = { viewModel.clearWatchHistory() },
                                clearAllLabel = "Clear History"
                            )
                        }
                    }
                }
            }
        }
    }

    // Automatic first-launch File Access prompt if no storage access is granted yet
    if (!storageAccess.canScanDevice && !startupPermissionPromptDismissed) {
        StorageAccessPromptDialog(
            title = "Allow File Access",
            message = "Luma Player needs file access to automatically discover all your video folders, match local subtitles, and seamlessly move, rename, or delete videos without manual folder mounting.",
            showVideosOnlyOption = true,
            onGrantAllFiles = {
                startupPermissionPromptDismissed = true
                requestAllFilesAccess()
            },
            onGrantVideosOnly = {
                startupPermissionPromptDismissed = true
                requestVideoReadAccess()
            },
            onDismiss = { startupPermissionPromptDismissed = true }
        )
    }

    // Contextual All Files Access upgrade dialog when Move/Rename/Delete needs full storage control
    allFilesPromptReason?.let { reason ->
        StorageAccessPromptDialog(
            title = "All Files Access Needed",
            message = reason,
            showVideosOnlyOption = false,
            onGrantAllFiles = {
                viewModel.dismissAllFilesPrompt()
                requestAllFilesAccess()
            },
            onGrantVideosOnly = { viewModel.dismissAllFilesPrompt() },
            onDismiss = { viewModel.dismissAllFilesPrompt() }
        )
    }

    moveTarget?.let { targetVideo ->
        val destinations = remember(folders) { viewModel.getSuggestedMoveDestinations() }
        MoveVideoDialog(
            video = targetVideo,
            destinations = destinations,
            onMoveToPath = { destPath ->
                viewModel.moveVideoFile(targetVideo, destPath)
                moveTarget = null
            },
            onDismiss = { moveTarget = null }
        )
    }

    renameTarget?.let { targetVideo ->
        RenameVideoDialog(
            video = targetVideo,
            onConfirmRename = { newName ->
                viewModel.renameVideoFile(targetVideo, newName)
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { targetVideo ->
        DeleteVideoConfirmDialog(
            video = targetVideo,
            onConfirmDelete = {
                viewModel.deleteVideoFile(targetVideo)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null }
        )
    }

    playlistPickerTarget?.let { targetVideo ->
        AddToPlaylistDialog(
            video = targetVideo,
            playlists = playlists,
            onSelectPlaylist = { playlist ->
                viewModel.addVideoToPlaylist(playlist.id, targetVideo.uri, playlist.name)
                playlistPickerTarget = null
            },
            onCreateNewPlaylist = {
                showQuickCreatePlaylist = true
            },
            onDismiss = { playlistPickerTarget = null }
        )
    }

    if (showQuickCreatePlaylist) {
        PlaylistNameDialog(
            title = "New Playlist",
            initialName = "",
            confirmLabel = "Create",
            onConfirm = { name ->
                viewModel.createPlaylist(name)
                showQuickCreatePlaylist = false
            },
            onDismiss = { showQuickCreatePlaylist = false }
        )
    }

    videoInfoTarget?.let { targetVideo ->
        VideoInfoDialog(
            video = targetVideo,
            onDismiss = { videoInfoTarget = null }
        )
    }
}
