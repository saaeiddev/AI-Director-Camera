package com.saeid.aidirectorcamera.director

data class DirectorPlan(
    val shot: String,
    val cameraHeight: String,
    val subject: String,
    val movement: String,
    val lens: String,
    val light: String,
    val coachPreset: String
)

object DirectorPlanner {
    fun plan(prompt: String): DirectorPlan {
        val p = prompt.lowercase()
        return when {
            "horror" in p || "scary" in p -> DirectorPlan(
                "Off-center medium-wide shot",
                "Chest to eye level",
                "Place subject on one third with negative space",
                "Slow creeping push-in",
                "1x / wide if the environment matters",
                "Keep the face readable but let the background fall darker",
                "Tracking"
            )
            "product" in p -> DirectorPlan(
                "Clean product hero shot",
                "Level with the product center",
                "Center or precise third",
                "Slow orbit or push-in",
                "2x / telephoto if available",
                "Soft side light; protect highlights",
                "Orbit"
            )
            "car" in p -> DirectorPlan(
                "Low three-quarter hero shot",
                "Near wheel / bumper height",
                "Car on lower-middle frame",
                "Slow tracking or arc",
                "1x to 2x depending on space",
                "Use side/back light for body reflections",
                "Tracking"
            )
            "dialog" in p || "conversation" in p -> DirectorPlan(
                "Over-the-shoulder medium close-up",
                "Near subject eye level",
                "Eyes near upper third; preserve look room",
                "Mostly locked, subtle drift only",
                "2x if space permits",
                "Keep both face and eyeline side consistent",
                "Medium"
            )
            "sci" in p || "future" in p -> DirectorPlan(
                "Symmetric low-angle medium shot",
                "Slightly below chest level",
                "Centered with architectural lines",
                "Controlled push-in",
                "1x or 2x",
                "Cool practicals with a controlled edge light",
                "Hero"
            )
            else -> DirectorPlan(
                "Low-angle medium hero shot",
                "Approximately waist level",
                "Center-left with clean headroom",
                "Slow push-in",
                "2x / telephoto if available",
                "Stronger side or back light; protect skin highlights",
                "Hero"
            )
        }
    }
}
