package app.chronotation

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.chronotation.data.entity.Plan
import app.chronotation.data.entity.Record
import app.chronotation.feature.WorkspaceState
import app.chronotation.feature.browse.BrowseScreen
import app.chronotation.feature.today.TodayScreen
import app.chronotation.ui.theme.PlanRecordTheme
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

    /** Six quarter-hour entries, some back to back and some overlapping, inside one hour. */
    private fun state(): WorkspaceState {
        val zone = ZoneId.systemDefault()
        val date = LocalDate.now()
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
        compose.setContent { PlanRecordTheme(false) { TodayScreen({}, state()) } }
        compose.waitForIdle()
        assertBlocksNeverTouch()
    }

    @Test fun weekBlocksNeverTouch() {
        compose.setContent { PlanRecordTheme(false) { BrowseScreen(false, state(), {}, {}, {}) } }
        compose.onNodeWithTag("browse_mode_1").performClick()
        compose.onNodeWithTag("calendar_scale_1").performClick()
        compose.onNodeWithTag("calendar_week").assertExists()
        compose.waitForIdle()
        assertBlocksNeverTouch()
    }
}
