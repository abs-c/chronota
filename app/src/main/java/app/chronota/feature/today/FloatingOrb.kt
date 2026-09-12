package app.chronota.feature.today

import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.border
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import app.chronota.R
import app.chronota.data.entity.TimerSession
import app.chronota.domain.*
import app.chronota.ui.components.glassRing
import app.chronota.ui.components.glassSurface
import app.chronota.ui.components.AppIcons
import app.chronota.ui.theme.Metrics
import app.chronota.ui.theme.Space
import java.time.Instant
import kotlinx.coroutines.delay
import kotlin.math.*

private fun wheelAngle(index: Int): Double = index * (90.0 / maxOf(1, orbActions.size - 1))
fun wheelHit(point: Offset, radius: Float, hitRadius: Float): OrbAction? = orbActions.withIndex().firstOrNull { (index, _) ->
    val angle = Math.toRadians(wheelAngle(index))
    (point - Offset((-radius * cos(angle)).toFloat(), (-radius * sin(angle)).toFloat())).getDistance() <= hitRadius
}?.value

@Composable
fun FloatingOrb(timer: TimerSession?, defaultAction: OrbAction, onAction: (OrbAction) -> Unit, onTimer: () -> Unit, onExpanded: (Boolean) -> Unit = {}, backdrop: androidx.compose.ui.graphics.layer.GraphicsLayer? = null, backdropOrigin: Offset = Offset.Zero, fixedAction: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) { onExpanded(expanded) }
    BackHandler(expanded) { expanded = false }
    var pressed by remember { mutableStateOf(false) }
    val expansion by animateFloatAsState(if (expanded) 1f else 0f, spring(dampingRatio = .72f, stiffness = 550f), label = "wheel")
    val pressScale by animateFloatAsState(if (pressed && !expanded) .9f else 1f, tween(110), label = "orbPress")
    val hold = remember { Animatable(0f) }
    LaunchedEffect(pressed) { if (pressed) hold.animateTo(1f, tween(500)) else hold.snapTo(0f) }
    var highlighted by remember { mutableStateOf<OrbAction?>(null) }
    var now by remember { mutableStateOf(Instant.now()) }
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val actionCallback by rememberUpdatedState(onAction)
    val timerCallback by rememberUpdatedState(onTimer)
    val radius = with(density) { Metrics.wheelRadius.toPx() }
    val hitRadius = with(density) { (Metrics.orb / 2).toPx() }
    val description = stringResource(if (fixedAction) defaultAction.label() else if (timer == null) R.string.orb_actions else R.string.timer)
    fun execute(action: OrbAction) { expanded = false; highlighted = null; if (action == OrbAction.TIMER && timer != null) timerCallback() else actionCallback(action) }
    fun tap() { if (expanded) expanded = false else if (fixedAction || timer == null) actionCallback(defaultAction) else timerCallback() }
    LaunchedEffect(timer?.token) { if (timer != null) while (true) { now = Instant.now(); delay(1000) } }
    Box(Modifier.requiredSize(Metrics.orb).testTag("action_orb"), contentAlignment = Alignment.BottomEnd) {
        if (expanded) {
            Box(Modifier.wrapContentSize(Alignment.BottomEnd, unbounded = true).requiredSize(Metrics.wheelSize)) {
                orbActions.forEachIndexed { index, action ->
                    val angle = Math.toRadians(wheelAngle(index))
                    val selected = highlighted == action
                    Column(Modifier.align(Alignment.BottomEnd).offset { IntOffset((-radius * expansion * cos(angle)).roundToInt(), (-radius * expansion * sin(angle)).roundToInt()) }
                        .size(Metrics.orb).graphicsLayer { alpha = expansion.coerceIn(0f, 1f); scaleX = .7f + .3f * expansion; scaleY = scaleX }.clip(CircleShape).glassSurface(backdrop, backdropOrigin, MaterialTheme.colorScheme.surfaceContainerHigh, tintAlpha = .16f).background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = .8f) else Color.Transparent).glassRing()
                        .testTag("wheel_${action.name}")
                        .semantics { onClick { execute(action); true } }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        TextButton(onClick = { execute(action) }, contentPadding = PaddingValues(), modifier = Modifier.fillMaxSize()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(action.icon(), null, Modifier.size(Metrics.icon), tint = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                Text(stringResource(action.label()), style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }
        // No panel behind the elapsed time: it reads straight on the page.
        if (timer != null && !expanded) Text(timerElapsedText(timer.elapsed(now)),
            Modifier.align(Alignment.TopEnd).offset(y = -Metrics.orb / 2), style = MaterialTheme.typography.labelMedium)
        // The dock's own material, nothing added: the same glass, the same wash of the surface colour,
        // the same light falling on it and the same rim. No colour of its own — what tells it apart is
        // its shape and the icon on it.
        Box(Modifier.size(Metrics.orb).graphicsLayer { scaleX = pressScale; scaleY = pressScale }.clip(CircleShape)
            .glassSurface(backdrop, backdropOrigin, MaterialTheme.colorScheme.surface, tintAlpha = .05f)
            .drawWithContent {
                drawContent()
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .18f), Color.Transparent), endY = size.height * .58f))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .025f)), startY = size.height * .75f))
            }
            .glassRing()
            .semantics { contentDescription = description; role = Role.Button; onClick { tap(); true }; onLongClick { expanded = true; true } }
            .testTag(if (fixedAction) if (defaultAction == OrbAction.PLAN) "add_plan" else "add_record" else "orb_primary").pointerInput(defaultAction, timer?.token, fixedAction) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
                    if (up != null) { pressed = false; up.consume(); tap() }
                    else {
                        if (currentEvent.changes.none { it.pressed }) { pressed = false; return@awaitEachGesture }
                        expanded = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            val relative = change.position - Offset(size.width / 2f, size.height / 2f)
                            val target = wheelHit(relative, radius, hitRadius)
                            if (target != highlighted) { highlighted = target; if (target != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                            change.consume()
                            if (!change.pressed) {
                                pressed = false
                                // Releasing anywhere but on an option dismisses the wheel.
                                if (target != null) execute(target) else expanded = false
                                highlighted = null
                                break
                            }
                        }
                    }
                }
            }, contentAlignment = Alignment.Center) {
            Icon(if (expanded) AppIcons.Close else if (timer != null && !fixedAction) AppIcons.Timer else defaultAction.icon(), null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(Metrics.icon))
            if (pressed && !expanded) Canvas(Modifier.matchParentSize().padding(Space.xxs)) { drawArc(Color.White.copy(alpha = .8f), -90f, hold.value * 360f, false, style = Stroke(Metrics.outline.toPx())) }
        }
    }
}

fun OrbAction.icon() = when (this) {
    OrbAction.PLAN -> AppIcons.Plan; OrbAction.RECORD -> AppIcons.Record; OrbAction.INBOX -> AppIcons.Todo
    OrbAction.GOAL -> AppIcons.Target
    OrbAction.TIMER -> AppIcons.Timer
}
// One label per action so the wheel and the Settings picker always read the same.
fun OrbAction.label(): Int = when (this) {
    OrbAction.PLAN -> R.string.wheel_plan
    OrbAction.GOAL -> R.string.new_goal
    OrbAction.RECORD -> R.string.wheel_record
    OrbAction.TIMER -> R.string.timing
    OrbAction.INBOX -> R.string.open_inbox
}
fun timerElapsedText(millis: Long): String = String.format(java.util.Locale.getDefault(), "%02d:%02d:%02d", millis / 3_600_000, millis / 60_000 % 60, millis / 1000 % 60)
