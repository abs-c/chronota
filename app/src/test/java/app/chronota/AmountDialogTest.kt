package app.chronota

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.chronota.ui.components.AmountDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS-w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AmountDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun confirmAppliesTheTypedNumber() {
        var confirmed: Pair<Int, Int>? = null
        compose.setContent {
            MaterialTheme {
                AmountDialog("Target", 10, 0, listOf(0, 1), {}, label = { it.toString() }, confirm = { value, unit -> confirmed = value to unit })
            }
        }
        // The field starts at the amount the editor holds, and typing replaces it.
        compose.onNode(hasSetTextAction()).performTextReplacement("1")
        compose.onNodeWithText("Confirm").performClick()
        assertEquals(1, confirmed!!.first)
    }
}
