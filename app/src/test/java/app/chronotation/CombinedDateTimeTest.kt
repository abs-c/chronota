package app.chronotation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.chronotation.ui.components.CalendarDialog
import app.chronotation.ui.components.ClockDialog
import app.chronotation.ui.theme.PlanRecordTheme
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
class CombinedDateTimeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun dateAndTimeAreSubmittedTogetherOnlyAfterConfirmation() {
        var result: LocalDateTime? = null
        compose.setContent { PlanRecordTheme(false) {
            CalendarDialog("Start", LocalDate.of(2026, 9, 8), {}, time = LocalTime.of(12, 30),
                selectDateTime = { day, time -> result = day.atTime(time) }) { fail("Date-only callback") }
        } }
        compose.onNodeWithTag("calendar_2026-09-09").performClick()
        compose.onNodeWithTag("wheel_hour_13").performClick()
        compose.onNodeWithTag("wheel_minute_32").performClick()
        assertNull(result)
        compose.onNodeWithText("Confirm").performClick()
        assertEquals(LocalDateTime.of(2026, 9, 9, 13, 32), result)
    }

    @Test fun clockDialogUsesWheelSelection() {
        var result: LocalTime? = null
        compose.setContent { PlanRecordTheme(false) { ClockDialog("Day start", LocalTime.of(4, 0), {}) { result = it } } }
        compose.onNodeWithTag("wheel_hour_05").performClick()
        compose.onNodeWithTag("wheel_minute").performScrollToIndex(30)
        compose.onNodeWithTag("wheel_minute_30").performClick()
        assertNull(result)
        compose.onNodeWithText("Confirm").performClick()
        assertEquals(LocalTime.of(5, 30), result)
    }
}
