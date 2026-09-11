package app.chronota

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.chronota.data.entity.Plan
import app.chronota.feature.WorkspaceState
import app.chronota.feature.browse.BrowseScreen
import app.chronota.ui.theme.Metrics
import app.chronota.ui.theme.ChronotaTheme
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Blocks keep the minimum height while nothing follows, squeeze into the room when a neighbour is
 * close behind, and go side by side once not even the floor height fits.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TimelineSqueezeTest {
    @get:Rule val compose = createComposeRule()

    private fun state(vararg spans: Pair<LocalTime, LocalTime>): WorkspaceState {
        val date = LocalDate.now()
        return WorkspaceState(loading = false, plans = spans.mapIndexed { index, (start, end) ->
            Plan(id = index + 1L, title = "Block $index", scheduledDate = date, startTime = start, endTime = end, zoneId = ZoneId.systemDefault().id)
        })
    }

    private fun blockRects(): List<androidx.compose.ui.geometry.Rect> =
        compose.onAllNodesWithTag("event_block").fetchSemanticsNodes().map { it.boundsInRoot }.filter { it.height > 0f }

    /** What a block of [height] measures once its gap inset is taken off. */
    private fun drawn(height: androidx.compose.ui.unit.Dp): Float = with(compose.density) { (height - Metrics.eventGap * 2).toPx() }
    private fun minHeight(): Float = drawn(Metrics.hourHeight / 3)
    private fun twelveMinutes(): Float = drawn(Metrics.hourHeight * 12f / 60f)

    @Test fun aCloseNeighbourSqueezesTheBlockInFrontOfIt() {
        // 09:00 for one minute, then 09:12: twelve minutes of room, above the ten-minute floor.
        compose.setContent { ChronotaTheme(false) { BrowseScreen(true, state(LocalTime.of(9, 0) to LocalTime.of(9, 1), LocalTime.of(9, 12) to LocalTime.of(9, 13)), {}, {}, {}) } }
        compose.onNodeWithTag("browse_mode_1").performClick()
        compose.onNodeWithTag("calendar_scale_1").performClick()
        val blocks = blockRects().sortedBy { it.top }
        assertEquals(2, blocks.size)
        // Squeezed block: twelve minutes of room, not the twenty-minute minimum.
        assertEquals(twelveMinutes(), blocks[0].height, 1f)
        assertEquals(minHeight(), blocks[1].height, 1f)
        // Sharing one lane, so they are stacked rather than side by side.
        assertEquals(blocks[0].left, blocks[1].left, 1f)
    }

    @Test fun aTouchingNeighbourKeepsTheBlockInItsOwnSlot() {
        // 09:00-09:05 and 09:05-09:20: the next starts exactly when this one ends.
        compose.setContent { ChronotaTheme(false) { BrowseScreen(true, state(LocalTime.of(9, 0) to LocalTime.of(9, 5), LocalTime.of(9, 5) to LocalTime.of(9, 20)), {}, {}, {}) } }
        compose.onNodeWithTag("browse_mode_1").performClick()
        compose.onNodeWithTag("calendar_scale_1").performClick()
        val blocks = blockRects().sortedBy { it.top }
        assertEquals(2, blocks.size)
        // They share a lane — stacked, never side by side — and the front one still gets the floor.
        assertEquals(drawn(Metrics.hourHeight * Metrics.eventFloorMinutes / 60f), blocks[0].height, 1f)
        assertEquals(minHeight(), blocks[1].height, 1f)
        assertEquals(blocks[0].left, blocks[1].left, 1f)
        assertTrue(blocks[1].top > blocks[0].top)
    }

    @Test fun onlyGenuinelyOverlappingBlocksGoSideBySide() {
        // 09:00-09:10 and 09:05-09:15 really do overlap, so they take a lane each.
        compose.setContent { ChronotaTheme(false) { BrowseScreen(true, state(LocalTime.of(9, 0) to LocalTime.of(9, 10), LocalTime.of(9, 5) to LocalTime.of(9, 15)), {}, {}, {}) } }
        compose.onNodeWithTag("browse_mode_1").performClick()
        compose.onNodeWithTag("calendar_scale_1").performClick()
        val blocks = blockRects().sortedBy { it.left }
        assertEquals(2, blocks.size)
        assertTrue(blocks[1].left > blocks[0].right - 1f)
    }

    @Test fun aTimerStoppedOnASecondDoesNotSplitTheLane() {
        // A timer stops on a second, so 09:00-09:05:12 laps 12s over the 09:05:00 entry beside it.
        // That is a fifth of a dp: invisible, so the two must stack rather than sit side by side.
        compose.setContent { ChronotaTheme(false) { BrowseScreen(true, state(
            LocalTime.of(9, 0, 0) to LocalTime.of(9, 5, 12),
            LocalTime.of(9, 5, 0) to LocalTime.of(9, 20)), {}, {}, {}) } }
        compose.onNodeWithTag("browse_mode_1").performClick()
        compose.onNodeWithTag("calendar_scale_1").performClick()
        val blocks = blockRects().sortedBy { it.top }
        assertEquals(2, blocks.size)
        assertEquals("seconds must not become a second column", blocks[0].left, blocks[1].left, 1f)
        assertTrue(blocks[1].top > blocks[0].top)
    }

    @Test fun aWholeMinuteOfOverlapStillTakesTwoColumns() {
        // 09:00-09:06:12 against 09:05:00 is more than a minute of real overlap, so it shows.
        compose.setContent { ChronotaTheme(false) { BrowseScreen(true, state(
            LocalTime.of(9, 0, 0) to LocalTime.of(9, 6, 12),
            LocalTime.of(9, 5, 0) to LocalTime.of(9, 20)), {}, {}, {}) } }
        compose.onNodeWithTag("browse_mode_1").performClick()
        compose.onNodeWithTag("calendar_scale_1").performClick()
        val blocks = blockRects().sortedBy { it.left }
        assertEquals(2, blocks.size)
        assertTrue(blocks[1].left > blocks[0].right - 1f)
    }
}
