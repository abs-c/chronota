package app.chronota.domain

import app.chronota.data.entity.Plan
import app.chronota.data.entity.Recurrence
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Upper bound for one expansion call so a daily plan cannot flood the UI. */
const val MAX_OCCURRENCES = 400

fun Plan.isRecurring(): Boolean = recurrence != Recurrence.NONE && scheduledDate != null

/** Dates explicitly removed from the series, one ISO date per line. */
fun Plan.skippedDates(): Set<LocalDate> = parseSkipDates(skipDates)

fun parseSkipDates(text: String): Set<LocalDate> = text.lines().mapNotNull { line -> line.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() } }.toSet()

fun skippedDatesText(dates: Collection<LocalDate>): String = dates.sorted().joinToString("\n") { it.toString() }

/**
 * Occurrence dates anchored on [Plan.scheduledDate] and bounded by [Plan.recurrenceUntil].
 * Results are sorted and capped at [limit]; the anchor itself is always the first candidate.
 */
fun Plan.occurrenceDates(from: LocalDate, to: LocalDate, limit: Int = MAX_OCCURRENCES): List<LocalDate> {
    val anchor = scheduledDate ?: return emptyList()
    if (!isRecurring()) return if (anchor >= from && anchor <= to && anchor !in skippedDates()) listOf(anchor) else emptyList()
    val last = recurrenceUntil?.let { minOf(it, to) } ?: to
    if (last < anchor || last < from) return emptyList()
    val step = recurrenceInterval.coerceIn(1, 999)
    val skipped = skippedDates()
    val dates = mutableListOf<LocalDate>()
    var index = firstIndex(anchor, maxOf(from, anchor), step)
    while (dates.size < limit) {
        val date = occurrenceAt(anchor, index, step)
        if (date > last) break
        if (date >= from && date !in skipped) dates += date
        index++
    }
    return dates
}

/** First occurrence on or after [from], or null when the series has already ended. */
fun Plan.nextOccurrence(from: LocalDate): LocalDate? {
    val anchor = scheduledDate ?: return null
    val skipped = skippedDates()
    if (!isRecurring()) return anchor.takeIf { it >= from && it !in skipped }
    val last = recurrenceUntil
    if (last != null && last < from) return null
    val step = recurrenceInterval.coerceIn(1, 999)
    var index = firstIndex(anchor, maxOf(from, anchor), step)
    var guard = 0
    while (guard++ < MAX_OCCURRENCES * 4) {
        val date = occurrenceAt(anchor, index, step)
        if (last != null && date > last) return null
        if (date >= from && date !in skipped) return date
        index++
    }
    return null
}

/** Occurrences that start inside [from]..[to], expanded into per-date plan copies. */
fun Plan.expandOccurrences(from: LocalDate, to: LocalDate, limit: Int = MAX_OCCURRENCES): List<Plan> =
    occurrenceDates(from, to, limit).map { date -> copy(scheduledDate = date) }

/** Span of one occurrence, keeping the series' time of day and cross-day offset. */
fun Plan.occurrenceSpan(date: LocalDate, dayStartMinutes: Int = 0): TimeSpan? =
    copy(scheduledDate = date).viewSpan(dayStartMinutes)

fun spanState(span: TimeSpan?, now: Instant): PlanTemporalState = when {
    span == null -> PlanTemporalState.UNSCHEDULED
    now < span.start -> PlanTemporalState.FUTURE
    now >= span.end -> PlanTemporalState.PAST
    else -> PlanTemporalState.ACTIVE
}

/** Trim a span to what has not happened yet; null once it is over. */
fun clampToNow(span: TimeSpan?, now: Instant): TimeSpan? =
    span?.let {
        // A span with no length is a point in time: it never becomes "over", so a zero-duration
        // plan keeps its block instead of disappearing the moment its time arrives.
        when {
            it.millis == 0L -> it
            it.end > now -> TimeSpan(maxOf(it.start, now), it.end)
            else -> null
        }
    }

/** Occurrence running right now, otherwise the next one that has not started yet. */
fun Plan.currentOrNextSpan(now: Instant, dayStartMinutes: Int = 0): TimeSpan? {
    if (!isRecurring()) return viewSpan(dayStartMinutes)
    val zone = ZoneId.of(zoneId)
    val today = now.atZone(zone).toLocalDate()
    val spans = occurrenceDates(today.minusDays(1), today.plusDays(2)).mapNotNull { occurrenceSpan(it, dayStartMinutes) }
    spans.firstOrNull { now >= it.start && now < it.end }?.let { return it }
    return spans.firstOrNull { it.start > now }
}

private fun Plan.occurrenceAt(anchor: LocalDate, index: Int, step: Int): LocalDate {
    val distance = index.toLong() * step
    return when (recurrence) {
        Recurrence.DAILY -> anchor.plusDays(distance)
        Recurrence.WEEKLY -> anchor.plusWeeks(distance)
        Recurrence.MONTHLY -> anchor.plusMonths(distance)
        Recurrence.YEARLY -> anchor.plusYears(distance)
        Recurrence.NONE -> anchor
    }
}

/** Index of the first occurrence at or after [from]; the caller filters the exact date. */
private fun Plan.firstIndex(anchor: LocalDate, from: LocalDate, step: Int): Int {
    if (from <= anchor) return 0
    val raw = when (recurrence) {
        Recurrence.DAILY -> ChronoUnit.DAYS.between(anchor, from)
        Recurrence.WEEKLY -> ChronoUnit.WEEKS.between(anchor, from)
        Recurrence.MONTHLY -> ChronoUnit.MONTHS.between(anchor, from)
        Recurrence.YEARLY -> ChronoUnit.YEARS.between(anchor, from)
        Recurrence.NONE -> 0L
    }
    return (raw / step).coerceAtLeast(0L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
}

/** Most recent occurrence that already started, used when recording what happened. */
fun Plan.lastOccurrenceSpan(now: Instant, dayStartMinutes: Int = 0): TimeSpan? {
    if (!isRecurring()) return viewSpan(dayStartMinutes)
    val zone = ZoneId.of(zoneId)
    val today = now.atZone(zone).toLocalDate()
    return occurrenceDates(today.minusYears(1), today.plusDays(1))
        .mapNotNull { occurrenceSpan(it, dayStartMinutes) }
        .lastOrNull { it.start <= now }
}
