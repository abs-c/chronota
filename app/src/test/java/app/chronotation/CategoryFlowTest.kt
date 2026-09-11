package app.chronotation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.chronotation.data.entity.Record
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CategoryFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun categoryGridCreatesChildInSelectedGroup() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("nav_settings").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithText("Categories").performClick()
        compose.onNodeWithTag("category_list").performScrollToNode(hasTestTag("add_category"))
        compose.onNodeWithTag("add_category").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("category_name").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("category_name").performTextInput("Study")
        compose.onNodeWithTag("save").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("category_name").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithTag("category_list").performScrollToNode(hasText("Study"))
        compose.onAllNodesWithText("Add subcategory").onLast().performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("category_name").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("category_name").performTextInput("Reading")
        compose.onNodeWithTag("save").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("category_name").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Reading").assertIsDisplayed()
        compose.onNodeWithText("Study").performClick()
        compose.onNodeWithText("Reading").assertDoesNotExist()
        compose.onNodeWithText("Study").performClick()
        compose.onNodeWithText("Reading").assertIsDisplayed().performClick()
        compose.onNodeWithText("Task attributes").performScrollTo().assertIsDisplayed()
    }

    @Test fun removingUsedAttributeWarnsOnceAndOnlyThenDropsItsValues() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("nav_settings").fetchSemanticsNodes().isNotEmpty() }
        val app = compose.activity.application as PlanRecordApplication
        var cinema = 0L
        compose.waitUntil(15_000) {
            cinema = kotlinx.coroutines.runBlocking { app.database.dao().allCategories().firstOrNull { it.name == "观影" }?.id } ?: 0L
            cinema != 0L
        }
        kotlinx.coroutines.runBlocking {
            val definition = app.database.dao().allDefinitions().first { it.name == "类型" }
            app.repository.saveRecord(Record(title = "Film", categoryId = cinema,
                startTime = java.time.Instant.now().minusSeconds(60), endTime = java.time.Instant.now()), mapOf(definition.id to "电影"))
        }
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithText("Categories").performClick()
        compose.onNodeWithTag("category_list").performScrollToNode(hasText("观影"))
        compose.onNodeWithText("观影").performClick()
        compose.onNodeWithText("类型").performScrollTo().performClick()
        // Deleting the attribute only edits the draft; the saved values are still untouched.
        // The property editor is the topmost dialog, so its Delete button is the last one.
        compose.onAllNodesWithText("Delete").onLast().performScrollTo().performClick()
        compose.onNodeWithText("Confirm").performClick()
        compose.onNodeWithTag("save").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Attribute in use").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1 saved value belongs to a choice or type this edit removes. Saving deletes it.").assertIsDisplayed()
        compose.onNodeWithTag("confirm_property_loss").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("save").fetchSemanticsNodes().isEmpty() }
        kotlinx.coroutines.runBlocking {
            val names = app.database.dao().allDefinitions().filter { it.categoryId == cinema }.map { it.name }
            assertFalse(names.contains("类型"))
            assertTrue(app.database.dao().allValues().none { it.value == "电影" })
        }
    }
}
