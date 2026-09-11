package app.chronota.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.chronota.ui.theme.Metrics
import app.chronota.ui.theme.Space
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Snapping hour and minute wheels. Every clock-time field uses this picker instead of
 * typing digits; the centred row is the selected value. Selection is shown by text
 * colour and size only — no fill, highlight or shadow.
 */
@Composable
fun TimeWheel(hour: Int, minute: Int, change: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Wheel(0..23, hour, { change(it, minute) }, "wheel_hour", Modifier.weight(1f))
        Text(":", Modifier.padding(horizontal = Space.sm), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Wheel(0..59, minute, { change(hour, it) }, "wheel_minute", Modifier.weight(1f))
    }
}

/** Enough repeats that the wheel keeps scrolling past the first value in either direction. */
private const val WheelCycles = 2000

@Composable
private fun Wheel(values: IntRange, selected: Int, select: (Int) -> Unit, tag: String, modifier: Modifier = Modifier) {
    val size = values.count()
    val state = rememberLazyListState(initialFirstVisibleItemIndex = size * (WheelCycles / 2) + (selected - values.first).coerceIn(0, size - 1))
    val fling = rememberSnapFlingBehavior(lazyListState = state, snapPosition = SnapPosition.Center)
    val scope = rememberCoroutineScope()
    val centered by remember { derivedStateOf {
        val info = state.layoutInfo
        val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
        info.visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - center) }?.index
    } }
    val centeredValue = centered?.let { values.first + it % size }
    // Commit the moment the centred item changes, without waiting for the fling to fully settle:
    // the wheel can look stopped while it is still animating, and a tap on Confirm should take the
    // value the user sees rather than the one from before the scroll.
    LaunchedEffect(centeredValue) {
        centeredValue?.let { if (it != selected) select(it) }
    }
    LazyColumn(state = state, flingBehavior = fling, modifier = modifier.height(Metrics.wheelItem * 5).testTag(tag),
        contentPadding = PaddingValues(vertical = Metrics.wheelItem * 2)) {
        items(count = size * WheelCycles) { index ->
            val value = values.first + index % size
            val active = index == centered
            Box(Modifier.fillMaxWidth().height(Metrics.wheelItem).testTag("${tag}_${value.toString().padStart(2, '0')}").clickable {
                select(value)
                scope.launch { state.animateScrollToItem(index) }
            }, contentAlignment = Alignment.Center) {
                Text(value.toString().padStart(2, '0'), style = if (active) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
                    color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
