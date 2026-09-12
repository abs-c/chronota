package app.chronota.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A page with a date band across its top and the page's own body under it.
 *
 * The band is a frosted window, not a painted strip: it shows the gray the page already has, and the
 * white sheet begins a little above its lower edge, so the frost of that boundary is what carries the
 * gray into the paper. That is the whole effect — there is no grey of the band's own to fade out, and
 * nothing of its colour can be left behind when the sheet changes.
 *
 * The band reserves its own height, so the schedule starts below it. Content left behind the glass
 * comes back as a smeared patch of its own colour, which reads as a broken pixel rather than as glass.
 *
 * The body is recorded into its own layer for the band to frost: sampling the backdrop the dock uses
 * is not possible here, because the band lives inside the page that backdrop is recorded from and
 * asking for it again recurses.
 */
@Composable
fun FloatingBand(band: @Composable () -> Unit, body: @Composable ColumnScope.() -> Unit) {
    val layer = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    var bandPx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    // The sheet starts this far above the band's lower edge, leaving the frost room to reach white
    // before the band ends; starting it exactly at the edge would leave a step where they meet.
    val blend = with(density) { (FrostRadiusDp * 1.5f).dp }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }) {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                Spacer(Modifier.height((with(density) { bandPx.toDp() } - blend).coerceAtLeast(0.dp)))
                Column(Modifier.fillMaxWidth().weight(1f).sheetSurface()) {
                    // The sheet's start is early, the schedule's is not: the frost gets its white
                    // without any of the body rising into the band behind it.
                    Spacer(Modifier.height(blend))
                    body()
                }
            }
        }
        Box(Modifier.align(Alignment.TopStart).fillMaxWidth().onSizeChanged { bandPx = it.height }.clipToBounds().glassBand(layer, origin)) { band() }
    }
}

/**
 * The frost of the band: the page's gray above, the sheet's white below, and the blurred boundary
 * between them carrying one into the other. Nothing is painted over it — the material is the blur.
 *
 * The lens is padded so its blur is not clamped at the rim; the band clips it (`clipToBounds`), or
 * the frost would reach over the sheet's first row and blur the lane labels under it.
 */
@Composable fun Modifier.glassBand(backdrop: GraphicsLayer, backdropOrigin: Offset): Modifier {
    val lens = rememberGraphicsLayer()
    val density = LocalDensity.current.density
    val padding = FrostPaddingDp * density
    var origin by remember { mutableStateOf(Offset.Zero) }
    val effect = remember(density) { frostEffect(density) }
    SideEffect { lens.renderEffect = effect }
    return onGloballyPositioned { origin = it.positionInRoot() }.drawWithContent {
        if (effect != null) {
            val offset = origin - backdropOrigin
            lens.record(size = IntSize((size.width + 2 * padding).roundToInt(), (size.height + 2 * padding).roundToInt())) {
                translate(padding - offset.x, padding - offset.y) { drawLayer(backdrop) }
            }
            translate(-padding, -padding) { drawLayer(lens) }
        }
        drawContent()
    }
}
