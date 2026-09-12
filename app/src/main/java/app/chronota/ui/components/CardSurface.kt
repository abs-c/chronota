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
 * The edge of a glass surface: a ring lit at the top and fading away at the bottom, drawn along the
 * shape rather than as a line, so the pill of the dock and the disc of the orb both get the right edge.
 */
@Composable fun Modifier.glassRing(stroke: Dp = Metrics.outline, shape: Shape = CircleShape): Modifier =
    border(stroke, Brush.verticalGradient(listOf(MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f), MaterialTheme.colorScheme.outlineVariant.copy(alpha = .15f))), shape)
