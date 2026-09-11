package app.chronotation.ui.components

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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

@Composable
fun Modifier.glassSurface(backdrop: GraphicsLayer?, backdropOrigin: Offset, tint: Color): Modifier {
    val layer = rememberGraphicsLayer()
    val density = LocalDensity.current.density
    val padding = 24f * density
    var bounds by remember { mutableStateOf(IntSize.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val effect = remember(bounds, density) {
        if (Build.VERSION.SDK_INT >= 33 && bounds.width > 0 && bounds.height > 0)
            liquidGlassEffect(bounds, padding, density)
        else BlurEffect(8f * density, 8f * density, TileMode.Clamp)
    }
    SideEffect { layer.renderEffect = effect }
    return onSizeChanged { bounds = it }.onGloballyPositioned { origin = it.positionInRoot() }.drawWithContent {
        if (backdrop != null) {
            val offset = origin - backdropOrigin
            layer.record(size = IntSize((size.width + 2 * padding).roundToInt(), (size.height + 2 * padding).roundToInt())) {
                translate(padding - offset.x, padding - offset.y) { drawLayer(backdrop) }
            }
            translate(-padding, -padding) { drawLayer(layer) }
        }
        drawRect(tint.copy(alpha = if (backdrop == null || Build.VERSION.SDK_INT < 31) .88f else .22f))
        drawContent()
    }
}
