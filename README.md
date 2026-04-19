# CookieSH

CookieSH is a Kotlin + Jetpack Compose developer toolkit for rooted Android devices, especially GSI users. It brings common low-level tasks into a single dark, touch-friendly app with Room persistence, Hilt injection, and coroutine-backed shell execution.

## Requirements

- Rooted Android device
- Android 8.0+ (API 26)

## Features

- Dashboard with animated gradient header, glassmorphism cards, and live device summary
- Props Editor with `getprop`, search, favorites, edit/add/delete, export, and PHH superuser presets
- Shell Runner with real-time output, root toggle, local command history, copy, and clear controls
- Logcat Viewer with live feed, search, tag/package/level filters, pause/resume, export, and buffer clear
- Package Manager with system/user/disabled filters, app details, disable/enable, uninstall for user 0, and force stop
- Partition Viewer with `/dev/block/by-name` parsing, active slot highlighting, size lookup, and per-partition dump
- ADB & Network Tools for ADB over Wi-Fi status/toggle, port control, IP display, and PHH manager package setter
- Boot Info screen parsing `/proc/cmdline`, `/proc/bootconfig`, `getprop`, and kernel version data

## Build

1. Open the project in Android Studio or use the Gradle wrapper.
2. Use a Java 21-compatible Gradle runtime if your environment defaults to a newer JDK.
3. Build the debug APK:

```bash
./gradlew :app:assembleDebug
```

## Tech Stack

- Kotlin
- Jetpack Compose
- Material3
- Hilt
- Room
- Kotlin Coroutines + Flow
- Coil

## License

Apache License 2.0
