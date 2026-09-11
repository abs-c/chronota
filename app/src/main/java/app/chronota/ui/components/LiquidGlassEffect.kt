package app.chronota.ui.components

import android.graphics.RuntimeShader
import android.graphics.RenderEffect
import android.graphics.Shader
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.unit.IntSize

@RequiresApi(33)
internal fun liquidGlassEffect(size: IntSize, padding: Float, density: Float): androidx.compose.ui.graphics.RenderEffect {
    val shader = RuntimeShader(RoundedRectRefractionWithDispersionShaderString).apply {
        setFloatUniform("size", size.width.toFloat(), size.height.toFloat())
        setFloatUniform("offset", -padding, -padding)
        val radius = size.height / 2f
        setFloatUniform("cornerRadii", radius, radius, radius, radius)
        setFloatUniform("refractionHeight", 14f * density)
        setFloatUniform("refractionAmount", -20f * density)
        setFloatUniform("depthEffect", .3f)
        setFloatUniform("chromaticAberration", .08f)
    }
    val lens = RenderEffect.createRuntimeShaderEffect(shader, "content")
    val blur = RenderEffect.createBlurEffect(7f * density, 7f * density, Shader.TileMode.CLAMP)
    return RenderEffect.createChainEffect(lens, blur).asComposeRenderEffect()
}
