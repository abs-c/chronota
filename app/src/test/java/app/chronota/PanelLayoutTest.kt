package app.chronota

import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.chronota.ui.components.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PanelLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Test fun calendarKeepsSundayVisibleAndConfirmsSelectedDate() {
        var selected: java.time.LocalDate? = null
        compose.setContent { MaterialTheme { CalendarDialog("Date", java.time.LocalDate.of(2026, 9, 8), {}, select = { selected = it }) } }
        compose.onNodeWithTag("calendar_2026-09-06").assertIsDisplayed().performClick()
        compose.onNodeWithText("Confirm").performClick()
        org.junit.Assert.assertEquals(java.time.LocalDate.of(2026, 9, 6), selected)
    }
    @Test fun inputInPanel() {
        compose.setContent { MaterialTheme { var value by remember { mutableStateOf("") }; PanelDialog("Editor", {}) { PlainInput("Title", value, { value = it }) } } }
        compose.onNodeWithContentDescription("Title").performTextInput("Hello")
        compose.onNodeWithText("Hello").assertExists()
    }

    @Test fun nestedSelectionPreservesEditorText() {
        compose.setContent {
            MaterialTheme {
                var value by remember { mutableStateOf("") }
                var options by remember { mutableStateOf(false) }
                PanelDialog("Editor", {}) {
                    PlainInput("Title", value, { value = it })
                    TextButton({ options = true }) { Text("Choose") }
                }
                if (options) SelectionDialog("Options", { options = false }) {
                    SelectionRow("Book", false, { options = false })
                }
            }
        }
        compose.onNodeWithContentDescription("Title").performTextInput("Keep this")
        compose.onNodeWithText("Choose").performClick()
        compose.onNodeWithText("Book").performClick()
        compose.onNodeWithText("Keep this").assertExists()
    }
}
