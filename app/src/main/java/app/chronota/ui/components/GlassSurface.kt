package app.chronota.ui.components

import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** How far the frost spreads: enough to lose a grid line, small enough to keep the page's colour. */
internal const val FrostRadiusDp = 16f

/** Room around a glass surface for the frost to reach into, so its edges are not clamped. */
internal const val FrostPaddingDp = 40f

/**
 * The frost every glass surface is made of: the page behind it, blurred, and nothing else. The dock,
 * the orb and the date band read as the same material because they all use this one effect.
 *
 * It is a plain blur and not a refraction shader. Bending the samples at the rim is what makes an
 * edge look liquid, but at these sizes it also drags what is behind the surface sideways and splits
 * it into seven chromatic samples: the grid lines under the dock came back displaced and rainbow
 * edged, which reads as a crack across the glass, and over a tinted disc it read as a wash of pale
 * colour rather than a drop of glass.
 */
internal fun frostEffect(density: Float): RenderEffect? =
    if (Build.VERSION.SDK_INT >= 31) BlurEffect(FrostRadiusDp * density, FrostRadiusDp * density, TileMode.Clamp) else null

/**
 * Frosted glass over the page. [tintAlpha] is how much of [tint] is washed over the frost — lower is
 * more transparent, and the opaque fallback on old devices ignores it.
 */
@Composable
fun Modifier.glassSurface(backdrop: GraphicsLayer?, backdropOrigin: Offset, tint: Color, tintAlpha: Float = .22f): Modifier {
    val layer = rememberGraphicsLayer()
    val density = LocalDensity.current.density
    val padding = FrostPaddingDp * density
    var origin by remember { mutableStateOf(Offset.Zero) }
    val effect = remember(density) { frostEffect(density) }
    SideEffect { layer.renderEffect = effect }
    return onGloballyPositioned { origin = it.positionInRoot() }.drawWithContent {
        val opaque = backdrop == null || effect == null
        if (backdrop != null && effect != null) {
            val offset = origin - backdropOrigin
            layer.record(size = IntSize((size.width + 2 * padding).roundToInt(), (size.height + 2 * padding).roundToInt())) {
                translate(padding - offset.x, padding - offset.y) { drawLayer(backdrop) }
            }
            translate(-padding, -padding) { drawLayer(layer) }
        }
        drawRect(tint.copy(alpha = if (opaque) .88f else tintAlpha))
        drawContent()
    }
}
