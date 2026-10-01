package com.saeid.aidirectorcamera.ai

import kotlin.math.abs

data class GuidanceInput(
    val subjectRect: NormRect?,
    val averageLuma: Double,
    val highlightFraction: Double,
    val horizonDegrees: Float,
    val smoothness: Float
)

object GuidanceEngine {
    fun recommendation(input: GuidanceInput): String {
        if (abs(input.horizonDegrees) > 3.0f) return "Level the horizon."
        if (input.averageLuma < 58.0) return "Subject is dark — move toward softer light."
        if (input.highlightFraction > 0.16) return "Highlights are clipping — reduce exposure."
        if (input.smoothness < 58f) return "Slow down the camera movement."

        val r = input.subjectRect ?: return "Find a clear subject or hold the camera steady."
        if (r.top < 0.035f) return "Give the subject a little more headroom."
        if (r.centerX < 0.30f) return "Move framing slightly right."
        if (r.centerX > 0.70f) return "Move framing slightly left."
        if (r.height < 0.24f) return "Move a little closer."
        return "Framing looks good — keep the movement smooth."
    }

    fun compositionConsistency(input: GuidanceInput): Int {
        var score = 100f
        score -= (abs(input.horizonDegrees) * 5f).coerceAtMost(25f)
        score -= ((100f - input.smoothness) * 0.25f).coerceAtMost(20f)
        input.subjectRect?.let { r ->
            val thirdA = 1f / 3f
            val thirdB = 2f / 3f
            val nearestThird = minOf(abs(r.centerX - thirdA), abs(r.centerX - thirdB))
            score -= (nearestThird * 55f).coerceAtMost(20f)
        } ?: run { score -= 20f }
        return score.coerceIn(0f, 100f).toInt()
    }
}
