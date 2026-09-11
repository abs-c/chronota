package app.chronota

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.chronota.data.entity.Plan
import app.chronota.data.entity.Record
import app.chronota.feature.WorkspaceState
import app.chronota.feature.browse.BrowseScreen
import app.chronota.feature.today.TodayScreen
import app.chronota.ui.theme.ChronotaTheme
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Neighbouring calendar blocks are always pulled apart by the shared block inset. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CalendarBlockSpacingTest {
    @get:Rule val compose = createComposeRule()

    /**
     * Six quarter-hour entries, some back to back and some overlapping, inside one hour.
     *
     * [date] defaults to today for the timeline test. The calendar test asks for the same day a week
     * back and turns the calendar back to it: the calendar keeps the records that have already
     * started, so entries a week from now would not be drawn at all.
     */
    private fun state(date: LocalDate = LocalDate.now()): WorkspaceState {
        val zone = ZoneId.systemDefault()
        val start = date.atTime(10, 0).atZone(zone).toInstant()
        return WorkspaceState(loading = false,
            records = (0 until 6).map { index ->
                val offset = index / 2 * 900L
                Record(id = index + 1L, title = "Block $index", startTime = start.plusSeconds(offset), endTime = start.plusSeconds(offset + 900L))
            },
            plans = (0 until 6).map { index ->
                val offset = index / 2 * 900L
                val from = start.plusSeconds(offset).atZone(zone)
                Plan(id = index + 1L, title = "Plan $index", scheduledDate = date, startTime = from.toLocalTime(),
                    endTime = from.plusSeconds(900).toLocalTime(), zoneId = zone.id)
            })
    }

    private fun assertBlocksNeverTouch() {
        val blocks = compose.onAllNodesWithTag("event_block").fetchSemanticsNodes().map { it.boundsInRoot }
        assertTrue("Expected several blocks, got ${blocks.size}", blocks.size >= 4)
        blocks.forEachIndexed { index, block ->
            blocks.drop(index + 1).forEach { other -> assertTrue("Blocks touch: $block / $other", apart(block, other)) }
        }
    }

    /** True when the two rectangles are separated on at least one axis. */
    private fun apart(a: Rect, b: Rect): Boolean =
        a.right <= b.left || b.right <= a.left || a.bottom <= b.top || b.bottom <= a.top

    @Test fun todayBlocksNeverTouch() {
        compose.setContent { ChronotaTheme(false) { TodayScreen({}, state()) } }
        compose.waitForIdle()
        assertBlocksNeverTouch()
    }

    @Test fun weekBlocksNeverTouch() {
        compose.setContent { ChronotaTheme(false) { BrowseScreen(true, state(LocalDate.now().minusDays(7)), {}, {}, {}) } }
        compose.onNodeWithTag("browse_mode_1").performClick()
        compose.onNodeWithTag("calendar_scale_1").performClick()
        compose.onNodeWithContentDescription("Previous period").performClick()
        compose.onNodeWithTag("calendar_week").assertExists()
        compose.waitForIdle()
        assertBlocksNeverTouch()
    }
}
