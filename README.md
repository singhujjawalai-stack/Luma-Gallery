# Luma Gallery

A fast, fully offline gallery app for Android — built because many Motorola
phones ship without a default gallery app. No cloud, no tracking, no Google
Photos dependency.

## Features

- **Photos tab** — 3-column grid grouped by day, with *On this day* and
  *Memories* (auto-grouped by month) carousels
- **Albums** — automatic albums per folder, with Favorites / Trash / Hidden
  shortcuts
- **Full-screen viewer** — swipe, pinch-zoom, double-tap zoom, slideshow
  auto-advance, info sheet (resolution, size, folder, duration)
- **Video playback** — ExoPlayer with native controls
- **Photo editor** — 8 filters (grayscale, sepia, warm, cool, vivid,
  contrast, invert), rotate, crop with draggable handles, and a freehand
  draw tool with 6 colors. Edits save a copy to *Pictures/Luma Gallery*.
- **Favorites** — persistent favorites stored on-device
- **Trash** — Android system trash on Android 10+ (restore or delete
  forever, 30-day retention managed by the system)
- **Hidden folders** — hide any album from the gallery
- **Search** — live search across file names and folders with photo/video
  filter, **plus content search**: find photos by what's inside them —
  any text in the picture (OCR) or objects/scenes like "red ball", "beach",
  "dog" — using bundled on-device ML Kit models. No internet needed.
- **People** — on-device face detection (ML Kit) with simple clustering,
  rename and remove people; nothing leaves the phone
- **Set as wallpaper** — apply any photo to the home screen, lock screen,
  or both, from the viewer

## Requirements

- Android 8.0+ (API 26). Trash requires Android 10+.
- JDK 17, Android SDK 35 to build.

## Build

```bat
gradlew.bat assembleDebug
```

APK output: `app\build\outputs\apk\debug\app-debug.apk`

## Install on your phone

Option A — USB:
```bat
adb install app\build\outputs\apk\debug\app-debug.apk
```

Option B — copy `app-debug.apk` to the phone (Drive, cable, etc.), tap it,
allow "install from unknown sources".

## Tech

Kotlin + Jetpack Compose (Material 3), MediaStore for media access,
Room for favorites/hidden/people, Media3 ExoPlayer, Coil for image loading,
ML Kit face detection (on-device), WorkManager for background indexing.
