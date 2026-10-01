# Implementation Matrix

| Area | Status | Notes |
|---|---|---|
| Real camera preview | Implemented | CameraX Preview |
| Real video recording | Implemented | CameraX Recorder + MediaStore |
| Microphone | Implemented | Permission-aware |
| Front/rear switch | Implemented | CameraSelector |
| 720p/1080p/4K | Implemented | Device capability filtered |
| Zoom | Implemented | Real CameraControl range |
| Focus | Implemented | Tap-to-focus |
| Exposure | Implemented | Real compensation range |
| Torch | Implemented | Only when hardware reports flash |
| Person / pose detection | Implemented | ML Kit stream mode |
| Subject tracking | Implemented (person) | ML Kit streaming prominent-person tracking |
| Composition guidance | Implemented | Thirds/headroom/subject position |
| Horizon | Implemented | Rotation-vector sensor |
| Movement smoothness | Implemented | Gyroscope + accelerometer |
| Lighting analysis | Implemented | Real Y-plane luminance/highlight sampling |
| Shot Coach | Implemented MVP | Measurable framing + screen-space movement guides |
| AI Director | Partial | Transparent offline deterministic planner; no LLM |
| Storyboard | Partial | Transparent offline deterministic planner |
| Projects | Implemented | Room database + shot metadata |
| Thermal management | Implemented MVP | AI sampling reduces; recording is not stopped |
| ARCore availability/fallback | Implemented | ARCore optional capability check |
| World-anchored AR paths | Not implemented | Requires ARCore SharedCamera/session/rendering integration |
| Depth distance | Not implemented | No fake distance telemetry |
| Explicit physical lens picker | Not implemented | Device zoom remains available |
| Manual FPS selection | Not implemented | Uses device-selected supported FPS |
| Playback analysis | Not implemented | Planned after real-device capture pipeline validation |
| Cloud generative AI | Not implemented | No secret upload path |
| Final APK | Not produced | Android SDK/device unavailable in this environment |
