package app.chronotation.feature.today

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.res.stringResource
import app.chronotation.R
import app.chronotation.data.entity.*
import app.chronotation.domain.*
import app.chronotation.feature.WorkspaceState
import app.chronotation.ui.components.*
import app.chronotation.ui.theme.*
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun TodayScreen(onSettings: () -> Unit, state: WorkspaceState = WorkspaceState(), snapMinutes: Int = 15,
    onPlan: (Long, Instant?) -> Unit = { _, _ -> }, onRecord: (Long, Instant?) -> Unit = { _, _ -> },
    policy: HistoricalPlanPolicy = HistoricalPlanPolicy.IMMUTABLE,
    onHold: (Plan) -> Unit = {}, onMove: (Plan, Instant, Instant) -> Unit = { _, _, _ -> },
    orb: @Composable () -> Unit = {}, weekView: Boolean = true,
    onCreatePlan: ((TimeSpan) -> Unit)? = null, onCreateRecord: ((TimeSpan) -> Unit)? = null,
    selectedDate: LocalDate? = null, showHeader: Boolean = true, showPlans: Boolean = true, showRecords: Boolean = true, calendarCutoff: Instant? = null,
    singleColumn: Boolean = false, swipeDate: Boolean = true, onPlanDate: ((Long, LocalDate) -> Unit)? = null) {
    val preferences = LocalDisplayPreferences.current
    val fallbackName = stringResource(R.string.uncategorized)
    val dayStart = preferences.dayStartMinutes
    var date by rememberSaveable { mutableStateOf(selectedDate ?: logicalDate(Instant.now(), dayStartMinutes = dayStart)) }
    var previousStart by rememberSaveable { mutableIntStateOf(dayStart) }
    LaunchedEffect(dayStart) {
        val instant = Instant.now()
        if (selectedDate == null && date == logicalDate(instant, dayStartMinutes = previousStart)) date = logicalDate(instant, dayStartMinutes = dayStart)
        previousStart = dayStart
    }
    LaunchedEffect(selectedDate) { selectedDate?.let { date = it } }
    var now by remember { mutableStateOf(Instant.now()) }
    // Keyed on the timer so that starting or finishing one re-reads the clock at once: the running
    // record's end has to be right the moment it appears, not a tick later.
    LaunchedEffect(state.timer?.token) { while (true) { now = Instant.now(); delay(30_000) } }
    val zone = ZoneId.systemDefault()
    val day = daySpan(date, zone, dayStart)
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    var chooseDate by remember { mutableStateOf(false) }
    val hourHeight = Metrics.hourHeight
    val height = hourHeight * (day.millis / 3_600_000f)
    val minEventHeight = minEventHeight(hourHeight)
    val minMillis = Metrics.eventMinMinutes * 60_000L
    val floorMillis = Metrics.eventFloorMinutes * 60_000L
    val chipStyle = if (minEventHeight < Metrics.compactLabelThreshold) MaterialTheme.typography.labelMedium.copy(fontSize = Metrics.compactLabelSize, lineHeight = Metrics.compactLabelLineHeight)
    else MaterialTheme.typography.labelMedium
    LaunchedEffect(Unit) {
        // The grid opens two hours above the current time, then keeps the caller's scroll position:
        // changing the day never jumps the axis, no matter which day is on screen.
        val instant = Instant.now()
        val hours = if (instant in day.start..day.end) (Duration.between(day.start, instant).toMillis() / 3_600_000f - 2f).coerceAtLeast(0f) else 7f
        scroll.scrollTo(with(density) { (hourHeight * hours).roundToPx() })
    }
    if (chooseDate) CalendarDialog(stringResource(R.string.date), date, { chooseDate = false }) { date = it; chooseDate = false }
    PageColumn {
        if (showHeader) {
        val locale = androidx.compose.ui.platform.LocalResources.current.configuration.locales[0]
        val monthPattern = if (locale.language == "zh") "yyyy 年 M 月" else "MMMM yyyy"
        Row(Modifier.fillMaxWidth().padding(start = Space.md, top = Space.xxs, end = Space.md), verticalAlignment = Alignment.CenterVertically) {
            // The title is its own rounded touch target, so the press reads as a control instead of a
            // hard-cornered band the width of the row.
            Row(Modifier.height(Metrics.touchTarget).padding(horizontal = Space.xs).clip(MaterialTheme.shapes.small).clickable { chooseDate = true }, verticalAlignment = Alignment.CenterVertically) {
                Text(if (weekView) date.format(DateTimeFormatter.ofPattern(monthPattern, locale)) else dateText(date),
                    style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.weight(1f))
            if (weekView || date != logicalDate(now, zone, dayStart)) TextButton(onClick = { date = logicalDate(Instant.now(), dayStartMinutes = dayStart) }, modifier = Modifier.testTag("jump_today")) { Text(stringResource(R.string.today)) }
        }
        if (weekView) {
            val monday = weekStartOf(date, preferences.weekStart)
            // The weekday names are always the same seven, so that row stays put; only the dates
            // slide, which is what a swipe actually changes.
            val weekSwipe = rememberSwipeController()
            Column(Modifier.fillMaxWidth().padding(horizontal = Space.md).swipeGestures(weekSwipe, { date = date.minusWeeks(1) }, { date = date.plusWeeks(1) })) {
                Row(Modifier.fillMaxWidth()) {
                    repeat(7) { index ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(monday.plusDays(index.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, locale), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().swipeTranslation(weekSwipe)) {
                    repeat(7) { index ->
                        val item = monday.plusDays(index.toLong())
                        Box(Modifier.weight(1f).testTag("week_day_$index").clickable { date = item }.padding(vertical = Space.xxs), contentAlignment = Alignment.Center) {
                            Box(Modifier.size(Metrics.calendarDay).clip(androidx.compose.foundation.shape.CircleShape).background(if (date == item) MaterialTheme.colorScheme.primary else if (item == logicalDate(now, zone, dayStart)) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent), contentAlignment = Alignment.Center) {
                                Text(item.dayOfMonth.toString(), color = if (date == item) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }
        }
        if (!singleColumn) Row(Modifier.fillMaxWidth().padding(vertical = Space.xxs)) {
            if (showPlans) Box(Modifier.weight(1f).padding(start = Metrics.timelineGutter)) { Text(stringResource(R.string.plan), style = MaterialTheme.typography.labelMedium) }
            if (showRecords) Box(Modifier.weight(1f).padding(start = if (showPlans) Space.xs else Metrics.timelineGutter)) { Text(stringResource(R.string.record), style = MaterialTheme.typography.labelMedium) }
        }
        val dayPlans = remember(state.plans, date, dayStart) {
            // The next calendar date is in scope: a late day start pulls its small hours into this day.
            state.plans.flatMap { plan -> plan.expandOccurrences(date.minusDays(1), date.plusDays(1)) }
                .filter { it.viewSpan(dayStart)?.clippedTo(day) != null }
        }
        val dayPlansById = remember(dayPlans) { dayPlans.associateBy { it.id } }
        fun shownSpan(plan: Plan): TimeSpan? = if (calendarCutoff == null) plan.viewSpan(dayStart) else clampToNow(plan.viewSpan(dayStart), calendarCutoff)
        val allDay = dayPlans.filter { it.allDay && shownSpan(it)?.clippedTo(day) != null }
        if (showPlans && allDay.isNotEmpty()) Row(Modifier.fillMaxWidth()) {
            // All-day chips keep the height of the shortest block, so they read as the same kind of chip.
            Column(Modifier.weight(1f).padding(start = Metrics.timelineGutter), verticalArrangement = Arrangement.spacedBy(Metrics.eventGap)) {
                allDay.forEach { plan -> Box(Modifier.fillMaxWidth().height(minEventHeight).padding(vertical = Metrics.eventGap)
                    .clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.secondaryContainer).clickable { onPlan(plan.id, null) }, contentAlignment = Alignment.CenterStart) {
                    Text(itemName(plan.title, state.categories.firstOrNull { it.id == plan.categoryId }), Modifier.padding(horizontal = Space.xs),
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, style = chipStyle)
                } }
            }
            if (showRecords && !singleColumn) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(Space.xs))
        Box(Modifier.weight(1f)) {
            Box(Modifier.fillMaxSize().topFade().verticalScroll(scroll).padding(top = Space.sm).padding(bottom = Metrics.dockClearance)) {
                Box(Modifier.fillMaxWidth().height(height)) {
                    val hours = (day.millis / 3_600_000).toInt()
                    repeat(hours + 1) { hour ->
                        val y = hourHeight * hour.toFloat()
                        if (hour > 0) HorizontalDivider(Modifier.offset(y = y), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = Metrics.gridAlpha))
                        // The gutter is sized for a clock time plus the raised "+1" marker, so the
                        // label holds its single line and stays clear of the blocks beside it.
                        if (hour < hours) Text(axisClockText(day.start.plusSeconds(hour * 3600L), date, zone),
                            Modifier.offset(y = y).padding(start = Space.xxs, top = Space.xxs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    // Swiping over the events moves them; the hour gutter and grid stay put.
                    Row(Modifier.fillMaxSize().horizontalSwipe(
                        enabled = swipeDate,
                        onPrevious = { date = date.minusDays(1) },
                        onNext = { date = date.plusDays(1) },
                    )) {
                        val planSpans = dayPlans.filter { !it.allDay }.mapNotNull { plan -> shownSpan(plan)?.clippedTo(day)?.let { plan.id to it } }
                        val planSpanById = planSpans.toMap()
                        // The running timer is drawn as the record it will become, and it is not in the
                        // database yet, so it is added here rather than in the stored list.
                        val shownRecords = remember(state.records, state.timer, now) { state.records + listOfNotNull(state.timer?.liveRecord(now)) }
                        val recordsById = remember(shownRecords) { shownRecords.associateBy { it.id } }
                        val records = shownRecords.mapNotNull { record -> TimeSpan(record.startTime, record.endTime).clippedTo(day)?.let { record.id to it } }
                        val planLabel = { id: Long -> val plan = dayPlansById.getValue(id); Triple(displayName(plan.title, state.categories.firstOrNull { it.id == plan.categoryId }?.name, preferences.preferCategoryName, fallbackName), state.categories.firstOrNull { it.id == plan.categoryId }, spanState(planSpanById[id], now) == PlanTemporalState.PAST) }
                        val recordLabel = { id: Long -> val record = recordsById.getValue(id); Triple(displayName(record.title, state.categories.firstOrNull { it.id == record.categoryId }?.name, preferences.preferCategoryName, fallbackName), state.categories.firstOrNull { it.id == record.categoryId }, false) }
                        if (singleColumn) Box(Modifier.weight(1f).fillMaxHeight().padding(start = Metrics.timelineGutter)) {
                            val items = (if (showPlans) planSpans.map { TimelineItem(it.first, true) to it.second } else emptyList()) +
                                (if (showRecords) records.map { TimelineItem(it.first, false) to it.second } else emptyList())
                            TimelineLane(Modifier.fillMaxSize(), day, items, snapMinutes, hourHeight, true, mixed = true,
                                add = { span -> if (span.start >= Instant.now()) onCreatePlan?.invoke(span) ?: onPlan(0, span.start) else onCreateRecord?.invoke(span) ?: onRecord(0, span.start) },
                                label = { item -> if (item.isPlan) planLabel(item.id) else recordLabel(item.id) },
                                click = { item -> if (item.isPlan) { if (onPlanDate != null) onPlanDate(item.id, date) else onPlan(item.id, null) } else onRecord(item.id, null) },
                                tintFallback = { MaterialTheme.colorScheme.tertiary },
                                isPlan = { item -> item.isPlan }, isLive = { item -> !item.isPlan && item.id == LIVE_RECORD_ID },
                                dragPlan = { item -> if (item.isPlan) dayPlansById[item.id]?.takeUnless { it.isRecurring() } else null }, onHold = onHold, onMove = onMove)
                        } else {
                            if (showPlans) Box(Modifier.weight(1f).fillMaxHeight().padding(start = Metrics.timelineGutter)) {
                                TimelineLane(Modifier.fillMaxSize(), day, planSpans, snapMinutes, hourHeight, true, add = { span -> onCreatePlan?.invoke(span) ?: onPlan(0, span.start) },
                                    label = planLabel, tintFallback = { MaterialTheme.colorScheme.tertiary }, click = { if (onPlanDate != null) onPlanDate(it, date) else onPlan(it, null) }, dragPlan = { id -> dayPlansById[id]?.takeUnless { it.isRecurring() } }, onHold = onHold, onMove = onMove)
                            }
                            if (showRecords) Box(Modifier.weight(1f).fillMaxHeight().padding(start = if (showPlans) 0.dp else Metrics.timelineGutter)) {
                                if (showPlans) VerticalDivider(Modifier.align(Alignment.CenterStart), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = Metrics.gridAlpha))
                                TimelineLane(Modifier.fillMaxSize(), day, records, snapMinutes, hourHeight, false, add = { span -> onCreateRecord?.invoke(span) ?: onRecord(0, span.start) },
                                    label = recordLabel, isLive = { it == LIVE_RECORD_ID }, click = { onRecord(it, null) })
                            }
                        }
                    }
                    if (now >= day.start && now < day.end) {
                        val y = hourHeight * (Duration.between(day.start, now).toMillis() / 3_600_000f)
                        HorizontalDivider(Modifier.offset(y = y), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(Space.md)) { orb() }
        }
    }
}

private fun Modifier.timelineGesture(key: Any, consumeDown: Boolean = false, tap: () -> Unit = {}, start: (Offset) -> Unit, drag: (Offset) -> Unit, end: () -> Unit, cancel: () -> Unit): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown()
        if (consumeDown) down.consume()
        val completed = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            val up = waitForUpOrCancellation()
            if (up != null) { up.consume(); tap() }
            true
        }
        if (completed == null && currentEvent.changes.any { it.pressed }) {
            currentEvent.changes.forEach { it.consume() }
            start(down.position)
            var previous = down.position
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) { cancel(); break }
                drag(change.position - previous); previous = change.position
                change.consume()
                if (!change.pressed) { end(); break }
            }
        }
    }
}

/** Key for a single mixed lane, where plan and record ids can collide. */
private data class TimelineItem(val id: Long, val isPlan: Boolean)

@Composable
private fun <T> TimelineLane(modifier: Modifier, day: TimeSpan, items: List<Pair<T, TimeSpan>>, snap: Int, hourHeight: androidx.compose.ui.unit.Dp, planSide: Boolean,
    mixed: Boolean = false, add: (TimeSpan) -> Unit, label: (T) -> Triple<String, Category?, Boolean>, click: (T) -> Unit,
    // An entry with no category is grey everywhere: its colour must not depend on which page, or which
    // column, happens to be drawing it.
    tintFallback: @Composable (T) -> Color = { MaterialTheme.colorScheme.tertiary },
    // Whether an item is a plan decides how its block is painted. It is asked separately from
    // [dragPlan] because a recurring plan is still a plan even though it cannot be dragged, and asked
    // again as [isLive] because the running timer is a record that has not been stored yet.
    isPlan: (T) -> Boolean = { planSide }, isLive: (T) -> Boolean = { false },
    dragPlan: (T) -> Plan? = { null }, onHold: (Plan) -> Unit = {}, onMove: (Plan, Instant, Instant) -> Unit = { _, _, _ -> }) {
    val density = LocalDensity.current
    val hourPx = with(density) { hourHeight.toPx() }
    val minEventHeight = minEventHeight(hourHeight)
    val minMillis = Metrics.eventMinMinutes * 60_000L
    val floorMillis = Metrics.eventFloorMinutes * 60_000L
    var anchor by remember { mutableFloatStateOf(0f) }
    var current by remember { mutableFloatStateOf(0f) }
    var selection by remember { mutableStateOf<TimeSpan?>(null) }
    // A mixed lane creates a plan from a future anchor and a record from a past one.
    fun planSelection(anchorMinutes: Float): Boolean = if (mixed) day.start.plusMillis((anchorMinutes * 60_000f).toLong()) >= Instant.now() else planSide
    BoxWithConstraints(modifier.timelineGesture(listOf(day, snap, hourHeight, planSide, mixed), start = {
        anchor = it.y / hourPx * 60; current = anchor
        selection = timelineSelection(day, anchor, current, snap, planSelection(anchor), Instant.now())
    }, drag = { current += it.y / hourPx * 60; selection = timelineSelection(day, anchor, current, snap, planSelection(anchor), Instant.now()) },
        end = { timelineSelection(day, anchor, current, snap, planSelection(anchor), Instant.now())?.let(add); selection = null }, cancel = { selection = null })) {
        val width = maxWidth
        selection?.let { span ->
            // The pending block is drawn with the same shape and inset as a real one, so it reads as
            // the block that is about to appear rather than as a bare bar.
            Box(Modifier.fillMaxWidth().offset(y = hourHeight * (Duration.between(day.start, span.start).toMillis() / 3_600_000f))
                .height(hourHeight * (span.millis / 3_600_000f)).padding(Metrics.eventGap)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(Radii.event))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = .2f)))
        }
        // Blocks keep the minimum height while nothing follows, squeeze into the room when something
        // does, and only take a lane of their own when not even the floor fits.
        arrangeTimeline(items, minMillis, floorMillis).forEach { placed -> key(placed.id) {
            val (title, category, past) = label(placed.id)
            val tint = category?.color?.let { Color(it) } ?: tintFallback(placed.id)
            val itemWidth = width / placed.lanes
            val y = hourHeight * (Duration.between(day.start, placed.span.start).toMillis() / 3_600_000f)
            val h = hourHeight * (placed.drawn.millis / 3_600_000f)
            val plan = dragPlan(placed.id)
            var pixels by remember { mutableFloatStateOf(0f) }
            var temporal by remember { mutableStateOf(PlanTemporalState.UNSCHEDULED) }
            val drawn = h
            Box(Modifier.offset { IntOffset((itemWidth * placed.lane).roundToPx(), y.roundToPx() + pixels.roundToInt()) }.width(itemWidth).height(drawn)
                .padding(horizontal = Metrics.blockSideGap, vertical = Metrics.eventGap).eventBlock(isPlan(placed.id), tint, past, isLive(placed.id))
                .testTag("event_block")
                .timelineGesture(listOf(placed.id, placed.span, hourHeight, plan), consumeDown = true, tap = { click(placed.id) }, start = {
                    temporal = plan?.temporalState(Instant.now()) ?: PlanTemporalState.UNSCHEDULED
                }, drag = { if (temporal == PlanTemporalState.FUTURE) pixels += it.y },
                    end = {
                        if (plan != null) {
                            if (temporal != PlanTemporalState.FUTURE) onHold(plan)
                            else if (kotlin.math.abs(pixels) > 2) plan.timeSpan()?.let { original ->
                                val seconds = (pixels / hourPx * 60 / snap).roundToInt() * snap * 60L
                                if (canCreatePlanAt(original.start.plusSeconds(seconds), Instant.now())) onMove(plan, original.start.plusSeconds(seconds), original.end.plusSeconds(seconds))
                            }
                        } else click(placed.id)
                        pixels = 0f
                    }, cancel = { pixels = 0f })) {
                // The inset tightens as the block shrinks, so a short block spends its room on the
                // text; below the minimum the icon goes, then the type shrinks, then the text goes.
                val inset = blockInset(drawn, minEventHeight, Metrics.compactLabelThreshold + Space.xxs * 2)
                val full = drawn >= minEventHeight
                val baseLabel = drawn >= Metrics.compactLabelThreshold + inset * 2
                val anyLabel = drawn >= Metrics.compactLabelHeight + inset * 2
                Row(Modifier.padding(horizontal = Space.xxs, vertical = inset), horizontalArrangement = Arrangement.spacedBy(Space.xxs), verticalAlignment = Alignment.CenterVertically) {
                    if (full) Icon(categoryIcons.firstOrNull { it.first == category?.icon }?.second ?: AppIcons.Categories, null, Modifier.size(Metrics.timelineIcon), tint = tint)
                    if (anyLabel) Text(title, style = if (baseLabel) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelMedium.copy(fontSize = Metrics.compactLabelSize, lineHeight = Metrics.compactLabelLineHeight),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (past) .6f else 1f), maxLines = if (drawn < 40.dp) 1 else 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }
            }
        } }
    }
}
