# Thingino Monitor v3 — clean GitHub upload

Upload the **contents** of this ZIP to the root of a **new, empty GitHub repository**. Keep the `.github/workflows/build-apk.yml` path intact. Do not upload the enclosing ZIP as a single file.

GitHub: Actions → Build Android APK → Run workflow. After success, download the `ThinginoMonitor-v3-debug` artifact and extract `app-debug.apk`.

Features: auto /24 IPv4 subnet discovery or manual prefix, HTTP Thingino WebUI identification, 2 FPS JPEG polling or continuous MJPEG, four cameras/page, Play/Pause, WEBUI browser, persistent preferences. Phone must be whitelisted on the cameras for unauthenticated previews.

Build: Java 17, Gradle 8.9, AGP 8.7.3, Android SDK 35. This package explicitly sets SDK packages to avoid the obsolete `tools` package error.

Not yet compiled or tested on an Android phone. Existing older workflows should not be copied into the new repository.
