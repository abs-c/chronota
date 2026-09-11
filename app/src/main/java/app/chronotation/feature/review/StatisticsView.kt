package app.chronotation.feature.review

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import app.chronotation.R
import app.chronotation.data.entity.*
import app.chronotation.domain.*
import app.chronotation.feature.WorkspaceState
import app.chronotation.feature.browse.ModeTabs
import app.chronotation.feature.todo.ExpiredGoals
import app.chronotation.feature.todo.GoalRecordsCard
import app.chronotation.ui.components.*
import app.chronotation.ui.theme.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val OVERVIEW_PAGES = 2001

@Composable internal fun StatisticsView(state: WorkspaceState, onRecord: (Long) -> Unit, onDeleteGoal: (Long) -> Unit = {}) {
    val zone = ZoneId.systemDefault()
    val dayStart = LocalDisplayPreferences.current.dayStartMinutes
    val now = rememberNow()
    val today = logicalDate(now, zone, dayStart)
    var detail by rememberSaveable { mutableStateOf<Long?>(null) }
    var overview by rememberSaveable { mutableStateOf(false) }
    var expired by rememberSaveable { mutableStateOf(false) }
    var expiredCategory by rememberSaveable { mutableStateOf<Long?>(null) }
    val open = detail?.let { id -> state.categories.firstOrNull { it.id == id } }
    // These pages sit above the statistics list without being navigation destinations, so the system
    // back gesture has to step out of them the same way their own back button does.
    BackHandler(expired || open != null || overview) {
        when {
            expired -> expired = false
            open != null -> detail = null
            else -> overview = false
        }
    }
    when {
        expired -> ExpiredGoals(state, expiredCategory, onBack = { expired = false }, onDeleteGoal = onDeleteGoal, includeActive = true, allowDelete = false)
        open != null -> CategoryDetail(state, open, today, zone, dayStart, { detail = null }, onDeleteGoal) { expiredCategory = open.id; expired = true }
        overview -> ShareOverview(state, today, zone, dayStart) { overview = false }
        else -> CategoryCards(state, today, zone, dayStart, open = { detail = it }, openOverview = { overview = true })
    }
}

@Composable private fun CategoryCards(state: WorkspaceState, today: LocalDate, zone: ZoneId, dayStart: Int, open: (Long) -> Unit, openOverview: () -> Unit) {
    var parent by rememberSaveable { mutableStateOf<Long?>(null) }
    var sort by rememberSaveable { mutableIntStateOf(1) }
    var sortDialog by remember { mutableStateOf(false) }
    val leaves = remember(state.categories) { state.categories.filter { category -> state.categories.none { it.parentId == category.id } } }
    val visible = when {
        parent == null -> leaves
        leaves.any { it.id == parent } -> leaves.filter { it.id == parent }
        else -> leaves.filter { it.parentId == parent }
    }
    val ordered = remember(visible, sort, state.records) {
        if (sort == 0) visible else visible.sortedByDescending { category -> state.records.filter { it.categoryId == category.id }.maxOfOrNull { it.startTime } ?: Instant.MIN }
    }
    val weekStart = weekStartOf(today, LocalDisplayPreferences.current.weekStart)
    val heatStart = weekStart.minusWeeks(3)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.md), verticalAlignment = Alignment.CenterVertically) {
            val chipScroll = rememberScrollState()
            val chipFade by animateFloatAsState(if (chipScroll.canScrollForward) 1f else 0f, tween(150), label = "chipFade")
            Box(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(chipScroll), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    AppFilterChip(parent == null, { parent = null }, stringResource(R.string.all_items))
                    state.categories.filter { it.parentId == null }.forEach { group ->
                        AppFilterChip(parent == group.id, { parent = group.id }, group.name)
                    }
                    Spacer(Modifier.width(Space.md))
                }
                if (chipFade > 0f) Box(Modifier.matchParentSize().graphicsLayer { alpha = chipFade }) {
                    Box(Modifier.align(Alignment.CenterEnd).width(Space.lg).fillMaxHeight().background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))))
                }
            }
            IconButton(onClick = openOverview, Modifier.testTag("stats_overview")) { Icon(AppIcons.Review, stringResource(R.string.share_overview), Modifier.size(Metrics.icon)) }
            IconButton(onClick = { sortDialog = true }, Modifier.testTag("stats_sort")) { Icon(AppIcons.Sort, stringResource(R.string.sort), Modifier.size(Metrics.icon)) }
        }
        LazyColumn(Modifier.fillMaxSize().topFade().testTag("statistics_list"), contentPadding = PaddingValues(start = Space.md, end = Space.md, top = Space.sm, bottom = Metrics.dockClearance), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            items(ordered, key = { it.id }) { category ->
                val accent = category.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
                val values = remember(state.records, category, heatStart, today) { dailyTotals(state.records, heatStart, today, zone, dayStart) { it.categoryId == category.id } }
                val total = remember(values) { values.values.sum() }
                val count = remember(state.records, category) { state.records.count { it.categoryId == category.id } }
                val week = remember(values, weekStart) { values.filterKeys { !it.isBefore(weekStart) }.values.sum() }
                CategoryCard(category, state.categories.firstOrNull { it.id == category.parentId }, values, week, total, count, today, heatStart, accent) { open(category.id) }
            }
            if (ordered.isEmpty()) item { ListEmpty(R.string.agenda_empty) }
        }
    }
    if (sortDialog) SelectionDialog(stringResource(R.string.sort), { sortDialog = false }) {
        SelectionRow(stringResource(R.string.sort_category_order), sort == 0, { sort = 0; sortDialog = false })
        SelectionRow(stringResource(R.string.sort_recent), sort == 1, { sort = 1; sortDialog = false })
    }
}

@Composable private fun CategoryCard(category: Category, group: Category?, values: Map<LocalDate, Long>, week: Long, total: Long, count: Int,
    today: LocalDate, heatStart: LocalDate, accent: Color, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainer)
        .clickable(onClick = click).padding(horizontal = Space.md, vertical = Space.sm), horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.CenterVertically) {
        Heatmap(values, heatStart, today, accent, Metrics.heatmapCell)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
            Text(category.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (group != null) Text(group.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.this_week) + " " + durationText(week), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.last_weeks) + " " + durationText(total) + " · " + pluralStringResource(R.plurals.record_count_value, count, count), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun Heatmap(values: Map<LocalDate, Long>, start: LocalDate, today: LocalDate, color: Color, cell: Dp, modifier: Modifier = Modifier, weeks: Int = 4) {
    val maximum = (values.values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        repeat(weeks) { week ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Text(start.plusWeeks(week.toLong()).format(DateTimeFormatter.ofPattern("MM/dd")), Modifier.width(Metrics.heatmapLabel), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                repeat(7) { day ->
                    val date = start.plusWeeks(week.toLong()).plusDays(day.toLong())
                    val amount = values[date] ?: 0L
                    val description = date.toString() + " · " + durationText(amount)
                    Box(Modifier.size(cell).clip(MaterialTheme.shapes.small).background(if (amount > 0 && !date.isAfter(today)) color.copy(alpha = .25f + .75f * amount / maximum) else MaterialTheme.colorScheme.surfaceContainerHigh).semantics { contentDescription = description })
                }
            }
        }
    }
}

/** Horizontally scrollable bars for the last days of one category. */
@Composable private fun DailyBarChart(values: Map<LocalDate, Long>, color: Color) {
    if (values.isEmpty()) return
    val maximum = (values.values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val scroll = rememberScrollState(Int.MAX_VALUE)
    Row(Modifier.fillMaxWidth().height(Metrics.dailyChart).horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(Space.xxs), verticalAlignment = Alignment.Bottom) {
        values.entries.sortedBy { it.key }.forEach { (date, amount) ->
            Column(Modifier.width(Metrics.barWidth).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().fillMaxHeight(amount.toFloat() / maximum).clip(MaterialTheme.shapes.small).background(if (amount > 0) color else MaterialTheme.colorScheme.surfaceContainerHigh))
                }
                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable private fun CategoryDetail(state: WorkspaceState, category: Category, today: LocalDate, zone: ZoneId, dayStart: Int, back: () -> Unit, onDeleteGoal: (Long) -> Unit, openExpired: () -> Unit) {
    val accent = category.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val group = state.categories.firstOrNull { it.id == category.parentId }
    val records = remember(state.records, category) { state.records.filter { it.categoryId == category.id } }
    val weekStart = weekStartOf(today, LocalDisplayPreferences.current.weekStart)
    val monthStart = today.withDayOfMonth(1)
    val weekSpan = TimeSpan(daySpan(weekStart, zone, dayStart).start, daySpan(weekStart.plusWeeks(1), zone, dayStart).start)
    val monthSpan = TimeSpan(daySpan(monthStart, zone, dayStart).start, daySpan(monthStart.plusMonths(1), zone, dayStart).start)
    val week = remember(records, weekSpan) { recordedTime(records, weekSpan) }
    val month = remember(records, monthSpan) { recordedTime(records, monthSpan) }
    val weekCount = remember(records, weekSpan) { records.count { it.overlaps(weekSpan) } }
    val monthCount = remember(records, monthSpan) { records.count { it.overlaps(monthSpan) } }
    val todaySpan = daySpan(today, zone, dayStart)
    val todayTotal = remember(records, todaySpan) { recordedTime(records, todaySpan) }
    val todayCount = remember(records, todaySpan) { records.count { it.overlaps(todaySpan) } }
    val total = remember(records) { records.sumOf { it.endTime.toEpochMilli() - it.startTime.toEpochMilli() } }
    // Time zero shows only the record count; otherwise the duration comes first.
    val summaries = listOf(
        Summary(R.string.today, todayTotal, countText(todayCount)),
        Summary(R.string.this_week, week, countText(weekCount)),
        Summary(R.string.this_month, month, countText(monthCount)),
        Summary(R.string.stats_total, total, countText(records.size)),
    )
    val heatStart = weekStart.minusWeeks(3)
    val values = remember(records, heatStart, today) { dailyTotals(records, heatStart, today, zone, dayStart) }
    val chartStart = today.minusDays(29)
    val chartValues = remember(records, chartStart, today) { dailyTotals(records, chartStart, today, zone, dayStart) }
    val weekTotals = (0 until 4).map { index ->
        val start = heatStart.plusWeeks(index.toLong())
        val span = TimeSpan(daySpan(start, zone, dayStart).start, daySpan(start.plusWeeks(1), zone, dayStart).start)
        Triple(start, (0..6).sumOf { values[start.plusDays(it.toLong())] ?: 0L }, records.count { it.overlaps(span) })
    }
    val stats = remember(state.definitions, state.values, records, category) {
        attributeStats(state.definitions.filter { it.categoryId == category.id }, state.values, records.map { it.id }.toSet())
    }
    Column(Modifier.fillMaxSize().testTag("category_detail")) {
        Row(Modifier.fillMaxWidth().heightIn(min = Metrics.pageHeader).padding(start = Space.xs, end = Space.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xxs)) {
            IconButton(onClick = back, Modifier.testTag("category_back")) { Icon(AppIcons.Back, stringResource(R.string.back), Modifier.size(Metrics.icon)) }
            CategoryMark(category)
            Spacer(Modifier.width(Space.xs))
            Column(Modifier.weight(1f)) {
                Text(category.name, style = MaterialTheme.typography.titleLarge)
                if (group != null) Text(group.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(Modifier.weight(1f).topFade().verticalScroll(rememberScrollState()).padding(top = Space.sm).padding(horizontal = Space.md).padding(bottom = Metrics.dockClearance), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                summaries.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        row.forEach { summary -> SummaryTile(stringResource(summary.label), summaryValue(summary), Modifier.weight(1f)) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = Space.md, vertical = Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                DailyBarChart(chartValues, accent)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md), verticalAlignment = Alignment.Top) {
                    Heatmap(values, heatStart, today, accent, Metrics.heatmapCellLarge)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                        weekTotals.forEach { (_, total, count) ->
                            val label = when {
                                count == 0 -> pluralStringResource(R.plurals.record_count_value, 0, 0)
                                total == 0L -> pluralStringResource(R.plurals.record_count_value, count, count)
                                else -> durationText(total) + " · " + pluralStringResource(R.plurals.record_count_value, count, count)
                            }
                            Row(Modifier.fillMaxWidth().height(Metrics.heatmapCellLarge), verticalAlignment = Alignment.CenterVertically) {
                                Text(label, Modifier.fillMaxWidth(), textAlign = TextAlign.End, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            GoalRecordsCard(state, category.id, openExpired)
            val visibleStats = stats.filter { it.filled > 0 }
            if (visibleStats.isNotEmpty()) {
                Text(stringResource(R.string.attribute_stats), style = MaterialTheme.typography.titleMedium)
                visibleStats.forEach { stat -> AttributeStatCard(stat, accent) }
            }
        }
    }
}

private data class Summary(val label: Int, val millis: Long, val countText: String)

@Composable private fun countText(count: Int): String = pluralStringResource(R.plurals.record_count_value, count, count)

/** Zero-minute totals show only the record count; otherwise "duration · count". */
@Composable private fun summaryValue(summary: Summary): String =
    if (summary.millis / 60_000 == 0L) summary.countText else durationText(summary.millis) + " · " + summary.countText

private fun Record.overlaps(span: TimeSpan): Boolean = startTime < span.end && (endTime > span.start || startTime >= span.start)

@Composable private fun SummaryTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainer).padding(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable private fun AttributeStatCard(stat: AttributeStat, accent: Color) {
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = Space.md, vertical = Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stat.definition.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(pluralStringResource(R.plurals.attribute_filled, stat.filled, stat.filled), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            stat.counts.isNotEmpty() -> {
                val maximum = stat.counts.maxOf { it.second }.coerceAtLeast(1)
                val options = stat.definition.options.lines()
                stat.counts.forEach { (option, count) ->
                    val color = if (stat.definition.type == PropertyType.SELECT || stat.definition.type == PropertyType.MULTISELECT) stat.definition.optionColor(options.indexOf(option)) ?: accent else accent
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        val label = when (option) { "true" -> stringResource(R.string.boolean_yes); "false" -> stringResource(R.string.boolean_no); else -> option }
                        Text(label, Modifier.width(Metrics.optionValueWidth), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Box(Modifier.weight(1f).height(Metrics.shareBar).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                            Box(Modifier.fillMaxWidth(count.toFloat() / maximum).fillMaxHeight().clip(CircleShape).background(color))
                        }
                        Text(count.toString(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.width(Metrics.percentColumn))
                    }
                }
            }
            else -> {
                if (stat.bins.isNotEmpty()) {
                    val maximum = stat.bins.maxOf { it.second }.coerceAtLeast(1)
                    Row(Modifier.fillMaxWidth().height(Metrics.binChart), horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.Bottom) {
                        stat.bins.forEach { (label, count) ->
                            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.BottomCenter) {
                                    Box(Modifier.fillMaxWidth().fillMaxHeight(count.toFloat() / maximum).clip(MaterialTheme.shapes.small).background(accent))
                                }
                                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                if (stat.average != null) Text(
                    stringResource(R.string.attribute_average, numberText(stat.average) + stat.definition.unit.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()) + (stat.total?.let { " · " + stringResource(R.string.attribute_total, numberText(it) + stat.definition.unit.takeIf { it.isNotBlank() }?.let { u -> " $u" }.orEmpty()) } ?: ""),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else if (stat.bins.isEmpty()) Text(pluralStringResource(R.plurals.text_stat_line, stat.filled, stat.filled, stat.chars), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun numberText(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.getDefault(), "%.1f", value)

@Composable private fun ShareOverview(state: WorkspaceState, today: LocalDate, zone: ZoneId, dayStart: Int, back: () -> Unit) {
    var period by rememberSaveable { mutableStateOf(ReviewPeriod.WEEK) }
    var base by rememberSaveable { mutableStateOf(today) }
    var from by rememberSaveable { mutableStateOf(today.minusDays(6)) }
    var through by rememberSaveable { mutableStateOf(today) }
    var picker by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = OVERVIEW_PAGES / 2) { OVERVIEW_PAGES }
    val scope = rememberCoroutineScope()
    val anchor = remember(period, pagerState.currentPage, base) { shiftAnchor(period, base, (pagerState.currentPage - OVERVIEW_PAGES / 2).toLong()) }
    val weekStart = LocalDisplayPreferences.current.weekStart
    val range = remember(period, anchor, from, through, zone, dayStart, weekStart) { runCatching { reviewRange(period, anchor, from, through, zone, dayStart, weekStart) }.getOrNull() }
    val report = remember(state.records, state.plans, state.categories, range, dayStart) { range?.let { reviewReport(state.records, state.plans, state.categories, it, zone, dayStart) } }
    val colors = state.categories.associate { it.id as Long? to (it.color?.let { value -> Color(value) } ?: MaterialTheme.colorScheme.primary) } + (null as Long? to MaterialTheme.colorScheme.primary)
    LazyColumn(Modifier.fillMaxSize().testTag("stats_overview"), contentPadding = PaddingValues(start = Space.md, end = Space.md, top = Space.sm, bottom = Metrics.dockClearance), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        item {
            Row(Modifier.fillMaxWidth().heightIn(min = Metrics.pageHeader).padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = back, Modifier.testTag("overview_back")) { Icon(AppIcons.Back, stringResource(R.string.back), Modifier.size(Metrics.icon)) }
                Text(stringResource(R.string.share_overview), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            }
            ModeTabs(listOf(R.string.day, R.string.week, R.string.month, R.string.custom_range), period.ordinal, { selected ->
                period = ReviewPeriod.entries[selected]
                base = anchor
                scope.launch { pagerState.scrollToPage(OVERVIEW_PAGES / 2) }
            }, "stats_period")
            if (period == ReviewPeriod.CUSTOM) {
                OptionGroup {
                    DateField(R.string.review_range_start, from, { from = it!! }, optional = false)
                    AppDivider()
                    DateField(R.string.review_range_end, through, { through = it!! }, optional = false)
                }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } }) { Icon(AppIcons.Previous, stringResource(R.string.previous_period), Modifier.size(Metrics.icon)) }
                    Text(rangeText(period, anchor), Modifier.weight(1f).height(Metrics.controlHeight).clip(MaterialTheme.shapes.small).clickable { picker = true }.wrapContentHeight(Alignment.CenterVertically), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } }) { Icon(AppIcons.Next, stringResource(R.string.next_period), Modifier.size(Metrics.icon)) }
                }
                HorizontalPager(pagerState, Modifier.fillMaxWidth().height(Metrics.chartStacked + Space.lg)) { page ->
                    val pageAnchor = shiftAnchor(period, base, (page - OVERVIEW_PAGES / 2).toLong())
                    val pageRange = remember(period, pageAnchor, from, through, zone, dayStart, weekStart) { runCatching { reviewRange(period, pageAnchor, from, through, zone, dayStart, weekStart) }.getOrNull() }
                    if (pageRange != null) {
                        val hourly = period == ReviewPeriod.DAY
                        val buckets = remember(state.records, pageRange, hourly, dayStart) { stackedTotals(state.records, pageRange, if (hourly) ChartBucket.HOUR else ChartBucket.DAY, zone, dayStart) }
                        StackedBarChart(buckets, colors, hourly, zone)
                    }
                }
            }
        }
        if (report == null) item { Text(stringResource(R.string.error_range), color = MaterialTheme.colorScheme.error) }
        else {
            item {
                if (report.total == 0L) ListEmpty(R.string.no_records)
                else {
                    DistributionRing(report, colors)
                    Text(stringResource(R.string.category_totals), Modifier.padding(top = Space.lg, bottom = Space.xs), style = MaterialTheme.typography.titleMedium)
                    report.categories.entries.sortedByDescending { it.value }.forEach { (id, duration) ->
                        val category = state.categories.firstOrNull { it.id == id }
                        val group = state.categories.firstOrNull { it.id == category?.parentId }
                        val share = (duration.toFloat() / report.total).coerceIn(0f, 1f)
                        Column(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                                Box(Modifier.size(Metrics.colorDot).background(colors.getValue(id), CircleShape))
                                Column(Modifier.weight(1f)) {
                                    Text(category?.name ?: stringResource(R.string.uncategorized), style = MaterialTheme.typography.bodyLarge)
                                    if (group != null) Text(group.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(durationText(duration), style = MaterialTheme.typography.bodyMedium)
                                Text("${(share * 100).roundToInt()}%", Modifier.width(Metrics.percentColumn), textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                            }
                            Box(Modifier.fillMaxWidth().height(Metrics.shareBar).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                Box(Modifier.fillMaxWidth(share).fillMaxHeight().clip(CircleShape).background(colors.getValue(id)))
                            }
                        }
                    }
                }
            }
        }
    }
    if (picker) CalendarDialog(stringResource(R.string.date), anchor, { picker = false }) { base = it; picker = false; scope.launch { pagerState.scrollToPage(OVERVIEW_PAGES / 2) } }
}

/** One bar per hour or day, stacked by category share. */
@Composable private fun StackedBarChart(buckets: List<Pair<Instant, Map<Long?, Long>>>, colors: Map<Long?, Color>, hourly: Boolean, zone: ZoneId) {
    if (buckets.isEmpty()) return
    val maximum = buckets.maxOfOrNull { it.second.values.sum() }?.coerceAtLeast(1) ?: 1
    val labelStep = maxOf(1, ceil(buckets.size / 7f).toInt())
    Column(Modifier.fillMaxWidth().height(Metrics.chartStacked), verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(Metrics.barGap), verticalAlignment = Alignment.Bottom) {
            buckets.forEach { (_, byCategory) ->
                val total = byCategory.values.sum()
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (total > 0) Column(Modifier.widthIn(max = Metrics.barWidth).fillMaxWidth().fillMaxHeight(total.toFloat() / maximum)
                        .clip(RoundedCornerShape(Metrics.barRadius))) {
                        byCategory.entries.sortedByDescending { it.value }.forEach { (id, value) ->
                            Box(Modifier.fillMaxWidth().weight(value.toFloat() / total).background(colors[id] ?: MaterialTheme.colorScheme.primary))
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Metrics.barGap)) {
            buckets.forEachIndexed { index, (start, _) ->
                val local = start.atZone(zone)
                Text(if (index % labelStep == 0) (if (hourly) local.hour.toString() else local.dayOfMonth.toString()) else "", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable private fun DistributionRing(report: ReviewReport, colors: Map<Long?, Color>) {
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val description = stringResource(R.string.chart_share) + " · " + durationText(report.total)
    Box(Modifier.fillMaxWidth().height(Metrics.chartRingFrame).semantics { contentDescription = description }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(Metrics.chartRing).padding(Space.sm)) {
            val stroke = Metrics.chartRingStroke.toPx()
            val inset = stroke / 2
            val circle = Size(size.width - stroke, size.height - stroke)
            val capDegrees = if (circle.width > 0f) (stroke / 2f) / (circle.width / 2f) * (180f / Math.PI.toFloat()) else 0f
            if (report.total == 0L) drawArc(track, -90f, 360f, false, Offset(inset, inset), circle, style = Stroke(stroke))
            var start = -90f
            report.categories.entries.sortedByDescending { it.value }.forEach { (id, value) ->
                val sweep = if (report.total == 0L) 0f else value * 360f / report.total
                val gap = if (sweep >= 359.5f) 0f else minOf(sweep * .5f, 2f * capDegrees + Metrics.chartGapDegrees)
                drawArc(colors.getValue(id), start + gap / 2f, (sweep - gap).coerceAtLeast(0f), false, Offset(inset, inset), circle, style = Stroke(stroke, cap = StrokeCap.Round))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.xxs)) {
            Text(durationText(report.total), style = MaterialTheme.typography.titleMedium)
            Text(pluralStringResource(R.plurals.record_count_value, report.records.size, report.records.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun shiftAnchor(period: ReviewPeriod, anchor: LocalDate, step: Long): LocalDate = when (period) {
    ReviewPeriod.DAY -> anchor.plusDays(step)
    ReviewPeriod.WEEK -> anchor.plusWeeks(step)
    else -> anchor.plusMonths(step)
}

@Composable private fun rangeText(period: ReviewPeriod, anchor: LocalDate): String {
    val locale = androidx.compose.ui.platform.LocalResources.current.configuration.locales[0]
    return when (period) {
        ReviewPeriod.WEEK -> {
            val start = weekStartOf(anchor, LocalDisplayPreferences.current.weekStart)
            dateText(start) + " – " + dateText(start.plusDays(6))
        }
        ReviewPeriod.MONTH -> YearMonth.from(anchor).format(DateTimeFormatter.ofPattern(if (locale.language == "zh") "yyyy 年 M 月" else "MMMM yyyy", locale))
        else -> dateText(anchor)
    }
}
