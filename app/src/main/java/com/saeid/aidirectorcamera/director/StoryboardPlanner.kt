package com.saeid.aidirectorcamera.director

data class StoryboardShot(val number: Int, val name: String, val intent: String)

object StoryboardPlanner {
    fun create(scene: String): List<StoryboardShot> {
        val p = scene.lowercase()
        val shots = mutableListOf(
            StoryboardShot(1, "Wide Establishing Shot", "Define geography and subject position")
        )
        if ("walk" in p || "move" in p) shots += StoryboardShot(shots.size + 1, "Medium Tracking Shot", "Follow the action cleanly")
        if ("car" in p || "door" in p) shots += StoryboardShot(shots.size + 1, "Detail Close-Up", "Capture the hand / door / mechanical action")
        if ("face" in p || "talk" in p || "dialog" in p) shots += StoryboardShot(shots.size + 1, "Medium Close-Up", "Prioritize expression and eyeline")
        if ("drive" in p || "leave" in p || "exit" in p) shots += StoryboardShot(shots.size + 1, "Rear Follow / Exit Shot", "Conclude the motion and direction")
        if (shots.size < 4) {
            shots += StoryboardShot(shots.size + 1, "Close-Up Detail", "Add editorial detail")
            shots += StoryboardShot(shots.size + 1, "Reverse / Cutaway", "Create a clean edit point")
        }
        return shots.take(8)
    }
}
