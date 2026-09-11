package app.chronotation

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.Surface
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.chronotation.data.entity.*
import app.chronotation.domain.OrbAction
import app.chronotation.feature.WorkspaceState
import app.chronotation.feature.review.ReviewScreen
import app.chronotation.feature.today.TodayScreen
import app.chronotation.feature.today.FloatingOrb
import app.chronotation.ui.theme.PlanRecordTheme
import java.io.File
import java.time.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "zh-rCN-w400dp-h850dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FeatureScreenshotsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun populated(): WorkspaceState {
        val now = ZonedDateTime.now().withSecond(0).withNano(0)
        fun plan(id: Long, title: String, start: ZonedDateTime, end: ZonedDateTime) = Plan(id = id, title = title, categoryId = 2,
            scheduledDate = start.toLocalDate(), startTime = start.toLocalTime(), endTime = end.toLocalTime(),
            endDayOffset = if (end.toLocalDate() == start.toLocalDate()) 0 else 1, zoneId = now.zone.id)
        return WorkspaceState(loading = false,
            categories = listOf(Category(id = 1, name = "学习"), Category(id = 2, parentId = 1, name = "阅读", color = 0xFF6688CC, icon = "book")),
            plans = listOf(plan(1, "阅读与整理", now.minusMinutes(90), now.minusMinutes(15)), plan(2, "继续写作", now.plusMinutes(15), now.plusMinutes(75))),
            records = listOf(Record(id = 1, title = "阅读笔记", categoryId = 2, sourcePlanId = 1, startTime = now.minusMinutes(80).toInstant(), endTime = now.minusMinutes(10).toInstant()),
                Record(id = 2, title = "散步休息", startTime = now.minusMinutes(30).toInstant(), endTime = now.minusMinutes(5).toInstant())))
    }
    private fun capture(name: String, dark: Boolean, review: Boolean = false) {
        val state = populated()
        compose.setContent {
            PlanRecordTheme(dark) { Surface {
                if (review) ReviewScreen(onSettings = {}, state = state)
                else TodayScreen(onSettings = {}, state = state, orb = { FloatingOrb(null, OrbAction.TIMER, {}, {}) })
            } }
        }
        compose.waitForIdle()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File("build/outputs/screenshots").apply { mkdirs() }.resolve("$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            bitmap.recycle()
        }
    }
    @Test fun populatedTodayLight() = capture("today-populated-light-zh", false)
    @Test fun populatedTodayDark() = capture("today-populated-dark-zh", true)
    @Test fun populatedReview() = capture("review-populated-light-zh", false, true)
}
