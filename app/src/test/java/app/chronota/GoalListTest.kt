package app.chronota

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.chronota.data.entity.*
import app.chronota.feature.WorkspaceState
import app.chronota.feature.todo.GoalList
import app.chronota.ui.theme.ChronotaTheme
import org.junit.Assert.assertEquals
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Retired instances leave the running list and are collected under the ended entry. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GoalListTest {
    @get:Rule val compose = createComposeRule()

    /** The expired instance is a goal of its own, exactly like the one that is still running. */
    private fun state() = WorkspaceState(loading = false,
        goals = listOf(
            Goal(id = 1, name = "Running goal", target = 3_600_000, periodUnit = GoalUnit.DAY, repeat = GoalRepeat.CYCLE, startAt = Instant.now().minusSeconds(3_600)),
            Goal(id = 2, name = "Retired goal", target = 3_600_000, repeat = GoalRepeat.NONE,
                startAt = Instant.now().minusSeconds(172_800), expiredAt = Instant.now().minusSeconds(86_400)),
        ))

    @Test fun expiredInstancesMoveToTheEndedEntryAndCanBeDeletedOnTheirOwn() {
        var goals by mutableStateOf(state().goals)
        val deleted = mutableListOf<Long>()
        compose.setContent {
            ChronotaTheme(false) {
                GoalList(WorkspaceState(loading = false, goals = goals), {}, {}, { id -> deleted += id; goals = goals.filterNot { it.id == id } })
            }
        }
        compose.onNodeWithText("Running goal").assertIsDisplayed()
        compose.onNodeWithText("Ended").assertIsDisplayed().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Retired goal").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Retired goal").assertIsDisplayed()
        compose.onNodeWithText("Running goal").assertDoesNotExist()
        // Deleting the expired one asks first, then removes only that instance.
        compose.onNodeWithContentDescription("Delete").performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Retired goal").fetchSemanticsNodes().isEmpty() }
        assertEquals(listOf(2L), deleted)
        assertEquals(listOf(1L), goals.map { it.id })
    }
}
