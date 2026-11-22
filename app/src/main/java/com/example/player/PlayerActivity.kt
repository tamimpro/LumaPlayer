package com.example.player

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import android.provider.OpenableColumns
import android.util.Rational
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.model.PlaybackQueueItem
import com.example.data.model.PlaybackRequest
import com.example.data.model.PlayerOrientationMode
import com.example.ui.theme.LumaPlayerTheme

class PlayerActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()
    private var isInPip by mutableStateOf(false)
    private var suppressAutoPipOnce = false

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    private val subtitlePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            viewModel.attachExternalSubtitle(uri)
        }
    }

    private val pipActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PIP_TOGGLE_PLAY -> viewModel.togglePlayPause()
                ACTION_PIP_REWIND -> viewModel.seekBy(-10_000L)
                ACTION_PIP_FORWARD -> viewModel.seekBy(10_000L)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        configureHdrAndImmersiveWindow()
        inspectDisplayCapabilities()

        // Ensure LumaPlaybackService is started
        val serviceIntent = Intent(this, LumaPlaybackService::class.java)
        startService(serviceIntent)

        // Handle external ACTION_VIEW video intents (local files or network stream links)
        handleIncomingViewIntent(intent)

        val filter = IntentFilter().apply {
            addAction(ACTION_PIP_TOGGLE_PLAY)
            addAction(ACTION_PIP_REWIND)
            addAction(ACTION_PIP_FORWARD)
        }
        ContextCompat.registerReceiver(this, pipActionReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val backgroundPlayActive by viewModel.backgroundPlayActive.collectAsStateWithLifecycle()

            LaunchedEffect(uiState.orientationMode, uiState.videoWidth, uiState.videoHeight) {
                applyRequestedOrientation(uiState.orientationMode, uiState.videoWidth, uiState.videoHeight)
            }

            LaunchedEffect(settings.hdrWindowEnabled) {
                applyWindowHdrMode(settings.hdrWindowEnabled)
            }

            LaunchedEffect(uiState.isPlaying, uiState.videoWidth, uiState.videoHeight, settings.autoEnterPip, backgroundPlayActive) {
                updatePipParams(
                    videoWidth = uiState.videoWidth,
                    videoHeight = uiState.videoHeight,
                    isPlaying = uiState.isPlaying,
                    autoEnter = settings.autoEnterPip && !backgroundPlayActive
                )
            }

            LumaPlayerTheme(forceDark = true) {
                PlayerScreen(
                    viewModel = viewModel,
                    isInPipMode = isInPip,
                    onNavigateBack = {
                        viewModel.persistProgressNow()
                        if (!PlayerSessionHolder.backgroundPlayActive.value) {
                            PlayerSessionHolder.activePlayer.value?.pause()
                        }
                        finish()
                    },
                    onEnterPip = { enterLumaPipMode() },
                    onEnterBackgroundAudio = { enterBackgroundAudioSession() },
                    onPickExternalSubtitle = {
                        subtitlePickerLauncher.launch(
                            arrayOf(
                                "application/x-subrip",
                                "text/vtt",
                                "text/plain",
                                "application/ttml+xml",
                                "*/*"
                            )
                        )
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        suppressAutoPipOnce = false
        if (!isInPip) {
            configureHdrAndImmersiveWindow()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingViewIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (suppressAutoPipOnce || PlayerSessionHolder.backgroundPlayActive.value) {
            return
        }
        if (viewModel.settings.value.autoEnterPip &&
            PlayerSessionHolder.activePlayer.value?.isPlaying == true &&
            !isInPip
        ) {
            enterLumaPipMode()
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip = isInPictureInPictureMode
        if (!isInPictureInPictureMode) {
            configureHdrAndImmersiveWindow()
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.persistProgressNow()
        if (!isInPip && !PlayerSessionHolder.backgroundPlayActive.value) {
            PlayerSessionHolder.activePlayer.value?.pause()
        }
    }

    private fun enterBackgroundAudioSession() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        suppressAutoPipOnce = true
        viewModel.closeSheet()
        viewModel.enableBackgroundAudioForSession()
        val state = viewModel.uiState.value
        updatePipParams(
            videoWidth = state.videoWidth,
            videoHeight = state.videoHeight,
            isPlaying = state.isPlaying,
            autoEnter = false
        )
        Toast.makeText(this, "Playing audio in background", Toast.LENGTH_SHORT).show()
        moveTaskToBack(true)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(pipActionReceiver) }
        super.onDestroy()
    }

    private fun handleIncomingViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val dataUri = intent.data ?: return
        val scheme = dataUri.scheme.orEmpty().lowercase()
        val isStream = scheme == "http" || scheme == "https"
        val title = resolveIntentTitle(dataUri)
        PlayerSessionHolder.submitRequest(
            PlaybackRequest(
                queue = listOf(
                    PlaybackQueueItem(
                        uri = dataUri.toString(),
                        title = title,
                        isStream = isStream
                    )
                ),
                startIndex = 0
            )
        )
    }

    private fun resolveIntentTitle(uri: Uri): String {
        if (uri.scheme == "content") {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            return cursor.getString(idx)?.substringBeforeLast('.') ?: "Video"
                        }
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.') ?: "Luma Video"
    }

    private fun configureHdrAndImmersiveWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        applyWindowHdrMode(viewModel.settings.value.hdrWindowEnabled)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun applyWindowHdrMode(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.colorMode = if (enabled) {
                ActivityInfo.COLOR_MODE_HDR
            } else {
                ActivityInfo.COLOR_MODE_DEFAULT
            }
        }
    }

    private fun inspectDisplayCapabilities() {
        val currentDisplay: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }

        val hdrLabels = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val types = currentDisplay?.hdrCapabilities?.supportedHdrTypes ?: intArrayOf()
            for (type in types) {
                when (type) {
                    Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION -> hdrLabels.add("Dolby Vision")
                    Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS -> hdrLabels.add("HDR10+")
                    Display.HdrCapabilities.HDR_TYPE_HDR10 -> hdrLabels.add("HDR10")
                    Display.HdrCapabilities.HDR_TYPE_HLG -> hdrLabels.add("HLG")
                }
            }
        }
        val refreshRate = currentDisplay?.refreshRate ?: 60f
        PlayerSessionHolder.updateDiagnostics {
            it.copy(
                displayHdrCapabilities = hdrLabels.distinct(),
                displayRefreshRateHz = refreshRate
            )
        }
    }

    private fun applyRequestedOrientation(
        mode: PlayerOrientationMode,
        videoWidth: Int,
        videoHeight: Int
    ) {
        when (mode) {
            PlayerOrientationMode.LANDSCAPE -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            PlayerOrientationMode.REVERSE_LANDSCAPE -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
            }
            PlayerOrientationMode.PORTRAIT -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            PlayerOrientationMode.SENSOR -> {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            }
            PlayerOrientationMode.AUTO_VIDEO -> {
                // Wait until Media3 reports real decoded video dimensions so vertical videos never flash landscape
                if (videoWidth <= 0 || videoHeight <= 0) return
                requestedOrientation = if (videoHeight > videoWidth) {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }
        }
    }

    private fun buildPipParams(
        videoWidth: Int,
        videoHeight: Int,
        isPlaying: Boolean,
        autoEnter: Boolean
    ): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null

        // Clamp aspect ratio to Android PiP supported range (0.4184 to 2.39)
        val rawRatio = if (videoWidth > 0 && videoHeight > 0) {
            videoWidth.toFloat() / videoHeight.toFloat()
        } else {
            16f / 9f
        }
        val clamped = rawRatio.coerceIn(0.45f, 2.35f)
        val rational = Rational((clamped * 1000).toInt(), 1000)

        val rewindAction = RemoteAction(
            Icon.createWithResource(this, android.R.drawable.ic_media_rew),
            getString(R.string.pip_action_rewind),
            getString(R.string.pip_action_rewind),
            PendingIntent.getBroadcast(
                this,
                201,
                Intent(ACTION_PIP_REWIND).setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        val playPauseAction = RemoteAction(
            Icon.createWithResource(
                this,
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            ),
            getString(if (isPlaying) R.string.pip_action_pause else R.string.pip_action_play),
            getString(if (isPlaying) R.string.pip_action_pause else R.string.pip_action_play),
            PendingIntent.getBroadcast(
                this,
                202,
                Intent(ACTION_PIP_TOGGLE_PLAY).setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        val forwardAction = RemoteAction(
            Icon.createWithResource(this, android.R.drawable.ic_media_ff),
            getString(R.string.pip_action_forward),
            getString(R.string.pip_action_forward),
            PendingIntent.getBroadcast(
                this,
                203,
                Intent(ACTION_PIP_FORWARD).setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(rational)
            .setActions(listOf(rewindAction, playPauseAction, forwardAction))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(autoEnter && isPlaying)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun updatePipParams(
        videoWidth: Int,
        videoHeight: Int,
        isPlaying: Boolean,
        autoEnter: Boolean
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = buildPipParams(videoWidth, videoHeight, isPlaying, autoEnter) ?: return
            runCatching { setPictureInPictureParams(params) }
        }
    }

    private fun enterLumaPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val state = viewModel.uiState.value
            val params = buildPipParams(
                videoWidth = state.videoWidth,
                videoHeight = state.videoHeight,
                isPlaying = state.isPlaying,
                autoEnter = viewModel.settings.value.autoEnterPip
            ) ?: return
            runCatching { enterPictureInPictureMode(params) }
        }
    }

    companion object {
        private const val ACTION_PIP_TOGGLE_PLAY = "com.example.luma.PIP_TOGGLE_PLAY"
        private const val ACTION_PIP_REWIND = "com.example.luma.PIP_REWIND"
        private const val ACTION_PIP_FORWARD = "com.example.luma.PIP_FORWARD"

        fun launch(context: Context, request: PlaybackRequest) {
            PlayerSessionHolder.submitRequest(request)
            val intent = Intent(context, PlayerActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
        }
    }
}
