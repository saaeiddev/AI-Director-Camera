# Privacy

This MVP performs camera analysis locally.

- Camera frames are consumed by on-device CameraX + ML Kit processing.
- Video is saved locally through Android MediaStore.
- Project metadata is stored locally in Room.
- The delivered source contains no cloud AI endpoint and no automatic image/video upload.
- ARCore is optional and only availability is checked in this MVP.

Any future cloud-AI feature must be opt-in, explain what leaves the phone, and prefer text/metadata or a user-selected still frame over continuous raw video.
