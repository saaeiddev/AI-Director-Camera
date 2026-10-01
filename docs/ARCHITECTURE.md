# Architecture

## Runtime data flow

1. `CameraSessionManager` binds CameraX `Preview`, `VideoCapture`, and `ImageAnalysis`.
2. `LiveFrameAnalyzer` samples camera frames and runs ML Kit Pose Detection in stream mode.
3. `LightingAnalyzer` reads the real luminance plane without converting full frames to Bitmaps.
4. `MotionAnalyzer` consumes rotation-vector and gyroscope events.
5. `GuidanceEngine` combines subject, lighting, horizon and smoothness signals into one priority recommendation.
6. Compose renders the camera HUD and Shot Coach overlays independently from the recording pipeline.
7. MediaStore receives the actual recorded MP4 file.
8. Room stores project/shot metadata separately from the media file.

## Reliability design

Camera + video + analysis is not guaranteed on all Android camera hardware levels. The manager first attempts the full three-use-case bind; if the device rejects that combination, the app unbinds and retries Preview + Video. This preserves the core camera rather than allowing optional AI analysis to crash it.

## Why ARCore is optional

The product must remain usable on phones without ARCore/depth. The current code detects ARCore availability but does not create an AR session. A future spatial implementation should use ARCore SharedCamera (or an equivalent architecture) so spatial tracking and the recorder do not fight for exclusive camera ownership.
