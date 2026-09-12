package app.chronota.feature.browse

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle as ComposeTextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.chronota.R
import app.chronota.domain.*
import app.chronota.feature.WorkspaceState
import app.chronota.feature.today.TodayScreen
import app.chronota.feature.todo.GoalList
import app.chronota.ui.components.*
import app.chronota.ui.theme.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters

@Composable
fun BrowseScreen(review: Boolean, state: WorkspaceState, onPlan: (Long) -> Unit, onRecord: (Long) -> Unit,
    add: (LocalDate?) -> Unit, createPlan: (TimeSpan) -> Unit = { onPlan(0) }, createRecord: (TimeSpan) -> Unit = { onRecord(0) }, statistics: @Composable () -> Unit = {},
    onGoal: (Long) -> Unit = {}, onAddGoal: () -> Unit = {}, onDeleteGoal: (Long) -> Unit = {}, onModeChange: (Int) -> Unit = {}, onPlanOccurrence: (Long, LocalDate?) -> Unit = { id, _ -> onPlan(id) }) {
    val savedPages = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
    var mode by rememberSaveable { mutableIntStateOf(0) }
    // Both timelines start with the details open, and review reads chronologically by default.
    var details by rememberSaveable { mutableStateOf(true) }
    var descending by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val dayStart = LocalDisplayPreferences.current.dayStartMinutes
    val zone = ZoneId.systemDefault()
    var category by rememberSaveable { mutableStateOf<Long?>(null) }

    // One ticking clock for this screen: the running timer's row grows with it, and it is also what
    // decides which plans have already ended.
    val now = rememberNow()
    val entries = remember(state.plans, state.records, state.timer, review, category, dayStart, state.categories, now) {
        val filterIds = category?.let { id -> setOf(id) + state.categories.filter { it.parentId == id }.map { it.id } }
        // The running timer rides with the records: this agenda keeps to plans on one tab and to
        // records on the other, and an unfinished record belongs with the records.
        val records = if (review) state.records + listOfNotNull(state.timer?.liveRecord(now)) else emptyList()
        browseEntries(if (review) emptyList() else state.plans, records, dayStartMinutes = dayStart)
            .filter { filterIds == null || it.categoryId in filterIds }
    }
    // The plans page keeps two tabs — its timeline and its goals — and they split the bar evenly.
    // Clamping also folds away a mode saved by a build that still had a calendar here.
    val tab = if (review) mode else mode.coerceIn(0, 1)
    LaunchedEffect(tab) { onModeChange(tab) }
    PageColumn {
        ModeTabs(if (review) listOf(R.string.agenda_view, R.string.calendar_view, R.string.category_totals) else listOf(R.string.agenda_view, R.string.goals), tab, { mode = it }, "browse_mode",
            top = Space.md, bottom = Space.xs)
        when (tab) {
            0 -> {
                Row(Modifier.fillMaxWidth().padding(horizontal = Space.md), verticalAlignment = Alignment.CenterVertically) {
                    val chipScroll = rememberScrollState()
                    val chipFade by animateFloatAsState(if (chipScroll.canScrollForward) 1f else 0f, tween(150), label = "chipFade")
                    Box(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(chipScroll), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                            AppFilterChip(category == null, { category = null }, stringResource(R.string.all_items))
                            state.categories.filter { it.parentId == null }.forEach { item ->
                                AppFilterChip(category == item.id, { category = item.id }, item.name)
                            }
                            Spacer(Modifier.width(Space.md))
                        }
                        if (chipFade > 0f) Box(Modifier.matchParentSize().graphicsLayer { alpha = chipFade }) {
                            Box(Modifier.align(Alignment.CenterEnd).width(Space.lg).fillMaxHeight().background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))))
                        }
                    }
                    IconToggleButton(details, { details = it }, Modifier.testTag("agenda_details")) {
                        Icon(AppIcons.Settings, stringResource(R.string.show_details), Modifier.size(Metrics.icon))
                    }
                    if (review) IconButton(onClick = { descending = !descending }, Modifier.testTag("agenda_sort")) {
                        Icon(AppIcons.Down, stringResource(if (descending) R.string.sort_descending else R.string.sort_ascending),
                            Modifier.size(Metrics.icon).graphicsLayer { rotationZ = if (descending) 0f else 180f })
                    } else IconToggleButton(showHistory, { showHistory = it }, Modifier.testTag("agenda_history")) {
                        Icon(AppIcons.Notebook, stringResource(R.string.show_history), Modifier.size(Metrics.icon))
                    }
                }
                // Plans read forward from now; past ones only appear when asked for.
                val visible = if (review) entries else entries.filter { showHistory || it.span?.end?.isAfter(now) != false }
                val reverse = review && descending
                val groups = visible.groupBy { it.date?.toString() ?: "" }.toSortedMap(if (reverse) reverseOrder() else naturalOrder())
                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                // Plans read forward, so they open on today and never jump to the bottom; review opens
                // on the newest day, which is the top when descending and the bottom when ascending.
                val today = logicalDate(now, zone, dayStart)
                val anchor = if (!review) groups.keys.indexOfFirst { it >= today.toString() }.takeIf { it >= 0 }
                    else if (reverse) 0 else groups.size - 1
                LaunchedEffect(anchor, visible.size) {
                    if (anchor != null && visible.isNotEmpty()) listState.scrollToItem(anchor)
                }
                LazyColumn(Modifier.weight(1f).topFade().testTag(if (review) "review_list" else "plan_list"), state = listState, contentPadding = PaddingValues(start = Space.md, end = Space.md, top = Space.sm, bottom = Metrics.dockClearance)) {
                    groups.forEach { (key, group) ->
                        val ordered = if (reverse) group.sortedByDescending { it.span?.start ?: Instant.MAX } else group.sortedBy { it.span?.start ?: Instant.MAX }
                        // The heading's date is the logical day these rows belong to, so a row's own
                        // times can tell whether they fell on the next calendar date.
                        val groupDate = group.first().date
                        item(key = "group_$key") {
                            Text(groupDate?.let { dateText(it) } ?: stringResource(R.string.inbox),
                                Modifier.padding(top = Space.sm, bottom = Space.xxs), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.fillMaxWidth()) {
                                ordered.forEach { entry -> TimelineRow(entry, state, details, descending = reverse, logicalDate = groupDate) { if (entry.isPlan) onPlanOccurrence(entry.id, entry.occurrence) else onRecord(entry.id) } }
                            }
                        }
                    }
                    if (visible.isEmpty()) item { CenterHint(R.string.empty_add_hint) }
                }
            }
            1 -> if (review) savedPages.SaveableStateProvider("calendar") {
                Column(Modifier.weight(1f)) { CalendarBrowser(state, onPlan, onRecord, add, createPlan, createRecord) }
            } else savedPages.SaveableStateProvider("goals") { Box(Modifier.weight(1f)) { GoalList(state, onGoal, onAddGoal, onDeleteGoal) } }
            else -> savedPages.SaveableStateProvider("statistics") { Box(Modifier.weight(1f)) { statistics() } }
        }
    }

}

/**
 * Segmented tabs for switching modes and calendar scales.
 *
 * [top] places the tabs themselves: the page-level bar keeps the same 16dp the primary pages use for
 * their titles, so its top edge lines up with Today and Me. [bottom] is the room left for whatever
 * follows — the timeline category chips, the calendar scale row or the goal list — which all start at
 * the same height because they use the same 12dp inset.
 */
@Composable
fun ModeTabs(labels: List<Int>, selected: Int, select: (Int) -> Unit, tag: String,
    top: androidx.compose.ui.unit.Dp = Space.sm, bottom: androidx.compose.ui.unit.Dp = Space.sm) {
    SegmentedTabs(labels, selected, select, tag, Modifier.fillMaxWidth().padding(horizontal = Space.md).padding(top = top, bottom = bottom))
}

/**
 * [showRange] is for the places with no time axis beside the card — the month page's list — where the
 * card is the only thing that can say when the entry ran. The agenda rows print the times already and
 * take the duration here instead.
 */
@Composable private fun AgendaCard(entry: BrowseEntry, state: WorkspaceState, showKind: Boolean = false, details: Boolean = false, showRange: Boolean = false, click: () -> Unit) {
    val category = state.categories.firstOrNull { it.id == entry.categoryId }
    val plan = if (entry.isPlan) state.plans.firstOrNull { it.id == entry.id } else null
    val name = itemName(entry.title, category)
    val meta = listOfNotNull(
        if (showKind) stringResource(if (entry.isPlan) R.string.plan else R.string.record) else null,
        // The running timer says so: its span only ever reaches "now", so it would otherwise read as
        // an entry that was cut short.
        if (entry.live) stringResource(R.string.timer_running) else null,
        category?.name?.takeIf { it != name },
        when {
            entry.allDay -> stringResource(R.string.all_day)
            entry.span == null -> null
            showRange -> spanTimeText(entry.span)
            entry.span.millis > 0 -> durationText(entry.span.millis)
            else -> null
        },
    ).joinToString(" · ")
    Row(Modifier.fillMaxWidth().cardSurface()
        .testTag("agenda_card")
        .clickable(onClick = click).padding(horizontal = Space.md, vertical = Space.sm), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Box(Modifier.padding(top = Space.xxs)) { CategoryMark(category) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
            Text(itemName(entry.title, category), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (details) AttributeSummary(entry, state)
        }
    }
}

@Composable private fun TimelineRow(entry: BrowseEntry, state: WorkspaceState, details: Boolean, descending: Boolean = false, logicalDate: LocalDate? = null, click: () -> Unit) {
    val category = state.categories.firstOrNull { it.id == entry.categoryId }
    val tint = category?.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val zone = ZoneId.systemDefault()
    val span = entry.span
    // An entry with no duration is a moment: it shows one time, not a range that starts and ends alike.
    // A range that crosses midnight prints both dates; otherwise a time that fell on the next calendar
    // date says so with a "+1", so the axis never claims it belongs to the date above it.
    val ends = span?.let { axisRangeEndsText(it, logicalDate, zone) }
    val start = ends?.first ?: AnnotatedString(if (entry.allDay) stringResource(R.string.all_day) else "—")
    val end = if (span != null && span.millis > 0) ends?.second else null
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    // The dot stays on the card icon's line; the times are laid out around it. The start is the
    // larger line, and the dot meets its inner edge — its bottom when the start reads first, its top
    // when the order is reversed. A moment has no second line, so its one time centres on the dot.
    Row(Modifier.fillMaxWidth().drawBehind {
        val x = (Metrics.agendaTime + Metrics.agendaAxis / 2).toPx()
        val center = Metrics.agendaNodeCenter.toPx()
        drawLine(axisColor, Offset(x, 0f), Offset(x, size.height), Metrics.divider.toPx())
        drawCircle(tint, Metrics.dotSmall.toPx() / 2f, Offset(x, center))
    }) {
        Column(Modifier.width(Metrics.agendaTime), horizontalAlignment = Alignment.End) {
            when {
                end == null -> Box(Modifier.height(Metrics.agendaTimeHeight), contentAlignment = Alignment.CenterEnd) {
                    TimeLine(start, MaterialTheme.typography.labelMedium)
                }
                descending -> {
                    Box(Modifier.height(Metrics.agendaNodeCenter - Metrics.agendaTimeGap), contentAlignment = Alignment.BottomEnd) {
                        TimeLine(end, MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(Metrics.agendaTimeGap))
                    TimeLine(start, MaterialTheme.typography.labelMedium)
                }
                else -> {
                    Box(Modifier.height(Metrics.agendaNodeCenter), contentAlignment = Alignment.BottomEnd) {
                        TimeLine(start, MaterialTheme.typography.labelMedium)
                    }
                    Spacer(Modifier.height(Metrics.agendaTimeGap))
                    TimeLine(end, MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.width(Metrics.agendaAxis))
        Box(Modifier.weight(1f).padding(bottom = Space.xs)) { AgendaCard(entry, state, details = details, click = click) }
    }
}

@Composable private fun TimeLine(text: AnnotatedString, style: ComposeTextStyle) =
    Text(text, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)

@Composable private fun ColumnScope.CalendarBrowser(state: WorkspaceState, onPlan: (Long) -> Unit, onRecord: (Long) -> Unit, add: (LocalDate?) -> Unit, createPlan: (TimeSpan) -> Unit, createRecord: (TimeSpan) -> Unit) {
    var scale by rememberSaveable { mutableIntStateOf(2) }
    val dayStart = LocalDisplayPreferences.current.dayStartMinutes
    val now = rememberNow()
    var date by rememberSaveable { mutableStateOf(logicalDate(Instant.now(), dayStartMinutes = dayStart)) }
    var previousStart by rememberSaveable { mutableIntStateOf(dayStart) }
    LaunchedEffect(dayStart) {
        if (date == logicalDate(now, dayStartMinutes = previousStart)) date = logicalDate(now, dayStartMinutes = dayStart)
        previousStart = dayStart
    }
    var picker by remember { mutableStateOf(false) }
    var scaleMenu by remember { mutableStateOf(false) }
    val locale = LocalResources.current.configuration.locales[0]
    val zone = ZoneId.systemDefault()
    // Whether this calendar also draws plans is its own setting; the records always belong here.
    val display = LocalDisplayPreferences.current
    val showPlans = display.recordsCalendarPlans
    val range = when (scale) {
        0 -> date..date
        1 -> weekStartOf(date, LocalDisplayPreferences.current.weekStart).let { it..it.plusDays(6) }
        else -> YearMonth.from(date).let { it.atDay(1)..it.atEndOfMonth() }
    }
    // The running timer is a record that is not stored yet, so it is added to the calendar's copy of
    // the records rather than to the state everything else reads.
    val liveRecords = listOfNotNull(state.timer?.liveRecord(now))
    val entries = remember(state.plans, state.records, state.timer, range, now, dayStart, showPlans) {
        calendarEntries(if (showPlans) state.plans else emptyList(), state.records + liveRecords,
            range.start, range.endInclusive, now, zone, dayStart, includePastPlans = false)
    }
    val dayState = remember(state, showPlans) {
        // The live record is deliberately not added to the records here: the day view draws it from the
        // timer itself, and listing it in both places would show the same entry in two lanes.
        state.copy(plans = if (showPlans) state.plans else emptyList())
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.md), verticalAlignment = Alignment.CenterVertically) {
        // The title is its own rounded touch target, so a press reads as a control rather than a band
        // of shadow the width of the row. There are no arrows beside it: every scale turns under a
        // horizontal swipe, and a press opens the date picker for anywhere the swipe cannot reach.
        Row(Modifier.weight(1f).height(Metrics.touchTarget).padding(horizontal = Space.xs).clip(MaterialTheme.shapes.small).clickable { picker = true }, verticalAlignment = Alignment.CenterVertically) {
            Text(if (scale == 0) dateText(date) else date.format(DateTimeFormatter.ofPattern(if (locale.language == "zh") "yyyy 年 M 月" else "MMMM yyyy", locale)),
                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = { date = logicalDate(now, dayStartMinutes = dayStart) }) { Text(stringResource(R.string.today)) }
        // The scale is a menu at the end of the row rather than a bar of its own.
        Box {
            Row(Modifier.height(Metrics.touchTarget).clip(MaterialTheme.shapes.small).clickable { scaleMenu = true }.testTag("calendar_scale")
                .padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Text(stringResource(when (scale) { 0 -> R.string.day; 1 -> R.string.week; else -> R.string.month }), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                Icon(AppIcons.Down, null, Modifier.size(Metrics.iconSmall), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DropdownMenu(expanded = scaleMenu, onDismissRequest = { scaleMenu = false }, shape = MaterialTheme.shapes.small,
                containerColor = MaterialTheme.colorScheme.surfaceContainer, shadowElevation = Metrics.menuElevation) {
                val check: @Composable () -> Unit = { Icon(AppIcons.Check, null, Modifier.size(Metrics.icon)) }
                listOf(R.string.day, R.string.week, R.string.month).forEachIndexed { index, label ->
                    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { scale = index; scaleMenu = false },
                        trailingIcon = if (scale == index) check else null, modifier = Modifier.testTag("calendar_scale_$index"))
                }
            }
        }
    }
    when (scale) {
        0 -> Column(Modifier.fillMaxSize().testTag("calendar_day")) {
            // The day keeps Today's week strip, so the two views read as the same one.
            WeekStrip(date, LocalDisplayPreferences.current.weekStart, { date = it }, { date = date.minusDays(1) }, { date = date.plusDays(1) })
            Box(Modifier.weight(1f).padding(top = Space.xs).sheetSurface()) {
                TodayScreen({}, dayState, onPlan = { id, _ -> if (id != 0L) onPlan(id) else add(date) }, onRecord = { id, _ -> if (id != 0L) onRecord(id) else add(date) },
                    onCreatePlan = createPlan, onCreateRecord = createRecord, calendarCutoff = now, selectedDate = date, showHeader = false, showPlans = showPlans, showRecords = true, singleColumn = true)
            }
        }
        1 -> Column(Modifier.fillMaxSize().testTag("calendar_week")) {
            // The weekday names are a fixed header. Below them a swipe anywhere turns the week, and
            // the dates and the blocks slide with it while the hour gutter on the left stays put, so
            // the numbers a swipe is read against never move.
            val weekSwipe = rememberSwipeController()
            Column(Modifier.fillMaxSize().swipeGestures(weekSwipe, { date = date.minusWeeks(1) }, { date = date.plusWeeks(1) })) {
                // The weekday names and the dates stay on the grouped page, and the sheet starts under
                // them with the all-day row, so the color changes below the dates rather than above.
                WeekGridHeader(date)
                WeekDateRow(date, weekSwipe) { date = it; scale = 0 }
                Column(Modifier.weight(1f).padding(top = Space.xs).sheetSurface()) {
                    WeekAllDayRow(date, entries, state, weekSwipe) { if (it.isPlan) onPlan(it.id) else onRecord(it.id) }
                    Column(Modifier.weight(1f).topFade().verticalScroll(rememberScrollState()).padding(top = Space.sm).padding(bottom = Metrics.dockClearance)) {
                        WeekGridBody(date, entries, state, weekSwipe) { if (it.isPlan) onPlan(it.id) else onRecord(it.id) }
                    }
                }
            }
        }
        else -> {
            val density = LocalDensity.current
            val month = YearMonth.from(date)
            val leading = (month.atDay(1).dayOfWeek.value - LocalDisplayPreferences.current.weekStart + 7) % 7
            val rows = (leading + month.lengthOfMonth() + 6) / 7
            val fullHeight = Metrics.tabHeight + Metrics.monthCell * rows
            val collapsedHeight = Metrics.tabHeight + Metrics.monthCell
            var fraction by remember { mutableFloatStateOf(0f) }
            val scope = rememberCoroutineScope()
            var settle by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
            fun settleFraction() {
                settle?.cancel()
                settle = scope.launch {
                    androidx.compose.animation.core.animate(fraction, if (fraction > .5f) 1f else 0f, animationSpec = androidx.compose.animation.core.tween(180)) { value, _ -> fraction = value }
                }
            }
            val maxCollapsePx = with(density) { (fullHeight - collapsedHeight).toPx() }
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            val connection = remember(maxCollapsePx) {
                object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                    override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                        val dy = available.y
                        if (dy < 0f && fraction < 1f) {
                            settle?.cancel()
                            val consumed = maxOf(dy, -(1f - fraction) * maxCollapsePx)
                            fraction -= consumed / maxCollapsePx
                            return androidx.compose.ui.geometry.Offset(0f, consumed)
                        }
                        val atTop = listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
                        if (dy > 0f && fraction > 0f && atTop) {
                            settle?.cancel()
                            val consumed = minOf(dy, fraction * maxCollapsePx)
                            fraction -= consumed / maxCollapsePx
                            return androidx.compose.ui.geometry.Offset(0f, consumed)
                        }
                        return androidx.compose.ui.geometry.Offset.Zero
                    }
                    override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity, available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                        settleFraction()
                        return androidx.compose.ui.unit.Velocity.Zero
                    }
                }
            }
            // The month grid is the backdrop here and the day's entries are the cards on it, so this
            // scale alone does not open a sheet.
            Column(Modifier.fillMaxSize().padding(top = Space.xs)) {
                val monthSwipe = rememberSwipeController()
                Column(Modifier.weight(1f).swipeGestures(monthSwipe, { date = date.minusMonths(1) }, { date = date.plusMonths(1) })) {
                Box(Modifier.fillMaxWidth().height(fullHeight + (collapsedHeight - fullHeight) * fraction).clipToBounds()
                    .pointerInput(maxCollapsePx) {
                        detectVerticalDragGestures(
                            onDragStart = { settle?.cancel() },
                            onDragEnd = { settleFraction() }
                        ) { change, amount ->
                            change.consume()
                            fraction = (fraction - amount / maxCollapsePx).coerceIn(0f, 1f)
                        }
                    }
                    ) { MonthGrid(date, entries, { date = it }, modifier = Modifier.padding(horizontal = Space.md), collapse = fraction, rowsModifier = Modifier.swipeTranslation(monthSwipe)) }
            LazyColumn(Modifier.weight(1f).swipeTranslation(monthSwipe).topFade().nestedScroll(connection).testTag("calendar_month"), state = listState, contentPadding = PaddingValues(start = Space.md, end = Space.md, top = Space.sm, bottom = Metrics.dockClearance), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                val dayEntries = entries.filter { it.onDate(date, zone, dayStart) }.sortedBy { it.span?.start }
                items(dayEntries, key = { it.key }) { entry -> AgendaCard(entry, state, showKind = true, details = display.monthDetails, showRange = true) { if (entry.isPlan) onPlan(entry.id) else onRecord(entry.id) } }
                if (dayEntries.isEmpty()) item { ListEmpty(R.string.agenda_empty) }
            }
                }
        }
    }
    }
    if (picker) CalendarDialog(stringResource(R.string.date), date, { picker = false }) { date = it; picker = false }
}

@Composable private fun MonthGrid(selected: LocalDate, entries: List<BrowseEntry>, select: (LocalDate) -> Unit, modifier: Modifier = Modifier, collapse: Float = 0f, rowsModifier: Modifier = Modifier) {
    val locale = LocalResources.current.configuration.locales[0]
    val zone = ZoneId.systemDefault()
    val dayStart = LocalDisplayPreferences.current.dayStartMinutes
    val weekStart = LocalDisplayPreferences.current.weekStart
    val month = YearMonth.from(selected)
    val first = month.atDay(1)
    val gridStart = weekStartOf(first, weekStart)
    val weeks = ((first.dayOfWeek.value - weekStart + 7) % 7 + month.lengthOfMonth() + 6) / 7
    val days = (0 until weeks * 7).map { gridStart.plusDays(it.toLong()) }
    val rows = days.chunked(7)
    // Collapsing scrolls the selected day's week under the weekday header instead of showing week one.
    val selectedRow = ((selected.toEpochDay() - gridStart.toEpochDay()) / 7).toInt().coerceIn(0, rows.lastIndex)
    val rowPx = with(LocalDensity.current) { Metrics.monthCell.toPx() }
    val scroll = rememberScrollState()
    LaunchedEffect(collapse, selectedRow, rowPx) { scroll.scrollTo((selectedRow * rowPx * collapse).toInt()) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) { repeat(7) { index -> Text(gridStart.plusDays(index.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, locale), Modifier.weight(1f).padding(vertical = Space.sm), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium) } }
        Column(Modifier.weight(1f).then(rowsModifier).verticalScroll(scroll, enabled = false)) {
        rows.forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    val inMonth = day.month == month.month
                    val active = day == selected
                    val muted = !inMonth
                    Box(Modifier.weight(1f).height(Metrics.monthCell), contentAlignment = Alignment.Center) {
                        Column(Modifier.size(Metrics.monthDay).clip(MaterialTheme.shapes.small)
                            .background(if (active) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .then(if (inMonth) Modifier.testTag("month_day_${day.dayOfMonth}").clickable { select(day) } else Modifier),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(day.dayOfMonth.toString(), color = when {
                                muted -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f)
                                active -> MaterialTheme.colorScheme.onPrimaryContainer
                                day == logicalDate(Instant.now(), zone, dayStart) -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurface
                            })
                            if (inMonth) {
                                val onDay = entries.filter { it.onDate(day, zone, dayStart) }
                                Row(Modifier.height(Metrics.colorDot), horizontalArrangement = Arrangement.spacedBy(Space.xxs), verticalAlignment = Alignment.CenterVertically) {
                                    if (onDay.any { it.isPlan }) Box(Modifier.size(Metrics.dotSmall).background(MaterialTheme.colorScheme.primary, CircleShape))
                                    if (onDay.any { !it.isPlan }) Box(Modifier.size(Metrics.dotSmall).background(MaterialTheme.colorScheme.tertiary, CircleShape))
                                }
                            } else Spacer(Modifier.height(Metrics.colorDot))
                        }
                    }
                }
            }
        }
        }
    }
}

/**
 * The weekday names down the top of the week. They never move: whatever the dates below say, the
 * columns are always the same seven weekdays, so this is the one line a swipe is read against.
 */
@Composable private fun WeekGridHeader(date: LocalDate) {
    val locale = LocalResources.current.configuration.locales[0]
    val monday = weekStartOf(date, LocalDisplayPreferences.current.weekStart)
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.md).padding(top = Space.xxs)) {
        Spacer(Modifier.width(Metrics.weekGutter))
        repeat(7) { index ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(monday.plusDays(index.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The seven dates under the weekday names. They slide, because they are what a swipe changes. */
@Composable private fun WeekDateRow(date: LocalDate, swipe: SwipeController, select: (LocalDate) -> Unit) {
    val zone = ZoneId.systemDefault()
    val display = LocalDisplayPreferences.current
    val today = logicalDate(Instant.now(), zone, display.dayStartMinutes)
    val monday = weekStartOf(date, display.weekStart)
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.md)) {
        Spacer(Modifier.width(Metrics.weekGutter))
        Row(Modifier.weight(1f).swipeTranslation(swipe)) {
            repeat(7) { index ->
                val day = monday.plusDays(index.toLong())
                // Deliberately the same shape as Today's week strip: the selected day filled, today
                // tinted, so both week views read as one control.
                Box(Modifier.weight(1f).clickable { select(day) }.padding(vertical = Space.xxs), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(Metrics.calendarDay).clip(CircleShape)
                        .background(if (day == date) MaterialTheme.colorScheme.primary else if (day == today) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent), contentAlignment = Alignment.Center) {
                        Text(day.dayOfMonth.toString(), color = if (day == date) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

/** Pinned all-day row above the week grid: one compact chip per all-day item. The chips slide. */
@Composable private fun WeekAllDayRow(date: LocalDate, entries: List<BrowseEntry>, state: WorkspaceState, swipe: SwipeController, open: (BrowseEntry) -> Unit) {
    val monday = weekStartOf(date, LocalDisplayPreferences.current.weekStart)
    val zone = ZoneId.systemDefault()
    val dayStart = LocalDisplayPreferences.current.dayStartMinutes
    // Chips match the shortest block, and shrink their label with it when the grid is dense.
    val minEventHeight = minEventHeight(Metrics.hourHeight)
    val chipStyle = if (minEventHeight < Metrics.compactLabelThreshold) MaterialTheme.typography.labelSmall.copy(fontSize = Metrics.compactLabelSize, lineHeight = Metrics.compactLabelLineHeight)
    else MaterialTheme.typography.labelSmall
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = Space.md)) {
        val dayWidth = (maxWidth - Metrics.weekGutter) / 7
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(Metrics.weekGutter))
            Row(Modifier.weight(1f).swipeTranslation(swipe)) {
                repeat(7) { index ->
                    val day = monday.plusDays(index.toLong())
                    Column(Modifier.weight(1f).padding(horizontal = Metrics.eventGap), verticalArrangement = Arrangement.spacedBy(Metrics.eventGap)) {
                        entries.filter { it.allDay && it.onDate(day, zone, dayStart) }.forEach { entry ->
                            val category = state.categories.firstOrNull { it.id == entry.categoryId }
                            Box(Modifier.fillMaxWidth().height(minEventHeight).padding(vertical = Metrics.eventGap).clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.secondaryContainer).clickable { open(entry) }, contentAlignment = Alignment.CenterStart) {
                                Text(itemName(entry.title, category), Modifier.padding(horizontal = Space.xxs),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = chipStyle)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun WeekGridBody(date: LocalDate, entries: List<BrowseEntry>, state: WorkspaceState, swipe: SwipeController, open: (BrowseEntry) -> Unit) {
    val monday = weekStartOf(date, LocalDisplayPreferences.current.weekStart)
    val zone = ZoneId.systemDefault()
    val dayStart = LocalDisplayPreferences.current.dayStartMinutes
    val hourHeight = Metrics.hourHeight
    val minEventHeight = minEventHeight(hourHeight)

    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = Space.md)) {
    // The hour lines span the whole grid, gutter included, exactly as the day view draws them, so the
    // times sit on a line in both views rather than beside one.
    Box(Modifier.fillMaxWidth().height(hourHeight * 24)) {
        repeat(24) { if (it > 0) HorizontalDivider(Modifier.offset(y = hourHeight * it), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = Metrics.gridAlpha)) }
    }
    Row(Modifier.fillMaxWidth()) {
        // Sized for the marked form of the label ("04 ⁺¹"), so the marker stays inside the gutter.
        Column(Modifier.width(Metrics.weekGutter)) {
            repeat(24) { Text(axisHourText(LocalTime.ofSecondOfDay(dayStart * 60L).plusHours(it.toLong()), dayStart), Modifier.height(hourHeight).padding(top = Space.xxs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1) }
        }
        // Only the day columns slide; the hour gutter on the left stays put.
        BoxWithConstraints(Modifier.weight(1f).height(hourHeight * 24).swipeTranslation(swipe)) {
        val dayWidth = maxWidth / 7
        Row(Modifier.fillMaxWidth()) {
        repeat(7) { index ->
            val day = monday.plusDays(index.toLong())
            val span = daySpan(day, zone, dayStart)
            Box(Modifier.width(dayWidth).height(hourHeight * 24)) {
                val onDay = entries.filter { it.onDate(day, zone, dayStart) && !it.allDay }
                val keyed = onDay.withIndex().associate { it.index.toLong() to it.value }
                // Blocks keep their minimum while nothing follows, squeeze into the room beside a
                // neighbour, and only take a lane of their own when not even the floor fits.
                val minMillis = Metrics.eventMinMinutes * 60_000L
                val floorMillis = Metrics.eventFloorMinutes * 60_000L
                val spans = keyed.mapNotNull { (id, entry) -> entry.span?.clippedTo(span)?.let { id to it } }
                arrangeTimeline(spans, minMillis, floorMillis).forEach { placed ->
                    val entry = keyed.getValue(placed.id)
                    val category = state.categories.firstOrNull { it.id == entry.categoryId }
                    // One fallback for both kinds: the block shape already says plan or record, so the tint does
                    // not have to, and an uncategorised block looks the same on every page.
                    val tint = category?.color?.let { Color(it) } ?: MaterialTheme.colorScheme.tertiary
                    val y = hourHeight * (Duration.between(span.start, placed.span.start).toMinutes() / 60f)
                    val drawn = hourHeight * (placed.drawn.millis / 3_600_000f)
                    // Same order of surrender as the day lane: inset, then icon, then type.
                    val inset = blockInset(drawn, minEventHeight, Metrics.compactLabelThreshold + Space.xxs * 2)
                    val full = drawn >= minEventHeight
                    val baseLabel = drawn >= Metrics.compactLabelThreshold + inset * 2
                    val anyLabel = drawn >= Metrics.compactLabelHeight + inset * 2
                    Box(Modifier.offset(x = dayWidth / placed.lanes * placed.lane, y = y).width(dayWidth / placed.lanes)
                        .height(drawn).padding(Metrics.eventGap)
                        .eventBlock(entry.isPlan, tint, live = entry.live).testTag("event_block").clickable { open(entry) }.padding(horizontal = Space.xxs, vertical = inset)) {
                        if (anyLabel) Text(itemName(entry.title, category), style = if (baseLabel) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelSmall.copy(fontSize = Metrics.compactLabelSize, lineHeight = Metrics.compactLabelLineHeight),
                            maxLines = if (full) 2 else 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                onDay.filter { it.span == null }.forEachIndexed { i, entry ->
                    val category = state.categories.firstOrNull { it.id == entry.categoryId }
                    Text(itemName(entry.title, category), Modifier.offset(y = minEventHeight * i).fillMaxWidth().clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.secondaryContainer).clickable { open(entry) }.padding(horizontal = Space.xxs), maxLines = 1, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
        }
        }
}
}
