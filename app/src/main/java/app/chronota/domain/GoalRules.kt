package app.chronota.domain

import app.chronota.data.entity.*
import java.time.*

data class GoalProgress(val current: Long, val target: Long, val direction: GoalDirection, val completed: Int = 0) {
    val isMet: Boolean get() = direction == GoalDirection.AT_LEAST && current >= target
    val isOver: Boolean get() = direction == GoalDirection.AT_MOST && current > target
}

/** A record matches when it passes the category, title (regex) and property filter. */
fun Goal.matches(record: Record, definition: PropertyDefinition?, value: String?): Boolean {
    if (categoryId != null && record.categoryId != categoryId) return false
    if (titleFilter.isNotBlank() && !runCatching { Regex(titleFilter).containsMatchIn(record.title) }.getOrDefault(false)) return false
    if (propertyDefinitionId == null || definition == null) return true
    return propertyMatches(definition, propertyFilter, value)
}

/**
 * Property filters keep their payload in one string:
 * NUMBER/RATING `min,max` (open ends allowed), SELECT/MULTISELECT selected options one per
 * line (the record's value set must be a subset), TEXT a regular expression.
 */
fun propertyMatches(definition: PropertyDefinition, filter: String, value: String?): Boolean {
    if (filter.isBlank()) return value != null
    if (value == null) return false
    return when (definition.type) {
        PropertyType.NUMBER, PropertyType.RATING -> {
            val actual = if (definition.type == PropertyType.RATING) ratingScore(value).toDouble() else value.toDoubleOrNull() ?: return false
            val parts = filter.split(',', limit = 2)
            val min = parts.getOrNull(0)?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            val max = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.toDoubleOrNull()
            (min == null || actual >= min) && (max == null || actual <= max)
        }
        PropertyType.SELECT, PropertyType.MULTISELECT -> {
            val wanted = filter.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            val actual = value.lines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            actual.isNotEmpty() && actual.all { it in wanted }
        }
        else -> runCatching { Regex(filter).containsMatchIn(value) }.getOrDefault(false)
    }
}

fun Goal.measure(record: Record): Long =
    if (metric == GoalMetric.TIME) Duration.between(record.startTime, record.endTime).toMillis() else 1L

/** The instant an instance that began at [from] ends: one [periodValue] [periodUnit] later. */
fun Goal.periodEnd(from: Instant, zone: ZoneId = ZoneId.systemDefault()): Instant =
    from.atZone(zone).let {
        when (periodUnit) {
            GoalUnit.MONTH -> it.plusMonths(periodValue.toLong())
            GoalUnit.WEEK -> it.plusWeeks(periodValue.toLong())
            GoalUnit.DAY -> it.plusDays(periodValue.toLong())
            GoalUnit.HOUR -> it.plusHours(periodValue.toLong())
            GoalUnit.MINUTE -> it.plusMinutes(periodValue.toLong())
        }
    }.toInstant()

/**
 * The span the running instance covers. [Goal.startAt] always marks the start of the instance
 * that is running now: repeating goals move it forward as each instance retires, which is what
 * keeps their periods chained (the next one starts exactly when the previous one ended).
 */
fun Goal.instanceWindow(now: Instant, zone: ZoneId = ZoneId.systemDefault()): TimeSpan =
    // A reset goal only ends when its target is reached, so it stays open until then.
    if (repeat == GoalRepeat.RESET && direction == GoalDirection.AT_LEAST) TimeSpan(startAt, maxOf(now, startAt))
    else TimeSpan(startAt, periodEnd(startAt, zone))

/**
 * RESET goals start over every time the accumulated amount reaches the target, so the
 * progress always sits below it. CYCLE and NONE measure the running instance.
 */
fun goalProgress(goal: Goal, records: List<Record>, definitions: List<PropertyDefinition>, propertyValues: List<PropertyValue>,
    now: Instant, zone: ZoneId = ZoneId.systemDefault()): GoalProgress {
    val definition = definitions.firstOrNull { it.id == goal.propertyDefinitionId }
    val byDefinition = propertyValues.filter { it.definitionId == goal.propertyDefinitionId }.associate { it.recordId to it.value }
    // Only what happened after the running instance began counts; retired ones live in the ended list.
    val matched = records.filter { it.endTime > goal.startAt && goal.matches(it, definition, byDefinition[it.id]) }.sortedBy { it.startTime }
    if (goal.repeat == GoalRepeat.RESET && goal.direction == GoalDirection.AT_LEAST) {
        var current = 0L
        var completed = 0
        matched.forEach { record ->
            current += goal.measure(record)
            if (current >= goal.target) { current = 0; completed++ }
        }
        return GoalProgress(current, goal.target, goal.direction, completed)
    }
    val window = goal.instanceWindow(now, zone)
    val current = matched.sumOf { record ->
        TimeSpan(record.startTime, record.endTime).clippedTo(window)?.let { if (goal.metric == GoalMetric.TIME) it.millis else 1L } ?: 0L
    }
    return GoalProgress(current, goal.target, goal.direction)
}

/** One instance that already ran out: the span it covered. */
data class GoalInstance(val start: Instant, val end: Instant)

/** What rolling a goal forward leaves behind: the retired instances and the running one. */
data class GoalRollover(val instances: List<GoalInstance>, val startAt: Instant, val retired: Boolean) {
    /** True when anything about the goal changed and rows have to be written. */
    val moved: Boolean get() = retired || instances.isNotEmpty()
}

/**
 * Rolls [goal] forward to the instance that contains [now], oldest instance first.
 *
 * CYCLE retires every instance that has run out — starting the next one at the exact moment the
 * previous expired, repeating until the running instance reaches beyond [now]. RESET retires the
 * instance only when its target was reached, starting the next one at that completion. NONE has no
 * successor, so once its frame has passed it is retired for good.
 */
fun goalRollover(goal: Goal, records: List<Record>, definitions: List<PropertyDefinition>, propertyValues: List<PropertyValue>,
    now: Instant, zone: ZoneId = ZoneId.systemDefault()): GoalRollover {
    val definition = definitions.firstOrNull { it.id == goal.propertyDefinitionId }
    val byDefinition = propertyValues.filter { it.definitionId == goal.propertyDefinitionId }.associate { it.recordId to it.value }
    val matched = records.filter { goal.matches(it, definition, byDefinition[it.id]) }.sortedBy { it.startTime }
    if (goal.repeat == GoalRepeat.RESET && goal.direction == GoalDirection.AT_LEAST) {
        val done = mutableListOf<GoalInstance>()
        var current = 0L
        var start = goal.startAt
        matched.filter { it.endTime > goal.startAt }.forEach { record ->
            current += goal.measure(record)
            if (current >= goal.target) {
                done += GoalInstance(start, record.endTime)
                current = 0
                start = record.endTime
            }
        }
        return GoalRollover(done, start, retired = false)
    }
    val end = goal.periodEnd(goal.startAt, zone)
    if (goal.repeat == GoalRepeat.NONE) {
        return if (now >= end) GoalRollover(listOf(GoalInstance(goal.startAt, end)), goal.startAt, retired = true)
        else GoalRollover(emptyList(), goal.startAt, retired = false)
    }
    val done = mutableListOf<GoalInstance>()
    var start = goal.startAt
    var cursor = end
    var guard = 0
    while (cursor <= now && cursor > start && guard++ < 1000) {
        done += GoalInstance(start, cursor)
        start = cursor
        cursor = goal.periodEnd(start, zone)
    }
    return GoalRollover(done, start, retired = false)
}

/** What an instance reached: how much [records] put into [span], measured the way the goal does. */
fun goalInstanceActual(goal: Goal, records: List<Record>, definitions: List<PropertyDefinition>, propertyValues: List<PropertyValue>,
    span: TimeSpan): Long {
    val definition = definitions.firstOrNull { it.id == goal.propertyDefinitionId }
    val byDefinition = propertyValues.filter { it.definitionId == goal.propertyDefinitionId }.associate { it.recordId to it.value }
    return records.filter { goal.matches(it, definition, byDefinition[it.id]) }.sumOf { record ->
        TimeSpan(record.startTime, record.endTime).clippedTo(span)?.let { if (goal.metric == GoalMetric.TIME) it.millis else 1L } ?: 0L
    }
}
