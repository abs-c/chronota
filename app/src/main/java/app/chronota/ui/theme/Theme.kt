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
 * Apple's grouped colors, mapped onto the Material roles.
 *
 * `background` is `systemGroupedBackground` and `surface` is `systemBackground`: a page that is one
 * continuous canvas — the timeline, the day and week views — paints `surface` and has no layering at
 * all, while a page made of cards sits on `background` and puts `secondarySystemGroupedBackground`
 * (white) on top of it. The roles read as:
 *
 * - `background` / `surfaceContainerLow`: the grouped page and the panels that open on it, which are
 *   the same color because a panel is the page lifted over a dimmed backdrop.
 * - `surface`: the plain canvas of a page with nothing to separate.
 * - `surfaceContainer`: the white sheet on top — cards, rows, fields and chips.
 * - `surfaceContainerHigh`: a well inside a sheet — progress tracks, empty heat-map cells, the
 *   segmented control's track (`systemGray5`).
 * - `surfaceContainerHighest`: the quiet gray button (`systemGray4` in the dark).
 * - `surfaceContainerLowest`: the raised thumb of a segmented control (`systemGray2` in the dark).
 * - `outline` / `outlineVariant`: Apple's separators, which is what grid lines are here.
 *
 * The text colors are deliberately a shade darker than `secondaryLabel` so 12sp labels stay legible.
 */
private val LightColors = lightColorScheme(
    primary = Color(0xFF4565BE), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE4EAFC), onPrimaryContainer = Color(0xFF304A92),
    secondary = Color(0xFF6E6E73), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE5E5EA), onSecondaryContainer = Color(0xFF1C1C1E),
    tertiary = Color(0xFF8E8E93), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE5E5EA), onTertiaryContainer = Color(0xFF1C1C1E),
    background = Color(0xFFF2F2F7), onBackground = Color(0xFF1C1C1E),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF1C1C1E),
    surfaceVariant = Color(0xFFE5E5EA), onSurfaceVariant = Color(0xFF6E6E73),
    surfaceContainer = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF2F2F7),
    surfaceContainerHigh = Color(0xFFE5E5EA), surfaceContainerHighest = Color(0xFFE5E5EA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFE5E5EA), surfaceBright = Color(0xFFFFFFFF),
    inverseSurface = Color(0xFF1C1C1E), inverseOnSurface = Color(0xFFF2F2F7),
    inversePrimary = Color(0xFFE4EAFC),
    outline = Color(0xFFC6C6C8), outlineVariant = Color(0xFFC6C6C8),
    error = Color(0xFFB3261E), onError = Color(0xFFFFFFFF),
    surfaceTint = Color.Transparent,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFADC2FF), onPrimary = Color(0xFF162D63),
    primaryContainer = Color(0xFF253759), onPrimaryContainer = Color(0xFFDCE6FF),
    secondary = Color(0xFF98989F), onSecondary = Color(0xFF1C1C1E),
    secondaryContainer = Color(0xFF2C2C2E), onSecondaryContainer = Color(0xFFF2F2F7),
    tertiary = Color(0xFF8E8E93), onTertiary = Color(0xFF1C1C1E),
    tertiaryContainer = Color(0xFF2C2C2E), onTertiaryContainer = Color(0xFFF2F2F7),
    // Both backgrounds are black in the dark, so a canvas page and a grouped page differ only by the
    // cards the grouped one puts on top.
    background = Color(0xFF000000), onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF000000), onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF2C2C2E), onSurfaceVariant = Color(0xFF98989F),
    surfaceContainer = Color(0xFF1C1C1E), surfaceContainerLow = Color(0xFF000000),
    surfaceContainerHigh = Color(0xFF2C2C2E), surfaceContainerHighest = Color(0xFF3A3A3C),
    surfaceContainerLowest = Color(0xFF636366),
    surfaceDim = Color(0xFF000000), surfaceBright = Color(0xFF2C2C2E),
    inverseSurface = Color(0xFFF2F2F7), inverseOnSurface = Color(0xFF1C1C1E),
    inversePrimary = Color(0xFF162D63),
    outline = Color(0xFF38383A), outlineVariant = Color(0xFF38383A),
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
