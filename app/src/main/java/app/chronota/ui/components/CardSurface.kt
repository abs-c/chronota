package app.chronota.ui.components

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
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
 * A page's main body — the calendar, the timeline. Full width and square topped, and lifted far
 * enough that the gray above it fades under the edge instead of stopping at a hard line.
 */
@Composable fun Modifier.sheetSurface(): Modifier = cardSurface(SheetShape, Metrics.sheetElevation)

/**
 * The grouped band a page's content slides under, the date row being the usual one. It casts the same
 * seam shadow as a sheet, downward, onto the paper below it.
 */
@Composable fun Modifier.bandSurface(): Modifier = this
    .shadow(Metrics.sheetElevation, RectangleShape, clip = false, ambientColor = SheetShadow, spotColor = SheetShadow)
    .background(MaterialTheme.colorScheme.background)
