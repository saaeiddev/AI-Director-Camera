import com.saeid.aidirectorcamera.ai.GuidanceEngine
import com.saeid.aidirectorcamera.ai.GuidanceInput
import com.saeid.aidirectorcamera.ai.NormRect
import com.saeid.aidirectorcamera.director.DirectorPlanner
import com.saeid.aidirectorcamera.director.StoryboardPlanner

fun main() {
    check(GuidanceEngine.recommendation(GuidanceInput(null, 128.0, 0.0, 7f, 90f)).contains("horizon", ignoreCase = true))
    check(GuidanceEngine.recommendation(GuidanceInput(NormRect(.3f,.2f,.7f,.8f), 35.0, 0.0, 0f, 90f)).contains("dark", ignoreCase = true))
    check(GuidanceEngine.compositionConsistency(GuidanceInput(NormRect(.25f,.2f,.45f,.7f), 120.0, 0.0, 0f, 95f)) in 0..100)
    check(DirectorPlanner.plan("cinematic car hero shot").shot.contains("hero", ignoreCase = true))
    check(DirectorPlanner.plan("horror scene").movement.contains("push", ignoreCase = true))
    check(StoryboardPlanner.create("A man walks to his car, opens the door and drives away").size >= 4)
    println("PURE LOGIC SMOKE TESTS PASSED")
}
