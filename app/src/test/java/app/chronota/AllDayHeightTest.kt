package app.chronota

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.chronota.data.entity.Plan
import app.chronota.feature.WorkspaceState
import app.chronota.feature.browse.BrowseScreen
import app.chronota.feature.today.TodayScreen
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

/** The all-day chip is a timeline chip: it must render at the minimum event height in every view. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AllDayHeightTest {
    @get:Rule val compose = createComposeRule()

    /**
     * [date] is a week out for the calendar test: the calendar draws only the plans that have not
     * ended yet, so an entry dated today would drop out of it once the clock passed its hour.
     */
    private fun state(date: LocalDate = LocalDate.now()): WorkspaceState {
        // The shortest possible entry sits at the current time, so the grid never scrolls it away.
        val now = LocalTime.now().withSecond(0).withNano(0)
        return WorkspaceState(loading = false,
            plans = listOf(
                Plan(id = 1, title = "All day", allDay = true, scheduledDate = date, zoneId = ZoneId.systemDefault().id),
                // Today's grid opens near the current time and the week's grid opens at midnight, so
                // one shortest entry sits at each end of the day.
                Plan(id = 2, title = "Tiny", scheduledDate = date, startTime = now, endTime = now.plusMinutes(1), zoneId = ZoneId.systemDefault().id),
                Plan(id = 3, title = "Tiny early", scheduledDate = date, startTime = LocalTime.of(0, 5), endTime = LocalTime.of(0, 6), zoneId = ZoneId.systemDefault().id),
            ))
    }

    private fun drawn(height: androidx.compose.ui.unit.Dp): Float = with(compose.density) { (height - Metrics.eventGap * 2).toPx() }

    /**
     * The all-day chip is drawn at the minimum block height. A block with a neighbour behind it may
     * be squeezed shorter than that, but never below the floor, and one with room still reaches it.
     */
    private fun assertChipMatchesMinimum(chip: androidx.compose.ui.geometry.Rect) {
        val minimum = drawn(Metrics.hourHeight / 3)
        val floor = drawn(Metrics.hourHeight * Metrics.eventFloorMinutes / 60f)
        assertEquals(minimum, chip.height, 1f)
        // A block scrolled fully out of view reports empty bounds, so only laid-out ones count.
        val blocks = compose.onAllNodesWithTag("event_block").fetchSemanticsNodes().map { it.boundsInRoot }.filter { it.height > 0f }
        assertTrue(blocks.isNotEmpty())
        blocks.forEach { assertTrue("block thinner than the floor: $it", it.height >= floor - 1f) }
        assertTrue(blocks.any { it.height >= minimum - 1f })
    }

    @Test fun todayAndDayViewsUseMinimumEventHeight() {
        compose.setContent { ChronotaTheme(false) { TodayScreen({}, state()) } }
        assertChipMatchesMinimum(compose.onNodeWithText("All day").fetchSemanticsNode().boundsInRoot)
    }

    @Test fun weekViewUsesMinimumEventHeight() {
        compose.setContent { ChronotaTheme(false) { BrowseScreen(true, state(LocalDate.now().plusDays(7)), {}, {}, {}) } }
        compose.onNodeWithTag("browse_mode_1").performClick()
        compose.onNodeWithTag("calendar_scale_1").performClick()
        compose.onNodeWithContentDescription("Next period").performClick()
        assertChipMatchesMinimum(compose.onNodeWithText("All day").fetchSemanticsNode().boundsInRoot)
    }
}
