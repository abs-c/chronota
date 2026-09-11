package app.chronota.ui.components

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import app.chronota.ui.theme.Metrics

/** Soft enough to read as a sheet lying on the page rather than a panel floating over it. */
private val SheetShadow = Color.Black.copy(alpha = .07f)

/**
 * The white sheet this app puts its content on: a card, a group of rows, a field, a calendar. The
 * page behind it is the grouped gray, and the hair of elevation is what separates the two.
 */
@Composable fun Modifier.cardSurface(shape: Shape = MaterialTheme.shapes.small): Modifier = this
    .shadow(Metrics.cardElevation, shape, clip = false, ambientColor = SheetShadow, spotColor = SheetShadow)
    .clip(shape)
    .background(MaterialTheme.colorScheme.surfaceContainer)
