# Real-device QA checklist

Do not mark a release final until these checks pass on physical Android hardware.

## Core camera

- Install APK cleanly.
- Launch after cold start and after force-stop.
- Grant/deny/re-grant camera permission.
- Grant/deny microphone permission.
- Rear preview starts without black frame.
- Front preview starts without black frame.
- Tap-to-focus responds.
- Zoom min/max reflect real hardware.
- Exposure compensation respects device range.
- Torch only appears usable on hardware with flash.
- 720p / 1080p / 4K choices match reported CameraX capabilities.

## Recording

- Start/stop video.
- Verify MP4 is playable.
- Verify microphone audio when permission is granted.
- Verify silent recording when microphone permission is denied.
- Verify MediaStore path appears in Gallery/Files.
- 5 minute, 15 minute and 30 minute sessions.
- Background/foreground during idle; do not intentionally background an active recording without defining expected behavior.
- Low-storage behavior.

## AI / sensors

- Pose appears on a real person.
- Subject box follows a walking subject.
- Guidance updates without freezing preview.
- Horizon reacts to device roll.
- Smoothness reacts to pan/tilt speed.
- Low light warning uses actual dark scenes.
- Highlight warning uses actual clipped scenes.

## Compatibility/fallback

- Flagship device with ARCore.
- Mid-range device.
- Device without ARCore if available.
- Device that rejects three CameraX use cases, verifying Preview + Video fallback.
- Portrait and landscape.
- Camera cutout/notch and multiple aspect ratios.

## Performance

- No ANR.
- No camera lifecycle crash after repeated mode switching.
- Monitor CPU/GPU/memory in Android Studio Profiler.
- Observe thermal throttling over long sessions.
- Validate no corrupted files after long recording.
