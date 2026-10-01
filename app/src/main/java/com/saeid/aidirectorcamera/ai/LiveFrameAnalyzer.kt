package com.saeid.aidirectorcamera.ai

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.util.concurrent.atomic.AtomicBoolean

class LiveFrameAnalyzer(
    private val onSnapshot: (AnalysisSnapshot) -> Unit
) : ImageAnalysis.Analyzer, AutoCloseable {

    private val busy = AtomicBoolean(false)
    @Volatile private var lastAnalysisMs = 0L
    @Volatile private var minAnalysisIntervalMs = 85L // ~12 FPS AI analysis; preview/recording remain full-rate.
    private val detector = PoseDetection.getClient(
        PoseDetectorOptions.Builder()
            .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
            .build()
    )

    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastAnalysisMs < minAnalysisIntervalMs) {
            imageProxy.close()
            return
        }
        if (!busy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            busy.set(false)
            imageProxy.close()
            return
        }

        lastAnalysisMs = now
        val lighting = LightingAnalyzer.analyze(imageProxy)
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val width = imageProxy.width.coerceAtLeast(1)
        val height = imageProxy.height.coerceAtLeast(1)

        detector.process(image)
            .addOnSuccessListener { pose ->
                val landmarks = pose.allPoseLandmarks.filter { it.inFrameLikelihood >= 0.45f }
                val rect = if (landmarks.size >= 4) {
                    val xs = landmarks.map { (it.position.x / width.toFloat()).coerceIn(0f, 1f) }
                    val ys = landmarks.map { (it.position.y / height.toFloat()).coerceIn(0f, 1f) }
                    NormRect(
                        left = xs.minOrNull() ?: 0f,
                        top = ys.minOrNull() ?: 0f,
                        right = xs.maxOrNull() ?: 1f,
                        bottom = ys.maxOrNull() ?: 1f
                    )
                } else null
                val confidence = if (landmarks.isEmpty()) 0f else landmarks.map { it.inFrameLikelihood }.average().toFloat()
                onSnapshot(
                    AnalysisSnapshot(
                        subjectRect = rect,
                        poseConfidence = confidence,
                        lighting = lighting,
                        frameWidth = width,
                        frameHeight = height,
                        timestampMs = System.currentTimeMillis()
                    )
                )
            }
            .addOnFailureListener {
                onSnapshot(
                    AnalysisSnapshot(
                        lighting = lighting,
                        frameWidth = width,
                        frameHeight = height,
                        timestampMs = System.currentTimeMillis()
                    )
                )
            }
            .addOnCompleteListener {
                busy.set(false)
                imageProxy.close()
            }
    }

    fun setMinAnalysisIntervalMs(value: Long) {
        minAnalysisIntervalMs = value.coerceIn(50L, 500L)
    }

    override fun close() {
        detector.close()
    }
}
