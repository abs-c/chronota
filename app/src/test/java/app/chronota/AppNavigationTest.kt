package app.chronota

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import app.chronota.domain.ThemeMode
import app.chronota.ui.ChronotaApp
import app.chronota.ui.theme.ChronotaTheme
import java.io.File
import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = ApplicationProvider.getApplicationContext<ChronotaApplication>()

    private fun launch() {
        compose.setContent {
            var mode by remember { mutableStateOf(ThemeMode.SYSTEM) }
            ChronotaTheme(mode.isDark(false)) {
                ChronotaApp(mode, { mode = it }, remember { SnackbarHostState() })
            }
        }
    }

    @Test fun fourTabsAndCategoryBackPreserveOrigin() {
        launch()
        compose.onNodeWithTag("nav_today").assertIsSelected()
        saveScreenshot("today-light-en")
        compose.onNodeWithTag("nav_todo").performClick().assertIsSelected()
        compose.onNodeWithTag("nav_settings").performClick().assertIsSelected()
        compose.onNodeWithText(context.getString(R.string.categories)).performClick()
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithTag("category_list").performScrollToNode(hasTestTag("add_category"))
        compose.onNodeWithTag("add_category").assertIsDisplayed()
        compose.onNodeWithTag("nav_today").assertDoesNotExist()
        compose.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
        compose.onNodeWithTag("nav_settings").assertIsSelected()
        compose.onNodeWithTag("nav_review").performClick().assertIsSelected()
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithText(context.getString(R.string.appearance)).performScrollTo().performClick()
        compose.onNodeWithTag("theme_DARK").performClick().assertIsSelected()
        saveScreenshot("settings-dark-en")
        compose.onNodeWithText("Confirm").performClick()
        compose.onNodeWithTag("nav_review").performClick()
        compose.onNodeWithTag("nav_review").assertIsSelected()
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    fun chineseResourcesAreUsedInNavigationAndSettings() {
        launch()
        compose.onNodeWithTag("nav_todo").performClick()
        compose.onNodeWithContentDescription("新计划").assertIsDisplayed()
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithText("外观").performScrollTo().performClick()
        compose.onNodeWithTag("theme_SYSTEM").assertIsDisplayed()
        compose.onNodeWithText("深色").assertIsDisplayed()
        saveScreenshot("settings-light-zh")
    }

    private fun saveScreenshot(name: String) {
        val directory = File("build/outputs/screenshots").apply { mkdirs() }
        // Draw the native Robolectric view directly; PixelCopy needs a device frame callback.
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(directory, "$name.png").outputStream().use { stream ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
            }
            bitmap.recycle()
        }
    }
}
