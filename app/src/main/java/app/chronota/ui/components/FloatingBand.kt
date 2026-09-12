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
            // The sheet runs the whole page, the band's own strip included: what the band has to
            // refract is this sheet and whatever the schedule has scrolled up into it.
            Column(Modifier.fillMaxSize().sheetSurface()) {
                Spacer(Modifier.height(with(density) { bandPx.toDp() }))
                body()
            }
        }
        Box(Modifier.align(Alignment.TopStart).fillMaxWidth().onSizeChanged { bandPx = it.height }.clipToBounds().glassBand(layer, origin)) {
            // The band keeps the glass and the grey's fade below its own content, so every band has
            // its glass under its date row whatever its content turned out to be.
            Column(Modifier.padding(bottom = Metrics.bandGlass * 2)) { band() }
        }
    }
}

/**
 * The band's glass: the lens over the whole band, then the page's grey closing over it towards the
 * top. The clear part at the lower edge is where the schedule shows through as it passes under.
 */
@Composable fun Modifier.glassBand(backdrop: GraphicsLayer, backdropOrigin: Offset): Modifier {
    val lens = rememberGraphicsLayer()
    val density = LocalDensity.current.density
    val padding = FrostPaddingDp * density
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val base = MaterialTheme.colorScheme.background
    val effect = remember(bounds, density) {
        // A radius of 0 makes the lens's normal degenerate along the band's flat edges: the shader
        // normalises an empty vector there and the bend comes out as nothing at all. A hair of a
        // radius keeps it well defined, and the band's own corners are off the sides of the screen.
        if (Build.VERSION.SDK_INT >= 33 && bounds.width > 0 && bounds.height > 0) liquidGlassEffect(bounds, padding, density, 2f * density)
        else frostEffect(density)
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
        // Grey over the band's content, spent by the time it reaches the glass, so the band's lower
        // edge is glass on the sheet rather than a grey strip painted over it.
        val strip = Metrics.bandGlass.toPx()
        val glassTop = (size.height - strip).coerceAtLeast(1f)
        val solidTop = (size.height - 2 * strip).coerceAtLeast(0f)
        drawRect(
            Brush.verticalGradient(
                0f to base,
                (solidTop / glassTop).coerceIn(0f, 1f) to base,
                1f to Color.Transparent,
                startY = 0f,
                endY = glassTop,
            )
        )
        drawContent()
    }
}
