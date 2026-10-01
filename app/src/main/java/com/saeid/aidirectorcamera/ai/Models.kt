package com.saeid.aidirectorcamera.ai

data class NormRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

data class LightingMetrics(
    val averageLuma: Double = 128.0,
    val darkFraction: Double = 0.0,
    val highlightFraction: Double = 0.0
)

data class AnalysisSnapshot(
    val subjectRect: NormRect? = null,
    val poseConfidence: Float = 0f,
    val lighting: LightingMetrics = LightingMetrics(),
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    val timestampMs: Long = 0L
)
