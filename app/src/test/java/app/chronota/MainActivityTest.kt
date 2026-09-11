package app.chronota

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.core.app.ApplicationProvider
import app.chronota.domain.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun languageChangesImmediatelyAndSurvivesRecreation() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("nav_settings").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("settings_list").performTouchInput { swipeUp() }
        compose.onNodeWithText("Language").performScrollTo().performClick()
        compose.onNodeWithText("简体中文").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("nav_settings").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("语言").fetchSemanticsNodes().isNotEmpty() }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("nav_settings").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(15_000) { compose.onAllNodesWithText("语言").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("语言").performScrollTo().performClick()
        compose.onNodeWithText("English").performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Language").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Language").performScrollTo().performClick()
        compose.onNode(hasText("Follow system") and hasAnyAncestor(isDialog())).performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("Language").fetchSemanticsNodes().isNotEmpty() }
        val preferences = (compose.activity.application as ChronotaApplication).preferencesRepository
        // English and the system language can have identical labels. Keep pumping the UI
        // while the ViewModel coroutine writes the preference, rather than blocking it.
        compose.waitUntil(15_000) { runBlocking { preferences.preferences.first().language.isEmpty() } }
    }

    @Test fun activityRecreationKeepsNavigationAndSavedTheme() {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("nav_today").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("nav_review").performClick()
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithText(compose.activity.getString(R.string.appearance)).performScrollTo().performClick()
        compose.onNodeWithTag("theme_DARK").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("theme_DARK").fetchSemanticsNodes()
                .any { it.config[SemanticsProperties.Selected] }
        }
        val application = ApplicationProvider.getApplicationContext<ChronotaApplication>()
        runBlocking {
            val stored = withTimeout(15_000) {
                application.preferencesRepository.preferences.first { it.themeMode == ThemeMode.DARK }
            }
            assertEquals(ThemeMode.DARK, stored.themeMode)
        }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("theme_DARK").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("theme_DARK").assertIsSelected()
        compose.onNodeWithText("Confirm").performClick()
        compose.onNodeWithTag("nav_settings").assertIsSelected()
        compose.onNodeWithTag("nav_review").performClick()
        compose.onNodeWithTag("nav_review").assertIsSelected()
    }
}
