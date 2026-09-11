package app.chronota.domain

import app.chronota.data.entity.*
import java.time.*
import java.time.temporal.TemporalAdjusters
import java.time.temporal.ChronoUnit

enum class ReviewPeriod { DAY, WEEK, MONTH, CUSTOM }


fun reviewRange(period: ReviewPeriod, anchor: LocalDate, from: LocalDate, through: LocalDate, zone: ZoneId, dayStartMinutes: Int = 0, weekStart: Int = 7): TimeSpan {
    val start = when (period) { ReviewPeriod.DAY -> anchor; ReviewPeriod.WEEK -> weekStartOf(anchor, weekStart); ReviewPeriod.MONTH -> anchor.withDayOfMonth(1); ReviewPeriod.CUSTOM -> from }
    val end = when (period) { ReviewPeriod.DAY -> start.plusDays(1); ReviewPeriod.WEEK -> start.plusDays(7); ReviewPeriod.MONTH -> start.plusMonths(1); ReviewPeriod.CUSTOM -> through.plusDays(1) }
    require(ChronoUnit.DAYS.between(start, end) in 1..366)
    return TimeSpan(daySpan(start, zone, dayStartMinutes).start, daySpan(end, zone, dayStartMinutes).start)
}

data class ReviewReport(val total: Long, val planned: Long,
    val categories: Map<Long?, Long>, val parents: Map<Long?, Long>, val trend: List<Pair<LocalDate, Long>>,
    val records: List<Pair<Record, TimeSpan>>)

fun reviewReport(records: List<Record>, plans: List<Plan>, categories: List<Category>, range: TimeSpan, zone: ZoneId, dayStartMinutes: Int = 0): ReviewReport {
    val clipped = records.mapNotNull { record -> TimeSpan(record.startTime, record.endTime).clippedTo(range)?.let { record to it } }
    val categoryMap = categories.associateBy { it.id }
    val byCategory = clipped.groupBy { it.first.categoryId }.mapValues { it.value.sumOf { row -> row.second.millis } }
    val byParent = clipped.groupBy { categoryMap[it.first.categoryId]?.parentId }.mapValues { it.value.sumOf { row -> row.second.millis } }
    val planned = plans.mapNotNull { plan -> plan.viewSpan(dayStartMinutes)?.clippedTo(range)?.let { plan to it } }
    val start = logicalDate(range.start, zone, dayStartMinutes)
    val count = ChronoUnit.DAYS.between(start, logicalDate(range.end, zone, dayStartMinutes)).toInt()
    val trend = (0 until count).map { index ->
        val date = start.plusDays(index.toLong())
        val day = daySpan(date, zone, dayStartMinutes)
        date to clipped.sumOf { it.second.clippedTo(day)?.millis ?: 0 }
    }
    return ReviewReport(clipped.sumOf { it.second.millis }, planned.sumOf { it.second.millis }, byCategory, byParent, trend, clipped)
}