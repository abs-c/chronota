package app.chronota.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import app.chronota.ui.theme.Metrics

/**
 * A page body with a band floating over its top.
 *
 * The layering a timeline wants: the white body and the blocks are the bottom layer, the gray date
 * band is the middle one, resting on top of them, and the dock and the orb float above everything.
 * The body is drawn a band taller than the page so its top runs under the band, and the band keeps
 * its colour at the top and goes clear at its lower edge, so what scrolls beneath it shows through as
 * it arrives.
 *
 * The band is faded, not blurred: Compose composites a recorded GraphicsLayer after the content
 * beside it, so a blurred copy of the body drawn here would land over the band's own dates and grey
 * them out.
 */
@Composable
fun FloatingBand(band: @Composable () -> Unit, body: @Composable ColumnScope.() -> Unit) {
    var bandPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    val base = MaterialTheme.colorScheme.background
    val edge = MaterialTheme.colorScheme.outlineVariant
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().offset { IntOffset(0, -bandPx) }) {
            Column(Modifier.fillMaxSize().sheetSurface()) {
                // The body keeps clear of the band, and scrolls under it from there.
                Spacer(Modifier.height(with(density) { bandPx.toDp() }))
                body()
            }
        }
        Box(Modifier.align(Alignment.TopStart).fillMaxWidth().onSizeChanged { bandPx = it.height }
            .drawWithContent {
                drawRect(Brush.verticalGradient(0f to base, .55f to base, 1f to base.copy(alpha = 0f)))
                drawContent()
                val hairline = Metrics.hairline.toPx()
                drawLine(
                    brush = Brush.horizontalGradient(listOf(Color.Transparent, edge.copy(alpha = .7f), Color.Transparent)),
                    start = Offset(0f, size.height - hairline / 2f),
                    end = Offset(size.width, size.height - hairline / 2f),
                    strokeWidth = hairline,
                )
            }) { band() }
    }
}
