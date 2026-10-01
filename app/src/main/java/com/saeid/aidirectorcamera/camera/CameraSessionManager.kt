package com.saeid.aidirectorcamera.camera

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.MediaStore
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DynamicRange
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.ArCoreApk
import com.saeid.aidirectorcamera.ai.AnalysisSnapshot
import com.saeid.aidirectorcamera.ai.LiveFrameAnalyzer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


enum class QualityMode(val label: String, val quality: Quality) {
    HD("720p", Quality.HD),
    FHD("1080p", Quality.FHD),
    UHD("4K", Quality.UHD)
}

data class CameraState(
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val isRecording: Boolean = false,
    val zoomRatio: Float = 1f,
    val minZoomRatio: Float = 1f,
    val maxZoomRatio: Float = 1f,
    val exposureIndex: Int = 0,
    val exposureMin: Int = 0,
    val exposureMax: Int = 0,
    val hasFlash: Boolean = false,
    val torchOn: Boolean = false,
    val quality: QualityMode = QualityMode.FHD,
    val availableQualities: List<QualityMode> = listOf(QualityMode.FHD),
    val lastSavedUri: String? = null,
    val lastDurationMs: Long = 0L,
    val arCoreStatus: String = "Checking ARCore…",
    val thermalStatus: String = "Thermal: normal",
    val error: String? = null
)

class CameraSessionManager(private val context: Context) : AutoCloseable {
    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val providerFuture = ProcessCameraProvider.getInstance(context)
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    @Volatile private var analysisIntervalMs = 85L

    private var camera: Camera? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var analyzer: LiveFrameAnalyzer? = null
    private var owner: LifecycleOwner? = null
    private var previewView: PreviewView? = null

    private val _state = MutableStateFlow(CameraState(arCoreStatus = detectArCore()))
    val state: StateFlow<CameraState> = _state

    private val _analysis = MutableStateFlow(AnalysisSnapshot())
    val analysis: StateFlow<AnalysisSnapshot> = _analysis

    private val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
        val (label, interval) = when {
            status >= PowerManager.THERMAL_STATUS_SEVERE -> "Thermal: severe • AI reduced" to 220L
            status >= PowerManager.THERMAL_STATUS_MODERATE -> "Thermal: warm • AI reduced" to 140L
            status >= PowerManager.THERMAL_STATUS_LIGHT -> "Thermal: light" to 105L
            else -> "Thermal: normal" to 85L
        }
        analysisIntervalMs = interval
        analyzer?.setMinAnalysisIntervalMs(interval)
        _state.value = _state.value.copy(thermalStatus = label)
    }

    init {
        runCatching { powerManager.addThermalStatusListener(mainExecutor, thermalListener) }
    }

    fun bind(lifecycleOwner: LifecycleOwner, view: PreviewView, quality: QualityMode = _state.value.quality) {
        owner = lifecycleOwner
        previewView = view
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                _state.value = _state.value.copy(error = null)
                provider.unbindAll()
                analyzer?.close()

                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val recorder = Recorder.Builder()
                    .setQualitySelector(
                        QualitySelector.from(
                            quality.quality,
                            FallbackStrategy.higherQualityOrLowerThan(quality.quality)
                        )
                    )
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)

                analyzer = LiveFrameAnalyzer { _analysis.value = it }.also {
                    it.setMinAnalysisIntervalMs(analysisIntervalMs)
                }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, analyzer!!) }

                val selector = CameraSelector.Builder()
                    .requireLensFacing(_state.value.lensFacing)
                    .build()

                camera = try {
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, videoCapture, imageAnalysis)
                } catch (analysisBindingError: Throwable) {
                    // Some Android camera stacks cannot sustain Preview + Video + Analysis together.
                    // Keep the real camera and recorder alive rather than crashing the core product.
                    provider.unbindAll()
                    analyzer?.close()
                    analyzer = null
                    _state.value = _state.value.copy(
                        error = "Live AI analysis is unavailable for this camera configuration; recording remains active."
                    )
                    provider.bindToLifecycle(lifecycleOwner, selector, preview, videoCapture)
                }
                val cam = camera ?: return@addListener
                val zoom = cam.cameraInfo.zoomState.value
                val exposure = cam.cameraInfo.exposureState
                val supported = Recorder.getVideoCapabilities(cam.cameraInfo).getSupportedQualities(DynamicRange.SDR)
                val available = QualityMode.entries.filter { supported.contains(it.quality) }.ifEmpty { listOf(QualityMode.FHD) }
                _state.value = _state.value.copy(
                    quality = quality,
                    minZoomRatio = zoom?.minZoomRatio ?: 1f,
                    maxZoomRatio = zoom?.maxZoomRatio ?: 1f,
                    zoomRatio = zoom?.zoomRatio ?: 1f,
                    exposureIndex = exposure.exposureCompensationIndex,
                    exposureMin = exposure.exposureCompensationRange.lower,
                    exposureMax = exposure.exposureCompensationRange.upper,
                    hasFlash = cam.cameraInfo.hasFlashUnit(),
                    availableQualities = available,
                    error = _state.value.error
                )
            } catch (t: Throwable) {
                _state.value = _state.value.copy(error = t.message ?: "Camera could not start")
            }
        }, mainExecutor)
    }

    fun rebind(quality: QualityMode) {
        val o = owner ?: return
        val p = previewView ?: return
        _state.value = _state.value.copy(quality = quality)
        bind(o, p, quality)
    }

    fun switchCamera() {
        val next = if (_state.value.lensFacing == CameraSelector.LENS_FACING_BACK)
            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        _state.value = _state.value.copy(lensFacing = next)
        owner?.let { o -> previewView?.let { p -> bind(o, p, _state.value.quality) } }
    }

    fun setZoom(ratio: Float) {
        val s = _state.value
        val safe = ratio.coerceIn(s.minZoomRatio, s.maxZoomRatio)
        camera?.cameraControl?.setZoomRatio(safe)
        _state.value = s.copy(zoomRatio = safe)
    }

    fun setExposure(index: Int) {
        val s = _state.value
        val safe = index.coerceIn(s.exposureMin, s.exposureMax)
        camera?.cameraControl?.setExposureCompensationIndex(safe)
        _state.value = s.copy(exposureIndex = safe)
    }

    fun toggleTorch() {
        val s = _state.value
        if (!s.hasFlash) return
        val next = !s.torchOn
        camera?.cameraControl?.enableTorch(next)
        _state.value = s.copy(torchOn = next)
    }

    fun tapToFocus(view: PreviewView, x: Float, y: Float) {
        val point = view.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()
        camera?.cameraControl?.startFocusAndMetering(action)
    }

    fun toggleRecording() {
        if (_state.value.isRecording) {
            recording?.stop()
            return
        }
        startRecording()
    }

    private fun startRecording() {
        val capture = videoCapture ?: return
        val name = "AI_Director_${System.currentTimeMillis()}"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/AI Director Camera")
        }
        val output = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        var pending = capture.output.prepareRecording(context, output)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            pending = pending.withAudioEnabled()
        }
        recording = pending.start(mainExecutor) { event ->
            when (event) {
                is VideoRecordEvent.Start -> _state.value = _state.value.copy(isRecording = true, error = null)
                is VideoRecordEvent.Finalize -> {
                    val uri = event.outputResults.outputUri.takeIf { it.toString().isNotBlank() }
                    val duration = event.recordingStats.recordedDurationNanos / 1_000_000L
                    _state.value = _state.value.copy(
                        isRecording = false,
                        lastSavedUri = uri?.toString(),
                        lastDurationMs = duration,
                        error = if (event.hasError()) "Recording failed: ${event.error}" else null
                    )
                    recording = null
                }
            }
        }
    }

    fun unbind() {
        if (providerFuture.isDone) runCatching { providerFuture.get().unbindAll() }
    }

    private fun detectArCore(): String = try {
        val availability = ArCoreApk.getInstance().checkAvailability(context)
        when {
            availability.isSupported -> "ARCore supported"
            availability.isUnknown -> "ARCore status pending"
            else -> "ARCore not supported — 2D guidance active"
        }
    } catch (_: Throwable) {
        "ARCore unavailable — 2D guidance active"
    }

    override fun close() {
        recording?.stop()
        analyzer?.close()
        unbind()
        runCatching { powerManager.removeThermalStatusListener(thermalListener) }
        analysisExecutor.shutdown()
    }
}
