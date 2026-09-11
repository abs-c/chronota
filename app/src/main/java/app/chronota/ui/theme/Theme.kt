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

/**
 * A grouped background with raised white content on top of it.
 *
 * The page is gray and everything that is the content — a settings group, a calendar, a card, a
 * field — is white and lifts off it with [cardSurface]. That is why `surfaceContainer` is white
 * here rather than the field gray of a stock Material palette: in this app it is the sheet, not the
 * well. The roles read as:
 *
 * - `background` / `surface` / `surfaceContainerLow`: the grouped page and the panels that open on
 *   it, which are the same color because a panel is the page lifted over a dimmed backdrop.
 * - `surfaceContainer`: the white sheet on top — cards, rows, fields and chips.
 * - `surfaceContainerHigh`: a well inside a sheet — progress tracks, empty heat-map cells, the
 *   segmented control's track.
 * - `surfaceContainerHighest`: the quiet gray button.
 * - `surfaceContainerLowest`: the raised thumb of a segmented control, which is the lightest
 *   surface in light mode and the lightest well in dark mode.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF4565BE), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4EAFC), onPrimaryContainer = Color(0xFF304A92),
    secondary = Color(0xFF6E6E73), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9E9EB), onSecondaryContainer = Color(0xFF1C1C1E),
    tertiary = Color(0xFF6E6E73), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE9E9EB), onTertiaryContainer = Color(0xFF1C1C1E),
    background = Color(0xFFF2F2F7), onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFF2F2F7), onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE9E9EB), onSurfaceVariant = Color(0xFF6E6E73),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF2F2F7),
    surfaceContainerHigh = Color(0xFFE9E9EB), surfaceContainerHighest = Color(0xFFE0E0E5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE0E0E5), surfaceBright = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF1C1C1E), inverseOnSurface = Color(0xFFF2F2F7),
    inversePrimary = Color(0xFFE4EAFC),
    outline = Color(0xFFC7C7CC), outlineVariant = Color(0xFFE0E0E5),
    error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF),
    surfaceTint = Color.Transparent,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFADC2FF), onPrimary = Color(0xFF162D63),
    primaryContainer = Color(0xFF253759), onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFF98989F), onSecondary = Color(0xFF1C1C1E),
    secondaryContainer = Color(0xFF2C2C2E), onSecondaryContainer = Color(0xFFF2F2F7),
    tertiary = Color(0xFF98989F), onTertiary = Color(0xFF1C1C1E),
    tertiaryContainer = Color(0xFF2C2C2E), onTertiaryContainer = Color(0xFFF2F2F7),
    // The page is black in the dark, and a sheet is the same black over the dimmed backdrop; the
    // white sheets of light mode become the raised #1C1C1E surfaces here.
    background = Color(0xFF000000), onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF000000), onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF2C2C2E), onSurfaceVariant = Color(0xFF98989F),
    surfaceContainer = Color(0xFF1C1C1E), surfaceContainerLow = Color(0xFF000000),
    surfaceContainerHigh = Color(0xFF2C2C2E), surfaceContainerHighest = Color(0xFF3A3A3C),
    surfaceContainerLowest = Color(0xFF48484A),
    surfaceDim = Color(0xFF000000), surfaceBright = Color(0xFF2C2C2E),
    inverseSurface = Color(0xFFF2F2F7), inverseOnSurface = Color(0xFF1C1C1E),
    inversePrimary = Color(0xFF162D63),
    outline = Color(0xFF48484A), outlineVariant = Color(0xFF2C2C2E),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
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
