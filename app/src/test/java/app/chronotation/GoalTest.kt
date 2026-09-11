package app.chronotation

import app.chronotation.data.entity.*
import app.chronotation.domain.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class GoalTest {
    private val zone: ZoneOffset = ZoneOffset.UTC
    private val monday = LocalDate.of(2026, 9, 7)

    private fun record(id: Long, category: Long?, date: LocalDate, startHour: Int, minutes: Long, title: String = "item") =
        Record(id = id, title = title, categoryId = category, startTime = date.atTime(startHour, 0).toInstant(zone), endTime = date.atTime(startHour, 0).toInstant(zone).plusSeconds(minutes * 60))

    private fun progress(goal: Goal, records: List<Record>, now: Instant, definitions: List<PropertyDefinition> = emptyList(), values: List<PropertyValue> = emptyList()) =
        goalProgress(goal, records, definitions, values, now, zone)

    @Test fun cycleGoalsMeasureTheirRunningInstanceOnly() {
        val start = monday.minusWeeks(1).atTime(9, 0).toInstant(zone)
        val goal = Goal(name = "Sleep", categoryId = 1, metric = GoalMetric.TIME, direction = GoalDirection.AT_LEAST, target = 600 * 60_000L,
            periodUnit = GoalUnit.WEEK, repeat = GoalRepeat.CYCLE, startAt = start)
        val records = listOf(
            record(1, 1, monday.minusWeeks(1), 9, 300),
            record(2, 1, monday, 9, 300),
            record(3, 2, monday.minusWeeks(1), 9, 300),
        )
        val progress = progress(goal, records, start.plus(Duration.ofDays(2)))
        assertEquals(300 * 60_000L, progress.current)
        assertFalse(progress.isMet)
    }

    @Test fun multiUnitInstancesCoverTheirWholeSpan() {
        val start = monday.atTime(0, 0).toInstant(zone)
        val goal = Goal(name = "Focus", categoryId = 1, metric = GoalMetric.COUNT, target = 3, periodUnit = GoalUnit.DAY, periodValue = 2,
            repeat = GoalRepeat.CYCLE, startAt = start)
        val records = listOf(record(1, 1, monday, 8, 30), record(2, 1, monday.plusDays(1), 8, 30), record(3, 1, monday.plusDays(2), 8, 30))
        assertEquals(2L, progress(goal, records, monday.plusDays(1).atTime(10, 0).toInstant(zone)).current)
    }

    @Test fun oneOffGoalStaysAnchoredToItsOwnFrame() {
        val created = monday.minusWeeks(1).atTime(0, 0).toInstant(zone)
        val goal = Goal(name = "Read", categoryId = 1, metric = GoalMetric.COUNT, target = 2, periodUnit = GoalUnit.WEEK, repeat = GoalRepeat.NONE, startAt = created)
        val records = listOf(record(1, 1, monday.minusWeeks(1), 8, 30), record(2, 1, monday, 8, 30))
        assertEquals(1L, progress(goal, records, monday.plusDays(1).atTime(10, 0).toInstant(zone)).current)
    }

    @Test fun expiredInstancesChainUntilTheRunningOneReachesBeyondNow() {
        val start = monday.minusWeeks(2).atTime(0, 0).toInstant(zone)
        val goal = Goal(id = 9, name = "Read", categoryId = 1, metric = GoalMetric.COUNT, target = 2, periodUnit = GoalUnit.WEEK,
            repeat = GoalRepeat.CYCLE, startAt = start)
        val records = listOf(
            record(1, 1, monday.minusWeeks(2), 8, 30),
            record(2, 1, monday.minusWeeks(2), 9, 30),
            record(3, 1, monday.minusWeeks(1), 8, 30),
        )
        val now = monday.plusDays(1).atTime(10, 0).toInstant(zone)
        val rollover = goalRollover(goal, records, emptyList(), emptyList(), now, zone)
        // Two instances ran out; each lands in the ended list with the amount it reached.
        assertEquals(2, rollover.instances.size)
        assertEquals(listOf(start, monday.minusWeeks(1).atTime(0, 0).toInstant(zone)), rollover.instances.map { it.start })
        assertEquals(2L, goalInstanceActual(goal, records, emptyList(), emptyList(), TimeSpan(rollover.instances[0].start, rollover.instances[0].end)))
        assertEquals(1L, goalInstanceActual(goal, records, emptyList(), emptyList(), TimeSpan(rollover.instances[1].start, rollover.instances[1].end)))
        assertFalse(rollover.retired)
        // The running instance starts where the last one ended, and reaches beyond now.
        assertEquals(monday.atTime(0, 0).toInstant(zone), rollover.startAt)
        assertTrue(goal.periodEnd(rollover.startAt, zone) > now)
        assertEquals(0L, progress(goal.copy(startAt = rollover.startAt), records, now).current)
    }

    @Test fun achievedResetGoalStartsItsNextInstanceAtTheCompletion() {
        val start = monday.atTime(8, 0).toInstant(zone)
        val goal = Goal(id = 4, name = "Read", categoryId = 1, metric = GoalMetric.COUNT, target = 2, repeat = GoalRepeat.RESET, startAt = start)
        val records = listOf(record(1, 1, monday, 9, 30), record(2, 1, monday, 10, 30), record(3, 1, monday, 11, 30))
        val now = monday.atTime(23, 0).toInstant(zone)
        val rollover = goalRollover(goal, records, emptyList(), emptyList(), now, zone)
        assertEquals(1, rollover.instances.size)
        assertEquals(start, rollover.instances.single().start)
        assertEquals(monday.atTime(10, 30).toInstant(zone), rollover.instances.single().end)
        assertEquals(monday.atTime(10, 30).toInstant(zone), rollover.startAt)
        assertEquals(1L, progress(goal.copy(startAt = rollover.startAt), records, now).current)
    }

    @Test fun resetGoalIgnoresWhatHappenedBeforeItsInstance() {
        val goal = Goal(name = "Read", categoryId = 1, metric = GoalMetric.COUNT, direction = GoalDirection.AT_LEAST, target = 2,
            repeat = GoalRepeat.RESET, startAt = monday.atTime(12, 0).toInstant(zone))
        val records = listOf(record(1, 1, monday, 9, 30), record(2, 1, monday, 13, 30))
        val progress = progress(goal, records, monday.atTime(14, 0).toInstant(zone))
        assertEquals(1L, progress.current)
        assertEquals(0, progress.completed)
    }

    @Test fun oneOffGoalRetiresOnceItsFrameHasPassed() {
        val start = monday.minusWeeks(1).atTime(9, 0).toInstant(zone)
        val goal = Goal(id = 3, name = "Trip", categoryId = 1, metric = GoalMetric.COUNT, target = 1, periodUnit = GoalUnit.WEEK,
            repeat = GoalRepeat.NONE, startAt = start)
        val running = goalRollover(goal, emptyList(), emptyList(), emptyList(), start.plus(Duration.ofDays(1)), zone)
        assertFalse(running.retired)
        assertTrue(running.instances.isEmpty())
        val over = goalRollover(goal, emptyList(), emptyList(), emptyList(), monday.atTime(9, 0).toInstant(zone), zone)
        assertTrue(over.retired)
        assertEquals(1, over.instances.size)
        assertEquals(start, over.instances.single().start)
    }

    @Test fun titleFilterUsesRegularExpressions() {
        val goal = Goal(name = "Read", titleFilter = "^Read")
        assertTrue(goal.matches(record(1, null, monday, 9, 30, title = "Read a book"), null, null))
        assertFalse(goal.matches(record(1, null, monday, 9, 30, title = "Walk then read"), null, null))
        assertTrue(goal.copy(titleFilter = "").matches(record(1, null, monday, 9, 30, title = "anything"), null, null))
    }

    @Test fun selectFilterMatchesSubsets() {
        val kind = PropertyDefinition(id = 5, categoryId = 1, name = "Kind", type = PropertyType.SELECT, options = "Book\nArticle\nFilm")
        val goal = Goal(name = "Read", categoryId = 1, propertyDefinitionId = 5, propertyFilter = "Book\nArticle")
        val item = record(1, 1, monday, 9, 30)
        assertTrue(goal.matches(item, kind, "Book"))
        assertTrue(goal.matches(item, kind, "Book\nArticle"))
        assertFalse(goal.matches(item, kind, "Film"))
        assertFalse(goal.matches(item.copy(categoryId = 2), kind, "Book"))
        assertTrue(goal.copy(propertyFilter = "").matches(item, kind, "Book"))
        assertFalse(goal.copy(propertyFilter = "").matches(item, kind, null))
    }

    @Test fun numberAndRatingFiltersMatchRanges() {
        val pages = PropertyDefinition(id = 6, categoryId = 1, name = "Pages", type = PropertyType.NUMBER)
        val rating = PropertyDefinition(id = 7, categoryId = 1, name = "Score", type = PropertyType.RATING)
        val item = record(1, 1, monday, 9, 30)
        assertTrue(Goal(name = "g", propertyDefinitionId = 6, propertyFilter = "60,120").matches(item, pages, "80"))
        assertFalse(Goal(name = "g", propertyDefinitionId = 6, propertyFilter = "60,120").matches(item, pages, "40"))
        assertTrue(Goal(name = "g", propertyDefinitionId = 6, propertyFilter = ",100").matches(item, pages, "80"))
        assertTrue(Goal(name = "g", propertyDefinitionId = 7, propertyFilter = "80,").matches(item, rating, "4.5"))
        assertFalse(Goal(name = "g", propertyDefinitionId = 7, propertyFilter = "80,").matches(item, rating, "3"))
    }

    @Test fun textFilterUsesRegularExpressions() {
        val note = PropertyDefinition(id = 8, categoryId = 1, name = "Note", type = PropertyType.TEXT)
        val item = record(1, 1, monday, 9, 30)
        assertTrue(Goal(name = "g", propertyDefinitionId = 8, propertyFilter = "Book|Article").matches(item, note, "Finished the Book"))
        assertFalse(Goal(name = "g", propertyDefinitionId = 8, propertyFilter = "^book").matches(item, note, "Finished the Book"))
    }

    @Test fun atMostGoalFlagsOvershoot() {
        val goal = Goal(name = "Limit", categoryId = 1, metric = GoalMetric.COUNT, direction = GoalDirection.AT_MOST, target = 1, periodUnit = GoalUnit.WEEK,
            repeat = GoalRepeat.CYCLE, startAt = monday.atTime(0, 0).toInstant(zone))
        val now = monday.atTime(12, 0).toInstant(zone)
        val records = listOf(record(1, 1, monday, 8, 30), record(2, 1, monday, 9, 30))
        val progress = progress(goal, records, now)
        assertEquals(2L, progress.current)
        assertTrue(progress.isOver)
        assertFalse(progress.isMet)
    }

    @Test fun dailyTotalsSplitRecordsAcrossLogicalDays() {
        val record = Record(id = 1, title = "Night", categoryId = 1, startTime = LocalDate.of(2026, 9, 8).atTime(23, 0).toInstant(zone), endTime = LocalDate.of(2026, 9, 9).atTime(1, 0).toInstant(zone))
        val totals = dailyTotals(listOf(record), LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 9), zone)
        assertEquals(3_600_000L, totals[LocalDate.of(2026, 9, 8)])
        assertEquals(3_600_000L, totals[LocalDate.of(2026, 9, 9)])
    }

    @Test fun attributeStatsAggregateRatingsAndOptions() {
        val rating = PropertyDefinition(id = 1, categoryId = 1, name = "Score", type = PropertyType.RATING)
        val select = PropertyDefinition(id = 2, categoryId = 1, name = "Kind", type = PropertyType.SELECT, options = "Film\nShow")
        val values = listOf(
            PropertyValue(10, 1, "4"), PropertyValue(11, 1, "5"),
            PropertyValue(10, 2, "Film"), PropertyValue(11, 2, "Film"), PropertyValue(12, 2, "Show"),
        )
        val stats = attributeStats(listOf(rating, select), values, setOf(10, 11))
        val ratingStat = stats.first { it.definition.id == 1L }
        assertEquals(2, ratingStat.filled)
        assertEquals(90.0, ratingStat.average!!, 0.001)
        val selectStat = stats.first { it.definition.id == 2L }
        assertEquals(listOf("Film" to 2), selectStat.counts)
    }
}
