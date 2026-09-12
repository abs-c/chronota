package app.chronota.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
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
 * The date band at the top of a timeline. It cannot sample the page backdrop the way the dock does —
 * the band sits inside the page that backdrop is recorded from, so asking for it again recurses — and
 * on a flat grouped page there is nothing under the band to refract anyway. What reads as glass here
 * is the frost and the edge: a breath of the surface colour, strongest at the seam and gone by the
 * middle of the band, over a lower edge that fades out at both ends.
 */
@Composable fun Modifier.glassBand(): Modifier {
    val lift = MaterialTheme.colorScheme.surfaceContainer
    val edge = MaterialTheme.colorScheme.outlineVariant
    return this
        .background(Brush.verticalGradient(0f to lift.copy(alpha = 0f), .5f to lift.copy(alpha = 0f), 1f to lift.copy(alpha = .6f)))
        .drawWithContent {
            drawContent()
            val hairline = Metrics.hairline.toPx()
            drawLine(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, edge.copy(alpha = .75f), Color.Transparent)),
                start = Offset(0f, size.height - hairline / 2f),
                end = Offset(size.width, size.height - hairline / 2f),
                strokeWidth = hairline,
            )
        }
}

/**
 * The edge of a glass surface: a ring lit at the top and fading away at the bottom, drawn along the
 * shape rather than as a line, so the pill of the dock and the disc of the orb both get the right edge.
 */
@Composable fun Modifier.glassRing(stroke: Dp = Metrics.outline, shape: Shape = CircleShape): Modifier =
    border(stroke, Brush.verticalGradient(listOf(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f), MaterialTheme.colorScheme.outlineVariant.copy(alpha = .15f))), shape)
