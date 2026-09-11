package app.chronota

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.chronota.feature.browse.ModeTabs
import app.chronota.ui.components.AppFilterChip
import app.chronota.ui.theme.Metrics
import app.chronota.ui.theme.ChronotaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The timeline category chips must be exactly as tall as the day/week/month segments. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModeTabsHeightTest {
    @get:Rule val compose = createComposeRule()

    @Test fun filterChipMatchesSegmentHeight() {
        compose.setContent { ChronotaTheme(false) {
            Column {
                AppFilterChip(true, {}, "Chip")
                ModeTabs(listOf(R.string.day, R.string.week), 0, {}, "probe")
            }
        } }
        val chip = compose.onNodeWithText("Chip").fetchSemanticsNode().boundsInRoot.height
        val label = compose.onNodeWithText("Chip", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.height
        val tab = compose.onNodeWithTag("probe_0").fetchSemanticsNode().boundsInRoot.height
        assertEquals(tab, chip, .01f)
        // The label must render at its full line height, otherwise it is clipped by the chip.
        assertEquals(Metrics.chipLineHeight.value, label, .01f)
    }
}
