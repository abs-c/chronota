package app.chronota.feature.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.chronota.R
import app.chronota.data.entity.*
import app.chronota.domain.*
import app.chronota.feature.WorkspaceState
import app.chronota.ui.components.*
import app.chronota.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private enum class TargetUnit { HOUR, MINUTE, COUNT }

@Composable
fun GoalList(state: WorkspaceState, onGoal: (Long) -> Unit, onAddGoal: () -> Unit, onDeleteGoal: (Long) -> Unit) {
    var ended by rememberSaveable { mutableStateOf(false) }
    // The ended list is a page above this one, so the system back gesture leaves it too.
    BackHandler(ended) { ended = false }
    if (ended) { ExpiredGoals(state, null, { ended = false }, onDeleteGoal); return }
    val now = rememberNow()
    val zone = ZoneId.systemDefault()
    // Expired instances are goals of their own and live in the ended list. Running goals are listed
    // by expiry, so the one that runs out next is always on top.
    val goals = remember(state.goals) { state.goals.filter { it.expiredAt == null }.sortedBy { it.expiry() } }
    val expired = remember(state.goals) { state.goals.any { it.expiredAt != null } }
    LazyColumn(Modifier.fillMaxSize().topFade().testTag("goal_list"), contentPadding = PaddingValues(start = Space.md, end = Space.md, top = Space.sm, bottom = Metrics.dockClearance), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        items(goals, key = { it.id }) { goal ->
            val progress = remember(goal, state.records, state.definitions, state.values, now) {
                goalProgress(goal, state.records, state.definitions, state.values, now, zone)
            }
            GoalCard(goal, state, progress, goal.instanceWindow(now, zone)) { onGoal(goal.id) }
        }
        // Without active goals the ended entry moves to the top; a page with nothing at all points at the orb.
        if (expired) item { OptionRow(stringResource(R.string.expired_goals), { ended = true }) }
        else if (goals.isEmpty()) item { CenterHint(R.string.empty_add_hint) }
    }
}

private data class GoalRecordRow(val name: String, val span: Pair<Instant, Instant>, val current: Long, val target: Long,
    val metric: GoalMetric, val direction: GoalDirection, val ended: Boolean, val startAt: Instant? = null)

/** One card with up to three goals of a category: name/time, then progress with status. */
@Composable fun GoalRecordsCard(state: WorkspaceState, categoryId: Long?, onOpen: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val now = rememberNow()
    val rows = remember(state.goals, state.records, state.definitions, state.values, categoryId, now) {
        val goals = state.goals.filter { it.categoryId == categoryId && it.expiredAt == null }.sortedBy { it.expiry() }.map { goal ->
            val progress = goalProgress(goal, state.records, state.definitions, state.values, now, zone)
            val window = goal.instanceWindow(now, zone)
            GoalRecordRow(goal.name, window.start to window.end, progress.current, goal.target, goal.metric, goal.direction, ended = false, startAt = goal.startAt)
        }
        val expired = state.goals.filter { it.categoryId == categoryId && it.expiredAt != null }
            .sortedByDescending { it.expiredAt }.map { goal ->
            val span = TimeSpan(goal.startAt, goal.expiredAt!!)
            GoalRecordRow(goal.name, span.start to span.end, goalInstanceActual(goal, state.records, state.definitions, state.values, span),
                goal.target, goal.metric, goal.direction, ended = true)
        }
        (goals + expired).take(3)
    }
    if (rows.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.goal_records), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onOpen) { Text(stringResource(R.string.details)) }
        }
    Column(Modifier.fillMaxWidth().cardSurface()
        .clickable(onClick = onOpen).padding(horizontal = Space.md, vertical = Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        rows.forEachIndexed { index, row ->
            if (index > 0) AppDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(row.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dateText(row.span.first.atZone(zone).toLocalDate()) + " – " + dateText(row.span.second.atZone(zone).toLocalDate()),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            val fraction = if (row.target > 0) (row.current.toFloat() / row.target).coerceIn(0f, 1f) else 0f
            val met = row.direction == GoalDirection.AT_LEAST && row.current >= row.target
            val over = row.direction == GoalDirection.AT_MOST && row.current > row.target
            val status = when {
                row.ended -> if (row.direction == GoalDirection.AT_MOST) { if (over) stringResource(R.string.goal_over) else stringResource(R.string.goal_within) } else if (met) stringResource(R.string.goal_done) else stringResource(R.string.goal_missed)
                row.startAt != null && now < row.startAt -> stringResource(R.string.goal_not_started)
                row.direction == GoalDirection.AT_MOST -> if (over) stringResource(R.string.goal_over) else stringResource(R.string.goal_within)
                met -> stringResource(R.string.goal_done)
                else -> stringResource(R.string.goal_in_progress)
            }
            val statusColor = when {
                over -> MaterialTheme.colorScheme.error
                met -> MaterialTheme.colorScheme.primary
                row.ended && row.direction == GoalDirection.AT_LEAST -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(amountText(row.metric, row.current) + " / " + amountText(row.metric, row.target), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                Box(Modifier.weight(1f).height(Metrics.shareBar).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(CircleShape).background(if (over || (row.ended && !met)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary))
                }
                Text(status, style = MaterialTheme.typography.labelMedium, color = statusColor, maxLines = 1)
            }
        }
    }
    }
}

/**
 * Secondary page listing expired goals, optionally together with the running ones.
 *
 * Expired goals are ordinary goals, so each one can be deleted on its own without touching any
 * other instance of the same target.
 */
@Composable
fun ExpiredGoals(state: WorkspaceState, categoryId: Long?, onBack: () -> Unit, onDeleteGoal: (Long) -> Unit, includeActive: Boolean = false, allowDelete: Boolean = true) {
    val expired = remember(state.goals, categoryId) {
        state.goals.filter { it.expiredAt != null && (categoryId == null || it.categoryId == categoryId) }.sortedByDescending { it.expiredAt }
    }
    val goals = remember(state.goals, categoryId) {
        if (includeActive) state.goals.filter { it.expiredAt == null && (categoryId == null || it.categoryId == categoryId) }.sortedBy { it.expiry() }
        else emptyList()
    }
    var confirmDelete by remember { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize().testTag("expired_goals")) {
        // Same banner metrics as PageHeader, so every secondary page starts its list at the same y.
        Row(Modifier.fillMaxWidth().heightIn(min = Metrics.pageHeader).padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, Modifier.testTag("expired_back")) { Icon(AppIcons.Back, stringResource(R.string.back), Modifier.size(Metrics.icon)) }
            Text(stringResource(if (includeActive) R.string.goal_records else R.string.expired_goals), style = MaterialTheme.typography.titleLarge)
        }
        LazyColumn(Modifier.fillMaxSize().topFade(), contentPadding = PaddingValues(start = Space.md, end = Space.md, top = Space.sm, bottom = Metrics.dockClearance), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            val now = Instant.now()
            val zone = ZoneId.systemDefault()
            items(goals, key = { "g" + it.id }) { goal ->
                GoalCard(goal, state, goalProgress(goal, state.records, state.definitions, state.values, now, zone), goal.instanceWindow(now, zone), {})
            }
            items(expired, key = { "e" + it.id }) { goal ->
                ExpiredGoalCard(goal, state, allowDelete = allowDelete, onDelete = { confirmDelete = goal.id })
            }
            if (expired.isEmpty() && goals.isEmpty()) item { ListEmpty(R.string.goal_empty) }
        }
    }
    confirmDelete?.let { id -> ConfirmAction(R.string.delete, R.string.confirm_delete, { confirmDelete = null }, { confirmDelete = null; onDeleteGoal(id) }) }
}

@Composable fun ExpiredGoalCard(goal: Goal, state: WorkspaceState, modifier: Modifier = Modifier, allowDelete: Boolean = true, onDelete: () -> Unit = {}) {
    val category = state.categories.firstOrNull { it.id == goal.categoryId }
    val accent = category?.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val span = TimeSpan(goal.startAt, goal.expiredAt ?: goal.startAt)
    val actual = remember(goal, state.records, state.definitions, state.values) {
        goalInstanceActual(goal, state.records, state.definitions, state.values, span)
    }
    val met = goal.direction == GoalDirection.AT_LEAST && actual >= goal.target
    val over = goal.direction == GoalDirection.AT_MOST && actual > goal.target
    Column(modifier.fillMaxWidth().cardSurface()
        .padding(horizontal = Space.md, vertical = Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            CategoryMark(category)
            Column(Modifier.weight(1f)) {
                Text(goal.name.ifBlank { category?.name ?: stringResource(R.string.uncategorized) }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(goalInstanceText(span), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            if (allowDelete) IconButton(onClick = onDelete, modifier = Modifier.size(Metrics.controlHeight)) {
                Icon(AppIcons.Close, stringResource(R.string.delete), Modifier.size(Metrics.icon), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.goal_progress, amountText(goal.metric, actual), amountText(goal.metric, goal.target)), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(when {
                    over -> R.string.goal_over
                    goal.direction == GoalDirection.AT_MOST -> R.string.goal_within
                    met -> R.string.goal_done
                    else -> R.string.goal_missed
                }),
                style = MaterialTheme.typography.labelMedium,
                color = if (over) MaterialTheme.colorScheme.error else if (met) accent else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Sep 10 – Sep 17": the span an instance covers. */
@Composable private fun goalInstanceText(window: TimeSpan): String {
    val zone = ZoneId.systemDefault()
    val start = window.start.atZone(zone).toLocalDate()
    val end = window.end.atZone(zone).toLocalDate()
    return if (start == end) dateText(start) else dateText(start) + " – " + dateText(end)
}

@Composable private fun GoalCard(goal: Goal, state: WorkspaceState, progress: GoalProgress, window: TimeSpan, click: () -> Unit) {
    val category = state.categories.firstOrNull { it.id == goal.categoryId }
    val accent = category?.color?.let { Color(it) } ?: MaterialTheme.colorScheme.primary
    val over = progress.isOver
    val reached = progress.isMet
    val current = amountText(goal.metric, progress.current)
    val target = amountText(goal.metric, progress.target)
    val fraction = if (goal.target > 0) (progress.current.toFloat() / goal.target).coerceIn(0f, 1f) else 0f
    Column(Modifier.fillMaxWidth().cardSurface()
        .clickable(onClick = click).padding(horizontal = Space.md, vertical = Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            CategoryMark(category)
            Column(Modifier.weight(1f)) {
                Text(goal.name.ifBlank { category?.name ?: stringResource(R.string.uncategorized) }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val detail = listOfNotNull(
                    // The span of the instance that is running now, so the rollover is visible.
                    goalInstanceText(window),
                    goal.name.takeIf { it.isNotBlank() }?.let { category?.name },
                    goal.titleFilter.takeIf { it.isNotBlank() }?.let { stringResource(R.string.goal_title_filter) + " " + it },
                    goal.propertyDefinitionId?.let { id -> state.definitions.firstOrNull { it.id == id }?.name?.let { name -> goal.propertyFilter.takeIf { it.isNotBlank() }?.let { name + " " + it } ?: name } },
                ).joinToString(" · ")
                if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        // The direction reads as its own line above the numbers, so it lines up with them instead of
        // floating in the corner of a two-line title block.
        Text(stringResource(if (goal.direction == GoalDirection.AT_LEAST) R.string.goal_at_least else R.string.goal_at_most),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.goal_progress, current, target), style = MaterialTheme.typography.titleMedium)
        Box(Modifier.fillMaxWidth().height(Metrics.shareBar).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().clip(CircleShape).background(if (over) MaterialTheme.colorScheme.error else accent))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(goalSummary(goal), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                over -> Text(stringResource(R.string.goal_over), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                goal.direction == GoalDirection.AT_MOST -> Text(stringResource(R.string.goal_within), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                reached -> Text(stringResource(R.string.goal_done), style = MaterialTheme.typography.labelMedium, color = accent)
                progress.completed > 0 -> Text(pluralStringResource(R.plurals.goal_reset_times, progress.completed, progress.completed), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // A running "reach at least" goal says so, instead of leaving the corner empty.
                else -> Text(stringResource(R.string.goal_in_progress), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable private fun amountText(metric: GoalMetric, value: Long): String =
    if (metric == GoalMetric.TIME) durationText(value) else value.toString() + " " + pluralStringResource(R.plurals.unit_times, value.toInt(), value.toInt())

/** When the running instance of this goal runs out; goals are listed by it. */
internal fun Goal.expiry(zone: ZoneId = ZoneId.systemDefault()): Instant = periodEnd(startAt, zone)

/** A reset goal has no frame: it only ends when its target is reached, whenever that happens. */
internal fun GoalRepeat.isOpenEndedFor(direction: GoalDirection): Boolean = this == GoalRepeat.RESET && direction == GoalDirection.AT_LEAST

@Composable private fun goalSummary(goal: Goal): String {
    val repeat = stringResource(goal.repeat.label())
    if (goal.repeat.isOpenEndedFor(goal.direction)) return repeat
    return listOf(goal.periodValue.toString() + " " + unitText(goal.periodUnit, goal.periodValue), repeat).joinToString(" · ")
}

internal fun GoalRepeat.label(): Int = when (this) {
    GoalRepeat.NONE -> R.string.goal_repeat_none
    GoalRepeat.CYCLE -> R.string.goal_repeat_cycle
    GoalRepeat.RESET -> R.string.goal_repeat_reset
}

@Composable internal fun unitText(unit: GoalUnit, count: Int): String = when (unit) {
    GoalUnit.MONTH -> pluralStringResource(R.plurals.unit_months, count, count)
    GoalUnit.WEEK -> pluralStringResource(R.plurals.unit_weeks, count, count)
    GoalUnit.DAY -> pluralStringResource(R.plurals.unit_days, count, count)
    GoalUnit.HOUR -> pluralStringResource(R.plurals.unit_hours, count, count)
    GoalUnit.MINUTE -> pluralStringResource(R.plurals.unit_minutes, count, count)
}

@Composable
fun GoalEditor(value: Goal, state: WorkspaceState, busy: Boolean, dismiss: () -> Unit, save: (Goal) -> Unit, delete: (() -> Unit)?, onDuplicate: ((Goal) -> Unit)? = null) {
    val zone = ZoneId.systemDefault()
    val dayStart = LocalDisplayPreferences.current.dayStartMinutes
    var name by rememberSaveable(value.id) { mutableStateOf(value.name) }
    var category by rememberSaveable(value.id) { mutableStateOf(value.categoryId) }
    var titleFilter by rememberSaveable(value.id) { mutableStateOf(value.titleFilter) }
    var definitionId by rememberSaveable(value.id) { mutableStateOf(value.propertyDefinitionId) }
    var propertyFilter by rememberSaveable(value.id) { mutableStateOf(value.propertyFilter) }
    var metric by rememberSaveable(value.id) { mutableStateOf(value.metric) }
    var direction by rememberSaveable(value.id) { mutableStateOf(value.direction) }
    var target by rememberSaveable(value.id) { mutableLongStateOf(value.target) }
    var periodUnit by rememberSaveable(value.id) { mutableStateOf(value.periodUnit) }
    var periodValue by rememberSaveable(value.id) { mutableIntStateOf(value.periodValue) }
    var repeat by rememberSaveable(value.id) { mutableStateOf(value.repeat) }
    var startDate by rememberSaveable(value.id) { mutableStateOf(logicalDate(value.startAt, zone, dayStart)) }
    var startTime by rememberSaveable(value.id) { mutableStateOf(value.startAt.atZone(zone).toLocalTime().withSecond(0).withNano(0)) }
    var targetDialog by remember { mutableStateOf(false) }
    var frameDialog by remember { mutableStateOf(false) }
    var propertyDialog by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<Int?>(null) }
    val definition = state.definitions.firstOrNull { it.id == definitionId }
    val buildGoal: () -> Goal? = {
        if (name.isBlank()) { error = R.string.error_goal_name; null } else {
            error = null
            value.copy(name = name.trim(), categoryId = category, titleFilter = titleFilter, propertyDefinitionId = definitionId,
                propertyFilter = if (definitionId == null) "" else propertyFilter, metric = metric, direction = direction, target = target,
                periodUnit = periodUnit, periodValue = periodValue.coerceAtLeast(1), repeat = repeat, startAt = resolveLocal(startDate, startTime, zone))
        }
    }
    EditorSheet(if (value.id == 0L) R.string.new_goal else R.string.edit_goal, dismiss, footer = {
        EditorActions(busy, onSave = { buildGoal()?.let(save) },
            onDelete = if (value.id != 0L && delete != null) ({ confirmDelete = true }) else null,
            onDuplicate = if (onDuplicate == null) null else ({ buildGoal()?.let(onDuplicate) }))
    }) {
        FormField(R.string.goal_name, name, { name = it }, tag = "goal_name")
        CategoryChoice(state.categories, category, { category = it; definitionId = null; propertyFilter = "" }, emptyLabel = R.string.all_items)
        FormField(R.string.goal_title_filter, titleFilter, { titleFilter = it }, tag = "goal_title")
        val definitionOptions = listOf<Pair<Long?, String>>(null to stringResource(R.string.goal_none)) + state.definitions.filter { it.categoryId == category }.map { it.id to it.name }
        ChoiceField(R.string.goal_property_filter, definitionId, definitionOptions, { definitionId = it; propertyFilter = "" })
        when {
            definition == null -> {}
            definition.type == PropertyType.SELECT || definition.type == PropertyType.MULTISELECT -> OptionRow(definition.name, { propertyDialog = true },
                value = propertyFilter.lines().filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString("、") ?: stringResource(R.string.goal_none))
            definition.type == PropertyType.NUMBER || definition.type == PropertyType.RATING -> {
                val parts = propertyFilter.split(',', limit = 2)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Box(Modifier.weight(1f)) { FormField(R.string.goal_min, parts.getOrNull(0).orEmpty().trim(), { propertyFilter = it + "," + parts.getOrNull(1).orEmpty() }, tag = "goal_min") }
                    Box(Modifier.weight(1f)) { FormField(R.string.goal_max, parts.getOrNull(1).orEmpty().trim(), { propertyFilter = parts.getOrNull(0).orEmpty() + "," + it }, tag = "goal_max") }
                }
            }
            else -> PlainInput(definition.name, propertyFilter, { propertyFilter = it })
        }
        ChoiceField(R.string.goal_direction, direction, listOf(GoalDirection.AT_LEAST to stringResource(R.string.goal_at_least), GoalDirection.AT_MOST to stringResource(R.string.goal_at_most)), { picked ->
            direction = picked
            // A limit is never "reached", so it cannot restart on completion.
            if (picked == GoalDirection.AT_MOST && repeat == GoalRepeat.RESET) repeat = GoalRepeat.CYCLE
        })
        OptionRow(stringResource(R.string.goal_target), { targetDialog = true }, value = amountText(metric, target))
        // Only a framed goal has a period; one that restarts on completion runs until it is reached.
        if (!repeat.isOpenEndedFor(direction)) OptionRow(stringResource(R.string.goal_limit), { frameDialog = true }, value = periodValue.toString() + " " + unitText(periodUnit, periodValue))
        DateTimeField(R.string.start_at, startDate, startTime) { date, time -> startDate = date; startTime = time }
        val repeats = if (direction == GoalDirection.AT_MOST) listOf(GoalRepeat.NONE, GoalRepeat.CYCLE) else GoalRepeat.entries.toList()
        ChoiceField(R.string.goal_repeat, repeat, repeats.map { it to stringResource(it.label()) }, { repeat = it })
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
    }
    if (targetDialog) {
        val (amount, unit) = targetParts(metric, target)
        AmountDialog(stringResource(R.string.goal_target), amount, unit, TargetUnit.entries, { targetDialog = false }, label = { targetUnitText(it) }) { value, picked ->
            metric = if (picked == TargetUnit.COUNT) GoalMetric.COUNT else GoalMetric.TIME
            target = if (picked == TargetUnit.COUNT) value.toLong() else value.toLong() * unitMillis(picked)
            targetDialog = false
        }
    }
    if (frameDialog) AmountDialog(stringResource(R.string.goal_limit), periodValue, periodUnit, GoalUnit.entries, { frameDialog = false }, label = { unitText(it, 1) }) { amount, unit -> periodValue = amount; periodUnit = unit; frameDialog = false }
    if (propertyDialog && definition != null) {
        val chosen = propertyFilter.lines().filter { it.isNotBlank() }.toSet()
        SelectionDialog(definition.name, { propertyDialog = false }) {
            definition.options.lines().filter { it.isNotBlank() }.forEach { option ->
                SelectionRow(option, option in chosen, { propertyFilter = (if (option in chosen) chosen - option else chosen + option).joinToString("\n") }, multi = true)
            }
        }
    }
    if (confirmDelete && delete != null) ConfirmAction(R.string.delete, R.string.confirm_delete, { confirmDelete = false }, { confirmDelete = false; delete() })
}

@Composable private fun targetUnitText(unit: TargetUnit): String = when (unit) {
    TargetUnit.HOUR -> unitText(GoalUnit.HOUR, 1)
    TargetUnit.MINUTE -> unitText(GoalUnit.MINUTE, 1)
    TargetUnit.COUNT -> pluralStringResource(R.plurals.unit_times, 1, 1)
}

private fun targetParts(metric: GoalMetric, target: Long): Pair<Int, TargetUnit> = when {
    metric == GoalMetric.COUNT -> target.toInt() to TargetUnit.COUNT
    target > 0 && target % 3_600_000L == 0L -> (target / 3_600_000L).toInt() to TargetUnit.HOUR
    else -> (target / 60_000L).toInt() to TargetUnit.MINUTE
}

private fun unitMillis(unit: TargetUnit): Long = when (unit) {
    TargetUnit.HOUR -> 3_600_000L
    else -> 60_000L
}
