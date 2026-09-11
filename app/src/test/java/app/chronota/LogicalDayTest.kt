package app.chronota

import app.chronota.data.entity.*
import app.chronota.domain.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class LogicalDayTest {
    private val zone = ZoneOffset.UTC
    private val date = LocalDate.of(2026, 9, 8)

    @Test fun shiftedBoundaryIsSharedByAgendaCalendarAndStatistics() {
        val start = Instant.parse("2026-09-09T03:30:00Z")
        val record = Record(id = 1, title = "", startTime = start, endTime = start.plusSeconds(3600))
        val entry = browseEntries(emptyList(), listOf(record), zone, 240).single()
        assertEquals(date, entry.date)
        assertTrue(entry.onDate(date, zone, 240))
        assertTrue(entry.onDate(date.plusDays(1), zone, 240))
        val range = reviewRange(ReviewPeriod.CUSTOM, date, date, date.plusDays(1), zone, 240)
        val report = reviewReport(listOf(record), emptyList(), emptyList(), range, zone, 240)
        assertEquals(listOf(1_800_000L, 1_800_000L), report.trend.map { it.second })
        assertEquals(report.total, report.trend.sumOf { it.second })
        assertEquals(date, logicalDate(start, zone, 240))
        assertEquals(date.plusDays(1), logicalDate(start.plusSeconds(1800), zone, 240))
        assertEquals(start, record.startTime)
    }

    @Test fun boundarySurvivesMissingAndRepeatedLocalHours() {
        val dst = ZoneId.of("America/New_York")
        for (day in listOf(LocalDate.of(2026, 3, 8), LocalDate.of(2026, 11, 1))) {
            val span = daySpan(day, dst, 150)
            assertEquals(day.minusDays(1), logicalDate(span.start.minusMillis(1), dst, 150))
            assertEquals(day, logicalDate(span.start, dst, 150))
            assertEquals(day, logicalDate(span.end.minusMillis(1), dst, 150))
            assertEquals(day.plusDays(1), logicalDate(span.end, dst, 150))
        }
    }

    @Test fun calendarOnlyKeepsFuturePortionAndDoesNotChangePlans() {
        val plan = Plan(title = "", scheduledDate = date, startTime = LocalTime.of(10, 0), endTime = LocalTime.of(11, 0), zoneId = "UTC")
        val now = Instant.parse("2026-09-08T10:30:00Z")
        assertEquals(now, calendarPlanSpan(plan, now, 240)!!.start)
        assertNull(calendarPlanSpan(plan, now.plusSeconds(1800), 240))
        assertEquals(LocalTime.of(10, 0), plan.startTime)
        assertEquals(date, logicalDate(plan.copy(allDay = true).viewSpan(240)!!.start, zone, 240))
    }

    @Test fun aTimedPlanKeepsPlainClockSemantics() {
        val plan = Plan(title = "", scheduledDate = date, startTime = LocalTime.of(1, 30), endTime = LocalTime.of(2, 30), zoneId = "UTC")
        val span = plan.timeSpan()!!
        // 01:30 means 01:30 on the scheduled date, whatever the day-start preference is: the editor
        // and the "add at this slot" gesture already pick the calendar date from the tapped instant.
        assertEquals(Instant.parse("2026-09-08T01:30:00Z"), span.start)
        assertEquals(Instant.parse("2026-09-08T02:30:00Z"), span.end)
        // Its logical day is then read off that instant, exactly like a record's.
        assertEquals(date.minusDays(1), logicalDate(span.start, zone, 240))
        assertEquals(date, logicalDate(Instant.parse("2026-09-08T10:00:00Z"), zone, 240))
    }

    @Test fun anEveningOrOvernightPlanIsNotShiftedTwice() {
        // 21:00-21:30 on the 8th is the evening of the 8th.
        val evening = Plan(title = "Evening", scheduledDate = date, startTime = LocalTime.of(21, 0), endTime = LocalTime.of(21, 30), zoneId = "UTC").timeSpan()!!
        assertEquals(date, logicalDate(evening.start, zone, 240))
        assertEquals(30 * 60_000L, evening.millis)
        // 23:00 to 01:00 the next calendar day is two hours, not a day and two hours.
        val overnight = Plan(title = "Overnight", scheduledDate = date, startTime = LocalTime.of(23, 0), endTime = LocalTime.of(1, 0), endDayOffset = 1, zoneId = "UTC").timeSpan()!!
        assertEquals(Instant.parse("2026-09-08T23:00:00Z"), overnight.start)
        assertEquals(Instant.parse("2026-09-09T01:00:00Z"), overnight.end)
        assertEquals(2 * 3_600_000L, overnight.millis)
        assertEquals(date, logicalDate(overnight.start, zone, 240))
    }

    @Test fun aPlanAddedInTheSmallHoursStaysInTheDayItWasAddedTo() {
        // 00:40 on the 9th, with the day starting at 04:00, is still the logical day of the 8th, and
        // its "01:30" slot is 01:30 on the 9th. That is what the gesture stores, and the plan has to
        // come back in the same view rather than landing a further day out.
        val now = Instant.parse("2026-09-09T00:40:00Z")
        val shown = logicalDate(now, zone, 240)
        assertEquals(date, shown)
        val plan = Plan(id = 1, title = "Late", scheduledDate = LocalDate.of(2026, 9, 9), startTime = LocalTime.of(1, 30), endTime = LocalTime.of(2, 30), zoneId = "UTC")
        assertEquals(shown, logicalDate(plan.timeSpan()!!.start, zone, 240))
        val entry = browseEntries(listOf(plan), emptyList(), zone, 240, shown).single()
        assertEquals(shown, entry.date)
        assertTrue(entry.onDate(shown, zone, 240))
        // The same plan is a future occurrence worth drawing on the calendar the user is on.
        assertEquals(1, calendarEntries(listOf(plan), emptyList(), shown, shown, now, zone, 240).size)
    }

    @Test fun blankTitleAlwaysFallsBackWithoutPersistingCategoryName() {
        assertEquals("Reading", displayName("", "Reading", false, "Uncategorized"))
        assertEquals("Chapter", displayName("Chapter", "Reading", false, "Uncategorized"))
        assertEquals("Reading", displayName("Chapter", "Reading", true, "Uncategorized"))
        assertEquals("Uncategorized", displayName("", null, false, "Uncategorized"))
    }
}
