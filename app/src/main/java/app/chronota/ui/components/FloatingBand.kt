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
import androidx.compose.ui.layout.SubcomposeLayout
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

/**
 * The band's alpha as it thins: one at the baseline, the floor at the lower edge, eased at both
 * ends. `eased` runs 0 there and 1 at the lower edge, following a smoothstep — so the ramp leaves the
 * header flat, without the corner a straight line would put there, and arrives at the floor flat too.
 * It changes nothing about the floor itself.
 */
private fun bandAlpha(eased: Float): Float = 1f - (1f - BandTint) * eased


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
    val density = LocalDensity.current
    // One measurement for both: the band is measured first, so the body can be laid out and the glass
    // drawn against a height that is already real. The alternative — measure, hold the height in state,
    // recompose — draws one frame where the height is still zero, and a gradient over zero pixels is
    // not a gradient: every pixel clamps to its last stop, which is the band as a flat slab of its
    // floor colour. That frame is what a screenshot catches and what the eye catches on the way in.
    SubcomposeLayout { constraints ->
        var origin = Offset.Zero
        val bandPlaceable = subcompose(BandSlot) {
            Box(Modifier.fillMaxWidth().onGloballyPositioned { origin = it.positionInRoot() }.clipToBounds().glassBand(layer, origin)) { band() }
        }.first().measure(constraints.copy(minHeight = 0))
        val bandPx = bandPlaceable.height
        val bodyFrom = if (BandGlassEnabled && BandTint < 1f) SolidFraction else 1f
        val bandDp = with(density) { bandPx.toDp() }
        val bodyPlaceable = subcompose(BodySlot) {
            Box(Modifier.fillMaxSize().drawWithContent { layer.record { this@drawWithContent.drawContent() }; drawLayer(layer) }) {
                Column(Modifier.fillMaxSize().sheetSurface()) {
                    Spacer(Modifier.height(bandDp * bodyFrom))
                    // A scroll inside is told how much of the band it begins under, so it can rest its
                    // first row at the band's lower edge rather than up at the glass line — and so that
                    // the stretch above that row, the blank before a day starts, sits under the glass.
                    CompositionLocalProvider(LocalBandHead provides bandDp * (1f - bodyFrom) + Metrics.hairline) { body() }
                }
            }
        }.first().measure(constraints)
        layout(constraints.maxWidth, constraints.maxHeight) {
            bodyPlaceable.place(0, 0)
            bandPlaceable.place(0, 0)
        }
    }
}

private object BandSlot
private object BodySlot

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
    val lighting = glassLighting()
    val base = MaterialTheme.colorScheme.background
    val body = MaterialTheme.colorScheme.surfaceContainerHigh
    val edge = MaterialTheme.colorScheme.outlineVariant
    val effect = remember(bounds, density) {
        // A radius of 0 makes the lens's normal degenerate along the band's flat edges: the shader
        // normalises an empty vector there and the bend comes out as nothing at all. A hair of a
        // radius keeps it well defined, and the band's own corners are off the sides of the screen.
        if (BandGlassEnabled && Build.VERSION.SDK_INT >= 33 && bounds.width > 0 && bounds.height > 0) liquidGlassEffect(bounds, padding, density, 2f * density, topOverhang = padding)
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
            // The grey the band has always been: solid down to the title's baseline, then thinning, then
            // glass — with the floor at 30% kept all the way to the lower edge. The thinning is eased
            // rather than straight: a straight ramp leaves the flat header in a corner, a change of
            // slope with no change of value, and the eye draws that corner as a line across the band.
            // On a dark page the ramp has twice as far to fall, so the same corner is twice as loud.
            val end = size.height.coerceAtLeast(1f)
            drawRect(
                Brush.verticalGradient(
                    0f to base,
                    SolidFraction to base,
                    .405f to base.copy(alpha = bandAlpha(.0608f)),
                    .51f to base.copy(alpha = bandAlpha(.216f)),
                    .615f to base.copy(alpha = bandAlpha(.4252f)),
                    .685f to base.copy(alpha = bandAlpha(.5757f)),
                    .79f to base.copy(alpha = bandAlpha(.7840f)),
                    .895f to base.copy(alpha = bandAlpha(.9392f)),
                    1f to base.copy(alpha = bandAlpha(1f)),
                    startY = 0f,
                    endY = end,
                )
            )
            // The glass's own body — one step off the page, so it has a tone rather than being only the
            // page's grey fading out — fades in with the glass. Drawn flat it would sit on the solid
            // header too, and a body colour is a wash: on a dark page that is a nine-level lift across
            // the whole band, which is enough to squeeze the ramp and leave the header at #090909
            // instead of the page's black. Anything that belongs to the glass has to arrive with it.
            drawRect(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    SolidFraction to Color.Transparent,
                    1f to body.copy(alpha = .12f),
                )
            )
        }
        drawContent()
    }
}

/**
 * The stretch of the band from the line its glass begins at down to its lower edge — the part of the
 * page that is still band, and where the day has not started yet.
 *
 * A timeline's scroll starts below it: with this as the top padding of the scrolled content, resting
 * at the top of the scroll puts the first hour exactly at the band's lower edge, and the stretch above
 * it, up under the glass, is the blank before the day began. Scrolling on carries the day up through
 * the glass, which is what the band is for.
 */
val LocalBandHead = compositionLocalOf { 0.dp }
