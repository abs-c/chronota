package app.chronota.domain

import app.chronota.data.entity.Plan
import app.chronota.data.entity.Record
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class BrowseEntry(val id: Long, val isPlan: Boolean, val title: String, val categoryId: Long?,
    val date: LocalDate?, val span: TimeSpan?, val allDay: Boolean = false, val occurrence: LocalDate? = null,
    /** The running timer's own entry: a record that is not stored yet, so it is shown, not counted. */
    val live: Boolean = false) {
    val key: String get() = (if (isPlan) "p" else "r") + id + (occurrence?.let { "@" + it.toEpochDay() } ?: "")
    fun onDate(date: LocalDate, zone: ZoneId, dayStartMinutes: Int = 0): Boolean = span?.clippedTo(daySpan(date, zone, dayStartMinutes)) != null || (span == null && this.date == date)
}

/** Agenda entries: one row per item; a recurring plan shows at its next occurrence. */
fun browseEntries(plans: List<Plan>, records: List<Record>, zone: ZoneId = ZoneId.systemDefault(),
    dayStartMinutes: Int = 0, today: LocalDate? = null): List<BrowseEntry> {
    val reference = today ?: logicalDate(Instant.now(), zone, dayStartMinutes)
    val planEntries = plans.flatMap { plan ->
        val anchor = plan.scheduledDate
        val dates = when {
            anchor == null -> listOf<LocalDate?>(null)
            !plan.isRecurring() -> listOf(anchor)
            else -> plan.occurrenceDates(reference.minusMonths(3), reference.plusMonths(3)).ifEmpty { listOf(plan.nextOccurrence(reference) ?: anchor) }
        }
        dates.map { shown ->
            val span = shown?.let { plan.occurrenceSpan(it, dayStartMinutes) } ?: plan.viewSpan(dayStartMinutes)
            BrowseEntry(plan.id, true, plan.title, plan.categoryId,
                if (plan.allDay) shown else span?.let { logicalDate(it.start, zone, dayStartMinutes) } ?: shown,
                span, plan.allDay, shown)
        }
    }
    val recordEntries = records.map { BrowseEntry(it.id, false, it.title, it.categoryId, logicalDate(it.startTime, zone, dayStartMinutes), TimeSpan(it.startTime, it.endTime), live = it.id == LIVE_RECORD_ID) }
    return planEntries + recordEntries
}

/** Calendar entries: plans expanded into every occurrence inside [from]..[to]. */
fun calendarEntries(plans: List<Plan>, records: List<Record>, from: LocalDate, to: LocalDate, now: Instant,
    zone: ZoneId = ZoneId.systemDefault(), dayStartMinutes: Int = 0, includePastPlans: Boolean = false): List<BrowseEntry> {
    // A logical day that starts late reaches into the next calendar date, so a plan sitting in those
    // small hours is scheduled on `to + 1` and would be missed if the expansion stopped at [to]. The
    // occurrence's own logical day then decides whether it was really asked for. Records need no such
    // allowance: their day is read off the instant, never off a stored date.
    val planEntries = plans.flatMap { plan ->
        plan.expandOccurrences(from, to.plusDays(1)).mapNotNull { occurrence ->
            val date = occurrence.scheduledDate ?: return@mapNotNull null
            val span = occurrence.viewSpan(dayStartMinutes) ?: return@mapNotNull null
            val belongs = if (plan.allDay) date else logicalDate(span.start, zone, dayStartMinutes)
            if (belongs.isBefore(from) || belongs.isAfter(to)) return@mapNotNull null
            when {
                span.end <= now && includePastPlans -> BrowseEntry(plan.id, true, plan.title, plan.categoryId, if (plan.allDay) date else logicalDate(span.start, zone, dayStartMinutes), span, plan.allDay, date)
                span.end <= now -> null
                else -> BrowseEntry(plan.id, true, plan.title, plan.categoryId,
                    if (plan.allDay) date else logicalDate(maxOf(span.start, now), zone, dayStartMinutes),
                    TimeSpan(maxOf(span.start, now), span.end), plan.allDay, date)
            }
        }
    }
    val recordEntries = records.filter { it.startTime < now }.map {
        BrowseEntry(it.id, false, it.title, it.categoryId, logicalDate(it.startTime, zone, dayStartMinutes), TimeSpan(it.startTime, minOf(it.endTime, now)), live = it.id == LIVE_RECORD_ID)
    }
    return planEntries + recordEntries
}
