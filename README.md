# ClipForge AI Mobile

Android-first video clipping editor written in Kotlin, Jetpack Compose and AndroidX Media3.

## Current Build #1 implementation
- Select downloaded videos with the Android document picker.
- Import direct HTTPS video-file URLs (not social media webpage URLs).
- Preview video using Media3 ExoPlayer.
- Set trim start and end positions.
- Choose a 9:16 output canvas (fit mode; does not automatically crop or track faces).
- Keep or mute the source audio.
- Export MP4 using Media3 Transformer to the app-specific Movies folder.

**Not yet implemented:** automatic highlights, automatic or manual captions, text overlays, timeline split/merge, transition presets, 1080p output guarantee, background download/export jobs, project persistence and campaign checks.

## Android-only APK build
1. Go to GitHub Actions > **Build Android APK**.
2. Open the latest run, or choose **Run workflow**.
3. When the build succeeds, open its **Artifacts** section and download **ClipForge-AI-Mobile-debug**.
4. Extract the ZIP and install `app-debug.apk` on your Android device.

## Known limitations
- Direct links must return actual video data, not HTML pages or DRM-protected streams.
- HTTPS video downloads are limited to 1 GB; downloads currently run in the foreground session.
- Export location is app-specific and may not automatically appear in Gallery.
- Vertical canvas uses fit mode and may include letterboxing.
- Export size, codec compatibility and speed depend on the phone.
- The APK build and device behavior have **not yet been independently verified**.

## Tech stack
Kotlin 2.0.21, Android Gradle Plugin 8.7.3, Gradle 8.9, Compose, Media3 1.5.1, Java 17.
