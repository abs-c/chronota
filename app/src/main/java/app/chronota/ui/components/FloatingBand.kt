package app.chronota.ui.components

import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import app.chronota.ui.theme.Metrics

/** The glass is on from here down: the band keeps its colour, and its colour is now a stain. */
private const val BandGlassEnabled = true

/**
 * Where the band's glass begins, as a fraction of its height — the title's baseline, which is where
 * the band stops being a header and starts being a window on the schedule (154px of 374px on the
 * emulator, at a 63px inset). Above it the band is its opaque self.
 */
private const val SolidFraction = .3f


/** The grey's alpha once it is fully glass: a tenth, so the schedule reads straight through. */
private const val BandTint = .3f

/**
 * A page with a band across its top and the page's own body under it.
 *
 * The band is glass over the schedule. It closes into the page's grey across its own content, so the
 * date and the week strip stay legible, and the last [Metrics.bandGlass] of its height is clear glass
 * — the schedule that reaches up there is seen and bent by the lens along the band's lower edge. A
 * band that is glass all the way up has no surface for its content to sit on; a band that is grey all
 * the way down has nothing to refract. The glass is below the date row, never across it: what shows
 * through the date row is a smear of whatever is behind it, which is what made that row look broken.
 *
 * The body still starts below the band — nothing scrolls out from under it by accident. The schedule
 * reaches up into the glass itself, which is the one thing that belongs there.
 *
 * The body is recorded into its own layer for the band to frost and bend: sampling the backdrop the
 * dock uses is not possible here, because the band lives inside the page that backdrop is recorded
 * from and asking for it again recurses.
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
            // The body starts at the band's lower edge, or at the glass's own line when that glass can
            // be seen through. There is no point putting the schedule behind a stain that shows
            // nothing: it would only bury the top of the timeline under an opaque band.
            val bodyFrom = if (BandGlassEnabled && BandTint < 1f) SolidFraction else 1f
            Column(Modifier.fillMaxSize().sheetSurface()) {
                Spacer(Modifier.height(with(density) { (bandPx * bodyFrom).toDp() }))
                body()
            }
        }
        // The band is its own content.
        Box(Modifier.align(Alignment.TopStart).fillMaxWidth().onSizeChanged { bandPx = it.height }.clipToBounds().glassBand(layer, origin)) { band() }
    }
}

/**
 * The band: flat grey across its content, and — once the glass is on — the lens over it with the grey
 * giving way towards the bottom, and an edge where it meets the sheet.
 */
@Composable fun Modifier.glassBand(backdrop: GraphicsLayer, backdropOrigin: Offset): Modifier {
    val lens = rememberGraphicsLayer()
    val density = LocalDensity.current.density
    val padding = FrostPaddingDp * density
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val base = MaterialTheme.colorScheme.background
    val edge = MaterialTheme.colorScheme.outlineVariant
    val effect = remember(bounds, density) {
        // A radius of 0 makes the lens's normal degenerate along the band's flat edges: the shader
        // normalises an empty vector there and the bend comes out as nothing at all. A hair of a
        // radius keeps it well defined, and the band's own corners are off the sides of the screen.
        if (BandGlassEnabled && Build.VERSION.SDK_INT >= 33 && bounds.width > 0 && bounds.height > 0) liquidGlassEffect(bounds, padding, density, 2f * density)
        else if (BandGlassEnabled) frostEffect(density)
        else null
    }
    SideEffect { lens.renderEffect = effect }
    return onSizeChanged { bounds = it }.onGloballyPositioned { origin = it.positionInRoot() }.drawWithContent {
        if (effect != null) {
            val offset = origin - backdropOrigin
            lens.record(size = IntSize((size.width + 2 * padding).roundToInt(), (size.height + 2 * padding).roundToInt())) {
                translate(padding - offset.x, padding - offset.y) { drawLayer(backdrop) }
            }
            translate(-padding, -padding) { drawLayer(lens) }
        }
        if (!BandGlassEnabled) {
            // The band is the page's grey and nothing else.
            drawRect(base)
        } else {
            // The grey the band has always been: solid down to the title's baseline, then thinning —
            // 1.0 down to a tenth — until it is fully glass, and glass the rest of the way to the
            // lower edge. So the band comes out of its opaque header, becomes a window on the
            // schedule, and ends as clear pane over the sheet.
            val end = size.height.coerceAtLeast(1f)
            drawRect(
                Brush.verticalGradient(
                    0f to base,
                    SolidFraction to base,
                    1f to base.copy(alpha = BandTint),
                    startY = 0f,
                    endY = end,
                )
            )
        }
        drawContent()
    }
}
