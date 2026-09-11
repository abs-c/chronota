package app.chronota

import androidx.compose.ui.test.*
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import app.chronota.feature.today.wheelHit
import androidx.compose.ui.test.junit4.createComposeRule
import app.chronota.domain.OrbAction
import app.chronota.feature.today.FloatingOrb
import app.chronota.ui.theme.ChronotaTheme
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OrbTest {
    @get:Rule val compose = createComposeRule()
    @Test fun wheelTargetsAndOutsideCancellation() {
        assertEquals(OrbAction.PLAN, wheelHit(Offset(-176f, 0f), 176f, 32f))
        assertEquals(OrbAction.GOAL, wheelHit(Offset(-152.4f, -88f), 176f, 32f))
        assertEquals(OrbAction.RECORD, wheelHit(Offset(-88f, -152.4f), 176f, 32f))
        assertEquals(OrbAction.TIMER, wheelHit(Offset(0f, -176f), 176f, 32f))
        assertNull(wheelHit(Offset.Zero, 176f, 32f))
        assertNull(wheelHit(Offset(-300f, -300f), 176f, 32f))
    }
    @Test fun tapUsesPreferenceAndReleasingWithoutSelectionClosesWheel() {
        var action: OrbAction? = null
        compose.setContent { ChronotaTheme(false) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) { FloatingOrb(null, OrbAction.PLAN, { action = it }, {}) } } }
        compose.onNodeWithContentDescription("Open actions").performClick()
        assertEquals(OrbAction.PLAN, action)
        action = null
        compose.onNodeWithContentDescription("Open actions").performTouchInput { longClick() }
        assertNull(action)
        compose.onNodeWithTag("wheel_RECORD").assertDoesNotExist()
    }
    @Test fun holdDragAndReleaseExecutesHighlightedAction() {
        var action: OrbAction? = null
        compose.setContent { ChronotaTheme(false) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) { FloatingOrb(null, OrbAction.PLAN, { action = it }, {}) } } }
        val orb = compose.onNodeWithContentDescription("Open actions")
        orb.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("wheel_RECORD").assertExists()
        val origin = orb.fetchSemanticsNode().boundsInRoot.center
        val target = compose.onNodeWithTag("wheel_RECORD").fetchSemanticsNode().boundsInRoot.center
        orb.performTouchInput { moveTo(center + target - origin); up() }
        assertEquals(OrbAction.RECORD, action)
        compose.onNodeWithTag("wheel_RECORD").assertDoesNotExist()
    }
}
