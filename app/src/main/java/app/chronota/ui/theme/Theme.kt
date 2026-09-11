package app.chronota.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import app.chronota.ui.components.PressIndication

private val LightColors = lightColorScheme(
    primary = Color(0xFF4565BE), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4EAFC), onPrimaryContainer = Color(0xFF304A92),
    secondary = Color(0xFF555C57), onSecondary = Color(0xFFFAFAF9),
    secondaryContainer = Color(0xFFECEEEB), onSecondaryContainer = Color(0xFF202322),
    tertiary = Color(0xFF555C57), onTertiary = Color(0xFFFAFAF9),
    tertiaryContainer = Color(0xFFECEEEB), onTertiaryContainer = Color(0xFF202322),
    background = Color(0xFFFAFAF9), onBackground = Color(0xFF202322),
    surface = Color(0xFFFAFAF9), onSurface = Color(0xFF202322),
    surfaceVariant = Color(0xFFF0F1EE), onSurfaceVariant = Color(0xFF646B65),
    surfaceContainer = Color(0xFFF0F1EE), surfaceContainerLow = Color(0xFFF5F6F3),
    surfaceContainerHigh = Color(0xFFECEEEB), surfaceContainerHighest = Color(0xFFE6E9E4),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE6E9E4), surfaceBright = Color(0xFFFAFAF9),
    inverseSurface = Color(0xFF292D29), inverseOnSurface = Color(0xFFE7EAE7),
    inversePrimary = Color(0xFFE7EAE7),
    outline = Color(0xFF737B74), outlineVariant = Color(0xFFDDE1DA),
    error = Color(0xFFAC3530), onError = Color(0xFFFFFFFF),
    surfaceTint = Color.Transparent,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFADC2FF), onPrimary = Color(0xFF162D63),
    primaryContainer = Color(0xFF253759), onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFFADB5AC), onSecondary = Color(0xFF202322),
    secondaryContainer = Color(0xFF292D29), onSecondaryContainer = Color(0xFFE7EAE7),
    tertiary = Color(0xFFADB5AC), onTertiary = Color(0xFF202322),
    tertiaryContainer = Color(0xFF292D29), onTertiaryContainer = Color(0xFFE7EAE7),
    background = Color(0xFF111312), onBackground = Color(0xFFE7EAE7),
    surface = Color(0xFF111312), onSurface = Color(0xFFE7EAE7),
    surfaceVariant = Color(0xFF1B1F1C), onSurfaceVariant = Color(0xFFA1ABA1),
    surfaceContainer = Color(0xFF1B1F1C), surfaceContainerLow = Color(0xFF171A18),
    surfaceContainerHigh = Color(0xFF252A25), surfaceContainerHighest = Color(0xFF303630),
    surfaceContainerLowest = Color(0xFF0C0E0D),
    surfaceDim = Color(0xFF111312), surfaceBright = Color(0xFF303630),
    inverseSurface = Color(0xFFE7EAE7), inverseOnSurface = Color(0xFF202322),
    inversePrimary = Color(0xFF202322),
    outline = Color(0xFF889388), outlineVariant = Color(0xFF303630),
    error = Color(0xFFFFB4AA), onError = Color(0xFF680D09),
    surfaceTint = Color.Transparent,
)

@Composable
fun ChronotaTheme(dark: Boolean, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = ChronotaTypography,
        shapes = ChronotaShapes,
    ) {
        val onSurface = MaterialTheme.colorScheme.onSurface
        val indication = remember(onSurface) { PressIndication(onSurface.copy(alpha = .07f)) }
        CompositionLocalProvider(LocalIndication provides indication, content = content)
    }
}
