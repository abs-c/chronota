package app.chronota.domain

import app.chronota.data.entity.*
import java.time.*
import java.time.temporal.ChronoUnit

enum class PlanTemporalState { UNSCHEDULED, FUTURE, ACTIVE, PAST }
enum class HistoricalPlanPolicy { IMMUTABLE, EDITABLE }
enum class OrbAction { TIMER, PLAN, RECORD, GOAL, INBOX }

// Single order shared by the long-press wheel and the Settings picker.
val orbActions = listOf(OrbAction.PLAN, OrbAction.GOAL, OrbAction.RECORD, OrbAction.TIMER)

/**
 * One minute is the smallest unit this app records. A running timer counts seconds so it can show
 * them, but nothing stored keeps them: every recorded moment and every plan time is cut back to the
 * minute it fell in, so seconds never mean anything once written. Callers should not round on their
 * own — this is the single place the rule lives.
 */
fun Instant.wholeMinute(): Instant = truncatedTo(ChronoUnit.MINUTES)

/** See [Instant.wholeMinute]: a plan's clock time is kept to the minute too. */
fun LocalTime.wholeMinute(): LocalTime = LocalTime.of(hour, minute)

data class TimeSpan(val start: Instant, val end: Instant) {
    init { require(end >= start) }
    val millis: Long get() = Duration.between(start, end).toMillis()
    fun clippedTo(other: TimeSpan): TimeSpan? {
        val left = maxOf(start, other.start)
        val right = minOf(end, other.end)
        if (right > left) return TimeSpan(left, right)
        // A zero-length event is kept when its instant falls inside the window.
        return if (start == end && start >= other.start && start < other.end) TimeSpan(start, start) else null
    }
}

// Reject non-existent spring-forward wall times; choose the earlier offset in fall-back overlaps.
fun resolveLocal(date: LocalDate, time: LocalTime, zone: ZoneId): Instant {
    val local = date.atTime(time)
    val offsets = zone.rules.getValidOffsets(local)
    require(offsets.isNotEmpty()) { "DST_GAP" }
    return local.toInstant(offsets.first())
}

// Editing only a title/category must preserve the original instant in a DST overlap.
fun resolveEditedInstant(original: Instant, date: LocalDate, time: LocalTime, zone: ZoneId): Instant {
    val local = original.atZone(zone)
    return if (local.toLocalDate() == date && local.toLocalTime() == time) original else resolveLocal(date, time, zone)
}

fun daySpan(date: LocalDate, zone: ZoneId, dayStartMinutes: Int = 0): TimeSpan =
    TimeSpan(date.atTime(LocalTime.ofSecondOfDay(dayStartMinutes * 60L)).atZone(zone).toInstant(),
        date.plusDays(1).atTime(LocalTime.ofSecondOfDay(dayStartMinutes * 60L)).atZone(zone).toInstant())

fun weekStartOf(date: LocalDate, weekStart: Int = 7): LocalDate =
    date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.of(weekStart.coerceIn(1, 7))))

fun logicalDate(instant: Instant, zone: ZoneId = ZoneId.systemDefault(), dayStartMinutes: Int = 0): LocalDate {
    val local = instant.atZone(zone)
    return local.toLocalDate().minusDays(if (instant < daySpan(local.toLocalDate(), zone, dayStartMinutes).start) 1 else 0)
}

fun Plan.viewSpan(dayStartMinutes: Int = 0): TimeSpan? {
    val date = scheduledDate ?: return null
    // Only an all-day plan is anchored to the day window; a timed plan keeps plain clock semantics.
    return if (allDay) TimeSpan(daySpan(date, ZoneId.of(zoneId), dayStartMinutes).start,
        daySpan(date.plusDays(endDayOffset.toLong()), ZoneId.of(zoneId), dayStartMinutes).end) else timeSpan()
}

fun displayName(title: String, category: String?, preferCategory: Boolean, fallback: String): String =
    if (preferCategory) category ?: title.ifBlank { fallback } else title.ifBlank { category ?: fallback }

fun calendarPlanSpan(plan: Plan, now: Instant, dayStartMinutes: Int): TimeSpan? =
    clampToNow(plan.currentOrNextSpan(now, dayStartMinutes), now)

fun calendarRecords(records: List<Record>, now: Instant): List<Record> =
    records.filter { it.startTime < now }.map { it.copy(endTime = minOf(it.endTime, now)) }

/**
 * A plan's [Plan.scheduledDate] is a plain calendar date and its clock times are plain local times,
 * exactly as the editor and the "add at this slot" gesture already produce them from a tapped
 * instant. Which *logical* day it belongs to is then read off the resulting instant with
 * [logicalDate] — the same helper records go through — so a plan made at 01:00 while the app shows
 * the previous logical day is filed with that day instead of being shifted a second time.
 */
fun Plan.timeSpan(): TimeSpan? {
    val date = scheduledDate ?: return null
    if (allDay) return TimeSpan(date.atStartOfDay(ZoneId.of(zoneId)).toInstant(), date.plusDays(endDayOffset.toLong() + 1).atStartOfDay(ZoneId.of(zoneId)).toInstant())
    val start = startTime ?: return null
    val end = endTime ?: return null
    return TimeSpan(resolveLocal(date, start, ZoneId.of(zoneId)), resolveLocal(date.plusDays(endDayOffset.toLong()), end, ZoneId.of(zoneId)))
}

fun Plan.temporalState(now: Instant): PlanTemporalState {
    val date = scheduledDate ?: return PlanTemporalState.UNSCHEDULED
    if (!isRecurring()) return spanState(timeSpan() ?: daySpan(date, ZoneId.of(zoneId)), now)
    val zone = ZoneId.of(zoneId)
    val today = now.atZone(zone).toLocalDate()
    occurrenceDates(today.minusDays(1), today.plusDays(1)).forEach { day ->
        occurrenceSpan(day)?.let { span -> if (now >= span.start && now < span.end) return PlanTemporalState.ACTIVE }
    }
    val next = nextOccurrence(today) ?: return PlanTemporalState.PAST
    val span = occurrenceSpan(next) ?: return PlanTemporalState.FUTURE
    // A recurring series stays future/editable while more occurrences remain.
    return if (now < span.start || now >= span.end) PlanTemporalState.FUTURE else PlanTemporalState.ACTIVE
}

fun Plan.canEdit(now: Instant, policy: HistoricalPlanPolicy): Boolean =
    policy == HistoricalPlanPolicy.EDITABLE || temporalState(now) != PlanTemporalState.PAST

fun Plan.recordDraft(now: Instant): Record {
    val span = if (isRecurring()) lastOccurrenceSpan(now) ?: currentOrNextSpan(now) else timeSpan()
    val start = span?.start ?: now.minusSeconds((estimatedDuration ?: 30) * 60)
    val end = span?.end ?: now
    val safeEnd = minOf(end, now)
    return Record(title = title, categoryId = categoryId, startTime = start.takeIf { it < safeEnd } ?: safeEnd.minusSeconds(1800), endTime = safeEnd, note = note)
}

fun canCreatePlanAt(instant: Instant, now: Instant): Boolean = instant >= now
fun canCreateRecordAt(instant: Instant, now: Instant): Boolean = instant < now

fun timelineSelection(day: TimeSpan, anchorMinutes: Float, endMinutes: Float, snap: Int, plan: Boolean, now: Instant): TimeSpan? {
    val anchor = day.start.plusMillis((anchorMinutes * 60_000).toLong())
    if (if (plan) !canCreatePlanAt(anchor, now) else !canCreateRecordAt(anchor, now)) return null
    val lower = minOf(anchorMinutes, endMinutes)
    val upper = maxOf(anchorMinutes, endMinutes)
    var start = day.start.plusSeconds((kotlin.math.floor(lower / snap) * snap).toLong() * 60)
    var end = day.start.plusSeconds((kotlin.math.ceil(upper / snap) * snap).toLong() * 60)
    if (upper - lower < 1) end = start.plusSeconds(30 * 60)
    start = maxOf(start, day.start)
    end = minOf(end, day.end)
    if (plan) {
        if (start < now) start = day.start.plusSeconds(((java.time.Duration.between(day.start, now).seconds / (snap * 60) + 1) * snap * 60))
    } else end = minOf(end, now)
    return if (end > start) TimeSpan(start, end) else null
}

fun Reminder.trigger(plan: Plan, now: Instant = Instant.now()): Instant? =
    (if (anchor == ReminderAnchor.START) (plan.currentOrNextSpan(now) ?: plan.viewSpan())?.start else plan.deadline)?.minusSeconds(minutesBefore * 60L)

fun TimerSession.elapsed(now: Instant): Long =
    (Duration.between(startTimestamp, pausedAt ?: now).toMillis() - pausedDuration).coerceAtLeast(0)

/** Id of the record a running timer will become. Stored records only ever carry positive ids. */
const val LIVE_RECORD_ID = -1L

/**
 * The running timer as the record it is about to become — for display only. It starts when the current
 * work segment did and ends at [now], so it grows as the timer runs. Nothing is written here: the row
 * appears when the timer is finished, which is also why statistics and goals must ignore this one.
 * A pomodoro break is not recorded, so it has no live record either.
 */
fun TimerSession.liveRecord(now: Instant): Record? {
    if (mode == TimerMode.POMODORO && phase != TimerPhase.WORK) return null
    val start = segmentStartedAt ?: phaseStartedAt
    // A screen's clock can be a tick behind the moment the timer started, so the end is clamped rather
    // than dropped: the block shows up at once and catches up on the next tick.
    return Record(id = LIVE_RECORD_ID, title = title, categoryId = categoryId,
        startTime = start, endTime = maxOf(start, now),
        createdBy = if (mode == TimerMode.TIMER) RecordSource.TIMER else RecordSource.POMODORO)
}

fun TimerSession.phaseEnd(): Instant = phaseStartedAt.plusMillis(
    (if (phase == TimerPhase.WORK) workMinutes else breakMinutes) * 60_000L + phasePausedDuration,
)

data class PositionedSpan<T>(val id: T, val span: TimeSpan, val lane: Int, val lanes: Int)

/** The finest difference the timeline can draw: it prints HH:mm, and a minute is one dp. */
private const val MINUTE_MILLIS = 60_000L

/** Layout connected overlap groups without rejecting or modifying the persisted intervals. */
fun <T> arrangeOverlaps(items: List<Pair<T, TimeSpan>>): List<PositionedSpan<T>> {
    val groups = mutableListOf<MutableList<Pair<T, TimeSpan>>>()
    var groupEnd: Instant? = null
    items.sortedBy { it.second.start }.forEach { item ->
        if (groupEnd == null || item.second.start >= groupEnd) {
            groups.add(mutableListOf())
            groupEnd = item.second.end
        } else groupEnd = maxOf(groupEnd!!, item.second.end)
        groups.last().add(item)
    }
    return groups.flatMap { group ->
        val ends = mutableListOf<Instant>()
        val placed = group.map { (id, span) ->
            var lane = ends.indexOfFirst { it <= span.start }
            if (lane < 0) { lane = ends.size; ends.add(span.end) } else ends[lane] = span.end
            PositionedSpan(id, span, lane, 0)
        }
        placed.map { it.copy(lanes = ends.size) }
    }
}

/** Where a block is drawn: its lane, how many lanes its group has, and the height it takes. */
data class TimelinePlacement<T>(val id: T, val span: TimeSpan, val lane: Int, val lanes: Int, val drawn: TimeSpan)

/**
 * Lays out one day of blocks.
 *
 * A block is never drawn shorter than [minMillis] — the room a line of text needs — so a one-minute
 * entry stays visible instead of turning into a sliver. It keeps that height only while nothing
 * follows within it: a block with a neighbour behind is squeezed into the room between them, which
 * costs it its icon and its normal label.
 *
 * Nothing is ever drawn thinner than [floorMillis], however little room it has. Blocks that merely
 * touch — the next one starts exactly when this one ends — keep their own slot and stack one after
 * another rather than being pushed side by side; a follow-up block paints over the little a squeezed
 * block borrows past its own end. Only genuinely overlapping blocks (a negative gap) take a lane of
 * their own, which is the one case where drawing larger than the span cannot cover a neighbour.
 */
fun <T> arrangeTimeline(items: List<Pair<T, TimeSpan>>, minMillis: Long, floorMillis: Long): List<TimelinePlacement<T>> {
    if (items.isEmpty()) return emptyList()
    val ordered = items.sortedBy { it.second.start }
    fun gapTo(nextStart: Instant?, start: Instant): Long =
        nextStart?.let { Duration.between(start, it).toMillis() } ?: Long.MAX_VALUE
    // A timer stops on a second, so a 09:05:12 end laps 12s over a 09:05:00 neighbour. The timeline
    // prints whole minutes and one minute is one dp, so an overlap finer than that is invisible:
    // trimming it keeps such neighbours stacked instead of pushing them into a second column.
    fun TimeSpan.trimBelowAMinute(nextStart: Instant?): TimeSpan =
        if (nextStart == null || Duration.between(nextStart, end).toMillis() !in 1 until MINUTE_MILLIS) this
        else TimeSpan(start, maxOf(start, nextStart))
    // Lane layout ignores the floor for gaps that are not negative, so touching blocks stay together.
    val laidOut = ordered.mapIndexed { index, (id, span) ->
        val nextStart = ordered.getOrNull(index + 1)?.second?.start
        val trimmed = span.trimBelowAMinute(nextStart)
        val gap = gapTo(nextStart, trimmed.start)
        val room = if (gap < 0) floorMillis else gap.coerceAtMost(minMillis)
        id to TimeSpan(trimmed.start, trimmed.start.plusMillis(maxOf(Duration.between(trimmed.start, trimmed.end).toMillis(), room)))
    }
    val placed = arrangeOverlaps(laidOut)
    val real = ordered.associate { (id, span) -> id to Duration.between(span.start, span.end).toMillis() }
    return placed.map { position ->
        // Sharing a lane is what fixes the neighbours, so the room is read back from the result.
        val next = placed.filter { it.lane == position.lane && it.span.start > position.span.start }.minByOrNull { it.span.start }
        val gap = gapTo(next?.span?.start, position.span.start)
        val room = if (gap < 0) floorMillis else gap.coerceAtMost(minMillis)
        val drawn = maxOf(real.getValue(position.id), room, floorMillis)
        TimelinePlacement(position.id, position.span, position.lane, position.lanes,
            TimeSpan(position.span.start, position.span.start.plusMillis(drawn)))
    }
}

fun canCreateOnDay(date: LocalDate, plan: Boolean, now: Instant, zone: ZoneId = ZoneId.systemDefault(), dayStartMinutes: Int = 0): Boolean {
    val day = daySpan(date, zone, dayStartMinutes)
    return if (plan) day.end > now else day.start < now
}
