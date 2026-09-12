package app.chronota.ui.components

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import app.chronota.ui.theme.Metrics
import app.chronota.ui.theme.SheetShape

/** Soft enough to read as a sheet lying on the page rather than a panel floating over it. */
private val SheetShadow = Color.Black.copy(alpha = .07f)

/**
 * What the band above casts onto the content just under it: a shade in the light, where both sides
 * are near-white, and a lift in the dark, where the sheet is already lighter than the black page.
 */
@Composable private fun seamColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < .5f) Color.White.copy(alpha = .05f) else Color.Black.copy(alpha = .10f)

/**
 * The white sheet this app puts its content on: a card, a group of rows, a field. The page behind it
 * is the grouped gray, and the hair of elevation is what separates the two.
 */
@Composable fun Modifier.cardSurface(shape: Shape = MaterialTheme.shapes.small, elevation: Dp = Metrics.cardElevation): Modifier = this
    .shadow(elevation, shape, clip = false, ambientColor = SheetShadow, spotColor = SheetShadow)
    .clip(shape)
    .background(MaterialTheme.colorScheme.surfaceContainer)

/** A page's main body — the calendar, the timeline. Full width, square topped, and lifted a little more. */
@Composable fun Modifier.sheetSurface(): Modifier = cardSurface(SheetShape, Metrics.sheetElevation)

/**
 * The shaded top edge of a page's body. The date band does not stop at a line; it falls away down
 * [Metrics.seamFade] of the content, so the gray and the paper read as one surface. Drawn under the
 * content, so the times and the blocks on it stay crisp.
 */
@Composable fun Modifier.seamShadow(): Modifier {
    val seam = seamColor()
    return drawWithContent {
        drawRect(
            brush = Brush.verticalGradient(listOf(seam, Color.Transparent), startY = 0f, endY = Metrics.seamFade.toPx()),
            size = Size(size.width, Metrics.seamFade.toPx()),
        )
        drawContent()
    }
}
