package app.chronota

import androidx.compose.material3.Text
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.chronota.data.entity.*
import app.chronota.domain.*
import app.chronota.feature.WorkspaceState
import app.chronota.feature.browse.BrowseScreen
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.chronota.data.repository.AppPreferences
import app.chronota.ui.components.LocalDisplayPreferences
import app.chronota.ui.theme.Metrics
import app.chronota.ui.theme.ChronotaTheme
import java.time.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BrowseTest {
    @get:Rule val compose = createComposeRule()

    @Test fun calendarOverlayAndScaleNavigationKeepIndependentItems() {
        val day = LocalDate.now()
        val start = Instant.now().minusSeconds(1200)
        val state = WorkspaceState(loading = false,
            plans = listOf(Plan(id = 1, title = "Planned reading", allDay = true, scheduledDate = day, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(11, 0))),
            records = listOf(Record(id = 1, title = "Actual reading", startTime = start, endTime = start.plusSeconds(600))))
        var opened: Long? = null
        // Whether a calendar draws plans is a setting now, so the screen is handed it directly.
        var showsPlans by mutableStateOf(false)
        compose.setContent {
            ChronotaTheme(false) {
                CompositionLocalProvider(LocalDisplayPreferences provides AppPreferences(recordsCalendarPlans = showsPlans)) {
                    BrowseScreen(true, state, {}, { opened = it }, {}, statistics = { Text("Report content") })
                }
            }
        }
        compose.onNodeWithText("Actual reading").performClick()
        assertEquals(1L, opened)
        compose.onNodeWithTag("browse_mode_1").performClick()
        compose.onNodeWithTag("calendar_month").assertExists()
        compose.onNodeWithText("Planned reading").assertDoesNotExist()
        showsPlans = true
        compose.onNodeWithText("Planned reading").assertExists()
        compose.onNodeWithTag("calendar_scale").performClick()
        compose.onNodeWithTag("calendar_scale_0").performClick()
        compose.onNodeWithTag("calendar_day").assertExists()
        compose.onNodeWithTag("calendar_scale").performClick()
        compose.onNodeWithTag("calendar_scale_1").performClick()
        compose.onNodeWithTag("calendar_week").assertExists()
        compose.onNodeWithTag("browse_mode_2").performClick()
        compose.onNodeWithText("Report content").assertIsDisplayed()
    }

    /** Review reads forward from the oldest day and shows the details without being asked. */
    @Test fun reviewTimelineStartsAscendingWithDetailsOpen() {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val state = WorkspaceState(loading = false,
            definitions = listOf(PropertyDefinition(id = 5, categoryId = 2, name = "Format", type = PropertyType.SELECT, options = "Book\nPaper")),
            records = listOf(
                Record(id = 1, title = "Older entry", categoryId = 2, startTime = yesterday.atTime(9, 0).toInstant(ZoneOffset.UTC), endTime = yesterday.atTime(10, 0).toInstant(ZoneOffset.UTC)),
                Record(id = 2, title = "Newer entry", categoryId = 2, startTime = today.atTime(9, 0).toInstant(ZoneOffset.UTC), endTime = today.atTime(10, 0).toInstant(ZoneOffset.UTC)),
            ),
            values = listOf(PropertyValue(2, 5, "Book")))
        compose.setContent { ChronotaTheme(false) { BrowseScreen(true, state, {}, {}, {}) } }
        // Oldest first, and its attributes are already open.
        val older = compose.onNodeWithText("Older entry").fetchSemanticsNode().boundsInRoot
        val newer = compose.onNodeWithText("Newer entry").fetchSemanticsNode().boundsInRoot
        assertTrue("Older entry should sit above the newer one", older.top < newer.top)
        compose.onNodeWithText("Format").assertExists()
        compose.onNodeWithText("Book").assertExists()
    }

    /** The node dot meets the start's inner edge on the card icon's line, one time or two. */
    @Test fun timelineNodeHugsTheLargerTimeOnTheIconLine() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        fun at(hour: Int, minute: Int) = today.atTime(hour, minute).atZone(zone).toInstant()
        val state = WorkspaceState(loading = false, records = listOf(
            Record(id = 1, title = "Ranged", startTime = at(9, 0), endTime = at(10, 30)),
            Record(id = 2, title = "Moment", startTime = at(11, 0), endTime = at(11, 0))))
        compose.setContent { ChronotaTheme(false) { BrowseScreen(true, state, {}, {}, {}) } }
        val iconLine = with(compose.density) { Metrics.agendaNodeCenter.toPx() }
        val gap = with(compose.density) { Metrics.agendaTimeGap.toPx() }
        fun card(title: String) = compose.onNode(hasTestTag("agenda_card") and hasText(title)).fetchSemanticsNode().boundsInRoot
        fun time(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot
        // Read forwards the start is on top, so the dot meets its bottom edge.
        assertEquals(card("Ranged").top + iconLine, time("09:00").bottom, 1f)
        assertEquals(gap, time("10:30").top - time("09:00").bottom, 1f)
        // A moment has one line, centred on the dot.
        assertEquals(card("Moment").top + iconLine, time("11:00").center.y, 1f)
        // Reversed, the start drops below the end and the dot meets its top edge instead.
        compose.onNodeWithTag("agenda_sort").performClick()
        assertEquals(card("Ranged").top + iconLine, time("09:00").top, 1f)
    }

    @Test fun calendarIncludesMidnightCrossingsButExcludesExclusiveEndDay() {
        val zone = ZoneOffset.UTC
        val first = LocalDate.of(2026, 9, 8)
        val start = first.atTime(23, 30).toInstant(zone)
        val entries = browseEntries(listOf(Plan(id = 1, title = "Inbox")),
            listOf(Record(id = 1, title = "Night", startTime = start, endTime = start.plusSeconds(3600))), zone)
        assertEquals(2, entries.map { it.key }.distinct().size)
        assertFalse(entries.first().onDate(first, zone))
        assertTrue(entries.last().onDate(first, zone))
        assertTrue(entries.last().onDate(first.plusDays(1), zone))
        assertFalse(entries.last().copy(span = TimeSpan(start, first.plusDays(1).atStartOfDay().toInstant(zone))).onDate(first.plusDays(1), zone))
    }
}
