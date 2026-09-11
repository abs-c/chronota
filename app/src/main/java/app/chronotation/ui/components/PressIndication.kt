package app.chronotation.ui.components

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.Dp
import app.chronotation.ui.theme.Radii
import kotlinx.coroutines.launch

/**
 * Flat press highlight instead of a Material ripple: the surface dims slightly while held. The wash
 * is rounded like the app's controls, so a press never paints a hard-cornered rectangle over a
 * surface whose real shape is rounded — callers that clip themselves get the same radius for free.
 */
class PressIndication(private val color: Color, private val cornerRadius: Dp = Radii.control) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = object : Modifier.Node(), DrawModifierNode {
        private var pressed by mutableStateOf(false)
        override fun onAttach() {
            coroutineScope.launch {
                interactionSource.interactions.collect { interaction ->
                    when (interaction) {
                        is PressInteraction.Press -> pressed = true
                        is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
                    }
                }
            }
        }
        override fun ContentDrawScope.draw() {
            drawContent()
            if (pressed) drawRoundRect(color, cornerRadius = CornerRadius(cornerRadius.toPx()))
        }
    }

    override fun equals(other: Any?): Boolean = other is PressIndication && other.color == color && other.cornerRadius == cornerRadius
    override fun hashCode(): Int = 31 * color.hashCode() + cornerRadius.hashCode()
}
