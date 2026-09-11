package app.chronota.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import app.chronota.R
import app.chronota.ui.theme.Metrics
import app.chronota.ui.theme.Space
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Vertical list whose rows reorder by long-pressing the grip on the left.
 * Rows follow the finger, swap live while dragging, and the displaced rows slide
 * into their new place; releasing settles the dragged row with the same easing.
 */
@Composable
fun <T> ReorderableRows(items: List<T>, key: (Int, T) -> Any, pitch: Dp, onMove: (Int, Int) -> Unit,
    modifier: Modifier = Modifier, row: @Composable (Int, T) -> Unit) {
    val pitchPx = with(LocalDensity.current) { pitch.toPx() }
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf<Any?>(null) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        items.forEachIndexed { index, item ->
            val id = key(index, item)
            key(id) {
                var previous by remember { mutableIntStateOf(index) }
                val shift = remember { Animatable(0f) }
                val isDragging = dragging == id
                LaunchedEffect(index, isDragging) {
                    when {
                        isDragging -> shift.snapTo(0f)
                        index != previous -> { shift.snapTo((previous - index) * pitchPx); shift.animateTo(0f, tween(200)) }
                    }
                    previous = index
                }
                Row(Modifier.graphicsLayer {
                    translationY = if (isDragging) offset.value else shift.value
                    alpha = if (isDragging) .9f else 1f
                }, verticalAlignment = Alignment.CenterVertically) {
                    DragHandle(id,
                        start = {
                            dragging = id
                            scope.launch { offset.snapTo(0f) }
                        },
                        drag = { dy ->
                            scope.launch {
                                val current = offset.value + dy
                                offset.snapTo(current)
                                val from = items.indices.firstOrNull { key(it, items[it]) == id }
                                if (from != null) {
                                    val target = (from + (current / pitchPx).roundToInt()).coerceIn(0, items.lastIndex)
                                    if (target != from) {
                                        onMove(from, target)
                                        offset.snapTo(current - (target - from) * pitchPx)
                                    }
                                }
                            }
                        },
                        stop = {
                            dragging = null
                            scope.launch { offset.animateTo(0f, tween(180)) }
                        })
                    Box(Modifier.weight(1f)) { row(index, item) }
                }
            }
        }
    }
}

@Composable
private fun DragHandle(id: Any, start: () -> Unit, drag: (Float) -> Unit, stop: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val onStart by rememberUpdatedState(start)
    val onDrag by rememberUpdatedState(drag)
    val onStop by rememberUpdatedState(stop)
    Box(Modifier.size(width = Space.lg, height = Metrics.controlHeight).pointerInput(id) {
        detectDragGesturesAfterLongPress(
            onDragStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onStart() },
            onDrag = { change, amount -> change.consume(); onDrag(amount.y) },
            onDragCancel = { onStop() },
            onDragEnd = { onStop() },
        )
    }, contentAlignment = Alignment.Center) {
        Icon(AppIcons.Grip, stringResource(R.string.reorder), Modifier.size(Metrics.iconSmall), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
