package app.chronotation

import app.chronotation.data.entity.Plan
import app.chronotation.data.entity.Recurrence
import app.chronotation.data.entity.Reminder
import app.chronotation.data.entity.ReminderAnchor
import app.chronotation.domain.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class RecurrenceTest {
    private fun plan(date: String, start: String, end: String, recurrence: Recurrence, interval: Int = 1,
        until: String? = null, endDayOffset: Int = 0, allDay: Boolean = false) = Plan(
        title = "Task", scheduledDate = LocalDate.parse(date), startTime = LocalTime.parse(start), endTime = LocalTime.parse(end),
        zoneId = "UTC", endDayOffset = endDayOffset, allDay = allDay, recurrence = recurrence, recurrenceInterval = interval,
        recurrenceUntil = until?.let(LocalDate::parse))

    @Test fun occurrencesFollowTheAnchoredRule() {
        val daily = plan("2026-09-07", "10:00", "11:00", Recurrence.DAILY)
        assertEquals(listOf(LocalDate.parse("2026-09-09"), LocalDate.parse("2026-09-10")),
            daily.occurrenceDates(LocalDate.parse("2026-09-09"), LocalDate.parse("2026-09-10")))
        assertEquals(listOf(LocalDate.parse("2026-09-07")), daily.occurrenceDates(LocalDate.parse("2026-01-01"), LocalDate.parse("2026-09-07")))

        val everyThreeDays = daily.copy(recurrenceInterval = 3)
        assertEquals(listOf(LocalDate.parse("2026-09-10")), everyThreeDays.occurrenceDates(LocalDate.parse("2026-09-08"), LocalDate.parse("2026-09-11")))

        val weekly = plan("2026-09-07", "10:00", "11:00", Recurrence.WEEKLY)
        assertEquals(listOf(LocalDate.parse("2026-09-14"), LocalDate.parse("2026-09-21"), LocalDate.parse("2026-09-28")),
            weekly.occurrenceDates(LocalDate.parse("2026-09-10"), LocalDate.parse("2026-09-30")))

        val monthly = plan("2026-01-31", "10:00", "11:00", Recurrence.MONTHLY)
        assertEquals(listOf(LocalDate.parse("2026-02-28"), LocalDate.parse("2026-03-31")),
            monthly.occurrenceDates(LocalDate.parse("2026-02-01"), LocalDate.parse("2026-03-31")))

        val yearly = plan("2026-02-28", "10:00", "11:00", Recurrence.YEARLY)
        assertEquals(listOf(LocalDate.parse("2028-02-28")), yearly.occurrenceDates(LocalDate.parse("2028-01-01"), LocalDate.parse("2028-12-31")))
    }

    @Test fun nonRecurringPlansKeepTheirSingleDate() {
        val once = plan("2026-09-07", "10:00", "11:00", Recurrence.NONE)
        assertEquals(listOf(LocalDate.parse("2026-09-07")), once.occurrenceDates(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30")))
        assertTrue(once.occurrenceDates(LocalDate.parse("2026-09-08"), LocalDate.parse("2026-09-30")).isEmpty())
        assertEquals(LocalDate.parse("2026-09-07"), once.nextOccurrence(LocalDate.parse("2026-09-01")))
        assertNull(once.nextOccurrence(LocalDate.parse("2026-09-08")))
    }

    @Test fun untilEndsTheSeries() {
        val daily = plan("2026-09-07", "10:00", "11:00", Recurrence.DAILY, until = "2026-09-09")
        assertEquals(listOf(LocalDate.parse("2026-09-07"), LocalDate.parse("2026-09-08"), LocalDate.parse("2026-09-09")),
            daily.occurrenceDates(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30")))
        assertEquals(LocalDate.parse("2026-09-09"), daily.nextOccurrence(LocalDate.parse("2026-09-09")))
        assertNull(daily.nextOccurrence(LocalDate.parse("2026-09-10")))
    }

    @Test fun occurrenceSpanKeepsTimeOfDayAndCrossDayOffset() {
        val night = plan("2026-09-07", "22:00", "02:00", Recurrence.DAILY, endDayOffset = 1)
        val span = night.occurrenceSpan(LocalDate.parse("2026-09-09"))!!
        assertEquals(Instant.parse("2026-09-09T22:00:00Z"), span.start)
        assertEquals(Instant.parse("2026-09-10T02:00:00Z"), span.end)

        val allDay = plan("2026-09-07", "00:00", "00:00", Recurrence.WEEKLY, allDay = true)
        val day = allDay.occurrenceSpan(LocalDate.parse("2026-09-14"))!!
        assertEquals(Instant.parse("2026-09-14T00:00:00Z"), day.start)
        assertEquals(Instant.parse("2026-09-15T00:00:00Z"), day.end)
    }

    @Test fun temporalStateFollowsTheRunningOccurrence() {
        val daily = plan("2026-09-01", "10:00", "11:00", Recurrence.DAILY)
        assertEquals(PlanTemporalState.ACTIVE, daily.temporalState(Instant.parse("2026-09-07T10:30:00Z")))
        assertEquals(PlanTemporalState.FUTURE, daily.temporalState(Instant.parse("2026-09-07T12:00:00Z")))
        assertEquals(PlanTemporalState.FUTURE, daily.temporalState(Instant.parse("2026-09-07T05:00:00Z")))
        assertTrue(daily.canEdit(Instant.parse("2026-09-07T12:00:00Z"), HistoricalPlanPolicy.IMMUTABLE))

        val ended = daily.copy(recurrenceUntil = LocalDate.parse("2026-09-05"))
        assertEquals(PlanTemporalState.PAST, ended.temporalState(Instant.parse("2026-09-07T12:00:00Z")))
        assertFalse(ended.canEdit(Instant.parse("2026-09-07T12:00:00Z"), HistoricalPlanPolicy.IMMUTABLE))
    }

    @Test fun agendaShowsTheNextOccurrenceAndCalendarExpandsTheRange() {
        val daily = plan("2026-09-01", "10:00", "11:00", Recurrence.DAILY)
        val today = LocalDate.parse("2026-09-07")
        val agenda = browseEntries(listOf(daily), emptyList(), ZoneOffset.UTC, 0, today)
        assertTrue(agenda.size > 1)
        val onToday = agenda.single { it.date == today }
        assertEquals(today, onToday.occurrence)
        assertEquals(Instant.parse("2026-09-07T10:00:00Z"), onToday.span!!.start)

        val ended = daily.copy(recurrenceUntil = LocalDate.parse("2026-09-04"))
        val last = browseEntries(listOf(ended), emptyList(), ZoneOffset.UTC, 0, today).last()
        assertEquals(LocalDate.parse("2026-09-04"), last.date)

        val now = Instant.parse("2026-09-07T00:00:00Z")
        val calendar = calendarEntries(listOf(daily), emptyList(), today, today.plusDays(2), now, ZoneOffset.UTC, 0)
        assertEquals(3, calendar.size)
        assertEquals(3, calendar.map { it.key }.distinct().size)
        assertTrue(calendar.all { it.span!!.end > now })
        val clamped = calendarEntries(listOf(daily), emptyList(), today, today, Instant.parse("2026-09-07T10:30:00Z"), ZoneOffset.UTC, 0).single()
        assertEquals(Instant.parse("2026-09-07T10:30:00Z"), clamped.span!!.start)
    }

    @Test fun remindersMoveToTheNextOccurrence() {
        val daily = plan("2026-09-01", "10:00", "11:00", Recurrence.DAILY)
        val reminder = Reminder(planId = 1, anchor = ReminderAnchor.START, minutesBefore = 30)
        val now = Instant.parse("2026-09-07T12:00:00Z")
        assertEquals(Instant.parse("2026-09-08T09:30:00Z"), reminder.trigger(daily, now))

        val plain = plan("2026-09-20", "10:00", "11:00", Recurrence.NONE)
        assertEquals(Instant.parse("2026-09-20T09:30:00Z"), reminder.trigger(plain, now))

        val ended = daily.copy(recurrenceUntil = LocalDate.parse("2026-09-05"))
        assertEquals(Instant.parse("2026-09-01T09:30:00Z"), reminder.trigger(ended, now))
    }

    @Test fun recordDraftUsesTheLatestOccurrence() {
        val daily = plan("2026-09-01", "10:00", "11:00", Recurrence.DAILY)
        val draft = daily.recordDraft(Instant.parse("2026-09-07T12:00:00Z"))
        assertEquals(Instant.parse("2026-09-07T10:00:00Z"), draft.startTime)
        assertEquals(Instant.parse("2026-09-07T11:00:00Z"), draft.endTime)
    }

    @Test fun skippedDatesLeaveTheSeries() {
        val daily = plan("2026-09-07", "10:00", "11:00", Recurrence.DAILY)
        val skipped = daily.copy(skipDates = "2026-09-08\n2026-09-10")
        assertEquals(listOf(LocalDate.parse("2026-09-07"), LocalDate.parse("2026-09-09")),
            skipped.occurrenceDates(LocalDate.parse("2026-09-07"), LocalDate.parse("2026-09-09")))
        assertEquals(LocalDate.parse("2026-09-11"), skipped.nextOccurrence(LocalDate.parse("2026-09-10")))
        val single = daily.copy(recurrence = Recurrence.NONE, skipDates = "2026-09-07")
        assertTrue(single.occurrenceDates(LocalDate.parse("2026-09-01"), LocalDate.parse("2026-09-30")).isEmpty())
    }
}
