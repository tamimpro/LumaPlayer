# Luma Player v2.0

![Version](https://img.shields.io/badge/version-2.0.0-b4690e?style=flat-square)
![Platform](https://img.shields.io/badge/Android-7.0%2B-3ddc84?style=flat-square)
![Engine](https://img.shields.io/badge/Media3-ExoPlayer%201.5.1-38e8ff?style=flat-square)

**A modern Android video player built for high-quality playback, flexible controls, local media management, and rock-solid network streaming.**

Luma Player combines an on-device video library with a configurable playback experience powered by AndroidX Media3. It is designed to make local videos and online streams effortless to play, organize, and customize.

> **Latest Release (`v2.0.0`):** Download the installable **`LumaPlayer-v2.0.0.apk`** directly from this repository's [**GitHub Releases**](https://github.com/tamimpro/LumaPlayer/releases/latest) page.

## What's New in v2.0

- **Bulletproof Online Seeking & Double-Tap Skipping:** Overhauled HTTP streaming pipeline (`Accept-Encoding: identity`, standard browser User-Agent profiles, CBR extractor seek indexing, configurable back-buffer retention, local LRU disk stream caching, and seekability guards) so skipping or double-tapping online links never restarts from `00:00`.
- **One-Tap Multi-Language Audio Switcher:** Dedicated top-bar **Audio Track** button (with active language badge like `ENG` / `HIN`) for both online streams and local multi-audio movies, plus automatic preferred audio & subtitle language selection.
- **Crisp Light Default Theme:** Launches in the clean **Crisp Light** theme by default with instant switching to **True AMOLED Dark** or **System Default**.
- **Ultra-Snappy Multi-Animations & 120Hz Scrolling:** Crisp route transitions, animated HUD choreography, directional double-tap skip arcs, and downsampled thumbnail loading for smooth scrolling across large 4K libraries.
- **Advanced Settings Control Center:** Full power-user hub inside Settings for tuning Network Buffers & Seek Engine, Hardware/Software Decoders & HDR, Default Audio/Subtitle Language Rules, HUD telemetry, and Stream/Thumbnail Caches.

## Features

### Local video library

- Scan and browse videos stored on your device.
- Navigate video folders and open individual files.
- Organize videos with playlists and favourites.
- Keep a watch history and resume supported videos from their last playback position.
- View video information and manage files. Moving, renaming, and deleting files may require Android's **All files access** permission.

### Online video and streaming

- Open direct HTTP or HTTPS video links.
- Supports the app's direct-file workflow for formats such as MP4 and MKV, plus **HLS** (`.m3u8`) and **MPEG-DASH** (`.mpd`) streams through Media3.
- Add request headers when a stream requires them, including `Authorization`, `Cookie`, `Referer`, and `User-Agent`, as well as custom headers.
- Save stream bookmarks for convenient access later.
- Choose automatic protocol detection or a protocol option in the Stream screen.

### Playback experience

- Media3 ExoPlayer playback engine with hardware-decoder support and decoder fallback.
- HDR-aware playback window and display-capability detection.
- Requests the highest supported video quality available to the device and source.
- Optional display refresh-rate matching on supported Android versions and displays.
- Playback speed control from **0.25× to 4×**.
- Aspect-ratio, zoom, and screen-orientation controls.
- Picture-in-Picture (PiP) and background audio playback.
- Automatic playback-position saving and resume.

### Gesture controls

- Swipe vertically on the left side to adjust screen brightness.
- Swipe vertically on the right side to adjust media volume.
- Swipe horizontally to seek through a video.
- Double-tap to skip backward or forward.
- Pinch to zoom, with a configurable range.
- Lock the player screen to avoid accidental touches.
- Configure gesture options, skip intervals, and sensitivity in Settings.

### Subtitles and audio

- Select available audio and subtitle tracks.
- Load an external subtitle file.
- Customize subtitle size and appearance, and adjust subtitle synchronization.
- Use the built-in equalizer controls, presets, custom band levels, and audio boost where supported by the device.

### Additional playback tools

- A–B repeat for replaying a selected section.
- Sleep timer, including an option to pause at the end of the current video.
- Playback diagnostics and stream retry controls.

## Screens and navigation

The main interface is organized around four sections:

| Section | Purpose |
| --- | --- |
| **Folders** | Browse videos by device folder. |
| **Videos** | Explore the scanned video library. |
| **Playlists** | Manage playlists and saved collections. |
| **Stream** | Enter network video URLs, configure request headers, and manage stream bookmarks. |

Settings provide appearance, storage access, playback defaults, gesture, subtitle, and display-related options.

## Requirements

- Android **7.0 (API 24)** or later.
- Android Studio with the Android SDK platform required by the project (`compileSdk` 36.1).
- JDK 17 or later for the Gradle build environment.

Playback quality depends on the video file or stream, Android's available decoders, device hardware, display capabilities, and (for network playback) connection quality. HDR, 4K, high-frame-rate playback, and seamless refresh-rate switching cannot be guaranteed on every device.

## Build from source

### 1. Clone the repository

```bash
git clone https://github.com/tamimpro/LumaPlayer.git
cd LumaPlayer
```

### 2. Open in Android Studio

Open the cloned project folder in Android Studio and allow Gradle to sync. Install the Android SDK components requested by the IDE if they are not already installed.

### 3. Build a debug APK

On macOS or Linux:

```bash
./gradlew assembleDebug
```

On Windows:

```bat
gradlew.bat assembleDebug
```

The generated APK will be available at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

You can install it on a connected device with:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Permissions

Luma Player requests Android permissions for the features that need them. Depending on Android version and how you use the app, these can include:

- **Video/media access:** to scan and play local videos.
- **All files access:** used for broader file management, including moving, renaming, and deleting videos. Grant this only when you need those features.
- **Internet and network state:** to open online videos and streams and check network connectivity.
- **Foreground media playback, notifications, and wake lock:** to support media-session controls and continued playback where enabled.

You can review or change app permissions at any time in Android Settings. Only open stream URLs and provide request headers for content you are authorized to access. Standard HTTP connections are not encrypted; use HTTPS whenever the source supports it.

## Technology stack

- **Kotlin**
- **Jetpack Compose** and Material 3
- **AndroidX Media3 / ExoPlayer** for media playback and media sessions
- **Room** for local library data
- **DataStore Preferences** for app settings
- **OkHttp** for network data sources
- **Kotlin Coroutines and Flow** for asynchronous work and observable state
- **Coil** for media artwork and video-frame loading

## Project structure

The main application source is under `app/src/main/java/com/example/`.

```text
app/
└── src/main/java/com/example/
    ├── data/       # Local data, models, repositories, and preferences
    ├── player/     # Playback service, player screen, and playback state
    └── ui/         # Compose screens, components, and theme
```

## Privacy

The app's Settings screen describes the video library and preferences as on-device and states that the app has no ads, analytics, or cloud tracking. Network access is used for online playback. Review the requested Android permissions and the source code before building or distributing your own version.

## Contributing

Bug reports, ideas, and code contributions are welcome. For a bug report, include your Android version, device model, steps to reproduce the issue, and relevant logs where possible. Please avoid posting private stream URLs, cookies, authorization tokens, or other credentials in public issues.

1. Fork the repository.
2. Create a branch for your change.
3. Make and test your changes.
4. Open a pull request with a clear description of what changed.

## License

No `LICENSE` file is currently included in this repository. Until a license is added, assume that the source code is **not licensed for reuse or redistribution**. Contact the repository owner for permission before using or distributing the code.

---

**Repository:** [github.com/tamimpro/LumaPlayer](https://github.com/tamimpro/LumaPlayer)
