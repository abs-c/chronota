package app.chronota.ui.components

import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/** How far the frost spreads: enough to lose a grid line, small enough to keep the page's colour. */
internal const val FrostRadiusDp = 3f

/** Room around a glass surface for the frost to reach into, so its edges are not clamped. */
internal const val FrostPaddingDp = 40f

/**
 * The frost a glass surface is made of: the page behind it, blurred, and nothing else. The dock,
 * the orb and the date band read as the same material because they all use this one effect.
 *
 * A plain blur is right for a surface whose backdrop is flat — the date band sits over a page of one
 * grey going into one white. Where the backdrop has structure the rim lens takes over (see
 * [liquidGlassEffect]): it is the same frost, with the last few dp before the outline bending what
 * is behind them.
 */
internal fun frostEffect(density: Float): RenderEffect? =
    if (Build.VERSION.SDK_INT >= 31) BlurEffect(FrostRadiusDp * density, FrostRadiusDp * density, TileMode.Clamp) else null

/**
 * Frosted glass over the page. [tintAlpha] is how much of [tint] is washed over the frost — lower is
 * more transparent, and the opaque fallback on old devices ignores it. [rounded] says whether the
 * surface's outline is a curve for the rim lens to bend along.
 */
@Composable
fun Modifier.glassSurface(backdrop: GraphicsLayer?, backdropOrigin: Offset, tint: Color, tintAlpha: Float = .22f, rounded: Boolean = true): Modifier {
    val layer = rememberGraphicsLayer()
    val density = LocalDensity.current.density
    val padding = FrostPaddingDp * density
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val effect = remember(bounds, density) {
        if (Build.VERSION.SDK_INT >= 33 && rounded && bounds.width > 0 && bounds.height > 0)
            liquidGlassEffect(bounds, padding, density, bounds.height / 2f)
        else frostEffect(density)
    }
    SideEffect { layer.renderEffect = effect }
    return onSizeChanged { bounds = it }.onGloballyPositioned { origin = it.positionInRoot() }.drawWithContent {
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

/**
 * The axis every light on the glass runs along: 45°, no matter the shape's proportions.
 *
 * A gradient from the top-left corner to the bottom-right one only reads as a diagonal while the
 * surface is roughly square. On a bar a thousand wide and fifty tall that same gradient is almost
 * horizontal, and the light comes in from the side instead of the corner. Projecting the shape onto
 * the 45° axis — half of width plus height — keeps the direction the same on a bar, a pill and a disc.
 */
internal fun diagonalLight(size: Size, colors: List<Color>): Brush {
    val reach = (size.width + size.height) / 2f
    return Brush.linearGradient(colors, start = Offset.Zero, end = Offset(reach, reach))
}

/**
 * How far to lean the light and its shade, per mode. White paper swallows a white highlight and shows
 * every shadow at full strength; a dark pane does the opposite. So the recipe stays one recipe, and
 * each mode scales it: the light up and the shade down in the light, the other way about in the dark.
 */

internal data class GlassLighting(val light: Float, val shade: Float, val wash: Float)

/**
 * The palette in force, not the system's: the app can pin its own theme.
 *
 * `light` and `shade` scale the rim's two sides and the gradients inside the pane. `wash` is the
 * broad surface light in absolute terms, because white on white and white on black are not the same
 * job: a highlight over a near-white pane can only ever add the few levels between the pane and
 * white, so in the light it is four tenths where in the dark half of that already glares.
 */
@Composable
internal fun glassLighting(): GlassLighting = if (MaterialTheme.colorScheme.background.luminance() < .5f)
    GlassLighting(light = .8f, shade = 1.3f, wash = .10f)
else
    GlassLighting(light = 1.35f, shade = .85f, wash = .40f)

/**
 * How much of its own colour a piece of glass carries. One value for all of them — the dock, the
 * orb, the balls that come out of it — so that none of them is a different thickness from the rest;
 * the band is the exception, because its body is the page's own grey thinning out.
 */
internal const val GlassBodyAlpha = .22f
