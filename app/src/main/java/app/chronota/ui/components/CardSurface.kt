package app.chronota.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.chronota.ui.theme.Metrics
import app.chronota.ui.theme.SheetShape

/** Soft enough to read as a sheet lying on the page rather than a panel floating over it. */
private val SheetShadow = Color.Black.copy(alpha = .07f)

/**
 * The white sheet this app puts its content on: a card, a group of rows, a field. The page behind it
 * is the grouped gray, and the hair of elevation is what separates the two.
 */
@Composable fun Modifier.cardSurface(shape: Shape = MaterialTheme.shapes.small, elevation: Dp = Metrics.cardElevation): Modifier = this
    .shadow(elevation, shape, clip = false, ambientColor = SheetShadow, spotColor = SheetShadow)
    .clip(shape)
    .background(MaterialTheme.colorScheme.surfaceContainer)

/**
 * A page's main body — the calendar, the timeline. Full width, square topped and flat: what marks its
 * top edge is the glass band above it, not a shadow.
 */
@Composable fun Modifier.sheetSurface(): Modifier = cardSurface(SheetShape, 0.dp)

/**
 * The edge of a piece of glass. One line is a frame, not an edge, so it is drawn in three passes
 * along the shape's own outline — a circle, a pill and a sheet each get their own:
 *
 *  - the thickness: a soft band just inside the outline, the light that entered the glass and is
 *    travelling along its edge;
 *  - the specular: a crisp highlight on the inner side of that band;
 *  - the silhouette: a whisper of the page's separator on the outer side, which is what keeps the
 *    glass from dissolving into a white sheet.
 *
 * The light comes from the upper left, so the first two run on a diagonal and are spent by the lower
 * right, where the silhouette takes over — glass is not lit from everywhere, and an evenly lit ring
 * reads as plastic.
 */
@Composable fun Modifier.glassRing(stroke: Dp = Metrics.outline, shape: Shape = CircleShape): Modifier {
    val separator = MaterialTheme.colorScheme.outlineVariant
    val layoutDirection = LocalLayoutDirection.current
    return clip(shape).drawWithContent {
        drawContent()
        val edge = when (val outline = shape.createOutline(size, layoutDirection, this)) {
            is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
            is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
            is Outline.Generic -> outline.path
        }
        val line = stroke.toPx()
        val lit = Brush.linearGradient(
            listOf(Color.White.copy(alpha = .6f), Color.White.copy(alpha = .22f), Color.White.copy(alpha = .05f)),
            start = Offset(0f, 0f),
            end = Offset(size.width * .82f, size.height),
        )
        drawPath(edge, brush = lit, alpha = .10f, style = Stroke(line * 3f))
        drawPath(edge, brush = lit, style = Stroke(line))
        drawPath(edge, brush = Brush.linearGradient(listOf(Color.Transparent, Color.Transparent, separator.copy(alpha = .34f))), style = Stroke(line))
    }
}
