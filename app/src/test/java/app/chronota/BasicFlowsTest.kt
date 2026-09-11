package app.chronota

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BasicFlowsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun manualRecordCanBeCreatedWithoutAPlan() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("nav_review").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav_review").performClick()
        compose.onNodeWithTag("add_record").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("record_title").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("record_title").performTextInput("Unplanned walk")
        compose.onNodeWithTag("save").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("record_title").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("review_list").performScrollToNode(hasText("Unplanned walk"))
        compose.onNodeWithTag("review_list").performTouchInput { swipeUp() }
        compose.onNodeWithText("Unplanned walk").assertIsDisplayed().performClick()
        compose.onNodeWithText("Linked plan").assertDoesNotExist()
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Unplanned walk").fetchSemanticsNodes().isEmpty() }
    }
    @Test fun createEditAndDeletePlanThroughRealDatabase() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("nav_todo").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav_todo").performClick()

        compose.onNodeWithTag("add_plan").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("plan_title").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("plan_title").performTextInput("Read a chapter")
        compose.onNodeWithTag("save").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Read a chapter").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithTag("plan_title").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Read a chapter").performClick()
        compose.onNodeWithTag("plan_title").performTextReplacement("Read two chapters")
        compose.onNodeWithTag("save").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("plan_title").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Read two chapters").performClick()
        compose.onNodeWithText("Delete").performScrollTo().performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Read two chapters").fetchSemanticsNodes().isEmpty() }
    }
}
