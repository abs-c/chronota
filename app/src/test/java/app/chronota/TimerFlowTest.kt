package app.chronota

import app.chronota.data.entity.TimerMode
import app.chronota.feature.WorkspaceViewModel
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TimerFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun activeTimerSurvivesActivityRecreationAndCanFinish() {
        org.robolectric.Shadows.shadowOf(compose.activity.application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("nav_today").fetchSemanticsNodes().isNotEmpty() }
        // The orb's default tap is "new record", so pick the timer from the long-press wheel.
        val orb = compose.onNodeWithContentDescription("Open actions")
        orb.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(700)
        compose.onNodeWithTag("wheel_TIMER").assertExists()
        val origin = orb.fetchSemanticsNode().boundsInRoot.center
        val target = compose.onNodeWithTag("wheel_TIMER").fetchSemanticsNode().boundsInRoot.center
        orb.performTouchInput { moveTo(center + target - origin); up() }
        compose.onNodeWithTag("timer_title").performTextInput("Focused reading")
        compose.onNodeWithTag("timer_title").assertTextContains("Focused reading")
        compose.onNodeWithTag("timer_start").performScrollTo()
        // Exercise the accessibility click action independently of dialog animation timing.
        compose.onNodeWithTag("timer_start").performSemanticsAction(SemanticsActions.OnClick) { it() }
        try { compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Timer").fetchSemanticsNodes().isNotEmpty() } }
        catch (failure: Throwable) {
            val app = compose.activity.application as ChronotaApplication
            println("TIMER_DB=" + kotlinx.coroutines.runBlocking { app.database.dao().currentTimer() })
            val vm = androidx.lifecycle.ViewModelProvider(compose.activity)[WorkspaceViewModel::class.java]
            println("TIMER_STATE=" + vm.state.value)
            println("TIMER_BUSY=" + vm.busy.value)
            throw failure
        }
        compose.onNodeWithContentDescription("Timer").performClick()
        // There is no pause any more: a running timer survives recreation on its own.
        compose.onNodeWithTag("timer_finish").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        // Twice over: once on the sheet, and once as the record the day view draws for the running
        // timer — the page behind the sheet shows it too.
        assertEquals(2, compose.onAllNodesWithText("Focused reading").fetchSemanticsNodes().size)
        compose.onNodeWithTag("timer_finish").assertIsDisplayed()
        ShadowSystemClock.advanceBy(Duration.ofSeconds(90))
        compose.onNodeWithTag("timer_finish").performClick()
        // The timer's record opens for review before it is stored.
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("save").fetchSemanticsNodes().isNotEmpty() }
        // The editor carries the title, and the timer is still running behind it, so its record is up.
        assertTrue(compose.onAllNodesWithText("Focused reading").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag("save").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("save").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithContentDescription("Open actions").assertIsDisplayed()
    }

    @Test fun finishingATimerOpensItsRecordWithAttributesAndTimes() {
        org.robolectric.Shadows.shadowOf(compose.activity.application).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("nav_today").fetchSemanticsNodes().isNotEmpty() }
        val app = compose.activity.application as ChronotaApplication
        var cinema = 0L
        compose.waitUntil(15_000) {
            cinema = runBlocking { app.database.dao().allCategories().firstOrNull { it.name == "观影" }?.id } ?: 0L
            cinema != 0L
        }
        runBlocking { app.timers.start("Reading", cinema, null, TimerMode.TIMER, 25, 5, 4, emptyMap()) }
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Timer").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Timer").performClick()
        // The running timer carries no attribute fields.
        compose.onNodeWithTag("timer_finish").assertIsDisplayed()
        compose.onAllNodesWithText("类型", substring = true).assertCountEquals(0)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(120))
        compose.onNodeWithTag("timer_finish").performClick()
        // Ending it opens the ordinary record editor, pre-filled from the timer.
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("save").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("New record").assertIsDisplayed()
        compose.onAllNodesWithText("观影").assertCountEquals(1)
        compose.onNodeWithText("类型 *").assertIsDisplayed()
        // Saving without the required attribute is refused, exactly like any other new record.
        compose.onNodeWithTag("save").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Please fill in the required attributes").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("OK").performClick()
        compose.onNodeWithText("类型 *").performClick()
        compose.onNodeWithText("电影").performClick()
        compose.onNodeWithTag("save").performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("save").fetchSemanticsNodes().isEmpty() }
        val record = runBlocking { app.database.dao().allRecords().maxBy { it.id } }
        assertEquals(cinema, record.categoryId)
        assertEquals("Reading", record.title)
        assertEquals("电影", runBlocking { app.database.dao().recordValues(record.id) }.single().value)
        compose.onNodeWithContentDescription("Open actions").assertIsDisplayed()
    }
}
