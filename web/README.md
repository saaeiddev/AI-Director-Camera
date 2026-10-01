# AI Director Camera — Web / iOS PWA

A browser/PWA adaptation of the Android **AI Director Camera** project, designed specifically for testing on iPhone Safari and as an Add-to-Home-Screen web app.

## Implemented

- Live rear/front camera using `getUserMedia`
- Video recording using `MediaRecorder` with iOS-friendly MIME detection
- Clip preview, Web Share / Save flow, and download fallback
- Rule-of-thirds, horizon and shot-technique overlays
- Shot Coach: Wide, Medium, Close-Up, Hero, Tracking, Dolly and Orbit
- Live luminance / clipping analysis
- On-device subject detection using TensorFlow.js + COCO-SSD when available
- Device orientation and motion guidance with iOS permission handling
- Hardware zoom and torch controls when the browser exposes them
- Offline AI Director rule engine ported from the Android app
- Storyboard shot-list generator
- Local Projects and shot metadata stored with `localStorage`
- Installable PWA manifest + service worker + iPhone Home Screen icon
- Safe-area-aware cinematic glass UI for notched/Dynamic Island iPhones

## iPhone Test

1. Open the GitHub Pages URL in Safari over HTTPS.
2. Tap **START CAMERA** and allow Camera + Microphone.
3. Tap **MOTION** and allow Motion & Orientation if prompted.
4. For an app-like experience: Safari Share → **Add to Home Screen** → **Open as Web App**.

## Web Limitations vs Native Android/iOS

Browser APIs do not expose every native camera capability. Physical lens enumeration, manual exposure/shutter/ISO, native ARKit/ARCore world tracking, guaranteed torch/zoom control, and background camera capture are not equivalent to the native Android app. Camera and sensor permissions also remain controlled by Safari/iOS.

The web build intentionally labels unsupported hardware controls dynamically instead of faking them.
