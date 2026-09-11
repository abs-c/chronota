package app.chronotation

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.chronotation.feature.settings.SettingsViewModel
import app.chronotation.ui.PlanRecordApp
import app.chronotation.ui.theme.PlanRecordTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onResume() {
        super.onResume()
        val app = application as PlanRecordApplication
        app.applicationScope.launch { app.timers.advance(); app.notifications.reschedule() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings: SettingsViewModel = viewModel(
                factory = SettingsViewModel.factory((application as PlanRecordApplication).preferencesRepository),
            )
            val preferences by settings.preferences.collectAsStateWithLifecycle()
            val resolved = preferences ?: return@setContent
            val dark = resolved.themeMode.isDark(isSystemInDarkTheme())
            val resources = LocalResources.current
            val snackbar = remember { SnackbarHostState() }
            SideEffect {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            LaunchedEffect(settings, resources) {
                lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    settings.messages.collect { snackbar.showSnackbar(resources.getString(it)) }
                }
            }
            LaunchedEffect(resolved.readFailed, resources) {
                if (resolved.readFailed) snackbar.showSnackbar(resources.getString(R.string.preferences_read_failed))
            }
            val context = androidx.compose.ui.platform.LocalContext.current
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val localized = remember(context, configuration, resolved.language) {
                localizedContext(context, resolved.language)
            }
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalContext provides remember(context, localized) {
                    object : android.content.ContextWrapper(context) {
                        override fun getResources(): android.content.res.Resources = localized.resources
                        override fun getAssets(): android.content.res.AssetManager = localized.assets
                    }
                },
                androidx.compose.ui.platform.LocalConfiguration provides localized.resources.configuration,
                LocalResources provides localized.resources,
                app.chronotation.ui.components.LocalDisplayPreferences provides resolved,
            ) {
                PlanRecordTheme(dark) {
                    PlanRecordApp(resolved.themeMode, settings::setTheme, snackbar)
                }
            }
        }
    }
}
