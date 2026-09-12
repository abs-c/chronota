package app.chronota.ui.components

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.unit.IntSize
import kotlin.math.min

/**
 * The glass itself: the rim lens over the backdrop, blurred.
 *
 * The bend is kept small on purpose. It is what gives the edge its thickness, but the same bend
 * pushed further drags the backdrop far enough sideways to read as a displaced copy of the page
 * rather than as a lens, and once the sample leaves the surface it takes the surface's own colour
 * with it. Both the band and the bend are also capped against the surface's smaller side, so a
 * wheel button a third of the dock's height gets a proportionally smaller rim instead of the same
 * one carved out of it.
 */
@RequiresApi(33)
internal fun liquidGlassEffect(size: IntSize, padding: Float, density: Float, cornerRadius: Float, topOverhang: Float = 0f): ComposeRenderEffect {
    val shortSide = min(size.width, size.height).toFloat()
    val shader = RuntimeShader(RoundedRectRefractionShaderString).apply {
        // `topOverhang` pushes the surface the lens bends along up out of sight while leaving its lower
        // edge where it was. The rim that runs along the top edge then falls in the strip the band
        // clips away, instead of bending the band's upper stretch — where the background has only just
        // begun to show through, and a bend there reads as a kink rather than as glass.
        setFloatUniform("size", size.width.toFloat(), size.height + topOverhang)
        setFloatUniform("offset", -padding, -(padding - topOverhang))
        setFloatUniform("cornerRadii", cornerRadius, cornerRadius, cornerRadius, cornerRadius)
        setFloatUniform("rimHeight", min(RimHeightDp * density, shortSide * .34f).coerceAtLeast(1f))
        setFloatUniform("rimBend", min(RimBendDp * density, shortSide * .2f).coerceAtLeast(1f))
        setFloatUniform("depthEffect", .12f)
    }
    val lens = RenderEffect.createRuntimeShaderEffect(shader, "content")
    val blur = RenderEffect.createBlurEffect(FrostRadiusDp * density, FrostRadiusDp * density, Shader.TileMode.CLAMP)
    // The blur runs first and the lens bends the soft image, so the fold the rim profile makes at the
    // very edge reads as the thickness of the glass instead of a crease in it.
    return RenderEffect.createChainEffect(lens, blur).asComposeRenderEffect()
}

/** How deep into the surface the rim's bend reaches. */
private const val RimHeightDp = 24f

/** How far the backdrop is pulled in at the very edge. */
private const val RimBendDp = 15f
