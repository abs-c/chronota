package app.chronotation.domain

import app.chronotation.data.entity.*
import java.time.*
import kotlin.math.ceil
import kotlin.math.floor

/** Recorded time per logical day, inclusive, for the records passing [predicate]. */
fun dailyTotals(records: List<Record>, from: LocalDate, to: LocalDate, zone: ZoneId,
    dayStartMinutes: Int = 0, predicate: (Record) -> Boolean = { true }): Map<LocalDate, Long> {
    val result = LinkedHashMap<LocalDate, Long>()
    var date = from
    while (!date.isAfter(to)) { result[date] = 0; date = date.plusDays(1) }
    records.filter(predicate).forEach { record ->
        val span = TimeSpan(record.startTime, record.endTime)
        var day = logicalDate(record.startTime, zone, dayStartMinutes)
        val last = logicalDate(record.endTime.minusMillis(1), zone, dayStartMinutes)
        while (!day.isAfter(last)) {
            span.clippedTo(daySpan(day, zone, dayStartMinutes))?.let { result[day] = (result[day] ?: 0) + it.millis }
            day = day.plusDays(1)
        }
    }
    return result
}

fun recordedTime(records: List<Record>, span: TimeSpan, predicate: (Record) -> Boolean = { true }): Long =
    records.filter(predicate).sumOf { TimeSpan(it.startTime, it.endTime).clippedTo(span)?.millis ?: 0 }

enum class ChartBucket { HOUR, DAY }

/** Per-bucket durations grouped by category, used by the stacked overview chart. */
fun stackedTotals(records: List<Record>, range: TimeSpan, bucket: ChartBucket, zone: ZoneId, dayStartMinutes: Int = 0): List<Pair<Instant, Map<Long?, Long>>> {
    val spans = mutableListOf<TimeSpan>()
    if (bucket == ChartBucket.DAY) {
        var date = logicalDate(range.start, zone, dayStartMinutes)
        while (true) {
            val span = daySpan(date, zone, dayStartMinutes)
            spans.add(span.clippedTo(range) ?: break)
            if (span.end >= range.end) break
            date = date.plusDays(1)
        }
    } else {
        var instant = range.start
        while (instant < range.end) {
            val end = minOf(instant.plusSeconds(3600), range.end)
            spans.add(TimeSpan(instant, end))
            instant = end
        }
    }
    return spans.map { span ->
        val byCategory = records.mapNotNull { record -> TimeSpan(record.startTime, record.endTime).clippedTo(span)?.let { record.categoryId to it.millis } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.sum() }.filterValues { it > 0 }
        span.start to byCategory
    }
}

/** Aggregated property values for one category, used by the category detail page. */
data class AttributeStat(val definition: PropertyDefinition, val filled: Int, val average: Double?, val total: Double?, val counts: List<Pair<String, Int>>, val bins: List<Pair<String, Int>> = emptyList(), val chars: Int = 0)

fun attributeStats(definitions: List<PropertyDefinition>, values: List<PropertyValue>, recordIds: Set<Long>): List<AttributeStat> =
    definitions.map { definition ->
        val entries = values.filter { it.definitionId == definition.id && it.recordId in recordIds }.map { it.value }
        when (definition.type) {
            PropertyType.RATING -> {
                val scores = entries.mapNotNull { it.toDoubleOrNull() }.map { it * 20 }
                AttributeStat(definition, entries.size, scores.takeIf { it.isNotEmpty() }?.average(), null, emptyList(), bins(scores, 0.0, 100.0, 5))
            }
            PropertyType.NUMBER -> {
                val numbers = entries.mapNotNull { it.toDoubleOrNull() }
                val bins = if (numbers.isEmpty()) emptyList() else bins(numbers, floor(numbers.min()).let { if (it == numbers.max()) it - 1 else it }, ceil(numbers.max()), 5)
                AttributeStat(definition, entries.size, numbers.takeIf { it.isNotEmpty() }?.average(), numbers.sum(), emptyList(), bins)
            }
            PropertyType.SELECT, PropertyType.MULTISELECT -> {
                val counts = entries.flatMap { it.lines() }.filter { it.isNotBlank() }.groupingBy { it }.eachCount()
                    .entries.sortedByDescending { it.value }.map { it.key to it.value }
                AttributeStat(definition, entries.size, null, null, counts)
            }
            PropertyType.BOOLEAN -> {
                val counts = listOf("true" to entries.count { it == "true" }, "false" to entries.count { it == "false" }).filter { it.second > 0 }
                AttributeStat(definition, entries.size, null, null, counts)
            }
            else -> AttributeStat(definition, entries.size, null, null, emptyList(), chars = entries.sumOf { it.length })
        }
    }

/** Equal-width histogram over [min]..[max]; the last bin includes the upper edge. */
fun bins(values: List<Double>, min: Double, max: Double, count: Int): List<Pair<String, Int>> {
    if (values.isEmpty()) return emptyList()
    if (max <= min) return listOf(numberLabel(min) to values.size)
    val width = (max - min) / count
    return (0 until count).map { index ->
        val low = min + width * index
        val high = if (index == count - 1) max else min + width * (index + 1)
        val label = numberLabel(low) + "–" + numberLabel(high)
        label to values.count { it >= low && (it < high || index == count - 1) }
    }
}

private fun numberLabel(value: Double): String = if (value % 1.0 == 0.0) value.toInt().toString() else String.format(java.util.Locale.getDefault(), "%.1f", value)
