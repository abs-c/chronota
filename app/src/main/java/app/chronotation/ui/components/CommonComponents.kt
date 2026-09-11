package app.chronotation.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.chronotation.R
import app.chronotation.ui.theme.Metrics
import app.chronotation.ui.theme.Space
import app.chronotation.ui.theme.Radii
import kotlinx.coroutines.launch

@Composable
fun AppDivider() = HorizontalDivider(
    thickness = Metrics.divider,
    color = MaterialTheme.colorScheme.outlineVariant,
)

@Composable
fun PageHeader(
    @StringRes title: Int,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onCategories: (() -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Metrics.pageHeader)
            .padding(horizontal = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) HeaderAction(AppIcons.Back, R.string.back, onBack)
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f).padding(horizontal = Space.xs)
                .semantics { heading() },
        )
        if (onCategories != null) HeaderAction(AppIcons.Categories, R.string.categories, onCategories)
        if (onAdd != null) HeaderAction(AppIcons.Add, R.string.add, onAdd)
        if (onSettings != null) HeaderAction(AppIcons.Wrench, R.string.settings, onSettings)
    }
}

@Composable
private fun HeaderAction(icon: ImageVector, @StringRes description: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(Metrics.touchTarget)) {
        Icon(icon, stringResource(description), modifier = Modifier.size(Metrics.icon))
    }
}

@Composable
fun EmptyState(icon: ImageVector, @StringRes title: Int, @StringRes body: Int) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(Space.lg).widthIn(max = Metrics.emptyMaxWidth),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Icon(icon, null, Modifier.size(Metrics.emptyIcon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(title), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Text(
                stringResource(body), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun AppFilterChip(selected: Boolean, onClick: () -> Unit, label: String, modifier: Modifier = Modifier) {
    FilterChip(selected, onClick, label = { Text(label, style = MaterialTheme.typography.labelMedium.copy(lineHeight = Metrics.chipLineHeight), maxLines = 1) }, shape = MaterialTheme.shapes.small, border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer),
        modifier = modifier.height(Metrics.chipHeight))
}

/** iOS-style segmented control, shared by the page tabs and in-dialog switchers. */
@Composable
fun SegmentedTabs(labels: List<Int>, selected: Int, select: (Int) -> Unit, tag: String, modifier: Modifier = Modifier) {
    Row(modifier.height(Metrics.segmentHeight).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainer).padding(Space.xxs),
        horizontalArrangement = Arrangement.spacedBy(Space.xxs), verticalAlignment = Alignment.CenterVertically) {
        labels.forEachIndexed { i, label ->
            val active = i == selected
            Box(Modifier.weight(1f).fillMaxHeight().clip(MaterialTheme.shapes.small)
                .background(if (active) MaterialTheme.colorScheme.surfaceContainerLowest else Color.Transparent)
                .clickable { select(i) }.testTag("${tag}_$i"), contentAlignment = Alignment.Center) {
                Text(stringResource(label), style = MaterialTheme.typography.labelLarge, maxLines = 1,
                    color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Empty-list message, inset to line up with the text of the rows it replaces. */
@Composable
fun ListEmpty(@StringRes text: Int, modifier: Modifier = Modifier) {
    Text(stringResource(text), modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.lg),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Centred hint for an empty list the floating orb fills. */
@Composable
fun CenterHint(@StringRes text: Int, modifier: Modifier = Modifier) {
    Text(stringResource(text), modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.lg), textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Shared state of a horizontal paging swipe: the gesture and the translation can sit on different elements. */
class SwipeController internal constructor() {
    internal val offset = Animatable(0f)
    internal var width by mutableIntStateOf(1)
    val translation: Float get() = offset.value
}

@Composable
fun rememberSwipeController(): SwipeController = remember { SwipeController() }

/** Swipe gesture: slides the content out, switches period, slides the next one in. */
@Composable
fun Modifier.swipeGestures(controller: SwipeController, onPrevious: () -> Unit, onNext: () -> Unit, enabled: Boolean = true): Modifier {
    val scope = rememberCoroutineScope()
    val threshold = with(LocalDensity.current) { 72.dp.toPx() }
    val previous by rememberUpdatedState(onPrevious)
    val next by rememberUpdatedState(onNext)
    return this.onSizeChanged { controller.width = it.width.coerceAtLeast(1) }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            detectHorizontalDragGestures(
                onDragCancel = { scope.launch { controller.offset.animateTo(0f, tween(160)) } },
                onDragEnd = {
                    val dx = controller.offset.value
                    val width = controller.width.toFloat()
                    scope.launch {
                        when {
                            dx > threshold -> { controller.offset.animateTo(width, tween(160)); previous(); controller.offset.snapTo(-width); controller.offset.animateTo(0f, tween(160)) }
                            dx < -threshold -> { controller.offset.animateTo(-width, tween(160)); next(); controller.offset.snapTo(width); controller.offset.animateTo(0f, tween(160)) }
                            else -> controller.offset.animateTo(0f, tween(160))
                        }
                    }
                },
            ) { change, amount ->
                change.consume()
                val width = controller.width.toFloat()
                scope.launch { controller.offset.snapTo((controller.offset.value + amount).coerceIn(-width, width)) }
            }
        }
}

/** Applies the swipe displacement; put it only on the elements that should follow the finger. */
@Composable fun Modifier.swipeTranslation(controller: SwipeController): Modifier = this.graphicsLayer { translationX = controller.translation }

/** Convenience for the common case where gesture and displacement live on the same element. */
@Composable
fun Modifier.horizontalSwipe(onPrevious: () -> Unit, onNext: () -> Unit, enabled: Boolean = true): Modifier {
    val controller = rememberSwipeController()
    return this.swipeGestures(controller, onPrevious, onNext, enabled).swipeTranslation(controller)
}

/** Softens content that scrolls directly under a static row. */
@Composable
fun Modifier.topFade(height: androidx.compose.ui.unit.Dp = 12.dp): Modifier {
    val fade = with(androidx.compose.ui.platform.LocalDensity.current) { height.toPx() }
    return this.graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }.drawWithContent {
        drawContent()
        drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(
            0f to androidx.compose.ui.graphics.Color.Transparent, 1f to androidx.compose.ui.graphics.Color.Black,
            startY = 0f, endY = fade, tileMode = androidx.compose.ui.graphics.TileMode.Clamp),
            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn)
    }
}

@Composable
fun PageColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = Metrics.contentMaxWidth).fillMaxSize(), content = content)
    }
}

/** Height of one hour in the timeline grids: 24 hours stretched over 1.3 screens. */
@Composable
fun rememberTimelineHourHeight(): androidx.compose.ui.unit.Dp =
    with(androidx.compose.ui.platform.LocalDensity.current) {
        androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.height.toDp() * 1.3f / 24f
    }

/**
 * The shortest a timeline block is drawn: the height of [Metrics.eventMinMinutes] minutes at the
 * caller's scale, so short entries stay visible without looking like longer ones. Derived from the
 * hour height the caller draws with, which keeps blocks and all-day chips at the same height.
 */
fun minEventHeight(hourHeight: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp =
    hourHeight * (Metrics.eventMinMinutes / 60f)

/**
 * Inset a block of [height] keeps around its label.
 *
 * [fullInsetFrom] is where a label plus the full inset still fit — the label's own line height plus
 * two [Space.xxs]. At or above it the block keeps the full inset; below it the inset tightens evenly
 * down to [Metrics.blockInsetMin] at [minHeight], so a short block spends its room on the text.
 */
fun blockInset(height: androidx.compose.ui.unit.Dp, minHeight: androidx.compose.ui.unit.Dp, fullInsetFrom: androidx.compose.ui.unit.Dp): androidx.compose.ui.unit.Dp {
    val fraction = if (fullInsetFrom <= minHeight) 1f else ((height - minHeight) / (fullInsetFrom - minHeight)).coerceIn(0f, 1f)
    return Metrics.blockInsetMin + (Space.xxs - Metrics.blockInsetMin) * fraction
}

/**
 * How a block is painted: a record is a filled block, a plan is a pale one behind a thin outline, and
 * the running timer is the same filled block as a record with a heavier fill — it is the record that is
 * still going, not a different kind of thing. Only the paint differs: shape, insets, gaps and type all
 * come from the caller untouched, so every block of the same length fills the same rectangle. [muted]
 * halves the paint, for a block whose time has passed.
 */
@Composable fun Modifier.eventBlock(plan: Boolean, tint: Color, muted: Boolean = false, live: Boolean = false): Modifier {
    val shape = RoundedCornerShape(Radii.event)
    val dim = if (muted) Metrics.pastBlockAlpha else 1f
    return when {
        live -> clip(shape).background(tint.copy(alpha = Metrics.liveFillAlpha * dim))
        plan -> clip(shape)
            .background(tint.copy(alpha = Metrics.planFillAlpha * dim))
            .border(Metrics.outline, tint.copy(alpha = Metrics.planOutlineAlpha * dim), shape)
        else -> clip(shape).background(tint.copy(alpha = Metrics.recordFillAlpha * dim))
    }
}

