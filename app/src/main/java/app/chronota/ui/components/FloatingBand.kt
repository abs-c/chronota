package app.chronota.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import app.chronota.ui.theme.Metrics

/**
 * A page body with a band floating over its top.
 *
 * The layering a timeline wants: the white body and the blocks are the bottom layer, the gray date
 * band is the middle one, resting on top of them, and the dock and the orb float above everything.
 * The band sits over the top of the sheet and turns into glass as it comes down into it.
 *
 * The band reserves its own height above the body: the sheet runs under it, the schedule starts
 * below it. Nothing of the body is left behind the glass — a chip or an hour label under a lens
 * comes back as a smeared patch of its own colour, which reads as a broken pixel, not as glass.
 *
 * The body is recorded into its own layer for the band to refract: sampling the backdrop the dock
 * uses is not possible here, because the band lives inside the page that backdrop is recorded from
 * and asking for it again recurses. The band also composites offscreen, so its mask stays inside the
 * band instead of cutting into the page behind it.
 */
@Composable
fun FloatingBand(band: @Composable () -> Unit, body: @Composable ColumnScope.() -> Unit) {
    val layer = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    var bandPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }) {
            Column(Modifier.fillMaxSize().sheetSurface()) {
                Spacer(Modifier.height(with(density) { bandPx.toDp() }))
                body()
            }
        }
        Box(Modifier.align(Alignment.TopStart).fillMaxWidth().onSizeChanged { bandPx = it.height }.glassBand(layer, origin)) { band() }
    }
}

/**
 * The dock's lens over a full-width band, faded in from the band's lower edge.
 *
 * The mask is a `DstIn` rectangle, which only works on an element that is composited offscreen;
 * without that the blend reaches the page behind the band and the band's own labels wash out.
 */
@Composable fun Modifier.glassBand(backdrop: GraphicsLayer, backdropOrigin: Offset): Modifier {
    val lens = rememberGraphicsLayer()
    val glass = rememberGraphicsLayer()
    val density = LocalDensity.current.density
    val padding = 48f * density
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val base = MaterialTheme.colorScheme.background
    val tint = MaterialTheme.colorScheme.surface
    val edge = MaterialTheme.colorScheme.outlineVariant
    val effect = remember(bounds, density) {
        if (Build.VERSION.SDK_INT >= 33 && bounds.width > 0 && bounds.height > 0)
            liquidGlassEffect(bounds, padding, density, 0f)
        else BlurEffect(9f * density, 9f * density, TileMode.Clamp)
    }
    SideEffect { lens.renderEffect = effect }
    return graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .onSizeChanged { bounds = it }
        .onGloballyPositioned { origin = it.positionInRoot() }
        .drawWithContent {
            val width = bounds.width
            val height = bounds.height
            if (width > 0 && height > 0) {
                val offset = origin - backdropOrigin
                lens.record(size = IntSize((width + 2 * padding).roundToInt(), (height + 2 * padding).roundToInt())) {
                    translate(padding - offset.x, padding - offset.y) { drawLayer(backdrop) }
                }
                glass.record(size = IntSize(width, height)) {
                    translate(-padding, -padding) { drawLayer(lens) }
                    drawRect(tint.copy(alpha = .16f))
                    // Clear at the top, full at the lower edge: the glass arrives from the boundary.
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Color.Black)), blendMode = BlendMode.DstIn)
                }
                drawLayer(glass)
            }
            // The band's own colour, solid where the glass is not, so the title stays readable.
            drawRect(Brush.verticalGradient(listOf(base, base, base.copy(alpha = 0f))))
            drawContent()
            val hairline = Metrics.hairline.toPx()
            drawLine(
                brush = Brush.horizontalGradient(listOf(Color.Transparent, edge.copy(alpha = .7f), Color.Transparent)),
                start = Offset(0f, size.height - hairline / 2f),
                end = Offset(size.width, size.height - hairline / 2f),
                strokeWidth = hairline,
            )
        }
}
