# AI Director Camera

**AI Director Camera** is a native Android cinematography assistant that combines a real CameraX preview/recording pipeline with on-device analysis, shot guidance, motion sensing, project organization, and AR-capability detection.

> Current status: **MVP / development build**. The APK builds successfully in GitHub Actions and is signature-verified. Real-device camera and AR behavior should still be validated on representative Android phones before production use.

## Features

- Real CameraX camera preview and video recording
- Front/rear camera switching
- Zoom, exposure, torch and tap-to-focus controls
- Live frame luminance analysis
- ML Kit pose-based subject tracking
- Shot Coach guidance and composition scoring
- Horizon, pan, tilt, roll and shake analysis using device sensors
- Cinematic movement guides such as dolly, tracking and orbit overlays
- AI Director shot suggestions using deterministic on-device rules
- Storyboard generation from scene keywords
- Room database for projects and recorded-shot metadata
- ARCore availability detection with graceful fallback
- Thermal-aware analysis throttling
- Dark cinematic Compose UI

## Tech Stack

Kotlin, Jetpack Compose, CameraX, ML Kit Pose Detection, ARCore, Room, KSP, Kotlin Coroutines, Android sensors, Gradle 9.6 and Android Gradle Plugin 9.4.

## Project Structure

```text
app/src/main/java/com/saeid/aidirectorcamera/
├── ai/          # Live analysis and guidance
├── camera/      # CameraX session and recording
├── data/        # Room entities, DAO and repository
├── director/    # Director and storyboard planning
├── sensors/     # Motion/orientation analysis
└── ui/          # Compose screens and theme

docs/            # Architecture, privacy and QA notes
scripts/         # Local build and logic-test helpers
.github/         # CI / release automation
```

## Build Locally

Requirements: Android SDK 36, JDK 17, and Gradle 9.6.

```bash
gradle :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install

Download **AI-Director-Camera.apk** from the GitHub Releases page, transfer it to an Android phone and install it. Android may ask you to allow installation from the selected source.

Debug application ID:

```text
com.saeid.aidirectorcamera.debug
```

## Automated Validation

CI builds the APK from the repository source tree, runs the pure Kotlin smoke tests, verifies the generated APK with `apksigner`, generates SHA-256, uploads a workflow artifact, and publishes the APK to GitHub Releases.

Hardware-specific CameraX/ARCore behavior and sustained thermal performance still require real-device QA.

## Current MVP Limitations

- Camera movement guidance is screen-space rather than fully world-anchored AR.
- Physical ultra-wide/main/telephoto lens enumeration is not implemented yet.
- Manual FPS selection is not exposed yet.
- AI Director and storyboard planning use deterministic on-device rules rather than a cloud generative-AI service.
- Advanced playback analysis and depth-based subject distance are future work.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Implementation Matrix](docs/IMPLEMENTATION_MATRIX.md)
- [Device Test Checklist](docs/DEVICE_TEST_CHECKLIST.md)
- [Privacy](docs/PRIVACY.md)
- [Development Test Results](TEST_RESULTS.txt)

## License

No open-source license has been declared yet. All rights remain with the repository owner unless a license is added later.


## Web / iOS PWA

An iPhone-friendly Progressive Web App is included under [`web/`](web/).

It provides a real browser camera feed, video recording through MediaRecorder, cinematic composition guides, Shot Coach modes, device-motion horizon/smoothness analysis, live lighting analysis, optional on-device subject detection, Director planning, Storyboard generation, local Projects, and installable PWA behavior.

The web build is designed for Safari on iPhone and is deployed through the dedicated GitHub Pages workflow in `.github/workflows/pages.yml`.

Expected Pages URL after GitHub Pages is enabled for this repository:

```text
https://saaeiddev.github.io/AI-Director-Camera/
```

Web APIs do not expose every native CameraX/ARCore capability, so this PWA is intended for iOS/browser testing rather than as a byte-for-byte replacement for the Android APK.
