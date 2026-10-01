package com.saeid.aidirectorcamera.ai

import androidx.camera.core.ImageProxy

object LightingAnalyzer {
    fun analyze(image: ImageProxy): LightingMetrics {
        val plane = image.planes.firstOrNull() ?: return LightingMetrics()
        val buffer = plane.buffer.duplicate()
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val width = image.width
        val height = image.height

        var sum = 0L
        var count = 0L
        var dark = 0L
        var bright = 0L
        val step = 8

        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                val index = y * rowStride + x * pixelStride
                if (index in 0 until buffer.limit()) {
                    val value = buffer.get(index).toInt() and 0xFF
                    sum += value
                    count++
                    if (value < 28) dark++
                    if (value > 238) bright++
                }
                x += step
            }
            y += step
        }
        if (count == 0L) return LightingMetrics()
        return LightingMetrics(
            averageLuma = sum.toDouble() / count.toDouble(),
            darkFraction = dark.toDouble() / count.toDouble(),
            highlightFraction = bright.toDouble() / count.toDouble()
        )
    }
}
