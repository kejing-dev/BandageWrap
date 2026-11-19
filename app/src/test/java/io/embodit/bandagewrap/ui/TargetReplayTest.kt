package io.embodit.bandagewrap.ui

import io.embodit.bandagewrap.ui.input.parseLoggedPath
import io.embodit.bandagewrap.ui.input.resolvePath
import io.embodit.bandagewrap.ui.scene.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** board-input hit replay (factory rule 2026-11-06): real hit paths resolve to the app's own targets. */
class TargetReplayTest {
    @Test
    fun hitPathsResolve() {
        // the arm mesh hangs below the poseNode; the collider holder is a sibling named hit:arm
        assertEquals(Target.ARM, resolvePath(parseLoggedPath("hit:arm < poseNode < armRig < stageRoot")) { Target.ofName(it) })
        assertEquals(Target.WRIST_HANDLE, resolvePath(parseLoggedPath("hit:wristHandle < stageRoot")) { Target.ofName(it) })
        assertEquals(Target.ELBOW_HANDLE, resolvePath(listOf("hit:elbowHandle")) { Target.ofName(it) })
        assertNull(resolvePath(parseLoggedPath("turn:3 < poseNode < armRig < stageRoot")) { Target.ofName(it) })
    }
}
